/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import de.greluc.krt.profit.basetool.android.core.network.await
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Everything the app says to Keycloak's token endpoint: the code exchange, the refresh, the
 * revocation, and the logout URL.
 *
 * **This is the only place a DPoP proof is attached.** Under the realm's refresh-only binding
 * policy (main repo ADR-0131), a proof sent anywhere else makes Keycloak bind the *access* token
 * too, and the backend's bearer filter rejects a bound access token — so a well-meant interceptor
 * would break every API call after the next login. Proof creation therefore lives behind
 * [DpopProofFactory] and is called from here only.
 *
 * **The client must not be the API client.** [OkHttpClient] instances are shared cheaply, but the
 * API client carries `MandatoryHeadersInterceptor`, which would put an `Authorization: Bearer`
 * header on a token request — Keycloak reads that as an attempt at client authentication and
 * answers `invalid_client`, i.e. login would fail as soon as a session already existed. Build the
 * client for this class with `KrtHttpClient.createTokenClient`.
 *
 * @property httpClient a token-scoped client; see above
 * @property configuration realm URLs and the public client id
 * @property proofFactory builds the DPoP proofs
 * @property serverClock turns the response's `expires_in` into an absolute instant
 * @property json lenient by default, so an added field in a realm upgrade is not an outage
 */
class TokenClient(
    private val httpClient: OkHttpClient,
    private val configuration: OidcConfiguration,
    private val proofFactory: DpopProofFactory,
    private val serverClock: ServerClock,
    private val json: Json = DEFAULT_JSON,
) {
    /**
     * The most recent `DPoP-Nonce` the realm handed out.
     *
     * RFC 9449 §8 lets the server demand a nonce at any time and rotate it on every response. The
     * realm does not require one today; carrying it anyway is what keeps enabling nonces from
     * being a client-breaking change.
     */
    private val nonce = AtomicReference<String?>(null)

    /**
     * Exchanges an authorization code for the first token set.
     *
     * @param code the code the redirect delivered
     * @param codeVerifier the PKCE verifier whose challenge started the flow
     * @return the outcome; [TokenResult.SessionEnded] here means the code was already used or
     *   expired, which for a login attempt means "start over"
     */
    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): TokenResult =
        requestTokens(
            FormBody
                .Builder()
                .add(PARAM_GRANT_TYPE, GRANT_AUTHORIZATION_CODE)
                .add(PARAM_CLIENT_ID, configuration.clientId)
                .add(PARAM_CODE, code)
                .add(PARAM_CODE_VERIFIER, codeVerifier)
                .add(PARAM_REDIRECT_URI, configuration.redirectUri)
                .build(),
        )

    /**
     * Exchanges a refresh token for a new token set.
     *
     * The realm does **not** rotate refresh tokens (main repo REQ-SEC-012 / ADR-0019 amendment 4),
     * which is precisely why the refresh token is DPoP-bound instead: a stolen one is useless
     * without the device key. A response may still carry a refresh token, and the caller stores
     * whatever came back rather than assuming it is unchanged.
     *
     * @param refreshToken the stored refresh token
     * @return the outcome; [TokenResult.SessionEnded] means the stored token is spent and the
     *   member has to log in again
     */
    suspend fun refresh(refreshToken: String): TokenResult =
        requestTokens(
            FormBody
                .Builder()
                .add(PARAM_GRANT_TYPE, GRANT_REFRESH_TOKEN)
                .add(PARAM_CLIENT_ID, configuration.clientId)
                .add(PARAM_REFRESH_TOKEN, refreshToken)
                .build(),
        )

    /**
     * Asks the realm to revoke a refresh token, and never fails the caller.
     *
     * Best-effort by design: logout must complete on a phone with no connectivity, and what
     * actually protects the device is the local wipe. Revocation shortens the window in which a
     * copy of the token that escaped the device is still worth something — valuable, but not worth
     * blocking a logout on.
     *
     * No DPoP proof is attached. RFC 9449 binds *token issuance*; revocation identifies the token
     * by value, and a proof there would be a claim about a request the realm does not check.
     *
     * @param refreshToken the token to revoke
     * @return `true` when the realm confirmed the revocation; for diagnostics and tests, not a
     *   branch the logout flow should take
     */
    suspend fun revokeRefreshToken(refreshToken: String): Boolean =
        try {
            val body =
                FormBody
                    .Builder()
                    .add(PARAM_CLIENT_ID, configuration.clientId)
                    .add(PARAM_TOKEN, refreshToken)
                    .add(PARAM_TOKEN_TYPE_HINT, TOKEN_TYPE_HINT_REFRESH)
                    .build()
            post(configuration.revocationEndpoint, body, proof = null).use { response ->
                if (!response.isSuccessful) {
                    KrtLog.w(LOG_TAG) { "refresh token revocation refused with HTTP ${response.code}" }
                }
                response.isSuccessful
            }
        } catch (io: IOException) {
            KrtLog.w(LOG_TAG, io) { "refresh token revocation could not be sent" }
            false
        }

    /**
     * Builds the RP-initiated logout URL to open in the browser.
     *
     * Opening it — rather than only wiping locally — is what ends the realm's SSO cookie; without
     * it the next login silently reuses the browser session and "log out, log in as someone else"
     * does not work. Keycloak accepts `post_logout_redirect_uri` only together with an
     * `id_token_hint` or a `client_id`; both are sent.
     *
     * @param idToken the ID token of the session being ended
     * @return the absolute URL for the Custom Tab
     */
    fun endSessionUri(idToken: String): String =
        configuration.endSessionEndpoint
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter(PARAM_ID_TOKEN_HINT, idToken)
            .addQueryParameter(PARAM_POST_LOGOUT_REDIRECT_URI, configuration.postLogoutRedirectUri)
            .addQueryParameter(PARAM_CLIENT_ID, configuration.clientId)
            .build()
            .toString()

    /**
     * Sends a grant request, retrying once if the realm demands a fresh nonce.
     *
     * The retry is the whole of RFC 9449 §8.3: the rejected response carried the nonce to use, it
     * was captured on the way past, and the second attempt is identical apart from the proof. One
     * retry only — a server that rejects the nonce it just issued is broken, and a loop there
     * would hammer the token endpoint.
     *
     * @param form the grant parameters
     * @return the outcome
     */
    private suspend fun requestTokens(form: FormBody): TokenResult =
        try {
            val first = postToTokenEndpoint(form).use { classify(it) }
            if (first is TokenResult.Rejected && first.error == ERROR_USE_DPOP_NONCE) {
                KrtLog.d(LOG_TAG) { "realm demanded a DPoP nonce, retrying once" }
                postToTokenEndpoint(form).use { classify(it) }
            } else {
                first
            }
        } catch (io: IOException) {
            // Logged, because Unreachable is the one outcome that tells the member nothing useful
            // ("you are offline") and can just as easily mean a blocked cleartext connection, a
            // wrong port or a TLS failure. Without this line the difference is invisible on device.
            KrtLog.w(LOG_TAG, io) { "token request did not reach the realm" }
            TokenResult.Unreachable(io)
        }

    /**
     * Posts to the token endpoint with a freshly minted proof, capturing any nonce on the way back.
     *
     * @param form the grant parameters
     * @return the raw response; the caller closes it
     * @throws IOException if the call never produced a response
     */
    private suspend fun postToTokenEndpoint(form: FormBody): Response {
        val proof =
            proofFactory.createProof(
                httpMethod = METHOD_POST,
                httpUri = configuration.tokenEndpoint,
                nonce = nonce.get(),
            )
        val response = post(configuration.tokenEndpoint, form, proof)
        response.header(HEADER_DPOP_NONCE)?.let(nonce::set)
        return response
    }

    /**
     * Executes one form POST.
     *
     * @param url the endpoint
     * @param body the form
     * @param proof the DPoP proof, or `null` where none belongs
     * @return the raw response; the caller closes it
     * @throws IOException if the call never produced a response
     */
    private suspend fun post(
        url: String,
        body: RequestBody,
        proof: String?,
    ): Response {
        val request =
            Request
                .Builder()
                .url(url)
                .header(HEADER_ACCEPT, MEDIA_TYPE_JSON)
                .apply { proof?.let { header(HEADER_DPOP, it) } }
                .post(body)
                .build()
        return httpClient.newCall(request).await()
    }

    /**
     * Turns one response into a [TokenResult].
     *
     * @param response the response to read
     * @return the outcome
     */
    private fun classify(response: Response): TokenResult {
        val body = readBody(response)
        return if (response.isSuccessful) granted(body) else rejected(body, response.code)
    }

    /**
     * Interprets a 2xx token response.
     *
     * @param body the response body
     * @return [TokenResult.Granted], or the state describing why it is not usable
     */
    private fun granted(body: String): TokenResult {
        val grant =
            parse(TokenResponseBody.serializer(), body)
                ?: return TokenResult.Malformed("token endpoint answered 2xx with a body that is not a grant")
        return if (!grant.tokenType.equals(TOKEN_TYPE_BEARER, ignoreCase = true)) {
            // The realm bound the access token. Named here so the 401 storm that would follow is
            // not mistaken for an app defect; see TokenResult.AccessTokenBound.
            KrtLog.e(LOG_TAG) { "realm issued token_type=${grant.tokenType}, expected $TOKEN_TYPE_BEARER" }
            TokenResult.AccessTokenBound(grant.tokenType)
        } else {
            TokenResult.Granted(grant.toTokenSet(serverClock.now()))
        }
    }

    /**
     * Interprets a non-2xx token response.
     *
     * @param body the response body
     * @param status the HTTP status, used when the body carries no OAuth error at all
     * @return the matching failure state
     */
    private fun rejected(
        body: String,
        status: Int,
    ): TokenResult {
        val error = parse(TokenErrorBody.serializer(), body)?.takeIf { it.error.isNotBlank() }
        KrtLog.w(LOG_TAG) { "token request refused: HTTP $status, error=${error?.error ?: "none"}" }
        return when (error?.error) {
            null -> TokenResult.Rejected("http_$status", null)
            ERROR_INVALID_GRANT -> TokenResult.SessionEnded(error.description)
            else -> TokenResult.Rejected(error.error, error.description)
        }
    }

    /**
     * Reads a response body, treating an unreadable one as empty.
     *
     * @param response the response to read
     * @return the body text, or an empty string
     */
    private fun readBody(response: Response): String =
        try {
            response.body.string()
        } catch (io: IOException) {
            KrtLog.w(LOG_TAG, io) { "token response body unreadable" }
            ""
        }

    /**
     * Parses a JSON body, treating anything unparseable as absent.
     *
     * @param T the target type
     * @param deserializer the serializer for [T]
     * @param body the raw body
     * @return the parsed value, or `null` when the body is not that JSON
     */
    private fun <T> parse(
        deserializer: DeserializationStrategy<T>,
        body: String,
    ): T? =
        try {
            body.takeIf { it.isNotBlank() }?.let { json.decodeFromString(deserializer, it) }
        } catch (malformed: IllegalArgumentException) {
            KrtLog.d(LOG_TAG) { "token endpoint body was not the expected JSON: ${malformed.javaClass.simpleName}" }
            null
        }

    private companion object {
        /** Log subsystem; token material never appears in a message. */
        const val LOG_TAG = "auth"

        const val METHOD_POST = "POST"

        const val HEADER_ACCEPT = "Accept"
        const val HEADER_DPOP = "DPoP"
        const val HEADER_DPOP_NONCE = "DPoP-Nonce"
        const val MEDIA_TYPE_JSON = "application/json"

        const val PARAM_GRANT_TYPE = "grant_type"
        const val PARAM_CLIENT_ID = "client_id"
        const val PARAM_CODE = "code"
        const val PARAM_CODE_VERIFIER = "code_verifier"
        const val PARAM_REDIRECT_URI = "redirect_uri"
        const val PARAM_REFRESH_TOKEN = "refresh_token"
        const val PARAM_TOKEN = "token"
        const val PARAM_TOKEN_TYPE_HINT = "token_type_hint"
        const val PARAM_ID_TOKEN_HINT = "id_token_hint"
        const val PARAM_POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri"

        const val GRANT_AUTHORIZATION_CODE = "authorization_code"
        const val GRANT_REFRESH_TOKEN = "refresh_token"
        const val TOKEN_TYPE_HINT_REFRESH = "refresh_token"

        /** The only `token_type` compatible with the backend's plain-Bearer resource server. */
        const val TOKEN_TYPE_BEARER = "Bearer"

        /** RFC 9449 §8.3: the realm wants the request repeated with the nonce it just issued. */
        const val ERROR_USE_DPOP_NONCE = "use_dpop_nonce"

        /** RFC 6749 §5.2: the grant is gone — expired, revoked, already used, or key-mismatched. */
        const val ERROR_INVALID_GRANT = "invalid_grant"

        /** Tolerates fields a realm upgrade adds; an unknown key must never fail a login. */
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The token endpoint's success body (RFC 6749 §5.1 plus Keycloak's additions).
 *
 * @property accessToken the bearer token
 * @property tokenType `Bearer` — anything else means the access token was DPoP-bound
 * @property expiresIn access-token lifetime in seconds; defaulted so a realm that omits it yields
 *   an immediately-stale token rather than a parse failure on the login path
 * @property refreshToken the refresh token, when one was issued
 * @property idToken the ID token, present whenever `openid` was requested
 * @property scope the granted scopes
 */
@Serializable
private data class TokenResponseBody(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    val scope: String? = null,
) {
    /**
     * Converts the wire body into the domain object, resolving `expires_in` against server time.
     *
     * @param now current server time
     * @return the token set
     */
    fun toTokenSet(now: Instant): TokenSet =
        TokenSet(
            accessToken = accessToken,
            accessTokenExpiresAt = now.plusSeconds(expiresIn),
            refreshToken = refreshToken,
            idToken = idToken,
            scope = scope,
        )
}

/**
 * The token endpoint's error body (RFC 6749 §5.2).
 *
 * @property error the error code; defaulted to empty so a body that is JSON but not an OAuth error
 *   is recognised as carrying no code rather than failing to parse
 * @property description the human-readable description, for the log
 */
@Serializable
private data class TokenErrorBody(
    val error: String = "",
    @SerialName("error_description") val description: String? = null,
)

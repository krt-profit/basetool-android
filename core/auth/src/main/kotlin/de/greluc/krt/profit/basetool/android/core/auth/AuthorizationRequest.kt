/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * One login attempt: the URL to open in the Custom Tab, and the three secrets that have to survive
 * until the browser comes back.
 *
 * All three are per-attempt and none may be reused. [state] ties the redirect to *this* attempt,
 * [PkceChallenge.verifier] is what redeems the code, and [nonce] ties the ID token to it. Keeping
 * them together in one object is what makes losing one of them a compile error rather than a
 * silently weakened login.
 *
 * @property url the absolute authorization URL for the browser
 * @property state CSRF value echoed by the realm; a redirect that does not carry it is not ours
 * @property nonce value the realm copies into the ID token
 * @property pkce the verifier/challenge pair for the code exchange
 */
data class AuthorizationRequest(
    val url: String,
    val state: String,
    val nonce: String,
    val pkce: PkceChallenge,
) {
    /**
     * Interprets the redirect the browser delivered.
     *
     * @param redirect the full redirect URI, exactly as it arrived
     * @return what the realm answered; see [AuthorizationResponse]
     */
    fun readRedirect(redirect: String): AuthorizationResponse {
        val parameters = queryOf(redirect)
        val returnedState = parameters[PARAM_STATE]
        val error = parameters[PARAM_ERROR]
        val code = parameters[PARAM_CODE]
        return when {
            // Checked before anything else is read: a redirect that is not ours must not be able
            // to steer the flow, not even into an error screen of its choosing.
            returnedState != state -> {
                KrtLog.w(LOG_TAG) { "authorization redirect carried a foreign state, ignoring it" }
                AuthorizationResponse.StateMismatch
            }

            error != null -> {
                AuthorizationResponse.Denied(error, parameters[PARAM_ERROR_DESCRIPTION])
            }

            code != null -> {
                AuthorizationResponse.Code(code)
            }

            else -> {
                AuthorizationResponse.Unusable("redirect carried neither a code nor an error")
            }
        }
    }

    /**
     * Splits a redirect's query string.
     *
     * Deliberately string-level rather than `HttpUrl`: production redirects to a verified App Link
     * (`https://…`), but the dev realm registers the custom scheme `de.kartell.basetool:/…`, which
     * `HttpUrl` refuses to parse at all. A URL parser here would make every dev-flavour login fail
     * with "redirect is not a URL" — on the build the login flow is developed against.
     *
     * @param redirect the redirect URI as delivered
     * @return its decoded query parameters; empty when there are none
     */
    private fun queryOf(redirect: String): Map<String, String> =
        redirect
            .substringAfter('?', "")
            .substringBefore('#')
            .split("&")
            .filter { it.contains("=") }
            .associate { pair ->
                val (name, value) = pair.split("=", limit = 2)
                // The (String, Charset) overload is API 33+ and minSdk is 30 — on a device at
                // the floor it would be a NoSuchMethodError at the moment of login.
                URLDecoder.decode(name, CHARSET_UTF_8) to URLDecoder.decode(value, CHARSET_UTF_8)
            }

    /**
     * Renders the attempt **without** the verifier or the URL that carries the challenge.
     *
     * @return a description safe to log
     */
    override fun toString(): String = "AuthorizationRequest(state=$state)"

    private companion object {
        const val LOG_TAG = "auth"

        /** Named rather than `Charsets.UTF_8`: the Charset overload of `decode` needs API 33. */
        const val CHARSET_UTF_8 = "UTF-8"
        const val PARAM_STATE = "state"
        const val PARAM_CODE = "code"
        const val PARAM_ERROR = "error"
        const val PARAM_ERROR_DESCRIPTION = "error_description"
    }
}

/**
 * Builds [AuthorizationRequest]s for one realm and client.
 *
 * @property configuration realm URLs, client id and the exact registered redirect
 * @property proofFactory supplies the DPoP key's thumbprint; the key itself stays inside it
 * @property random entropy for `state`, `nonce` and the PKCE verifier
 */
class AuthorizationRequestFactory(
    private val configuration: OidcConfiguration,
    private val proofFactory: DpopProofFactory,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Starts a login attempt.
     *
     * The request carries `dpop_jkt` (RFC 9449 §10) although the realm binds only the refresh
     * token: it names the key the eventual grant must be bound to *before* any token exists, which
     * closes the window in which an intercepted code could be redeemed against a different key.
     * Keycloak accepts the parameter under the refresh-only policy (security concept §4).
     *
     * @param scopes the scopes to request; the default is the set the client is configured for
     * @return the request to open, together with the secrets its redirect will be checked against
     */
    fun create(scopes: List<String> = DEFAULT_SCOPES): AuthorizationRequest {
        val pkce = PkceChallenge.generate(random)
        val state = randomValue()
        val nonce = randomValue()
        val url =
            configuration.authorizationEndpoint
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter(PARAM_RESPONSE_TYPE, RESPONSE_TYPE_CODE)
                .addQueryParameter(PARAM_CLIENT_ID, configuration.clientId)
                .addQueryParameter(PARAM_REDIRECT_URI, configuration.redirectUri)
                .addQueryParameter(PARAM_SCOPE, scopes.joinToString(" "))
                .addQueryParameter(PARAM_STATE, state)
                .addQueryParameter(PARAM_NONCE, nonce)
                .addQueryParameter(PARAM_CODE_CHALLENGE, pkce.challenge)
                .addQueryParameter(PARAM_CODE_CHALLENGE_METHOD, CODE_CHALLENGE_S256)
                .addQueryParameter(PARAM_DPOP_JKT, proofFactory.publicKeyThumbprint())
                .build()
                .toString()
        return AuthorizationRequest(url = url, state = state, nonce = nonce, pkce = pkce)
    }

    /**
     * Mints one opaque, single-use value.
     *
     * @return 256 bits of entropy, base64url-encoded
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun randomValue(): String {
        val bytes = ByteArray(RANDOM_BYTES).also(random::nextBytes)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }

    private companion object {
        /** Entropy behind `state` and `nonce`; the same 256 bits PKCE uses. */
        const val RANDOM_BYTES = 32

        /**
         * What the client asks for.
         *
         * `offline_access` is deliberately absent: the client is not configured for it, and an
         * offline token would outlive the SSO session the revocation levers act on (security
         * concept §3).
         */
        val DEFAULT_SCOPES = listOf("openid", "profile", "email", "roles")

        const val PARAM_RESPONSE_TYPE = "response_type"
        const val PARAM_CLIENT_ID = "client_id"
        const val PARAM_REDIRECT_URI = "redirect_uri"
        const val PARAM_SCOPE = "scope"
        const val PARAM_STATE = "state"
        const val PARAM_NONCE = "nonce"
        const val PARAM_CODE_CHALLENGE = "code_challenge"
        const val PARAM_CODE_CHALLENGE_METHOD = "code_challenge_method"
        const val PARAM_DPOP_JKT = "dpop_jkt"

        const val RESPONSE_TYPE_CODE = "code"
        const val CODE_CHALLENGE_S256 = "S256"
    }
}

/**
 * What came back from the browser.
 *
 * The distinctions are the ones that lead somewhere different: a code continues the flow, a denial
 * returns the member to the login screen with a reason, and the two remaining cases are redirects
 * that should never be acted on at all.
 */
sealed interface AuthorizationResponse {
    /**
     * The realm issued an authorization code.
     *
     * @property code the code, to be redeemed with the request's PKCE verifier
     */
    data class Code(
        val code: String,
    ) : AuthorizationResponse

    /**
     * The realm refused, or the member cancelled.
     *
     * @property error the OAuth error code, e.g. `access_denied`
     * @property description the realm's description, for the log
     */
    data class Denied(
        val error: String,
        val description: String?,
    ) : AuthorizationResponse

    /**
     * The redirect did not carry this attempt's `state`.
     *
     * Either a stale redirect from an abandoned attempt or someone else's — a login must not be
     * completed from a redirect the app did not start, which is the whole job of `state`.
     */
    data object StateMismatch : AuthorizationResponse

    /**
     * The redirect is ours but carries nothing to act on.
     *
     * @property reason what was missing
     */
    data class Unusable(
        val reason: String,
    ) : AuthorizationResponse
}

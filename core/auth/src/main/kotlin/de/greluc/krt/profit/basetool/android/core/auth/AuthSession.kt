/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.network.AccessTokenProvider
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The signed-in session: the in-memory access token, its single-flight refresh, and logout.
 *
 * The access token is a field so [AccessTokenProvider] can be read synchronously from an OkHttp
 * interceptor (ADR-0001); refreshing is a separate suspending call made before a request. Only
 * `invalid_grant` ends a session: a transport failure yields [SessionState.Stale] at start-up and
 * leaves an established session standing.
 *
 * @property tokenClient talks to the realm's token endpoint
 * @property refreshTokenStore the encrypted refresh token at rest
 * @property cipher the Keystore-backed cipher; its key is deleted on logout
 * @property serverClock decides when the access token counts as spent
 */
class AuthSession(
    private val tokenClient: TokenClient,
    private val refreshTokenStore: RefreshTokenStore,
    private val cipher: SecretCipher,
    private val serverClock: ServerClock,
) : AccessTokenProvider {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Unknown)

    /** The session as the UI should render it. */
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    /**
     * The live token set.
     *
     * `@Volatile` because [currentAccessToken] is read from OkHttp's network threads while
     * refreshes write it from a coroutine.
     */
    @Volatile
    private var tokens: TokenSet? = null

    private val refreshMutex = Mutex()

    override fun currentAccessToken(): String? = tokens?.accessToken

    /**
     * Restores the session from the stored refresh token, if there is one.
     *
     * Called once at start-up; a stored token that no longer works is cleared here.
     *
     * @return the resulting state, also published on [state]
     */
    suspend fun restore(): SessionState =
        refreshMutex.withLock {
            val current = mutableState.value
            if (current is SessionState.SignedIn && !needsRefresh()) {
                return@withLock current
            }
            when (val stored = refreshTokenStore.read()) {
                is StoredRefreshToken.Present -> {
                    publish(
                        stateFor(
                            tokenClient.refresh(stored.token),
                            previousRefreshToken = stored.token,
                        ),
                    )
                }

                StoredRefreshToken.Absent -> {
                    publish(SessionState.SignedOut)
                }

                StoredRefreshToken.Locked -> {
                    KrtLog.i(LOG_TAG) { "session cannot be restored while the app lock is closed" }
                    mutableState.value
                }
            }
        }

    /**
     * Completes a login by redeeming the authorization code, checking the ID token's `nonce` against the one this
     * attempt sent.
     *
     * @param request the attempt the redirect belongs to; supplies the PKCE verifier and the nonce
     * @param code the authorization code from the redirect
     * @return what happened; only [LoginResult.SignedIn] establishes a session
     */
    suspend fun completeLogin(
        request: AuthorizationRequest,
        code: String,
    ): LoginResult {
        val result = tokenClient.exchangeCode(code = code, codeVerifier = request.pkce.verifier)
        if (result !is TokenResult.Granted) {
            publish(SessionState.SignedOut)
            return LoginResult.Failed(result)
        }
        val claims = result.tokens.idToken?.let(IdTokenClaims::parse)
        return if (claims?.nonce != request.nonce) {
            KrtLog.e(LOG_TAG) { "ID token nonce does not match the authorization request" }
            publish(SessionState.SignedOut)
            LoginResult.NonceMismatch
        } else {
            adopt(result.tokens, previousRefreshToken = null)
            publish(SessionState.SignedIn(claims))
            LoginResult.SignedIn(claims)
        }
    }

    /**
     * Refreshes the access token if it is spent, at most once at a time.
     *
     * Call it before a batch of API calls, not inside the synchronous interceptor.
     *
     * @return the refresh outcome, or `null` when the current token was still good and nothing was
     *   sent
     */
    suspend fun refreshIfNeeded(): TokenResult? {
        if (!needsRefresh()) return null
        return refreshMutex.withLock {
            if (!needsRefresh()) {
                null
            } else {
                when (val stored = storedRefreshToken()) {
                    is StoredRefreshToken.Present -> {
                        val result = tokenClient.refresh(stored.token)
                        publish(stateFor(result, previousRefreshToken = stored.token))
                        result
                    }

                    StoredRefreshToken.Absent -> {
                        publish(SessionState.SignedOut)
                        null
                    }

                    StoredRefreshToken.Locked -> {
                        null
                    }
                }
            }
        }
    }

    /**
     * Renews the access token the server has just refused, skipping the local freshness check of [refreshIfNeeded].
     *
     * @param refused the rejected token; when the session already holds a different one, that one is
     *   returned without a new refresh
     * @return a usable access token, or `null` when the session could not be renewed
     */
    suspend fun refreshFor(refused: String?): String? =
        refreshMutex.withLock {
            val current = tokens?.accessToken
            if (current != null && current != refused) {
                return@withLock current
            }
            val stored = storedRefreshToken()
            if (stored is StoredRefreshToken.Locked) {
                return@withLock null
            }
            if (stored !is StoredRefreshToken.Present) {
                publish(SessionState.SignedOut)
                return@withLock null
            }
            val result = tokenClient.refresh(stored.token)
            publish(stateFor(result, previousRefreshToken = stored.token))
            (result as? TokenResult.Granted)?.tokens?.accessToken
        }

    /**
     * Ends the session and returns the URL that ends it at the realm too.
     *
     * Drops the in-memory state first, then attempts revocation, then wipes locally. Opening the returned
     * URL ends the realm's SSO cookie.
     *
     * @return the RP-initiated logout URL to open in a browser, or `null` when no ID token was held
     */
    suspend fun logout(): String? {
        val ending = tokens
        tokens = null
        publish(SessionState.SignedOut)

        val refreshToken = ending?.refreshToken ?: refreshTokenStore.readTokenOrNull()
        if (refreshToken != null) {
            tokenClient.revokeRefreshToken(refreshToken)
        }
        refreshTokenStore.clear()
        cipher.deleteKey()

        return ending?.idToken?.let(tokenClient::endSessionUri)
    }

    /**
     * The refresh token this session should use, from memory first and the store second.
     *
     * @return the in-memory token wrapped as [StoredRefreshToken.Present] when there is one, else
     *   whatever the store says — including [StoredRefreshToken.Locked], which callers must not
     *   read as "signed out"
     */
    private suspend fun storedRefreshToken(): StoredRefreshToken =
        tokens?.refreshToken?.let(StoredRefreshToken::Present) ?: refreshTokenStore.read()

    /**
     * Whether the held access token is missing or about to expire.
     *
     * @return `true` when a refresh is due
     */
    private fun needsRefresh(): Boolean {
        val current = tokens ?: return true
        return current.needsRefresh(serverClock.now())
    }

    /**
     * Turns a token-endpoint outcome into a session state, storing the grant when there is one.
     *
     * @param result what the token endpoint answered
     * @param previousRefreshToken the token that was spent, kept when the response carries no new
     *   one
     * @return the state to publish
     */
    private suspend fun stateFor(
        result: TokenResult,
        previousRefreshToken: String?,
    ): SessionState =
        when (result) {
            is TokenResult.Granted -> {
                adopt(result.tokens, previousRefreshToken)
                SessionState.SignedIn(result.tokens.idToken?.let(IdTokenClaims::parse))
            }

            is TokenResult.SessionEnded -> {
                KrtLog.i(LOG_TAG) { "refresh token is no longer accepted, signing out" }
                tokens = null
                refreshTokenStore.clear()
                SessionState.SignedOut
            }

            else -> {
                KrtLog.w(LOG_TAG) { "session could not be refreshed: ${result::class.simpleName}" }
                mutableState.value as? SessionState.SignedIn ?: SessionState.Stale(result)
            }
        }

    /**
     * Takes a fresh grant into memory and onto disk.
     *
     * A response without `refresh_token` keeps the previous one, since the realm does not rotate them
     * (REQ-SEC-012).
     *
     * @param granted the new token set
     * @param previousRefreshToken the refresh token in play before this exchange
     */
    private suspend fun adopt(
        granted: TokenSet,
        previousRefreshToken: String?,
    ) {
        val effective = granted.copy(refreshToken = granted.refreshToken ?: previousRefreshToken)
        tokens = effective
        effective.refreshToken?.let { refreshTokenStore.write(it) }
    }

    /**
     * Publishes a state.
     *
     * @param next the new state
     * @return [next], so callers can publish and return in one step
     */
    private fun publish(next: SessionState): SessionState {
        mutableState.value = next
        return next
    }

    private companion object {
        /** Log subsystem; token material never appears in a message. */
        const val LOG_TAG = "auth"
    }
}

/**
 * What the app knows about the member's session.
 *
 * [Stale] is the state that stops a tunnel from becoming a logout: a stored session exists and
 * could not be proven right now. The UI shows a retry, not a password prompt.
 */
sealed interface SessionState {
    /** Nothing has been read yet — the start-up state, before [AuthSession.restore]. */
    data object Unknown : SessionState

    /** No usable session: never logged in, logged out, or the realm refused the refresh token. */
    data object SignedOut : SessionState

    /**
     * A live session.
     *
     * @property claims the ID token's claims, or `null` when the realm sent no readable ID token
     */
    data class SignedIn(
        val claims: IdTokenClaims?,
    ) : SessionState

    /**
     * A stored session that could not be proven at start-up, for a reason that is not a refusal.
     *
     * Not reachable from [SignedIn]: a failed refresh leaves an established session alone.
     *
     * @property cause the token-endpoint outcome, typically [TokenResult.Unreachable]
     */
    data class Stale(
        val cause: TokenResult,
    ) : SessionState
}

/**
 * The outcome of redeeming an authorization code.
 */
sealed interface LoginResult {
    /**
     * A session was established.
     *
     * @property claims the ID token's claims
     */
    data class SignedIn(
        val claims: IdTokenClaims?,
    ) : LoginResult

    /**
     * The realm did not grant tokens.
     *
     * @property reason the token-endpoint outcome, never [TokenResult.Granted]
     */
    data class Failed(
        val reason: TokenResult,
    ) : LoginResult

    /**
     * Tokens were granted, but the ID token's `nonce` belongs to a different authorization request; nothing is stored
     * and no session starts.
     */
    data object NonceMismatch : LoginResult
}

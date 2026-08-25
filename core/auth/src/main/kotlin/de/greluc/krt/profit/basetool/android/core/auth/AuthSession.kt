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
 * The signed-in session: the access token in memory, the refresh that keeps it alive, and the
 * logout that takes it apart.
 *
 * **This is what makes [AccessTokenProvider] synchronous** (ADR-0001). The provider is read from an
 * OkHttp interceptor, which cannot suspend; wrapping a coroutine there would mean `runBlocking` on
 * a network thread. So the current access token is a field, and keeping it fresh is a separate,
 * suspending job the caller does before a request rather than inside one.
 *
 * **Refreshing is single-flight.** Several screens loading at once would otherwise each notice the
 * expiry and each start a refresh — several token requests where one belongs, each one a DPoP proof
 * the realm has to verify, and all but one of the results thrown away. The mutex plus the
 * re-check inside it means concurrent callers wait for the first refresh and then find it done.
 *
 * **Only `invalid_grant` ends a session.** A refresh that fails because the phone is on a train
 * leaves the stored token exactly where it is: wiping it would turn a tunnel into a logout, and the
 * member would be asked for a password they did not need. That distinction is [SessionState.Stale]
 * against [SessionState.SignedOut], and it is the reason the token client separates those states in
 * the first place.
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
     * Called once at start-up. A stored token that no longer works is cleared here rather than at
     * the first API call, so the member meets the login screen instead of an error.
     *
     * @return the resulting state, also published on [state]
     */
    suspend fun restore(): SessionState =
        when (val stored = refreshTokenStore.read()) {
            is StoredRefreshToken.Present -> {
                publish(
                    stateFor(tokenClient.refresh(stored.token), previousRefreshToken = stored.token),
                )
            }

            StoredRefreshToken.Absent -> {
                publish(SessionState.SignedOut)
            }

            StoredRefreshToken.Locked -> {
                // Nothing is decided here, and deliberately nothing is published: the session is
                // still unread, not ended. The gate calls this again once the member has
                // authenticated. Reaching this branch means a caller ran ahead of the lock, which
                // is worth a line because it used to be the silent half of a logout.
                KrtLog.i(LOG_TAG) { "session cannot be restored while the app lock is closed" }
                state.value
            }
        }

    /**
     * Completes a login by redeeming the authorization code.
     *
     * The ID token's `nonce` is checked against the one this attempt sent. Without that check the
     * `nonce` parameter is decoration: it exists so a token minted for a *different* authorization
     * request cannot be injected into this one.
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
            // Either a replayed ID token or a realm that dropped the nonce. Both mean this token
            // was not minted for this login, so nothing is stored and no session starts.
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
     * Call it before a batch of API calls, not inside the interceptor: the interceptor is
     * synchronous, and this suspends.
     *
     * @return the refresh outcome, or `null` when the current token was still good and nothing was
     *   sent
     */
    suspend fun refreshIfNeeded(): TokenResult? {
        if (!needsRefresh()) return null
        return refreshMutex.withLock {
            // Re-checked inside the lock: by the time a queued caller gets here, the refresh it was
            // waiting for has usually already happened, and repeating it would defeat the point.
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

                    // The call that brought us here runs on an OkHttp thread, and on a cold start
                    // it can be a request made *above* the lock gate — the version check is one.
                    // Publishing SignedOut here is what logged a member out before they had even
                    // been offered the fingerprint prompt: the session was never read, so there is
                    // nothing to conclude. The request goes out unauthenticated and fails on its
                    // own terms, which is the honest outcome for a request nobody could have
                    // authorised yet.
                    StoredRefreshToken.Locked -> {
                        null
                    }
                }
            }
        }
    }

    /**
     * Renews the access token the server has just refused.
     *
     * The freshness check [refreshIfNeeded] makes is deliberately skipped: the server's `401` is a
     * harder fact than the local expiry estimate, and the two disagree whenever the device clock is
     * off or the token was revoked early.
     *
     * @param refused the token that was rejected. When the session already holds a different one,
     *   another caller refreshed while this one was in flight and that token is returned unused —
     *   which is what keeps a burst of parallel 401s to a single refresh.
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
                // Same reasoning as refreshIfNeeded: a sealed token is not an ended session.
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
     * The order is deliberate. The in-memory state is dropped **first**, so "log out" is instant
     * and cannot be undone by a slow network; the revocation is attempted next, while the token is
     * still known; the local wipe follows. Opening the returned URL is what kills the realm's SSO
     * cookie — without it the next login silently reuses the browser session, and "log out, then
     * log in as someone else" does not work.
     *
     * @return the RP-initiated logout URL to open in a browser, or `null` when no ID token was held
     *   and there is therefore nothing to end
     */
    suspend fun logout(): String? {
        val ending = tokens
        tokens = null
        publish(SessionState.SignedOut)

        // Sealed or absent are the same thing here: there is no token to revoke, and the wipe
        // below happens either way. A logout must never depend on the lock being open.
        val refreshToken = ending?.refreshToken ?: refreshTokenStore.readTokenOrNull()
        if (refreshToken != null) {
            tokenClient.revokeRefreshToken(refreshToken)
        }
        refreshTokenStore.clear()
        // The key as well as the blob: a key left behind can decrypt any copy of the ciphertext
        // that escaped, and a wipe should not depend on having found every copy.
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
                // The only outcome that destroys anything. The grant is gone at the realm; keeping
                // the blob would just fail again on every start-up.
                KrtLog.i(LOG_TAG) { "refresh token is no longer accepted, signing out" }
                tokens = null
                refreshTokenStore.clear()
                SessionState.SignedOut
            }

            else -> {
                // Offline, a realm outage, a misconfiguration. The session may well still be
                // valid, so nothing is cleared and the UI is told it cannot currently tell.
                KrtLog.w(LOG_TAG) { "session could not be refreshed: ${result::class.simpleName}" }
                SessionState.Stale(result)
            }
        }

    /**
     * Takes a fresh grant into memory and onto disk.
     *
     * A refresh response that carries no `refresh_token` keeps the one that was spent — the realm
     * does not rotate them (main repo REQ-SEC-012), and overwriting the field with `null` would
     * throw away the only way back into the session.
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
     * A stored session that could not be refreshed for a reason that is not a refusal.
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
     * Tokens were granted, but the ID token belongs to a different authorization request.
     *
     * Nothing is stored and no session starts: an ID token whose `nonce` does not match was not
     * minted for this login, and accepting it is the injection the nonce exists to prevent.
     */
    data object NonceMismatch : LoginResult
}

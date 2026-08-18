/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

/**
 * The outcome of a token request, as a state rather than an exception.
 *
 * The distinctions here are the ones that lead to different behaviour, and each of them was a
 * plausible silent failure first:
 *
 * - [SessionEnded] and [Rejected] both arrive as HTTP 400. Only the first means "show the login
 *   screen"; treating the second the same way turns a realm misconfiguration into an infinite
 *   login loop that looks like the member's fault.
 * - [AccessTokenBound] is a *successful* token response. It is separated out because it is the
 *   documented way the whole DPoP posture breaks (security concept §4, constraint 3) and its
 *   natural symptom — every API call 401s — points at the wrong component entirely.
 * - [Unreachable] means no HTTP response existed, which is the only state that should read as
 *   "you are offline".
 */
sealed interface TokenResult {
    /**
     * The realm issued a grant.
     *
     * @property tokens the new token set
     */
    data class Granted(
        val tokens: TokenSet,
    ) : TokenResult

    /**
     * The realm refused the grant with `invalid_grant` — the session is over.
     *
     * Reached when the refresh token expired, the SSO session was ended elsewhere, an admin
     * revoked it, or a proof was signed by a key the refresh token is not bound to. The app's only
     * answer to all of them is a fresh login, so they are one state.
     *
     * @property reason the realm's `error_description`, for the log — never shown verbatim
     */
    data class SessionEnded(
        val reason: String?,
    ) : TokenResult

    /**
     * The realm answered 2xx but bound the access token, which the backend will reject.
     *
     * `token_type` other than `Bearer` means the per-client "Require DPoP bound tokens" switch is
     * on and overrides the refresh-only client policy. Spring Security's bearer filter rejects an
     * access token carrying `cnf.jkt`, so every subsequent API call would fail with a 401 that
     * looks like an app bug. Failing loudly here names the actual cause.
     *
     * @property tokenType the type the realm returned, e.g. `DPoP`
     */
    data class AccessTokenBound(
        val tokenType: String,
    ) : TokenResult

    /**
     * The realm refused the request for a reason the app cannot resolve by re-authenticating.
     *
     * @property error the OAuth 2.0 error code, e.g. `invalid_client`, `unauthorized_client`
     * @property description the realm's description, for the log
     */
    data class Rejected(
        val error: String,
        val description: String?,
    ) : TokenResult

    /**
     * Something answered, but not the token endpoint.
     *
     * A captive portal's 200-with-HTML is the case that matters: parsed leniently it would look
     * like a grant with no tokens in it, and the failure would surface much later as an empty
     * session.
     *
     * @property reason what could not be made sense of
     */
    data class Malformed(
        val reason: String,
    ) : TokenResult

    /**
     * No HTTP response — DNS, TLS, connectivity or timeout.
     *
     * @property cause the underlying I/O failure
     */
    data class Unreachable(
        val cause: Throwable,
    ) : TokenResult
}

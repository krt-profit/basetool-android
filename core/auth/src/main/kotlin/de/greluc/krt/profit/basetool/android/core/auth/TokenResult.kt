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
 * - [SessionEnded] and [Rejected] both arrive as HTTP 400; only the first means "show the login screen".
 * - [AccessTokenBound] is a successful response whose DPoP-bound access token the backend would reject.
 * - [Unreachable] means no HTTP response existed, the only state that reads as offline.
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
     * The realm refused the grant with `invalid_grant`: the session is over and the member must log in again.
     *
     * @property reason the realm's `error_description`, for the log — never shown verbatim
     */
    data class SessionEnded(
        val reason: String?,
    ) : TokenResult

    /**
     * The realm answered 2xx but DPoP-bound the access token (`token_type` other than `Bearer`), which the backend's
     * bearer filter rejects.
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
     * Something answered, but not the token endpoint, e.g. a captive portal's HTML page.
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

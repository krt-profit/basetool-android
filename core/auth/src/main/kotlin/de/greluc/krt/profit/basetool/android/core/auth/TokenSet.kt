/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import java.time.Duration
import java.time.Instant

/**
 * One grant, as the token endpoint returned it.
 *
 * Only [refreshToken] is ever persisted (ADR-0002); the access token lives in memory for its five
 * minutes and the ID token for as long as the session needs its claims.
 *
 * [accessTokenExpiresAt] is an absolute instant on the **server's** clock rather than the
 * `expires_in` duration the wire carries. A duration is only meaningful together with the moment it
 * was received, and every caller that would have to pair the two is a caller that can get it wrong.
 *
 * @property accessToken the bearer token for API calls — plain `Bearer`, never DPoP-bound
 * @property accessTokenExpiresAt when the access token stops being accepted, in server time
 * @property refreshToken the token to exchange for the next access token, or `null` if the realm
 *   issued none
 * @property idToken source of the member's profile claims; `/userinfo` must not be called
 * @property scope the granted scopes, as returned
 */
data class TokenSet(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String?,
    val idToken: String?,
    val scope: String?,
) {
    /**
     * Whether the access token should be replaced before the next call goes out.
     *
     * @param now current server time, from `ServerClock`
     * @param margin how long before actual expiry to consider the token spent; the default absorbs
     *   the round trip of the request the token would be used on
     * @return `true` when a refresh is due
     */
    fun needsRefresh(
        now: Instant,
        margin: Duration = DEFAULT_REFRESH_MARGIN,
    ): Boolean = !now.plus(margin).isBefore(accessTokenExpiresAt)

    /**
     * Renders the grant **without** any token material.
     *
     * The generated `toString` of a data class prints every property, which would put an access
     * token into any log line, crash report or debugger transcript that touches this object. The
     * app's logging rule (main repo REQ-OBS-004, inherited by contract) says never — and the
     * cheapest way to keep a rule is to make the material unavailable.
     *
     * @return a description carrying only shape and timing
     */
    override fun toString(): String =
        "TokenSet(expiresAt=$accessTokenExpiresAt, scope=$scope, " +
            "refreshToken=${present(refreshToken)}, idToken=${present(idToken)})"

    /**
     * Describes a token's presence without revealing it.
     *
     * @param token the token to describe
     * @return `present` or `absent`
     */
    private fun present(token: String?): String = if (token == null) "absent" else "present"

    companion object {
        /**
         * Default refresh margin.
         *
         * The realm issues 300 s access tokens; refreshing 30 s early costs one extra token call
         * per ten minutes of use and removes the race where a token expires while its request is
         * in flight.
         */
        val DEFAULT_REFRESH_MARGIN: Duration = Duration.ofSeconds(30)
    }
}

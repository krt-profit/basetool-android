/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import okhttp3.OkHttpClient
import java.time.Duration

/**
 * Builds the one [OkHttpClient] the app talks to the Basetool API with.
 *
 * Two properties of this client are decisions rather than defaults:
 *
 * - **No HTTP cache.** OkHttp installs none unless asked, and none is asked for deliberately
 *   (security concept §4): the read cache is meant to be the only persistence layer for member
 *   data, because it is the one that is backup-excluded, wiped on logout and clearable from
 *   settings. A disk cache here would be a second copy of the same data outside every wipe path.
 *   The server mirrors the intent with `no-store` on its sensitive reads (main repo REQ-SEC-031).
 * - **Interceptor order.** [ServerTimeInterceptor] is added first so it observes every response,
 *   including ones a later stage rejects; [TokenRefreshInterceptor] follows, so a token it renews
 *   is in place before the headers are written and again on its retry; [MandatoryHeadersInterceptor]
 *   is last, so the headers it adds sit on the request that actually goes out.
 *
 * The timeouts are short on purpose. This is a foreground-only app (no push channel, decision Q2),
 * so a request nobody is waiting for does not exist, and a member watching a spinner is better
 * served by a quick failure they can retry than by a socket that hangs for a minute.
 */
object KrtHttpClient {
    /** Time allowed to establish the TCP and TLS connection. */
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)

    /** Time allowed to read the response once the request is out. */
    private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

    /** Time allowed to write the request body. */
    private val WRITE_TIMEOUT: Duration = Duration.ofSeconds(30)

    /**
     * Creates the API client.
     *
     * @param serverClock updated from every response's `Date` header, for DPoP proof timing
     * @param accessTokenProvider supplies the bearer token, or `null` when anonymous
     * @param correlationIdFactory mints one correlation id per request
     * @param languageTagProvider decides the language of localised error bodies
     * @param activeOrgUnitProvider supplies the org-unit pin, or `null`
     * @param refreshIfSpent renews an access token that is at or near expiry, before the call goes
     *   out; the default does nothing, which is right for a client with no session behind it
     * @param refreshAfterRejection renews the token the server answered `401` to and returns a
     *   usable one, or `null` when the session is over; the default gives up, which turns the
     *   rejection into the ordinary "not signed in" state
     * @return a client with no cache, the three app interceptors and the timeouts above
     */
    fun create(
        serverClock: ServerClock,
        accessTokenProvider: AccessTokenProvider,
        correlationIdFactory: CorrelationIdFactory,
        languageTagProvider: LanguageTagProvider,
        activeOrgUnitProvider: ActiveOrgUnitProvider,
        refreshIfSpent: () -> Unit = {},
        refreshAfterRejection: (String?) -> String? = { null },
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .writeTimeout(WRITE_TIMEOUT)
            .retryOnConnectionFailure(true)
            .addInterceptor(ServerTimeInterceptor(serverClock))
            .addInterceptor(
                TokenRefreshInterceptor(
                    currentToken = { accessTokenProvider.currentAccessToken() },
                    refreshIfSpent = refreshIfSpent,
                    refreshAfterRejection = refreshAfterRejection,
                ),
            ).addInterceptor(
                MandatoryHeadersInterceptor(
                    accessTokenProvider = accessTokenProvider,
                    correlationIdFactory = correlationIdFactory,
                    languageTagProvider = languageTagProvider,
                    activeOrgUnitProvider = activeOrgUnitProvider,
                ),
            ).build()

    /**
     * Derives the client used for Keycloak's token, revocation and logout endpoints.
     *
     * **The API client cannot be reused as-is, and the reason is a login failure rather than a
     * matter of taste.** [MandatoryHeadersInterceptor] attaches `Authorization: Bearer <access
     * token>` to every request it sees. Keycloak reads an `Authorization` header on the token
     * endpoint as an attempt at client authentication and answers `invalid_client` — so a session
     * refresh would work exactly once, on the first login, and fail forever after. The other three
     * headers are merely meaningless there: the correlation id is the *backend's* log join key, and
     * the org-unit pin is a Basetool concept the realm has never heard of.
     *
     * What is kept is everything that costs something to duplicate — the connection pool, the
     * dispatcher and the timeouts all come from [api] — so token calls reuse a warm TLS connection
     * instead of opening a second one to the same host.
     *
     * [ServerTimeInterceptor] is re-added deliberately, and it matters more here than on the API
     * client: the clock a DPoP proof must agree with is **Keycloak's**, and this is the only
     * traffic that observes it directly.
     *
     * @param api the API client to derive from
     * @param serverClock the same clock instance the proof factory reads
     * @return a client that sends exactly the headers a token request should carry
     */
    fun createTokenClient(
        api: OkHttpClient,
        serverClock: ServerClock,
    ): OkHttpClient =
        api
            .newBuilder()
            .apply { interceptors().clear() }
            .addInterceptor(ServerTimeInterceptor(serverClock))
            .build()
}

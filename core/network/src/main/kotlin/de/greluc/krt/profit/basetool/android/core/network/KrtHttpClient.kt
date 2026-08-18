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
 *   including ones a later stage rejects; [MandatoryHeadersInterceptor] follows so the headers it
 *   adds sit on the request that actually goes out.
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
     * @return a client with no cache, the two app interceptors and the timeouts above
     */
    fun create(
        serverClock: ServerClock,
        accessTokenProvider: AccessTokenProvider,
        correlationIdFactory: CorrelationIdFactory,
        languageTagProvider: LanguageTagProvider,
        activeOrgUnitProvider: ActiveOrgUnitProvider,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .writeTimeout(WRITE_TIMEOUT)
            .retryOnConnectionFailure(true)
            .addInterceptor(ServerTimeInterceptor(serverClock))
            .addInterceptor(
                MandatoryHeadersInterceptor(
                    accessTokenProvider = accessTokenProvider,
                    correlationIdFactory = correlationIdFactory,
                    languageTagProvider = languageTagProvider,
                    activeOrgUnitProvider = activeOrgUnitProvider,
                ),
            ).build()
}

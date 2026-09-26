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
 * - No HTTP cache, so no member data persists outside the wipeable read cache.
 * - Interceptor order: [ServerTimeInterceptor], [TokenRefreshInterceptor],
 *   [MandatoryHeadersInterceptor], then [OneShotWriteInterceptor].
 * - `retryOnConnectionFailure` stays on, but never replays a write that may have been sent
 *   (REQ-APP-API-009).
 * - Timeouts are short, since every request is in the foreground.
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
     * @param refreshIfSpent renews an access token at or near expiry before the call; the default does
     *   nothing
     * @param refreshAfterRejection renews the token the server answered `401` to, or returns `null`
     *   when the session is over; the default gives up
     * @return a client with no cache, the four app interceptors and the configured timeouts
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
            ).addInterceptor(OneShotWriteInterceptor())
            .build()

    /**
     * Derives the client used for Keycloak's token, revocation and logout endpoints.
     *
     * Shares [api]'s connection pool, dispatcher and timeouts but drops the Basetool headers, since
     * Keycloak answers a bearer `Authorization` on its token endpoint with `invalid_client`. Keeps
     * [ServerTimeInterceptor] for DPoP timing and [OneShotWriteInterceptor] so token `POST`s are never
     * replayed.
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
            .addInterceptor(OneShotWriteInterceptor())
            .build()
}

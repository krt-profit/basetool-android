/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * How the two clients differ, and why the difference is not cosmetic.
 *
 * The API client and the token client talk to two different servers with two different ideas of
 * what a header means. Keycloak treats an `Authorization` header on its token endpoint as client
 * authentication and answers `invalid_client` — so an app that reused the API client for refreshes
 * would log in once and then be unable to renew a session, which is the kind of defect that only
 * appears after the access token's first five minutes.
 */
class KrtHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var clock: ServerClock

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        clock = ServerClock()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `the token client sends none of the api headers`() {
        val tokenClient = KrtHttpClient.createTokenClient(apiClient(), clock)
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

        tokenClient.newCall(Request.Builder().url(server.url("/realms/iri/token")).build()).execute().use { }

        val recorded = server.takeRequest()
        assertNull(
            "an Authorization header makes Keycloak answer invalid_client",
            recorded.headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION],
        )
        assertNull(recorded.headers[MandatoryHeadersInterceptor.HEADER_ACTIVE_ORG_UNIT])
        assertNull(recorded.headers[MandatoryHeadersInterceptor.HEADER_CORRELATION_ID])
    }

    @Test
    fun `the token client still observes the server clock`() {
        // This traffic is the only kind that observes *Keycloak's* clock, and Keycloak is the
        // party that judges a DPoP proof's iat. Dropping the interceptor here would leave the
        // proof timed against the backend's clock instead.
        val tokenClient = KrtHttpClient.createTokenClient(apiClient(), clock)
        val serverTime = Instant.now().plusSeconds(DRIFT_SECONDS)
        server.enqueue(MockResponse.Builder().code(HTTP_OK).setHeader("Date", httpDate(serverTime)).build())

        tokenClient.newCall(Request.Builder().url(server.url("/realms/iri/token")).build()).execute().use { }

        assertEquals(
            "the Date header of a token response must move the clock",
            DRIFT_SECONDS.toDouble(),
            clock.observedOffset().seconds.toDouble(),
            TOLERANCE_SECONDS.toDouble(),
        )
    }

    @Test
    fun `the api client keeps sending them`() {
        // The counterpart assertion: the token client's emptiness has to come from deriving it,
        // not from the API client having been misconfigured in the first place.
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

        apiClient().newCall(Request.Builder().url(server.url("/api/v1/me")).build()).execute().use { }

        val recorded = server.takeRequest()
        assertNotNull(recorded.headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION])
        assertNotNull(recorded.headers[MandatoryHeadersInterceptor.HEADER_CORRELATION_ID])
    }

    /**
     * Builds an API client whose providers all answer, so a missing header in the token client can
     * only come from the derivation.
     *
     * @return the API client
     */
    private fun apiClient(): OkHttpClient =
        KrtHttpClient.create(
            serverClock = clock,
            accessTokenProvider = { "jwt-value" },
            correlationIdFactory = { "corr-1" },
            languageTagProvider = { "de-DE" },
            activeOrgUnitProvider = { "org-42" },
        )

    /**
     * Formats an instant as an HTTP-date.
     *
     * @param instant the moment to format
     * @return the RFC 1123 representation used by the `Date` header
     */
    private fun httpDate(instant: Instant): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC))

    private companion object {
        const val HTTP_OK = 200

        /** A drift large enough that a dropped interceptor cannot pass as rounding. */
        const val DRIFT_SECONDS = 45L

        /** Slack for the wall-clock reads this assertion brackets. */
        const val TOLERANCE_SECONDS = 5L
    }
}

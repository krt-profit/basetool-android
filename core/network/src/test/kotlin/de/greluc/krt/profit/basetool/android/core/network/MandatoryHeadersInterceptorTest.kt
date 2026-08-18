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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Contract of the headers every Basetool API call must carry.
 *
 * Three of the four fail silently when missing — a dropped org-unit pin shows another squadron's
 * data, a dropped correlation id makes a member's report untraceable, a dropped `Accept-Language`
 * shows German errors to an English user — so each is asserted individually rather than through one
 * "happy path" request.
 */
class MandatoryHeadersInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    /**
     * Builds a client whose interceptor is fed by the given fakes.
     *
     * @param token bearer token, or `null` for an anonymous caller
     * @param orgUnit org-unit pin, or `null` when nothing is pinned
     * @return a client with only the interceptor under test installed
     */
    private fun clientWith(
        token: String?,
        orgUnit: String?,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                MandatoryHeadersInterceptor(
                    accessTokenProvider = { token },
                    correlationIdFactory = { "corr-1" },
                    languageTagProvider = { "de-DE" },
                    activeOrgUnitProvider = { orgUnit },
                ),
            ).build()

    @Test
    fun `sends all four headers for an authenticated call`() {
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())
        val client = clientWith(token = "jwt-value", orgUnit = "org-42")

        client.newCall(Request.Builder().url(server.url("/api/v1/me/capabilities")).build()).execute().use { }

        val recorded = server.takeRequest()
        assertEquals("Bearer jwt-value", recorded.headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION])
        assertEquals("org-42", recorded.headers[MandatoryHeadersInterceptor.HEADER_ACTIVE_ORG_UNIT])
        assertEquals("de-DE", recorded.headers[MandatoryHeadersInterceptor.HEADER_ACCEPT_LANGUAGE])
        assertEquals("corr-1", recorded.headers[MandatoryHeadersInterceptor.HEADER_CORRELATION_ID])
    }

    @Test
    fun `omits the authorization header entirely when there is no session`() {
        // Not an empty bearer: the anonymous endpoints treat a malformed Authorization as a failed
        // authentication attempt, which would turn a guest read into a 401.
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())
        val client = clientWith(token = null, orgUnit = "org-42")

        client.newCall(Request.Builder().url(server.url("/api/v1/terms/status")).build()).execute().use { }

        assertNull(server.takeRequest().headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION])
    }

    @Test
    fun `omits the org-unit pin before one has been chosen`() {
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())
        val client = clientWith(token = "jwt-value", orgUnit = null)

        client.newCall(Request.Builder().url(server.url("/api/v1/me/active-org-unit")).build()).execute().use { }

        assertNull(server.takeRequest().headers[MandatoryHeadersInterceptor.HEADER_ACTIVE_ORG_UNIT])
    }

    @Test
    fun `a header set by the caller wins`() {
        // The token exchange carries its own Authorization, and a retry reuses the correlation id
        // of the attempt it repeats. Overwriting either would break both.
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())
        val client = clientWith(token = "session-token", orgUnit = "org-42")

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/api/v1/terms/status"))
                    .header(MandatoryHeadersInterceptor.HEADER_AUTHORIZATION, "Bearer explicit")
                    .header(MandatoryHeadersInterceptor.HEADER_CORRELATION_ID, "corr-retry")
                    .build(),
            ).execute()
            .use { }

        val recorded = server.takeRequest()
        assertEquals("Bearer explicit", recorded.headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION])
        assertEquals("corr-retry", recorded.headers[MandatoryHeadersInterceptor.HEADER_CORRELATION_ID])
    }

    private companion object {
        /** The only status these tests care about — they assert requests, not responses. */
        const val HTTP_OK = 200
    }
}

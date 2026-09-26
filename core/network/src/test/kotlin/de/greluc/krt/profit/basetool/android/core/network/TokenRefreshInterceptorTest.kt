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
import org.junit.Before
import org.junit.Test

/**
 * A call whose access token has run out is refreshed rather than failing.
 */
class TokenRefreshInterceptorTest {
    private lateinit var server: MockWebServer
    private var token: String? = FIRST_TOKEN

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        token = FIRST_TOKEN
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `a rejected call is retried once with the renewed token`() {
        val refused = mutableListOf<String?>()
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())
        val client =
            client(
                refreshAfterRejection = { stale ->
                    refused.add(stale)
                    token = SECOND_TOKEN
                    SECOND_TOKEN
                },
            )

        val code = client.newCall(request()).execute().use { it.code }

        assertEquals(HTTP_OK, code)
        assertEquals(listOf(FIRST_TOKEN), refused)
        val sent =
            listOf(server.takeRequest(), server.takeRequest())
                .map { it.headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION] }
        assertEquals(listOf("Bearer $FIRST_TOKEN", "Bearer $SECOND_TOKEN"), sent)
    }

    @Test
    fun `a session that cannot be renewed keeps the rejection`() {
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        val client = client(refreshAfterRejection = { null })

        val code = client.newCall(request()).execute().use { it.code }

        assertEquals(HTTP_UNAUTHORIZED, code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a second rejection is not retried again`() {
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        val client =
            client(
                refreshAfterRejection = {
                    token = SECOND_TOKEN
                    SECOND_TOKEN
                },
            )

        val code = client.newCall(request()).execute().use { it.code }

        assertEquals(HTTP_UNAUTHORIZED, code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a spent token is exchanged before the call goes out`() {
        server.enqueue(MockResponse.Builder().code(HTTP_OK).build())
        val client =
            client(
                refreshIfSpent = { token = SECOND_TOKEN },
                refreshAfterRejection = { null },
            )

        client.newCall(request()).execute().use { }

        assertEquals(
            "Bearer $SECOND_TOKEN",
            server.takeRequest().headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION],
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an anonymous call is never retried`() {
        token = null
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        var exchanges = 0
        val client =
            client(
                refreshAfterRejection = {
                    exchanges++
                    null
                },
            )

        client.newCall(request()).execute().use { }

        assertEquals(0, exchanges)
        assertEquals(1, server.requestCount)
    }

    /**
     * Builds a client whose token is the mutable [token] of this test.
     *
     * @param refreshIfSpent the proactive half.
     * @param refreshAfterRejection the reactive half.
     * @return the API client under test.
     */
    private fun client(
        refreshIfSpent: () -> Unit = {},
        refreshAfterRejection: (String?) -> String?,
    ): OkHttpClient =
        KrtHttpClient.create(
            serverClock = ServerClock(),
            accessTokenProvider = { token },
            correlationIdFactory = { "corr-1" },
            languageTagProvider = { "de-DE" },
            activeOrgUnitProvider = { null },
            refreshIfSpent = refreshIfSpent,
            refreshAfterRejection = refreshAfterRejection,
        )

    /**
     * @return a request to the mock server.
     */
    private fun request(): Request = Request.Builder().url(server.url("/api/v1/inventory/aggregated")).build()

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val FIRST_TOKEN = "jwt-one"
        const val SECOND_TOKEN = "jwt-two"
    }
}

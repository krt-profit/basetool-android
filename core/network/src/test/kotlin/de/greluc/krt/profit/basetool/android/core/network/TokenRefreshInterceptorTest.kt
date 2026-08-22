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
 * What happens to a call whose access token has run out.
 *
 * The failure this guards against was found on a device and is invisible in a short test run: the
 * app worked for the realm's access-token lifespan — an hour — and then every screen said "Signal
 * Lost" at once, because nothing ever exchanged the spent token. A session that dies while the
 * member is still signed in is not a network problem and must not be rendered as one.
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
        // The member is signed out, not offline: the 401 has to reach the caller so the app can
        // say so rather than retrying forever.
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        val client = client(refreshAfterRejection = { null })

        val code = client.newCall(request()).execute().use { it.code }

        assertEquals(HTTP_UNAUTHORIZED, code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a second rejection is not retried again`() {
        // The server is refusing a token it has just minted. Retrying that in a loop would hammer
        // the realm from every screen at once.
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
        // The ordinary case costs no failed request: the expiry is known locally.
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
        // Nothing was signed, so the 401 is the server's answer to the request itself.
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

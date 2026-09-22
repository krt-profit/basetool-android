/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * A write that may already have reached the server is never sent a second time by the transport
 * (REQ-APP-API-009, ADR-0023).
 *
 * Each test drives the client exactly as the app builds it, through [KrtHttpClient], because the
 * property is a combination of two settings — `retryOnConnectionFailure` and the one-shot body —
 * and either of them alone proves nothing.
 *
 * The failure is staged the way it happens in the field: a **pooled** connection the server drops
 * after it has read the whole request. The warm-up call is not decoration. OkHttp retries on a new
 * connection only when the failed one had already carried a successful exchange; on a fresh
 * connection it gives up for every verb, and the GET control below would then pass for the wrong
 * reason.
 */
class WriteReplayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            KrtHttpClient.create(
                serverClock = ServerClock(),
                accessTokenProvider = { "jwt-value" },
                correlationIdFactory = { "corr-1" },
                languageTagProvider = { "de-DE" },
                activeOrgUnitProvider = { null },
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a POST dropped after it was sent is not replayed`() {
        warmUpPooledConnection()
        server.enqueue(droppedAfterRequest())
        server.enqueue(ok())

        assertFailsInTransport(write("POST"))

        assertEquals(
            "the server saw the warm-up and exactly one POST; a second POST is a double booking",
            2,
            server.requestCount,
        )
    }

    @Test
    fun `a PATCH dropped after it was sent is not replayed`() {
        warmUpPooledConnection()
        server.enqueue(droppedAfterRequest())
        server.enqueue(ok())

        assertFailsInTransport(write("PATCH"))

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a GET dropped the same way is still retried`() {
        // The control. Without it the two tests above would also pass if the client had simply
        // stopped retrying anything, which would turn every stale pooled connection into an error
        // banner on a list screen.
        warmUpPooledConnection()
        server.enqueue(droppedAfterRequest())
        server.enqueue(ok())

        client.newCall(Request.Builder().url(server.url("/api/v1/missions")).get().build()).execute().use {
            assertEquals(HTTP_OK, it.code)
        }

        assertEquals("warm-up, the dropped GET and its retry", WARM_UP_DROP_AND_RETRY, server.requestCount)
    }

    @Test
    fun `a PUT is still retried, because replacing a row twice leaves it replaced once`() {
        warmUpPooledConnection()
        server.enqueue(droppedAfterRequest())
        server.enqueue(ok())

        client.newCall(write("PUT")).execute().use { assertEquals(HTTP_OK, it.code) }

        assertEquals(WARM_UP_DROP_AND_RETRY, server.requestCount)
    }

    @Test
    fun `a 408 on a POST is handed to the caller rather than replayed`() {
        server.enqueue(MockResponse.Builder().code(HTTP_CLIENT_TIMEOUT).build())
        server.enqueue(ok())

        client.newCall(write("POST")).execute().use { assertEquals(HTTP_CLIENT_TIMEOUT, it.code) }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 408 on a GET is still retried`() {
        server.enqueue(MockResponse.Builder().code(HTTP_CLIENT_TIMEOUT).build())
        server.enqueue(ok())

        client.newCall(Request.Builder().url(server.url("/api/v1/missions")).get().build()).execute().use {
            assertEquals(HTTP_OK, it.code)
        }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the token client does not replay a token request either`() {
        // Every token call is a POST, and a refresh rotates the refresh token: a replay of one that
        // landed can only be refused, and the member is signed out for a network blip.
        val tokenClient = KrtHttpClient.createTokenClient(client, ServerClock())
        warmUpPooledConnection(tokenClient)
        server.enqueue(droppedAfterRequest())
        server.enqueue(ok())

        assertFailsInTransport(write("POST"), through = tokenClient)

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the app's own 401 retry still re-sends the whole body`() {
        // One-shot is a flag OkHttp's recovery reads, not a property of the bytes. The token
        // interceptor re-sends a request the server REFUSED before doing anything, which is a
        // deliberate replay and has to keep working — with the same payload, not an empty one.
        var token = "expired"
        val refreshing =
            KrtHttpClient.create(
                serverClock = ServerClock(),
                accessTokenProvider = { token },
                correlationIdFactory = { "corr-1" },
                languageTagProvider = { "de-DE" },
                activeOrgUnitProvider = { null },
                refreshAfterRejection = {
                    token = "renewed"
                    token
                },
            )
        server.enqueue(MockResponse.Builder().code(HTTP_UNAUTHORIZED).build())
        server.enqueue(ok())

        refreshing.newCall(write("POST")).execute().use { assertEquals(HTTP_OK, it.code) }

        val rejected = server.takeRequest()
        val retried = server.takeRequest()
        assertEquals(PAYLOAD, rejected.body?.utf8())
        assertEquals("the retry carries the same payload", PAYLOAD, retried.body?.utf8())
        assertEquals("Bearer renewed", retried.headers[MandatoryHeadersInterceptor.HEADER_AUTHORIZATION])
    }

    /**
     * Opens a connection and completes one exchange on it, so the next call reuses it from the pool.
     *
     * @param through the client whose pool the connection should land in.
     */
    private fun warmUpPooledConnection(through: OkHttpClient = client) {
        server.enqueue(ok())
        through.newCall(Request.Builder().url(server.url("/warm-up")).get().build()).execute().use {
            assertEquals(HTTP_OK, it.code)
        }
    }

    /**
     * Builds a write carrying [PAYLOAD] under [method].
     *
     * @param method the verb.
     * @return the request, not yet sent.
     */
    private fun write(method: String): Request =
        Request.Builder()
            .url(server.url("/api/v1/bank/transfers"))
            .method(method, PAYLOAD.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

    /**
     * Sends [request] and asserts it ends in a transport failure rather than a response.
     *
     * @param request the write.
     * @param through the client to send it with.
     */
    private fun assertFailsInTransport(
        request: Request,
        through: OkHttpClient = client,
    ) {
        try {
            through.newCall(request).execute().close()
            fail("a write dropped after it was sent must surface as a failure, not be replayed")
        } catch (expected: IOException) {
            // What ApiReader turns into ApiError.Network: the member sees it and can decide.
        }
    }

    /**
     * A response the server never sends: it reads the request in full, then closes the socket.
     *
     * @return the scripted exchange.
     */
    private fun droppedAfterRequest(): MockResponse =
        MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

    /**
     * An empty `200`.
     *
     * @return the scripted exchange.
     */
    private fun ok(): MockResponse = MockResponse.Builder().code(HTTP_OK).build()

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CLIENT_TIMEOUT = 408

        /** Requests the server sees when a dropped call IS retried: warm-up, drop, retry. */
        const val WARM_UP_DROP_AND_RETRY = 3

        /** A write body whose second arrival would be a second booking. */
        const val PAYLOAD = """{"amount":100,"version":3}"""
    }
}

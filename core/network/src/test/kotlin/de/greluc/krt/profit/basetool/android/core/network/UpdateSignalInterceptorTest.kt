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
import java.util.Collections

/**
 * The transport tells the update gate about a `404` and about `APP_UPDATE_REQUIRED`, and about
 * nothing else (REQ-APP-API-010).
 */
class UpdateSignalInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val signals: MutableList<UpdateSignal> = Collections.synchronizedList(mutableListOf())

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
                updateSignals = { signals += it },
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a NOT_FOUND problem is reported`() {
        server.enqueue(problem(HTTP_NOT_FOUND, "NOT_FOUND"))

        get("/api/v1/missions/42")

        assertEquals(listOf(UpdateSignal.NOT_FOUND), signals)
    }

    @Test
    fun `the edge's bare 404 page is reported`() {
        server.enqueue(MockResponse.Builder().code(HTTP_NOT_FOUND).body("<html>404</html>").build())

        get("/api/v1/operations")

        assertEquals(listOf(UpdateSignal.NOT_FOUND), signals)
    }

    @Test
    fun `APP_UPDATE_REQUIRED is reported once, whatever its status`() {
        server.enqueue(problem(HTTP_GONE, "APP_UPDATE_REQUIRED"))
        server.enqueue(problem(HTTP_NOT_FOUND, "APP_UPDATE_REQUIRED"))

        get("/api/v1/missions/slim")
        get("/api/v1/missions/slim")

        assertEquals(listOf(UpdateSignal.UPDATE_REQUIRED, UpdateSignal.UPDATE_REQUIRED), signals)
    }

    @Test
    fun `other failures and successes are not reported`() {
        server.enqueue(MockResponse.Builder().code(HTTP_OK).body("{}").build())
        server.enqueue(problem(HTTP_FORBIDDEN, "FORBIDDEN"))
        server.enqueue(problem(HTTP_CONFLICT, "OPTIMISTIC_LOCK"))
        server.enqueue(MockResponse.Builder().code(HTTP_UNAVAILABLE).build())

        repeat(REQUESTS_WITHOUT_SIGNAL) { get("/api/v1/missions/42") }

        assertEquals(emptyList<UpdateSignal>(), signals)
    }

    @Test
    fun `a 404 on the policy itself is not reported`() {
        server.enqueue(MockResponse.Builder().code(HTTP_NOT_FOUND).build())

        get("/api/v1/app/version-policy")

        assertEquals(emptyList<UpdateSignal>(), signals)
    }

    @Test
    fun `a 404 outside the API is not reported`() {
        server.enqueue(MockResponse.Builder().code(HTTP_NOT_FOUND).build())

        get("/favicon.ico")

        assertEquals(emptyList<UpdateSignal>(), signals)
    }

    @Test
    fun `the caller still reads the problem body`() {
        server.enqueue(problem(HTTP_NOT_FOUND, "NOT_FOUND"))

        val body =
            client.newCall(Request.Builder().url(server.url("/api/v1/missions/42")).get().build()).execute().use {
                it.body.string()
            }

        assertEquals(problemBody("NOT_FOUND"), body)
    }

    /**
     * Issues one GET and discards the answer.
     *
     * @param path the request path.
     */
    private fun get(path: String) {
        client.newCall(Request.Builder().url(server.url(path)).get().build()).execute().close()
    }

    /**
     * Builds an RFC 7807 answer.
     *
     * @param status the HTTP status.
     * @param code the problem code.
     * @return the response.
     */
    private fun problem(
        status: Int,
        code: String,
    ): MockResponse =
        MockResponse.Builder()
            .code(status)
            .setHeader("Content-Type", "application/problem+json")
            .body(problemBody(code))
            .build()

    /**
     * The JSON of a problem body.
     *
     * @param code the problem code.
     * @return the body.
     */
    private fun problemBody(code: String): String = """{"status":0,"code":"$code","extra":"ignored"}"""

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_GONE = 410
        const val HTTP_UNAVAILABLE = 503
        const val REQUESTS_WITHOUT_SIGNAL = 4
    }
}

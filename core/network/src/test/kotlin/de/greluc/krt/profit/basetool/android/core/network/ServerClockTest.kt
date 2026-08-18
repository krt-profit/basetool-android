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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * The clock DPoP proofs are timed against.
 *
 * Keycloak accepts a 10 s proof lifetime with 15 s of skew, so a device whose clock is a minute off
 * cannot log in — and the failure presents as "login broken", not "clock wrong". Learning the offset
 * from the `Date` header is what keeps that from happening (security concept §4).
 */
class ServerClockTest {
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

    @Test
    fun `falls back to device time until a response has been seen`() {
        val clock = ServerClock()

        assertEquals(Duration.ZERO, clock.observedOffset())
        assertTrue(abs(Duration.between(Instant.now(), clock.now()).toMillis()) < TOLERANCE_MILLIS)
    }

    @Test
    fun `learns a positive offset from a server running ahead`() {
        val clock = ServerClock()
        val deviceTime = Instant.parse("2026-08-18T10:00:00Z")

        clock.observe(serverTime = deviceTime.plusSeconds(SKEW_SECONDS), deviceTime = deviceTime)

        assertEquals(Duration.ofSeconds(SKEW_SECONDS), clock.observedOffset())
    }

    @Test
    fun `learns a negative offset from a server running behind`() {
        val clock = ServerClock()
        val deviceTime = Instant.parse("2026-08-18T10:00:00Z")

        clock.observe(serverTime = deviceTime.minusSeconds(SKEW_SECONDS), deviceTime = deviceTime)

        assertEquals(Duration.ofSeconds(-SKEW_SECONDS), clock.observedOffset())
    }

    @Test
    fun `the interceptor feeds the clock from a real response`() {
        val clock = ServerClock()
        val client = OkHttpClient.Builder().addInterceptor(ServerTimeInterceptor(clock)).build()
        server.enqueue(
            MockResponse
                .Builder()
                .code(HTTP_OK)
                .addHeader("Date", "Tue, 18 Aug 2026 10:00:00 GMT")
                .build(),
        )

        client.newCall(Request.Builder().url(server.url("/api/v1/terms/status")).build()).execute().use { }

        // The device clock is "now", the server said 2026-08-18T10:00:00Z, so the offset is the gap
        // between the two — whatever the machine running this test believes the date is.
        val expected = Duration.between(Instant.now(), Instant.parse("2026-08-18T10:00:00Z"))
        assertTrue(abs(expected.minus(clock.observedOffset()).toMillis()) < TOLERANCE_MILLIS)
    }

    @Test
    fun `a response without a Date header leaves the offset untouched`() {
        val clock = ServerClock()
        clock.observe(
            serverTime = Instant.parse("2026-08-18T10:00:30Z"),
            deviceTime = Instant.parse("2026-08-18T10:00:00Z"),
        )
        val client = OkHttpClient.Builder().addInterceptor(ServerTimeInterceptor(clock)).build()
        server.enqueue(MockResponse.Builder().code(HTTP_OK).removeHeader("Date").build())

        client.newCall(Request.Builder().url(server.url("/api/v1/terms/status")).build()).execute().use { }

        assertEquals(Duration.ofSeconds(SKEW_SECONDS), clock.observedOffset())
    }

    private companion object {
        /** The only status these tests care about — they assert the Date header, not the code. */
        const val HTTP_OK = 200

        /** A drift large enough to matter to Keycloak's 15 s skew allowance. */
        const val SKEW_SECONDS = 30L

        /** Slack for the wall-clock reads the assertions bracket. */
        const val TOLERANCE_MILLIS = 5_000L
    }
}

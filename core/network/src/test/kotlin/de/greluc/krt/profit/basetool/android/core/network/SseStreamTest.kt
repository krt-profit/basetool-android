/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hand-rolled SSE reader: what ends an event, what a comment line does, and that the flow
 * completes when the server closes the stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SseStreamTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
    }

    private lateinit var server: MockWebServer
    private lateinit var stream: SseStream

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        stream = SseStream(OkHttpClient(), server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Enqueues a stream body and closes it, which is what makes the flow complete.
     *
     * @param body the raw event-stream text.
     * @param status the status code.
     */
    private fun respond(
        body: String,
        status: Int = HTTP_OK,
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .setHeader("Content-Type", "text/event-stream")
                .body(body)
                .build(),
        )
    }

    @Test
    fun `a named event with data is delivered whole`() =
        runTest {
            respond("event: notification\ndata: new\n\n")

            val events = stream.events("/stream").toList()

            assertEquals(listOf(SseEvent("notification", "new")), events)
        }

    @Test
    fun `a blank line is what ends an event, not a newline`() =
        runTest {
            respond("event: connected\ndata: ok\n\nevent: notification\ndata: new\n\n")

            val events = stream.events("/stream").toList()

            assertEquals(listOf("connected", "notification"), events.map { it.name })
        }

    @Test
    fun `a comment line keeps the connection alive without producing an event`() =
        runTest {
            respond(": keep-alive\n\nevent: notification\ndata: new\n\n")

            val events = stream.events("/stream").toList()

            assertEquals(listOf(SseEvent("notification", "new")), events)
        }

    @Test
    fun `multiple data lines are joined, not lost`() =
        runTest {
            respond("event: notification\ndata: one\ndata: two\n\n")

            val events = stream.events("/stream").toList()

            assertEquals("one\ntwo", events.single().data)
        }

    @Test
    fun `an event without a name takes the format's default`() =
        runTest {
            respond("data: bare\n\n")

            val events = stream.events("/stream").toList()

            assertEquals("message", events.single().name)
        }

    @Test
    fun `a refused stream completes rather than throwing`() =
        runTest {
            respond("", status = HTTP_UNAUTHORIZED)

            val events = stream.events("/stream").toList()

            assertTrue(events.isEmpty())
        }

    @Test
    fun `the request announces itself as an event stream`() =
        runTest {
            respond("event: connected\ndata: ok\n\n")

            stream.events("/stream").toList()

            val request = server.takeRequest()
            assertEquals("text/event-stream", request.headers["Accept"])
            assertEquals("no-cache", request.headers["Cache-Control"])
        }
}

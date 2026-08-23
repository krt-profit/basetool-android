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
 * The hand-rolled SSE reader.
 *
 * Written by hand rather than pulled in as `okhttp-sse`, so the framing rules are this project's
 * responsibility and are asserted here: what ends an event, what a comment line does, and that the
 * flow completes rather than hangs when the server closes the stream.
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
            // Two events in one body. Flushing on every newline would deliver four half-events.
            respond("event: connected\ndata: ok\n\nevent: notification\ndata: new\n\n")

            val events = stream.events("/stream").toList()

            assertEquals(listOf("connected", "notification"), events.map { it.name })
        }

    @Test
    fun `a comment line keeps the connection alive without producing an event`() =
        runTest {
            // The SSE keep-alive. Treating it as data would deliver an empty event every few
            // seconds, and a caller that re-reads on every event would then poll instead of push.
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
            // A 401 here means the token expired. The caller must be able to stop, and an
            // exception would push that decision into a crash instead.
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

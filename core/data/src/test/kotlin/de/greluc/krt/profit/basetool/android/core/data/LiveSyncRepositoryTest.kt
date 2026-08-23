/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.SseStream
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The app half of the live-sync bridge: what it does with the server's frames, and what it sends
 * after its own writes (REQ-APP-SYNC-001…004, server ADR-0143).
 */
class LiveSyncRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: LiveSyncRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().removeSuffix("/")
        val client = OkHttpClient()
        repository =
            LiveSyncRepository(
                stream = SseStream(httpClient = client, baseUrl = baseUrl),
                reader = ApiReader(httpClient = client, baseUrl = baseUrl, json = KrtJson, logTag = "test"),
                // Collapsed so the coalescing assertions cost milliseconds rather than the real
                // 1500 ms of a global window each.
                timing =
                    LiveSyncTiming(
                        resourceWindow = 20.milliseconds,
                        globalWindow = 20.milliseconds,
                        reconnectSettle = 5.milliseconds,
                        reconnectBase = 5.milliseconds,
                        reconnectCeiling = 20.milliseconds,
                    ),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `names the requested rooms in the query, so the URL is the whole subscription`() =
        runBlocking {
            enqueueStream(subscribed("inventory"))

            observe(setOf(LiveSyncTopic.INVENTORY, LiveSyncTopic.mission(MISSION_ID)), 1)

            val requested = server.takeRequest().target
            assertTrue(requested, requested.startsWith("/api/v1/live-sync/stream?topics="))
            assertTrue(requested, requested.contains("inventory"))
            assertTrue(requested, requested.contains("mission%3A$MISSION_ID"))
        }

    @Test
    fun `reports only the rooms the server accepted, so a screen can tell live from refused`() =
        runBlocking {
            // Two asked for, one granted. Silence from the refused room is indistinguishable from
            // silence from a quiet one, so this list is the only thing that tells them apart.
            enqueueStream(subscribed("inventory"))

            val accepted =
                observe(setOf(LiveSyncTopic.INVENTORY, LiveSyncTopic.ORDERS), 1)
                    .first() as LiveSyncEvent.Subscribed

            assertEquals(setOf(LiveSyncTopic.INVENTORY), accepted.topics)
        }

    @Test
    fun `emits a change with its sections`() =
        runBlocking {
            enqueueStream(subscribed("inventory") + changed("inventory", "stock"))

            val change = observe(setOf(LiveSyncTopic.INVENTORY), 2)[1] as LiveSyncEvent.Changed

            assertEquals(LiveSyncTopic.INVENTORY, change.topic)
            assertEquals(setOf("stock"), change.sections)
        }

    @Test
    fun `folds frames inside one window into a single event carrying the union`() =
        runBlocking {
            // The bound that matters: the relay is cheap, the re-fetch herd it triggers is not.
            enqueueStream(
                subscribed("mission:$MISSION_ID") +
                    changed("mission:$MISSION_ID", "crew") +
                    changed("mission:$MISSION_ID", "finance") +
                    changed("mission:$MISSION_ID", "crew"),
            )

            val change =
                observe(setOf(LiveSyncTopic.mission(MISSION_ID)), 2)[1] as LiveSyncEvent.Changed

            assertEquals(setOf("crew", "finance"), change.sections)
        }

    @Test
    fun `keeps rooms apart, so one room's window does not swallow another's change`() =
        runBlocking {
            enqueueStream(
                subscribed("inventory", "materialboard") +
                    changed("inventory", "stock") +
                    changed("materialboard", "board"),
            )

            val events = observe(setOf(LiveSyncTopic.INVENTORY, LiveSyncTopic.MATERIALBOARD), 3)
            val topics = events.filterIsInstance<LiveSyncEvent.Changed>().map { it.topic }.toSet()

            assertEquals(setOf(LiveSyncTopic.INVENTORY, LiveSyncTopic.MATERIALBOARD), topics)
        }

    @Test
    fun `reconnects after the server closes the stream, because it closes every one of them`() =
        runBlocking {
            // Thirty minutes is the server's design, and a phone drops far more often than that.
            // A client that treated the end of a stream as the end of live sync would be live for
            // half an hour and silently stale after it.
            enqueueStream(subscribed("inventory"))
            enqueueStream(subscribed("inventory") + changed("inventory", "stock"))

            val change = observe(setOf(LiveSyncTopic.INVENTORY), 3)[2] as LiveSyncEvent.Changed

            assertEquals(setOf("stock"), change.sections)
        }

    @Test
    fun `ignores the heartbeat, so an idle stream is not mistaken for a change`() =
        runBlocking {
            enqueueStream(
                subscribed("inventory") +
                    "event: heartbeat\ndata: ok\n\n" +
                    changed("inventory", "stock"),
            )

            val change = observe(setOf(LiveSyncTopic.INVENTORY), 2)[1] as LiveSyncEvent.Changed

            assertEquals(setOf("stock"), change.sections)
        }

    @Test
    fun `drops a frame for a room this build does not know rather than inventing one`() =
        runBlocking {
            // Only a newer server can send this. A screen has nothing to do with a room it has no
            // code for, and a synthesised topic would be a room key nobody is listening on.
            enqueueStream(
                subscribed("inventory") +
                    changed("members", "grid") +
                    changed("inventory", "stock"),
            )

            val change = observe(setOf(LiveSyncTopic.INVENTORY), 2)[1] as LiveSyncEvent.Changed

            assertEquals(LiveSyncTopic.INVENTORY, change.topic)
        }

    @Test
    fun `never opens a stream for an empty room set`() =
        runBlocking {
            assertTrue(repository.observe(emptySet()).toList().isEmpty())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `sends the announcement the server's frame shape expects`() =
        runBlocking {
            server.enqueue(MockResponse(code = 202))

            val result = repository.publish(LiveSyncTopic.INVENTORY, setOf("stock"))

            assertTrue(result is ApiResult.Success)
            val request = server.takeRequest()
            assertEquals("/api/v1/live-sync/changed", request.target)
            assertEquals("POST", request.method)
            val body = request.body?.utf8().orEmpty()
            assertTrue(body, body.contains("\"topic\":\"inventory\""))
            assertTrue(body, body.contains("\"stock\""))
        }

    @Test
    fun `does not call the server when nothing changed`() =
        runBlocking {
            val result = repository.publish(LiveSyncTopic.INVENTORY, emptySet())

            assertTrue(result is ApiResult.Success)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `answers a failure without raising, because the write it follows already committed`() =
        runBlocking {
            // A screen reporting this would be reporting somebody else's refresh as the member's
            // own save having failed.
            server.enqueue(MockResponse(code = 429))

            val result = repository.publish(LiveSyncTopic.INVENTORY, setOf("stock"))

            assertTrue(result is ApiResult.Failure)
            assertFalse(result is ApiResult.Success)
        }

    /**
     * Collects the first [count] events and lets the flow go.
     *
     * [LiveSyncSource.observe] never completes on its own — it reconnects — so a test has to say
     * how many events it is waiting for. The timeout is the whole point of the helper: without it a
     * regression that stops emitting hangs the suite instead of failing it.
     *
     * @param topics the rooms to ask for.
     * @param count how many events to wait for.
     * @return the events, in arrival order.
     */
    private suspend fun observe(
        topics: Set<LiveSyncTopic>,
        count: Int,
    ): List<LiveSyncEvent> = withTimeout(10.seconds) { repository.observe(topics).take(count).toList() }

    /**
     * Serves one SSE body and then ends the stream.
     *
     * @param body the frames, already framed.
     */
    private fun enqueueStream(body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", "text/event-stream")
                .body(body)
                .build(),
        )
    }

    private fun subscribed(vararg topics: String): String {
        val list = topics.joinToString(",") { "\"$it\"" }
        return "event: subscribed\ndata: {\"topics\":[$list]}\n\n"
    }

    private fun changed(
        topic: String,
        vararg sections: String,
    ): String {
        val list = sections.joinToString(",") { "\"$it\"" }
        return "event: changed\ndata: {\"topic\":\"$topic\",\"sections\":[$list]}\n\n"
    }

    private companion object {
        const val MISSION_ID = "8f14e45f-ceea-467a-9c5b-5f1f52a3a1c2"
        const val HTTP_OK = 200
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.SseStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The waits the live-sync client is built around.
 *
 * A parameter rather than constants so a test can collapse them: asserting that two frames inside
 * one window arrive as one event otherwise costs the real 1500 ms, per assertion, and a suite that
 * slow stops being run.
 *
 * @property resourceWindow ADR-0094's coalescing window for one resource's room.
 * @property globalWindow ADR-0094's window for a tool-wide room — longer because such a room is
 *   read by every member at once, and the re-fetch herd, not the relay, is the binding cost.
 * @property reconnectSettle the spread over a reconnect after a stream that had been working. The
 *   server closes every stream after thirty minutes by design, so without this every phone that
 *   connected together would come back together, forever.
 * @property reconnectBase the first backoff step after a failed attempt.
 * @property reconnectCeiling the longest a client waits before trying again.
 */
data class LiveSyncTiming(
    val resourceWindow: Duration = 400.milliseconds,
    val globalWindow: Duration = 1_500.milliseconds,
    val reconnectSettle: Duration = 1.seconds,
    val reconnectBase: Duration = 1.seconds,
    val reconnectCeiling: Duration = 30.seconds,
)

/** What the live-sync stream tells a screen. */
sealed interface LiveSyncEvent {
    /**
     * The rooms the server actually opened, sent once when the stream connects.
     *
     * A screen must read this rather than assume it got what it asked for: a room missing here will
     * never speak, and silence from a live room and silence from a room that was refused look
     * exactly the same. Only this event tells the two apart, and only the second means the screen
     * has to keep refreshing on its own.
     *
     * @property topics the accepted rooms.
     */
    data class Subscribed(
        val topics: Set<LiveSyncTopic>,
    ) : LiveSyncEvent

    /**
     * A room changed and the named regions should be re-read.
     *
     * Already coalesced: several frames inside the room's window arrive as one event carrying the
     * union of their sections.
     *
     * @property topic the room.
     * @property sections the regions to re-read.
     */
    data class Changed(
        val topic: LiveSyncTopic,
        val sections: Set<String>,
    ) : LiveSyncEvent
}

/** Receives change signals for a set of rooms, and announces the app's own writes. */
interface LiveSyncSource {
    /**
     * Watches a set of rooms until the collector is cancelled.
     *
     * @param topics the rooms to join; an empty set never emits.
     * @return a cold flow that connects on collection and reconnects on its own.
     */
    fun observe(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent>

    /**
     * Tells the other viewers of a room that it changed.
     *
     * @param topic the room.
     * @param sections the regions that changed.
     * @return success, or the classified failure — which the caller ignores.
     */
    suspend fun publish(
        topic: LiveSyncTopic,
        sections: Set<String>,
    ): ApiResult<Unit>
}

/**
 * The live-sync client: one SSE stream in, one signal out (REQ-APP-SYNC-001…004, server ADR-0143).
 *
 * Three things happen here that a screen must not have to think about.
 *
 * **Reconnect.** The server closes a stream every thirty minutes by design, and a phone loses its
 * connection far more often than that. [observe] reopens with a full-jittered backoff and keeps
 * going until it is cancelled. It never gives up on a failure it cannot classify — [SseStream]
 * completes the flow the same way for a clean close, a dropped socket and a `401`, so treating any
 * of them as terminal would silently strand the screen for the two cases that recover on their own.
 * A refused stream costs a reconnect attempt per backoff step, and the backoff is what bounds it.
 *
 * **Coalescing.** A room is re-read at most once per window — 400 ms for one resource, 1500 ms for
 * a tool-wide room, both **full-jittered** (REQ-APP-SYNC-003). This is what actually protects the
 * server: the relay rate is cheap, the re-fetch herd it triggers is not, and a global room can hold
 * every member at once. The jitter matters as much as the window — without it a frame broadcast to
 * two hundred viewers produces two hundred reads at the same instant.
 *
 * **Silence about failure.** [publish] answers a result nobody is expected to act on. It follows a
 * mutation that has already committed; a screen that reported an error here would be reporting a
 * failure of somebody else's refresh as a failure of the member's own save.
 *
 * @property stream the SSE reader.
 * @property reader the API client, used only for [publish].
 * @property json the parser for the two small frame shapes.
 * @property timing the coalescing windows and the reconnect backoff; overridden only by tests,
 *   which would otherwise have to spend the real 1500 ms of a global window per assertion.
 */
class LiveSyncRepository(
    private val stream: SseStream,
    private val reader: ApiReader,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val timing: LiveSyncTiming = LiveSyncTiming(),
) : LiveSyncSource {
    /**
     * Convenience constructor for the object graph.
     *
     * Both halves ride the one API client, which is what keeps the bearer token, the active org
     * unit and the correlation id identical on the stream and on the signal it answers with.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers.
     * @param baseUrl the flavour's API origin.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        SseStream(httpClient = httpClient, baseUrl = baseUrl),
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /** {@inheritDoc} */
    override fun observe(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent> =
        callbackFlow {
            if (topics.isEmpty()) {
                close()
                return@callbackFlow
            }
            val pending = mutableMapOf<LiveSyncTopic, MutableSet<String>>()
            val timers = mutableMapOf<LiveSyncTopic, Job>()
            val guard = Mutex()

            val connection =
                launch {
                    var attempt = 0
                    while (isActive) {
                        val delivered =
                            collectOnce(topics) { event ->
                                when (event) {
                                    // The acceptance list is never coalesced: it is the screen's
                                    // signal that a room is live at all, and delaying it would let
                                    // the screen believe it is live for a window it is not.
                                    is LiveSyncEvent.Subscribed -> {
                                        trySend(event)
                                    }

                                    is LiveSyncEvent.Changed -> {
                                        launch { coalesce(event, pending, timers, guard) { trySend(it) } }
                                    }
                                }
                            }
                        // A stream that delivered something was working, so the next drop starts
                        // over at the shortest wait rather than inheriting an old backoff.
                        attempt = if (delivered) 0 else attempt + 1
                        delay(backoff(attempt))
                    }
                }
            awaitClose { connection.cancel() }
        }

    /**
     * Folds a change frame into its room's window, emitting the union once the window closes.
     *
     * The first frame for a quiet room starts a timer; every frame inside that window only widens
     * the section set. So a room is re-read once per window however many frames arrive, which is
     * the bound that matters — the relay is cheap, the re-fetch herd it triggers is not, and a
     * tool-wide room can hold every member at once.
     *
     * The window is jittered per room rather than fixed, so a frame broadcast to two hundred
     * viewers does not produce two hundred reads at the same instant.
     *
     * @param event the frame.
     * @param pending sections accumulated per room, guarded by [guard].
     * @param timers the open window per room, guarded by [guard].
     * @param guard serialises the two maps against the frames arriving on the reader.
     * @param emit where the coalesced event goes.
     */
    private suspend fun coalesce(
        event: LiveSyncEvent.Changed,
        pending: MutableMap<LiveSyncTopic, MutableSet<String>>,
        timers: MutableMap<LiveSyncTopic, Job>,
        guard: Mutex,
        emit: (LiveSyncEvent) -> Unit,
    ) = coroutineScope {
        val start =
            guard.withLock {
                pending.getOrPut(event.topic) { mutableSetOf() }.addAll(event.sections)
                if (timers.containsKey(event.topic)) {
                    return@withLock false
                }
                true
            }
        if (!start) {
            return@coroutineScope
        }
        val timer =
            launch {
                delay(window(event.topic))
                val sections = guard.withLock { pending.remove(event.topic).orEmpty() }
                guard.withLock { timers.remove(event.topic) }
                if (sections.isNotEmpty()) {
                    emit(LiveSyncEvent.Changed(event.topic, sections))
                }
            }
        guard.withLock { timers[event.topic] = timer }
    }

    /**
     * The coalescing window for a room, full-jittered.
     *
     * ADR-0094's numbers, unchanged: a tool-wide room is read by everyone at once and gets the long
     * window; one resource is read by the handful of people looking at it and gets the short one.
     *
     * @param topic the room.
     * @return the wait before its accumulated sections are emitted.
     */
    private fun window(topic: LiveSyncTopic): Duration {
        val ceiling = if (topic.global) timing.globalWindow else timing.resourceWindow
        return Random.nextLong(1L, ceiling.inWholeMilliseconds + 1).milliseconds
    }

    /** {@inheritDoc} */
    override suspend fun publish(
        topic: LiveSyncTopic,
        sections: Set<String>,
    ): ApiResult<Unit> {
        if (sections.isEmpty()) {
            return ApiResult.Success(Unit)
        }
        val result =
            reader.postAccepted(
                CHANGED_PATH,
                ChangedRequest(topic = topic.wire, sections = sections.toList()),
                ChangedRequest.serializer(),
            )
        if (result is ApiResult.Failure) {
            // Deliberately swallowed at the log level: the member's own write already succeeded,
            // and the only consequence is that peers refresh on their own cadence instead.
            KrtLog.d(LOG_TAG) { "signal for ${topic.wire} not relayed" }
        }
        return result
    }

    /**
     * Runs one connection to completion.
     *
     * @param topics the rooms to ask for.
     * @param emit where to put the parsed events.
     * @return whether the connection delivered anything, which decides the backoff reset.
     */
    private suspend fun collectOnce(
        topics: Set<LiveSyncTopic>,
        emit: (LiveSyncEvent) -> Unit,
    ): Boolean {
        var delivered = false
        val query = listOf(TOPICS_PARAM to topics.joinToString(",") { it.wire })
        stream.events(STREAM_PATH, query).collect { event ->
            delivered = true
            when (event.name) {
                SUBSCRIBED_EVENT -> parseSubscribed(event.data)?.let(emit)
                CHANGED_EVENT -> parseChanged(event.data)?.let(emit)
                else -> Unit
            }
        }
        return delivered
    }

    /**
     * Parses the once-per-stream acceptance list.
     *
     * @param data the frame body.
     * @return the event, or `null` if the frame did not parse.
     */
    private fun parseSubscribed(data: String): LiveSyncEvent.Subscribed? =
        runCatching { json.decodeFromString(SubscribedFrame.serializer(), data) }
            .getOrNull()
            ?.let { frame ->
                LiveSyncEvent.Subscribed(frame.topics.mapNotNull(LiveSyncTopic::parse).toSet())
            }

    /**
     * Parses a change frame.
     *
     * A frame naming a room this build does not know is dropped rather than passed on with a
     * synthesised topic: it can only come from a newer server, and a screen has nothing to do with
     * a room it has no code for.
     *
     * @param data the frame body.
     * @return the event, or `null` if the frame did not parse or named nothing usable.
     */
    private fun parseChanged(data: String): LiveSyncEvent.Changed? {
        val frame = runCatching { json.decodeFromString(ChangedFrame.serializer(), data) }.getOrNull()
        val topic = frame?.topic?.let(LiveSyncTopic::parse) ?: return null
        val sections = frame.sections.filter { it.isNotBlank() }.toSet()
        return if (sections.isEmpty()) null else LiveSyncEvent.Changed(topic, sections)
    }

    /**
     * The wait before the next reconnect attempt.
     *
     * Full jitter rather than a plain exponential: every phone on the network loses its connection
     * at the same moment when the server restarts, and an unjittered backoff would bring all of
     * them back in one wave, repeatedly.
     *
     * @param attempt how many consecutive attempts have failed; zero after a working connection.
     * @return the wait.
     */
    private fun backoff(attempt: Int): Duration {
        if (attempt == 0) {
            return Random.nextLong(timing.reconnectSettle.inWholeMilliseconds + 1).milliseconds
        }
        val ceiling =
            minOf(
                timing.reconnectBase.inWholeMilliseconds shl minOf(attempt - 1, RECONNECT_MAX_SHIFT),
                timing.reconnectCeiling.inWholeMilliseconds,
            )
        return Random.nextLong(ceiling + 1).milliseconds
    }

    private companion object {
        const val STREAM_PATH = "/api/v1/live-sync/stream"
        const val CHANGED_PATH = "/api/v1/live-sync/changed"
        const val TOPICS_PARAM = "topics"
        const val SUBSCRIBED_EVENT = "subscribed"
        const val CHANGED_EVENT = "changed"

        const val RECONNECT_MAX_SHIFT = 5

        /** Log subsystem. A topic names a resource id and is logged only at debug. */
        const val LOG_TAG = "livesync"
    }
}

/** The `subscribed` frame. */
@Serializable
private data class SubscribedFrame(
    val topics: List<String> = emptyList(),
)

/** The `changed` frame. */
@Serializable
private data class ChangedFrame(
    val topic: String? = null,
    val sections: List<String> = emptyList(),
)

/** The publish body. */
@Serializable
private data class ChangedRequest(
    val topic: String,
    val sections: List<String>,
)

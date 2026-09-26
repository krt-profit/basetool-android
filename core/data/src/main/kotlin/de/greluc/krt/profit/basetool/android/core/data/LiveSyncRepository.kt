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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The waits the live-sync client is built around; a parameter so tests can shorten them.
 *
 * @property resourceWindow the coalescing window for one resource's room (ADR-0094).
 * @property globalWindow the longer coalescing window for a tool-wide room (ADR-0094).
 * @property reconnectSettle the spread over a reconnect after a stream that had been working.
 * @property reconnectBase the first backoff step after a failed attempt.
 * @property reconnectCeiling the longest a client waits before trying again.
 * @property unionSettle how long the room union has to hold still before the connection follows
 *   it.
 */
data class LiveSyncTiming(
    val resourceWindow: Duration = 400.milliseconds,
    val globalWindow: Duration = 1_500.milliseconds,
    val reconnectSettle: Duration = 1.seconds,
    val reconnectBase: Duration = 1.seconds,
    val reconnectCeiling: Duration = 30.seconds,
    val unionSettle: Duration = 250.milliseconds,
)

/** What the live-sync stream tells a screen. */
sealed interface LiveSyncEvent {
    /**
     * The rooms the server actually opened, sent once when the stream connects; a room missing here
     * will never emit.
     *
     * @property topics the accepted rooms.
     */
    data class Subscribed(
        val topics: Set<LiveSyncTopic>,
    ) : LiveSyncEvent

    /**
     * A room changed and the named regions should be re-read; already coalesced over the room's
     * window.
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
 * The live-sync client: one shared SSE stream in, one signal out (REQ-APP-SYNC-001…004).
 *
 * All [observe] callers share one connection carrying the debounced union of their rooms. The
 * stream reconnects with full-jittered backoff until cancelled, change frames are coalesced per
 * room over a jittered window (REQ-APP-SYNC-003), and [publish] reports no failure to its caller.
 *
 * @property stream the SSE reader.
 * @property reader the API client, used only for [publish].
 * @property json the parser for the two small frame shapes.
 * @property timing the coalescing windows and the reconnect backoff; overridden by tests.
 */
class LiveSyncRepository(
    private val stream: SseStream,
    private val reader: ApiReader,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val timing: LiveSyncTiming = LiveSyncTiming(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : LiveSyncSource {
    /** How many collectors currently want each room, so a room leaves the union when the last one goes. */
    private val demand = mutableMapOf<LiveSyncTopic, Int>()

    private val guard = Mutex()

    /** The union of every collector's rooms; the connection follows this and nothing else. */
    private val union = MutableStateFlow<Set<LiveSyncTopic>>(emptySet())

    /**
     * The one connection, shared by every collector.
     *
     * A changed union reopens the stream after a debounce, and the connection is held only while a
     * collector is subscribed.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val shared: SharedFlow<LiveSyncEvent> =
        union
            .debounce(timing.unionSettle)
            .distinctUntilChanged()
            .flatMapLatest { topics -> if (topics.isEmpty()) emptyFlow() else connection(topics) }
            .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)

    /**
     * Builds the client on the one API client, so the stream and [publish] carry the same headers.
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
        flow {
            if (topics.isEmpty()) {
                return@flow
            }
            register(topics, +1)
            try {
                emitAll(
                    shared.mapNotNull { event ->
                        when (event) {
                            is LiveSyncEvent.Subscribed -> {
                                LiveSyncEvent.Subscribed(event.topics.intersect(topics))
                            }

                            is LiveSyncEvent.Changed -> {
                                event.takeIf { it.topic in topics }
                            }
                        }
                    },
                )
            } finally {
                withContext(NonCancellable) { register(topics, -1) }
            }
        }

    /**
     * Adds or removes one collector's reference-counted demand and republishes the union.
     *
     * @param topics the caller's rooms.
     * @param delta `+1` on subscribe, `-1` on teardown.
     */
    private suspend fun register(
        topics: Set<LiveSyncTopic>,
        delta: Int,
    ) {
        guard.withLock {
            for (topic in topics) {
                val next = (demand[topic] ?: 0) + delta
                if (next <= 0) {
                    demand.remove(topic)
                } else {
                    demand[topic] = next
                }
            }
            union.value = demand.keys.toSet()
        }
    }

    /**
     * The reconnecting connection for one topic union.
     *
     * @param topics the rooms to ask for.
     * @return a flow that emits until it is cancelled, reopening the stream as needed.
     */
    private fun connection(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent> =
        callbackFlow {
            val pending = mutableMapOf<LiveSyncTopic, MutableSet<String>>()
            val timers = mutableMapOf<LiveSyncTopic, Job>()
            val windows = Mutex()

            val worker =
                launch {
                    var attempt = 0
                    var refusals = 0
                    while (isActive) {
                        val attempt2 =
                            collectOnce(topics) { event ->
                                when (event) {
                                    is LiveSyncEvent.Subscribed -> {
                                        trySend(event)
                                    }

                                    is LiveSyncEvent.Changed -> {
                                        launch { coalesce(event, pending, timers, windows) { trySend(it) } }
                                    }
                                }
                            }
                        val verdict = attempt2.refused?.takeIf { it in FINAL_REFUSALS }
                        if (verdict != null) {
                            refusals++
                            if (refusals >= MAX_REFUSALS) {
                                KrtLog.d(LOG_TAG) { "giving up after $refusals refusals ($verdict)" }
                                trySend(LiveSyncEvent.Subscribed(emptySet()))
                                break
                            }
                        } else {
                            refusals = 0
                        }
                        attempt = if (attempt2.delivered) 0 else attempt + 1
                        delay(backoff(attempt))
                    }
                }
            awaitClose { worker.cancel() }
        }

    /**
     * Folds a change frame into its room's jittered window, emitting the union of sections once the
     * window closes.
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
     * The full-jittered coalescing window for a room: the long one for a tool-wide room, the short one
     * for a single resource (ADR-0094).
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
            KrtLog.d(LOG_TAG) { "signal for ${topic.wire} not relayed" }
        }
        return result
    }

    /**
     * Runs one connection to completion.
     *
     * @param topics the rooms to ask for.
     * @param emit where to put the parsed events.
     * @return what the attempt came to, telling a refusal status apart from a dropped socket.
     */
    private suspend fun collectOnce(
        topics: Set<LiveSyncTopic>,
        emit: (LiveSyncEvent) -> Unit,
    ): Attempt {
        val refused = AtomicInteger(NO_STATUS)
        val delivered = AtomicBoolean(false)
        val query = listOf(TOPICS_PARAM to topics.joinToString(",") { it.wire })
        stream
            .events(STREAM_PATH, query) { status -> refused.set(status) }
            .collect { event ->
                delivered.set(true)
                when (event.name) {
                    SUBSCRIBED_EVENT -> parseSubscribed(event.data)?.let(emit)
                    CHANGED_EVENT -> parseChanged(event.data)?.let(emit)
                    else -> Unit
                }
            }
        return Attempt(
            refused = refused.get().takeIf { it != NO_STATUS },
            delivered = delivered.get(),
        )
    }

    /**
     * What one connection attempt came to.
     *
     * @param refused the HTTP status if the stream was refused outright, else `null`.
     * @param delivered whether anything at all arrived, heartbeats included.
     */
    private data class Attempt(
        val refused: Int?,
        val delivered: Boolean,
    )

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
     * Parses a change frame; a frame naming a room this build does not know is dropped.
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
     * The full-jittered exponential wait before the next reconnect attempt.
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

    internal companion object {
        const val STREAM_PATH = "/api/v1/live-sync/stream"
        const val CHANGED_PATH = "/api/v1/live-sync/changed"
        const val TOPICS_PARAM = "topics"
        const val SUBSCRIBED_EVENT = "subscribed"
        const val CHANGED_EVENT = "changed"

        const val RECONNECT_MAX_SHIFT = 5

        /** Consecutive refusals before the client accepts the verdict and stops asking. */
        const val MAX_REFUSALS = 2

        const val HTTP_BAD_REQUEST = 400

        const val HTTP_FORBIDDEN = 403

        /**
         * Statuses that end the attempt loop for the current topic union instead of feeding the
         * reconnect backoff; `401` is absent because a renewed token fixes it.
         */
        val FINAL_REFUSALS = setOf(HTTP_BAD_REQUEST, HTTP_FORBIDDEN)

        /** Sentinel for "the stream opened", since the atomic cannot hold a null. */
        const val NO_STATUS = 0

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

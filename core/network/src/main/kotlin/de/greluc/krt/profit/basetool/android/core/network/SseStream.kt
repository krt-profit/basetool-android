/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration

/**
 * One named event from a Server-Sent-Event stream.
 *
 * @property name the event name, or `"message"` when the server sent none — the SSE default.
 * @property data the accumulated `data:` lines, newline-joined.
 */
data class SseEvent(
    val name: String,
    val data: String,
)

/**
 * Reads a Server-Sent-Event stream over the app's own HTTP client.
 *
 * **Hand-rolled rather than `okhttp-sse`.** The framing this needs is three rules — `event:`,
 * `data:`, blank line ends the event — and the library would be a new third-party dependency, which
 * this project's privacy gate treats as a decision rather than a detail. It would also not supply
 * what actually matters here, which is the reconnect policy: the caller owns that, because only the
 * caller knows whether the screen behind the stream is still on show.
 *
 * **The read timeout is removed for this client, and only for it.** A stream is idle by design
 * between events — the server heartbeats every twenty seconds — so the shared client's read timeout
 * would tear the connection down as a matter of course. The derived client keeps the connection
 * pool, the interceptors and therefore the bearer token and the mandatory headers of the original.
 *
 * @property httpClient the API client; a derived copy without a read timeout is used.
 * @property baseUrl the flavour's API origin.
 */
class SseStream(
    httpClient: OkHttpClient,
    private val baseUrl: String,
) {
    private val streamClient: OkHttpClient =
        httpClient.newBuilder()
            .readTimeout(Duration.ZERO)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * Opens [path] and emits every event until the stream ends or the collector is cancelled.
     *
     * The flow **completes** when the server closes the stream — which it does every thirty minutes
     * by design, and whenever a sixth connection for the same member evicts the oldest. Completion
     * is therefore normal, not an error, and reconnecting is the caller's decision.
     *
     * A non-2xx answer completes the flow as well rather than throwing: a `401` here means the
     * token expired and the caller should stop, not retry in a loop.
     *
     * @param path the stream's path, e.g. `/api/v1/notifications/stream`.
     * @return a cold flow of events; collecting it opens the connection, cancelling closes it.
     */
    fun events(path: String): Flow<SseEvent> =
        callbackFlow {
            val call =
                streamClient.newCall(
                    Request.Builder()
                        .url("$baseUrl$path".toHttpUrl())
                        .header("Accept", "text/event-stream")
                        // A proxy that cached a stream would serve the first member's bytes to the
                        // next one. The backend says so too; saying it here as well costs nothing.
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build(),
                )

            val reader = Thread { runReader(call, this) }
            reader.isDaemon = true
            reader.start()

            awaitClose {
                // Cancelling the call is what unblocks the reader thread; closing the body from
                // another thread is not safe, and letting it run would hold a socket per screen.
                call.cancel()
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Runs the reader to the end of the stream and closes the producer, whatever happens.
     *
     * **The broad catch is deliberate and the detekt rule is wrong at this call site.** This is the
     * top of a plain `Thread`: anything that escapes takes the whole process down. A member losing
     * the push channel is a degraded app, and the poll behind it covers that; a member losing the
     * app because a line failed to parse is a crash report.
     *
     * @param call the prepared request.
     * @param scope the producer to send events to and to close when the stream ends.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runReader(
        call: Call,
        scope: ProducerScope<SseEvent>,
    ) {
        try {
            readInto(call, scope)
        } catch (failure: IOException) {
            // The usual ending: the collector cancelled the call, or the network went.
            KrtLog.d(LOG_TAG) { "stream ended: ${failure.javaClass.simpleName}" }
        } catch (failure: RuntimeException) {
            KrtLog.w(LOG_TAG) { "stream failed: ${failure.javaClass.simpleName}" }
        } finally {
            scope.close()
        }
    }

    /**
     * Executes [call] and feeds every parsed event into [scope].
     *
     * Split out of [events] so the framing is readable on its own, and so the thread body above is
     * just "run this, let nothing escape".
     *
     * A line that matches nothing here is a comment (`: keep-alive`), an `id:` or a `retry:`. All
     * three belong to the format and carry nothing this stream uses — and a comment in particular
     * must **not** flush an event, or an idle connection would deliver an empty one every few
     * seconds and a caller re-reading on each would be polling while believing it was using push.
     *
     * @param call the prepared request.
     * @param scope the producer to send events to.
     */
    private fun readInto(
        call: Call,
        scope: ProducerScope<SseEvent>,
    ) {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                KrtLog.w(LOG_TAG) { "stream refused with ${response.code}" }
                return
            }
            val source = response.body.source()
            var name = DEFAULT_EVENT
            val data = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8LineStrict()
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty() || name != DEFAULT_EVENT) {
                            scope.trySend(SseEvent(name, data.toString()))
                        }
                        name = DEFAULT_EVENT
                        data.setLength(0)
                    }

                    line.startsWith(EVENT_PREFIX) -> {
                        name = line.removePrefix(EVENT_PREFIX).trim()
                    }

                    line.startsWith(DATA_PREFIX) -> {
                        if (data.isNotEmpty()) {
                            data.append('\n')
                        }
                        data.append(line.removePrefix(DATA_PREFIX).trim())
                    }
                }
            }
        }
    }

    private companion object {
        /** The SSE default event name, per the format. */
        const val DEFAULT_EVENT = "message"

        const val EVENT_PREFIX = "event:"
        const val DATA_PREFIX = "data:"

        /** Log subsystem. No event data is ever logged — it can name a member. */
        const val LOG_TAG = "sse"
    }
}

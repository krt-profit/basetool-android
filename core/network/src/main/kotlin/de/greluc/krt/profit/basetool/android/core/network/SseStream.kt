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
 * Reads a Server-Sent-Event stream over the app's own HTTP client, with a hand-rolled parser.
 *
 * Uses a derived client without a read timeout but with the original pool and interceptors; the
 * reconnect policy belongs to the caller. Open so tests can substitute it.
 *
 * @property httpClient the API client; a derived copy without a read timeout is used.
 * @property baseUrl the flavour's API origin.
 */
open class SseStream(
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
     * The flow completes normally when the server closes the stream or answers non-2xx; reconnecting is
     * the caller's decision.
     *
     * @param path the stream's path, e.g. `/api/v1/notifications/stream`.
     * @param query query parameters, encoded through the URL builder.
     * @param onRefused called with the status when the server answers non-2xx, before the flow
     *   completes.
     * @return a cold flow of events; collecting it opens the connection, cancelling closes it.
     */
    open fun events(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        onRefused: ((Int) -> Unit)? = null,
    ): Flow<SseEvent> =
        callbackFlow {
            val url =
                "$baseUrl$path".toHttpUrl().newBuilder()
                    .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
                    .build()
            val call =
                streamClient.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build(),
                )

            val reader = Thread { runReader(call, this, onRefused) }
            reader.isDaemon = true
            reader.start()

            awaitClose {
                call.cancel()
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Runs the reader to the end of the stream and closes the producer, catching everything since this
     * is the top of a plain `Thread`.
     *
     * @param call the prepared request.
     * @param scope the producer to send events to and to close when the stream ends.
     * @param onRefused reports a non-2xx answer to the caller.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runReader(
        call: Call,
        scope: ProducerScope<SseEvent>,
        onRefused: ((Int) -> Unit)?,
    ) {
        try {
            readInto(call, scope, onRefused)
        } catch (failure: IOException) {
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
     * Comment, `id:` and `retry:` lines are ignored and never flush an event.
     *
     * @param call the prepared request.
     * @param scope the producer to send events to.
     * @param onRefused reports a non-2xx answer to the caller.
     */
    private fun readInto(
        call: Call,
        scope: ProducerScope<SseEvent>,
        onRefused: ((Int) -> Unit)?,
    ) {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                KrtLog.w(LOG_TAG) { "stream refused with ${response.code}" }
                onRefused?.invoke(response.code)
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

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * Marks the body of every `POST` and `PATCH` as one-shot, so OkHttp's own recovery never sends the
 * same write a second time (REQ-APP-API-009, ADR-0023).
 *
 * **Why this exists.** The client keeps `retryOnConnectionFailure` on, and OkHttp then replays a
 * request on its own in two situations: an `IOException` *after* the request went out (a pooled
 * connection the server had already closed, a reset while the answer was on its way), and an HTTP
 * `408`. For a read that is exactly right. For a write it is not: the server may already have
 * booked the transfer, taken the order or added the participant, and the replay does it again —
 * or, for a versioned row, answers `409` for a save that in fact succeeded. OkHttp skips both
 * replays when the body reports [RequestBody.isOneShot], and that flag is the only switch it
 * offers per request. The bodies the app builds (`toRequestBody`, `FormBody`, `MultipartBody`)
 * all report `false`.
 *
 * **What stays retried, and why that is safe.**
 *
 * - A connection that fails *before* the request is sent — an HTTP/2 connection the server shut
 *   down (`ConnectionShutdownException`), a route that could not connect — is still retried: the
 *   server never saw the write, so repeating it cannot double it. OkHttp tells the two cases apart
 *   itself; the one-shot flag only vetoes the "may have been sent" branch.
 * - `GET`, `PUT` and `DELETE` are idempotent by definition: repeating one leaves the server where
 *   the first left it. On this API a `PUT` that replaces a versioned row is even refused with `409`
 *   on replay rather than applied twice. Only `POST` and `PATCH` can take effect twice.
 * - The app's own 401 retry in [TokenRefreshInterceptor] is untouched. It re-sends a request the
 *   server *refused* before doing anything, and it can, because one-shot is a flag read by
 *   OkHttp's recovery and not a property of the bytes: the wrapped body is still a replayable
 *   buffer and writes the same content every time it is asked to.
 *
 * The cost is honest: a write that hits a stale pooled connection now fails with
 * [ApiError.Network] and the member presses save again, where OkHttp used to repeat it silently.
 * That is the trade REQ-APP-API-009 makes — a failure the member sees and can retry, instead of a
 * duplicate nobody sees.
 */
internal class OneShotWriteInterceptor : Interceptor {
    /**
     * Wraps the body of a non-idempotent write and passes every other request through unchanged.
     *
     * @param chain the call.
     * @return the response to the (possibly re-bodied) request.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (request.method !in NON_IDEMPOTENT_METHODS || body == null || body.isOneShot()) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().method(request.method, OneShotBody(body)).build())
    }

    /**
     * A body that says it may be written once, and otherwise behaves exactly like [delegate].
     *
     * @property delegate the body the caller built; its content type, length and bytes are kept.
     */
    private class OneShotBody(
        private val delegate: RequestBody,
    ) : RequestBody() {
        /**
         * The caller's content type, so the server still reads JSON as JSON.
         *
         * @return the delegate's media type, or `null` when it declared none.
         */
        override fun contentType(): MediaType? = delegate.contentType()

        /**
         * The caller's length, so the request keeps its `Content-Length` rather than switching to
         * chunked transfer encoding.
         *
         * @return the delegate's length in bytes, or `-1` when it is unknown.
         */
        override fun contentLength(): Long = delegate.contentLength()

        /**
         * Writes the delegate's bytes.
         *
         * @param sink where OkHttp wants the body.
         */
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)

        /**
         * The whole point: tells OkHttp's retry and `408` logic that this body must not be sent
         * again on its own initiative.
         *
         * @return always `true`.
         */
        override fun isOneShot(): Boolean = true

        /**
         * Kept from the delegate; no body the app builds is duplex.
         *
         * @return whether the delegate streams in both directions.
         */
        override fun isDuplex(): Boolean = delegate.isDuplex()
    }

    private companion object {
        /**
         * The verbs whose repetition can change server state twice. `PUT` is absent on purpose —
         * see the class comment.
         */
        val NON_IDEMPOTENT_METHODS = setOf("POST", "PATCH")
    }
}

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
 * Connection failures before the request is sent are still retried, idempotent verbs are
 * untouched, and the app's own 401 retry in [TokenRefreshInterceptor] still works.
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
         * The verbs whose repetition can change server state twice; `PUT` is idempotent and excluded.
         */
        val NON_IDEMPOTENT_METHODS = setOf("POST", "PATCH")
    }
}

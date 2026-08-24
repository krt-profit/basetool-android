/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits this call's response, cancelling the underlying socket when the coroutine is cancelled.
 *
 * The alternative — `withContext(Dispatchers.IO) { execute() }` — looks equivalent and is not: a
 * cancelled coroutine there leaves the request running to completion, holding a connection and a
 * thread for the full read timeout. On a screen a member navigated away from that is the difference
 * between an abandoned request and a 30-second one.
 *
 * A response that arrives after cancellation is **closed rather than dropped**. An unclosed
 * [Response] leaks its connection out of the pool, and OkHttp reports it only as a `StrictMode`-ish
 * warning much later, in an unrelated call.
 *
 * **This resumes on the caller's dispatcher, which for a ViewModel is the main thread — so the
 * caller must move the response handling off it.** `ApiReader` wraps both of its handling blocks in
 * `withContext(Dispatchers.IO)` for that reason, and the reason is not theoretical: closing an
 * HTTP/2 response whose body was never read makes OkHttp write an `RST_STREAM` to the socket, and
 * that is a `NetworkOnMainThreadException` that crashes the app. Found on a device, on the one
 * write that returns a body the app does not parse. Reads only escaped it because a body already
 * buffered in memory needs no socket. Note that this is a different point from the paragraph
 * above: the objection there is to replacing `enqueue` with `withContext { execute() }`, which
 * would break cancellation. Wrapping the *handling* keeps both properties.
 *
 * @return the response; the caller owns it and must close it
 * @throws IOException if the call fails before a response was received
 */
suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (continuation.isCancelled) {
                        response.close()
                    } else {
                        continuation.resume(response)
                    }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }
            },
        )
    }

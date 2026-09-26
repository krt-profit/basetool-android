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
 * A response arriving after cancellation is closed. This resumes on the caller's dispatcher, so the
 * caller must handle the response off the main thread.
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

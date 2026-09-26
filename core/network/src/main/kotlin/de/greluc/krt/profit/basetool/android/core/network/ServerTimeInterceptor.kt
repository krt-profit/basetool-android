/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.time.Instant

/**
 * Feeds every response's `Date` header into the [ServerClock], observing without modifying.
 *
 * A malformed or missing `Date` is ignored; `Response.receivedResponseAtMillis` is the device-side
 * reading.
 *
 * @property serverClock the clock to update
 */
class ServerTimeInterceptor(
    private val serverClock: ServerClock,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val serverDate = response.headers.getInstant("Date")
        if (serverDate != null) {
            serverClock.observe(
                serverTime = serverDate,
                deviceTime = Instant.ofEpochMilli(response.receivedResponseAtMillis),
            )
        }
        return response
    }
}

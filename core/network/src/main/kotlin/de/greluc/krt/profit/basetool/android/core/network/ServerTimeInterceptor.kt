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
 * Feeds every response's `Date` header into the [ServerClock].
 *
 * Placed as an application interceptor so it also sees responses served from a cache or produced by
 * a redirect chain; it never modifies the request or the response, only observes.
 *
 * A malformed or missing `Date` is ignored rather than treated as an error: the header is advisory
 * here, and a single odd response must not poison the offset. `Response.receivedResponseAtMillis`
 * is used as the device-side reading so the pair is taken at the same instant OkHttp completed the
 * exchange, not whenever this interceptor happens to run.
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

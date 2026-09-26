/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the offset between the device clock and the server's, learned from response `Date`
 * headers, so DPoP proofs are timed against server time.
 *
 * Until a response is seen, [now] is the device clock. Thread-safe.
 */
class ServerClock {
    private val offset = AtomicReference(Duration.ZERO)

    /**
     * Records the server's view of "now" from a response.
     *
     * @param serverTime the parsed `Date` header of a response
     * @param deviceTime the device clock at the moment that response was received
     */
    fun observe(
        serverTime: Instant,
        deviceTime: Instant,
    ) {
        offset.set(Duration.between(deviceTime, serverTime))
    }

    /**
     * The current time as the server would state it.
     *
     * @return device time corrected by the last observed offset, or plain device time when no
     *   response has been seen yet
     */
    fun now(): Instant = Instant.now().plus(offset.get())

    /**
     * How far the device clock is from the server's, as last observed; for diagnostics and tests, while
     * callers needing server time use [now].
     *
     * @return the offset; [Duration.ZERO] before the first response
     */
    fun observedOffset(): Duration = offset.get()
}

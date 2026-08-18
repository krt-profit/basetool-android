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
 * Tracks the offset between this device's clock and the server's, so time-sensitive values can be
 * computed against **server** time rather than the raw device clock.
 *
 * This exists for DPoP. Keycloak accepts a proof lifetime of 10 s with 15 s of clock skew, which is
 * tighter than ordinary mobile clock drift — a phone whose clock is a minute off produces proofs
 * the token endpoint rejects, and the failure looks like "login broken" rather than "clock wrong".
 * The desktop extractor records clock drift as its primary DPoP failure mode (main repo
 * REQ-INGEST-012), so the app computes proof `iat` from here instead.
 *
 * The offset is learned from the `Date` header every response carries. Until a response has been
 * seen, [now] is the device clock — the honest fallback, since there is nothing better to use.
 *
 * Thread-safe: the offset is a single atomic reference, written from OkHttp's network threads and
 * read from wherever a proof is built.
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
     * How far the device clock is from the server's, as last observed.
     *
     * Exposed for diagnostics and tests, not for callers doing their own arithmetic — a caller that
     * needs server time calls [now].
     *
     * @return the offset; [Duration.ZERO] before the first response
     */
    fun observedOffset(): Duration = offset.get()
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The wait before a refused first load is retried (design chapter 14).
 *
 * - A `503` climbs the fixed 3, 6, 12, 30 s ladder, then holds.
 * - A `429` uses the server's `Retry-After`, falling back to the ladder when it is absent or unusable.
 *
 * Stateless: the caller keeps the attempt count and resets it on a manual retry.
 */
object RetryBackoff {
    /** The ladder of design chapter 14, in order; the last step repeats. */
    private val LADDER = listOf(3.seconds, 6.seconds, 12.seconds, 30.seconds)

    /**
     * The longest `Retry-After` that is obeyed as given.
     *
     * A server that asks for an hour is not wrong, but a screen that sat on a live countdown for an
     * hour would be a frozen app in the member's eyes. Past the cap the ladder's own ceiling is
     * used and the member can retry by hand, which is the honest failure: late rather than stuck.
     */
    private val RETRY_AFTER_CAP = 5.seconds * 12

    /**
     * The wait before attempt number [attempt].
     *
     * @param attempt how many attempts have already failed; `0` for the first wait.
     * @param retryAfterSeconds the server's `Retry-After` in seconds, or `null` when absent or
     *   unparseable; zero or negative counts as absent.
     * @return the wait.
     */
    fun next(
        attempt: Int,
        retryAfterSeconds: Long? = null,
    ): Duration {
        val serverAsked = retryAfterSeconds?.takeIf { it > 0 }?.seconds
        if (serverAsked != null && serverAsked <= RETRY_AFTER_CAP) {
            return serverAsked
        }
        val step = attempt.coerceAtLeast(0).coerceAtMost(LADDER.lastIndex)
        return LADDER[step]
    }

    /**
     * Parses a `Retry-After` header in delta-seconds form; the HTTP-date form is not read, so device clock skew cannot
     * distort the wait.
     *
     * @param header the raw header value, or `null`.
     * @return the seconds, or `null` when there is nothing usable.
     */
    fun parseRetryAfter(header: String?): Long? = header?.trim()?.toLongOrNull()?.takeIf { it > 0 }
}

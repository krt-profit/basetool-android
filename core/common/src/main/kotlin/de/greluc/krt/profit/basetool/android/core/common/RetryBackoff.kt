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
 * The wait before a refused first load is retried (design chapter 14, `REQ-APP-UI-*`).
 *
 * Two rules, and they are not the same rule:
 *
 * - a **`503`** climbs the fixed ladder the design names — 3 → 6 → 12 → 30 seconds, then holds;
 * - a **`429`** uses the server's own `Retry-After` when it sent one, because the server knows when
 *   its bucket refills and the client is guessing. Only when the header is missing or unusable does
 *   it fall back to the ladder.
 *
 * Honouring `Retry-After` is what keeps a rate-limited client from making the limit worse: a ladder
 * that retries sooner than the server said spends a token that was never going to be granted, and a
 * ladder that retries much later leaves the member waiting for no reason.
 *
 * **A manual retry resets the ladder.** The member pressing the button is new information — they are
 * still there and still want it — and inheriting a thirty-second wait from an automatic attempt they
 * did not make would punish them for waiting.
 *
 * Stateless and pure: the caller keeps the attempt count, which is what lets a screen reset it on a
 * manual retry without this class knowing anything about screens.
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
     * @param retryAfterSeconds the server's `Retry-After`, in seconds, or `null` when it sent none
     *   or sent something unparseable. A zero or negative value is treated as absent — a server
     *   telling a rate-limited client to retry immediately is not an instruction worth following.
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
     * Parses a `Retry-After` header value that carries a number of seconds.
     *
     * Only the delta-seconds form is read. The HTTP-date form is legal and this server does not
     * send it; parsing it would mean trusting the device clock against the server's, and a clock
     * skew would turn a three-second wait into a wait of hours or none at all.
     *
     * @param header the raw header value, or `null`.
     * @return the seconds, or `null` when there is nothing usable.
     */
    fun parseRetryAfter(header: String?): Long? = header?.trim()?.toLongOrNull()?.takeIf { it > 0 }
}

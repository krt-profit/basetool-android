/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** The retry ladder of design chapter 14, and when the server overrides it. */
class RetryBackoffTest {
    @Test
    fun `the ladder is the one the design names, and it holds at the top`() {
        assertEquals(3.seconds, RetryBackoff.next(0))
        assertEquals(6.seconds, RetryBackoff.next(1))
        assertEquals(THIRD_STEP.seconds, RetryBackoff.next(2))
        assertEquals(TOP_STEP.seconds, RetryBackoff.next(LAST_RUNG))
        // It holds rather than growing: a screen the member is still looking at must keep trying at
        // a rate they can perceive as trying.
        assertEquals(TOP_STEP.seconds, RetryBackoff.next(FAR_PAST_THE_TOP))
    }

    @Test
    fun `the server's Retry-After wins over the ladder`() {
        // The server knows when its bucket refills; the client is guessing. Retrying sooner spends
        // a token that was never going to be granted.
        assertEquals(SERVER_ASKED.seconds, RetryBackoff.next(attempt = 0, retryAfterSeconds = SERVER_ASKED))
        assertEquals(SERVER_ASKED.seconds, RetryBackoff.next(attempt = LAST_RUNG, retryAfterSeconds = SERVER_ASKED))
    }

    @Test
    fun `an absent, zero or negative Retry-After falls back to the ladder`() {
        // "Retry immediately" from a server that just rate-limited you is not an instruction worth
        // following.
        assertEquals(3.seconds, RetryBackoff.next(attempt = 0, retryAfterSeconds = null))
        assertEquals(3.seconds, RetryBackoff.next(attempt = 0, retryAfterSeconds = 0))
        assertEquals(6.seconds, RetryBackoff.next(attempt = 1, retryAfterSeconds = NEGATIVE))
    }

    @Test
    fun `an unreasonably long Retry-After is capped back onto the ladder`() {
        // A live countdown running for an hour is a frozen app in the member's eyes. Late rather
        // than stuck: the ladder's ceiling, and the manual retry stays available.
        assertEquals(TOP_STEP.seconds, RetryBackoff.next(attempt = LAST_RUNG, retryAfterSeconds = AN_HOUR))
    }

    @Test
    fun `a negative attempt count cannot walk off the ladder`() {
        assertEquals(3.seconds, RetryBackoff.next(-1))
    }

    @Test
    fun `only the delta-seconds form of Retry-After is read`() {
        assertEquals(TWELVE, RetryBackoff.parseRetryAfter("12"))
        assertEquals(TWELVE, RetryBackoff.parseRetryAfter("  12 "))
        assertNull(RetryBackoff.parseRetryAfter(null))
        assertNull(RetryBackoff.parseRetryAfter("0"))
        // The HTTP-date form is legal and deliberately not parsed: it would mean trusting the
        // device clock against the server's, and a skew turns three seconds into hours or none.
        assertNull(RetryBackoff.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    private companion object {
        /** A plausible `Retry-After`, well inside the cap. */
        const val SERVER_ASKED = 7L

        /** A server telling a rate-limited client to retry in the past. */
        const val NEGATIVE = -5L

        /** Past the cap: honouring it would freeze the screen for an hour. */
        const val AN_HOUR = 3_600L

        /** The ladder's third rung, named because detekt counts it and the design fixes it. */
        const val THIRD_STEP = 12L

        /** An attempt count well past the last rung, to prove the ladder holds instead of growing. */
        const val FAR_PAST_THE_TOP = 9

        /** The ladder's ceiling, which repeats rather than growing. */
        const val TOP_STEP = 30L

        /** Index of the last rung. */
        const val LAST_RUNG = 3

        const val TWELVE = 12L
    }
}

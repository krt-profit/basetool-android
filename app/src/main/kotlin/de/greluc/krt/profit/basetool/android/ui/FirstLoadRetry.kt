/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import de.greluc.krt.profit.basetool.android.core.common.RetryBackoff
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One second, in the units [delay] takes. */
private const val ONE_SECOND_MS = 1_000L

/**
 * The automatic retry behind design chapter 14's full-screen countdown (REQ-APP-UI-003).
 *
 * Every screen that loads from the server needs the same three rules, and repeating them per
 * ViewModel is how they drift — the Bank had the only copy, and the next nine screens would each
 * have re-derived the conditions from the one before it.
 *
 * **The three conditions, each a case where retrying would be wrong:**
 *
 * - Only a **retryable** failure. A `403` or a `404` answers the same in three seconds, and a
 *   countdown in front of one promises the member something that will not happen.
 * - Only when the screen has **nothing on it**. With content loaded, chapter 14 wants the banner
 *   and the member keeps their place; replacing loaded data with a wall takes away what they were
 *   reading to tell them the server is busy.
 * - Only **one** timer at a time, so a pull-to-refresh landing on a failure does not stack a
 *   second countdown on the first.
 *
 * The attempt count lives here rather than in [RetryBackoff], which is what lets [onManualRetry]
 * reset it: a member pressing the button is new information, and inheriting a thirty-second wait
 * from an automatic attempt they did not make would punish them for having waited.
 *
 * @property scope the ViewModel's scope; the countdown dies with the screen.
 * @property onCountdown receives the seconds left, and `null` when nothing is counting.
 * @property onRetry runs the load again — the same call the first load made.
 */
class FirstLoadRetry(
    private val scope: CoroutineScope,
    private val onCountdown: (Int?) -> Unit,
    private val onRetry: () -> Unit,
) {
    private var job: Job? = null
    private var attempts = 0

    /**
     * Starts the countdown after a failed load, when all three conditions hold.
     *
     * @param error what the read failed with.
     * @param hasContent whether the screen had content to keep.
     */
    fun onFailure(
        error: ApiError,
        hasContent: Boolean,
    ) {
        val retryable = error is ApiError.ServiceUnavailable || error is ApiError.RateLimited
        if (!retryable || hasContent || job?.isActive == true) {
            return
        }
        job =
            scope.launch {
                val wait =
                    RetryBackoff.next(
                        attempt = attempts,
                        retryAfterSeconds =
                            (error as? ApiError.RateLimited)?.retryAfter?.inWholeSeconds,
                    )
                attempts++
                var left = wait.inWholeSeconds.toInt()
                while (left > 0) {
                    onCountdown(left)
                    delay(ONE_SECOND_MS)
                    left--
                }
                onCountdown(null)
                onRetry()
            }
    }

    /** The member asked again. Cancels the countdown, starts the ladder over and reloads. */
    fun onManualRetry() {
        job?.cancel()
        attempts = 0
        onCountdown(null)
        onRetry()
    }

    /**
     * A load succeeded.
     *
     * Resets the ladder so a later outage starts at three seconds rather than wherever the last
     * one ended — without this, a screen that recovered would still be waiting thirty seconds a
     * week later.
     */
    fun onSuccess() {
        job?.cancel()
        attempts = 0
        onCountdown(null)
    }
}

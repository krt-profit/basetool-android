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
 * The automatic retry behind the full-screen first-load countdown (REQ-APP-UI-003).
 *
 * Retries only a retryable failure, only while the screen has no content, and runs one timer at a
 * time. The attempt count lives here so [onManualRetry] can reset it.
 *
 * @property scope the ViewModel's scope; the countdown dies with the screen.
 * @property onCountdown receives the seconds left, and `null` when nothing is counting.
 * @property onRetry runs the load again, the same call the first load made.
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

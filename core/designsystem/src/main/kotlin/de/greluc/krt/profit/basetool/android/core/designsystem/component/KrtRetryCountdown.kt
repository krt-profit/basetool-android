/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/**
 * The full-screen retry state of design chapter 14: a live countdown inside the orange ring
 * (`REQ-APP-UI-*`).
 *
 * **Only for a screen whose FIRST load failed.** Chapter 14 is explicit about this and it is the
 * rule most easily lost: a screen that already has content keeps it and gets the banner instead.
 * Replacing loaded data with a countdown would take away what the member was reading in order to
 * tell them the server is busy — which they can see from the banner without losing their place.
 *
 * The countdown value is passed in rather than run here. The waiting belongs to whoever owns the
 * retry — the same place that holds the attempt count and resets it on a manual press — and a
 * composable that ticked on its own would keep counting through a configuration change while the
 * real timer did something else.
 *
 * @param secondsLeft seconds until the automatic retry; the ring shows it as it counts down.
 * @param title the in-fiction headline, e.g. „Signal instabil".
 * @param message the plain explanatory line under it.
 * @param retryLabel the manual-retry button's label.
 * @param onRetry pressed by the member. A manual retry resets the backoff, which is the caller's
 *   job — this only reports the press.
 * @param modifier layout modifier.
 */
@Composable
fun KrtRetryCountdown(
    secondsLeft: Int,
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s24)
                .testTag("krt-retry-countdown"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
    ) {
        Box(contentAlignment = Alignment.Center) {
            KrtSpinner()
            Text(
                // The number sits inside the ring, per chapter 14. Clamped at zero: a negative
                // countdown is a timer that overran, and showing "-2" would report a bug to the
                // member instead of to us.
                text = secondsLeft.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.TextMuted,
                modifier = Modifier.testTag("krt-retry-seconds"),
            )
        }
        // Chapter 14 draws this state's heading uppercase at title size in the warning tint
        // (`#FFD23F`), not as a small neutral line. It is the difference between a screen that
        // says the app is retrying and one that looks like it has nothing to show: the same words
        // in `titleSmall` grey read as a caption under a spinner.
        Text(
            text = title.krtUppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = KrtTheme.colors.warning,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        KrtOutlineButton(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.testTag("krt-retry-button"),
        )
    }
}

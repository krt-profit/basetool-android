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
 * The full-screen retry state: a live countdown inside the orange ring (`REQ-APP-UI-*`).
 *
 * Only for a screen whose first load failed; a screen with content keeps it and shows the banner.
 * The countdown is driven by the caller that owns the retry.
 *
 * @param secondsLeft seconds until the automatic retry; the ring shows it as it counts down.
 * @param title the in-fiction headline, e.g. „Signal instabil".
 * @param message the plain explanatory line under it.
 * @param retryLabel the manual-retry button's label.
 * @param onRetry pressed by the member; resetting the backoff is the caller's job.
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
                text = secondsLeft.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.TextMuted,
                modifier = Modifier.testTag("krt-retry-seconds"),
            )
        }
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

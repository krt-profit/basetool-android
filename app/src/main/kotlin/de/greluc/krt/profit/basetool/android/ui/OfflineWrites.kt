/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * How far a write action fades while the device has no network.
 *
 * The app never queues a write (REQ-APP-PI-003, REQ-APP-INV-010): a booking taken offline would land minutes later
 * against a Lager that has moved on, and the member would never see the conflict. A faded, disabled
 * control says that plainly.
 */
const val DISABLED_WRITE_ALPHA: Float = 0.45f

/** The band that says why the write actions are greyed out. */
@Composable
fun OfflineBand() {
    Text(
        text = stringResource(R.string.offline_writes_disabled),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
    )
}

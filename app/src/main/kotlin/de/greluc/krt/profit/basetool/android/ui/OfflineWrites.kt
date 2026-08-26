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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOfflineBanner
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
    // Chapter 14's offline exemplar is a banner, not a sentence: a yellow edge, the wifi-off
    // glyph, the state uppercase in the warning tint, and the reason under it. This was one muted
    // line the eye skips — on a screen whose buttons have just gone grey, the notice that explains
    // why has to be the thing you see first.
    //
    // No "Zuletzt aktualisiert" stamp: the artboard quotes one, and the app has nothing truthful to
    // put there — it holds no cache and records no load time, so any timestamp would be invented.
    // The same goes for the CACHE chip the artboard puts on the row beneath.
    KrtOfflineBanner(
        title = stringResource(R.string.offline_banner_title),
        lastUpdated = stringResource(R.string.offline_writes_disabled),
        modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
    )
}

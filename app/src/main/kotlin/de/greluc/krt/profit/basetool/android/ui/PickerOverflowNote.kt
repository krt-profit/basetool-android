/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette

/**
 * The line a server-side picker shows when its page is not the whole catalogue (ADR-0104).
 *
 * Draws nothing when there is nothing more.
 *
 * @param more whether the catalogue holds candidates this page does not carry.
 * @param modifier layout modifier.
 */
@Composable
fun PickerOverflowNote(
    more: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!more) {
        return
    }
    Text(
        text = stringResource(R.string.picker_more_matches),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = modifier,
    )
}

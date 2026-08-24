/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** How much wider the detail pane is than the list beside it. */
private const val DETAIL_WEIGHT = 1.5f

/**
 * The tablet's list-detail layout, and the phone's plain list.
 *
 * Design ch. 00 and 03 put a list beside its detail on every wide window; chapters 06, 10, 11 and
 * 12 each name their own pairing ("Queue + Detail", "Orders + Detail", "Konten + Detail"). Below
 * the breakpoint the caller navigates to the detail as its own screen instead, which is why this
 * takes a nullable [detail] rather than owning the selection: **the list is the same list in both
 * layouts**, and only what a tap does differs.
 *
 * The two panes are separated by a hairline rather than by elevation or a gap, because the design
 * system draws every division that way — depth comes from hairlines and brackets, never shadow.
 *
 * With nothing selected the detail side shows a short prompt rather than staying blank. An empty
 * half-screen reads as a screen that failed to load; a sentence naming what to tap reads as a
 * screen waiting for you.
 *
 * @param detail the detail pane for the current selection, or `null` when nothing is selected.
 *   Ignored entirely on a narrow window.
 * @param modifier layout modifier.
 * @param emptyDetailMessage the prompt shown while nothing is selected.
 * @param list the list pane, which is the whole screen on a phone.
 */
@Composable
fun KrtListDetail(
    detail: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    emptyDetailMessage: String? = null,
    list: @Composable () -> Unit,
) {
    if (!isWideWindow()) {
        Box(modifier = modifier.fillMaxSize()) { list() }
        return
    }
    Row(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { list() }
        Box(
            modifier =
                Modifier
                    .width(KrtSpacing.hairline)
                    .fillMaxHeight()
                    .background(KrtPalette.Gray3),
        )
        Box(
            modifier = Modifier.weight(DETAIL_WEIGHT).fillMaxHeight(),
            contentAlignment = if (detail == null) Alignment.Center else Alignment.TopStart,
        ) {
            if (detail != null) {
                detail()
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_chevron_right,
                    title = stringResource(R.string.list_detail_none_title),
                    message = emptyDetailMessage ?: stringResource(R.string.list_detail_none_message),
                    modifier = Modifier.padding(KrtSpacing.lg),
                )
            }
        }
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.LocalScreenTopBar
import de.greluc.krt.profit.basetool.android.navigation.ScreenTopBar
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * How much wider the detail pane is than the list beside it.
 */
private const val DETAIL_WEIGHT = 2f

/**
 * The tablet's list-detail layout, and the phone's plain list.
 *
 * On a narrow window only [list] is shown and the caller navigates to the detail itself. The panes
 * are divided by a hairline; with nothing selected the detail side shows [emptyDetailMessage].
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
                DetailPane(detail)
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_chevron_right,
                    title = stringResource(R.string.list_detail_none_title),
                    message = emptyDetailMessage ?: stringResource(R.string.list_detail_none_message),
                    modifier = Modifier.padding(KrtSpacing.s16),
                )
            }
        }
    }
}

/**
 * One detail pane, drawing as its own head the [ScreenTopBar] its content publishes.
 *
 * The publication goes to a pane-local slot instead of the app bar, so the app bar keeps naming the
 * section.
 *
 * @param detail the pane's content.
 */
@Composable
private fun DetailPane(detail: @Composable () -> Unit) {
    val head = remember { mutableStateOf<ScreenTopBar?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        head.value?.let { published ->
            DetailPaneHead(published)
        }
        CompositionLocalProvider(LocalScreenTopBar provides head) { detail() }
    }
}

/**
 * The title, subtitle and actions a detail pane published, drawn as its head.
 *
 * Uses the top bar's title styling without its insets, org chip or bell.
 *
 * @param head what the pane published.
 */
@Composable
private fun DetailPaneHead(head: ScreenTopBar) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    head.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = KrtPalette.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    head.titleBadge?.invoke()
                }
                head.subtitle?.invoke()
            }
            head.actions?.invoke()
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KrtSpacing.hairline)
                    .background(KrtPalette.Gray3),
        )
    }
}

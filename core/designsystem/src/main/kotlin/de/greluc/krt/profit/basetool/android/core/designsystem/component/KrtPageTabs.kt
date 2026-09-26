/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * The page-level tab row of the design system's `.tab-nav`, where exactly one page shows.
 *
 * The open tab is white with a 3 dp orange underline; counts sit beside labels in orange. The row
 * scrolls horizontally rather than truncating labels.
 *
 * @param tabs the tabs, in order.
 * @param selectedIndex which one is showing.
 * @param onSelect a tab was picked, by index.
 * @param modifier layout modifier.
 */
@Composable
fun KrtPageTabs(
    tabs: List<KrtPageTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(KrtPalette.Gray4)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            tabs.forEachIndexed { index, tab ->
                PageTab(
                    tab = tab,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
        KrtHairlineRule()
    }
}

/**
 * One tab: its label, its count in orange, and the underline that marks the open one.
 *
 * @param tab what to draw.
 * @param selected whether this tab is showing.
 * @param onClick opens it.
 */
@Composable
private fun PageTab(
    tab: KrtPageTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .heightIn(min = KrtSpacing.touchTarget)
                .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TAB_PADDING_H, vertical = TAB_PADDING_V),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tab.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color =
                    when {
                        selected -> KrtPalette.White
                        tab.locked -> KrtPalette.TextMuted.copy(alpha = LOCKED_LABEL_ALPHA)
                        else -> KrtPalette.TextMuted
                    },
                maxLines = 1,
            )
            tab.count?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (tab.locked) {
                KrtIcon(
                    id = R.drawable.ic_krt_lock,
                    contentDescription = null,
                    size = LOCK_GLYPH,
                    tint = KrtPalette.TextMuted,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TAB_UNDERLINE)
                    .testTag(if (selected) TAB_UNDERLINE_TAG else "")
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/** A locked tab's label opacity — the design system's disabled-style 45 %. */
private const val LOCKED_LABEL_ALPHA = 0.45f

/** The lock glyph beside a locked tab's label. */
private val LOCK_GLYPH = 12.dp

/** The open tab's underline, for the test that measures it is not zero pixels wide. */
const val TAB_UNDERLINE_TAG: String = "krt-page-tab-underline"

/** Horizontal padding of a tab label — `.tab-nav .tab` is 14 px. */
private val TAB_PADDING_H = 14.dp

/** Vertical padding of a tab label — `.tab-nav .tab` is 11 px. */
private val TAB_PADDING_V = 11.dp

/** The open tab's underline — 3 px in `.tab-nav .tab.active`. */
private val TAB_UNDERLINE = 3.dp

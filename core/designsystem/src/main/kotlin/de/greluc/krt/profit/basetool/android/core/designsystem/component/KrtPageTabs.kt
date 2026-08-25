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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * The page-level tab row of the design system's `.tab-nav`.
 *
 * Text tabs on the dark-gray band with a hairline under them; the open one is white with a 3 dp
 * orange underline, and counts sit beside their label in orange. **Not** filter chips: a chip row
 * reads as a set a member can combine, and these are pages of which exactly one is showing. Both
 * the Einsatz detail (design ch. 06 artboard 2) and the Auftrag detail (ch. 10 artboard 2) use it,
 * which is why it lives here rather than in either screen.
 *
 * Horizontally scrollable, because seven German tab labels do not fit a 411 dp phone and the
 * alternative — truncating them — makes "Teilnehmer" and "Frequenzen" look alike.
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
        modifier = Modifier.clickable(onClick = onClick).heightIn(min = KrtSpacing.touchTarget),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TAB_PADDING_H, vertical = TAB_PADDING_V),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        ) {
            Text(
                text = tab.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) KrtPalette.White else KrtPalette.Gray2,
                maxLines = 1,
            )
            tab.count?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TAB_UNDERLINE)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/** Horizontal padding of a tab label — `.tab-nav .tab` is 14 px. */
private val TAB_PADDING_H = 14.dp

/** Vertical padding of a tab label — `.tab-nav .tab` is 11 px. */
private val TAB_PADDING_V = 11.dp

/** The open tab's underline — 3 px in `.tab-nav .tab.active`. */
private val TAB_UNDERLINE = 3.dp

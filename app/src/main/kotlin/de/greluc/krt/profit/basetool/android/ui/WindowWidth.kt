/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * Whether the window is wide enough for the layouts the design reserves for a tablet.
 *
 * One breakpoint decides all of them, because the design uses one: **840 dp**, Material's expanded
 * lower bound. Above it a chapter shows the navigation rail, a dense table instead of cards, and a
 * list beside its detail; below it the bottom bar, cards, and a detail that takes the whole screen.
 *
 * Kept in one place on purpose. The rule lived privately inside the navigation shell while every
 * other screen simply had no wide layout at all, so the first screen to grow one would otherwise
 * have re-derived the number — and a second copy of a breakpoint is a second copy that drifts.
 *
 * This recomposes when the window changes, which is what makes a fold, a rotation or a resized
 * multi-window swap the layout without losing state.
 *
 * @return `true` for expanded and wider windows.
 */
@Composable
fun isWideWindow(): Boolean =
    currentWindowAdaptiveInfoV2()
        .windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * The left/right gutter a screen's scrolling content sits in.
 *
 * **Zero on a phone, [KrtSpacing.s12] from a tablet's width up.** The design draws the two cases
 * differently and both readings are right for their own width: on a phone a dense row list is
 * full-bleed inside the frame (chapter 09's Lager tree spans 49…411 of a 48…412 screen) and there
 * is no room to give away; on a tablet the same list would put a row's first character against the
 * navigation rail and its last figure against the screen edge, two thousand device pixels apart,
 * beside a Hangar whose cards *are* inset.
 *
 * The breakpoint is **medium**, not [isWideWindow]'s expanded: this is a question about how much
 * width there is to spare, not about whether a list fits beside its detail, and a 700 dp window has
 * width to spare long before it has room for two panes.
 *
 * Put the result on the list's `contentPadding`, never on its `Modifier.padding`: the first keeps
 * the scrollbar and the overscroll at the true edge and lets a row draw its own full-width
 * background inside the inset, the second clips both.
 *
 * Cards were already inset at every width and do not use this — they are drawn that way on the
 * phone too, and passing them through here would take their gutter away.
 *
 * @return the horizontal inset for this window.
 */
@Composable
fun contentGutter(): Dp =
    if (
        currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    ) {
        KrtSpacing.s12
    } else {
        0.dp
    }

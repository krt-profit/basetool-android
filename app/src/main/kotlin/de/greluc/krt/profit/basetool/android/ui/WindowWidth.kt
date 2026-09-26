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
 * Whether the window is at least 840 dp wide, the single breakpoint for the design's tablet layouts.
 *
 * Recomposes when the window changes, so a fold, rotation or resize swaps the layout without losing state.
 *
 * @return `true` for expanded and wider windows.
 */
@Composable
fun isWideWindow(): Boolean =
    currentWindowAdaptiveInfoV2()
        .windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * The horizontal gutter for a screen's scrolling content: zero on a phone, [KrtSpacing.s12] from medium width up.
 *
 * Apply it as the list's `contentPadding`, not as `Modifier.padding`. Cards are inset already and do
 * not use it.
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

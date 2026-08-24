/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

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

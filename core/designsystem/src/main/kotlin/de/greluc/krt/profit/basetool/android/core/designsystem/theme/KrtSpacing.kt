/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing and metric scale. Nothing in the app may sit off this scale.
 *
 * Values come from `docs/design/android/01 Foundations.dc.html` where 1 CSS pixel equals 1 dp, with
 * one deliberate platform correction: the web design system's 44 px minimum touch target is rounded
 * up to Android's 48 dp ([touchTarget]).
 */
@Suppress("MagicNumber")
object KrtSpacing {
    /** 4 dp — the smallest step; icon-to-label gaps inside dense chips. */
    val xs = 4.dp

    /** 8 dp — gap between chips, icon-to-label in buttons. */
    val sm = 8.dp

    /** 12 dp — row internal gaps, vertical padding of the Fan Kit band. */
    val md = 12.dp

    /** 16 dp — the workhorse: screen edge margin and card padding. */
    val lg = 16.dp

    /** 24 dp — gap between sections. */
    val xl = 24.dp

    /** 32 dp — generous separation, e.g. above a screen's primary action block. */
    val xxl = 32.dp

    /** Minimum size of any interactive target (Android rounds the web system's 44 px up). */
    val touchTarget = 48.dp

    /** Minimum height of a dense list row; the whole row is the touch target. */
    val denseRow = 56.dp

    /** Content column cap on tablets — wider text columns hurt readability. */
    val contentMax = 1200.dp

    /** Width of every border in the system; depth comes from hairlines, never from shadows. */
    val hairline = 1.dp

    /** The orange under-rule below table heads and screen headers. */
    val headingRule = 2.dp

    /** Leg length of the HUD corner brackets (modals use 13 dp, see the modal component). */
    val bracket = 10.dp
}

/**
 * Duration of every colour and fade transition, in milliseconds.
 *
 * The design system allows exactly this one motion: 200 ms colour/fade. No bounces, no parallax, no
 * decorative movement — and animations must be skipped when the system reports reduced motion.
 *
 * Do not pass this to `tween(...)` directly. Read `KrtTheme.motionMs`, which resolves to `0` on a
 * device that asks for reduced motion; this constant is only the unreduced value it falls back to.
 */
const val KRT_MOTION_MS = 200

/**
 * Opacity of the press highlight — white at 8 %, per the design system's single ripple rule.
 *
 * Applied to every interaction state through the theme's `RippleConfiguration`, not per component.
 * Material's own default is a 8/10/10/16 % ladder, so leaving it unset renders presses at 10 %.
 */
const val KRT_RIPPLE_ALPHA = 0.08f

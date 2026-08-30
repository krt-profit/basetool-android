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
 * Values come from `docs/design/android/01 Foundations.dc.html` §5 (the scale strip) and §8 (the
 * binding margin table), where 1 CSS pixel equals 1 dp.
 *
 * > **Why the token names do not read xs 6 · sm 10 · md 16 · lg 20 · xl 24.** §8's Compose line
 * > names that scale, and the chapter's **own** scale strip in §5 lists `4 · 8 · 12 · 16 · 24 · 32`
 * > — which is what the artboards are drawn against and what these tokens have always carried. The
 * > margin table needs 4, 8, 10, 12, 14, 16, 20 and 24; renaming the five existing steps to the
 * > prose scale would move every one of ~830 call sites onto a value no artboard uses, and would
 * > still leave 4, 8, 12 and 14 without a token. So the drawn scale wins and the three steps the
 * > table needs and this object lacked — [cards], [inset], [sheet] — were added instead. The prose
 * > line is on the next design gap list.
 */
@Suppress("MagicNumber")
object KrtSpacing {
    /** 4 dp — the smallest step; icon-to-label gaps inside dense chips. */
    val xs = 4.dp

    /** 8 dp — gap between chips, icon-to-label in buttons, a list row's vertical padding. */
    val sm = 8.dp

    /** 10 dp — between cards in a list or a stack (design ch. 01 §8). */
    val cards = 10.dp

    /** 14 dp — horizontal padding inside a card and inside a list row (ch. 01 §8). */
    val inset = 14.dp

    /** 20 dp — a sheet's side margin, and a tablet's list column (ch. 01 §8). */
    val sheet = 20.dp

    /** 12 dp — row internal gaps, vertical padding of the Fan Kit band. */
    val md = 12.dp

    /** 16 dp — the workhorse: screen edge margin and card padding. */
    val lg = 16.dp

    /** 24 dp — gap between sections. */
    val xl = 24.dp

    /** 32 dp — generous separation, e.g. above a screen's primary action block. */
    val xxl = 32.dp

    /**
     * Minimum size of any interactive target — 44 dp (design ch. 01 §5).
     *
     * The navigation bar and the app bar use [navTarget] instead, which is Android's own 48 dp:
     * those two are hit while walking, and the chapter names them as the exception.
     */
    val touchTarget = 44.dp

    /** 48 dp — the floor for a navigation-bar or app-bar icon. */
    val navTarget = 48.dp

    /** Minimum height of a dense list row; the whole row is the touch target. */
    val denseRow = 56.dp

    /** Width of every border in the system; depth comes from hairlines, never from shadows. */
    val hairline = 1.dp

    /**
     * The focus glow's radius — 6 dp, the smallest of the three (design ch. 01 §1).
     *
     * The capped scale is **radius ≤ 12 dp and alpha ≤ 0.10** in three sizes: focus 6 dp/.10 on an
     * input, emphasis 12 dp/.07 on the bar carrying the one primary action, overlay 12 dp/.10 on a
     * sheet, a modal or a toast. Nothing in the app glows harder than that, and the 20 dp bloom
     * this replaced was above the cap on both counts.
     */
    val glowFocus = 6.dp

    /** The emphasis and overlay glow radius — 12 dp, the ceiling. */
    val glowOverlay = 12.dp

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

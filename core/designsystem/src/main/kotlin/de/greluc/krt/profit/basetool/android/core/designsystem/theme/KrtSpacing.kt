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
 * **Nine steps, positional names** — `4 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 32`, straight from
 * `01 Foundations.dc.html` §5 and the token artifact `artifacts/compose/KrtTokens.kt`. A screen
 * that wants 18 dp has a layout problem, not a special case.
 *
 * > **Why the names are numbers.** They were `xs · sm · md · lg · xl` here and `xs · sm · md ·
 * > lg · xl` in the artifact too — for **different values**. The app's `md` was 12 dp and the
 * > artifact's was 16, so any measurement carried from one side to the other landed on a value
 * > nobody had chosen, silently. The scale was ratified on 2026-08-30 with positional names for
 * > exactly that reason, and the rename moved 933 call sites in one pass.
 */
@Suppress("MagicNumber")
object KrtSpacing {
    /** 4 dp — field to helper text; icon-to-label inside a dense chip. */
    val s4 = 4.dp

    /** 8 dp — inside a dense row: chip gaps, icon-to-label in buttons. */
    val s8 = 8.dp

    /** 10 dp — between cards in a list or a stack. */
    val s10 = 10.dp

    /** 12 dp — between sections; a card's vertical padding. */
    val s12 = 12.dp

    /** 14 dp — a card's horizontal padding, and a list row's. */
    val s14 = 14.dp

    /** 16 dp — the workhorse: a phone's screen gutter and a modal's padding. */
    val s16 = 16.dp

    /** 20 dp — a sheet's side margin. */
    val s20 = 20.dp

    /** 24 dp — a tablet's content gutter, and the Materialbörse's column gutter. */
    val s24 = 24.dp

    /** 32 dp — generous separation, e.g. above a screen's primary action block. */
    val s32 = 32.dp

    /**
     * **Minimum tap area** — 44 dp, for rows, accordion heads and menu entries (ch. 01 §5).
     *
     * > Never derive a control's height from this. [controlHeight] is that, and conflating the two
     * > shrank every input, button, select and segmented control in the app the day ch. 01 lowered
     * > the floor from 48 to 44. Chapter 02 §1 now states all three sizes together for that reason.
     */
    val touchTarget = 44.dp

    /** 48 dp — the floor for a navigation-bar or app-bar icon slot. */
    val navIconFloor = 48.dp

    /**
     * 48 dp — the height of a **control**: field, button, icon button, select, segmented control
     * (design ch. 02 §1, and `KrtDimens.controlHeight` in the token artifact).
     *
     * The date/time pair matches it deliberately (§11): a form must not jump when one row of it is
     * a pair rather than a field.
     */
    val controlHeight = 48.dp

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

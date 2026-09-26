/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing and metric scale; nothing in the app may sit off it.
 *
 * Nine steps with positional names: `4 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 32` dp.
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
     * Minimum tap area — 44 dp, for rows, accordion heads and menu entries; control height is
     * [controlHeight], not this.
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

    /**
     * An icon-only row action, square.
     *
     * The same 48 dp as any other control (ch. 02 §1, ruled in round 14 · S3). Named separately
     * from [controlHeight] because the ruling is about a *button*, and the next reader looking for
     * „how big is a ✎" should find it rather than infer it from a field's height.
     */
    val iconButton = 48.dp

    /**
     * The **one** ratified departure from [iconButton]: 40 dp wide, [touchTarget] high.
     *
     * The Ablauf row's move buttons, and nothing else (ch. 18 §3 · E8). They share a row with a
     * tick and an overflow, which is the reason the exception exists — a control that does not
     * has no claim on it.
     */
    val iconButtonSmall = 40.dp

    /** Minimum height of a dense list row; the whole row is the touch target. */
    val denseRow = 56.dp

    /** Width of every border in the system; depth comes from hairlines, never from shadows. */
    val hairline = 1.dp

    /**
     * The focus glow's radius — 6 dp, the smallest of the three capped glows (radius ≤ 12 dp,
     * alpha ≤ 0.10).
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
 * Duration of every colour and fade transition, in milliseconds, before reduced motion is applied.
 *
 * Pass `KrtTheme.motionMs` to `tween(...)` instead, which resolves to `0` under reduced motion.
 */
const val KRT_MOTION_MS = 200

/**
 * Opacity of the press highlight — white at 8 %, per the design system's single ripple rule.
 *
 * Applied to every interaction state through the theme's `RippleConfiguration`, not per component.
 * Material's own default is a 8/10/10/16 % ladder, so leaving it unset renders presses at 10 %.
 */
const val KRT_RIPPLE_ALPHA = 0.08f

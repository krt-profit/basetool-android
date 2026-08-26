/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

/**
 * Which rung of the ladder an instant falls on.
 *
 * Exists for one question a caller genuinely has to ask: **does the rendered timestamp already
 * contain a clock reading?** Design ch. 06 pairs an Einsatz row as „TS 20:30 · in 2 Std." — an
 * absolute time and a distance — and the lower rungs are compounds that carry their own time, so a
 * row that prints both ends up saying „TS 20:44 · gestern, 20:44".
 */
enum class KrtTimeRung {
    /** „vor 4 Min.", „in 2 Std.", „morgen" — a distance, with no clock in it. */
    DISTANCE,

    /** „gestern, 21:14" — the previous calendar day, with its time. */
    YESTERDAY,

    /** „15.08., 09:30" — older than yesterday, with date and time. */
    DATED,
    ;

    /** Whether this form already prints a time of day. */
    val carriesClock: Boolean get() = this != DISTANCE
}

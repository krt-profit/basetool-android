/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

/**
 * Which rung of the relative-time ladder an instant falls on.
 *
 * Lets a caller tell whether the rendered timestamp already contains a clock time, so it does not
 * print the time twice.
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

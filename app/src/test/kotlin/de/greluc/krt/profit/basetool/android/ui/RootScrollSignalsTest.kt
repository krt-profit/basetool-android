/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The counter behind design chapter 03's „re-tap scrolls to top".
 *
 * The scrolling itself is Compose's and is verified on a device. What this pins is the property
 * that makes the rule safe: **counters are per route**. One shared counter is the obvious
 * implementation and is wrong — every screen watching it would jump to the top the next time it
 * was composed, so a member returning to a list they had scrolled would silently lose their place
 * because they had once re-tapped a different tab.
 */
class RootScrollSignalsTest {
    private companion object {
        const val LAGER = "inventory"
        const val ORDERS = "orders"
    }

    @Test
    fun `a destination never re-tapped has no pending request`() {
        assertEquals(0, RootScrollSignals().ticksFor(LAGER))
    }

    @Test
    fun `each re-tap is one request`() {
        val signals = RootScrollSignals()

        signals.request(LAGER)
        signals.request(LAGER)

        assertEquals(2, signals.ticksFor(LAGER))
    }

    @Test
    fun `a re-tap on one destination leaves its siblings alone`() {
        val signals = RootScrollSignals()

        signals.request(LAGER)

        assertEquals("Lager was asked", 1, signals.ticksFor(LAGER))
        assertEquals("Aufträge was not", 0, signals.ticksFor(ORDERS))
    }
}

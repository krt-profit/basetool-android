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
 * Tests the counter behind design chapter 03's „re-tap scrolls to top": counters are per route, so a re-tap never
 * scrolls another tab's list.
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

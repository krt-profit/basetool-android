/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the refusal holder guarantees.
 *
 * Both properties here are load-bearing for the design's rule that the lock toast is a singleton
 * whose clock restarts on every tap (design ch. 09, artboard 14). Neither is visible from a
 * screenshot, and the second one is the reason [Denial] carries a serial at all: keying the
 * dismissal effect on the text alone would let the first tap's timer expire under the second.
 */
class GatedActionTest {
    private val role =
        Gate(
            allowed = false,
            reason = "Dafür brauchst du die Rolle Logistiker.",
            detail = "Vergeben je Staffel durch die Administration.",
        )

    private val row =
        Gate(
            allowed = false,
            reason = "Nur deine eigene Zeile — fremde Einträge ändert nur ein Logistiker.",
            detail = "Regel: eigene Zeile oder Bearbeitungsrecht auf die Einheit des Eintrags.",
        )

    @Test
    fun `nothing is shown until something is refused`() {
        assertNull(DenialState().current)
    }

    @Test
    fun `a refusal carries the grant to ask for and who hands it out`() {
        val denials = DenialState()

        denials.raise(role)

        assertEquals("Dafür brauchst du die Rolle Logistiker.", denials.current?.title)
        assertEquals("Vergeben je Staffel durch die Administration.", denials.current?.detail)
    }

    @Test
    fun `tapping the same lock twice restarts the clock instead of stacking`() {
        val denials = DenialState()

        denials.raise(role)
        val first = denials.current
        denials.raise(role)
        val second = denials.current

        assertEquals(first?.title, second?.title)
        assertNotEquals("the serial must move so the dismissal timer restarts", first?.serial, second?.serial)
    }

    @Test
    fun `a second refusal replaces the first rather than queueing behind it`() {
        val denials = DenialState()

        denials.raise(role)
        denials.raise(row)

        assertEquals(row.reason, denials.current?.title)
    }

    @Test
    fun `clearing takes the refusal off screen`() {
        val denials = DenialState()
        denials.raise(role)

        denials.clear()

        assertNull(denials.current)
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import de.greluc.krt.profit.basetool.android.core.data.RefineryStoreLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The two rules of the Einlagern form that a wrong reading breaks silently.
 *
 * A run can yield the same material at two grades, and keying a line on the material alone made
 * them one — Compose rejected the duplicate list key outright, and before it did, editing one line
 * edited both. And `personal` excludes a job order on the server (400), so the pair must never be
 * assembled in the first place.
 */
class RefineryStoreTest {
    private fun line(
        material: String = "m1",
        quality: Int = 733,
    ) = RefineryStoreLine(
        materialId = material,
        materialName = "Agricium",
        computed = 1.8,
        amount = "1.8",
        quality = quality,
        locationId = "loc1",
    )

    @Test
    fun `the same material at two grades is two lines`() {
        // Agricium at 733 and Agricium at 874 come out of one run. Keyed on the material alone they
        // collide, which Compose treats as a fatal duplicate key.
        assertNotEquals(line(quality = 733).key, line(quality = 874).key)
    }

    @Test
    fun `a line's identity is stable across an edit`() {
        val edited = line().copy(amount = "1.9", note = "korrigiert")
        assertEquals(line().key, edited.key)
    }

    @Test
    fun `a personal line carries no job order`() {
        // The server answers 400 for the pair. Clearing the order when personal is ticked means the
        // combination cannot be sent, so the rule never arrives as an unexplained refusal.
        val personal = line().copy(jobOrderId = "job-1").copy(personal = true, jobOrderId = null)
        assertEquals(null, personal.jobOrderId)
    }
}

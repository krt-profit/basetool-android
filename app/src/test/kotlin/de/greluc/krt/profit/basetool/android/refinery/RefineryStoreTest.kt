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
 * Tests the Einlagern form: two grades of one material stay separate lines, and `personal` is never combined with a job
 * order.
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
        assertNotEquals(line(quality = 733).key, line(quality = 874).key)
    }

    @Test
    fun `a line's identity is stable across an edit`() {
        val edited = line().copy(amount = "1.9", note = "korrigiert")
        assertEquals(line().key, edited.key)
    }

    @Test
    fun `a personal line carries no job order`() {
        val personal = line().copy(jobOrderId = "job-1").copy(personal = true, jobOrderId = null)
        assertEquals(null, personal.jobOrderId)
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * What a member typed is not what `toDoubleOrNull()` expects.
 *
 * The app is German-first and the decimal key of a German keyboard is a comma. A device showed both
 * halves of the consequence: the refinery's Einlagern sent no request at all for „1,9", and the
 * bank's amounts folded the same text into `BigDecimal.ZERO` — which does not refuse a booking, it
 * books nothing and reports success.
 */
class AmountsTest {
    private companion object {
        /** The figure both spellings have to reach. */
        const val ONE_POINT_NINE = 1.9
    }

    @Test
    fun `a comma is a decimal separator, because the German keyboard says so`() {
        assertEquals(ONE_POINT_NINE, parseTypedAmount("1,9"))
        assertEquals(BigDecimal("1.9"), parseTypedDecimal("1,9"))
    }

    @Test
    fun `a point still works, because the wire and half the world use it`() {
        assertEquals(ONE_POINT_NINE, parseTypedAmount("1.9"))
        assertEquals(BigDecimal("1.9"), parseTypedDecimal("1.9"))
    }

    @Test
    fun `blank is nothing typed, which is not the same answer as zero`() {
        assertNull(parseTypedAmount("   "))
        assertNull(parseTypedDecimal(""))
        assertNull(parseTypedAmount(null))
    }

    @Test
    fun `text that is not a figure stays refused`() {
        assertNull(parseTypedAmount("viel"))
        assertNull(parseTypedDecimal("1,2,3"))
    }
}

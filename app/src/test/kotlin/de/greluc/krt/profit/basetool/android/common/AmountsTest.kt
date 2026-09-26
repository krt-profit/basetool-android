/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Tests how a `numeric(_,4)` wire amount such as `86400.0000` becomes a readable figure without ever passing through a
 * `Double`.
 */
class AmountsTest {
    private val german = Locale.GERMAN

    @Test
    fun `the zeros a numeric column pads with are dropped`() {
        assertEquals("86.400", formatAmount("86400.0000", german))
    }

    @Test
    fun `thousands are grouped for the locale`() {
        assertEquals("1.234.567", formatAmount("1234567", german))
        assertEquals("1,234,567", formatAmount("1234567", Locale.ENGLISH))
    }

    @Test
    fun `a real fraction survives, because it is information`() {
        assertEquals("1.234,5", formatAmount("1234.50", german))
    }

    @Test
    fun `nothing goes through a Double`() {
        assertEquals("12.345.678.901.234.567", formatAmount("12345678901234567", german))
    }

    @Test
    fun `a blank amount stays blank rather than becoming a zero`() {
        assertEquals("", formatAmount("", german))
        assertEquals("", formatSignedAmount("", income = true, locale = german))
    }

    @Test
    fun `something that is not a number is shown as it came`() {
        assertEquals("n/a", formatAmount("n/a", german))
    }

    @Test
    fun `the sign comes from the entry kind, never from the digits`() {
        assertEquals("+86.400", formatSignedAmount("86400.0000", income = true, locale = german))
        assertEquals("−11.700", formatSignedAmount("11700.0000", income = false, locale = german))
    }

    @Test
    fun `the minus is the typographic one, not a hyphen`() {
        assertEquals('−', formatSignedAmount("11700", income = false, locale = german).first())
    }

    @Test
    fun `a negative figure uses the same minus as a signed one`() {
        assertEquals("−2.500", formatAmount("-2500", Locale.GERMANY))
        assertEquals(
            formatSignedAmount("2500", income = false, locale = Locale.GERMANY),
            formatAmount("-2500", Locale.GERMANY),
        )
    }
}

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
 * How a server amount becomes a figure a member reads.
 *
 * The whole reason this exists: the wire carries `86400.0000` — the column is `numeric(_,4)` — and
 * the first version of the Finanzen tab displayed exactly that. Faithful, and unreadable. Found on
 * a device, because no test asserted what the *string* looked like.
 *
 * The fix must not undo the reason the raw string was kept in the first place: no `Double` ever
 * touches these values.
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
        // Stripping is for the padding a fixed-scale column adds, not for digits the member entered.
        assertEquals("1.234,5", formatAmount("1234.50", german))
    }

    @Test
    fun `nothing goes through a Double`() {
        // 17 significant digits: a Double would round this and the assertion would fail. That is
        // the point of the test -- it is here to break if someone "simplifies" the parse.
        assertEquals("12.345.678.901.234.567", formatAmount("12345678901234567", german))
    }

    @Test
    fun `a blank amount stays blank rather than becoming a zero`() {
        // The server omits a sum it has none of. Printing "0" would claim a booking of nothing.
        assertEquals("", formatAmount("", german))
        assertEquals("", formatSignedAmount("", income = true, locale = german))
    }

    @Test
    fun `something that is not a number is shown as it came`() {
        // A value this build cannot parse is a server change worth seeing, not one to hide behind
        // a placeholder.
        assertEquals("n/a", formatAmount("n/a", german))
    }

    @Test
    fun `the sign comes from the entry kind, never from the digits`() {
        // The server stores both incomes and expenses as positive magnitudes. Deriving the sign
        // from the value would show every expense as an income.
        assertEquals("+86.400", formatSignedAmount("86400.0000", income = true, locale = german))
        assertEquals("−11.700", formatSignedAmount("11700.0000", income = false, locale = german))
    }

    @Test
    fun `the minus is the typographic one, not a hyphen`() {
        // A hyphen in a column of grouped figures reads as a dash between two numbers; the true
        // minus is the same width as the plus above it.
        assertEquals('−', formatSignedAmount("11700", income = false, locale = german).first())
    }
}

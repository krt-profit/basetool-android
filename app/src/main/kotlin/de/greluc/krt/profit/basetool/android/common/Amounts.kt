/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * The minus sign the design uses: U+2212, not the hyphen-minus of a keyboard.
 *
 * A hyphen in a column of grouped figures reads as a dash between two numbers; the true minus is
 * the same width as the plus it sits under.
 */
private const val MINUS = "−"

/** Beyond this the value is not an aUEC amount, and formatting it would invent precision. */
private const val MAX_FRACTION_DIGITS = 2

/**
 * Renders a server amount grouped for the member's locale, exactly and without `Double`
 * (REQ-APP-MIS-011).
 *
 * The string is parsed as a `BigDecimal`, stripped of trailing zeros and grouped. Shared by every
 * area that shows money.
 *
 * @param raw the amount as the server rendered it; may be blank.
 * @param locale the member's locale, which decides the grouping separator.
 * @return the grouped amount, or the input unchanged when it is not a number.
 */
fun formatAmount(
    raw: String,
    locale: Locale = Locale.getDefault(),
): String {
    val value = raw.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
    return when (value) {
        null -> {
            raw.trim()
        }

        else -> {
            NumberFormat.getNumberInstance(locale)
                .apply {
                    isGroupingUsed = true
                    maximumFractionDigits = MAX_FRACTION_DIGITS
                }
                .format(value.stripTrailingZeros())
                .replace("-", MINUS)
        }
    }
}

/**
 * Renders an amount with the sign its bookkeeping gives it.
 *
 * The sign comes from [income], never from the digits, since the server stores positive magnitudes.
 *
 * @param raw the amount as the server rendered it.
 * @param income whether this is an income.
 * @param locale the member's locale.
 * @return e.g. `+86.400` or `−11.700`; an empty string stays empty rather than becoming a lone sign.
 */
fun formatSignedAmount(
    raw: String,
    income: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    val formatted = formatAmount(raw, locale)
    if (formatted.isEmpty()) {
        return ""
    }
    return (if (income) "+" else MINUS) + formatted
}

/**
 * Parses a decimal without throwing.
 *
 * @return the value, or `null` when the text is not a number.
 */
private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(trim()) }.getOrNull()

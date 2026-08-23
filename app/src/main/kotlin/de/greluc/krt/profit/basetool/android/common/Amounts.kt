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
 * Renders a server amount the way a member reads it.
 *
 * **Exact, and not via `Double`.** The wire carries `86400.0000` — the column is `numeric(_,4)` —
 * and the string is parsed as a `BigDecimal`, stripped of the zeros that carry no information, and
 * grouped for [locale]. No arithmetic happens: `stripTrailingZeros` and grouping are lossless, while
 * a `Double` round trip is exactly how a total gains a rounding error the server never had
 * (REQ-APP-MIS-011).
 *
 * Shared by every area that shows money — the Einsatz Finanzen tab, an Operation's roll-up and the
 * bank ledger — because a member reading the same figure on two screens must read the same figure.
 *
 * Displaying the raw string instead was the first attempt and is what a device run rejected:
 * `86400.0000` is faithful and unreadable, and the design's own figures are grouped (`86.400`).
 *
 * @param raw the amount as the server rendered it; may be blank.
 * @param locale the member's locale, which decides the grouping separator.
 * @return the grouped amount, or the input unchanged when it is not a number — a value this build
 *   cannot parse is shown as it came rather than replaced by a placeholder that hides it.
 */
fun formatAmount(
    raw: String,
    locale: Locale = Locale.getDefault(),
): String {
    val value = raw.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
    return when (value) {
        // Blank stays blank -- the server omits a sum it has none of, and "0" would claim a
        // booking of nothing. Unparseable is shown as it came: a server change worth seeing.
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
        }
    }
}

/**
 * Renders an amount with the sign its bookkeeping gives it.
 *
 * The sign comes from **what kind of entry this is**, never from the digits: the server stores both
 * incomes and expenses as positive magnitudes, so deriving it from the value would show every
 * expense as an income.
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

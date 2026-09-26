/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * Reads a figure a member typed, accepting a comma as well as a point as the decimal separator.
 *
 * A blank string is `null`, not zero.
 *
 * @param text what was typed.
 * @return the figure, or `null` when the text is not one.
 */
fun parseTypedAmount(text: String?): Double? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()

/**
 * Reads a money figure a member typed, with the same comma rule as [parseTypedAmount].
 *
 * @param text what was typed.
 * @return the figure, or `null` when the text is not one.
 */
fun parseTypedDecimal(text: String?): java.math.BigDecimal? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toBigDecimalOrNull()

/**
 * Writes a figure back into an editable field in the plain form [parseTypedAmount] reads: no grouping, no currency, and
 * no `.0` on a whole number.
 *
 * @param value the figure, or `null` for a field the server left unset.
 * @return the text, empty when there is no figure.
 */
fun formatTypedAmount(value: Double?): String =
    when {
        value == null -> ""
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> value.toString()
    }

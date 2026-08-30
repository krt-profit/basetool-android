/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * Reads a figure a member typed.
 *
 * **Accepts a comma as well as a point.** The app is German-first, and on a German locale the
 * decimal key of the keyboard is a comma — `toDoubleOrNull()` rejects what that key produces. A
 * device showed the consequence twice: the refinery's Einlagern silently sent nothing, and the
 * Lager's booking draft fell through to zero and booked it.
 *
 * A blank string is `null` rather than zero: "nothing typed" and "zero" are different answers, and
 * only the caller knows which of them is acceptable.
 *
 * @param text what was typed.
 * @return the figure, or `null` when the text is not one.
 */
fun parseTypedAmount(text: String?): Double? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()

/**
 * Reads a money figure a member typed.
 *
 * The [parseTypedAmount] rule applied to the bank's decimals, where the fallback is worse: every
 * one of those call sites folds an unparseable amount into `BigDecimal.ZERO`, so a comma did not
 * refuse a booking — it booked nothing and said it had worked.
 *
 * @param text what was typed.
 * @return the figure, or `null` when the text is not one.
 */
fun parseTypedDecimal(text: String?): java.math.BigDecimal? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toBigDecimalOrNull()

/**
 * Writes a figure back into a field a member will edit.
 *
 * The inverse of [parseTypedAmount], and deliberately plain: no grouping separators and no
 * currency, because whatever comes out here is read straight back by [parseTypedAmount] when the
 * form is sent. A whole number loses its `.0`, so a prefilled cost field reads `4200` rather than
 * `4200.0` — the second looks like a value the app invented.
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

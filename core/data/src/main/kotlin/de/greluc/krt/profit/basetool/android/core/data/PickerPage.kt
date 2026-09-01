/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * One page of picker candidates, and whether the catalogue holds more than fits on it.
 *
 * Every server-side picker search answers a **page**, never the catalogue. A client that renders
 * the page and says nothing is indistinguishable from one that has shown everything — which is how
 * 28 of 53 locations once went missing from the web's Lager booking form, with nothing on screen
 * indicating the list had been cut. ADR-0104 is the rule that came out of it: a bounded view says
 * it is bounded.
 *
 * @param T what the picker offers.
 * @property rows what may be offered.
 * @property more whether narrowing the search would reveal further candidates.
 */
data class PickerPage<T>(
    val rows: List<T> = emptyList(),
    val more: Boolean = false,
)

/**
 * Reads a page response into a [PickerPage].
 *
 * `totalElements`, **not** `rows.size == pageSize`: a page that happens to be exactly full is not
 * evidence of more, and a row dropped for having no id would make the size comparison lie in the
 * other direction.
 *
 * @param T what the picker offers.
 * @param rows the rows that survived mapping.
 * @param totalElements what the server said the whole result set holds.
 * @return the page, with the overflow flag resolved.
 */
fun <T> krtPickerPage(
    rows: List<T>,
    totalElements: Long?,
): PickerPage<T> = PickerPage(rows = rows, more = (totalElements ?: 0L) > rows.size.toLong())

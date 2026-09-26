/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * One page of picker candidates, and whether the catalogue holds more than fits on it (ADR-0104).
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
 * Reads a page response into a [PickerPage], deriving the overflow from `totalElements` rather
 * than from the row count.
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

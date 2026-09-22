/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * One page of a paginated list, as the backend's `PageResponse` delivers it, with the rows already
 * mapped to the domain model.
 *
 * Every paged list in the app — the ledger, the Einsätze, the Lager groups, the notifications, … —
 * has exactly this shape, and until 2026-09-22 each had its own data class with the rows under a
 * different name (`bookings`, `missions`, `groups`, …) and, in three of them, the two totals in the
 * other order. That made a positional constructor call a trap: `(rows, 0, 3, 1)` meant one thing
 * for most pages and the opposite for three, and the compiler accepted both. The per-area names
 * survive as typealiases (`MissionPage = Page<Mission>`), so a signature still says what it pages.
 *
 * Deliberately **not** the picker's [PickerPage]: a picker page carries only whether the catalogue
 * holds more than it shows (main repo ADR-0104), never page indices, and conflating the two would
 * invite a picker to page-walk.
 *
 * @param T the domain row.
 * @property rows the rows on this page, in the server's order; rows the mapper could not read are
 *   already dropped, so `rows.size` can be smaller than the page size even mid-list.
 * @property page the zero-based index of this page.
 * @property totalPages how many pages the whole list has.
 * @property totalElements how many rows the whole list has — the figure a "N gesamt" label reads,
 *   never `rows.size`.
 */
data class Page<T>(
    val rows: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /**
     * Whether another page follows this one — read off [totalPages], not off whether this page is
     * full, because a full last page is not evidence of more.
     */
    val hasMore: Boolean get() = page + 1 < totalPages
}

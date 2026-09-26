/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * One page of a paginated list, as the backend's `PageResponse` delivers it, with the rows mapped
 * to the domain model.
 *
 * Per-area names survive as typealiases, e.g. `MissionPage = Page<Mission>`.
 *
 * @param T the domain row.
 * @property rows the rows on this page, in the server's order; unreadable rows are already dropped,
 *   so `rows.size` can be smaller than the page size.
 * @property page the zero-based index of this page.
 * @property totalPages how many pages the whole list has.
 * @property totalElements how many rows the whole list has; the figure a "N gesamt" label reads.
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

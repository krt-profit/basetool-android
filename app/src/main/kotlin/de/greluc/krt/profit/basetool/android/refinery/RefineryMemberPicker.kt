/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The picker for whom a line's refined output is booked onto, when it is not the caller.
 *
 * Offered only to a Logistician; the server checks `canManageUserInventory(targetUserId)` per line
 * (REQ-SEC-039). A line left without a receiver falls back to the order's owner.
 *
 * @property open which line index the picker is open for, or `null` when it is closed.
 * @property query what the member typed.
 * @property results the current page of matches.
 * @property more whether the roster holds more than this page (ADR-0104).
 * @property loading whether a search is in flight.
 */
data class RefineryMemberPickerState(
    val open: Int? = null,
    val query: String = "",
    val results: List<MemberOption> = emptyList(),
    val more: Boolean = false,
    val loading: Boolean = false,
)

/**
 * Drives [RefineryMemberPickerState] against the `/users/search` roster search.
 *
 * @property roster the search, or `null` when the screen was built without one; the picker then never
 *   opens and the field only displays the current receiver.
 * @property scope the view model's scope.
 * @property read the current picker state.
 * @property write publishes a new picker state.
 */
class RefineryMemberPicker(
    private val roster: BookInOptions?,
    private val scope: CoroutineScope,
    private val read: () -> RefineryMemberPickerState,
    private val write: (RefineryMemberPickerState) -> Unit,
) {
    /**
     * Opens the picker for one store line and seeds it with an empty search.
     *
     * @param lineIndex which line's receiver is being chosen.
     */
    fun open(lineIndex: Int) {
        if (roster == null) return
        write(RefineryMemberPickerState(open = lineIndex))
        search("")
    }

    /** Closes the picker without changing the line. */
    fun dismiss() = write(RefineryMemberPickerState())

    /**
     * Runs a roster search.
     *
     * @param query what the member typed; shorter than [MIN_QUERY] clears the list **and** the
     *   overflow flag, so „there are more" never stands beside an empty list.
     */
    fun query(query: String) {
        write(read().copy(query = query))
        search(query)
    }

    /**
     * Performs the search itself.
     *
     * @param query the trimmed term.
     */
    private fun search(query: String) {
        val source = roster ?: return
        if (query.trim().length < MIN_QUERY) {
            write(read().copy(results = emptyList(), more = false, loading = false))
            return
        }
        write(read().copy(loading = true))
        scope.launch {
            when (val result = source.members(query)) {
                is ApiResult.Success -> {
                    write(
                        read().copy(
                            results = result.value.rows,
                            more = result.value.more,
                            loading = false,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "member search failed: ${result.error}" }
                    write(read().copy(results = emptyList(), more = false, loading = false))
                }
            }
        }
    }

    private companion object {
        /** Below this the search is not run — the same floor the Lager's pickers use. */
        const val MIN_QUERY = 2

        /** Log subsystem. A member's name is never logged, only that a search failed. */
        const val LOG_TAG = "refinery-roster"
    }
}

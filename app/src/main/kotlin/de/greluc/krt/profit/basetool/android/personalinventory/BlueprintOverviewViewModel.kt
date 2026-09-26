/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOverviewEntry
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOwner
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Log subsystem. Owner names are member data and never reach the log. */
private const val LOG_TAG = "blueprint-overview"

/** How long the search waits after the last keystroke. */
private const val SEARCH_DEBOUNCE_MS = 300L

/** How far the caller's own list of owners has got. */
sealed interface OwnersState {
    /** Nothing asked yet — the card is on screen but its owners have not been fetched. */
    data object Idle : OwnersState

    /** The read is in flight; the card says „Besitzer werden geladen …". */
    data object Loading : OwnersState

    /**
     * They arrived. An empty list is a result: „Keine Besitzer in deiner Orgeinheit."
     *
     * @property owners who holds it.
     */
    data class Ready(
        val owners: List<BlueprintOwner>,
    ) : OwnersState

    /**
     * The read failed — for this row only. „Besitzer konnten nicht geladen werden."
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : OwnersState
}

/** Which rows the chip row shows. */
enum class OverviewFilter {
    /** Every blueprint the search matches. */
    ALL,

    /** Only those nobody in scope holds — „Nicht erfasst". */
    UNRECORDED,
}

/** How far the list has got. */
sealed interface OverviewPhase {
    /** The first page is on its way. */
    data object Loading : OverviewPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : OverviewPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : OverviewPhase
}

/**
 * Everything „Blueprint-Verfügbarkeit" draws.
 *
 * @property entries the rows loaded so far.
 * @property owners each row's owner list, keyed by product key.
 * @property query the search term.
 * @property filter which chip is active.
 * @property phase how far the first page has got.
 * @property loadingMore whether the next page is in flight.
 * @property hasMore whether the server has another page.
 * @property total how many blueprints the search matches, as the server counts them.
 */
data class BlueprintOverviewState(
    val entries: List<BlueprintOverviewEntry> = emptyList(),
    val owners: Map<String, OwnersState> = emptyMap(),
    val query: String = "",
    val filter: OverviewFilter = OverviewFilter.ALL,
    val phase: OverviewPhase = OverviewPhase.Loading,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val total: Long = 0,
) {
    /**
     * The rows the list draws.
     *
     * The „Nicht erfasst" filter is applied client-side to the loaded rows, and the screen says so while
     * more pages exist (ADR-0104).
     */
    val visible: List<BlueprintOverviewEntry>
        get() =
            when (filter) {
                OverviewFilter.ALL -> entries
                OverviewFilter.UNRECORDED -> entries.filter { it.unrecorded }
            }

    /** Whether the filter is narrowing a list the server has more of. */
    val filterIsPartial: Boolean
        get() = filter == OverviewFilter.UNRECORDED && hasMore
}

/**
 * Drives „Blueprint-Verfügbarkeit" (REQ-APP-PI-014).
 *
 * Owners load per row, so one row's failure does not fail the list.
 *
 * @property source the two reads.
 */
class BlueprintOverviewViewModel(
    private val source: PersonalBlueprintSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BlueprintOverviewState())

    /** What the screen draws. */
    val state: StateFlow<BlueprintOverviewState> = mutableState.asStateFlow()

    private var searchJob: Job? = null
    private var loadedOnce = false

    /** Loads the first page, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload()
    }

    /**
     * The search term changed.
     *
     * @param query what was typed.
     */
    fun onQueryChanged(query: String) {
        mutableState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                reload()
            }
    }

    /**
     * A filter chip was tapped.
     *
     * @param filter which one.
     */
    fun onFilterChanged(filter: OverviewFilter) {
        mutableState.update { it.copy(filter = filter) }
    }

    /** Reads the first page again. */
    fun onRetry() {
        reload()
    }

    /** Appends the next page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is OverviewPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.overview(current.query, page = current.entries.size / PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            entries = latest.entries + result.value.rows,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the next overview page failed: ${result.error}" }
                    mutableState.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    /**
     * Reads one row's owners, once, when its card appears.
     *
     * @param entry the row.
     */
    fun onRowShown(entry: BlueprintOverviewEntry) {
        if (mutableState.value.owners[entry.productKey] != null) {
            return
        }
        mutableState.update { state ->
            state.copy(
                owners = state.owners + (entry.productKey to OwnersState.Loading),
            )
        }
        viewModelScope.launch {
            val next =
                when (val result = source.owners(entry.productKey)) {
                    is ApiResult.Success -> {
                        OwnersState.Ready(result.value)
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "owners could not be read: ${result.error}" }
                        OwnersState.Failed(result.error)
                    }
                }
            mutableState.update { state ->
                state.copy(
                    owners = state.owners + (entry.productKey to next),
                )
            }
        }
    }

    /** Reads the first page, dropping whatever was loaded. */
    private fun reload() {
        mutableState.update { it.copy(phase = OverviewPhase.Loading, entries = emptyList(), owners = emptyMap()) }
        viewModelScope.launch {
            when (val result = source.overview(mutableState.value.query, page = 0, pageSize = PAGE_SIZE)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            entries = result.value.rows,
                            phase = OverviewPhase.Ready,
                            hasMore = result.value.hasMore,
                            total = result.value.totalElements,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the overview could not be read: ${result.error}" }
                    mutableState.update { it.copy(phase = OverviewPhase.Failed(result.error)) }
                }
            }
        }
    }

    private companion object {
        /** Rows per page; the same figure the blueprints list uses. */
        const val PAGE_SIZE = 30
    }
}

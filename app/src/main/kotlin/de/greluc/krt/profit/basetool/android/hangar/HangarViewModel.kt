/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.HangarSource
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Which half of the Hangar screen is showing. */
enum class HangarSegment {
    /** The caller's own ships. */
    MINE,

    /** The active org unit's aggregate, one row per ship type. */
    ORG,
}

/** How far the current half has got. */
sealed interface HangarPhase {
    /** The first page is on its way. */
    data object Loading : HangarPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : HangarPhase

    /**
     * It did not arrive.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : HangarPhase
}

/**
 * Everything the Hangar screen draws.
 *
 * The two halves keep **separate** rows, totals and phases. Sharing them would make switching the
 * segment show the other half's content for a frame, and a failure on one half would present itself
 * as a failure of the other.
 *
 * @property segment which half is showing
 * @property searchText what is in the search field right now, ahead of the debounce
 * @property ships the caller's ships loaded so far
 * @property shipsTotal how many the server has for the current filter
 * @property types the aggregate rows loaded so far
 * @property typesTotal how many ship types the server has for the current filter
 * @property phase how far the showing half has got
 * @property page the last page index that arrived for the showing half
 * @property hasMore whether that half has another page
 * @property loadingMore whether it is in flight
 * @property refreshing whether a pull-to-refresh is running over rows already on screen
 */
data class HangarState(
    val segment: HangarSegment = HangarSegment.MINE,
    val searchText: String = "",
    val ships: List<Ship> = emptyList(),
    val shipsTotal: Long = 0,
    val types: List<ShipTypeSummary> = emptyList(),
    val typesTotal: Long = 0,
    val phase: HangarPhase = HangarPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
) {
    /** Whether the member has narrowed anything. */
    val isNarrowed: Boolean get() = searchText.isNotBlank()

    /** How many rows the showing half has in total on the server. */
    val total: Long get() = if (segment == HangarSegment.MINE) shipsTotal else typesTotal
}

/**
 * Drives the Hangar.
 *
 * Typing is debounced by 300 ms and the segment is not, for the reason the Einsatz list gives: a
 * search term arrives one keystroke at a time, a tapped segment is one deliberate act.
 *
 * **Switching the segment reloads that half from page 0.** The alternative — keeping whatever was
 * last loaded — shows a member the aggregate they saw ten minutes ago while the header says it is
 * current.
 *
 * @property source where the ships come from
 */
@OptIn(FlowPreview::class)
class HangarViewModel(
    private val source: HangarSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HangarState())

    /** What the screen draws. */
    val state: StateFlow<HangarState> = mutableState.asStateFlow()

    private val typedText = MutableStateFlow("")
    private var loadJob: Job? = null
    private var loadedOnce = false

    init {
        viewModelScope.launch {
            typedText
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { reload(keepRows = false) }
        }
    }

    /** Loads the showing half, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /**
     * Switches half and loads it.
     *
     * @param segment the half the member picked.
     */
    fun onSegmentSelected(segment: HangarSegment) {
        if (segment == mutableState.value.segment) {
            return
        }
        loadedOnce = true
        mutableState.value = mutableState.value.copy(segment = segment)
        reload(keepRows = false)
    }

    /**
     * Records a keystroke, updating the field synchronously and deferring the request.
     *
     * @param text what the member has typed so far.
     */
    fun onSearchChanged(text: String) {
        mutableState.value = mutableState.value.copy(searchText = text)
        typedText.value = text
    }

    /** Re-reads the showing half while keeping its rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepRows = true)
    }

    /** Appends the next page of the showing half. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is HangarPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val next = current.page + 1
            if (current.segment == HangarSegment.MINE) {
                appendShips(next, current.searchText)
            } else {
                appendTypes(next, current.searchText)
            }
        }
    }

    /**
     * Appends a page of ships.
     *
     * @param page the page index to fetch.
     * @param search the current filter.
     */
    private suspend fun appendShips(
        page: Int,
        search: String,
    ) {
        when (val result = source.myShips(search = search, page = page)) {
            is ApiResult.Success -> {
                // Read the state again: a refresh may have replaced the rows while this page was
                // in flight, and appending to the stale snapshot would resurrect them.
                val latest = mutableState.value
                mutableState.value =
                    latest.copy(
                        ships = latest.ships + result.value.ships,
                        shipsTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "next page of ships failed: ${result.error}" }
                mutableState.value = mutableState.value.copy(loadingMore = false)
            }
        }
    }

    /**
     * Appends a page of the aggregate.
     *
     * @param page the page index to fetch.
     * @param search the current filter.
     */
    private suspend fun appendTypes(
        page: Int,
        search: String,
    ) {
        when (val result = source.orgOverview(search = search, page = page)) {
            is ApiResult.Success -> {
                val latest = mutableState.value
                mutableState.value =
                    latest.copy(
                        types = latest.types + result.value.types,
                        typesTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "next page of the hangar aggregate failed: ${result.error}" }
                mutableState.value = mutableState.value.copy(loadingMore = false)
            }
        }
    }

    /**
     * Loads page 0 of the showing half.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        loadJob?.cancel()
        val current = mutableState.value
        if (!keepRows) {
            mutableState.value = current.copy(phase = HangarPhase.Loading)
        }
        loadJob =
            viewModelScope.launch {
                if (current.segment == HangarSegment.MINE) {
                    loadShips(current.searchText)
                } else {
                    loadTypes(current.searchText)
                }
            }
    }

    /**
     * Loads page 0 of the caller's ships.
     *
     * @param search the current filter.
     */
    private suspend fun loadShips(search: String) {
        when (val result = source.myShips(search = search, page = 0)) {
            is ApiResult.Success -> {
                mutableState.value =
                    mutableState.value.copy(
                        ships = result.value.ships,
                        shipsTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        phase = HangarPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "ships could not be read: ${result.error}" }
                mutableState.value =
                    mutableState.value.copy(
                        phase = HangarPhase.Failed(result.error),
                        loadingMore = false,
                        refreshing = false,
                    )
            }
        }
    }

    /**
     * Loads page 0 of the aggregate.
     *
     * @param search the current filter.
     */
    private suspend fun loadTypes(search: String) {
        when (val result = source.orgOverview(search = search, page = 0)) {
            is ApiResult.Success -> {
                mutableState.value =
                    mutableState.value.copy(
                        types = result.value.types,
                        typesTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        phase = HangarPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the hangar aggregate could not be read: ${result.error}" }
                mutableState.value =
                    mutableState.value.copy(
                        phase = HangarPhase.Failed(result.error),
                        loadingMore = false,
                        refreshing = false,
                    )
            }
        }
    }

    private companion object {
        /** The design spec's 300 ms, the same figure every other search field uses. */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** Log subsystem. A ship's name is member input and never reaches the log. */
        const val LOG_TAG = "hangar"
    }
}

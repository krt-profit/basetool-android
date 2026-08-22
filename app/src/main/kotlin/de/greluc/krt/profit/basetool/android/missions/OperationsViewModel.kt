/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.Operation
import de.greluc.krt.profit.basetool.android.core.data.OperationQuery
import de.greluc.krt.profit.basetool.android.core.data.OperationSource
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
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

/** How far the Operationen list has got. */
sealed interface OperationsPhase {
    /** The first page is on its way. */
    data object Loading : OperationsPhase

    /** A page arrived. It may be empty, which is a result and not a failure. */
    data object Ready : OperationsPhase

    /**
     * The list could not be loaded at all.
     *
     * @property error what went wrong, so a rate limit can be told apart from an outage.
     */
    data class Failed(
        val error: ApiError,
    ) : OperationsPhase
}

/**
 * Everything the Operationen list draws.
 *
 * @property query what the member has narrowed to; its `text` is the **debounced** term
 * @property searchText what is in the search field right now, updated on every keystroke ahead of
 *   the debounce — a controlled field bound to the debounced value discards every character, which
 *   is a defect this project has already shipped once and now guards against
 * @property operations every row loaded so far, across pages, in server order
 * @property total how many Operationen the filter matches on the server
 * @property phase how far the first page has got
 * @property page the zero-based index of the last page that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that next page is in flight
 * @property refreshing whether a pull-to-refresh is running over an already-populated list
 */
data class OperationsState(
    val query: OperationQuery = OperationQuery.NONE,
    val searchText: String = "",
    val operations: List<Operation> = emptyList(),
    val total: Long = 0,
    val phase: OperationsPhase = OperationsPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
) {
    /** Whether the member has narrowed anything, read from the typed term so reset appears at once. */
    val isNarrowed: Boolean get() = query.isNarrowed || searchText.isNotBlank()

    /** Whether the filter matched nothing. */
    val isEmpty: Boolean get() = phase is OperationsPhase.Ready && operations.isEmpty()
}

/**
 * Drives the Operationen list.
 *
 * The Einsatz list's rules apply unchanged — typing debounced, everything else immediate, every
 * reload from page 0 — and are not re-argued here. What differs is what the list can be narrowed
 * by: an Operation has no start time of its own, so there is no "Vergangene aus"; the finished ones
 * are a group in the list rather than something to switch off.
 *
 * **Loaded lazily.** The list is behind a segment, and a member who never taps "Operationen" should
 * not pay for it. [load] is therefore called by the screen when the segment is first shown, not on
 * construction.
 *
 * @property source where the Operationen come from
 */
@OptIn(FlowPreview::class)
class OperationsViewModel(
    private val source: OperationSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OperationsState())

    /** What the screen draws. */
    val state: StateFlow<OperationsState> = mutableState.asStateFlow()

    private val typedText = MutableStateFlow("")

    private var loadJob: Job? = null

    /** Whether [load] has already run, so showing the segment twice does not reload it. */
    private var loadedOnce = false

    init {
        viewModelScope.launch {
            typedText
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { text ->
                    mutableState.value = mutableState.value.copy(query = mutableState.value.query.copy(text = text))
                    reload()
                }
        }
    }

    /**
     * Loads the first page, the first time the segment is shown.
     *
     * Subsequent calls are ignored: switching back to a list that is already there should show it,
     * not reload it. Pull-to-refresh is how a member asks for fresh rows.
     */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload()
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

    /**
     * Narrows to a set of statuses, or widens to all of them when [statuses] is empty.
     *
     * @param statuses the statuses to show.
     */
    fun onStatusesChanged(statuses: Set<OperationStatus>) {
        val updated = mutableState.value.query.copy(statuses = statuses)
        if (updated == mutableState.value.query) {
            return
        }
        mutableState.value = mutableState.value.copy(query = updated)
        reload()
    }

    /** Clears every filter, the search field included. */
    fun onResetFilters() {
        typedText.value = ""
        mutableState.value = mutableState.value.copy(query = OperationQuery.NONE, searchText = "")
        reload()
    }

    /** Re-reads the first page while keeping the rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepRows = true)
    }

    /**
     * Appends the next page.
     *
     * Ignored when one is already in flight or the server has no more.
     */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is OperationsPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.search(current.query, page = current.page + 1)) {
                is ApiResult.Success -> {
                    val loaded = result.value
                    // Read the state again: a refresh may have replaced the rows while this page
                    // was in flight, and appending to the stale snapshot would resurrect them.
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            operations = latest.operations + loaded.operations,
                            total = loaded.totalElements,
                            page = loaded.page,
                            hasMore = loaded.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    // The rows on screen stay: a failed next page is not a reason to replace a
                    // working list with an error.
                    KrtLog.w(LOG_TAG) { "next page of Operationen failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Loads page 0 for the current filter.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean = false) {
        loadJob?.cancel()
        val query = mutableState.value.query
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = OperationsPhase.Loading)
        }
        loadJob =
            viewModelScope.launch {
                when (val result = source.search(query, page = 0)) {
                    is ApiResult.Success -> {
                        val loaded = result.value
                        mutableState.value =
                            mutableState.value.copy(
                                operations = loaded.operations,
                                total = loaded.totalElements,
                                page = loaded.page,
                                hasMore = loaded.hasMore,
                                phase = OperationsPhase.Ready,
                                loadingMore = false,
                                refreshing = false,
                            )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "Operationen could not be read: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = OperationsPhase.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                    }
                }
            }
    }

    private companion object {
        /** The design spec's 300 ms, the same figure the Einsatz search uses. */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** Log subsystem. Search terms are member input and never reach the log. */
        const val LOG_TAG = "operations"
    }
}

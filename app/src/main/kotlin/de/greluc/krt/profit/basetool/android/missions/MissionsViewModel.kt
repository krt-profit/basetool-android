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
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * How far the list has got.
 *
 * A sealed type rather than a pair of booleans so "loading" and "failed" cannot both be true, and
 * so the screen's `when` is exhaustive — a new phase then breaks compilation instead of silently
 * rendering nothing.
 */
sealed interface MissionsPhase {
    /** The first page is on its way; the screen shows the loading indicator. */
    data object Loading : MissionsPhase

    /** A page arrived. It may still be empty, which is a result and not a failure. */
    data object Ready : MissionsPhase

    /**
     * The list could not be loaded at all.
     *
     * @property error what went wrong, so the screen can tell a rate limit apart from an outage
     *   instead of showing one message for both.
     */
    data class Failed(
        val error: ApiError,
    ) : MissionsPhase
}

/**
 * Everything the Einsatz list draws.
 *
 * @property query what the member has narrowed to; its `text` is the debounced term actually sent
 *   to the server
 * @property searchText what is in the search field right now, updated on every keystroke ahead of
 *   the debounce
 * @property missions every row loaded so far, across pages, in server order
 * @property total how many Einsätze the filter matches on the server, even when fewer are loaded
 * @property phase how far the first page has got
 * @property page the zero-based index of the last page that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that next page is in flight
 * @property refreshing whether a pull-to-refresh is running over an already-populated list
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 */
data class MissionsState(
    val query: MissionQuery = MissionQuery.NONE,
    val searchText: String = "",
    val missions: List<Mission> = emptyList(),
    val total: Long = 0,
    val phase: MissionsPhase = MissionsPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
) {
    /**
     * Whether the member has narrowed anything, which is what decides if "zurücksetzen" is offered.
     *
     * Reads the *typed* term rather than the debounced one, so the reset appears on the first
     * keystroke instead of 300 ms later.
     */
    val isNarrowed: Boolean get() = query.isNarrowed || searchText.isNotBlank()

    /** Whether the filter matched nothing — an ordinary result the screen states in its own words. */
    val isEmpty: Boolean get() = phase is MissionsPhase.Ready && missions.isEmpty()
}

/**
 * Drives the Einsatz list.
 *
 * Search input is debounced; every other filter change reloads immediately. Every reload starts at
 * page 0 and replaces the rows.
 *
 * @property source where the Einsätze come from
 */
@OptIn(FlowPreview::class)
class MissionsViewModel(
    private val source: MissionSource,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MissionsState())

    /** What the screen draws. */
    val state: StateFlow<MissionsState> = mutableState.asStateFlow()

    /**
     * The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003).
     *
     * Shared rather than re-derived: the conditions under which a countdown is right are the same
     * on every screen.
     */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.update { it.copy(retryIn = left) } },
            onRetry = { reload(keepRows = false) },
        )

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    /**
     * The raw search field content, before debouncing.
     *
     * Held separately from [state] so the field stays responsive: the member sees each keystroke
     * immediately while the request waits for them to stop typing.
     */
    private val typedText = MutableStateFlow("")

    /**
     * The in-flight first-page load.
     *
     * Cancelled before a new one starts, so a slow response for an abandoned filter cannot land
     * after a fast response for the current one and overwrite it.
     */
    private var loadJob: Job? = null

    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.MISSIONS)) { sections ->
            if (LiveSyncSections.MISSIONS_LIST in sections) {
                reload(keepRows = true)
            }
        }
        viewModelScope.launch {
            typedText
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { text ->
                    mutableState.update { state -> state.copy(query = state.query.copy(text = text)) }
                    reload()
                }
        }
    }

    /**
     * Loads the first page for the current filter.
     *
     * Safe to call more than once; a second call replaces the first rather than racing it.
     */
    fun load() {
        reload()
    }

    /**
     * Records a keystroke: updates the field state synchronously and defers the request by
     * [SEARCH_DEBOUNCE_MS].
     *
     * @param text what the member has typed so far.
     */
    fun onSearchChanged(text: String) {
        mutableState.update { it.copy(searchText = text) }
        typedText.value = text
    }

    /**
     * Narrows to a set of statuses, or widens to all of them when [statuses] is empty.
     *
     * @param statuses the statuses to show.
     */
    fun onStatusesChanged(statuses: Set<MissionStatus>) {
        updateQuery { it.copy(statuses = statuses) }
    }

    /**
     * Shows or hides Einsätze that already started.
     *
     * @param include whether past Einsätze belong in the list.
     */
    fun onIncludePastChanged(include: Boolean) {
        updateQuery { it.copy(includePast = include) }
    }

    /**
     * Narrows to a date range.
     *
     * @param from lower bound, or `null` for none.
     * @param until upper bound, or `null` for none.
     */
    fun onRangeChanged(
        from: Instant?,
        until: Instant?,
    ) {
        updateQuery { it.copy(from = from, until = until) }
    }

    /**
     * Clears every filter, the search field included.
     *
     * The field is cleared through [typedText] as well, or the next keystroke would restore the old
     * term from a value the member can no longer see.
     */
    fun onResetFilters() {
        typedText.value = ""
        mutableState.update { it.copy(query = MissionQuery.NONE, searchText = "") }
        reload()
    }

    /**
     * Re-reads the first page while keeping the rows on screen.
     *
     * What pull-to-refresh calls. The distinction from [load] is only visual: the list does not
     * flash back to a spinner, because the member is looking at content they expect to stay.
     */
    fun onRefresh() {
        mutableState.update { it.copy(refreshing = true) }
        reload(keepRows = true)
    }

    /**
     * Appends the next page.
     *
     * Ignored when one is already in flight or the server has no more — so a fast scroll cannot
     * queue several requests for the same page.
     */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is MissionsPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val next = current.page + 1
            when (val result = source.search(current.query, page = next)) {
                is ApiResult.Success -> {
                    val loaded = result.value
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            missions = latest.missions + loaded.rows,
                            total = loaded.totalElements,
                            page = loaded.page,
                            hasMore = loaded.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of Einsätze failed: ${result.error}" }
                    mutableState.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    /**
     * Applies a change to the filter and reloads immediately.
     *
     * @param change produces the new filter from the current one.
     */
    private fun updateQuery(change: (MissionQuery) -> MissionQuery) {
        val updated = change(mutableState.value.query)
        if (updated == mutableState.value.query) {
            return
        }
        mutableState.update { it.copy(query = updated) }
        reload()
    }

    /**
     * Loads page 0 for the current filter.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives; `false` shows
     *   the loading indicator instead.
     */
    private fun reload(keepRows: Boolean = false) {
        loadJob?.cancel()
        val query = mutableState.value.query
        if (!keepRows) {
            mutableState.update { it.copy(phase = MissionsPhase.Loading) }
        }
        loadJob =
            viewModelScope.launch {
                when (val result = source.search(query, page = 0)) {
                    is ApiResult.Success -> {
                        val loaded = result.value
                        mutableState.update {
                            it.copy(
                                missions = loaded.rows,
                                total = loaded.totalElements,
                                page = loaded.page,
                                hasMore = loaded.hasMore,
                                phase = MissionsPhase.Ready,
                                loadingMore = false,
                                refreshing = false,
                            )
                        }
                        retry.onSuccess()
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "Einsätze could not be read: ${result.error}" }
                        mutableState.update {
                            it.copy(
                                phase = MissionsPhase.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                        }
                        retry.onFailure(result.error, hasContent = keepRows)
                    }
                }
            }
    }

    private companion object {
        /**
         * How long typing pauses before the term is sent.
         *
         * The design spec fixes 300 ms for this field; it is long enough that an ordinary word is
         * one request rather than six, and short enough not to feel like a delay.
         */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** Log subsystem. Search terms are member input and never reach the log. */
        const val LOG_TAG = "missions"
    }
}

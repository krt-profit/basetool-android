/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BoardEntry
import de.greluc.krt.profit.basetool.android.core.data.BoardSide
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.MaterialBoardSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialLookup
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.ReleasableStock
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the board has got. */
sealed interface BoardPhase {
    /** The first page is on its way. */
    data object Loading : BoardPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : BoardPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : BoardPhase
}

/** Which sheet is open, if any. */
sealed interface BoardSheet {
    /** None. */
    data object None : BoardSheet

    /**
     * „Gesuch erstellen".
     *
     * @property materialId the picked material, or `null` while nothing is picked.
     * @property materialName what it is called, for the field.
     * @property amount the wanted amount, as typed.
     * @property minQuality the minimum quality, as typed.
     * @property remark the note, as typed.
     */
    data class NewRequest(
        val materialId: String? = null,
        val materialName: String = "",
        val matches: List<MaterialOption> = emptyList(),
        val searching: Boolean = false,
        val amount: String = "",
        val minQuality: String = "",
        val remark: String = "",
    ) : BoardSheet {
        /**
         * Whether „Gesuch veröffentlichen" may be pressed.
         *
         * The material has to be **picked**, not typed: the request addresses it by id, and a name
         * the member typed and never selected has none. That is the failure mode the web app's
         * comboboxes have hit before — a field that looks filled and submits nothing.
         */
        val submittable: Boolean
            get() = materialId != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0
    }

    /**
     * „Angebot erstellen", which picks from the caller's own stock rather than the catalogue.
     *
     * @property stock the caller's releasable entries, once read.
     * @property loadingStock whether that read is in flight.
     * @property picked the chosen entry, or `null`.
     * @property amount how much of it, as typed.
     * @property remark the note, as typed.
     */
    data class NewOffer(
        val stock: List<ReleasableStock> = emptyList(),
        val loadingStock: Boolean = true,
        val picked: ReleasableStock? = null,
        val amount: String = "",
        val remark: String = "",
    ) : BoardSheet {
        /** Whether „Angebot veröffentlichen" may be pressed. */
        val submittable: Boolean
            get() = picked != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0
    }
}

/**
 * Everything the board draws.
 *
 * @property side which segment is selected
 * @property entries the rows of that segment
 * @property phase how far the first page has got
 * @property page the last page index that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property busyEntryId the row whose write is in flight, or `null`
 * @property sheet which sheet is open
 * @property saving whether a create is in flight
 * @property online whether a write can be sent at all
 * @property error what the last write returned, or `null`
 */
data class MaterialBoardState(
    val side: BoardSide = BoardSide.OFFERS,
    val entries: List<BoardEntry> = emptyList(),
    val phase: BoardPhase = BoardPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val busyEntryId: String? = null,
    val sheet: BoardSheet = BoardSheet.None,
    val saving: Boolean = false,
    val online: Boolean = true,
    val error: ApiError? = null,
) {
    /** Whether a write may be offered at all. */
    val writable: Boolean get() = online && !saving
}

/**
 * Drives the Materialbörse (REQ-APP-MARKET-001…008).
 *
 * **A write updates the row it was made on, never the whole page.** Every board write answers with
 * the updated row, so the toggle replaces one entry in place. Re-reading the page instead would
 * scroll the member back to the top on every tap — on a board whose entire interaction is tapping
 * rows.
 *
 * @property source where the board comes from
 * @property materials the catalogue behind „Gesuch erstellen"
 * @property liveSync the shared change stream, or `null` when it is not wired
 */
class MaterialBoardViewModel(
    private val source: MaterialBoardSource,
    private val materials: MaterialLookup,
    connectivity: Connectivity?,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MaterialBoardState())

    /** What the screen draws. */
    val state: StateFlow<MaterialBoardState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var loadedOnce = false

    /** The chapter-14 retry ladder for this screen's first load. */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepRows = false) },
        )

    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.MATERIALBOARD)) { sections ->
            // Both halves ride one room. A change to the half the member is not looking at is
            // ignored on purpose: reloading it would cost a request for a list nobody can see.
            val mine =
                when (mutableState.value.side) {
                    BoardSide.OFFERS -> LiveSyncSections.BOARD_OFFERS
                    BoardSide.REQUESTS -> LiveSyncSections.BOARD_REQUESTS
                }
            if (mine in sections) {
                reload(keepRows = true)
            }
        }
        connectivity?.let { link ->
            viewModelScope.launch {
                link.online.collect { online ->
                    mutableState.value = mutableState.value.copy(online = online)
                }
            }
        }
    }

    /** Loads the first page, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /**
     * Switches the segment.
     *
     * @param side which half.
     */
    fun onSideChanged(side: BoardSide) {
        if (side == mutableState.value.side) {
            return
        }
        loadedOnce = true
        mutableState.value = mutableState.value.copy(side = side, entries = emptyList())
        reload(keepRows = false)
    }

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        loadedOnce = true
        retry.onManualRetry()
    }

    /** Re-reads the first page while keeping the rows on screen. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepRows = true)
    }

    /** Appends the next page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is BoardPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.board(current.side, page = current.page + 1)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            entries = latest.entries + result.value.entries,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of the board failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Toggles „Ich kann liefern" on one row.
     *
     * @param entry the row.
     */
    fun onSignalToggled(entry: BoardEntry) {
        if (!entry.canSignal || !mutableState.value.writable) {
            return
        }
        mutableState.value = mutableState.value.copy(busyEntryId = entry.id, error = null)
        viewModelScope.launch {
            when (val result = source.setInterest(entry, !entry.viewerInterested)) {
                is ApiResult.Success -> {
                    replace(result.value)
                    announce()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the interest toggle failed: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(busyEntryId = null, error = result.error)
                }
            }
        }
    }

    /**
     * Withdraws one of the caller's own rows.
     *
     * @param entry the row.
     */
    fun onWithdraw(entry: BoardEntry) {
        if (!entry.mine || !mutableState.value.writable) {
            return
        }
        mutableState.value = mutableState.value.copy(busyEntryId = entry.id, error = null)
        viewModelScope.launch {
            when (val result = source.withdraw(entry)) {
                is ApiResult.Success -> {
                    // Dropped from the list rather than replaced: a withdrawn row is no longer on
                    // the board, and leaving it there with a changed status would invite the
                    // member to withdraw it again.
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            entries = latest.entries.filterNot { it.id == entry.id },
                            busyEntryId = null,
                        )
                    announce()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "withdrawing the entry failed: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(busyEntryId = null, error = result.error)
                }
            }
        }
    }

    /** Opens „Gesuch erstellen". */
    fun onNewRequest() {
        mutableState.value = mutableState.value.copy(sheet = BoardSheet.NewRequest(), error = null)
    }

    /** Opens „Angebot erstellen" and reads the caller's own stock behind it. */
    fun onNewOffer() {
        mutableState.value = mutableState.value.copy(sheet = BoardSheet.NewOffer(), error = null)
        viewModelScope.launch {
            when (val result = source.releasableStock()) {
                is ApiResult.Success -> {
                    updateOfferSheet { it.copy(stock = result.value, loadingStock = false) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the releasable stock could not be read: ${result.error}" }
                    // The sheet stays open with an empty list and its own message: closing it
                    // under the member would lose whatever they had already typed.
                    updateOfferSheet { it.copy(loadingStock = false) }
                    mutableState.value = mutableState.value.copy(error = result.error)
                }
            }
        }
    }

    /** Closes whichever sheet is open. */
    fun onSheetDismissed() {
        mutableState.value = mutableState.value.copy(sheet = BoardSheet.None, error = null)
    }

    /**
     * Edits the open request sheet.
     *
     * @param edit what to change.
     */
    fun onRequestEdited(edit: (BoardSheet.NewRequest) -> BoardSheet.NewRequest) {
        val sheet = mutableState.value.sheet as? BoardSheet.NewRequest ?: return
        mutableState.value = mutableState.value.copy(sheet = edit(sheet))
    }

    /**
     * Searches the catalogue for the request sheet.
     *
     * **The picked id is cleared as soon as the text changes.** A member who picks „Quantainium"
     * and then edits the field is no longer describing the material they picked, and submitting
     * the stale id would post a request for something they did not choose.
     *
     * @param query what the member typed.
     */
    fun onMaterialQueryChanged(query: String) {
        val sheet = mutableState.value.sheet as? BoardSheet.NewRequest ?: return
        mutableState.value =
            mutableState.value.copy(
                sheet =
                    sheet.copy(
                        materialName = query,
                        materialId = null,
                        searching = query.isNotBlank(),
                        matches = if (query.isBlank()) emptyList() else sheet.matches,
                    ),
            )
        searchJob?.cancel()
        if (query.isBlank()) {
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                when (val result = materials.materials(query)) {
                    is ApiResult.Success -> {
                        updateRequestSheet { it.copy(matches = result.value, searching = false) }
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the material search failed: ${result.error}" }
                        updateRequestSheet { it.copy(matches = emptyList(), searching = false) }
                    }
                }
            }
    }

    /**
     * Takes one of the search results.
     *
     * @param option the picked material.
     */
    fun onMaterialPicked(option: MaterialOption) {
        searchJob?.cancel()
        updateRequestSheet {
            it.copy(
                materialId = option.id,
                materialName = option.name,
                matches = emptyList(),
                searching = false,
            )
        }
    }

    /**
     * Edits the open offer sheet.
     *
     * @param edit what to change.
     */
    fun onOfferEdited(edit: (BoardSheet.NewOffer) -> BoardSheet.NewOffer) {
        updateOfferSheet(edit)
    }

    /** Publishes the request the sheet describes. */
    fun onRequestSubmitted() {
        val sheet = mutableState.value.sheet as? BoardSheet.NewRequest
        val materialId = sheet?.materialId
        val amount = sheet?.amount?.toDoubleOrNull()
        // Guarded rather than trusted: `submittable` gates the button, and this repeats the check
        // because a screen is not the only thing that can call a public method on a ViewModel.
        if (sheet == null || materialId == null || amount == null) {
            return
        }
        submit {
            source.createRequest(
                materialId = materialId,
                amount = amount,
                minQuality = sheet.minQuality.toIntOrNull(),
                remark = sheet.remark,
            )
        }
    }

    /** Publishes the offer the sheet describes. */
    fun onOfferSubmitted() {
        val sheet = mutableState.value.sheet as? BoardSheet.NewOffer
        val picked = sheet?.picked
        val amount = sheet?.amount?.toDoubleOrNull()
        if (sheet == null || picked == null || amount == null) {
            return
        }
        submit {
            source.createOffer(
                inventoryItemId = picked.inventoryItemId,
                amount = amount,
                remark = sheet.remark,
            )
        }
    }

    /** Clears the last write error. */
    fun onErrorDismissed() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    /**
     * Runs a create, then closes the sheet and re-reads the board.
     *
     * A re-read rather than an in-place insert: the create endpoints answer `202` with no body, so
     * the app does not have the row it just made and inventing one locally would show a member an
     * entry the server might have shaped differently.
     *
     * @param write the create to run.
     */
    private fun submit(write: suspend () -> ApiResult<Unit>) {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = write()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, sheet = BoardSheet.None)
                    announce()
                    reload(keepRows = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the board entry could not be created: ${result.error}" }
                    // The sheet stays open, holding what the member typed.
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Replaces one row in place.
     *
     * @param entry the row as it now stands.
     */
    private fun replace(entry: BoardEntry) {
        val latest = mutableState.value
        mutableState.value =
            latest.copy(
                entries = latest.entries.map { if (it.id == entry.id) entry else it },
                busyEntryId = null,
            )
    }

    /**
     * Edits the open offer sheet, if that is the one open.
     *
     * @param edit what to change.
     */
    private fun updateOfferSheet(edit: (BoardSheet.NewOffer) -> BoardSheet.NewOffer) {
        val sheet = mutableState.value.sheet as? BoardSheet.NewOffer ?: return
        mutableState.value = mutableState.value.copy(sheet = edit(sheet))
    }

    /**
     * Edits the open request sheet, if that is the one open.
     *
     * @param edit what to change.
     */
    private fun updateRequestSheet(edit: (BoardSheet.NewRequest) -> BoardSheet.NewRequest) {
        val sheet = mutableState.value.sheet as? BoardSheet.NewRequest ?: return
        mutableState.value = mutableState.value.copy(sheet = edit(sheet))
    }

    /**
     * Tells the room the board changed.
     *
     * Both sections, always. A member switching segments has to see a change made on the other
     * half, and the frame carries no data — announcing one section would be cheaper by nothing and
     * wrong half the time.
     */
    private fun announce() {
        publishLiveSync(
            liveSync,
            LiveSyncTopic.MATERIALBOARD,
            LiveSyncSections.BOARD_OFFERS,
            LiveSyncSections.BOARD_REQUESTS,
        )
    }

    /**
     * Loads page 0 of the current segment.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        loadJob?.cancel()
        val side = mutableState.value.side
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = BoardPhase.Loading)
        }
        loadJob =
            viewModelScope.launch {
                when (val result = source.board(side, page = 0)) {
                    is ApiResult.Success -> {
                        retry.onSuccess()
                        mutableState.value =
                            mutableState.value.copy(
                                entries = result.value.entries,
                                page = result.value.page,
                                hasMore = result.value.hasMore,
                                phase = BoardPhase.Ready,
                                loadingMore = false,
                                refreshing = false,
                            )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the board could not be read: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = BoardPhase.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                        retry.onFailure(result.error, hasContent = keepRows)
                    }
                }
            }
    }

    private companion object {
        /** Log subsystem. A remark is member input and never reaches the log. */
        const val LOG_TAG = "materialboard"

        /** Same debounce the Lager's pickers use, so one typing rhythm fits the whole app. */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

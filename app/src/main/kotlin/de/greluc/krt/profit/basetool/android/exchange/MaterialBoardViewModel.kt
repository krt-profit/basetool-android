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
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
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

/**
 * Which half of a create sheet is showing.
 *
 * **One form with a switch, not two entries in the menu** — design ch. 17's first decision, and the
 * precedent is the Auftrag form, which has split „Material / Items" the same way since round 5. A
 * second menu entry would be a fourth place where the same distinction has to be explained.
 */
enum class BoardKind {
    /** A material: SCU, from stock for an offer, with a minimum quality for a request. */
    MATERIAL,

    /** A craftable item: pieces, addressed by product key and bound to no stock row. */
    ITEM,
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
     * @property matches what the picker's last search answered with.
     * @property moreMatches whether the catalogue holds materials this page does not carry.
     * @property amount the wanted amount, as typed.
     * @property minQuality the minimum quality, as typed.
     * @property remark the note, as typed.
     */
    data class NewRequest(
        val materialId: String? = null,
        val materialName: String = "",
        val matches: List<MaterialOption> = emptyList(),
        val moreMatches: Boolean = false,
        val searching: Boolean = false,
        val amount: String = "",
        val minQuality: String = "",
        val remark: String = "",
        val kind: BoardKind = BoardKind.MATERIAL,
        val productKey: String? = null,
        val productName: String = "",
        val products: List<BlueprintProduct> = emptyList(),
    ) : BoardSheet {
        /**
         * Whether „Gesuch veröffentlichen" may be pressed.
         *
         * The material — or the product — has to be **picked**, not typed: the request addresses
         * it by an id the wire needs, and a name the member typed and never selected has none. That
         * is the failure mode the web app's comboboxes have hit before: a field that looks filled
         * and submits nothing.
         */
        val submittable: Boolean
            get() =
                when (kind) {
                    BoardKind.MATERIAL -> materialId != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0
                    BoardKind.ITEM -> productKey != null && (amount.trim().toIntOrNull() ?: 0) > 0
                }
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
        val kind: BoardKind = BoardKind.MATERIAL,
        val productKey: String? = null,
        val productName: String = "",
        val products: List<BlueprintProduct> = emptyList(),
    ) : BoardSheet {
        /** Whether „Angebot veröffentlichen" may be pressed. */
        val submittable: Boolean
            get() =
                when (kind) {
                    BoardKind.MATERIAL -> picked != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0
                    BoardKind.ITEM -> productKey != null && (amount.trim().toIntOrNull() ?: 0) > 0
                }
    }

    /**
     * „Eigenen Eintrag bearbeiten" (design ch. 17 artboard 3).
     *
     * One sheet for both halves, because the two writes differ in one field. What may be changed
     * is **not** what the artboard says: it claims only the remark is editable on an offer and
     * calls that the web rule, while the web's own modal edits the amount with an „Alles" shortcut
     * and `MaterialExchangeOfferUpdateRequest` requires `offeredAmount`. So the amount is editable
     * here too, and the stock bound stays the server's to enforce — the board row carries no
     * stock figure to check it against.
     *
     * @property entry the row being rewritten, which carries the version to echo.
     * @property amount the amount, as typed.
     * @property minQuality the minimum quality, as typed; a request's field only.
     * @property remark the note, as typed.
     * @property confirmingWithdrawal whether the withdrawal confirmation is up.
     */
    data class EditEntry(
        val entry: BoardEntry,
        val amount: String = "",
        val minQuality: String = "",
        val remark: String = "",
        val confirmingWithdrawal: Boolean = false,
    ) : BoardSheet {
        /** Whether „Speichern" may be pressed. */
        val submittable: Boolean
            get() = (amount.replace(',', '.').trim().toDoubleOrNull() ?: 0.0) > 0.0

        /** Whether this row's minimum-quality field applies — requests carry one, offers do not. */
        val hasQuality: Boolean get() = entry.side == BoardSide.REQUESTS
    }
}

/**
 * The item half's write, or `null` when the sheet does not describe one.
 *
 * The two create sheets carry the same three item fields and hand them to two different endpoints,
 * so the *shape* of the check lives here once rather than as a return ladder in each.
 *
 * @receiver the sheet.
 * @param write what to do with a picked key and a whole number of pieces.
 * @return the write, or `null` when either is missing.
 */
private fun BoardSheet.NewRequest.itemWrite(
    write: suspend (String, Int) -> ApiResult<Unit>,
): (suspend () -> ApiResult<Unit>)? =
    productKey?.let { key ->
        amount.trim().toIntOrNull()?.takeIf { it > 0 }?.let { pieces -> { write(key, pieces) } }
    }

/**
 * The same, for the offer sheet.
 *
 * @receiver the sheet.
 * @param write what to do with a picked key and a whole number of pieces.
 * @return the write, or `null` when either is missing.
 */
private fun BoardSheet.NewOffer.itemWrite(
    write: suspend (String, Int) -> ApiResult<Unit>,
): (suspend () -> ApiResult<Unit>)? =
    productKey?.let { key ->
        amount.trim().toIntOrNull()?.takeIf { it > 0 }?.let { pieces -> { write(key, pieces) } }
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
                            // The sheet the withdrawal was started from has lost its subject.
                            sheet =
                                if (latest.sheet is BoardSheet.EditEntry) {
                                    BoardSheet.None
                                } else {
                                    latest.sheet
                                },
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

    /**
     * The member pressed „Zurückziehen" in the edit sheet.
     *
     * With interested members it asks first and names them, because withdrawing is visible to
     * them; with nobody waiting it withdraws straight away. Design ch. 17 artboard 3.
     *
     * > **No undo.** The artboard offers a five-second undo toast. Withdrawal is
     * > `POST …/deactivate` and there is **no endpoint that reactivates a row**, so an undo would
     * > have to re-post the entry as a new one — a different row, with a new id, a new timestamp
     * > and no interested members. Flagged on the design gap list rather than faked.
     */
    fun onWithdrawRequested() {
        val sheet = mutableState.value.sheet as? BoardSheet.EditEntry ?: return
        if (sheet.entry.interestCount > 0) {
            mutableState.value =
                mutableState.value.copy(sheet = sheet.copy(confirmingWithdrawal = true))
            return
        }
        onWithdraw(sheet.entry)
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
                        updateRequestSheet {
                            it.copy(
                                matches = result.value.rows,
                                moreMatches = result.value.more,
                                searching = false,
                            )
                        }
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

    /** Publishes the request the sheet describes — material or item, as the switch stands. */
    fun onRequestSubmitted() {
        // Guarded rather than trusted: `submittable` gates the button, and this repeats the check
        // because a screen is not the only thing that can call a public method on a ViewModel.
        val sheet = (mutableState.value.sheet as? BoardSheet.NewRequest)?.takeIf { it.submittable }
        val write: (suspend () -> ApiResult<Unit>)? =
            when {
                sheet == null -> {
                    null
                }

                sheet.kind == BoardKind.ITEM -> {
                    sheet.itemWrite { key, pieces ->
                        source.createItemRequest(
                            productKey = key,
                            quantity = pieces,
                            minQuality = sheet.minQuality.toIntOrNull(),
                            remark = sheet.remark,
                        )
                    }
                }

                else -> {
                    sheet.materialId?.let { id ->
                        sheet.amount.toDoubleOrNull()?.let { amount ->
                            {
                                source.createRequest(
                                    materialId = id,
                                    amount = amount,
                                    minQuality = sheet.minQuality.toIntOrNull(),
                                    remark = sheet.remark,
                                )
                            }
                        }
                    }
                }
            }
        write?.let { submit(it) }
    }

    /** Publishes the offer the sheet describes — material or item, as the switch stands. */
    fun onOfferSubmitted() {
        val sheet = (mutableState.value.sheet as? BoardSheet.NewOffer)?.takeIf { it.submittable }
        val write: (suspend () -> ApiResult<Unit>)? =
            when {
                sheet == null -> {
                    null
                }

                sheet.kind == BoardKind.ITEM -> {
                    sheet.itemWrite { key, pieces ->
                        source.createItemOffer(
                            productKey = key,
                            quantity = pieces,
                            remark = sheet.remark,
                        )
                    }
                }

                else -> {
                    sheet.picked?.let { picked ->
                        sheet.amount.toDoubleOrNull()?.let { amount ->
                            {
                                source.createOffer(
                                    inventoryItemId = picked.inventoryItemId,
                                    amount = amount,
                                    remark = sheet.remark,
                                )
                            }
                        }
                    }
                }
            }
        write?.let { submit(it) }
    }

    /**
     * Searches the craftable products behind the item half of either create sheet.
     *
     * The picked key is cleared as soon as the text changes, for the reason
     * [onMaterialQueryChanged] gives.
     *
     * @param query what the member typed.
     */
    fun onProductQueryChanged(query: String) {
        withProductSheet(
            request = { it.copy(productName = query, productKey = null) },
            offer = { it.copy(productName = query, productKey = null) },
        )
        searchJob?.cancel()
        if (query.isBlank()) {
            withProductSheet(
                request = { it.copy(products = emptyList()) },
                offer = { it.copy(products = emptyList()) },
            )
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                val found =
                    when (val result = source.searchProducts(query)) {
                        is ApiResult.Success -> {
                            result.value
                        }

                        is ApiResult.Failure -> {
                            KrtLog.w(LOG_TAG) { "the product search failed: ${result.error}" }
                            emptyList()
                        }
                    }
                withProductSheet(
                    request = { it.copy(products = found) },
                    offer = { it.copy(products = found) },
                )
            }
    }

    /**
     * Takes one of the product results.
     *
     * @param product the picked product.
     */
    fun onProductPicked(product: BlueprintProduct) {
        searchJob?.cancel()
        withProductSheet(
            request = {
                it.copy(
                    productKey = product.productKey,
                    productName = product.name,
                    products = emptyList(),
                )
            },
            offer = {
                it.copy(
                    productKey = product.productKey,
                    productName = product.name,
                    products = emptyList(),
                )
            },
        )
    }

    /**
     * Opens „Eintrag bearbeiten" on one of the caller's own rows.
     *
     * @param entry the row.
     */
    fun onEditEntry(entry: BoardEntry) {
        if (!entry.mine) {
            return
        }
        mutableState.value =
            mutableState.value.copy(
                sheet =
                    BoardSheet.EditEntry(
                        entry = entry,
                        amount = entry.amount,
                        minQuality = entry.quality?.toString().orEmpty(),
                        remark = entry.remark.orEmpty(),
                    ),
                error = null,
            )
    }

    /**
     * Edits the open „bearbeiten" sheet.
     *
     * @param edit what to change.
     */
    fun onEntryEdited(edit: (BoardSheet.EditEntry) -> BoardSheet.EditEntry) {
        val sheet = mutableState.value.sheet as? BoardSheet.EditEntry ?: return
        mutableState.value = mutableState.value.copy(sheet = edit(sheet))
    }

    /** Sends the rewritten row. */
    fun onEntrySubmitted() {
        val sheet = (mutableState.value.sheet as? BoardSheet.EditEntry)?.takeIf { it.submittable }
        val amount = sheet?.amount?.replace(',', '.')?.trim()?.toDoubleOrNull()
        if (sheet == null || amount == null) {
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val result =
                when (sheet.entry.side) {
                    BoardSide.OFFERS -> {
                        source.updateOffer(sheet.entry, amount, sheet.remark)
                    }

                    BoardSide.REQUESTS -> {
                        source.updateRequest(
                            sheet.entry,
                            amount,
                            sheet.minQuality.toIntOrNull(),
                            sheet.remark,
                        )
                    }
                }
            when (result) {
                is ApiResult.Success -> {
                    // The write answers with the row, so it is replaced in place rather than the
                    // page re-read — the member keeps their scroll position.
                    mutableState.value =
                        mutableState.value.copy(saving = false, sheet = BoardSheet.None)
                    replace(result.value)
                    announce()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the board entry could not be rewritten: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
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
     * Applies whichever of the two edits fits the open create sheet.
     *
     * The item half lives on both sheets and behaves identically on each, so one call site rather
     * than a `when` repeated at every one of them.
     *
     * @param request what to change on the request sheet.
     * @param offer what to change on the offer sheet.
     */
    private fun withProductSheet(
        request: (BoardSheet.NewRequest) -> BoardSheet.NewRequest,
        offer: (BoardSheet.NewOffer) -> BoardSheet.NewOffer,
    ) {
        val sheet = mutableState.value.sheet
        val next =
            when (sheet) {
                is BoardSheet.NewRequest -> request(sheet)
                is BoardSheet.NewOffer -> offer(sheet)
                else -> null
            }
        next?.let { mutableState.value = mutableState.value.copy(sheet = it) }
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

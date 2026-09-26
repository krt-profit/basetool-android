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
import de.greluc.krt.profit.basetool.android.core.data.HomeLocation
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipDraft
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
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
 * What the ship editor holds.
 *
 * The draft lives in the state rather than in the composable so a 409 can hand it back unchanged —
 * the same rule the rest of phase 3 follows.
 */
sealed interface ShipEditor {
    /** No editor is open. */
    data object Closed : ShipEditor

    /**
     * The editor is open.
     *
     * @property editing the ship being changed, or `null` for a new one.
     * @property name the member's own name for it.
     * @property hull the chosen ship type, or `null` while none is.
     * @property hullQuery what is in the hull search.
     * @property insuranceLti whether the ship carries lifetime insurance.
     * @property insuranceMonths the month count, as typed, when it does not.
     * @property place where it is parked, or `null`.
     * @property fitted whether it is ready.
     * @property saving whether a save is in flight.
     * @property error what the last attempt returned, or `null`.
     */
    data class Open(
        val editing: Ship? = null,
        val name: String = "",
        val hull: ShipTypeOption? = null,
        val hullQuery: String = "",
        val insuranceLti: Boolean = true,
        val insuranceMonths: String = "",
        val place: HomeLocation? = null,
        val fitted: Boolean = false,
        val saving: Boolean = false,
        val error: ApiError? = null,
    ) : ShipEditor {
        /**
         * What the insurance field will send: `LTI` or a whole number of months from 0 to 120.
         */
        val insurance: String? get() =
            if (insuranceLti) {
                LTI
            } else {
                insuranceMonths.toIntOrNull()
                    ?.takeIf { it in 0..MAX_INSURANCE_MONTHS }
                    ?.toString()
            }

        /** Whether there is something the server will accept. */
        val submittable: Boolean get() = hull != null && insurance != null

        private companion object {
            const val LTI = "LTI"
            const val MAX_INSURANCE_MONTHS = 120
        }
    }
}

/**
 * Everything the Hangar screen draws.
 *
 * The two halves keep separate rows, totals and phases.
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
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property clearRequested whether "Hangar leeren" is waiting on its danger modal
 * @property homeLocationSet how many ships a just-finished bulk home-location write touched,
 *   until it is acknowledged
 * @property cleared how many ships a just-finished wipe removed, until it is acknowledged
 * @property bulkHomeLocation the open bulk home-location sheet, or `null`
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
    val retryIn: Int? = null,
    val online: Boolean = true,
    val editor: ShipEditor = ShipEditor.Closed,
    val hulls: List<ShipTypeOption> = emptyList(),
    val places: List<HomeLocation> = emptyList(),
    val pendingDelete: Ship? = null,
    val clearRequested: Boolean = false,
    val cleared: Int? = null,
    val homeLocationSet: Int? = null,
    val bulkHomeLocation: BulkHomeLocation? = null,
    val deleting: Boolean = false,
    val lastFailure: ApiError? = null,
) {
    /** Whether the member has narrowed anything. */
    val isNarrowed: Boolean get() = searchText.isNotBlank()

    /** How many rows the showing half has in total on the server. */
    val total: Long get() = if (segment == HangarSegment.MINE) shipsTotal else typesTotal
}

/**
 * Drives the Hangar: both halves, the ship editor, the delete confirmation and the bulk actions.
 *
 * Typing is debounced by 300 ms; switching the segment reloads that half from page 0 without delay.
 *
 * @property source where the ships come from
 */
@Suppress("TooManyFunctions")
@OptIn(FlowPreview::class)
class HangarViewModel(
    private val source: HangarSource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HangarState())

    /** What the screen draws. */
    val state: StateFlow<HangarState> = mutableState.asStateFlow()

    /**
     * The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003).
     *
     * One ladder for both segments, because they share one phase: a member who switches while
     * a countdown runs is looking at the same failed screen with a different heading.
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

    private val typedText = MutableStateFlow("")

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.update { it.copy(online = online) }
            }
        }
    }

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
        mutableState.update { it.copy(segment = segment) }
        reload(keepRows = false)
    }

    /**
     * Records a keystroke, updating the field synchronously and deferring the request.
     *
     * @param text what the member has typed so far.
     */
    fun onSearchChanged(text: String) {
        mutableState.update { it.copy(searchText = text) }
        typedText.value = text
    }

    /**
     * Shows the member's ships of the tapped aggregate type, by putting it into the search and switching
     * to „Meine Schiffe".
     *
     * @param typeName the ship type that was tapped.
     */
    fun onTypeDrilldown(typeName: String) {
        onSearchChanged(typeName)
        onSegmentSelected(HangarSegment.MINE)
    }

    /** Re-reads the showing half while keeping its rows on screen. */
    fun onRefresh() {
        mutableState.update { it.copy(refreshing = true) }
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
                val latest = mutableState.value
                mutableState.value =
                    latest.copy(
                        ships = latest.ships + result.value.rows,
                        shipsTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "next page of ships failed: ${result.error}" }
                mutableState.update { it.copy(loadingMore = false) }
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
                        types = latest.types + result.value.rows,
                        typesTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "next page of the hangar aggregate failed: ${result.error}" }
                mutableState.update { it.copy(loadingMore = false) }
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
                mutableState.update {
                    it.copy(
                        ships = result.value.rows,
                        shipsTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        phase = HangarPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                    )
                }
                retry.onSuccess()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "ships could not be read: ${result.error}" }
                mutableState.update {
                    it.copy(
                        phase = HangarPhase.Failed(result.error),
                        loadingMore = false,
                        refreshing = false,
                    )
                }
                retry.onFailure(result.error, hasContent = false)
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
                mutableState.update {
                    it.copy(
                        types = result.value.rows,
                        typesTotal = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        phase = HangarPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                    )
                }
                retry.onSuccess()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the hangar aggregate could not be read: ${result.error}" }
                mutableState.update {
                    it.copy(
                        phase = HangarPhase.Failed(result.error),
                        loadingMore = false,
                        refreshing = false,
                    )
                }
                retry.onFailure(result.error, hasContent = false)
            }
        }
    }

    /** Opens the editor for a new ship. */
    fun onCreate() {
        mutableState.update { it.copy(editor = ShipEditor.Open()) }
        loadPickers()
    }

    /**
     * Opens the editor for one of the member's ships.
     *
     * Seeded from what the row already carries, including the hull and the place, so a member who
     * only flips "fitted" does not have to pick them again.
     *
     * @param ship the row.
     */
    fun onEdit(ship: Ship) {
        val months = ship.insurance?.takeIf { it != INSURANCE_LTI }
        mutableState.update { state ->
            state.copy(
                editor =
                    ShipEditor.Open(
                        editing = ship,
                        name = ship.name.orEmpty(),
                        hull = ship.typeId?.let { ShipTypeOption(it, ship.typeName, ship.manufacturerName) },
                        insuranceLti = months == null,
                        insuranceMonths = months.orEmpty(),
                        place = ship.locationId?.let { HomeLocation(it, ship.locationName.orEmpty()) },
                        fitted = ship.fitted,
                    ),
            )
        }
        loadPickers()
    }

    /** Closes the editor, discarding what was entered. */
    fun onEditorDismissed() {
        mutableState.update { it.copy(editor = ShipEditor.Closed) }
    }

    /**
     * Updates the open editor.
     *
     * @param transform how it changes.
     */
    private fun editor(transform: (ShipEditor.Open) -> ShipEditor.Open) {
        val open = mutableState.value.editor as? ShipEditor.Open ?: return
        mutableState.update { it.copy(editor = transform(open)) }
    }

    /**
     * Sets the ship's name.
     *
     * @param value what the member typed.
     */
    fun onShipNameChanged(value: String) = editor { it.copy(name = value, error = null) }

    /**
     * Narrows the hull picker.
     *
     * @param value what the member typed.
     */
    fun onHullQueryChanged(value: String) = editor { it.copy(hullQuery = value, error = null) }

    /**
     * Picks a hull.
     *
     * @param hull the ship type.
     */
    fun onHullChosen(hull: ShipTypeOption) = editor { it.copy(hull = hull, error = null) }

    /**
     * Switches between lifetime insurance and a month count.
     *
     * @param lti whether the ship carries LTI.
     */
    fun onInsuranceLtiChanged(lti: Boolean) = editor { it.copy(insuranceLti = lti, error = null) }

    /**
     * Sets the month count.
     *
     * @param value what the member typed, unparsed.
     */
    fun onInsuranceMonthsChanged(value: String) =
        editor { it.copy(insuranceMonths = value.filter(Char::isDigit).take(MONTH_DIGITS), error = null) }

    /**
     * Picks where the ship is parked.
     *
     * @param place the location, or `null` to clear it.
     */
    fun onPlaceChosen(place: HomeLocation?) = editor { it.copy(place = place, error = null) }

    /**
     * Sets whether the ship is fitted.
     *
     * @param fitted whether it is ready.
     */
    fun onFittedChanged(fitted: Boolean) = editor { it.copy(fitted = fitted, error = null) }

    /** Saves what the editor holds. */
    fun onSave() {
        val open = mutableState.value.editor as? ShipEditor.Open
        val hull = open?.hull
        val insurance = open?.insurance
        val sendable = open != null && hull != null && insurance != null
        if (!sendable || !mutableState.value.online) {
            return
        }
        val draft =
            ShipDraft(
                name = open.name.trim().takeIf { it.isNotEmpty() },
                typeId = hull.id,
                insurance = insurance,
                locationId = open.place?.id,
                fitted = open.fitted,
            )
        editor { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val editing = open.editing
            val result =
                if (editing == null) {
                    source.create(draft)
                } else {
                    source.update(editing.id, editing.version, draft)
                }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(editor = ShipEditor.Closed) }
                    onRefresh()
                }

                is ApiResult.Failure -> {
                    editor { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Asks before removing a ship.
     *
     * @param ship the row.
     */
    fun onDeleteRequested(ship: Ship) {
        mutableState.update { it.copy(pendingDelete = ship) }
    }

    /** Opens the danger modal behind "Hangar leeren". */
    fun onClearRequested() {
        mutableState.update { it.copy(clearRequested = true) }
    }

    /** Closes it without deleting anything. */
    fun onClearDismissed() {
        mutableState.update { it.copy(clearRequested = false) }
    }

    /**
     * Deletes every ship the member owns.
     *
     * The list is reloaded rather than emptied locally: the endpoint deletes what the *server*
     * considers the caller's fleet, and a screen that clears itself would claim to know that set
     * matched the page it happened to be showing.
     */
    fun onClearConfirmed() {
        if (!mutableState.value.online) {
            return
        }
        val emptied = mutableState.value.ships.size
        mutableState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val result = source.clearHangar()
            mutableState.update { state ->
                state.copy(
                    clearRequested = false,
                    deleting = false,
                    lastFailure = (result as? ApiResult.Failure)?.error,
                    cleared = (result as? ApiResult.Success)?.let { emptied },
                )
            }
            if (result is ApiResult.Success) {
                onRefresh()
            }
        }
    }

    /** Takes the "home location set" confirmation off screen once it has been read. */
    fun onHomeLocationSetAcknowledged() {
        mutableState.update { it.copy(homeLocationSet = null) }
    }

    /** Takes the "hangar emptied" confirmation off screen once it has been read. */
    fun onClearedAcknowledged() {
        mutableState.update { it.copy(cleared = null) }
    }

    /** Opens the bulk home-location sheet. */
    fun onBulkHomeLocationRequested() {
        mutableState.update { it.copy(bulkHomeLocation = BulkHomeLocation()) }
    }

    /** Closes it. */
    fun onBulkHomeLocationDismissed() {
        mutableState.update { it.copy(bulkHomeLocation = null) }
    }

    /**
     * Records the place the whole fleet is to move to.
     *
     * @param place the chosen home location.
     */
    fun onBulkHomeLocationChosen(place: HomeLocation) {
        val bulk = mutableState.value.bulkHomeLocation ?: return
        mutableState.update { it.copy(bulkHomeLocation = bulk.copy(place = place)) }
    }

    /** Applies the chosen place to every ship. */
    fun onBulkHomeLocationApplied() {
        val bulk = mutableState.value.bulkHomeLocation
        val place = bulk?.place
        if (place == null || bulk.saving || !mutableState.value.online) {
            return
        }
        val affected = mutableState.value.ships.size
        mutableState.update { it.copy(bulkHomeLocation = bulk.copy(saving = true, error = null)) }
        viewModelScope.launch {
            when (val result = source.setHomeLocationForAll(place.id)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(bulkHomeLocation = null, homeLocationSet = affected) }
                    onRefresh()
                }

                is ApiResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            bulkHomeLocation = bulk.copy(saving = false, error = result.error),
                        )
                    }
                }
            }
        }
    }

    /** Abandons the removal. */
    fun onDeleteDismissed() {
        mutableState.update { it.copy(pendingDelete = null) }
    }

    /** Removes the ship the member confirmed. */
    fun onDeleteConfirmed() {
        val ship = mutableState.value.pendingDelete ?: return
        if (!mutableState.value.online) {
            return
        }
        mutableState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val result = source.delete(ship.id)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(pendingDelete = null, deleting = false) }
                    onRefresh()
                }

                is ApiResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            pendingDelete = null,
                            deleting = false,
                            lastFailure = result.error,
                        )
                    }
                }
            }
        }
    }

    /** Acknowledges the last write failure. */
    fun onFailureShown() {
        mutableState.update { it.copy(lastFailure = null) }
    }

    /**
     * Reads the hull and place catalogues, once per editor opening.
     *
     * A failure is silent; a ship whose hull is already picked can still be saved.
     */
    private fun loadPickers() {
        viewModelScope.launch {
            (source.shipTypes("") as? ApiResult.Success)?.let {
                mutableState.update { state -> state.copy(hulls = it.value) }
            }
            (source.homeLocations() as? ApiResult.Success)?.let {
                mutableState.update { state -> state.copy(places = it.value) }
            }
        }
    }

    private companion object {
        /** The design spec's 300 ms, the same figure every other search field uses. */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** What the server calls lifetime insurance. */
        const val INSURANCE_LTI = "LTI"

        /** A month count longer than this is a typo, not a number. */
        const val MONTH_DIGITS = 3

        /** Log subsystem. A ship's name is member input and never reaches the log. */
        const val LOG_TAG = "hangar"
    }
}

/**
 * The open bulk home-location sheet.
 *
 * @property place the chosen location, or `null` until the member picks one.
 * @property saving whether the write is in flight.
 */
data class BulkHomeLocation(
    val place: HomeLocation? = null,
    val saving: Boolean = false,
    val error: ApiError? = null,
)

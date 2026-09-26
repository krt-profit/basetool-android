/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrder
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDeleteSource
import de.greluc.krt.profit.basetool.android.core.data.RefineryPhase
import de.greluc.krt.profit.basetool.android.core.data.RefineryServerStatus
import de.greluc.krt.profit.basetool.android.core.data.RefinerySource
import de.greluc.krt.profit.basetool.android.core.data.RefineryStoreLine
import de.greluc.krt.profit.basetool.android.core.data.RefineryStoreSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** Chapter 11 asks for a minutely countdown, and nothing on these screens is finer-grained. */
private const val TICK_MILLIS = 60_000L

/**
 * The clock the Raffinerie screens judge readiness against.
 *
 * Injected rather than looped inside the view model, so a test can pass `emptyFlow()` and drive
 * [RefineryListState.now] itself.
 *
 * @return a flow emitting the current time once a minute, for as long as it is collected.
 */
fun minuteTicker(): Flow<OffsetDateTime> =
    flow {
        while (true) {
            delay(TICK_MILLIS)
            emit(OffsetDateTime.now())
        }
    }

/** Which of the member's orders the list shows. */
enum class RefineryFilter {
    /**
     * Still refining or waiting to be collected; the default filter.
     *
     * Combines two of the other filters rather than naming a server status, and is declared first
     * because the chip row follows declaration order.
     */
    ACTIVE,

    /** Everything, including the booked and the cancelled ones. */
    ALL,

    /** Still refining. */
    RUNNING,

    /** Finished and not yet booked into the Lager. */
    READY,

    /** Booked into the Lager. */
    STORED,
    ;

    /**
     * The server statuses this filter has to ask for.
     *
     * `RUNNING` and `READY` both request `OPEN`/`IN_PROGRESS`; the device splits them by the run's end
     * time.
     *
     * @return the statuses to request; empty means all of them.
     */
    fun serverStatuses(): Set<RefineryServerStatus> =
        when (this) {
            ALL -> {
                emptySet()
            }

            ACTIVE, RUNNING, READY -> {
                setOf(RefineryServerStatus.OPEN, RefineryServerStatus.IN_PROGRESS)
            }

            STORED -> {
                setOf(RefineryServerStatus.COMPLETED)
            }
        }

    /**
     * Whether a row belongs on screen under this filter.
     *
     * @param order the row.
     * @param now the moment to judge readiness against.
     * @return whether to show it.
     */
    fun accepts(
        order: RefineryOrder,
        now: OffsetDateTime,
    ): Boolean =
        when (this) {
            ACTIVE -> order.phaseAt(now) in setOf(RefineryPhase.RUNNING, RefineryPhase.READY)
            ALL -> true
            RUNNING -> order.phaseAt(now) == RefineryPhase.RUNNING
            READY -> order.phaseAt(now) == RefineryPhase.READY
            STORED -> order.phaseAt(now) == RefineryPhase.STORED
        }
}

/** How far the list has got. */
sealed interface RefineryPhaseState {
    /** The first page is on its way. */
    data object Loading : RefineryPhaseState

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : RefineryPhaseState

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : RefineryPhaseState
}

/**
 * Everything the order list draws.
 *
 * @property filter which chip is selected
 * @property loaded every row the current filter's request has returned so far
 * @property phase how far the first page has got
 * @property page the last page index that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property myUserId the caller's own backend id, or `null` while the identity read is out or has
 *   failed
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property now the clock the countdown and the ready-split are judged against; ticks each minute
 */
data class RefineryListState(
    val filter: RefineryFilter = RefineryFilter.ACTIVE,
    val loaded: List<RefineryOrder> = emptyList(),
    val phase: RefineryPhaseState = RefineryPhaseState.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val myUserId: String? = null,
    val retryIn: Int? = null,
    val now: OffsetDateTime = OffsetDateTime.now(),
) {
    /**
     * The rows to draw: [loaded] filtered by the chip against [now].
     *
     * Because `RUNNING` and `READY` split one server answer, the screen shows no total.
     */
    val orders: List<RefineryOrder> get() = loaded.filter { filter.accepts(it, now) }
}

/**
 * Drives the Raffinerie order list (REQ-APP-REF-001…004).
 *
 * „Abholbereit" is derived from the run's end time, so [now] advances once a minute while the screen
 * is open.
 *
 * @property source where the orders come from
 * @property identity supplies the caller's backend user id, or `null` where a caller cannot be
 *   resolved at all
 * @property liveSync the shared change stream, or `null` when it is not wired
 */
class RefineryViewModel(
    private val source: RefinerySource,
    private val identity: IdentitySource? = null,
    private val liveSync: LiveSyncSource? = null,
    clock: Flow<OffsetDateTime> = minuteTicker(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(RefineryListState())

    /** What the screen draws. */
    val state: StateFlow<RefineryListState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedOnce = false

    /** The chapter-14 retry ladder for this screen's first load. */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.update { it.copy(retryIn = left) } },
            onRetry = { reload(keepRows = false) },
        )

    init {
        resolveIdentity()
        observeLiveSync(liveSync, setOf(LiveSyncTopic.REFINERY)) { sections ->
            if (LiveSyncSections.REFINERY_QUEUE in sections) {
                reload(keepRows = true)
            }
        }
        viewModelScope.launch {
            clock.collect { now -> mutableState.update { it.copy(now = now) } }
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
     * Switches the chip and reloads, even between `RUNNING` and `READY`.
     *
     * @param filter the chip that was tapped.
     */
    fun onFilterChanged(filter: RefineryFilter) {
        if (filter == mutableState.value.filter) {
            return
        }
        loadedOnce = true
        mutableState.update { it.copy(filter = filter) }
        reload(keepRows = false)
    }

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        loadedOnce = true
        retry.onManualRetry()
    }

    /** Re-reads the first page while keeping the rows on screen. */
    fun onRefresh() {
        mutableState.update { it.copy(refreshing = true) }
        loadedOnce = true
        reload(keepRows = true)
    }

    /** Appends the next page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is RefineryPhaseState.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (
                val result =
                    source.myOrders(current.filter.serverStatuses(), page = current.page + 1)
            ) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            loaded = latest.loaded + result.value.rows,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of orders failed: ${result.error}" }
                    mutableState.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    /**
     * Loads page 0 for the current chip.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        loadJob?.cancel()
        val filter = mutableState.value.filter
        if (!keepRows) {
            mutableState.update { it.copy(phase = RefineryPhaseState.Loading) }
        }
        loadJob =
            viewModelScope.launch {
                when (val result = source.myOrders(filter.serverStatuses(), page = 0)) {
                    is ApiResult.Success -> {
                        retry.onSuccess()
                        mutableState.update {
                            it.copy(
                                loaded = result.value.rows,
                                page = result.value.page,
                                hasMore = result.value.hasMore,
                                phase = RefineryPhaseState.Ready,
                                loadingMore = false,
                                refreshing = false,
                                now = OffsetDateTime.now(),
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "orders could not be read: ${result.error}" }
                        mutableState.update {
                            it.copy(
                                phase = RefineryPhaseState.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                        }
                        retry.onFailure(result.error, hasContent = keepRows)
                    }
                }
            }
    }

    /**
     * Reads who the caller is, so a card can say which order is theirs.
     *
     * **Never fatal.** The screen's subject is the unit's refinery runs; losing the „ (du)"
     * suffix is a smaller failure than an error banner over content that loaded fine. A card then
     * names its owner plainly, which is wrong about nobody.
     */
    private fun resolveIdentity() {
        val reader = identity ?: return
        viewModelScope.launch {
            when (val result = reader.myUserId()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(myUserId = result.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "own user id could not be read: ${result.error}" }
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. A member's yield is their business and never reaches the log. */
        const val LOG_TAG = "refinery"
    }
}

/** How far one order has got. */
sealed interface RefineryDetailPhase {
    /** In flight. */
    data object Loading : RefineryDetailPhase

    /** It arrived. */
    data object Ready : RefineryDetailPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : RefineryDetailPhase
}

/**
 * One order in full, and the booking action.
 *
 * @property orderId which order, known before anything has loaded
 * @property order the order once it arrives
 * @property phase how far the read has got
 * @property refreshing whether a pull-to-refresh is running
 * @property confirming whether the „In Lager buchen" confirmation is showing
 * @property storing whether the booking is in flight
 * @property stored whether this screen booked it, which is what the confirmation line reports
 * @property lines the Einlagern form, one per material of the run, or empty while it is closed
 * @property busy set while the run is being booked, or `null`
 * @property online whether a write can be sent at all
 * @property error what the last write returned, or `null`
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property now the clock readiness is judged against; ticks each minute
 */
data class RefineryDetailState(
    val orderId: String,
    val order: RefineryOrder? = null,
    val phase: RefineryDetailPhase = RefineryDetailPhase.Loading,
    val refreshing: Boolean = false,
    val confirming: Boolean = false,
    val storing: Boolean = false,
    val stored: Boolean = false,
    val online: Boolean = true,
    val error: ApiError? = null,
    val retryIn: Int? = null,
    val now: OffsetDateTime = OffsetDateTime.now(),
    val myUserId: String? = null,
    val lines: List<RefineryStoreLine> = emptyList(),
    val memberPicker: RefineryMemberPickerState = RefineryMemberPickerState(),
    val busy: String? = null,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
) {
    /** Whether the booking may be offered at all. */
    val storable: Boolean
        get() = online && !storing && order?.canStoreAt(now) == true

    /**
     * Whether deleting this run is allowed: it is the caller's own and not yet booked (REQ-APP-REF-012).
     *
     * The server would allow deleting a booked run; the app draws the action locked instead.
     */
    val deletable: Boolean
        get() = order != null && order.status != RefineryServerStatus.COMPLETED && mine

    /**
     * Whether this run is the caller's own, which every write requires (`canEditRefineryOrder`).
     *
     * An unknown identity counts as not mine.
     */
    val mine: Boolean
        get() = myUserId != null && order?.ownerId == myUserId
}

/**
 * The writes the Raffinerie detail performs, and the identity that gates them.
 *
 * @property store books a finished run's yield into the Lager, or `null` where that is not wired.
 * @property roster who the output may be booked onto, for a Logistician who may choose.
 * @property delete cancels the run, or `null` where the action is not offered.
 * @property identity the caller's own id, so ownership can gate the two writes above.
 */
data class RefineryDetailSeams(
    val store: RefineryStoreSource? = null,
    val roster: BookInOptions? = null,
    val delete: RefineryOrderDeleteSource? = null,
    val identity: IdentitySource? = null,
)

/**
 * Drives one Raffinerie order and its booking (REQ-APP-REF-005…006).
 *
 * Each material's booking starts at the run's computed figure and the order's refinery, both
 * editable.
 *
 * @property source where the order comes from
 * @property connectivity whether the device has a network
 * @property orderId which order to load
 * @property liveSync the shared change stream, or `null` when it is not wired
 * @property seams the booking and the deletion, plus the identity that gates them, each absent
 *   where it is not wired
 */
class RefineryDetailViewModel(
    private val source: RefinerySource,
    connectivity: Connectivity?,
    orderId: String,
    private val liveSync: LiveSyncSource? = null,
    private val seams: RefineryDetailSeams = RefineryDetailSeams(),
    clock: Flow<OffsetDateTime> = minuteTicker(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(RefineryDetailState(orderId = orderId))

    /**
     * Who the output is booked onto, for the Logistician who may choose (REQ-SEC-039).
     *
     * Held here rather than in the sheet so the choice survives a recomposition and so the search
     * runs in the view model's scope.
     */
    val memberPicker =
        RefineryMemberPicker(
            roster = seams.roster,
            scope = viewModelScope,
            read = { mutableState.value.memberPicker },
            write = { picker -> mutableState.update { it.copy(memberPicker = picker) } },
        )

    /** What the screen draws. */
    val state: StateFlow<RefineryDetailState> = mutableState.asStateFlow()

    /** The chapter-14 retry ladder for this screen's first load. */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.update { it.copy(retryIn = left) } },
            onRetry = { load(keepOrder = false) },
        )

    init {
        resolveIdentity()
        observeLiveSync(
            liveSync,
            setOf(LiveSyncTopic.refineryOrder(orderId), LiveSyncTopic.REFINERY),
        ) { sections ->
            if (LiveSyncSections.REFINERY_ORDER in sections ||
                LiveSyncSections.REFINERY_STORE in sections
            ) {
                load(keepOrder = true)
            }
        }
        viewModelScope.launch {
            clock.collect { now -> mutableState.update { it.copy(now = now) } }
        }
        connectivity?.let { link ->
            viewModelScope.launch {
                link.online.collect { online ->
                    mutableState.update { it.copy(online = online) }
                }
            }
        }
        load(keepOrder = false)
    }

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    /** Re-reads the order while keeping what is on screen. */
    fun onRefresh() {
        mutableState.update { it.copy(refreshing = true) }
        load(keepOrder = true)
    }

    /** Opens the booking confirmation. */
    fun onStoreRequested() {
        if (!mutableState.value.storable) {
            return
        }
        mutableState.update { it.copy(confirming = true, error = null) }
    }

    /** Closes it without booking. */
    fun onStoreDismissed() {
        mutableState.update { it.copy(confirming = false) }
    }

    /** Books the yield into the Lager. */
    fun onStoreConfirmed() {
        val order = mutableState.value.order ?: return
        if (!mutableState.value.storable) {
            return
        }
        mutableState.update { it.copy(confirming = false, storing = true, error = null) }
        viewModelScope.launch {
            when (val result = source.store(order)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(storing = false, stored = true, error = null) }
                    publishLiveSync(
                        liveSync,
                        LiveSyncTopic.refineryOrder(order.id),
                        LiveSyncSections.REFINERY_ORDER,
                        LiveSyncSections.REFINERY_STORE,
                    )
                    publishLiveSync(liveSync, LiveSyncTopic.REFINERY, LiveSyncSections.REFINERY_QUEUE)
                    publishLiveSync(
                        liveSync,
                        LiveSyncTopic.INVENTORY,
                        LiveSyncSections.INVENTORY_STOCK,
                    )
                    load(keepOrder = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "booking the yield failed: ${result.error}" }
                    mutableState.update { it.copy(storing = false, error = result.error) }
                }
            }
        }
    }

    /** Clears the last write error. */
    fun onErrorDismissed() {
        mutableState.update { it.copy(error = null) }
    }

    /**
     * Reads the order.
     *
     * @param keepOrder whether what is on screen survives until the answer arrives.
     */
    private fun load(keepOrder: Boolean) {
        val id = mutableState.value.orderId
        if (!keepOrder) {
            mutableState.update { it.copy(phase = RefineryDetailPhase.Loading) }
        }
        viewModelScope.launch {
            when (val result = source.detail(id)) {
                is ApiResult.Success -> {
                    retry.onSuccess()
                    mutableState.update {
                        it.copy(
                            order = result.value,
                            phase = RefineryDetailPhase.Ready,
                            refreshing = false,
                            now = OffsetDateTime.now(),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "order could not be read: ${result.error}" }
                    mutableState.update {
                        it.copy(
                            phase = RefineryDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
                    }
                    retry.onFailure(result.error, hasContent = keepOrder)
                }
            }
        }
    }

    /**
     * Opens the Einlagern form, one line per material of the run.
     *
     * Each line starts at the computed figure and at the order's own refinery, which is what makes
     * the common case a single tap — and every one of them is meant to be overridable, because what
     * the run calculated and what came out of it are not always the same number.
     */
    fun onStoreFormRequested() {
        val order = mutableState.value.order ?: return
        mutableState.update { state ->
            state.copy(
                lines =
                    order.yields.mapNotNull { good ->
                        good.materialId?.let {
                            RefineryStoreLine(
                                materialId = it,
                                materialName = good.materialName,
                                computed = good.amount,
                                amount = good.amount.toString(),
                                quality = good.quality ?: 0,
                                locationId = order.locationId,
                            )
                        }
                    },
                error = null,
            )
        }
    }

    /** Closes the form, discarding what was typed on lines that were not booked. */
    fun onStoreFormDismissed() {
        mutableState.update { it.copy(lines = emptyList(), busy = null) }
    }

    /**
     * Records a change to one line.
     *
     * @param line the line as it now stands.
     */
    fun onLineChanged(line: RefineryStoreLine) {
        mutableState.update { state ->
            state.copy(
                lines =
                    state.lines.map { if (it.key == line.key) line else it },
            )
        }
    }

    /** The member asked to delete this run; the confirmation is raised. */
    fun onDeleteRequested() {
        mutableState.update { it.copy(confirmingDelete = true, error = null) }
    }

    /** The confirmation was dismissed. */
    fun onDeleteDismissed() {
        mutableState.update { it.copy(confirmingDelete = false) }
    }

    /**
     * Deletes the run.
     *
     * No undo is offered afterwards, because there is nothing to undo with: the server cancels the
     * order and has no endpoint that brings it back.
     */
    fun onDeleteConfirmed() {
        val current = mutableState.value
        val writer = seams.delete
        val id = current.order?.id.takeIf { current.deletable && !current.deleting }
        if (writer == null || id == null) {
            return
        }
        mutableState.value = current.copy(deleting = true, error = null)
        viewModelScope.launch {
            when (val answer = writer.deleteOrder(id)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            deleting = false,
                            confirmingDelete = false,
                            deleted = true,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "deleting the run was refused: ${answer.error}" }
                    mutableState.update {
                        it.copy(
                            deleting = false,
                            confirmingDelete = false,
                            error = answer.error,
                        )
                    }
                }
            }
        }
    }

    /**
     * Books every line of the form in one call, since the server closes the order on it.
     */
    fun onStoreAll() {
        val current = mutableState.value
        val orderId = current.order?.id
        val writer = seams.store
        val sendable = orderId != null && writer != null && current.lines.isNotEmpty()
        if (!sendable || current.busy != null) {
            return
        }
        mutableState.value = current.copy(busy = ALL_LINES, error = null)
        viewModelScope.launch {
            val answer = requireNotNull(writer).storeLines(requireNotNull(orderId), current.lines)
            when (answer) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(busy = null, stored = true, lines = emptyList()) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "storing the run was refused: ${answer.error}" }
                    mutableState.update { it.copy(busy = null, error = answer.error) }
                }
            }
        }
    }

    /**
     * Reads who the caller is, so the writes can be gated on ownership.
     *
     * A failure leaves [RefineryDetailState.mine] false and the writes locked with their reason,
     * which is the safe direction: the server would refuse them anyway, and a lock explains itself
     * where a `403` toast does not.
     */
    private fun resolveIdentity() {
        val reader = seams.identity ?: return
        viewModelScope.launch {
            when (val result = reader.myUserId()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(myUserId = result.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "own user id could not be read: ${result.error}" }
                }
            }
        }
    }

    private companion object {
        /** What `busy` holds while the whole run is in flight; the call is not per line. */
        const val ALL_LINES = "all"

        /** Log subsystem. */
        const val LOG_TAG = "refinery"
    }
}

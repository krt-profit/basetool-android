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
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** Chapter 11 asks for a minutely countdown, and nothing on these screens is finer-grained. */
private const val TICK_MILLIS = 60_000L

/**
 * The clock the Raffinerie screens judge readiness against.
 *
 * A parameter rather than a `while (true)` inside a ViewModel, and the reason is not tidiness: an
 * endless ticker started in `init` never lets a test's virtual clock go idle, so
 * `advanceUntilIdle()` hangs forever instead of failing. Found exactly that way. A test passes
 * `emptyFlow()` and drives [RefineryListState.now] itself.
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
     * `RUNNING` and `READY` ask for the same pair, because the server does not distinguish them —
     * both are `OPEN`/`IN_PROGRESS` and the run's end time is what tells them apart. The split
     * happens on the device, against a clock that ticks.
     *
     * @return the statuses to request; empty means all of them.
     */
    fun serverStatuses(): Set<RefineryServerStatus> =
        when (this) {
            ALL -> {
                emptySet()
            }

            RUNNING, READY -> {
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
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property now the clock the countdown and the ready-split are judged against; ticks each minute
 */
data class RefineryListState(
    val filter: RefineryFilter = RefineryFilter.ALL,
    val loaded: List<RefineryOrder> = emptyList(),
    val phase: RefineryPhaseState = RefineryPhaseState.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val now: OffsetDateTime = OffsetDateTime.now(),
) {
    /**
     * The rows to draw.
     *
     * `RUNNING` and `READY` are a split of one server answer, so this filters what arrived rather
     * than what the server counted. That is also why this screen shows **no total**: a count taken
     * from the server would describe the unsplit pair, and one taken from [loaded] would describe
     * only the pages fetched so far. Neither is the number a member would read it as, so the
     * screen states none and keeps the „mehr laden" control honest instead.
     */
    val orders: List<RefineryOrder> get() = loaded.filter { filter.accepts(it, now) }
}

/**
 * Drives the member's own Raffinerie orders (REQ-APP-REF-001…004).
 *
 * **The clock is state.** „Abholbereit" is not a status the server has — it is the run's end time
 * having passed — so the list would sit on „In Arbeit" forever without something that ticks. [now]
 * advances once a minute, which is the granularity design chapter 11 asks for and cheap enough to
 * run for as long as the screen is open.
 *
 * @property source where the orders come from
 * @property liveSync the shared change stream, or `null` when it is not wired
 */
class RefineryViewModel(
    private val source: RefinerySource,
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
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepRows = false) },
        )

    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.REFINERY)) { sections ->
            if (LiveSyncSections.REFINERY_QUEUE in sections) {
                reload(keepRows = true)
            }
        }
        viewModelScope.launch {
            clock.collect { now -> mutableState.value = mutableState.value.copy(now = now) }
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
     * Switches the chip.
     *
     * A move between `RUNNING` and `READY` needs no round trip — they are one server answer split
     * two ways — but reloading anyway keeps one code path, and the alternative is a cache whose
     * staleness nobody would notice until a member wondered why a finished run was missing.
     *
     * @param filter the chip that was tapped.
     */
    fun onFilterChanged(filter: RefineryFilter) {
        if (filter == mutableState.value.filter) {
            return
        }
        loadedOnce = true
        mutableState.value = mutableState.value.copy(filter = filter)
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
                            loaded = latest.loaded + result.value.orders,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of orders failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
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
            mutableState.value = mutableState.value.copy(phase = RefineryPhaseState.Loading)
        }
        loadJob =
            viewModelScope.launch {
                when (val result = source.myOrders(filter.serverStatuses(), page = 0)) {
                    is ApiResult.Success -> {
                        retry.onSuccess()
                        mutableState.value =
                            mutableState.value.copy(
                                loaded = result.value.orders,
                                page = result.value.page,
                                hasMore = result.value.hasMore,
                                phase = RefineryPhaseState.Ready,
                                loadingMore = false,
                                refreshing = false,
                                now = OffsetDateTime.now(),
                            )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "orders could not be read: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = RefineryPhaseState.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                        retry.onFailure(result.error, hasContent = keepRows)
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
    val lines: List<RefineryStoreLine> = emptyList(),
    val busy: String? = null,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
) {
    /** Whether the booking may be offered at all. */
    val storable: Boolean
        get() = online && !storing && order?.canStoreAt(now) == true

    /**
     * Whether deleting this run is allowed.
     *
     * A booked run is not deletable: its yield already exists as Lager rows, and cancelling the
     * order here would not take them back — design ch. 11 artboard 7 says so in the modal, and
     * the corresponding correction is made on the Lager rows instead. The **server allows it**
     * (`DELETE` only sets `CANCELED`, whatever the status), so this is the app's rule and the
     * action is drawn locked rather than left out (`REQ-APP-REF-012`).
     */
    val deletable: Boolean
        get() = order != null && order.status != RefineryServerStatus.COMPLETED
}

/**
 * The writes the Raffinerie detail performs.
 *
 * Bundled rather than passed one by one: the view model already carries the six arguments detekt
 * allows, and a seventh would buy a suppression instead of a smaller constructor.
 *
 * @property store books a finished run's yield into the Lager, or `null` where that is not wired.
 * @property delete cancels the run, or `null` where the action is not offered.
 */
data class RefineryDetailWrites(
    val store: RefineryStoreSource? = null,
    val delete: RefineryOrderDeleteSource? = null,
)

/**
 * Drives one Raffinerie order and its booking (REQ-APP-REF-005…006).
 *
 * **The booking starts from the order and is meant to be corrected.** Each material begins at the
 * run's computed figure and the order's own refinery; every one of those is editable, because what
 * a run calculated and what came out of it are not always the same number — design chapter 11,
 * artboard 3, which calls that override the reason the form exists.
 *
 * @property source where the order comes from
 * @property connectivity whether the device has a network
 * @property orderId which order to load
 * @property liveSync the shared change stream, or `null` when it is not wired
 * @property writes the two writes this screen performs — the booking and the deletion — each
 *   absent where it is not wired
 */
class RefineryDetailViewModel(
    private val source: RefinerySource,
    connectivity: Connectivity?,
    orderId: String,
    private val liveSync: LiveSyncSource? = null,
    private val writes: RefineryDetailWrites = RefineryDetailWrites(),
    clock: Flow<OffsetDateTime> = minuteTicker(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(RefineryDetailState(orderId = orderId))

    /** What the screen draws. */
    val state: StateFlow<RefineryDetailState> = mutableState.asStateFlow()

    /** The chapter-14 retry ladder for this screen's first load. */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { load(keepOrder = false) },
        )

    init {
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
            clock.collect { now -> mutableState.value = mutableState.value.copy(now = now) }
        }
        connectivity?.let { link ->
            viewModelScope.launch {
                link.online.collect { online ->
                    mutableState.value = mutableState.value.copy(online = online)
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
        mutableState.value = mutableState.value.copy(refreshing = true)
        load(keepOrder = true)
    }

    /** Opens the booking confirmation. */
    fun onStoreRequested() {
        if (!mutableState.value.storable) {
            return
        }
        mutableState.value = mutableState.value.copy(confirming = true, error = null)
    }

    /** Closes it without booking. */
    fun onStoreDismissed() {
        mutableState.value = mutableState.value.copy(confirming = false)
    }

    /** Books the yield into the Lager. */
    fun onStoreConfirmed() {
        val order = mutableState.value.order ?: return
        if (!mutableState.value.storable) {
            return
        }
        mutableState.value = mutableState.value.copy(confirming = false, storing = true, error = null)
        viewModelScope.launch {
            when (val result = source.store(order)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(storing = false, stored = true, error = null)
                    // Two rooms, because a booking changes two screens that are not the same
                    // screen: this order, and the Lager it just created entries in. Announcing
                    // only the order would leave every open Lager — web tab or phone — showing a
                    // stock figure that is already wrong.
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
                    mutableState.value =
                        mutableState.value.copy(storing = false, error = result.error)
                }
            }
        }
    }

    /** Clears the last write error. */
    fun onErrorDismissed() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    /**
     * Reads the order.
     *
     * @param keepOrder whether what is on screen survives until the answer arrives.
     */
    private fun load(keepOrder: Boolean) {
        val id = mutableState.value.orderId
        if (!keepOrder) {
            mutableState.value = mutableState.value.copy(phase = RefineryDetailPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.detail(id)) {
                is ApiResult.Success -> {
                    retry.onSuccess()
                    mutableState.value =
                        mutableState.value.copy(
                            order = result.value,
                            phase = RefineryDetailPhase.Ready,
                            refreshing = false,
                            now = OffsetDateTime.now(),
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "order could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = RefineryDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
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
        mutableState.value =
            mutableState.value.copy(
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

    /** Closes the form, discarding what was typed on lines that were not booked. */
    fun onStoreFormDismissed() {
        mutableState.value = mutableState.value.copy(lines = emptyList(), busy = null)
    }

    /**
     * Records a change to one line.
     *
     * @param line the line as it now stands.
     */
    fun onLineChanged(line: RefineryStoreLine) {
        mutableState.value =
            mutableState.value.copy(
                lines =
                    mutableState.value.lines.map { if (it.key == line.key) line else it },
            )
    }

    /** The member asked to delete this run; the confirmation is raised. */
    fun onDeleteRequested() {
        mutableState.value = mutableState.value.copy(confirmingDelete = true, error = null)
    }

    /** The confirmation was dismissed. */
    fun onDeleteDismissed() {
        mutableState.value = mutableState.value.copy(confirmingDelete = false)
    }

    /**
     * Deletes the run.
     *
     * No undo is offered afterwards, because there is nothing to undo with: the server cancels the
     * order and has no endpoint that brings it back.
     */
    fun onDeleteConfirmed() {
        val current = mutableState.value
        val writer = writes.delete
        val id = current.order?.id.takeIf { current.deletable && !current.deleting }
        if (writer == null || id == null) {
            return
        }
        mutableState.value = current.copy(deleting = true, error = null)
        viewModelScope.launch {
            when (val answer = writer.deleteOrder(id)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            deleting = false,
                            confirmingDelete = false,
                            deleted = true,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "deleting the run was refused: ${answer.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            deleting = false,
                            confirmingDelete = false,
                            error = answer.error,
                        )
                }
            }
        }
    }

    /**
     * Books every line of the form in one call.
     *
     * One call, because the server closes the order on it: booking card by card loses every
     * material after the first, which a device demonstrated before this was a single submit.
     */
    fun onStoreAll() {
        val current = mutableState.value
        val orderId = current.order?.id
        val writer = writes.store
        val sendable = orderId != null && writer != null && current.lines.isNotEmpty()
        if (!sendable || current.busy != null) {
            return
        }
        mutableState.value = current.copy(busy = ALL_LINES, error = null)
        viewModelScope.launch {
            val answer = requireNotNull(writer).storeLines(requireNotNull(orderId), current.lines)
            when (answer) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(busy = null, stored = true, lines = emptyList())
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "storing the run was refused: ${answer.error}" }
                    mutableState.value =
                        mutableState.value.copy(busy = null, error = answer.error)
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

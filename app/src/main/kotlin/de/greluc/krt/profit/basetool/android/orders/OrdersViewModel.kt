/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAssignee
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandoverSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemStock
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.data.JobOrderWorkSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.MaterialClaimSource
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the queue has got. */
sealed interface OrdersPhase {
    /** The first page is on its way. */
    data object Loading : OrdersPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : OrdersPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : OrdersPhase
}

/**
 * Everything the queue draws.
 *
 * @property statuses which statuses are selected; empty means all of them
 * @property orders the rows loaded so far
 * @property total how many the filter matches on the server
 * @property phase how far the first page has got
 * @property page the last page index that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property expanded which orders have their material list open, by id
 */
data class OrdersState(
    val statuses: Set<JobOrderStatus> = emptySet(),
    val orders: List<JobOrder> = emptyList(),
    val total: Long = 0,
    val phase: OrdersPhase = OrdersPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val expanded: Set<String> = emptySet(),
    val ageThresholds: JobOrderAgeThresholds = JobOrderAgeThresholds(),
)

/**
 * Drives the Auftrag queue.
 *
 * **The status filter is server-side**, like every other list in this app: filtering a page the
 * server already truncated would make the stated count wrong.
 *
 * The material list of a row collapses by default, mirroring the web app. The open/closed set lives
 * in this state rather than in each row's composable so it survives a scroll — a `LazyColumn`
 * disposes what leaves the viewport, and a member who opened three rows would find them shut on the
 * way back.
 *
 * @property source where the orders come from
 */
class OrdersViewModel(
    private val source: JobOrderSource,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.ORDERS)) { sections ->
            // The queue room is refused outright to a requester who only sees their own Aufträge,
            // so a screen that never hears from it is correct rather than broken — the server said
            // so in the subscribed list.
            if (LiveSyncSections.ORDERS_QUEUE in sections) {
                reload(keepRows = true)
            }
        }
    }

    private val mutableState = MutableStateFlow(OrdersState())

    /** What the screen draws. */
    val state: StateFlow<OrdersState> = mutableState.asStateFlow()

    /**
     * The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003).
     *
     * Shared rather than re-derived: the conditions under which a countdown is right are the same
     * on every screen.
     */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepRows = false) },
        )

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    private var loadJob: Job? = null
    private var loadedOnce = false

    /** Loads the first page, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /**
     * Narrows to a set of statuses, or widens to all of them when [statuses] is empty.
     *
     * @param statuses the statuses to show.
     */
    fun onStatusesChanged(statuses: Set<JobOrderStatus>) {
        if (statuses == mutableState.value.statuses) {
            return
        }
        loadedOnce = true
        mutableState.value = mutableState.value.copy(statuses = statuses)
        reload(keepRows = false)
    }

    /**
     * Opens or closes one row's material list.
     *
     * @param orderId the row that was tapped.
     */
    fun onToggleMaterials(orderId: String) {
        val open = mutableState.value.expanded
        mutableState.value =
            mutableState.value.copy(
                expanded = if (orderId in open) open - orderId else open + orderId,
            )
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
        if (current.loadingMore || !current.hasMore || current.phase !is OrdersPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.queue(current.statuses, page = current.page + 1)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            orders = latest.orders + result.value.orders,
                            total = result.value.totalElements,
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
     * Loads page 0 for the current filter.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        loadJob?.cancel()
        val statuses = mutableState.value.statuses
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = OrdersPhase.Loading)
        }
        loadJob =
            viewModelScope.launch {
                // Asked for alongside the page rather than in an init block: the source caches
                // them after the first read, and a colour that arrives one frame after the rows
                // would repaint the list in front of the member.
                val thresholds = source.ageThresholds()
                when (val result = source.queue(statuses, page = 0)) {
                    is ApiResult.Success -> {
                        mutableState.value =
                            mutableState.value.copy(
                                ageThresholds = thresholds,
                                orders = result.value.orders,
                                total = result.value.totalElements,
                                page = result.value.page,
                                hasMore = result.value.hasMore,
                                phase = OrdersPhase.Ready,
                                loadingMore = false,
                                refreshing = false,
                            )
                        retry.onSuccess()
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "orders could not be read: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = OrdersPhase.Failed(result.error),
                                loadingMore = false,
                                refreshing = false,
                            )
                        retry.onFailure(result.error, hasContent = keepRows)
                    }
                }
            }
    }

    private companion object {
        /** Log subsystem. A comment is member input and never reaches the log. */
        const val LOG_TAG = "orders"
    }
}

/** How far one order has got. */
sealed interface OrderDetailPhase {
    /** In flight. */
    data object Loading : OrderDetailPhase

    /** It arrived. */
    data object Ready : OrderDetailPhase

    /**
     * It did not.
     *
     * @property error what went wrong; `Forbidden` and `NotFound` are ordinary answers.
     */
    data class Failed(
        val error: ApiError,
    ) : OrderDetailPhase
}

/**
 * One order in full.
 *
 * @property orderId which order, known before anything has loaded
 * @property order the order once it arrives
 * @property phase how far the read has got
 * @property refreshing whether a pull-to-refresh is running
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property me who the caller is, once known; `null` while the read is out or after it failed
 * @property noteDraft the caller's note while they are editing it, or `null` when the editor is
 *   closed. Empty string is an open editor holding nothing, which is how a note is cleared
 * @property statusPickerOpen whether the status picker is showing
 * @property saving whether a write is in flight
 * @property online whether a write can be sent at all
 * @property error what the last write returned, or `null`
 */
data class OrderDetailState(
    val orderId: String,
    val order: JobOrder? = null,
    val phase: OrderDetailPhase = OrderDetailPhase.Loading,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val me: Identity? = null,
    val noteDraft: String? = null,
    /**
     * The note the server refused in an optimistic-lock race, kept so the member can re-apply it.
     *
     * Design ch. 10 artboard 7: a 409 does not discard what somebody typed. The field is reset to
     * what the server now holds and the refused text is offered beside it — losing a paragraph
     * because a colleague saved first is the failure this whole mechanism exists to prevent.
     */
    val rejectedNote: String? = null,
    val handover: OrderHandoverDraft? = null,
    /**
     * The item handover being filled in, or `null` when the sheet is shut.
     *
     * Its own slot beside [handover]: the two are separate writes against separate endpoints, and
     * one shared slot would let a material sheet be submitted as an item one.
     */
    val itemHandover: ItemHandoverDraft? = null,
    /**
     * The production run being filled in, or `null` when the sheet is shut.
     *
     * A **second** write on an item Auftrag, not a variant of the handover: the Übergabe hands
     * finished goods over, the Herstellung consumes the earmarked material and creates item stock
     * (design ch. 10 artboard 15).
     */
    val production: ProductionDraft? = null,
    /** Which page of the Auftrag is showing (design ch. 10 artboard 2). */
    val tab: OrderTab = OrderTab.POSITIONS,
    /** The Zusagen — their own read, because they are not part of the order aggregate. */
    val claims: ClaimsState = ClaimsState(),
    /**
     * The units the caller belongs to.
     *
     * Read for one question: whether they are on the **requesting** side of this order, which is
     * what `canEditJobOrderAsRequester` turns on and the only way to know which of the two edit
     * forms to open.
     */
    val myUnitIds: Set<String> = emptySet(),
    /**
     * The game-item stock earmarked to this Auftrag, keyed by item id.
     *
     * Its own read (`/orders/{id}/item-stock`), because the order aggregate carries no stock. It
     * fills the availability chip on each sub-assembly — „Lager" or „Fehlt n".
     */
    val itemStock: Map<String, JobOrderItemStock> = emptyMap(),
    /**
     * Whether this order's responsible unit is a Spezialkommando.
     *
     * Resolved by id against the active org units, because `SquadronReferenceDto` carries no kind.
     * `null` while that read is still out — which is not the same as „no", and is why the Zusagen
     * tab appears once the answer arrives rather than being guessed from the unit's name.
     */
    val responsibleIsSpecialCommand: Boolean? = null,
    val statusPickerOpen: Boolean = false,
    /**
     * The status the member has picked but not yet applied.
     *
     * Design ch. 10 artboard 8 separates choosing from applying: the sheet carries a „Status
     * übernehmen" action rather than moving the order the instant a row is tapped. A status change
     * is visible to everyone on the order and two of the four cannot be taken back, so a mistap
     * must not be able to make one.
     */
    val statusChoice: JobOrderStatus? = null,
    /** Whether the terminal-status confirmation is up (artboard 9). */
    val statusConfirmOpen: Boolean = false,
    val saving: Boolean = false,
    val online: Boolean = true,
    val error: ApiError? = null,
) {
    /** The caller's own row on this order, or `null` when they are not on it. */
    val myAssignment: JobOrderAssignee?
        get() = me?.let { identity -> order?.assignees?.firstOrNull { it.userId == identity.userId } }

    /**
     * Whether a write may be offered at all.
     *
     * Not knowing who the caller is disables every one of them: an assignment addresses a member
     * by id, and there is no id to address.
     */
    val writable: Boolean
        get() = online && !saving && me != null

    /**
     * Which edit form this caller may open, or `null` when they may open none.
     *
     * The two gates are the server's own. A Logistician rewrites the whole order
     * (`canEditJobOrder`). A member of the **requesting** unit gets the narrower form
     * (`canEditJobOrderAsRequester`) — and only while **nothing at all** has been delivered: the
     * freeze is on the whole order, not per line, so one handover anywhere closes that path.
     *
     * Nothing else is inferred: the app is told which form it opened rather than the form working
     * out what it is allowed to write.
     */
    val editMode: OrderFormMode?
        get() =
            when {
                me?.logistician == true -> {
                    OrderFormMode.EDIT
                }

                order?.requestingOrgUnitId in myUnitIds && order?.krtUndelivered() == true -> {
                    OrderFormMode.EDIT_AS_REQUESTER
                }

                else -> {
                    null
                }
            }

    /**
     * Whether editing is offered at all on this order.
     *
     * An **item** order is editable too since 2026-08-29 (`PUT /orders/{id}/items`) — but only by a
     * Logistician: the requester's own item path (`/{id}/items/requested`) is not built. And the
     * server refuses the write once anything has been handed over, because the lines are what the
     * delivery was measured against, so the control is drawn with that reason rather than hidden.
     */
    val editableKind: Boolean
        get() {
            val current = order ?: return true
            return current.items.isEmpty() ||
                (me?.logistician == true && current.krtUndelivered())
        }

    /**
     * The order's item lines as a two-level tree.
     *
     * The server models a sub-assembly as a **real ordered line with a parent**, so the tree is the
     * order's own lines grouped by `parentItemId` rather than a recipe read. Design ch. 10 artboard
     * 12 limits it to two levels on purpose — deeper does not fit a phone — and the app follows
     * that: a line whose own child has children is drawn with the depth note rather than nested
     * further.
     */
    val itemTree: List<ItemBranch>
        get() {
            val lines = order?.items.orEmpty()
            val byParent = lines.filter { it.parentItemId != null }.groupBy { it.parentItemId }
            return lines
                .filter { it.parentItemId == null }
                .map { parent ->
                    val children = byParent[parent.id].orEmpty()
                    ItemBranch(
                        line = parent,
                        children = children,
                        deeper = children.any { child -> byParent[child.id].orEmpty().isNotEmpty() },
                    )
                }
        }

    /**
     * The tabs this order actually has.
     *
     * Zusagen exist only on a Spezialkommando order — the server refuses a claim on anything else
     * — so offering the tab elsewhere would offer a surface whose every action is a 400.
     */
    val tabs: List<OrderTab>
        get() =
            OrderTab.entries.filter { it != OrderTab.CLAIMS || responsibleIsSpecialCommand == true }

    /** Whether the status control belongs on this screen. */
    val statusChangeable: Boolean
        get() = me?.logistician == true

    /**
     * Whether the caller may book a production run.
     *
     * The endpoint's own gate is `LOGISTICIAN or OFFICER or ADMIN` plus edit scope on the Auftrag;
     * the flag the app holds covers the first and is a **hint, never a gate** — the control is
     * drawn either way and the server stays the authority (ADR-0011).
     *
     * Deliberately **not** folded together with [writable]. Being offline is not a missing grant,
     * and the refusal this flag drives names one — „Dafür brauchst du die Rolle Logistiker." would
     * be a false statement about a member who simply has no signal.
     */
    val productionAllowed: Boolean
        get() = me?.logistician == true

    /**
     * Whether the priority control belongs on this screen.
     *
     * The same grant as the status control, plus a queue position to move: an order the server
     * gave no priority is not in the queue, and „move it up" would be an instruction to put it
     * back there.
     */
    val priorityChangeable: Boolean
        get() = me?.logistician == true && order?.priority != null
}

/**
 * One top-level ordered item and the sub-assemblies under it.
 *
 * @property line the item that was ordered.
 * @property children its sub-assemblies — ordered lines of their own, with this one as parent.
 * @property deeper whether the recipe goes further than the two levels this screen draws, which the
 *   card says out loud rather than silently truncating.
 */
data class ItemBranch(
    val line: JobOrderItem,
    val children: List<JobOrderItem>,
    val deeper: Boolean,
)

/**
 * Everything one order's screen reads and writes through.
 *
 * A parameter object rather than five constructor arguments: the screen genuinely needs all of
 * them, and a constructor that wide is both hard to read at the call site and past what the
 * codebase's own static analysis allows.
 *
 * @property orders where the order comes from.
 * @property work the two writes that record work on it — the Übergabe and the Herstellung.
 * @property bookIn where produced stock may land.
 * @property claims the Zusagen on this order.
 * @property orgUnits the caller's own Staffeln, and every active unit — the first says who may
 *   pledge, the second says whether this order's responsible unit is a Spezialkommando.
 * @property identity who the caller is — which decides whose row on this order is theirs, and
 *   whether the status control is offered at all.
 * @property liveSync a peer's change on the same order, or `null` where none is wired.
 */
data class OrderDetailSources(
    val orders: JobOrderSource,
    val work: JobOrderWorkSource,
    val bookIn: BookInOptions,
    val claims: MaterialClaimSource,
    val orgUnits: OrgUnitSource,
    val identity: IdentitySource,
    val liveSync: LiveSyncSource? = null,
)

/**
 * Whether nothing at all has been handed over on this order.
 *
 * The requester's edit is frozen by the **whole order**, not per line: one handover anywhere —
 * material or item — closes the path, and the server answers 400 for the attempt.
 *
 * @receiver the order.
 * @return whether the requester may still edit it.
 */
private fun JobOrder.krtUndelivered(): Boolean = handovers.isEmpty() && itemHandovers.isEmpty()

/**
 * Drives one order.
 *
 * @property sources everything the screen reads and writes through
 * @property connectivity whether the device has a network
 * @property orderId which order to load
 */
class OrderDetailViewModel(
    sources: OrderDetailSources,
    connectivity: Connectivity,
    private val orderId: String,
) : ViewModel() {
    private val source = sources.orders
    private val identity = sources.identity
    private val orgUnits = sources.orgUnits
    private val liveSync = sources.liveSync

    private val mutableState = MutableStateFlow(OrderDetailState(orderId = orderId))

    /** What the screen draws. */
    val state: StateFlow<OrderDetailState> = mutableState.asStateFlow()

    /**
     * Recording that material changed hands — the write that finishes an Auftrag.
     *
     * Public, and called by the screen directly rather than through wrappers here: the same shape
     * the Einsatz's own holders use.
     */
    val handover =
        OrderHandover(
            source = sources.work,
            scope = viewModelScope,
            read = { mutableState.value.handover },
            write = { draft -> mutableState.value = mutableState.value.copy(handover = draft) },
            // The Auftrag is re-read rather than patched: a handover moves the line's open amount,
            // the order's status and possibly the whole order into „completed", and none of that is
            // in the answer.
            onRecorded = { reload(keepContent = true) },
        )

    /**
     * The Zusagen — which Staffel has signed up to deliver what.
     *
     * Their own holder and their own read: they live on `/orders/{id}/claims` rather than in the
     * order aggregate, and a pledge moves figures the server computes.
     */
    val claims =
        OrderClaims(
            source = sources.claims,
            orgUnits = sources.orgUnits,
            scope = viewModelScope,
            read = { mutableState.value.claims },
            write = { claims -> mutableState.value = mutableState.value.copy(claims = claims) },
        )

    /**
     * Handing finished items over — the write that finishes an item Auftrag.
     *
     * A second holder rather than a mode on [handover]: the endpoints, the units and the ceilings
     * differ, and folding them together would mean one form that is wrong for both.
     */
    val itemHandover =
        OrderItemHandover(
            source = sources.work,
            scope = viewModelScope,
            read = { mutableState.value.itemHandover },
            write = { draft -> mutableState.value = mutableState.value.copy(itemHandover = draft) },
            onRecorded = { reload(keepContent = true) },
        )

    /**
     * Booking a production run — the write that moves an item line's „hergestellt" figure.
     *
     * Public for the same reason [handover] is: the screen drives it directly rather than through
     * a wrapper per callback.
     */
    val production =
        OrderProduction(
            source = sources.work,
            options = sources.bookIn,
            myUserId = { mutableState.value.me?.userId ?: (identity.myUserId() as? ApiResult.Success)?.value },
            scope = viewModelScope,
            slot =
                ProductionSlot(
                    read = { mutableState.value.production },
                    write = { draft -> mutableState.value = mutableState.value.copy(production = draft) },
                ),
            // Re-read rather than patched: a run moves the line's manufactured count, the order's
            // derived material demand, the linked stock it consumed and possibly the whole order
            // into „completed" — none of which is in the answer.
            onBooked = { reload(keepContent = true) },
        )

    /** The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003). */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepContent = false) },
        )

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
        observeLiveSync(liveSync, setOf(LiveSyncTopic.order(orderId))) { sections ->
            // Both regions the app can move ride the one detail read, so either one re-reads the
            // order — in place, because the member may be part-way through typing a note.
            if (sections.any { it in WATCHED_SECTIONS }) {
                reload(keepContent = true)
            }
        }
    }

    /** Loads the order. */
    fun load() {
        readIdentity()
        readMyUnits()
        reload(keepContent = false)
        claims.load(orderId)
    }

    /**
     * Reads the item stock earmarked to this Auftrag.
     *
     * Only for an item order: a material order has no game-item stock, and asking would be a round
     * trip for an answer that is always empty.
     */
    private fun readItemStock() {
        if (mutableState.value.order?.items.isNullOrEmpty()) {
            return
        }
        viewModelScope.launch {
            val result = source.itemStock(orderId)
            if (result is ApiResult.Success) {
                mutableState.value =
                    mutableState.value.copy(itemStock = result.value.associateBy { it.gameItemId })
            }
        }
    }

    /**
     * Reads which units the caller belongs to, once.
     *
     * Only the ids are kept: the question is membership, and a name would answer it less exactly
     * while carrying more than the screen needs.
     */
    private fun readMyUnits() {
        if (mutableState.value.myUnitIds.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            val result = orgUnits.memberships()
            if (result is ApiResult.Success) {
                mutableState.value = mutableState.value.copy(myUnitIds = result.value.map { it.id }.toSet())
            }
        }
    }

    /**
     * Works out whether this order's responsible unit is a Spezialkommando.
     *
     * `SquadronReferenceDto` carries no kind, so the id is matched against the active org units —
     * the same list the order form's customer picker is built from. Guessing from the unit's name
     * („SK …") was the alternative and is a naming convention, not a fact.
     *
     * A failure leaves the answer `null`, which keeps the Zusagen tab hidden rather than offering a
     * surface whose every action would be a 400.
     */
    private fun resolveResponsibleKind() {
        if (mutableState.value.responsibleIsSpecialCommand != null) {
            return
        }
        viewModelScope.launch {
            val responsibleId = mutableState.value.order?.responsibleOrgUnitId ?: return@launch
            val result = orgUnits.activeAllKinds()
            if (result is ApiResult.Success) {
                mutableState.value =
                    mutableState.value.copy(
                        responsibleIsSpecialCommand =
                            result.value.firstOrNull { it.id == responsibleId }?.kind ==
                                OrgUnitKind.SPECIAL_COMMAND,
                    )
            }
        }
    }

    /**
     * Reads who the caller is, once.
     *
     * A failure is not fatal to the screen: the order still reads, and what is lost is the ability
     * to tell which assignee row is the caller's — so no write is offered rather than one offered
     * against a guess.
     */
    private fun readIdentity() {
        if (mutableState.value.me != null) {
            return
        }
        viewModelScope.launch {
            when (val result = identity.me()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(me = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the caller could not be identified: ${result.error}" }
                }
            }
        }
    }

    /** Puts the caller on the order, or takes them off it. */
    fun onToggleAssignment() {
        val current = mutableState.value
        val me = current.me ?: return
        if (!current.writable) {
            return
        }
        write { source.setAssigned(orderId, me.userId, assigned = current.myAssignment == null) }
    }

    /** Opens the note editor on the caller's own row. */
    fun onEditNote() {
        val current = mutableState.value
        if (current.writable) {
            mutableState.value = current.copy(noteDraft = current.myAssignment?.note.orEmpty(), error = null)
        }
    }

    /**
     * Updates what the editor holds.
     *
     * @param value what the member typed.
     */
    fun onNoteChanged(value: String) {
        mutableState.value = mutableState.value.copy(noteDraft = value.take(NOTE_LENGTH), error = null)
    }

    /** Closes the editor, discarding what was typed. */
    fun onDismissNote() {
        mutableState.value = mutableState.value.copy(noteDraft = null, error = null)
    }

    /**
     * Saves the note.
     *
     * An emptied editor clears the note rather than writing a blank one: those are the same
     * intention, and the server has a verb for it.
     */
    fun onSaveNote() {
        val current = mutableState.value
        val me = current.me
        val draft = current.noteDraft
        if (me == null || draft == null || !current.writable) {
            return
        }
        val note = draft.trim().takeIf { it.isNotEmpty() }
        write {
            source.setAssigneeNote(orderId, me.userId, note, current.myAssignment?.version)
        }
    }

    /**
     * Moves the order one place towards the front of the queue, or to the front outright.
     *
     * **Not a drag.** The web reorders by dragging a row, which needs a list on screen and a
     * pointer that can hold a row while the rest of it scrolls; the design has drawn no phone
     * equivalent (design round 8 §4 asks for one). What a Logistician actually wants is „this one
     * sooner", so that is what the control offers, expressed in the absolute position the endpoint
     * takes.
     *
     * A no-op when the order has no priority — a completed or rejected order is out of the queue,
     * and giving it a position would put it back in.
     *
     * @param toFront `true` for position 1, `false` for one place up.
     */
    fun onRaisePriority(toFront: Boolean) {
        val current = mutableState.value
        val priority = current.order?.priority ?: return
        if (!current.priorityChangeable || !current.writable || priority <= FIRST_PRIORITY) {
            return
        }
        val target = if (toFront) FIRST_PRIORITY else priority - 1
        write { source.setPriority(orderId, target) }
    }

    /**
     * Moves the order one place towards the back of the queue.
     *
     * There is no „to the back": the queue's length is a page count away, and a control that
     * guessed at it would put the order somewhere nobody asked for.
     */
    fun onLowerPriority() {
        val current = mutableState.value
        val priority = current.order?.priority ?: return
        if (!current.priorityChangeable || !current.writable) {
            return
        }
        write { source.setPriority(orderId, priority + 1) }
    }

    /** Shows the status picker. */
    fun onOpenStatusPicker() {
        val current = mutableState.value
        if (current.statusChangeable && current.writable) {
            mutableState.value = current.copy(statusPickerOpen = true, error = null)
        }
    }

    /**
     * Puts the refused note back into the editor.
     *
     * The member has seen what the server now holds and decided their own text should win. Saving
     * is a separate act; this only re-fills the field, so they can still edit or abandon it.
     */
    fun onReapplyRejectedNote() {
        val current = mutableState.value
        val rejected = current.rejectedNote ?: return
        mutableState.value = current.copy(noteDraft = rejected, rejectedNote = null)
    }

    /**
     * Switches to another page of the order.
     *
     * @param tab the page.
     */
    fun onTabSelected(tab: OrderTab) {
        mutableState.value = mutableState.value.copy(tab = tab)
    }

    /** Closes the status picker, discarding an unapplied choice. */
    fun onDismissStatusPicker() {
        mutableState.value =
            mutableState.value.copy(
                statusPickerOpen = false,
                statusChoice = null,
                statusConfirmOpen = false,
            )
    }

    /**
     * Marks a status as the one the member intends, without moving the order.
     *
     * @param status the picked status; the order's current status is inert in the sheet, so a tap
     *   on it is ignored rather than treated as a no-op write.
     */
    fun onStatusSelected(status: JobOrderStatus) {
        val current = mutableState.value
        if (status == current.order?.status) {
            return
        }
        mutableState.value = current.copy(statusChoice = status, error = null)
    }

    /**
     * Applies the picked status, asking first when it cannot be taken back.
     *
     * `COMPLETED` and `REJECTED` are terminal **in this app**: no screen here offers a way back out
     * of either, so from a member's side the change is one-way and the confirmation says so before
     * the change rather than after it (design ch. 10 artboard 9).
     */
    fun onApplyStatus() {
        val current = mutableState.value
        val choice = current.statusChoice ?: return
        if (choice in TERMINAL_STATUSES && !current.statusConfirmOpen) {
            mutableState.value = current.copy(statusConfirmOpen = true)
            return
        }
        mutableState.value = current.copy(statusConfirmOpen = false)
        onStatusChosen(choice)
    }

    /** Backs out of the terminal confirmation, keeping the choice on screen. */
    fun onDismissStatusConfirm() {
        mutableState.value = mutableState.value.copy(statusConfirmOpen = false)
    }

    /**
     * Moves the order.
     *
     * @param status where it should stand.
     */
    fun onStatusChosen(status: JobOrderStatus) {
        val current = mutableState.value
        if (!current.statusChangeable || !current.writable) {
            return
        }
        write { source.setStatus(orderId, status, current.order?.version) }
    }

    /**
     * Recovers from a lost optimistic-lock race on the note.
     *
     * Re-reads the order so the field shows what the server holds and the next save carries the
     * current version, and keeps the refused text beside it. A failed re-read leaves the refusal on
     * screen as an ordinary error — there is nothing better to offer, and pretending the reload
     * worked would hand the member a stale version to save against.
     */
    private suspend fun onConflict() {
        val refused = mutableState.value.noteDraft
        when (val reload = source.detail(orderId)) {
            is ApiResult.Success -> {
                mutableState.value =
                    mutableState.value.copy(
                        order = reload.value,
                        phase = OrderDetailPhase.Ready,
                        noteDraft =
                            reload.value.assignees
                                .firstOrNull { it.userId == mutableState.value.me?.userId }
                                ?.note
                                .orEmpty(),
                        rejectedNote = refused,
                        saving = false,
                        error = ApiError.OptimisticLock(),
                    )
            }

            // The reload failed too. Keep the typed text exactly where it is and say only what is
            // known — that the save lost the race. Resetting the field here would throw the
            // member's paragraph away at the moment the network is least able to give it back.
            is ApiResult.Failure -> {
                mutableState.value =
                    mutableState.value.copy(saving = false, error = ApiError.OptimisticLock())
            }
        }
    }

    /**
     * Runs one write and files what comes back.
     *
     * Every write answers with the whole order, so the screen is redrawn from the answer rather
     * than patched: the version and the assignee order are the server's to decide.
     *
     * @param request the call.
     */
    private fun write(request: suspend () -> ApiResult<JobOrder>) {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            order = result.value,
                            phase = OrderDetailPhase.Ready,
                            noteDraft = null,
                            statusPickerOpen = false,
                            statusChoice = null,
                            statusConfirmOpen = false,
                            saving = false,
                            error = null,
                        )
                    // Both write paths land here, and both move what another viewer of this order
                    // is looking at: the assignee list and the status in the header.
                    publishLiveSync(
                        liveSync,
                        LiveSyncTopic.order(orderId),
                        LiveSyncSections.ORDER_ASSIGNEES,
                        LiveSyncSections.ORDER_HEADER,
                    )
                }

                // The editor stays open with what was typed: a conflict or a refusal is not a
                // reason to make the member write their note again.
                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the order could not be changed: ${result.error}" }
                    if (result.error is ApiError.OptimisticLock) {
                        // A lost race is the one failure with a second step: the member has to see
                        // what the order says NOW before deciding whether their text still applies.
                        // Reloading also brings the version their next save needs, so re-applying
                        // and saving cannot lose the race a second time for the same reason.
                        onConflict()
                        return@launch
                    }
                    mutableState.value =
                        mutableState.value.copy(
                            saving = false,
                            // A refused write leaves the sheet closed but the choice discarded:
                            // re-opening it must show where the order actually is, not what the
                            // member wanted it to be.
                            statusPickerOpen = false,
                            statusChoice = null,
                            statusConfirmOpen = false,
                            error = result.error,
                        )
                }
            }
        }
    }

    /** Re-reads it, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
    }

    /**
     * Reads the order.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = OrderDetailPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.detail(orderId)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            order = result.value,
                            phase = OrderDetailPhase.Ready,
                            refreshing = false,
                        )
                    retry.onSuccess()
                    readItemStock()
                    // Only now: the responsible unit's id arrives with the order, so the kind
                    // lookup has nothing to match against before this point.
                    resolveResponsibleKind()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "order could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = OrderDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
                    retry.onFailure(result.error, hasContent = false)
                }
            }
        }
    }

    private companion object {
        /**
         * Sections of this order's room that cost a re-read.
         *
         * Both ride the one detail read, so they fold into a single refresh rather than two.
         */
        val WATCHED_SECTIONS =
            setOf(LiveSyncSections.ORDER_ASSIGNEES, LiveSyncSections.ORDER_HEADER)

        /** The front of the queue. The server counts from 1, not from 0. */
        const val FIRST_PRIORITY = 1

        /** What the server's note column takes. */
        const val NOTE_LENGTH = 500

        /** Log subsystem. */
        const val LOG_TAG = "orders"
    }
}

/**
 * The statuses this app offers no way back out of.
 *
 * Not a server rule — the backend would accept a move back — but an app one: no screen here has the
 * action, so for a member the change is one-way and the confirmation is honest in saying so.
 */
private val TERMINAL_STATUSES = setOf(JobOrderStatus.COMPLETED, JobOrderStatus.REJECTED)

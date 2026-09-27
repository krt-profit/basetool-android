/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.BulkRebookResult
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LagerFilter
import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.data.LagerTreeSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialEntryPage
import de.greluc.krt.profit.basetool.android.core.data.PersonalFilter
import de.greluc.krt.profit.basetool.android.core.data.StockGroup
import de.greluc.krt.profit.basetool.android.core.data.StolenFilter
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How far the tree has got. */
sealed interface InventoryPhase {
    /** The first page is on its way. */
    data object Loading : InventoryPhase

    /** A page arrived; it may be empty, which is a result. */
    data object Ready : InventoryPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : InventoryPhase
}

/**
 * How far one opened group has got.
 *
 * A group is its own little screen: it loads on the tap that opened it, and it can fail on its own
 * without the tree around it failing.
 */
sealed interface StackPhase {
    /** The stacks are on their way. */
    data object Loading : StackPhase

    /**
     * They arrived.
     *
     * @property stacks the holdings inside this group.
     */
    data class Ready(
        val stacks: List<InventoryStack>,
    ) : StackPhase

    /** They did not. The group stays open and says so rather than closing itself. */
    data object Failed : StackPhase
}

/**
 * How far the entries of one stack have got.
 *
 * The third level of the tree, added in phase 3: a member cannot book out what they cannot select,
 * and the two levels phase 2 read stop at the stack.
 */
sealed interface EntriesPhase {
    /** The entries are on their way. */
    data object Loading : EntriesPhase

    /**
     * They arrived.
     *
     * @property entries the individual bookings inside this stack.
     */
    data class Ready(
        val entries: List<InventoryEntry>,
    ) : EntriesPhase

    /** They did not. The stack stays open and says so. */
    data object Failed : EntriesPhase
}

/**
 * Everything the Lager tree draws.
 *
 * @property groups the material rows loaded so far
 * @property total how many materials the org unit holds in total
 * @property phase how far the first page has got
 * @property page the last page index that arrived
 * @property hasMore whether the server has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property opened the state of each opened group, keyed by material id
 * @property withStockOnly whether groups holding nothing are hidden
 * @property openedStacks the state of each opened stack, keyed by [stackKey]
 * @property released which rows are already offered on the Materialbörse, accumulated as stacks
 *   open
 * @property online whether a booking can be sent at all
 * @property allocation the open Zuordnung sheet, or `null`
 * @property selection the rows long-pressed into selection mode; empty means the mode is off
 * @property bulk the open bulk-move sheet, or `null`
 * @property checkout the open Sammel-Ausbuchen sheet, or `null`
 * @property scope which Lager the tree shows (design ch. 19, N1)
 * @property filter what the tree is narrowed to
 * @property filtersOpen whether the filter row is expanded (REQ-INV-037)
 * @property preloaded the stacks a grouped read already delivered, keyed by group key
 * @property locationOptions the places the location filter offers, taken from an unfiltered read
 * @property selectionPersonal for each selected row whose kind is known, whether it is personal
 * @property move the open „Mein Lager" move sheet, or `null`
 * @property personalNotice whether to say that a personal book-in landed in „Mein Lager"
 */
data class InventoryState(
    val groups: List<InventoryGroup> = emptyList(),
    val total: Long = 0,
    val phase: InventoryPhase = InventoryPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val opened: Map<String, StackPhase> = emptyMap(),
    val withStockOnly: Boolean = false,
    val openedStacks: Map<String, EntriesPhase> = emptyMap(),
    val released: Set<String> = emptySet(),
    val online: Boolean = true,
    val allocation: AllocationSheetState? = null,
    val selection: Set<String> = emptySet(),
    val bulk: BulkMoveState? = null,
    val checkout: BulkCheckoutState? = null,
    val scope: LagerScope = LagerScope.ORG,
    val filter: LagerFilter = LagerFilter(),
    val filtersOpen: Boolean = true,
    val preloaded: Map<String, List<InventoryStack>> = emptyMap(),
    val locationOptions: List<LocationOption> = emptyList(),
    val selectionPersonal: Map<String, Boolean> = emptyMap(),
    val move: StockMoveState? = null,
    val personalNotice: Boolean = false,
) {
    /** Whether the tree reads the grouped endpoints rather than the paged aggregate. */
    val grouped: Boolean
        get() = scope == LagerScope.MY || filter.active

    /**
     * How the selection splits into personal and shared rows, counting only rows whose kind is known.
     *
     * @return the personal count and the shared count.
     */
    fun selectionComposition(): Pair<Int, Int> {
        val kinds = selection.mapNotNull { selectionPersonal[it] }
        return kinds.count { it } to kinds.count { !it }
    }

    /**
     * Every entry the tree has loaded, across open and collapsed stacks.
     *
     * @return the entries.
     */
    fun loadedEntries(): List<InventoryEntry> =
        openedStacks.values.filterIsInstance<EntriesPhase.Ready>().flatMap { it.entries }

    /**
     * The selected entries among those the tree has loaded; ids whose entry is not loaded are left out.
     *
     * @return the selected entries.
     */
    fun selectedEntries(): List<InventoryEntry> =
        openedStacks
            .values
            .filterIsInstance<EntriesPhase.Ready>()
            .flatMap { it.entries }
            .filter { it.id in selection }

    /**
     * How many rows of one material group are selected, counted from the loaded entries.
     *
     * Collapsing a group keeps its entries, so it still counts them.
     *
     * @param materialId the group.
     * @return how many of its rows are selected, and how many it has; the latter `null` when it has
     *   never been opened.
     */
    fun selectionIn(materialId: String): Pair<Int, Int?> {
        val prefix = "$materialId|"
        val loaded =
            openedStacks
                .filterKeys { it.startsWith(prefix) }
                .values
                .filterIsInstance<EntriesPhase.Ready>()
                .flatMap { it.entries }
        if (loaded.isEmpty()) {
            return 0 to null
        }
        return loaded.count { it.id in selection } to loaded.size
    }

    /**
     * The groups the tree shows, with "Nur mit Bestand" applied on the device to the loaded page.
     *
     * The count below the list still states the server's total.
     */
    val visibleGroups: List<InventoryGroup>
        get() =
            if (withStockOnly) {
                groups.filter { (it.amount?.toDoubleOrNull() ?: 0.0) > 0.0 }
            } else {
                groups
            }
}

/**
 * The open bulk-move sheet.
 *
 * @property place where the selected rows are being sent, or `null` until one is picked.
 * @property places the org's locations.
 * @property morePlaces whether the catalogue holds places this page does not carry.
 * @property saving whether the move is running.
 * @property error the last refusal.
 * @property result what the server did, shown as the sheet's second step.
 */
data class BulkMoveState(
    val place: LocationOption? = null,
    val places: List<LocationOption> = emptyList(),
    val morePlaces: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val result: BulkRebookResult? = null,
)

/**
 * The Sammel-Ausbuchen sheet.
 *
 * `POST /inventory/bulk-checkout` carries only the ids, so the sheet asks once and collects no
 * reason, note or source plan; the rows are deleted whole with their earmarks.
 *
 * @property saving whether the call is in flight.
 * @property error what it was refused with, or `null`.
 * @property done whether it succeeded; the sheet's result step.
 * @property count how many rows it was asked to book out.
 */
data class BulkCheckoutState(
    val count: Int,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val done: Boolean = false,
)

/**
 * Drives the Lager tree.
 *
 * A group's stacks are fetched only when it is opened, and closing it keeps what was loaded.
 *
 * @property source where the Lager comes from
 * @property connectivity whether the device has a network, which decides whether the booking
 *   actions are offered
 * @property liveSync the live-sync bridge, or `null` in a test or a preview; a peer's change
 *   re-reads what is open
 * @property lager the grouped reads and the „Mein Lager" writes; without them the tree is the
 *   unfiltered Org-Lager only
 */
class InventoryViewModel(
    private val source: InventorySource,
    connectivity: Connectivity,
    private val liveSync: LiveSyncSource? = null,
    private val lager: LagerSources = LagerSources(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryState())

    /** What the screen draws. */
    val state: StateFlow<InventoryState> = mutableState.asStateFlow()

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

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.update { it.copy(online = online) }
            }
        }
        observeLiveSync(liveSync, setOf(LiveSyncTopic.INVENTORY)) { sections ->
            if (LiveSyncSections.INVENTORY_STOCK in sections) {
                if (loadedOnce) {
                    reReadOpenPath()
                }
            }
        }
    }

    private var loadedOnce = false

    /** Loads the first page, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepRows = false)
    }

    /** Re-reads the first page and drops every loaded group, since their contents may have moved. */
    fun onRefresh() {
        mutableState.update { it.copy(refreshing = true, opened = emptyMap()) }
        loadedOnce = true
        reload(keepRows = true)
    }

    /**
     * Shows or hides the groups that hold nothing.
     *
     * @param enabled whether to hide them.
     */
    fun onWithStockOnlyChanged(enabled: Boolean) {
        mutableState.update { it.copy(withStockOnly = enabled) }
    }

    /**
     * Opens or closes one group; a grouped read already carries its stacks, so it opens at once.
     *
     * @param groupKey the group's [InventoryGroup.key].
     */
    fun onToggleGroup(groupKey: String) {
        val current = mutableState.value
        if (groupKey in current.opened) {
            mutableState.update { it.copy(opened = it.opened - groupKey) }
            return
        }
        if (current.grouped) {
            val stacks = current.preloaded[groupKey].orEmpty()
            mutableState.update { it.copy(opened = it.opened + (groupKey to StackPhase.Ready(stacks))) }
            return
        }
        mutableState.update { it.copy(opened = it.opened + (groupKey to StackPhase.Loading)) }
        viewModelScope.launch { readStacks(groupKey) }
    }

    /**
     * Reads one group's stacks and files the answer under it.
     *
     * @param materialId the group.
     * @return the stacks that arrived, or an empty list when the read failed — the caller uses them
     *   to decide which of them still need their entries re-read.
     */
    private suspend fun readStacks(materialId: String): List<InventoryStack> {
        if (mutableState.value.grouped) {
            val stacks = mutableState.value.preloaded[materialId].orEmpty()
            mutableState.update { state ->
                if (materialId in state.opened) {
                    state.copy(opened = state.opened + (materialId to StackPhase.Ready(stacks)))
                } else {
                    state
                }
            }
            return stacks
        }
        val result = source.stacks(materialId)
        val phase =
            when (result) {
                is ApiResult.Success -> {
                    StackPhase.Ready(result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "stacks could not be read: ${result.error}" }
                    StackPhase.Failed
                }
            }
        mutableState.update { state ->
            if (materialId in state.opened) state.copy(opened = state.opened + (materialId to phase)) else state
        }
        return (phase as? StackPhase.Ready)?.stacks.orEmpty()
    }

    /**
     * Opens or closes one stack's entries, keyed by material, holder, place and quality.
     *
     * @param materialId the group's material.
     * @param stack the stack inside it.
     */
    fun onToggleStack(
        materialId: String,
        stack: InventoryStack,
    ) {
        val key = stackKey(materialId, stack)
        if (key in mutableState.value.openedStacks) {
            mutableState.update { it.copy(openedStacks = it.openedStacks - key) }
            return
        }
        mutableState.update { it.copy(openedStacks = it.openedStacks + (key to EntriesPhase.Loading)) }
        viewModelScope.launch { readEntries(materialId, stack) }
    }

    /**
     * Reads one stack's entries and files the answer under it.
     *
     * @param materialId the group the stack sits in.
     * @param stack the stack.
     */
    private suspend fun readEntries(
        materialId: String,
        stack: InventoryStack,
    ) {
        val key = stackKey(materialId, stack)
        val state = mutableState.value
        val group = state.groups.firstOrNull { it.key == materialId }
        val tree = lager.tree
        val read =
            if (tree != null && group != null) {
                tree.stackEntries(scope = state.scope, group = group, stack = stack)
            } else {
                source.entries(materialId = materialId, stack = stack)
            }
        val phase =
            when (val result = read) {
                is ApiResult.Success -> {
                    EntriesPhase.Ready(result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "entries could not be read: ${result.error}" }
                    EntriesPhase.Failed
                }
            }
        mutableState.update { latest ->
            if (key in latest.openedStacks) {
                latest.copy(openedStacks = latest.openedStacks + (key to phase))
            } else {
                latest
            }
        }
        if (phase is EntriesPhase.Ready) {
            val ids = phase.entries.map { it.id }
            viewModelScope.launch {
                val released = source.releasedEntryIds(ids)
                if (released.isNotEmpty()) {
                    mutableState.update { state -> state.copy(released = state.released + released) }
                }
            }
        }
    }

    /**
     * Re-reads the whole open path after a booking changed it, keeping every open group and stack open.
     */
    fun onBookingSaved() {
        publishLiveSync(liveSync, LiveSyncTopic.INVENTORY, LiveSyncSections.INVENTORY_STOCK)
        reReadOpenPath()
    }

    /**
     * Re-reads the open path in place, with no spinner, collapse or emptied list, for own and peer
     * changes alike.
     */
    private fun reReadOpenPath() {
        val openGroups = mutableState.value.opened.keys.toList()
        val openStacks = mutableState.value.openedStacks.keys.toSet()
        mutableState.update { it.copy(refreshing = true) }
        loadedOnce = true
        viewModelScope.launch {
            readFirstPage()
            openGroups.forEach { materialId ->
                readStacks(materialId)
                    .filter { stackKey(materialId, it) in openStacks }
                    .forEach { readEntries(materialId, it) }
            }
            val latest = mutableState.value
            val alive =
                latest.opened.entries
                    .flatMap { (materialId, phase) ->
                        (phase as? StackPhase.Ready)?.stacks.orEmpty().map { stackKey(materialId, it) }
                    }.toSet()
            mutableState.value =
                latest.copy(openedStacks = latest.openedStacks.filterKeys { it in alive })
        }
    }

    /** Appends the next page of groups. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is InventoryPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = source.groups(page = current.page + 1)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            groups = latest.groups + result.value.rows,
                            total = result.value.totalElements,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of the Lager failed: ${result.error}" }
                    mutableState.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    /**
     * Puts a row into the selection, or takes it out.
     *
     * The first long-press starts selection mode and an empty selection ends it.
     *
     * @param entryId the row.
     */
    fun onToggleSelected(entryId: String) {
        val personal = mutableState.value.loadedEntries().firstOrNull { it.id == entryId }?.personal
        mutableState.update { state ->
            val adding = entryId !in state.selection
            state.copy(
                selection = if (adding) state.selection + entryId else state.selection - entryId,
                selectionPersonal =
                    if (adding && personal != null) {
                        state.selectionPersonal + (entryId to personal)
                    } else {
                        state.selectionPersonal
                    },
            )
        }
    }

    /**
     * Selects, or when all are already in deselects, every loaded entry under one branch.
     *
     * Selection is always a set of entries; a branch whose entries are not loaded selects nothing.
     *
     * @param materialId the group, or the group a stack belongs to.
     * @param stack the stack to limit to, or `null` for the whole group.
     */
    fun onToggleBranch(
        materialId: String,
        stack: InventoryStack? = null,
    ) {
        val prefix = if (stack == null) "$materialId|" else stackKey(materialId, stack)
        val entries =
            mutableState.value.openedStacks
                .filterKeys { if (stack == null) it.startsWith(prefix) else it == prefix }
                .values
                .filterIsInstance<EntriesPhase.Ready>()
                .flatMap { it.entries }
        val ids = entries.map { it.id }.toSet()
        if (ids.isEmpty()) {
            val current = mutableState.value
            val closed =
                (stack?.let(::listOf) ?: current.preloaded[materialId].orEmpty())
                    .filter { stackKey(materialId, it) !in current.openedStacks }
            if (current.grouped && closed.isNotEmpty()) {
                mutableState.update { state ->
                    state.copy(
                        openedStacks =
                            state.openedStacks + closed.associate { stackKey(materialId, it) to EntriesPhase.Loading },
                    )
                }
                viewModelScope.launch {
                    closed.forEach { readEntries(materialId, it) }
                    onToggleBranch(materialId, stack)
                }
            }
            return
        }
        mutableState.update { state ->
            val removing = ids.all { it in state.selection }
            state.copy(
                selection = if (removing) state.selection - ids else state.selection + ids,
                selectionPersonal = state.selectionPersonal + entries.associate { it.id to it.personal },
            )
        }
    }

    /** Clears the selection, which leaves selection mode. */
    fun onSelectionCleared() {
        mutableState.update { it.copy(selection = emptySet(), selectionPersonal = emptyMap()) }
    }

    /**
     * Closes the bulk move's result step, ending the batch.
     *
     * Clears the selection and the opened stacks, whose cached entries still show the old place, and
     * re-reads the tree.
     */
    fun onBulkMoveFinished() {
        mutableState.update { it.copy(bulk = null, selection = emptySet(), openedStacks = emptyMap()) }
        reload(keepRows = true)
    }

    /**
     * The Sammel-Ausbuchen, as one object rather than four methods.
     *
     * Grouped because the view model already carries every function detekt allows, and because the
     * three steps are one interaction: open the sheet, send it, close it.
     */
    val checkoutActions: BulkCheckoutActions = BulkCheckoutActions()

    /**
     * The three steps of design ch. 09 artboard 20.
     *
     * An inner class so it can reach the same state the rest of the view model writes; it owns no
     * state of its own.
     */
    inner class BulkCheckoutActions {
        /**
         * Opens the sheet.
         *
         * The second action of the same selection bar the bulk rebooking uses — no second entry
         * point and no second selection pattern, which is what the artboard asks for.
         */
        fun request() {
            val current = mutableState.value
            if (current.selection.isEmpty()) {
                return
            }
            mutableState.value =
                current.copy(checkout = BulkCheckoutState(count = current.selection.size))
        }

        /**
         * Closes the Sammel-Ausbuchen sheet.
         *
         * Before it ran the selection stays; after the rows are gone selection mode ends.
         */
        fun close() {
            val current = mutableState.value
            val finished = current.checkout?.done == true
            mutableState.value =
                current.copy(
                    checkout = null,
                    selection = if (finished) emptySet() else current.selection,
                )
            if (finished) {
                reload(keepRows = false)
            }
        }

        /**
         * Books every selected row out in one all-or-nothing call.
         *
         * The sheet shows either the done step or the refusal, and a refusal keeps the selection.
         */
        fun confirm() {
            val current = mutableState.value
            val open = current.checkout ?: return
            val ids = current.selection.toList()
            if (ids.isEmpty() || open.saving) {
                return
            }
            mutableState.value = current.copy(checkout = open.copy(saving = true, error = null))
            viewModelScope.launch {
                when (val result = source.bulkCheckout(ids)) {
                    is ApiResult.Success -> {
                        mutableState.update { it.copy(checkout = open.copy(saving = false, done = true)) }
                        publishLiveSync(
                            liveSync,
                            LiveSyncTopic.INVENTORY,
                            LiveSyncSections.INVENTORY_STOCK,
                        )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the bulk checkout was refused: ${result.error}" }
                        mutableState.update {
                            it.copy(
                                checkout = open.copy(saving = false, error = result.error),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Opens the bulk-move sheet over the current selection. */
    fun onBulkMoveRequested() {
        if (mutableState.value.selection.isEmpty()) {
            return
        }
        mutableState.update { it.copy(bulk = BulkMoveState()) }
        viewModelScope.launch {
            val places = source.locations("")
            val open = mutableState.value.bulk ?: return@launch
            mutableState.update {
                it.copy(
                    bulk =
                        open.copy(
                            places = (places as? ApiResult.Success)?.value?.rows.orEmpty(),
                            morePlaces = (places as? ApiResult.Success)?.value?.more == true,
                        ),
                )
            }
        }
    }

    /** Closes it. */
    fun onBulkMoveDismissed() {
        mutableState.update { it.copy(bulk = null) }
    }

    /**
     * Records where the selection is being sent.
     *
     * @param place the chosen location.
     */
    fun onBulkMovePlace(place: LocationOption) {
        val open = mutableState.value.bulk ?: return
        mutableState.update { it.copy(bulk = open.copy(place = place, error = null)) }
    }

    /**
     * Moves every selected row to the chosen place in one all-or-nothing call.
     */
    fun onBulkMoveConfirmed() {
        val open = mutableState.value.bulk
        val place = open?.place
        val ids = mutableState.value.selection.toList()
        val ready = place != null && ids.isNotEmpty() && !open.saving
        if (!ready || !mutableState.value.online) {
            return
        }
        mutableState.update { it.copy(bulk = open.copy(saving = true, error = null)) }
        viewModelScope.launch {
            when (val result = source.bulkRebook(entryIds = ids, locationId = place.id)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(bulk = open.copy(saving = false, result = result.value)) }
                }

                is ApiResult.Failure -> {
                    mutableState.update { it.copy(bulk = open.copy(saving = false, error = result.error)) }
                }
            }
        }
    }

    /**
     * Opens the Zuordnung sheet on one entry.
     *
     * The targets are fetched when the sheet opens rather than with the list: they are two lookups
     * a member who never splits a stack should not pay for on every Lager load.
     *
     * @param entry the stock entry to split.
     */
    fun onAllocate(entry: InventoryEntry) {
        mutableState.update {
            it.copy(
                allocation =
                    AllocationSheetState(
                        entry = entry,
                        jobOrders = entry.jobOrderAllocations.toRows(),
                        missions = entry.missionAllocations.toRows(),
                    ),
            )
        }
        viewModelScope.launch {
            val orders = source.orderTargets()
            val missions = source.missionTargets()
            val open = mutableState.value.allocation ?: return@launch
            mutableState.update {
                it.copy(
                    allocation =
                        open.copy(
                            orderTargets = (orders as? ApiResult.Success)?.value.orEmpty(),
                            missionTargets = (missions as? ApiResult.Success)?.value.orEmpty(),
                        ),
                )
            }
        }
    }

    /** Closes it, discarding anything not saved. */
    fun onAllocationDismissed() {
        mutableState.update { it.copy(allocation = null) }
    }

    /**
     * Records a typed amount.
     *
     * @param kind which split.
     * @param targetId the row.
     * @param amount what was typed.
     */
    fun onAllocationAmount(
        kind: AllocationKind,
        targetId: String,
        amount: String,
    ) {
        editAllocation(kind, targetId) { it.copy(amount = amount.filter { c -> c.isDigit() || c == '.' }) }
    }

    /**
     * Steps a row up or down.
     *
     * @param kind which split.
     * @param targetId the row.
     * @param by `+1` or `-1`.
     */
    fun onAllocationStep(
        kind: AllocationKind,
        targetId: String,
        by: Int,
    ) {
        editAllocation(kind, targetId) { row ->
            val current = row.amount.trim().toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            val next = (current + by.toBigDecimal()).coerceAtLeast(java.math.BigDecimal.ZERO)
            row.copy(amount = next.stripTrailingZeros().toPlainString())
        }
    }

    /**
     * Adds a target to a split at zero, leaving the amount to the member.
     *
     * @param kind which split.
     * @param target what was picked.
     */
    fun onAllocationAdd(
        kind: AllocationKind,
        target: AllocationTarget,
    ) {
        val open = mutableState.value.allocation ?: return
        val row =
            AllocationRow(
                targetId = target.id,
                label = target.label,
                subtitle = target.subtitle,
                amount = "0",
                serverAmount = null,
            )
        val next =
            if (kind == AllocationKind.JOB_ORDER) {
                open.copy(jobOrders = open.jobOrders + row, picking = null)
            } else {
                open.copy(missions = open.missions + row, picking = null)
            }
        mutableState.update { it.copy(allocation = next) }
    }

    /**
     * Opens or closes an add picker.
     *
     * @param kind the split whose picker to open, or `null` to close.
     */
    fun onAllocationPick(kind: AllocationKind?) {
        val open = mutableState.value.allocation ?: return
        mutableState.update { it.copy(allocation = open.copy(picking = kind)) }
    }

    /**
     * Writes every changed row in sequence, each carrying the version the previous write returned.
     *
     * A failure stops the sequence; rows already written stay written and their count is reported.
     */
    fun onAllocationSave() {
        val open = mutableState.value.allocation ?: return
        if (!open.submittable) {
            return
        }
        mutableState.update { it.copy(allocation = open.copy(saving = true, error = null, partial = 0)) }
        viewModelScope.launch {
            var entry = open.entry
            var written = 0
            for ((kind, row) in open.pending) {
                val result =
                    source.setAllocation(
                        entryId = entry.id,
                        kind = kind,
                        targetId = row.targetId,
                        amount = row.amount,
                        existing = row.existsOnServer,
                        version = entry.version,
                    )
                when (result) {
                    is ApiResult.Success -> {
                        entry = result.value
                        written++
                    }

                    is ApiResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                allocation =
                                    open.copy(
                                        entry = entry,
                                        jobOrders = entry.jobOrderAllocations.toRows(),
                                        missions = entry.missionAllocations.toRows(),
                                        saving = false,
                                        error = result.error,
                                        partial = written,
                                    ),
                            )
                        }
                        return@launch
                    }
                }
            }
            mutableState.update { it.copy(allocation = null) }
            reload(keepRows = true)
        }
    }

    /**
     * Rewrites one row of the open sheet.
     *
     * @param kind which split.
     * @param targetId the row.
     * @param change what to make of it.
     */
    private fun editAllocation(
        kind: AllocationKind,
        targetId: String,
        change: (AllocationRow) -> AllocationRow,
    ) {
        val open = mutableState.value.allocation ?: return
        val edit = { rows: List<AllocationRow> ->
            rows.map { if (it.targetId == targetId) change(it) else it }
        }
        val next =
            if (kind == AllocationKind.JOB_ORDER) {
                open.copy(jobOrders = edit(open.jobOrders), error = null)
            } else {
                open.copy(missions = edit(open.missions), error = null)
            }
        mutableState.update { it.copy(allocation = next) }
    }

    /**
     * Loads page 0.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        if (!keepRows) {
            mutableState.update { it.copy(phase = InventoryPhase.Loading) }
        }
        viewModelScope.launch { readFirstPage() }
    }

    /** Reads page 0 into the state, whatever the reason for the read was. */
    private suspend fun readFirstPage() {
        val tree = lager.tree
        if (tree != null && mutableState.value.grouped) {
            readGrouped(tree)
            return
        }
        when (val result = source.groups(page = 0)) {
            is ApiResult.Success -> {
                mutableState.update {
                    it.copy(
                        groups = result.value.rows,
                        total = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        phase = InventoryPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                        preloaded = emptyMap(),
                    )
                }
                retry.onSuccess()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the Lager could not be read: ${result.error}" }
                mutableState.update {
                    it.copy(
                        phase = InventoryPhase.Failed(result.error),
                        loadingMore = false,
                        refreshing = false,
                    )
                }
                retry.onFailure(result.error, hasContent = false)
            }
        }
    }

    /**
     * Reads the grouped view of the current scope and filter, keeping the rows on screen until the
     * answer arrives.
     *
     * @param tree the grouped reads.
     */
    private suspend fun readGrouped(tree: LagerTreeSource) {
        val asked = mutableState.value
        when (val result = tree.grouped(asked.scope, asked.filter)) {
            is ApiResult.Success -> {
                val groups = result.value
                mutableState.update { state ->
                    if (state.scope != asked.scope || state.filter != asked.filter) {
                        state
                    } else {
                        state.copy(
                            groups = groups.map { it.group },
                            total = groups.size.toLong(),
                            page = 0,
                            hasMore = false,
                            phase = InventoryPhase.Ready,
                            loadingMore = false,
                            refreshing = false,
                            preloaded = groups.mapNotNull { g -> g.group.key?.let { it to g.stacks } }.toMap(),
                            opened =
                                state.opened.mapValues { (key, phase) ->
                                    groups.firstOrNull { it.group.key == key }?.let { StackPhase.Ready(it.stacks) }
                                        ?: phase
                                },
                            locationOptions =
                                if (asked.filter.active) state.locationOptions else groups.krtPlaces(),
                        )
                    }
                }
                retry.onSuccess()
                if (asked.filter.active && mutableState.value.locationOptions.isEmpty()) {
                    (tree.grouped(asked.scope, LagerFilter()) as? ApiResult.Success)?.let { unfiltered ->
                        mutableState.update { it.copy(locationOptions = unfiltered.value.krtPlaces()) }
                    }
                }
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the grouped Lager could not be read: ${result.error}" }
                mutableState.update {
                    it.copy(phase = InventoryPhase.Failed(result.error), loadingMore = false, refreshing = false)
                }
                retry.onFailure(result.error, hasContent = false)
            }
        }
    }

    /**
     * The Lager's scope segment, its filter row and „Alles wählen", as one object: the view model
     * already carries every function detekt allows.
     */
    val controls: LagerControls = LagerControls()

    /** Switches scope and narrows the tree; every change re-reads the first view from scratch. */
    inner class LagerControls {
        /**
         * Switches between the Org-Lager and „Mein Lager".
         *
         * The selection, the open branches and the place filter belong to one scope and are dropped;
         * the personal filter is kept for a later return.
         *
         * @param scope the scope to show.
         */
        fun scope(scope: LagerScope) {
            if (mutableState.value.scope == scope) {
                return
            }
            mutableState.update {
                it.copy(
                    scope = scope,
                    filter = it.filter.copy(locationIds = emptySet()),
                    selection = emptySet(),
                    selectionPersonal = emptyMap(),
                    opened = emptyMap(),
                    openedStacks = emptyMap(),
                    locationOptions = emptyList(),
                    groups = emptyList(),
                )
            }
            loadedOnce = true
            reload(keepRows = false)
        }

        /**
         * Sets the „Mein Lager" stock-kind filter; tapping the active value again resets it to all.
         *
         * @param value the value tapped.
         */
        fun personal(value: PersonalFilter) {
            val current = mutableState.value.filter.personal
            narrow { it.copy(personal = if (current == value) PersonalFilter.ALL else value) }
        }

        /**
         * Sets the „gestohlen" filter.
         *
         * @param value the value tapped.
         */
        fun stolen(value: StolenFilter) = narrow { it.copy(stolen = value) }

        /**
         * Sets the places to keep.
         *
         * @param ids the place ids; empty keeps every place.
         */
        fun locations(ids: Set<String>) = narrow { it.copy(locationIds = ids) }

        /** Clears every filter value. */
        fun reset() = narrow { LagerFilter() }

        /**
         * Notes that a personal book-in landed; from the Org-Lager, where the row will not appear,
         * the screen says where it went (design ch. 19, artboard 3).
         */
        fun personalBooked() {
            if (mutableState.value.scope == LagerScope.ORG) {
                mutableState.update { it.copy(personalNotice = true) }
            }
        }

        /** Clears that notice once it has been shown. */
        fun personalNoticeShown() {
            mutableState.update { it.copy(personalNotice = false) }
        }

        /** Opens or closes the filter row. */
        fun toggleRow() {
            mutableState.update { it.copy(filtersOpen = !it.filtersOpen) }
        }

        /**
         * Selects every one of the caller's rows under the current filter, across collapsed stacks,
         * as the server resolves it (REQ-INV-034); in the Org-Lager it selects what is loaded.
         */
        fun selectAll() {
            val tree = lager.tree
            val state = mutableState.value
            if (tree == null || state.scope != LagerScope.MY) {
                val loaded = state.loadedEntries()
                mutableState.update {
                    it.copy(
                        selection = it.selection + loaded.map { e -> e.id },
                        selectionPersonal = it.selectionPersonal + loaded.associate { e -> e.id to e.personal },
                    )
                }
                return
            }
            viewModelScope.launch {
                when (val result = tree.myEntryIds(state.filter)) {
                    is ApiResult.Success -> {
                        mutableState.update {
                            it.copy(selection = result.value.keys, selectionPersonal = result.value)
                        }
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the selection could not be resolved: ${result.error}" }
                    }
                }
            }
        }

        /**
         * Replaces the filter and re-reads, keeping the selection only where the filter still shows it.
         *
         * @param change what to make of the current filter.
         */
        private fun narrow(change: (LagerFilter) -> LagerFilter) {
            val before = mutableState.value.filter
            val after = change(before)
            if (after == before) {
                return
            }
            mutableState.update {
                it.copy(
                    filter = after,
                    selection = emptySet(),
                    selectionPersonal = emptyMap(),
                    opened = emptyMap(),
                    openedStacks = emptyMap(),
                )
            }
            loadedOnce = true
            reload(keepRows = false)
        }
    }

    /** The „Mein Lager" move sheets: rebooking, the org-unit change, their bulk forms. */
    val moves: StockMoveHolder =
        StockMoveHolder(
            state = mutableState,
            scope = viewModelScope,
            lager = lager,
            options = source,
            afterWrite = {
                publishLiveSync(liveSync, LiveSyncTopic.INVENTORY, LiveSyncSections.INVENTORY_STOCK)
                reReadOpenPath()
            },
        )

    private companion object {
        /** Log subsystem. A holder's name is member data and never reaches the log. */
        const val LOG_TAG = "inventory"
    }
}

/**
 * The distinct places a grouped read's stacks sit at, by name.
 *
 * @receiver the grouped read.
 * @return the places, sorted by name.
 */
private fun List<StockGroup>.krtPlaces(): List<LocationOption> =
    flatMap { it.stacks }
        .mapNotNull { stack -> stack.locationId?.let { LocationOption(it, stack.location.orEmpty()) } }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }

/**
 * What the Lager screen reads and writes beyond the Org-Lager aggregate.
 *
 * @property tree the grouped reads of both scopes, or `null` where only the aggregate is wanted.
 * @property moves the „Mein Lager" writes, or `null`.
 * @property identity who the caller is, for the unit pickers of „Mein Lager", or `null`.
 */
data class LagerSources(
    val tree: LagerTreeSource? = null,
    val moves: de.greluc.krt.profit.basetool.android.core.data.StockMoveSource? = null,
    val identity: de.greluc.krt.profit.basetool.android.core.data.IdentitySource? = null,
)

/**
 * The key one stack is opened under: its group and the whole stack identity the entry read is
 * narrowed by — holder, place, quality, owning unit, the personal flag and the stolen marker.
 *
 * @param materialId the group's key.
 * @param stack the stack.
 * @return the key.
 */
fun stackKey(
    materialId: String,
    stack: InventoryStack,
): String =
    listOf(
        materialId,
        stack.holderId,
        stack.locationId,
        stack.quality,
        stack.owningOrgUnitId,
        stack.personal,
        stack.stolen,
    ).joinToString("|")

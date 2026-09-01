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
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialEntryPage
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @property released which rows are already offered on the Materialbörse. Accumulated as stacks
 *   open, because that is when their ids become known.
 * @property online whether a booking can be sent at all
 * @property allocation the open Zuordnung sheet, or `null`
 * @property selection the rows long-pressed into selection mode; empty means the mode is off
 * @property bulk the open bulk-move sheet, or `null`
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
) {
    /**
     * The entries currently selected, as far as the tree has read them.
     *
     * Needed because the selection is a set of **ids** while the question a screen asks about it —
     * "does this include somebody else's row?" — is about the rows. An id whose entry is no longer
     * loaded is left out rather than guessed at; the server still refuses what it must.
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
     * How much of one material group is in the selection.
     *
     * Counted from the entries the tree has already read rather than from the group's own totals,
     * because those are amounts and this is a count of rows. A group whose stacks were never opened
     * has none loaded and reports `null` for the total — the chip then says „n gewählt" instead of
     * „n/m gewählt", which is exactly the distinction design ch. 09, artboard 5 draws between an
     * open and a collapsed group.
     *
     * Collapsing does not drop the entries, so a group closed after picking rows still counts them.
     *
     * @param materialId the group.
     * @return how many of its rows are selected, and how many it has — the latter `null` when it
     *   has never been opened.
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
     * The rows the tree actually shows.
     *
     * "Nur mit Bestand" is applied **on the device**, and that is deliberate rather than an
     * oversight: the endpoint has no such parameter, and the alternative would be to leave the
     * chip out. What makes it safe is that the chip hides rows from a page the member already has —
     * it never claims to have filtered the whole warehouse, and the count below the list keeps
     * stating the server's total.
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
 * @property result what the server did, once it has — the sheet's **second step** rather than a
 *   toast (design ch. 09, artboard 9). A skipped row needs its explaining sentence, and a toast is
 *   too fleeting to carry one.
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
 * The Sammel-Ausbuchen sheet (design ch. 09 artboard 20).
 *
 * Thinner than the artboard, and deliberately: `POST /inventory/bulk-checkout` carries **only the
 * ids**. There is no reason field on it („Verbraucht" / „Verworfen"), no note, and no per-row
 * source planner — the rows are deleted whole and their earmarks cascade away with them. The
 * sheet therefore says what will happen and asks once, instead of collecting three inputs the call
 * cannot send.
 *
 * @property saving whether the call is in flight.
 * @property error what it was refused with, or `null`.
 * @property done whether it succeeded — the sheet's result step.
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
 * **A group's stacks are fetched when it is opened, never before.** The tree's first level is one
 * request; fetching every group's holdings up front would pull the whole warehouse to draw a dozen
 * headings, most of which a member never opens.
 *
 * Closing a group **keeps** what was loaded, so re-opening it is instant. The Lager changes slowly
 * enough that a member re-opening a group within one visit expects what they just saw; pull-to-
 * refresh is how they ask for more.
 *
 * @property source where the Lager comes from
 * @property connectivity whether the device has a network, which is what decides whether the
 *   booking actions are offered at all
 * @property liveSync the live-sync bridge, or `null` in a test or a preview. The shared Lager is
 *   the surface where a peer's booking matters most — two members moving the same stock is the
 *   ordinary case, not the exception — so this screen re-reads what is open when the room speaks.
 */
class InventoryViewModel(
    private val source: InventorySource,
    connectivity: Connectivity,
    private val liveSync: LiveSyncSource? = null,
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
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepRows = false) },
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
        observeLiveSync(liveSync, setOf(LiveSyncTopic.INVENTORY)) { sections ->
            if (LiveSyncSections.INVENTORY_STOCK in sections) {
                // Only once the screen has something to refresh. Before that the member has not
                // loaded the Lager yet, and re-reading it in the background would spend a request
                // on a screen nobody is looking at.
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
        mutableState.value = mutableState.value.copy(refreshing = true, opened = emptyMap())
        loadedOnce = true
        reload(keepRows = true)
    }

    /**
     * Shows or hides the groups that hold nothing.
     *
     * @param enabled whether to hide them.
     */
    fun onWithStockOnlyChanged(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(withStockOnly = enabled)
    }

    fun onToggleGroup(materialId: String) {
        val opened = mutableState.value.opened
        if (materialId in opened) {
            mutableState.value = mutableState.value.copy(opened = opened - materialId)
            return
        }
        mutableState.value = mutableState.value.copy(opened = opened + (materialId to StackPhase.Loading))
        viewModelScope.launch { readStacks(materialId) }
    }

    /**
     * Reads one group's stacks and files the answer under it.
     *
     * @param materialId the group.
     * @return the stacks that arrived, or an empty list when the read failed — the caller uses them
     *   to decide which of them still need their entries re-read.
     */
    private suspend fun readStacks(materialId: String): List<InventoryStack> {
        val result = source.stacks(materialId)
        val phase =
            when (result) {
                is ApiResult.Success -> {
                    StackPhase.Ready(result.value)
                }

                is ApiResult.Failure -> {
                    // The group stays open and says so. Closing it would look like the tap did not
                    // register, and the member would try again.
                    KrtLog.w(LOG_TAG) { "stacks could not be read: ${result.error}" }
                    StackPhase.Failed
                }
            }
        // Only if the group is still open: a member who closed it while the read was in flight
        // must not have it spring open again.
        val current = mutableState.value
        if (materialId in current.opened) {
            mutableState.value = current.copy(opened = current.opened + (materialId to phase))
        }
        return (phase as? StackPhase.Ready)?.stacks.orEmpty()
    }

    /**
     * Opens or closes one stack's entries.
     *
     * Keyed by the four values that identify a stack — material, holder, place, quality — because
     * that is exactly what the entry read is narrowed by, and nothing shorter is unique.
     *
     * @param materialId the group's material.
     * @param stack the stack inside it.
     */
    fun onToggleStack(
        materialId: String,
        stack: InventoryStack,
    ) {
        val key = stackKey(materialId, stack)
        val current = mutableState.value
        if (key in current.openedStacks) {
            mutableState.value = current.copy(openedStacks = current.openedStacks - key)
            return
        }
        mutableState.value =
            current.copy(openedStacks = current.openedStacks + (key to EntriesPhase.Loading))
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
        val phase =
            when (val result = source.entries(materialId = materialId, stack = stack)) {
                is ApiResult.Success -> {
                    EntriesPhase.Ready(result.value)
                }

                is ApiResult.Failure -> {
                    // Same rule as one level up: the stack stays open and says so, rather than
                    // closing itself and looking like a tap that did not register.
                    KrtLog.w(LOG_TAG) { "entries could not be read: ${result.error}" }
                    EntriesPhase.Failed
                }
            }
        val latest = mutableState.value
        if (key in latest.openedStacks) {
            mutableState.value = latest.copy(openedStacks = latest.openedStacks + (key to phase))
        }
        if (phase is EntriesPhase.Ready) {
            // After the rows, not with them: the Lager read has nothing to do with the exchange,
            // and a member waiting for the second call to see the first would be paying for a
            // mark. A failure leaves the set as it was — no mark is the honest answer when nobody
            // asked, and a banner over the tree would be about something the tree does not do.
            val ids = phase.entries.map { it.id }
            viewModelScope.launch {
                val released = source.releasedEntryIds(ids)
                if (released.isNotEmpty()) {
                    mutableState.value =
                        mutableState.value.copy(released = mutableState.value.released + released)
                }
            }
        }
    }

    /**
     * Re-reads whatever is open, after a booking changed it.
     *
     * A booking changes what a stack holds and not only the entry that moved, so the whole open
     * path is re-read rather than patched: the group's total, the stack's total and the entry list
     * can all have changed at once.
     *
     * **What is open stays open.** Collapsing the tree back to its top level after every booking
     * would make the member re-open the group and the stack to see what their own booking did —
     * which is the one thing they are looking at.
     */
    fun onBookingSaved() {
        // The member's own booking is the one that has to reach everybody else. A peer's change
        // arrives through the room and must NOT be re-announced, or two clients would keep
        // bouncing one booking off each other.
        publishLiveSync(liveSync, LiveSyncTopic.INVENTORY, LiveSyncSections.INVENTORY_STOCK)
        reReadOpenPath()
    }

    /**
     * Re-reads the open path in place, whether the change was the member's own or a peer's.
     *
     * In place is the whole point: no spinner over the tree, no collapse, no emptied list. A
     * member who did not ask for anything must not watch their screen blank itself because
     * somebody on the other side of the organisation booked something out.
     */
    private fun reReadOpenPath() {
        val openGroups = mutableState.value.opened.keys.toList()
        val openStacks = mutableState.value.openedStacks.keys.toSet()
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        viewModelScope.launch {
            readFirstPage()
            openGroups.forEach { materialId ->
                readStacks(materialId)
                    .filter { stackKey(materialId, it) in openStacks }
                    .forEach { readEntries(materialId, it) }
            }
            // A stack that no longer exists — the booking emptied it — leaves its key behind, and
            // the row it belonged to is gone with it. Dropping the orphans keeps the map from
            // growing over a session.
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
                            groups = latest.groups + result.value.groups,
                            total = result.value.totalElements,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "next page of the Lager failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Puts a row into the selection, or takes it out.
     *
     * Long-press starts the mode (design ch. 02 §4) and an empty selection ends it: there is no
     * separate "leave selection mode", because a mode a member can be in with nothing selected is a
     * mode they have to notice they are in.
     *
     * @param entryId the row.
     */
    fun onToggleSelected(entryId: String) {
        val current = mutableState.value.selection
        val next = if (entryId in current) current - entryId else current + entryId
        mutableState.value = mutableState.value.copy(selection = next)
    }

    /**
     * Selects — or, when they are all in already, deselects — every entry under one branch.
     *
     * The design makes selection **always a set of entries** (design ch. 09, artboard 5: „Auswahl
     * ist IMMER Eintrags-Menge"). A group or stack row therefore carries no selection state of its
     * own; long-pressing one is shorthand for its leaves, which is what `bulk-rebook` takes — entry
     * ids plus one target, and the source may differ per entry.
     *
     * A branch whose entries are not loaded selects nothing rather than guessing at ids. The row is
     * still tappable to open it, so the member's next action reaches the same place.
     *
     * @param materialId the group, or the group a stack belongs to.
     * @param stack the stack to limit to, or `null` for the whole group.
     */
    fun onToggleBranch(
        materialId: String,
        stack: InventoryStack? = null,
    ) {
        val prefix = if (stack == null) "$materialId|" else stackKey(materialId, stack)
        val ids =
            mutableState.value.openedStacks
                .filterKeys { if (stack == null) it.startsWith(prefix) else it == prefix }
                .values
                .filterIsInstance<EntriesPhase.Ready>()
                .flatMap { phase -> phase.entries.map { it.id } }
                .toSet()
        if (ids.isEmpty()) {
            return
        }
        val current = mutableState.value.selection
        val next = if (ids.all { it in current }) current - ids else current + ids
        mutableState.value = mutableState.value.copy(selection = next)
    }

    /** Clears the selection, which leaves selection mode. */
    fun onSelectionCleared() {
        mutableState.value = mutableState.value.copy(selection = emptySet())
    }

    /**
     * Closes the result step, which is what ends the whole batch.
     *
     * Only here does the selection go and the tree re-read. The opened stacks are dropped as well,
     * not just the group list: their entries are cached per stack and the rows that just moved
     * still carry the OLD place, so leaving them would show a member the move they just made as not
     * having happened.
     */
    fun onBulkMoveFinished() {
        mutableState.value =
            mutableState.value.copy(bulk = null, selection = emptySet(), openedStacks = emptyMap())
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
         * Closes it.
         *
         * A sheet abandoned before it ran leaves the selection alone; one closed after the rows are
         * gone ends the mode, because there is nothing left to act on.
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
         * Books every selected row out.
         *
         * **All or nothing.** The endpoint refuses the whole call on a foreign row or an unknown
         * id, so there is no „ausgebucht / übersprungen" to report the way the bulk rebooking does
         * — the sheet shows either the done step or the refusal, and the selection survives a
         * refusal.
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
                        mutableState.value =
                            mutableState.value.copy(checkout = open.copy(saving = false, done = true))
                        // The rows are gone from the shared Lager; every other open Lager has to
                        // know.
                        publishLiveSync(
                            liveSync,
                            LiveSyncTopic.INVENTORY,
                            LiveSyncSections.INVENTORY_STOCK,
                        )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the bulk checkout was refused: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                checkout = open.copy(saving = false, error = result.error),
                            )
                    }
                }
            }
        }
    }

    /**
     * Books every selected row out.
     *
     * **All or nothing.** The endpoint refuses the whole call on a foreign row or an unknown id, so
     * there is no „ausgebucht / übersprungen" to report the way the bulk rebooking does — the
     * sheet shows either the done step or the refusal, and the selection survives a refusal.
     */
    fun onBulkCheckoutConfirmed() {
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
                    mutableState.value =
                        mutableState.value.copy(
                            checkout = open.copy(saving = false, done = true),
                        )
                    // The rows are gone from the shared Lager; every other open Lager has to know.
                    publishLiveSync(
                        liveSync,
                        LiveSyncTopic.INVENTORY,
                        LiveSyncSections.INVENTORY_STOCK,
                    )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the bulk checkout was refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            checkout = open.copy(saving = false, error = result.error),
                        )
                }
            }
        }
    }

/** Opens the bulk-move sheet over the current selection. */
    fun onBulkMoveRequested() {
        if (mutableState.value.selection.isEmpty()) {
            return
        }
        mutableState.value = mutableState.value.copy(bulk = BulkMoveState())
        viewModelScope.launch {
            val places = source.locations("")
            val open = mutableState.value.bulk ?: return@launch
            mutableState.value =
                mutableState.value.copy(
                    bulk =
                        open.copy(
                            places = (places as? ApiResult.Success)?.value?.rows.orEmpty(),
                            morePlaces = (places as? ApiResult.Success)?.value?.more == true,
                        ),
                )
        }
    }

    /** Closes it. */
    fun onBulkMoveDismissed() {
        mutableState.value = mutableState.value.copy(bulk = null)
    }

    /**
     * Records where the selection is being sent.
     *
     * @param place the chosen location.
     */
    fun onBulkMovePlace(place: LocationOption) {
        val open = mutableState.value.bulk ?: return
        mutableState.value = mutableState.value.copy(bulk = open.copy(place = place, error = null))
    }

    /**
     * Moves every selected row to the chosen place.
     *
     * One call, not one per row: the endpoint is all-or-nothing on its own terms, which is what a
     * member selecting twelve stacks expects — a half-applied move would leave them reading a list
     * they can no longer reason about.
     */
    fun onBulkMoveConfirmed() {
        val open = mutableState.value.bulk
        val place = open?.place
        val ids = mutableState.value.selection.toList()
        val ready = place != null && ids.isNotEmpty() && !open.saving
        if (!ready || !mutableState.value.online) {
            return
        }
        mutableState.value = mutableState.value.copy(bulk = open.copy(saving = true, error = null))
        viewModelScope.launch {
            when (val result = source.bulkRebook(entryIds = ids, locationId = place.id)) {
                is ApiResult.Success -> {
                    // The sheet stays open on its result step. Closing here and re-reading would
                    // drop the one number a member cannot reconstruct — how many rows were skipped
                    // because they already stood at the target, which is not a failure and needs
                    // its sentence (design ch. 09, artboard 9).
                    mutableState.value =
                        mutableState.value.copy(bulk = open.copy(saving = false, result = result.value))
                }

                is ApiResult.Failure -> {
                    // The selection is deliberately left standing: nothing was changed, and a
                    // member who has just picked twelve rows must not have to pick them again to
                    // retry (artboard 10).
                    mutableState.value =
                        mutableState.value.copy(bulk = open.copy(saving = false, error = result.error))
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
        mutableState.value =
            mutableState.value.copy(
                allocation =
                    AllocationSheetState(
                        entry = entry,
                        jobOrders = entry.jobOrderAllocations.toRows(),
                        missions = entry.missionAllocations.toRows(),
                    ),
            )
        viewModelScope.launch {
            val orders = source.orderTargets()
            val missions = source.missionTargets()
            val open = mutableState.value.allocation ?: return@launch
            mutableState.value =
                mutableState.value.copy(
                    allocation =
                        open.copy(
                            orderTargets = (orders as? ApiResult.Success)?.value.orEmpty(),
                            missionTargets = (missions as? ApiResult.Success)?.value.orEmpty(),
                        ),
                )
        }
    }

    /** Closes it, discarding anything not saved. */
    fun onAllocationDismissed() {
        mutableState.value = mutableState.value.copy(allocation = null)
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
     * Adds a target to a split, at zero.
     *
     * At zero rather than at the whole rest: the member picked what to promise to, not how much,
     * and a row that arrives pre-filled with everything left is one tap away from a split nobody
     * intended.
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
        mutableState.value = mutableState.value.copy(allocation = next)
    }

    /**
     * Opens or closes an add picker.
     *
     * @param kind the split whose picker to open, or `null` to close.
     */
    fun onAllocationPick(kind: AllocationKind?) {
        val open = mutableState.value.allocation ?: return
        mutableState.value = mutableState.value.copy(allocation = open.copy(picking = kind))
    }

    /**
     * Writes every changed row, in sequence.
     *
     * Sequential and not parallel, because each write returns a new optimistic-locking version that
     * the next one has to carry — firing them together would make all but the first collide with
     * their own predecessor.
     *
     * A failure stops the sequence rather than pushing on. What already landed stays landed, and
     * the count is reported: "three of five were written" is a fact the member needs, and pretending
     * the save was atomic would leave them re-entering changes that are already in.
     */
    fun onAllocationSave() {
        val open = mutableState.value.allocation ?: return
        if (!open.submittable) {
            return
        }
        mutableState.value = mutableState.value.copy(allocation = open.copy(saving = true, error = null, partial = 0))
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
                        mutableState.value =
                            mutableState.value.copy(
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
                        return@launch
                    }
                }
            }
            mutableState.value = mutableState.value.copy(allocation = null)
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
        mutableState.value = mutableState.value.copy(allocation = next)
    }

    /**
     * Loads page 0.
     *
     * @param keepRows whether the rows on screen survive until the answer arrives.
     */
    private fun reload(keepRows: Boolean) {
        if (!keepRows) {
            mutableState.value = mutableState.value.copy(phase = InventoryPhase.Loading)
        }
        viewModelScope.launch { readFirstPage() }
    }

    /** Reads page 0 into the state, whatever the reason for the read was. */
    private suspend fun readFirstPage() {
        when (val result = source.groups(page = 0)) {
            is ApiResult.Success -> {
                mutableState.value =
                    mutableState.value.copy(
                        groups = result.value.groups,
                        total = result.value.totalElements,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                        phase = InventoryPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                    )
                retry.onSuccess()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the Lager could not be read: ${result.error}" }
                mutableState.value =
                    mutableState.value.copy(
                        phase = InventoryPhase.Failed(result.error),
                        loadingMore = false,
                        refreshing = false,
                    )
                retry.onFailure(result.error, hasContent = false)
            }
        }
    }

    private companion object {
        /** Log subsystem. A holder's name is member data and never reaches the log. */
        const val LOG_TAG = "inventory"
    }
}

/**
 * The key one stack is opened under.
 *
 * Material, holder, place and quality together — the same four the entry read is narrowed by.
 * Anything shorter collides: one member can hold the same material at two places, and the same
 * place can hold two qualities of it.
 *
 * @param materialId the group's material.
 * @param stack the stack.
 * @return the key.
 */
fun stackKey(
    materialId: String,
    stack: InventoryStack,
): String = listOf(materialId, stack.holderId, stack.locationId, stack.quality).joinToString("|")

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationReduction
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.BookInDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutKind
import de.greluc.krt.profit.basetool.android.core.data.GameItemOption
import de.greluc.krt.profit.basetool.android.core.data.InventoryAllocation
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
import de.greluc.krt.profit.basetool.android.core.data.krtToDoubleOrNull
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Maps the form's earmark rows onto what the repository sends.
 *
 * @return one allocation per row that names an amount.
 */
private fun List<AllocationRow>.krtToAllocations(): List<InventoryAllocation> =
    map { InventoryAllocation(targetId = it.targetId, label = it.label, subtitle = it.subtitle, amount = it.amount) }

/** Which of the booking form's modes is showing (design ch. 09, Frame 2). */
enum class BookingMode {
    /** Material arrives. */
    IN,

    /** Material leaves — discarded, handed over or sold. */
    OUT,

    /** Nothing moves — only the entry's note changes. */
    NOTE,
}

/**
 * Which catalogue a book-in names.
 *
 * The server takes the two in **mutually exclusive** fields and treats them differently in three
 * further ways — a material row requires a grade and an item row forbids one, an item amount must
 * be a positive whole number, and an item always merges into a matching stack. So this is a kind of
 * row, not a filter on one list (REQ-INV-029).
 */
enum class BookingCatalogKind {
    /** Ore and refined goods — graded, and measured in SCU or in pieces. */
    MATERIAL,

    /** A finished product — ungraded, always counted in whole pieces. */
    ITEM,
}

/** What the server calls a material measured in standard cargo units rather than in pieces. */
private const val SCU_UNIT = "SCU"

/**
 * What the booking form holds.
 *
 * One state for every mode rather than one each: the member switches between them with a segment
 * and must not lose the amount they already typed, which is the field the moving modes share.
 *
 * @property mode which mode is showing.
 * @property kind whether a book-in names a material or a game item. Only asked of a book-in: an
 *   entry already knows what it holds, and every way out is the same for both.
 * @property entry the entry being booked out or rebooked; `null` in [BookingMode.IN].
 * @property amount how much, as typed.
 * @property material the material picked, for booking in.
 * @property materialQuery what is in the material search.
 * @property materials what that search returned.
 * @property gameItem the item picked, for booking in in item mode.
 * @property gameItemQuery what is in the item search.
 * @property gameItems what that search returned.
 * @property moreGameItems whether the catalogue holds items this page does not carry.
 * @property orderTargets the Aufträge a book-in may earmark part of the new row for.
 * @property missionTargets the same for Einsätze.
 * @property jobOrderSplit what the member has earmarked for which Auftrag, entered while booking
 *   in (Variante C). Sent with the booking, in one request, so the server checks the sum and every
 *   target in the same transaction that creates the row.
 * @property missionSplit the same for Einsätze. Never filled in item mode — the server refuses a
 *   mission earmark on an item row.
 * @property picking which "+ zuordnen" picker is open, or `null`.
 * @property moreMaterials whether the catalogue holds materials this page does not carry.
 * @property place the place picked.
 * @property placeQuery what is in the place search.
 * @property places what that search returned.
 * @property morePlaces whether the catalogue holds places this page does not carry.
 * @property quality the quality as typed, for booking in.
 * @property outKind what happens to the material on the way out.
 * @property member the member a transfer hands it to.
 * @property memberQuery what is in the member search.
 * @property members what that search returned.
 * @property moreMembers whether the roster holds members this page does not carry.
 * @property terminal the terminal a sale happens at.
 * @property terminals the terminals that buy this material.
 * @property jobOrderPlan what the member typed into each Auftrag earmark, keyed by target id.
 * @property missionPlan the same for the Einsatz earmarks — a separate map, because the two
 *   taggings are independent and a shared one would make the arithmetic wrong in both.
 * @property orgUnit the org-unit pool a transfer's moved row lands in.
 * @property orgUnits the pools the receiving member belongs to.
 * @property mergeStock whether the server may merge the moved amount into an identical
 *   entry at the target; only meaningful for an SCU material.
 * @property sellAmount what the sale fetched, as typed.
 * @property note the entry's note, as typed.
 * @property online whether a booking can be sent at all.
 * @property saving whether a booking is in flight.
 * @property error what the last attempt returned, or `null`.
 */
data class BookingState(
    val mode: BookingMode = BookingMode.IN,
    val kind: BookingCatalogKind = BookingCatalogKind.MATERIAL,
    val entry: InventoryEntry? = null,
    val amount: String = "",
    val material: MaterialOption? = null,
    val materialQuery: String = "",
    val materials: List<MaterialOption> = emptyList(),
    val moreMaterials: Boolean = false,
    val gameItem: GameItemOption? = null,
    val gameItemQuery: String = "",
    val gameItems: List<GameItemOption> = emptyList(),
    val moreGameItems: Boolean = false,
    val orderTargets: List<AllocationTarget> = emptyList(),
    val missionTargets: List<AllocationTarget> = emptyList(),
    val jobOrderSplit: List<AllocationRow> = emptyList(),
    val missionSplit: List<AllocationRow> = emptyList(),
    val picking: AllocationKind? = null,
    val place: LocationOption? = null,
    val placeQuery: String = "",
    val places: List<LocationOption> = emptyList(),
    val morePlaces: Boolean = false,
    val quality: String = "",
    val outKind: BookOutKind = BookOutKind.DISCARD,
    val member: MemberOption? = null,
    val memberQuery: String = "",
    val members: List<MemberOption> = emptyList(),
    val moreMembers: Boolean = false,
    val terminal: TerminalOption? = null,
    val terminals: List<TerminalOption> = emptyList(),
    val jobOrderPlan: Map<String, String> = emptyMap(),
    val missionPlan: Map<String, String> = emptyMap(),
    val orgUnit: OrgUnitOption? = null,
    val orgUnits: List<OrgUnitOption> = emptyList(),
    val mergeStock: Boolean = false,
    val sellAmount: String = "",
    val note: String = "",
    val online: Boolean = true,
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /**
     * Whether the form holds something the server will accept.
     *
     * Each mode has its own minimum, and the transfer and the sale each need the thing that makes
     * them what they are — a recipient and a terminal. Offering a save without them would send a
     * booking the server refuses, which reads to the member as the app being unreliable rather
     * than as a field they missed.
     */
    val submittable: Boolean
        get() =
            when (mode) {
                // A note moves nothing, so it needs no amount — and an emptied note is a
                // deliberate edit, not an incomplete form.
                BookingMode.NOTE -> {
                    entry != null && note != entry.note.orEmpty()
                }

                // Both kinds need an amount and a place; what they need beyond that is where the
                // server's two catalogues part company (REQ-INV-029). A material row needs a grade
                // — the web form marks the field required and the server refuses without it. An
                // item row needs none, refuses one, and needs its amount to be a whole number.
                BookingMode.IN -> {
                    // `!splitOverbooked` because the server refuses the WHOLE booking when a split
                    // promises more than the amount (R5) — not just the earmark. Dimming the CTA
                    // is what keeps a member from expecting a row that will never exist.
                    positiveAmount && place != null && !splitOverbooked &&
                        when (kind) {
                            BookingCatalogKind.MATERIAL -> material != null && qualityGiven
                            BookingCatalogKind.ITEM -> gameItem != null && wholeAmount
                        }
                }

                BookingMode.OUT -> {
                    positiveAmount && entry != null && outKindSatisfied && herkunftValid
                }
            }

    /** Whether the amount is a quantity and not zero. */
    private val positiveAmount: Boolean
        get() = amount.toDoubleOrNull()?.let { it > 0 } == true

    /**
     * Whether a grade has been entered, which a material row may not go without.
     *
     * Zero is a grade and passes; blank is the absence the server refuses.
     */
    private val qualityGiven: Boolean
        get() = quality.trim().toIntOrNull() != null

    /**
     * One of the two splits.
     *
     * @param kind which one.
     * @return its rows.
     */
    fun split(kind: AllocationKind): List<AllocationRow> =
        if (kind == AllocationKind.JOB_ORDER) jobOrderSplit else missionSplit

    /**
     * The rest of one split, which is what the member is watching while they type.
     *
     * @param kind which one.
     * @return what is not earmarked yet.
     */
    fun rest(kind: AllocationKind): BigDecimal =
        if (kind == AllocationKind.JOB_ORDER) jobOrderRest else missionRest

    /**
     * What one split's picker may still offer.
     *
     * A target already earmarked is left out — two rows for the same Auftrag would be two promises
     * the server merges into one — and so is one that has no use for what is being booked: the
     * server checks every earmark against its target's own requirement, so offering the rest would
     * be offering a rejection. Missions carry no requirement and are filtered only for duplicates.
     *
     * @param dimension which split.
     * @return the targets left to pick.
     */
    fun offerable(dimension: AllocationKind): List<AllocationTarget> {
        val taken = split(dimension).map { it.targetId }.toSet()
        val all = if (dimension == AllocationKind.JOB_ORDER) orderTargets else missionTargets
        val bookingItem = kind == BookingCatalogKind.ITEM
        return all
            .filterNot { it.id in taken }
            .filter {
                dimension == AllocationKind.MISSION || it.krtAccepts(catalogId, item = bookingItem)
            }
    }

    /**
     * The same state with one split replaced.
     *
     * @param kind which split.
     * @param rows what it now holds.
     * @return the updated state.
     */
    internal fun withSplit(
        kind: AllocationKind,
        rows: List<AllocationRow>,
    ): BookingState =
        if (kind == AllocationKind.JOB_ORDER) copy(jobOrderSplit = rows) else copy(missionSplit = rows)

    /** What is being booked in, for the earmark targets to be matched against. */
    val catalogId: String?
        get() = if (kind == BookingCatalogKind.ITEM) gameItem?.id else material?.id

    /** How much of the booked amount is not earmarked for an Auftrag yet. */
    val jobOrderRest: BigDecimal
        get() = bookedAmount - jobOrderSplit.krtSum()

    /** The same for the Einsatz split, reconciled apart because the server reconciles it apart. */
    val missionRest: BigDecimal
        get() = bookedAmount - missionSplit.krtSum()

    /**
     * Whether either split promises more than is being booked in.
     *
     * The server refuses the **whole booking** when a split exceeds the amount (R5), so this dims
     * the CTA rather than letting a member find out after they expected a row to exist.
     */
    val splitOverbooked: Boolean
        get() = jobOrderRest.signum() < 0 || missionRest.signum() < 0

    /** The amount being booked in, as a figure; zero while the field is unreadable. */
    private val bookedAmount: BigDecimal
        get() = amount.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO

    /**
     * Whether the amount is a whole number, which an item row may not go without.
     *
     * `ValidQuantityAmountValidator` refuses `amount % 1 != 0` for a game item outright: items are
     * counted, not measured, and half a medical station is not a quantity. Checked here so the
     * CTA does not invite the refusal.
     */
    private val wholeAmount: Boolean
        get() = amount.krtToDoubleOrNull()?.let { it % 1.0 == 0.0 } == true

    /** Whether the chosen way out has the field that makes it what it is. */
    private val outKindSatisfied: Boolean
        get() =
            when (outKind) {
                BookOutKind.DISCARD -> true
                BookOutKind.TRANSFER -> transferMoves
                BookOutKind.SELL -> terminal != null
            }

    /** How much is leaving the entry, as a number the plan can be measured against. */
    val deducted: Double
        get() = amount.toDoubleOrNull() ?: 0.0

    /** The Auftrag half of the deduct-from plan. */
    val jobOrderDimension: HerkunftDimension
        get() =
            herkunftDimension(
                tags = entry?.jobOrderAllocations.orEmpty(),
                rest = entry?.jobOrderRest,
                deducted = deducted,
                typed = jobOrderPlan,
            )

    /** The Einsatz half. */
    val missionDimension: HerkunftDimension
        get() =
            herkunftDimension(
                tags = entry?.missionAllocations.orEmpty(),
                rest = entry?.missionRest,
                deducted = deducted,
                typed = missionPlan,
            )

    /**
     * Whether the plan is one the server would accept.
     *
     * Only asked of a book-out that moves stock. A note changes no quantity, and booking in has no
     * earmarks to source from.
     */
    val herkunftValid: Boolean
        get() =
            mode != BookingMode.OUT ||
                (jobOrderDimension.valid && missionDimension.valid)

    /**
     * Whether the material is counted in SCU rather than as pieces.
     *
     * It decides one thing: whether the stock-merge opt-in is offered. The server merges a `PIECE`
     * transfer into an identical target stack either way, so a checkbox there would be a control
     * that changes nothing — which is worse than no checkbox, because the member cannot tell.
     */
    val materialIsScu: Boolean
        get() =
            kind == BookingCatalogKind.MATERIAL &&
                (entry?.unit ?: material?.unit)?.equals(SCU_UNIT, ignoreCase = true) == true

    /**
     * Whether the transfer would actually move the material.
     *
     * The server refuses a transfer that changes neither the holder nor the place, and it is right
     * to: nothing would happen. Picking the entry's own holder is the easy way into that refusal —
     * the picker offers them like anyone else — so the form has to know the rule too.
     */
    val transferMoves: Boolean
        get() =
            (member != null && member.id != entry?.holderId) ||
                (place != null && place.id != entry?.locationId)
}

/**
 * Drives the Lager's booking form.
 *
 * Its own view model rather than more state on the tree's: the form has four pickers and three
 * modes, and folding that into the screen that also pages a tree would make both harder to read
 * than either.
 *
 * @property source the Lager.
 * @property connectivity whether there is a network at all.
 */
class BookingViewModel(
    private val source: InventorySource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow<BookingState?>(null)

    /** What the form draws, or `null` when it is closed. */
    val state: StateFlow<BookingState?> = mutableState.asStateFlow()

    /**
     * The book-in's earmarks.
     *
     * Public so the screen wires its actions straight to it: the split is its own question with
     * its own state, and relaying five methods through the view model would add a hop and nothing
     * else.
     */
    val splits: BookingSplitHolder =
        BookingSplitHolder(source = source, scope = viewModelScope, update = ::update)

    private val onlineState = MutableStateFlow(true)

    /** Whether a booking can be sent at all. */
    val online: StateFlow<Boolean> = onlineState.asStateFlow()

    private var searchJob: Job? = null
    private var saved: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                onlineState.value = online
                update { it.copy(online = online) }
            }
        }
    }

    /**
     * Opens the form for booking material in.
     *
     * @param onSaved what to run once a booking lands, so the tree can re-read itself.
     */
    fun openBookIn(onSaved: () -> Unit) {
        saved = onSaved
        mutableState.value = BookingState(mode = BookingMode.IN, online = onlineState.value)
        splits.load()
    }

    /**
     * Opens the form on one entry.
     *
     * @param entry the entry.
     * @param mode which of the entry's modes the form opens on.
     * @param onSaved what to run once a booking lands.
     */
    fun openForEntry(
        entry: InventoryEntry,
        mode: BookingMode,
        onSaved: () -> Unit,
    ) {
        saved = onSaved
        mutableState.value =
            BookingState(
                mode = mode,
                entry = entry,
                note = entry.note.orEmpty(),
                online = onlineState.value,
            )
    }

    /**
     * Answers the conflict dialog's „Neu laden": closes the form and makes the tree re-read itself.
     *
     * It reuses the `onSaved` hook the caller already supplies for a landed booking, because the
     * tree needs exactly the same thing after a refused one: the row it is showing is stale either
     * way. Reloading does **not** retry the write -- see [ConflictModal] for why a retry against a
     * newer version would defeat the lock it just ran into.
     */
    fun onConflictReload() {
        saved?.invoke()
        onDismissed()
    }

    /** Closes the form, discarding what was typed. */
    fun onDismissed() {
        searchJob?.cancel()
        mutableState.value = null
    }

    /**
     * Switches mode.
     *
     * The amount survives: it is the one field every mode shares, and a member who typed 12 before
     * realising they meant "aus" should not type it again.
     *
     * @param mode the new mode.
     */
    fun onModeChanged(mode: BookingMode) {
        update { it.copy(mode = mode, error = null) }
    }

    /**
     * Updates the form.
     *
     * @param transform how it changes.
     */
    private fun update(transform: (BookingState) -> BookingState) {
        mutableState.value = mutableState.value?.let(transform)
    }

    /**
     * Sets the amount.
     *
     * @param value what the member typed, unparsed.
     */
    fun onAmountChanged(value: String) = update { it.copy(amount = value.filterQuantity(), error = null) }

    /**
     * Sets the quality.
     *
     * @param value what the member typed, unparsed.
     */
    fun onQualityChanged(value: String) =
        update { it.copy(quality = value.filter(Char::isDigit).take(QUALITY_DIGITS), error = null) }

    /**
     * Sets what a sale fetched.
     *
     * @param value what the member typed, unparsed.
     */
    fun onSellAmountChanged(value: String) = update { it.copy(sellAmount = value.filterQuantity(), error = null) }

    /**
     * Sets the entry's note.
     *
     * @param value what the member typed.
     */
    fun onNoteChanged(value: String) = update { it.copy(note = value.take(NOTE_LENGTH), error = null) }

    /**
     * Sets what happens to the material on the way out.
     *
     * @param kind discard, transfer or sell.
     */
    fun onOutKindChanged(kind: BookOutKind) {
        update { it.copy(outKind = kind, error = null) }
        when (kind) {
            BookOutKind.SELL -> loadTerminals()
            BookOutKind.TRANSFER -> loadOrgUnits()
            BookOutKind.DISCARD -> Unit
        }
    }

    /**
     * Searches materials.
     *
     * @param query what the member typed.
     */
    fun onMaterialQueryChanged(query: String) {
        update { it.copy(materialQuery = query) }
        searchPicker(query, source::materials) { rows, more ->
            copy(materials = rows, moreMaterials = more)
        }
    }

    /**
     * Picks a material.
     *
     * @param material the material.
     */
    fun onMaterialChosen(material: MaterialOption) =
        update { it.copy(material = material, materials = emptyList(), error = null) }

    /**
     * Types into the item search.
     *
     * @param query what the member typed.
     */
    fun onGameItemQueryChanged(query: String) {
        update { it.copy(gameItem = null, gameItemQuery = query) }
        searchPicker(query, source::gameItems) { rows, more ->
            copy(gameItems = rows, moreGameItems = more)
        }
    }

    /**
     * Picks an item.
     *
     * @param item what was picked.
     */
    fun onGameItemChosen(item: GameItemOption) =
        update { it.copy(gameItem = item, gameItemQuery = item.name, gameItems = emptyList(), error = null) }

    /**
     * Switches a book-in between the two catalogues.
     *
     * The other kind's pick is dropped rather than kept: the server takes exactly one of the two,
     * and a material still held in state while an item is showing is a booking nobody can see
     * being assembled. The grade goes with it — an item row refuses one.
     *
     * @param kind which catalogue the form now names.
     */
    fun onKindChanged(kind: BookingCatalogKind) =
        update {
            if (it.kind == kind) {
                it
            } else {
                it.copy(
                    kind = kind,
                    material = null,
                    materialQuery = "",
                    materials = emptyList(),
                    moreMaterials = false,
                    gameItem = null,
                    gameItemQuery = "",
                    gameItems = emptyList(),
                    moreGameItems = false,
                    quality = "",
                    error = null,
                )
            }
        }

    /**
     * Searches places.
     *
     * @param query what the member typed.
     */
    fun onPlaceQueryChanged(query: String) {
        update { it.copy(placeQuery = query) }
        searchPicker(query, source::locations) { rows, more ->
            copy(places = rows, morePlaces = more)
        }
    }

    /**
     * Picks a place.
     *
     * @param place the place.
     */
    fun onPlaceChosen(place: LocationOption) =
        update { it.copy(place = place, places = emptyList(), error = null) }

    /**
     * Searches members.
     *
     * @param query what the member typed.
     */
    fun onMemberQueryChanged(query: String) {
        update { it.copy(memberQuery = query) }
        searchPicker(query, source::members) { rows, more ->
            copy(members = rows, moreMembers = more)
        }
    }

    /**
     * Picks a member.
     *
     * @param member the recipient.
     */
    fun onMemberChosen(member: MemberOption) {
        update { it.copy(member = member, members = emptyList(), error = null) }
        // The pool picker offers the RECEIVING member's memberships, so a new recipient means a
        // new set of legal choices. Keeping the old ones would offer a unit the write refuses.
        loadOrgUnits()
    }

    /**
     * Picks the org-unit pool the moved stock lands in.
     *
     * @param orgUnit the pool.
     */
    fun onOrgUnitChosen(orgUnit: OrgUnitOption) = update { it.copy(orgUnit = orgUnit, error = null) }

    /**
     * Records how much of the deduction comes from one Auftrag earmark.
     *
     * @param targetId the Auftrag.
     * @param amount what the member typed.
     */
    fun onJobOrderShare(
        targetId: String,
        amount: String,
    ) = update { it.copy(jobOrderPlan = it.jobOrderPlan + (targetId to amount), error = null) }

    /**
     * Records how much comes from one Einsatz earmark.
     *
     * @param targetId the Einsatz.
     * @param amount what the member typed.
     */
    fun onMissionShare(
        targetId: String,
        amount: String,
    ) = update { it.copy(missionPlan = it.missionPlan + (targetId to amount), error = null) }

    /**
     * Sets whether the server may fold the moved amount into an identical entry at the target.
     *
     * @param merge whether to merge.
     */
    fun onMergeStockChanged(merge: Boolean) = update { it.copy(mergeStock = merge, error = null) }

    /**
     * Picks a terminal.
     *
     * @param terminal where the sale happens.
     */
    fun onTerminalChosen(terminal: TerminalOption) =
        update { it.copy(terminal = terminal, error = null) }

    /** Sends whatever the form holds. */
    fun onSave() {
        val current = mutableState.value ?: return
        if (!current.submittable || !onlineState.value) {
            return
        }
        update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = send(current)) {
                is ApiResult.Success -> {
                    mutableState.value = null
                    saved?.invoke()
                }

                // The form keeps every field: a conflict or a refusal is not a reason to make the
                // member type an amount and re-pick a material.
                is ApiResult.Failure -> {
                    update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Sends what the form holds as the booking its mode stands for.
     *
     * @param current the form.
     * @return what the server answered.
     */
    private suspend fun send(current: BookingState): ApiResult<Unit> =
        when (current.mode) {
            BookingMode.IN -> {
                val item = current.kind == BookingCatalogKind.ITEM
                source.bookIn(
                    BookInDraft(
                        // Exactly one of the two, which is the server's XOR and a DB check
                        // constraint besides: sending both refuses the booking, sending neither
                        // refuses it too.
                        materialId = current.material?.id.takeUnless { item },
                        gameItemId = current.gameItem?.id.takeIf { item },
                        locationId = current.place?.id.orEmpty(),
                        amount = current.amount,
                        quality = current.quality.toIntOrNull().takeUnless { item },
                        jobOrderAllocations = current.jobOrderSplit.krtToAllocations(),
                        // Never on an item row: the server refuses a mission earmark there
                        // (REQ-INV-031), and the form does not offer the split — this is the
                        // second lock, for a split entered before the kind was switched.
                        missionAllocations =
                            if (item) emptyList() else current.missionSplit.krtToAllocations(),
                    ),
                )
            }

            BookingMode.OUT -> {
                source.bookOut(
                    id = current.entry?.id.orEmpty(),
                    version = current.entry?.version,
                    draft =
                        BookOutDraft(
                            amount = current.amount,
                            kind = current.outKind,
                            targetUserId = current.member?.id,
                            targetLocationId = current.place?.id,
                            terminal = current.terminal?.name,
                            sellAmount = current.sellAmount.takeIf { it.isNotBlank() },
                            targetOwningOrgUnitId =
                                current.orgUnit?.id.takeIf { current.outKind == BookOutKind.TRANSFER },
                            mergeStock =
                                current.mergeStock &&
                                    current.outKind == BookOutKind.TRANSFER &&
                                    current.materialIsScu,
                            jobOrderReductions =
                                current.jobOrderDimension
                                    .reductions(current.deducted, current.jobOrderPlan)
                                    .map { (id, share) -> AllocationReduction(id, share) },
                            missionReductions =
                                current.missionDimension
                                    .reductions(current.deducted, current.missionPlan)
                                    .map { (id, share) -> AllocationReduction(id, share) },
                        ),
                )
            }

            BookingMode.NOTE -> {
                source.updateNote(
                    id = current.entry?.id.orEmpty(),
                    version = current.entry?.version,
                    note = current.note.takeIf { it.isNotBlank() },
                )
            }
        }

    /**
     * Reads the org units the transfer may hand stock to, and presets the pool.
     *
     * Asked for whoever will hold the stock — the picked recipient, or the entry's current holder
     * when the member picker was left alone, because leaving it alone means „keep the holder".
     *
     * The preset matters more than it looks: a submit that never touches this picker has to leave
     * the stock in the unit it is already in. Without it, every transfer that only changed the
     * place would silently re-pool the row and drop it out of sight of everyone scoped to the old
     * unit.
     */
    private fun loadOrgUnits() {
        val current = mutableState.value ?: return
        val receiver = current.member?.id ?: current.entry?.holderId ?: return
        viewModelScope.launch {
            val options = (source.orgUnitsFor(receiver) as? ApiResult.Success)?.value ?: return@launch
            update { state ->
                state.copy(
                    orgUnits = options,
                    // Keep an explicit choice that is still legal; otherwise fall back to the
                    // entry's own pool. A membershipless receiver leaves both null, and the
                    // server stamps the row ownerless — which is the correct outcome, not a gap.
                    orgUnit =
                        options.firstOrNull { it.id == state.orgUnit?.id }
                            ?: options.firstOrNull { it.id == state.entry?.owningOrgUnitId },
                )
            }
        }
    }

    /**
     * Runs a debounced picker search.
     *
     * One function for all four pickers rather than four near-copies: they differ only in which
     * catalogue they ask and which pair of state fields they write, and the four-fold repetition
     * was where the item picker's overflow flag would have been forgotten. A query below
     * [MIN_SEARCH] clears the list **and** the flag — leaving a stale „there are more" beside an
     * empty list is the one wrong thing this can say.
     *
     * The single [searchJob] is deliberate: the sheet shows one picker at a time, so a new search
     * cancelling the last one is what keeps a slow answer from landing after a faster one.
     *
     * @param T what the picker offers.
     * @param query what the member typed.
     * @param fetch the catalogue to ask.
     * @param write puts the rows and the overflow flag on the form.
     */
    private fun <T> searchPicker(
        query: String,
        fetch: suspend (String) -> ApiResult<PickerPage<T>>,
        write: BookingState.(List<T>, Boolean) -> BookingState,
    ) {
        searchJob?.cancel()
        if (query.trim().length < MIN_SEARCH) {
            update { it.write(emptyList(), false) }
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MILLIS)
                (fetch(query) as? ApiResult.Success)?.let { result ->
                    update { it.write(result.value.rows, result.value.more) }
                }
            }
    }

    /** Reads the terminals that buy the entry's material. */
    private fun loadTerminals() {
        val current = mutableState.value
        val materialId = current?.material?.id ?: current?.entry?.materialId
        if (materialId == null) {
            // Without a material id there is no terminal list to offer. The sheet says so rather
            // than showing an empty list that looks like a failed read.
            return
        }
        viewModelScope.launch {
            (source.terminals(materialId) as? ApiResult.Success)?.let { result ->
                update { it.copy(terminals = result.value) }
            }
        }
    }

    private companion object {
        /** How long typing settles before a search goes out. */
        const val DEBOUNCE_MILLIS = 300L

        /** Below this a search returns most of the catalogue. */
        const val MIN_SEARCH = 2

        /** Quality runs 0–1000, so four digits is the ceiling. */
        const val QUALITY_DIGITS = 4

        /** What the server's note column takes. */
        const val NOTE_LENGTH = 500
    }
}

/**
 * Keeps only what a quantity may contain.
 *
 * Digits and one separator: the Lager books in cSCU and µSCU, so a decimal point has to survive,
 * but a member cannot type two of them into one number.
 *
 * @return the cleaned text.
 */
private fun String.filterQuantity(): String {
    val cleaned = replace(',', '.').filter { it.isDigit() || it == '.' }
    val first = cleaned.indexOf('.')
    return if (first < 0) {
        cleaned
    } else {
        cleaned.substring(0, first + 1) + cleaned.substring(first + 1).filter(Char::isDigit)
    }
}

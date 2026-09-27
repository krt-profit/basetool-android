/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.BulkChangeResult
import de.greluc.krt.profit.basetool.android.core.data.BulkRebookResult
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.krtToDoubleOrNull
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which „Mein Lager" move a sheet performs. */
enum class StockMoveKind {
    /** „Umbuchen" between personal and the shared Lager (REQ-INV-007, REQ-INV-036). */
    REBOOK,

    /** „Einheit ändern" of personal rows (REQ-INV-052). */
    ORG_UNIT,

    /** Marking stock „gestohlen" or removing the marker (REQ-INV-053). */
    STOLEN,
}

/**
 * One open „Mein Lager" move sheet, single or for the selection.
 *
 * @property kind which move.
 * @property entry the row of a single move, or `null` for a selection.
 * @property ids the rows of a selection move; empty for a single one.
 * @property toPersonal whether a rebooking makes the stock personal; always `false` for [StockMoveKind.ORG_UNIT].
 * @property amount the amount of a single rebooking, as typed.
 * @property units the caller's memberships across all four kinds.
 * @property unitsLoaded whether that lookup has answered.
 * @property unitId the chosen unit; for [StockMoveKind.ORG_UNIT] `null` means „Keine Einheit".
 * @property initialUnitId the unit the row carries now, which the org-unit change must differ from.
 * @property merge the per-action merge opt-in.
 * @property scu whether every row is an `SCU` material, the only case the opt-in is offered for.
 * @property sharedInSelection how many shared rows a selection holds; an org-unit change refuses them.
 * @property saving whether the write is in flight.
 * @property error the last refusal, or `null`.
 * @property rebooked a selection rebooking's result step, or `null`.
 * @property changed a selection org-unit change's or marking's result step, or `null`.
 * @property stolen the marker a [StockMoveKind.STOLEN] sheet sets.
 */
data class StockMoveState(
    val kind: StockMoveKind,
    val entry: InventoryEntry? = null,
    val ids: List<String> = emptyList(),
    val toPersonal: Boolean = false,
    val amount: String = "",
    val units: List<OrgUnitOption> = emptyList(),
    val unitsLoaded: Boolean = false,
    val unitId: String? = null,
    val initialUnitId: String? = null,
    val merge: Boolean = false,
    val scu: Boolean = false,
    val sharedInSelection: Int = 0,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val rebooked: BulkRebookResult? = null,
    val changed: BulkChangeResult? = null,
    val stolen: Boolean = true,
) {
    /** Whether the sheet acts on a selection rather than one row. */
    val bulk: Boolean
        get() = entry == null

    /** Whether the sheet has reached its result step. */
    val finished: Boolean
        get() = rebooked != null || changed != null

    /** Whether an org-unit change over the selection is refused before its picker (artboard 7). */
    val refused: Boolean
        get() = kind == StockMoveKind.ORG_UNIT && bulk && sharedInSelection > 0

    /** Whether a rebooking into the shared Lager still lacks its pool. */
    private val poolMissing: Boolean
        get() = kind == StockMoveKind.REBOOK && !toPersonal && units.isNotEmpty() && unitId == null

    /** Whether the typed amount is positive and within the row. */
    private val amountValid: Boolean
        get() {
            val typed = amount.krtToDoubleOrNull() ?: return false
            val available = entry?.amount?.krtToDoubleOrNull() ?: return false
            return typed > 0.0 && typed <= available
        }

    /** Whether the write may be sent. */
    val submittable: Boolean
        get() =
            !saving && !finished &&
                when (kind) {
                    StockMoveKind.REBOOK -> {
                        !poolMissing && (bulk || amountValid)
                    }

                    StockMoveKind.ORG_UNIT -> {
                        if (bulk) {
                            sharedInSelection == 0 && unitsLoaded
                        } else {
                            unitId !=
                                initialUnitId
                        }
                    }

                    StockMoveKind.STOLEN -> {
                        bulk || amountValid
                    }
                }
}

/**
 * Drives the „Mein Lager" move sheets on the Lager's shared state.
 *
 * A class of its own rather than more functions on [InventoryViewModel], which already carries
 * every function detekt allows; it owns no state beyond the sheet it writes into [state].
 *
 * @property state the Lager's state, whose `move` and `selection` this holder writes.
 * @property scope where the writes run.
 * @property lager the writes and the caller's identity.
 * @property options the membership lookup behind the unit pickers.
 * @property afterWrite re-reads the tree and tells peers, once a write landed.
 */
class StockMoveHolder(
    private val state: MutableStateFlow<InventoryState>,
    private val scope: CoroutineScope,
    private val lager: LagerSources,
    private val options: BookInOptions,
    private val afterWrite: () -> Unit,
) {
    /**
     * Opens „Umbuchen" on one row; the direction is the opposite of the row's personal flag.
     *
     * @param entry the row.
     */
    fun openRebook(entry: InventoryEntry) {
        open(
            StockMoveState(
                kind = StockMoveKind.REBOOK,
                entry = entry,
                toPersonal = !entry.personal,
                amount = entry.amount.krtWhole(),
                initialUnitId = entry.owningOrgUnitId,
                scu = entry.unit.isScu(),
            ),
        )
    }

    /**
     * Opens „Markierte umbuchen" in one of the two personal modes.
     *
     * @param toPersonal `true` for „Als persönlich umbuchen".
     */
    fun openBulkRebook(toPersonal: Boolean) {
        val current = state.value
        if (current.selection.isEmpty()) {
            return
        }
        val chosen = current.selectedEntries()
        open(
            StockMoveState(
                kind = StockMoveKind.REBOOK,
                ids = current.selection.toList(),
                toPersonal = toPersonal,
                initialUnitId = chosen.map { it.owningOrgUnitId }.distinct().singleOrNull(),
                scu = chosen.size == current.selection.size && chosen.all { it.unit.isScu() },
            ),
        )
    }

    /**
     * Opens „Einheit ändern" on one personal row.
     *
     * @param entry the row.
     */
    fun openOrgUnit(entry: InventoryEntry) {
        open(
            StockMoveState(
                kind = StockMoveKind.ORG_UNIT,
                entry = entry,
                unitId = entry.owningOrgUnitId,
                initialUnitId = entry.owningOrgUnitId,
                scu = entry.unit.isScu(),
            ),
        )
    }

    /** Opens „Markierte: Einheit ändern"; a selection with a shared row is refused before the picker. */
    fun openBulkOrgUnit() {
        val current = state.value
        if (current.selection.isEmpty()) {
            return
        }
        val chosen = current.selectedEntries()
        val common = chosen.map { it.owningOrgUnitId }.distinct().singleOrNull()
        open(
            StockMoveState(
                kind = StockMoveKind.ORG_UNIT,
                ids = current.selection.toList(),
                unitId = common,
                initialUnitId = common,
                scu = chosen.size == current.selection.size && chosen.all { it.unit.isScu() },
                sharedInSelection = current.selectionComposition().second,
            ),
        )
    }

    /**
     * Opens „Als gestohlen markieren" or „Markierung entfernen" on one row (design ch. 19,
     * artboard 8); a part is split off as its own row.
     *
     * @param entry the row.
     * @param stolen the marker to set.
     */
    fun openStolen(
        entry: InventoryEntry,
        stolen: Boolean,
    ) {
        state.update {
            it.copy(
                move =
                    StockMoveState(
                        kind = StockMoveKind.STOLEN,
                        entry = entry,
                        stolen = stolen,
                        amount = entry.amount.krtWhole(),
                        unitsLoaded = true,
                    ),
            )
        }
    }

    /**
     * Marks or unmarks the whole selection.
     *
     * @param stolen the marker to set.
     */
    fun openBulkStolen(stolen: Boolean) {
        val current = state.value
        if (current.selection.isEmpty()) {
            return
        }
        state.update {
            it.copy(
                move =
                    StockMoveState(
                        kind = StockMoveKind.STOLEN,
                        ids = current.selection.toList(),
                        stolen = stolen,
                        unitsLoaded = true,
                    ),
            )
        }
    }

    /** Takes the shared rows out of the selection, the way out of the org-unit refusal (artboard 7). */
    fun dropShared() {
        state.update { current ->
            val shared = current.selection.filter { current.selectionPersonal[it] == false }.toSet()
            val kept = current.selection - shared
            current.copy(
                selection = kept,
                selectionPersonal = current.selectionPersonal - shared,
                move = current.move?.copy(ids = kept.toList(), sharedInSelection = 0),
            )
        }
    }

    /**
     * Sets the amount of a single rebooking.
     *
     * @param value what was typed.
     */
    fun amount(value: String) = edit { it.copy(amount = value.krtQuantity(), error = null) }

    /** Fills in the whole row („Alles"). */
    fun all() = edit { it.copy(amount = it.entry?.amount.krtWhole(), error = null) }

    /**
     * Picks a unit.
     *
     * @param id the unit, or `null` for „Keine Einheit".
     */
    fun unit(id: String?) = edit { it.copy(unitId = id, error = null) }

    /**
     * Sets the merge opt-in.
     *
     * @param merge whether to merge.
     */
    fun merge(merge: Boolean) = edit { it.copy(merge = merge, error = null) }

    /**
     * Closes the sheet. After a result step the selection has done its job and ends; before, it stays.
     */
    fun close() {
        state.update {
            val done = it.move?.finished == true
            it.copy(
                move = null,
                selection = if (done) emptySet() else it.selection,
                selectionPersonal = if (done) emptyMap() else it.selectionPersonal,
            )
        }
    }

    /** Sends the move. A single move closes on success; a selection move shows its result step. */
    fun confirm() {
        val open = state.value.move ?: return
        val moves = lager.moves
        if (!open.submittable || moves == null || !state.value.online) {
            return
        }
        edit { it.copy(saving = true, error = null) }
        scope.launch {
            val result: ApiResult<StockMoveState> =
                when (open.kind) {
                    StockMoveKind.REBOOK -> rebook(open, moves)
                    StockMoveKind.ORG_UNIT -> changeUnit(open, moves)
                    StockMoveKind.STOLEN -> mark(open, moves)
                }
            when (result) {
                is ApiResult.Success -> {
                    state.update {
                        it.copy(move = if (open.bulk) result.value.copy(saving = false) else null)
                    }
                    afterWrite()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the move was refused: ${result.error}" }
                    edit { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Sends a rebooking.
     *
     * @param open the sheet.
     * @param moves the writes.
     * @return the sheet with its result, or the refusal.
     */
    private suspend fun rebook(
        open: StockMoveState,
        moves: de.greluc.krt.profit.basetool.android.core.data.StockMoveSource,
    ): ApiResult<StockMoveState> {
        val entry = open.entry
        val merge = open.merge && open.scu
        return if (entry != null) {
            moves.rebookPersonal(entry, open.amount, open.unitId, merge).mapTo(open)
        } else {
            when (val result = moves.bulkRebookPersonal(open.ids, open.toPersonal, open.unitId, merge)) {
                is ApiResult.Success -> ApiResult.Success(open.copy(rebooked = result.value))
                is ApiResult.Failure -> result
            }
        }
    }

    /**
     * Sends an org-unit change.
     *
     * @param open the sheet.
     * @param moves the writes.
     * @return the sheet with its result, or the refusal.
     */
    private suspend fun changeUnit(
        open: StockMoveState,
        moves: de.greluc.krt.profit.basetool.android.core.data.StockMoveSource,
    ): ApiResult<StockMoveState> {
        val entry = open.entry
        val merge = open.merge && open.scu
        return if (entry != null) {
            moves.changeOrgUnit(entry, open.unitId, merge).mapTo(open)
        } else {
            when (val result = moves.bulkChangeOrgUnit(open.ids, open.unitId, merge)) {
                is ApiResult.Success -> ApiResult.Success(open.copy(changed = result.value))
                is ApiResult.Failure -> result
            }
        }
    }

    /**
     * Sends a marking.
     *
     * @param open the sheet.
     * @param moves the writes.
     * @return the sheet with its result, or the refusal.
     */
    private suspend fun mark(
        open: StockMoveState,
        moves: de.greluc.krt.profit.basetool.android.core.data.StockMoveSource,
    ): ApiResult<StockMoveState> {
        val entry = open.entry
        return if (entry != null) {
            moves.markStolen(entry, open.stolen, open.amount).mapTo(open)
        } else {
            when (val result = moves.bulkMarkStolen(open.ids, open.stolen)) {
                is ApiResult.Success -> ApiResult.Success(open.copy(changed = result.value))
                is ApiResult.Failure -> result
            }
        }
    }

    /**
     * Shows a sheet and loads the caller's memberships behind its picker.
     *
     * A rebooking into the shared Lager is preset to the row's unit, or the first membership when the
     * row has none, so it is never empty; the org-unit change keeps the row's own value.
     *
     * @param sheet the sheet to show.
     */
    private fun open(sheet: StockMoveState) {
        state.update { it.copy(move = sheet) }
        if (sheet.kind == StockMoveKind.REBOOK && sheet.toPersonal) {
            edit { it.copy(unitsLoaded = true) }
            return
        }
        scope.launch {
            val me = lager.identity?.myUserId()
            val units =
                ((me as? ApiResult.Success)?.value?.let { options.orgUnitsFor(it) } as? ApiResult.Success)
                    ?.value
                    .orEmpty()
            edit { current ->
                val preset =
                    when {
                        current.kind == StockMoveKind.ORG_UNIT -> current.unitId
                        units.any { it.id == current.initialUnitId } -> current.initialUnitId
                        else -> units.firstOrNull()?.id
                    }
                current.copy(units = units, unitsLoaded = true, unitId = preset)
            }
        }
    }

    /**
     * Rewrites the open sheet, if one is open.
     *
     * @param change what to make of it.
     */
    private fun edit(change: (StockMoveState) -> StockMoveState) {
        state.update { current -> current.move?.let { current.copy(move = change(it)) } ?: current }
    }

    private companion object {
        /** Log subsystem. */
        const val LOG_TAG = "inventory"
    }
}

/**
 * Carries a single write's success over to the sheet.
 *
 * @receiver the write's result.
 * @param sheet the sheet it was sent from.
 * @return the sheet on success, or the same failure.
 */
private fun ApiResult<Unit>.mapTo(sheet: StockMoveState): ApiResult<StockMoveState> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(sheet)
        is ApiResult.Failure -> this
    }

/**
 * An amount as a member types it: `80` rather than the wire's `80.0`.
 *
 * @receiver the amount as the server rendered it, or `null`.
 * @return the amount without trailing zeros, or an empty string.
 */
internal fun String?.krtWhole(): String =
    this?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: this.orEmpty()

/**
 * Whether a wire unit is standard cargo units, the only unit the merge opt-in applies to.
 *
 * @receiver the unit, or `null`.
 * @return whether it is `SCU`.
 */
internal fun String?.isScu(): Boolean = this?.equals("SCU", ignoreCase = true) == true

/**
 * Keeps only what a quantity may contain: digits and one decimal separator.
 *
 * @receiver what was typed.
 * @return the cleaned text.
 */
internal fun String.krtQuantity(): String {
    val cleaned = replace(',', '.').filter { it.isDigit() || it == '.' }
    val first = cleaned.indexOf('.')
    return if (first <
        0
    ) {
        cleaned
    } else {
        cleaned.substring(0, first + 1) + cleaned.substring(first + 1).filter(Char::isDigit)
    }
}

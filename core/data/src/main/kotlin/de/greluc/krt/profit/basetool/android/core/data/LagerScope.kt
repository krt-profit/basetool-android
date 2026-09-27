/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult

/** Which of the Lager's two scopes the tree shows (design ch. 19, N1). */
enum class LagerScope {
    /** The org unit's shared stock, every member's contributions. */
    ORG,

    /** The caller's own rows, personal and shared („Mein Lager"). */
    MY,
}

/** The „Mein Lager" stock-kind filter; the two narrowing values exclude each other (REQ-INV-046). */
enum class PersonalFilter {
    /** Personal and shared rows alike. */
    ALL,

    /** Only the caller's personal rows. */
    PERSONAL_ONLY,

    /** Only the caller's shared rows. */
    SHARED_ONLY,
}

/** The three-state „gestohlen" filter of both Lager scopes (REQ-INV-053). */
enum class StolenFilter {
    /** Stolen and regular stock alike. */
    ALL,

    /** Regular stock only. */
    WITHOUT,

    /** Stolen stock only. */
    ONLY,
}

/**
 * What the tree is narrowed to; every value is applied by the server, so the group totals always
 * match the visible stacks.
 *
 * @property personal the stock-kind filter; honoured in [LagerScope.MY] only
 * @property stolen the „gestohlen" filter
 * @property locationIds the places to keep; empty keeps every place (REQ-INV-040)
 */
data class LagerFilter(
    val personal: PersonalFilter = PersonalFilter.ALL,
    val stolen: StolenFilter = StolenFilter.ALL,
    val locationIds: Set<String> = emptySet(),
) {
    /** Whether any value narrows the tree. */
    val active: Boolean
        get() = personal != PersonalFilter.ALL || stolen != StolenFilter.ALL || locationIds.isNotEmpty()

    /** How many filter values are set, for the collapsed filter row's count (REQ-INV-037). */
    val count: Int
        get() =
            listOf(personal != PersonalFilter.ALL, stolen != StolenFilter.ALL, locationIds.isNotEmpty())
                .count { it }
}

/**
 * One material or game-item group together with its stacks, as a grouped read answers it.
 *
 * @property group the group row.
 * @property stacks its stacks, in server order.
 */
data class StockGroup(
    val group: InventoryGroup,
    val stacks: List<InventoryStack>,
)

/**
 * What a whole-row bulk write did; a row already in the requested state is skipped, not failed.
 *
 * @property changed how many rows the write changed.
 * @property skipped how many already were in that state.
 */
data class BulkChangeResult(
    val changed: Int,
    val skipped: Int,
)

/**
 * The grouped Lager reads, for both scopes and every filter.
 *
 * The unfiltered Org-Lager keeps its paged aggregate ([InventorySource.groups]); a filtered
 * Org-Lager and every „Mein Lager" view read the grouped endpoints, which answer groups and stacks
 * in one call.
 */
interface LagerTreeSource {
    /**
     * Reads the groups and their stacks.
     *
     * In [LagerScope.MY] both catalogues are read — materials first, then game items — because the
     * member's own stock is one list; the Org-Lager reads materials only, items have their own
     * screen.
     *
     * @param scope which Lager.
     * @param filter what to narrow to.
     * @return the groups, or the classified failure of either read.
     */
    suspend fun grouped(
        scope: LagerScope,
        filter: LagerFilter,
    ): ApiResult<List<StockGroup>>

    /**
     * Reads the entries of one stack, keyed by the stack's whole identity: catalogue entry, place,
     * quality, holder, owning unit, the personal flag and the stolen marker.
     *
     * @param scope which Lager the stack was read from.
     * @param group the group it sits in.
     * @param stack the stack.
     * @return the entries, or the classified failure.
     */
    suspend fun stackEntries(
        scope: LagerScope,
        group: InventoryGroup,
        stack: InventoryStack,
    ): ApiResult<List<InventoryEntry>>

    /**
     * Resolves „Alles wählen" on the server: every one of the caller's row ids under the filter,
     * across collapsed stacks, with whether each row is personal (REQ-INV-034).
     *
     * @param filter the filter the tree shows.
     * @return the ids mapped to their personal flag, or the classified failure.
     */
    suspend fun myEntryIds(filter: LagerFilter): ApiResult<Map<String, Boolean>>
}

/**
 * The „Mein Lager" writes that move stock between the personal and the shared dimension or change a
 * personal row's unit (REQ-INV-007, REQ-INV-036, REQ-INV-052).
 */
interface StockMoveSource {
    /**
     * Rebooks part or all of one of the caller's rows between personal and shared; the direction is
     * the opposite of the row's current flag.
     *
     * @param entry the row, read at its current version.
     * @param amount how much, as typed.
     * @param targetOrgUnitId the pool a row moving into the shared Lager lands in; ignored when
     *   the row becomes personal.
     * @param mergeStock whether an `SCU` row may merge into an identical stack at the target.
     * @return success, or the classified failure; a stale version is [de.greluc.krt.profit.basetool.android.core.network.ApiError.OptimisticLock].
     */
    suspend fun rebookPersonal(
        entry: InventoryEntry,
        amount: String,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<Unit>

    /**
     * Rebooks whole rows of the caller's selection into one personal state.
     *
     * @param entryIds the rows.
     * @param personal `true` for „Als persönlich umbuchen", `false` for „Ins gemeinsame Lager".
     * @param targetOrgUnitId the one pool every row moving into the shared Lager lands in.
     * @param mergeStock the per-action merge opt-in.
     * @return how many moved and how many already were there, or the classified failure; a row
     *   carrying an earmark refuses a personalize as a whole.
     */
    suspend fun bulkRebookPersonal(
        entryIds: List<String>,
        personal: Boolean,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<BulkRebookResult>

    /**
     * Sets the owning unit of one of the caller's personal rows.
     *
     * @param entry the row, read at its current version.
     * @param targetOrgUnitId a unit the caller belongs to, or `null` for „Keine Einheit".
     * @param mergeStock whether an `SCU` row may merge into the new unit's stack.
     * @return success, or the classified failure.
     */
    suspend fun changeOrgUnit(
        entry: InventoryEntry,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<Unit>

    /**
     * Sets the owning unit of several personal rows at once; a selection with a shared or a foreign
     * row is refused as a whole.
     *
     * @param entryIds the rows.
     * @param targetOrgUnitId the unit, or `null` for „Keine Einheit".
     * @param mergeStock the per-action merge opt-in.
     * @return how many changed and how many already had the unit, or the classified failure.
     */
    suspend fun bulkChangeOrgUnit(
        entryIds: List<String>,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<BulkChangeResult>
}

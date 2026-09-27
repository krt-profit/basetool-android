/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.BookInDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutDraft
import de.greluc.krt.profit.basetool.android.core.data.BulkChangeResult
import de.greluc.krt.profit.basetool.android.core.data.BulkRebookResult
import de.greluc.krt.profit.basetool.android.core.data.GameItemOption
import de.greluc.krt.profit.basetool.android.core.data.GameItemStock
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryPage
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LagerFilter
import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.data.LagerTreeSource
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
import de.greluc.krt.profit.basetool.android.core.data.StockGroup
import de.greluc.krt.profit.basetool.android.core.data.StockMoveSource
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The device has a network. */
internal object LagerOnline : Connectivity {
    override val online: Flow<Boolean> = flowOf(true)
}

/** The Org-Lager aggregate and the lookups, answering with nothing unless a test sets them. */
internal open class StubInventory : InventorySource {
    var units: List<OrgUnitOption> = emptyList()
    val unitLookups = mutableListOf<String>()

    override suspend fun groups(
        page: Int,
        pageSize: Int,
    ): ApiResult<InventoryPage> = ApiResult.Success(InventoryPage(emptyList(), 0, 1, 0))

    override suspend fun stacks(materialId: String): ApiResult<List<InventoryStack>> = ApiResult.Success(emptyList())

    override suspend fun entries(
        materialId: String,
        stack: InventoryStack,
    ): ApiResult<List<InventoryEntry>> = ApiResult.Success(emptyList())

    override suspend fun bookIn(draft: BookInDraft): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun bulkRebook(
        entryIds: List<String>,
        locationId: String,
    ): ApiResult<BulkRebookResult> = ApiResult.Success(BulkRebookResult(entryIds.size, 0))

    override suspend fun bulkCheckout(entryIds: List<String>): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun gameItemStock(): ApiResult<List<GameItemStock>> = ApiResult.Success(emptyList())

    override suspend fun bookOut(
        id: String,
        version: Long?,
        draft: BookOutDraft,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun updateNote(
        id: String,
        version: Long?,
        note: String?,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>> = ApiResult.Success(emptyList())

    override suspend fun materials(query: String): ApiResult<PickerPage<MaterialOption>> =
        ApiResult.Success(
            PickerPage(),
        )

    override suspend fun locations(query: String): ApiResult<PickerPage<LocationOption>> =
        ApiResult.Success(
            PickerPage(),
        )

    override suspend fun gameItems(query: String): ApiResult<PickerPage<GameItemOption>> =
        ApiResult.Success(
            PickerPage(),
        )

    override suspend fun releasedEntryIds(entryIds: List<String>): Set<String> = emptySet()

    override suspend fun members(query: String): ApiResult<PickerPage<MemberOption>> = ApiResult.Success(PickerPage())

    override suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>> {
        unitLookups.add(userId)
        return ApiResult.Success(units)
    }

    override suspend fun setAllocation(
        entryId: String,
        kind: AllocationKind,
        targetId: String,
        amount: String,
        existing: Boolean,
        version: Long?,
    ): ApiResult<InventoryEntry> = error("not used")

    override suspend fun orderTargets(): ApiResult<List<AllocationTarget>> = ApiResult.Success(emptyList())

    override suspend fun missionTargets(): ApiResult<List<AllocationTarget>> = ApiResult.Success(emptyList())
}

/**
 * The grouped reads, recording what was asked.
 *
 * @property groups what every grouped read answers.
 * @property entries what every stack read answers.
 */
internal class FakeTree(
    var groups: List<StockGroup> = emptyList(),
    var entries: List<InventoryEntry> = emptyList(),
) : LagerTreeSource {
    val groupedCalls = mutableListOf<Pair<LagerScope, LagerFilter>>()
    val stackCalls = mutableListOf<InventoryStack>()
    var ids: Map<String, Boolean> = emptyMap()

    override suspend fun grouped(
        scope: LagerScope,
        filter: LagerFilter,
    ): ApiResult<List<StockGroup>> {
        groupedCalls.add(scope to filter)
        return ApiResult.Success(groups)
    }

    override suspend fun stackEntries(
        scope: LagerScope,
        group: InventoryGroup,
        stack: InventoryStack,
    ): ApiResult<List<InventoryEntry>> {
        stackCalls.add(stack)
        return ApiResult.Success(entries)
    }

    override suspend fun myEntryIds(filter: LagerFilter): ApiResult<Map<String, Boolean>> = ApiResult.Success(ids)
}

/** The „Mein Lager" writes, recording each call. */
internal class FakeMoves : StockMoveSource {
    val rebooked = mutableListOf<Triple<String, String, String?>>()
    val bulkRebooked = mutableListOf<Triple<List<String>, Boolean, String?>>()
    val unitChanges = mutableListOf<Pair<String, String?>>()
    val bulkUnitChanges = mutableListOf<Pair<List<String>, String?>>()
    var answer: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun rebookPersonal(
        entry: InventoryEntry,
        amount: String,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<Unit> {
        rebooked.add(Triple(entry.id, amount, targetOrgUnitId))
        return answer
    }

    override suspend fun bulkRebookPersonal(
        entryIds: List<String>,
        personal: Boolean,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<BulkRebookResult> {
        bulkRebooked.add(Triple(entryIds, personal, targetOrgUnitId))
        return ApiResult.Success(BulkRebookResult(entryIds.size - 1, 1))
    }

    override suspend fun changeOrgUnit(
        entry: InventoryEntry,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<Unit> {
        unitChanges.add(entry.id to targetOrgUnitId)
        return answer
    }

    override suspend fun bulkChangeOrgUnit(
        entryIds: List<String>,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<BulkChangeResult> {
        bulkUnitChanges.add(entryIds to targetOrgUnitId)
        return ApiResult.Success(BulkChangeResult(entryIds.size, 0))
    }

    val marked = mutableListOf<Triple<String, Boolean, String>>()
    val bulkMarked = mutableListOf<Pair<List<String>, Boolean>>()

    override suspend fun markStolen(
        entry: InventoryEntry,
        stolen: Boolean,
        amount: String,
    ): ApiResult<Unit> {
        marked.add(Triple(entry.id, stolen, amount))
        return answer
    }

    override suspend fun bulkMarkStolen(
        entryIds: List<String>,
        stolen: Boolean,
    ): ApiResult<BulkChangeResult> {
        bulkMarked.add(entryIds to stolen)
        return ApiResult.Success(BulkChangeResult(entryIds.size, 0))
    }
}

/** A caller with a fixed id. */
internal object FakeIdentity : IdentitySource {
    override suspend fun myUserId(): ApiResult<String> = ApiResult.Success("u1")

    override suspend fun me(): ApiResult<Identity> = ApiResult.Success(Identity(userId = "u1", logistician = false))

    override fun forget() = Unit
}

/** The four units the member belongs to, one of each kind. */
internal val FOUR_UNITS =
    listOf(
        OrgUnitOption("iri", "IRIDIUM", kind = OrgUnitKind.SQUADRON),
        OrgUnitOption("pro", "Profit", kind = OrgUnitKind.BEREICH),
        OrgUnitOption("van", "VANGUARD", kind = OrgUnitKind.SPECIAL_COMMAND),
        OrgUnitOption("nord", "Nord", kind = OrgUnitKind.ORGANISATIONSLEITUNG),
    )

/**
 * One „Mein Lager" row.
 *
 * @param id the row.
 * @param personal whether it is personal.
 * @param unit its owning unit, or `null`.
 * @return the row.
 */
internal fun lagerEntry(
    id: String,
    personal: Boolean,
    unit: String? = null,
) = InventoryEntry(
    id = id,
    materialName = "Quantainium",
    materialId = "m1",
    unit = "SCU",
    locationName = "ARC-L1",
    locationId = "l1",
    holder = null,
    holderId = "u1",
    amount = "80.0",
    quality = "874",
    personal = personal,
    note = null,
    version = 3L,
    owningOrgUnitId = unit,
)

/**
 * One stack of Quantainium at ARC-L1.
 *
 * @param personal whether it is personal.
 * @param stolen whether it is marked stolen.
 * @param unit its owning unit, or `null`.
 * @return the stack.
 */
internal fun lagerStack(
    personal: Boolean,
    stolen: Boolean = false,
    unit: String? = null,
) = InventoryStack(
    holder = null,
    location = "ARC-L1",
    personal = personal,
    amount = "80",
    quality = "874",
    entryCount = 1,
    holderId = "u1",
    locationId = "l1",
    owningOrgUnitId = unit,
    owningOrgUnitName = unit?.uppercase(),
    stolen = stolen,
)

/** Quantainium with a shared and a personal stack. */
internal val QUANTAINIUM =
    StockGroup(
        group = InventoryGroup("m1", "Quantainium", "SCU", "522", "874", "874"),
        stacks = listOf(lagerStack(personal = false, unit = "pro"), lagerStack(personal = true)),
    )

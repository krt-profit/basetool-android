/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AggregatedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.AllocationReductionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkCheckoutRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkRebookRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkRebookResultDto
import de.greluc.krt.profit.basetool.android.core.contract.model.GroupedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryAllocationInput
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryAllocationWriteDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemBookOutDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemNoteUpdateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryStackDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.LocationReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialSellingTerminalDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitMembershipOptionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseAggregatedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseGameItemReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseInventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseLocationReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseUserDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UserDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient

/**
 * One material group of the Lager tree — the row a member sees before opening anything.
 *
 * @property materialId the material's id, which is how its stacks are asked for
 * @property name the material's name
 * @property unit the quantity unit the server names, e.g. `SCU`; `null` when it sends none
 * @property amount how much of it the org unit holds, as the server rendered it
 * @property quality the average quality, or `null`
 * @property maxQuality the best quality in the group, or `null`
 */
data class InventoryGroup(
    val materialId: String?,
    val name: String,
    val unit: String?,
    val amount: String?,
    val quality: String?,
    val maxQuality: String?,
)

/**
 * One stack inside a group: a member's holding at one place and quality.
 *
 * @property holder whose stack it is, or `null` for one the server did not attribute
 * @property location where it is, or `null`
 * @property personal whether it is the holder's private stock rather than the shared Lager
 * @property amount how much, as the server rendered it
 * @property quality the quality key the server groups by (not the average), used to look up the
 *   entries
 * @property entryCount how many individual entries it sums up
 * @property holderId whose stack it is, by id; part of the entry read's key
 * @property locationId where it is, by id
 * @property owningOrgUnitId which org-unit pool it belongs to, or `null` for an unpooled holding;
 *   part of the entry read's key
 */
data class InventoryStack(
    val holder: String?,
    val location: String?,
    val personal: Boolean,
    val amount: String?,
    val quality: String?,
    val entryCount: Int,
    val holderId: String? = null,
    val locationId: String? = null,
    val owningOrgUnitId: String? = null,
)

/**
 * One page of a material's entries, flat across every holder and place, as the tablet's detail
 * pane shows it.
 *
 * [Page.rows] holds the rows on this page.
 */
typealias MaterialEntryPage = Page<InventoryEntry>

/**
 * One entry inside a stack: the thing a booking actually moves.
 *
 * @property id the entry's id
 * @property materialName what it is
 * @property unit the unit the amount is expressed in, or `null`
 * @property locationName where it is, or `null`
 * @property locationId the same, by id
 * @property materialId which material it holds, or `null`; used to look up the terminals a sale can
 *   pick from
 * @property holder whose it is, or `null`
 * @property holderId whose it is, by id; compared against the transfer picker's choice
 * @property amount how much, as the server rendered it
 * @property quality the quality, or `null`
 * @property note the member's own note, or `null`
 * @property version optimistic-locking version
 * @property canEdit whether this caller may write to this row, as the server answered it
 *   (REQ-SEC-047); `null` means unknown and leaves the control enabled
 * @property jobOrderAllocations how much of this entry is promised to which Auftrag
 * @property jobOrderRest what is left after the Auftrag split, as the server rendered it
 * @property missionAllocations how much is promised to which Einsatz
 * @property missionRest what is left after the Einsatz split, independent of [jobOrderRest]
 * @property owningOrgUnitId which org-unit pool the entry sits in, or `null` for an unpooled row;
 *   the transfer's org-unit picker presets to it
 */
data class InventoryEntry(
    val id: String,
    val materialName: String,
    val materialId: String?,
    val unit: String?,
    val locationName: String?,
    val locationId: String?,
    val holder: String?,
    val holderId: String?,
    val amount: String?,
    val quality: String?,
    val personal: Boolean,
    val note: String?,
    val version: Long?,
    val jobOrderAllocations: List<InventoryAllocation> = emptyList(),
    val jobOrderRest: String? = null,
    val missionAllocations: List<InventoryAllocation> = emptyList(),
    val missionRest: String? = null,
    val owningOrgUnitId: String? = null,
    val canEdit: Boolean? = null,
)

/**
 * An org unit a transfer may hand stock to, drawn from the **destination** member's memberships.
 *
 * @property id what the booking sends
 * @property name the unit as it is called
 * @property shorthand its abbreviation, or `null`
 */
data class OrgUnitOption(
    val id: String,
    val name: String,
    val shorthand: String? = null,
)

/**
 * Which of an entry's two independent splits an allocation belongs to; the server reconciles
 * Auftrag and Einsatz amounts against the entry separately.
 */
enum class AllocationKind {
    /** Promised to an Auftrag. */
    JOB_ORDER,

    /** Promised to an Einsatz. */
    MISSION,
}

/**
 * One promise made out of a stock entry.
 *
 * @property targetId the Auftrag or Einsatz it is promised to.
 * @property label what to call that target on screen.
 * @property subtitle a second line where the target has one - an Einsatz's planned start - else
 *   `null`.
 * @property amount how much, as the server rendered it.
 */
data class InventoryAllocation(
    val targetId: String,
    val label: String,
    val subtitle: String?,
    val amount: String,
)

/**
 * Something an allocation can point at.
 *
 * @property id the Auftrag or Einsatz.
 * @property label what to call it.
 * @property subtitle a second line, or `null`.
 * @property requiredMaterialIds which materials the target asks for, empty when it names none.
 * @property requiredGameItemIds the same for items.
 */
data class AllocationTarget(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val requiredMaterialIds: List<String> = emptyList(),
    val requiredGameItemIds: List<String> = emptyList(),
) {
    /**
     * Whether this target has any use for what is being booked in.
     *
     * A target that names no requirement is always offered; otherwise the server would reject an
     * earmark that does not match.
     *
     * @param catalogId the material or item being booked in, or `null` before one is picked.
     * @param item whether that id names an item rather than a material.
     * @return whether the target may be offered.
     */
    fun krtAccepts(
        catalogId: String?,
        item: Boolean,
    ): Boolean {
        val required = if (item) requiredGameItemIds else requiredMaterialIds
        return catalogId == null || required.isEmpty() || catalogId in required
    }
}

/**
 * One game item and where the org unit's copies of it sit.
 *
 * @property id the game item.
 * @property name what it is called.
 * @property kind the catalogue's free-text category string.
 * @property manufacturer who makes it, or `null`.
 * @property amount how many pieces there are in total.
 * @property holders how many distinct members hold them.
 * @property locations the distinct places, in the order the server listed them.
 */
data class GameItemStock(
    val id: String,
    val name: String,
    val kind: String?,
    val manufacturer: String?,
    val amount: Double,
    val holders: Int,
    val locations: List<String>,
)

/** What a book-out does with the material. */
enum class BookOutKind {
    /** It is gone: spoiled, lost, spent. */
    DISCARD,

    /** It changes hands or place. */
    TRANSFER,

    /** It is sold at a terminal. */
    SELL,
}

/**
 * Maps the form's earmarks onto the wire, or to `null` when there are none.
 *
 * A row whose amount is not a positive number is dropped rather than sent as a zero: the server
 * reads a zero as an earmark of nothing and would create it, leaving a target promised nothing.
 *
 * @return the inputs, or `null` when nothing is earmarked.
 */
private fun List<InventoryAllocation>.krtToInputs(): List<InventoryAllocationInput>? =
    mapNotNull { row ->
        row.amount.krtToDoubleOrNull()?.takeIf { it > 0.0 }?.let {
            InventoryAllocationInput(targetId = row.targetId, amount = it)
        }
    }.takeIf { it.isNotEmpty() }

/**
 * What booking stock in carries.
 *
 * Exactly one of `materialId` and `gameItemId` is set (REQ-INV-029). A material row requires a
 * quality and an item row forbids one; an item amount must be a positive whole number; `mergeStock`
 * is ignored for an item.
 *
 * @property materialId the material being booked in, or `null` for an item row
 * @property gameItemId the item being booked in, or `null` for a material row
 * @property locationId where it goes
 * @property amount how much
 * @property quality the quality, 0–1000; `null` for an item row
 * @property personal whether it is private stock; always `false` from the app
 * @property mergeStock whether the server may merge it into an identical entry
 * @property jobOrderAllocations Auftrag earmarks for the new row, validated in the same
 *   transaction as the booking (REQ-INV-027); empty leaves the row unassigned
 * @property missionAllocations the same for Einsätze; always empty for an item row (REQ-INV-031)
 */
data class BookInDraft(
    val materialId: String? = null,
    val gameItemId: String? = null,
    val locationId: String,
    val amount: String,
    val quality: Int?,
    val personal: Boolean = false,
    val mergeStock: Boolean = true,
    val jobOrderAllocations: List<InventoryAllocation> = emptyList(),
    val missionAllocations: List<InventoryAllocation> = emptyList(),
)

/**
 * What booking material out carries.
 *
 * @property amount how much leaves the entry
 * @property kind what happens to it
 * @property targetUserId who receives it, for a transfer
 * @property targetLocationId where it goes, for a transfer
 * @property terminal the terminal it is sold at, for a sale
 * @property sellAmount what it fetched, for a sale
 * @property jobOrderReductions how much of the deducted amount comes from each Auftrag earmark;
 *   empty takes it from the unassigned rest first
 * @property missionReductions the same for the Einsatz earmarks, sourced independently
 * @property targetOwningOrgUnitId which org-unit pool the moved row lands in, for a transfer;
 *   `null` lets the server resolve it
 * @property mergeStock whether the server may fold the moved amount into an identical entry at the
 *   target; only meaningful for an `SCU` material
 */
data class BookOutDraft(
    val amount: String,
    val kind: BookOutKind,
    val targetUserId: String? = null,
    val targetLocationId: String? = null,
    val terminal: String? = null,
    val sellAmount: String? = null,
    val targetOwningOrgUnitId: String? = null,
    val mergeStock: Boolean = false,
    val jobOrderReductions: List<AllocationReduction> = emptyList(),
    val missionReductions: List<AllocationReduction> = emptyList(),
)

/**
 * How much of a book-out comes out of one earmark.
 *
 * On a `TRANSFER` the reduced tags travel with the stock; on a `SELL` the mission reductions also
 * decide who is credited what.
 *
 * @property targetId the Auftrag or Einsatz the amount is taken from.
 * @property amount how much comes from it.
 */
data class AllocationReduction(
    val targetId: String,
    val amount: Double,
)

/**
 * A material the booking form can pick.
 *
 * @property id what a booking sends
 * @property name the material
 * @property unit the unit its amounts are expressed in, or `null`
 */
data class MaterialOption(
    val id: String,
    val name: String,
    val unit: String?,
)

/**
 * A game item the booking form can pick; always counted in whole pieces.
 *
 * @property id what a booking sends as `gameItemId`
 * @property name what to show for it
 */
data class GameItemOption(
    val id: String,
    val name: String,
)

/**
 * A place the booking form can pick.
 *
 * @property id what a booking sends
 * @property name the place
 */
data class LocationOption(
    val id: String,
    val name: String,
)

/**
 * A member the booking form can hand material to.
 *
 * @property id what a booking sends
 * @property name the member, as the web app renders them
 */
data class MemberOption(
    val id: String,
    val name: String,
)

/**
 * A terminal that buys a material.
 *
 * @property id what a sale sends
 * @property name the terminal
 * @property price what it pays per unit, or `null`
 */
data class TerminalOption(
    val id: String,
    val name: String,
    val price: String?,
)

/**
 * One page of material groups.
 *
 * [Page.rows] holds the rows on this page.
 */
typealias InventoryPage = Page<InventoryGroup>

/**
 * The material catalogue search as a seam of its own, shared by the Lager and the Materialbörse's
 * „Gesuch erstellen" sheet.
 */
fun interface MaterialLookup {
    /**
     * Searches materials.
     *
     * @param query what the member typed.
     * @return one page of matches, and whether the catalogue holds more (ADR-0104).
     */
    suspend fun materials(query: String): ApiResult<PickerPage<MaterialOption>>
}

/**
 * What a bulk rebook did; rows already at the target are skipped, not failed.
 *
 * @property rebooked how many rows moved.
 * @property skipped how many were already where they were being sent.
 */
data class BulkRebookResult(
    val rebooked: Int,
    val skipped: Int,
)

/**
 * The allocation half of the Lager's API.
 *
 * Split out from [InventorySource] because it is a seam of its own: three endpoints that address a
 * stock entry's promises rather than its stock, and a screen — the Zuordnung sheet — that needs
 * only these. [InventorySource] extends it, so a caller that wants both still asks for one type.
 */
interface InventoryAllocationSource {
    @Suppress("LongParameterList")
    suspend fun setAllocation(
        entryId: String,
        kind: AllocationKind,
        targetId: String,
        amount: String,
        existing: Boolean,
        version: Long?,
    ): ApiResult<InventoryEntry>

    /**
     * The Aufträge an allocation may point at.
     *
     * @return the open orders, or the classified failure.
     */
    suspend fun orderTargets(): ApiResult<List<AllocationTarget>>

    /**
     * The Einsätze an allocation may point at.
     *
     * @return the missions, or the classified failure.
     */
    suspend fun missionTargets(): ApiResult<List<AllocationTarget>>
}

/**
 * Reads a material whole, across every holder and place, for the Lager's tablet pane.
 */
interface MaterialDetailSource {
    /**
     * Reads one page of a material's entries, across every holder and place.
     *
     * The page carries its totals so the pane can state what it does not show (ADR-0104).
     *
     * @param materialId which material.
     * @param page the zero-based page index.
     * @return the page, or the classified failure.
     */
    suspend fun materialEntries(
        materialId: String,
        page: Int = 0,
    ): ApiResult<MaterialEntryPage>
}

/**
 * Where stock can land: the three lookups every book-in form asks for.
 *
 * Its own seam rather than part of [InventorySource] alone, because the Herstellung books produced
 * units into the Lager without reading it — a form that needs „wo, bei wem, in welchen Pool" should
 * not have to stand up the whole warehouse to be tested.
 */
interface BookInOptions {
    /**
     * Searches places.
     *
     * @param query what the member typed.
     * @return one page of matches, and whether the catalogue holds more (ADR-0104).
     */
    suspend fun locations(query: String): ApiResult<PickerPage<LocationOption>>

    /**
     * Searches the game-item catalogue, which is separate from the materials.
     *
     * @param query what the member typed.
     * @return one page of matches, and whether the catalogue holds more (ADR-0104).
     */
    suspend fun gameItems(query: String): ApiResult<PickerPage<GameItemOption>>

    /**
     * Which of these entries are already offered on the Materialbörse.
     *
     * @param entryIds the rows on screen; an empty list asks nothing.
     * @return the subset that is released; empty on a failure.
     */
    suspend fun releasedEntryIds(entryIds: List<String>): Set<String>

    /**
     * Searches members.
     *
     * @param query what the member typed.
     * @return one page of matches, and whether the roster holds more (ADR-0104).
     */
    suspend fun members(query: String): ApiResult<PickerPage<MemberOption>>

    /**
     * Reads the org units stock may be booked into for the **owning** member, whose memberships the
     * server validates the choice against.
     *
     * @param userId the member the stock would belong to.
     * @return their memberships across all four org-unit kinds.
     */
    suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>>
}

/**
 * The Lager reads, as a seam.
 */
interface InventorySource :
    MaterialLookup,
    BookInOptions,
    InventoryAllocationSource {
    /**
     * Reads one page of material groups.
     *
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun groups(
        page: Int = 0,
        pageSize: Int = InventoryRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<InventoryPage>

    /**
     * Reads the stacks of one material.
     *
     * @param materialId the material whose group was opened.
     * @return its stacks, or a failure; an empty list is valid for a just-emptied group.
     */
    suspend fun stacks(materialId: String): ApiResult<List<InventoryStack>>

    /**
     * Reads the entries inside one stack, addressed by material, holder, place, quality and owning
     * org unit.
     *
     * @param materialId which material's group the stack sits in.
     * @param stack the stack row that was opened.
     * @return the entries, or the classified failure.
     */
    suspend fun entries(
        materialId: String,
        stack: InventoryStack,
    ): ApiResult<List<InventoryEntry>>

    /**
     * Books material in.
     *
     * @param draft what the member entered.
     * @return success, or the classified failure.
     */
    suspend fun bookIn(draft: BookInDraft): ApiResult<Unit>

    /**
     * Moves several of the caller's rows to one location at once.
     *
     * All or nothing: an unknown id, a foreign row or a blocking earmark aborts the whole call. Rows
     * already at the target are skipped and counted.
     *
     * @param entryIds the rows to move.
     * @param locationId where they go.
     * @return how many moved and how many were already there, or the classified failure.
     */
    suspend fun bulkRebook(
        entryIds: List<String>,
        locationId: String,
    ): ApiResult<BulkRebookResult>

    /**
     * Books several of the caller's own rows out in full in one call (Sammel-Ausbuchen).
     *
     * All or nothing: each row is deleted with its earmarks, and a foreign or unknown id aborts the
     * whole call.
     *
     * @param entryIds which rows.
     * @return nothing on success, or the classified failure.
     */
    suspend fun bulkCheckout(entryIds: List<String>): ApiResult<Unit>

    /**
     * Reads the org unit's complete **game-item** stock, grouped per item, in one unpaged call.
     *
     * @return the items, or the classified failure.
     */
    suspend fun gameItemStock(): ApiResult<List<GameItemStock>>

    /**
     * Books material out of one entry.
     *
     * @param id the entry.
     * @param version the version the entry was read at.
     * @param draft what the member entered.
     * @return success, or the classified failure.
     */

    suspend fun bookOut(
        id: String,
        version: Long?,
        draft: BookOutDraft,
    ): ApiResult<Unit>

    /**
     * Changes an entry's note.
     *
     * @param id the entry.
     * @param version the version the entry was read at.
     * @param note the new note, or `null` to clear it.
     * @return success, or the classified failure.
     */
    suspend fun updateNote(
        id: String,
        version: Long?,
        note: String?,
    ): ApiResult<Unit>

    /**
     * Reads the terminals that buy a material.
     *
     * @param materialId the material.
     * @return the terminals with their prices.
     */
    suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>>
}

/**
 * Reads the Lager from the backend: the aggregate for the group rows and the grouped read for a
 * group the member opens.
 *
 * The org unit follows from the `X-Active-Org-Unit-Id` header the interceptor sets.
 *
 * @property reader performs the calls and classifies their failures
 */
class InventoryRepository(
    private val reader: ApiReader,
) : InventorySource,
    MaterialDetailSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads one page of groups.
     *
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun groups(
        page: Int,
        pageSize: Int,
    ): ApiResult<InventoryPage> {
        val params = listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString())
        return reader.get(AGGREGATED_PATH, params, PageResponseAggregatedInventoryDto.serializer())
            .map { it.toModel(page) }
    }

    /**
     * Reads one material's stacks.
     *
     * @param materialId the material.
     * @return the stacks, or the classified failure.
     */
    override suspend fun stacks(materialId: String): ApiResult<List<InventoryStack>> {
        val params = listOf(MATERIAL_PARAM to materialId)
        return when (
            val result =
                reader.get(GROUPED_PATH, params, ListSerializer(GroupedInventoryDto.serializer()))
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.flatMap { it.stacks.orEmpty() }.map { it.toModel() })
            }
        }
    }

    override suspend fun entries(
        materialId: String,
        stack: InventoryStack,
    ): ApiResult<List<InventoryEntry>> {
        val params =
            buildList {
                add(MATERIAL_ID_PARAM to materialId)
                stack.locationId?.let { add(LOCATION_ID_PARAM to it) }
                stack.holderId?.let { add(USER_ID_PARAM to it) }
                stack.quality?.wholeNumber()?.let { add(QUALITY_PARAM to it) }
                stack.owningOrgUnitId?.let { add(OWNING_ORG_UNIT_PARAM to it) }
                add(PAGE_PARAM to "0")
                add(SIZE_PARAM to ENTRY_PAGE_SIZE.toString())
            }
        return when (
            val result =
                reader.get(ENTRIES_PATH, params, PageResponseInventoryItemDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.content.orEmpty().mapNotNull { it.toEntry() })
            }
        }
    }

    override suspend fun materialEntries(
        materialId: String,
        page: Int,
    ): ApiResult<MaterialEntryPage> =
        when (
            val result =
                reader.get(
                    "$MATERIAL_PATH/$materialId",
                    listOf(
                        PAGE_PARAM to page.toString(),
                        SIZE_PARAM to MATERIAL_PAGE_SIZE.toString(),
                    ),
                    PageResponseInventoryItemDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    MaterialEntryPage(
                        rows = result.value.content.orEmpty().mapNotNull { it.toEntry() },
                        page = result.value.page ?: page,
                        totalPages = result.value.totalPages ?: 1,
                        totalElements = result.value.totalElements ?: 0L,
                    ),
                )
            }
        }

    override suspend fun bookIn(draft: BookInDraft): ApiResult<Unit> =
        sendUnit(
            BOOK_IN_PATH,
            InventoryItemCreateDto(
                amount = parseTypedAmount(draft.amount) ?: 0.0,
                locationId = draft.locationId,
                materialId = draft.materialId,
                gameItemId = draft.gameItemId,
                quality = draft.quality.takeIf { draft.gameItemId == null },
                personal = draft.personal,
                mergeStock = draft.mergeStock,
                jobOrderAllocations = draft.jobOrderAllocations.krtToInputs(),
                missionAllocations = draft.missionAllocations.krtToInputs(),
            ),
            InventoryItemCreateDto.serializer(),
        )

    override suspend fun setAllocation(
        entryId: String,
        kind: AllocationKind,
        targetId: String,
        amount: String,
        existing: Boolean,
        version: Long?,
    ): ApiResult<InventoryEntry> {
        val quantity = parseTypedAmount(amount) ?: 0.0
        val body =
            InventoryAllocationWriteDto(
                field = kind.toWire(),
                targetId = targetId,
                amount = quantity.takeIf { it > 0.0 },
                version = version,
            )
        val path = "$ALLOCATION_PATH_PREFIX/$entryId/allocation"
        val result =
            when {
                quantity <= 0.0 -> {
                    reader.send(
                        path = path,
                        method = "DELETE",
                        body = body,
                        bodySerializer = InventoryAllocationWriteDto.serializer(),
                        deserializer = InventoryItemDto.serializer(),
                    )
                }

                existing -> {
                    reader.send(
                        path = path,
                        method = "PATCH",
                        body = body,
                        bodySerializer = InventoryAllocationWriteDto.serializer(),
                        deserializer = InventoryItemDto.serializer(),
                    )
                }

                else -> {
                    reader.post(
                        path = path,
                        body = body,
                        bodySerializer = InventoryAllocationWriteDto.serializer(),
                        deserializer = InventoryItemDto.serializer(),
                    )
                }
            }
        return when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toEntry()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK))
            }
        }
    }

    override suspend fun orderTargets(): ApiResult<List<AllocationTarget>> =
        when (
            val result =
                reader.get(
                    path = "/api/v1/orders/lookup",
                    deserializer = ListSerializer(JobOrderReferenceDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { reference ->
                        reference.id?.let { id ->
                            AllocationTarget(
                                id = id,
                                label = reference.displayId?.let { "#$it" } ?: id,
                                subtitle = reference.handle?.takeIf { it.isNotBlank() },
                                requiredMaterialIds = reference.requiredMaterialIds.orEmpty(),
                                requiredGameItemIds = reference.requiredGameItemIds.orEmpty(),
                            )
                        }
                    },
                )
            }
        }

    override suspend fun missionTargets(): ApiResult<List<AllocationTarget>> =
        when (
            val result =
                reader.get(
                    path = "/api/v1/missions/lookup",
                    deserializer = ListSerializer(MissionReferenceDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { reference ->
                        reference.id?.let { id ->
                            AllocationTarget(
                                id = id,
                                label = reference.name.orEmpty().ifBlank { id },
                                subtitle = reference.status?.takeIf { it.isNotBlank() },
                            )
                        }
                    },
                )
            }
        }

    override suspend fun gameItemStock(): ApiResult<List<GameItemStock>> =
        reader.get(
            ALL_GROUPED_PATH,
            listOf(CATALOG_PARAM to CATALOG_ITEM),
            ListSerializer(GroupedInventoryDto.serializer()),
        )
            .map { loaded -> loaded.mapNotNull { it.toItemStock() } }

    override suspend fun bulkCheckout(entryIds: List<String>): ApiResult<Unit> =
        reader.postAccepted(
            BULK_CHECKOUT_PATH,
            BulkCheckoutRequest(itemIds = entryIds),
            BulkCheckoutRequest.serializer(),
        )

    override suspend fun bulkRebook(
        entryIds: List<String>,
        locationId: String,
    ): ApiResult<BulkRebookResult> =
        when (
            val result =
                reader.post(
                    path = BULK_REBOOK_PATH,
                    body =
                        BulkRebookRequest(
                            itemIds = entryIds,
                            mode = BulkRebookRequest.Mode.LOCATION,
                            targetLocationId = locationId,
                            mergeStock = true,
                        ),
                    bodySerializer = BulkRebookRequest.serializer(),
                    deserializer = BulkRebookResultDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    BulkRebookResult(
                        rebooked = result.value.rebooked ?: 0,
                        skipped = result.value.skipped ?: 0,
                    ),
                )
            }
        }

    override suspend fun bookOut(
        id: String,
        version: Long?,
        draft: BookOutDraft,
    ): ApiResult<Unit> =
        sendUnit(
            "$BOOK_IN_PATH/$id/book-out",
            InventoryItemBookOutDto(
                amount = parseTypedAmount(draft.amount) ?: 0.0,
                version = version ?: 0L,
                type = draft.kind.toWire(),
                targetUserId = draft.targetUserId,
                targetLocationId = draft.targetLocationId,
                terminal = draft.terminal,
                sellAmount = parseTypedDecimal(draft.sellAmount)?.let(::KrtDecimal),
                targetOwningOrgUnitId = draft.targetOwningOrgUnitId,
                mergeStock = draft.mergeStock,
                jobOrderReductions = draft.jobOrderReductions.toWire(),
                missionReductions = draft.missionReductions.toWire(),
            ),
            InventoryItemBookOutDto.serializer(),
        )

    override suspend fun updateNote(
        id: String,
        version: Long?,
        note: String?,
    ): ApiResult<Unit> =
        reader.put(
            "$BOOK_IN_PATH/$id/note",
            InventoryItemNoteUpdateRequest(version = version ?: 0L, note = note),
            InventoryItemNoteUpdateRequest.serializer(),
            InventoryItemDto.serializer(),
        )
            .map { }

    override suspend fun materials(query: String): ApiResult<PickerPage<MaterialOption>> {
        val params =
            listOf(
                SEARCH_PARAM to query.trim(),
                PAGE_PARAM to "0",
                SIZE_PARAM to PICKER_PAGE_SIZE.toString(),
            )
        return when (
            val result = reader.get(MATERIALS_PATH, params, PageResponseMaterialDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    krtPickerPage(
                        result.value.content.orEmpty().mapNotNull { it.toOption() },
                        result.value.totalElements,
                    ),
                )
            }
        }
    }

    override suspend fun gameItems(query: String): ApiResult<PickerPage<GameItemOption>> {
        val params =
            listOf(
                SEARCH_PARAM to query.trim(),
                PAGE_PARAM to "0",
                SIZE_PARAM to PICKER_PAGE_SIZE.toString(),
            )
        return when (
            val result =
                reader.get(ITEM_CATALOG_PATH, params, PageResponseGameItemReferenceDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    krtPickerPage(
                        result.value.content.orEmpty().mapNotNull { row ->
                            row.id?.let { GameItemOption(id = it, name = row.name.orEmpty()) }
                        },
                        result.value.totalElements,
                    ),
                )
            }
        }
    }

    override suspend fun releasedEntryIds(entryIds: List<String>): Set<String> {
        if (entryIds.isEmpty()) {
            return emptySet()
        }
        val params = entryIds.map { RELEASED_IDS_PARAM to it }
        return when (
            val result =
                reader.get(RELEASED_IDS_PATH, params, ListSerializer(serializer<String>()))
        ) {
            is ApiResult.Failure -> emptySet()
            is ApiResult.Success -> result.value.toSet()
        }
    }

    override suspend fun locations(query: String): ApiResult<PickerPage<LocationOption>> {
        val params =
            listOf(
                SEARCH_PARAM to query.trim(),
                PAGE_PARAM to "0",
                SIZE_PARAM to LOCATION_PAGE_SIZE.toString(),
            )
        return when (
            val result =
                reader.get(LOCATIONS_PATH, params, PageResponseLocationReferenceDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    krtPickerPage(
                        result.value.content.orEmpty().mapNotNull { it.toOption() },
                        result.value.totalElements,
                    ),
                )
            }
        }
    }

    override suspend fun members(query: String): ApiResult<PickerPage<MemberOption>> {
        val params =
            listOf(
                QUERY_PARAM to query.trim(),
                PAGE_PARAM to "0",
                SIZE_PARAM to PICKER_PAGE_SIZE.toString(),
            )
        return when (
            val result = reader.get(MEMBERS_PATH, params, PageResponseUserDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    krtPickerPage(
                        result.value.content.orEmpty().mapNotNull { it.toOption() },
                        result.value.totalElements,
                    ),
                )
            }
        }
    }

    override suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>> =
        reader.get(
            path = "/api/v1/users/$userId/memberships",
            query = listOf(ALL_KINDS_PARAM to "true"),
            deserializer = ListSerializer(OrgUnitMembershipOptionDto.serializer()),
        )
            .map { loaded -> loaded.mapNotNull { it.toOption() } }

    override suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>> =
        reader.get(
            "/api/v1/materials/$materialId/terminals",
            ListSerializer(MaterialSellingTerminalDto.serializer()),
        )
            .map { loaded -> loaded.mapNotNull { it.toOption() } }

    /**
     * Sends a booking and discards the saved entry it answers with; the screen re-reads the tree.
     *
     * @param B the request type
     * @param path where to send it.
     * @param body the payload.
     * @param serializer the request serializer.
     * @return success, or the classified failure.
     */
    private suspend fun <B> sendUnit(
        path: String,
        body: B,
        serializer: kotlinx.serialization.SerializationStrategy<B>,
    ): ApiResult<Unit> = reader.postUnit(path, body, serializer)

    companion object {
        /** Groups per page. */
        const val DEFAULT_PAGE_SIZE: Int = 50

        /** Log subsystem. A holder's name is member data and never reaches the log. */
        private const val LOG_TAG = "inventory"

        private const val AGGREGATED_PATH = "/api/v1/inventory/aggregated"
        private const val GROUPED_PATH = "/api/v1/inventory/all/grouped"
        private const val ENTRIES_PATH = "/api/v1/inventory/all/stack/entries"

        /** One material's entries, flat and paged — the tablet pane's read. */
        private const val MATERIAL_PATH = "/api/v1/inventory/material"

        /** How many entries the tablet pane asks for at once. */
        private const val MATERIAL_PAGE_SIZE = 50
        private const val BOOK_IN_PATH = "/api/v1/inventory"
        private const val ALLOCATION_PATH_PREFIX = "/api/v1/inventory"
        private const val BULK_REBOOK_PATH = "/api/v1/inventory/bulk-rebook"
        private const val BULK_CHECKOUT_PATH = "/api/v1/inventory/bulk-checkout"
        private const val ALL_GROUPED_PATH = "/api/v1/inventory/all/grouped"
        private const val CATALOG_PARAM = "catalog"
        private const val CATALOG_ITEM = "ITEM"

        /** The status a 200 that could not be mapped is reported under. */
        private const val HTTP_OK = 200
        private const val MATERIALS_PATH = "/api/v1/materials/search"
        private const val LOCATIONS_PATH = "/api/v1/locations/search"
        private const val MEMBERS_PATH = "/api/v1/users/search"

        /** The item catalogue behind the book-in's item picker — the order form's own search. */
        private const val ITEM_CATALOG_PATH = "/api/v1/orders/item-catalog"

        /** Which rows on screen are already offered on the Materialbörse. */
        private const val RELEASED_IDS_PATH = "/api/v1/material-exchange/released-item-ids"

        /** Repeated once per row asked about. */
        private const val RELEASED_IDS_PARAM = "ids"
        private const val MATERIAL_ID_PARAM = "materialId"
        private const val LOCATION_ID_PARAM = "locationId"
        private const val USER_ID_PARAM = "userId"
        private const val QUALITY_PARAM = "quality"
        private const val OWNING_ORG_UNIT_PARAM = "owningOrgUnitId"

        /** Widens the membership lookup from Staffel/SK to all four org-unit kinds. */
        private const val ALL_KINDS_PARAM = "allKinds"
        private const val SEARCH_PARAM = "search"
        private const val QUERY_PARAM = "query"

        /** How many entries one stack may hold before the screen has to page. */
        private const val ENTRY_PAGE_SIZE = 100

        /** How many rows a picker asks for. */
        private const val PICKER_PAGE_SIZE = 50

        /**
         * How many places one location search offers; larger than [PICKER_PAGE_SIZE] because the location
         * catalogue is small and bounded.
         */
        private const val LOCATION_PAGE_SIZE = 200
        private const val MATERIAL_PARAM = "materialIds"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
    }
}

/**
 * Maps a page of groups onto the model.
 *
 * @param page the page index that was requested.
 * @return the page.
 */
private fun PageResponseAggregatedInventoryDto.toModel(page: Int): InventoryPage =
    InventoryPage(
        rows = content.orEmpty().map { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one group row onto the model; a row without a material id is kept but cannot be opened.
 *
 * @return the group.
 */
private fun AggregatedInventoryDto.toModel(): InventoryGroup =
    InventoryGroup(
        materialId = material?.id,
        name = material?.name ?: gameItem?.name.orEmpty(),
        unit = material?.quantityType,
        amount = amount?.toPlainString(),
        quality = quality?.toPlainString(),
        maxQuality = maxQuality?.toPlainString(),
    )

/**
 * Maps one stack onto the model.
 *
 * @return the stack.
 */
private fun InventoryStackDto.toModel(): InventoryStack =
    InventoryStack(
        holder = user?.effectiveName,
        location = location?.name,
        personal = personal == true,
        amount = totalAmount?.toPlainString(),
        quality = quality?.toString(),
        entryCount = entryCount ?: 0,
        holderId = user?.id,
        locationId = location?.id,
        owningOrgUnitId = owningSquadron?.id,
    )

/**
 * Maps one membership row onto a picker option.
 *
 * @return the option, or `null` without an id — a choice a booking cannot send.
 */
private fun OrgUnitMembershipOptionDto.toOption(): OrgUnitOption? {
    val unitId = orgUnitId ?: return null
    return OrgUnitOption(
        id = unitId,
        name = orgUnitName.orEmpty().ifBlank { orgUnitShorthand.orEmpty() },
        shorthand = orgUnitShorthand?.takeIf { it.isNotBlank() },
    )
}

/**
 * Reduces a server-rendered quality to the whole number its query parameter takes.
 *
 * @return the digits before the decimal point, or `null` when the text is not a number at all.
 */
private fun String.wholeNumber(): String? = toBigDecimalOrNull()?.toBigInteger()?.toString()

/**
 * Renders a quantity without scientific notation.
 *
 * A `Double` prints as `1.0E7` past seven digits, and a warehouse figure that reads like a physics
 * constant is a figure a member cannot check.
 *
 * @return the plain decimal form.
 */
private fun Double.toPlainString(): String = java.math.BigDecimal(this.toString()).toPlainString()

/**
 * Maps one entry onto the model.
 *
 * @return the entry, or `null` without an id — a row a booking cannot address.
 */
private fun InventoryItemDto.toEntry(): InventoryEntry? {
    val rowId = id ?: return null
    return InventoryEntry(
        id = rowId,
        materialName = material?.name.orEmpty(),
        materialId = material?.id,
        unit = material?.quantityType?.value,
        locationName = location?.name,
        locationId = location?.id,
        holder = user?.effectiveName,
        holderId = user?.id,
        amount = amount?.toPlainString(),
        quality = quality?.toString(),
        personal = personal == true,
        owningOrgUnitId = owningSquadron?.id,
        canEdit = canEdit,
        note = note?.takeIf { it.isNotBlank() },
        version = version,
        jobOrderAllocations =
            jobOrderAllocations.orEmpty().mapNotNull { allocation ->
                allocation.jobOrderId?.let { target ->
                    InventoryAllocation(
                        targetId = target,
                        label = allocation.jobOrderDisplayId?.let { "#$it" } ?: target,
                        subtitle = null,
                        amount = allocation.amount?.toPlainString() ?: "0",
                    )
                }
            },
        jobOrderRest = jobOrderRest?.toPlainString(),
        missionAllocations =
            missionAllocations.orEmpty().mapNotNull { allocation ->
                allocation.missionId?.let { target ->
                    InventoryAllocation(
                        targetId = target,
                        label = allocation.missionName.orEmpty().ifBlank { target },
                        subtitle = allocation.missionPlannedStartTime,
                        amount = allocation.amount?.toPlainString() ?: "0",
                    )
                }
            },
        missionRest = missionRest?.toPlainString(),
    )
}

/**
 * Maps a deduct-from plan onto the wire, or onto nothing.
 *
 * @return the reductions, or `null` when there is no plan — which is the server's default.
 */
private fun List<AllocationReduction>.toWire(): List<AllocationReductionDto>? =
    takeIf { it.isNotEmpty() }?.map { AllocationReductionDto(targetId = it.targetId, amount = it.amount) }

/**
 * Maps the app's allocation kind onto the wire enum.
 *
 * @return the generated constant.
 */
private fun AllocationKind.toWire(): InventoryAllocationWriteDto.Field =
    when (this) {
        AllocationKind.JOB_ORDER -> InventoryAllocationWriteDto.Field.JOB_ORDER
        AllocationKind.MISSION -> InventoryAllocationWriteDto.Field.MISSION
    }

/**
 * Maps the app's book-out kind onto the wire enum.
 *
 * @return the generated constant.
 */
private fun BookOutKind.toWire(): InventoryItemBookOutDto.Type =
    when (this) {
        BookOutKind.DISCARD -> InventoryItemBookOutDto.Type.DISCARD
        BookOutKind.TRANSFER -> InventoryItemBookOutDto.Type.TRANSFER
        BookOutKind.SELL -> InventoryItemBookOutDto.Type.SELL
    }

/**
 * Maps one material onto the picker's model.
 *
 * @return the option, or `null` without an id.
 */
private fun MaterialDto.toOption(): MaterialOption? {
    val materialId = id ?: return null
    return MaterialOption(id = materialId, name = name.orEmpty(), unit = quantityType)
}

/**
 * Maps one place onto the picker's model.
 *
 * @return the option, or `null` without an id.
 */
private fun LocationReferenceDto.toOption(): LocationOption? {
    val placeId = id ?: return null
    return LocationOption(id = placeId, name = name.orEmpty())
}

/**
 * Maps one member onto the picker's model.
 *
 * `effectiveName` and not `username`: it is what the web app renders and what a member recognises.
 *
 * @return the option, or `null` without an id.
 */
private fun UserDto.toOption(): MemberOption? {
    val memberId = id ?: return null
    return MemberOption(id = memberId, name = effectiveName.orEmpty())
}

/**
 * Maps one terminal onto the picker's model.
 *
 * @return the option, or `null` without an id.
 */
private fun MaterialSellingTerminalDto.toOption(): TerminalOption? {
    val terminal = terminalId ?: return null
    return TerminalOption(
        id = terminal,
        name = terminalName.orEmpty(),
        price = priceSell?.value?.toPlainString(),
    )
}

/**
 * One grouped row as the game-item screen holds it.
 *
 * @receiver what the server sent.
 * @return the row, or `null` when it names no game item.
 */
private fun GroupedInventoryDto.toItemStock(): GameItemStock? {
    val item = gameItem
    val id = item?.id
    if (item == null || id == null) {
        return null
    }
    val stacks = stacks.orEmpty()
    return GameItemStock(
        id = id,
        name = item.name.orEmpty(),
        kind = item.kind?.takeIf { it.isNotBlank() },
        manufacturer = item.manufacturer?.takeIf { it.isNotBlank() },
        amount = totalAmount ?: 0.0,
        holders = stacks.mapNotNull { it.user?.id }.distinct().size,
        locations = stacks.mapNotNull { it.location?.name?.takeIf { name -> name.isNotBlank() } }.distinct(),
    )
}

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
import de.greluc.krt.profit.basetool.android.core.contract.model.GroupedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemBookOutDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemNoteUpdateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryStackDto
import de.greluc.krt.profit.basetool.android.core.contract.model.LocationReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialSellingTerminalDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseAggregatedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseInventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseLocationReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseUserDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UserDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
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
 * One stack inside a group — a member's holding at one place and quality.
 *
 * @property holder whose stack it is, or `null` for one the server did not attribute
 * @property location where it is, or `null`
 * @property personal whether it is the holder's private stock rather than the shared Lager
 * @property amount how much, as the server rendered it
 * @property quality the stack's quality — the **key** the server groups by, not the average it
 *   also reports; the entry read is looked up by it and the average matches only by coincidence
 * @property entryCount how many individual entries it sums up
 * @property holderId whose stack it is, by id — carried since phase 3 because the entry read is
 *   asked for by (material, holder, place, quality) and a name cannot key that
 * @property locationId where it is, by id
 * @property owningOrgUnitId which org-unit pool it belongs to, or `null` for an unpooled holding —
 *   part of the entry read's key, and omitting it asks for the unpooled stack instead
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
 * One entry inside a stack — the thing a booking actually moves.
 *
 * @property id the entry's id
 * @property materialName what it is
 * @property unit the unit the amount is expressed in, or `null`
 * @property locationName where it is, or `null`
 * @property locationId the same, by id
 * @property materialId which material it holds, or `null` when the server sent none — the
 *   terminals a sale can pick from are looked up by it
 * @property holder whose it is, or `null`
 * @property holderId whose it is, by id — a transfer has to change the holder or the place, and a
 *   name cannot be compared against the one the picker returns
 * @property amount how much, as the server rendered it
 * @property quality the quality, or `null`
 * @property note the member's own note, or `null`
 * @property version the optimistic lock, echoed by every booking
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
 * What booking material in carries.
 *
 * @property materialId what is being booked in
 * @property locationId where it goes
 * @property amount how much
 * @property quality the quality, 0–1000, or `null` when the material has none
 * @property personal whether it is private stock rather than the shared Lager. Always `false` from
 *   the app: the Lager reads exclude private stock, so booking it in from here would put material
 *   somewhere no screen of this app can show it again
 * @property mergeStock whether the server may merge it into an identical entry
 */
data class BookInDraft(
    val materialId: String,
    val locationId: String,
    val amount: String,
    val quality: Int?,
    val personal: Boolean = false,
    val mergeStock: Boolean = true,
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
 */
data class BookOutDraft(
    val amount: String,
    val kind: BookOutKind,
    val targetUserId: String? = null,
    val targetLocationId: String? = null,
    val terminal: String? = null,
    val sellAmount: String? = null,
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
 * @property groups the rows on this page
 * @property page the zero-based page index
 * @property totalPages how many pages exist
 * @property totalElements how many materials the org unit holds in total
 */
data class InventoryPage(
    val groups: List<InventoryGroup>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * The material catalogue, as a seam of its own.
 *
 * Extracted from [InventorySource] rather than duplicated: the Materialbörse's „Gesuch erstellen"
 * sheet needs exactly this one method and nothing else the Lager offers, and a second repository
 * calling `/api/v1/materials/search` would be a second place for the page cap and the trimming to
 * drift.
 */
fun interface MaterialLookup {
    /**
     * Searches materials.
     *
     * @param query what the member typed.
     * @return the matches, capped by the server's page size.
     */
    suspend fun materials(query: String): ApiResult<List<MaterialOption>>
}

/**
 * The Lager reads, as a seam.
 */
interface InventorySource : MaterialLookup {
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
     * @return its stacks, or a failure. An empty list is an ordinary answer for a group that has
     *   just been emptied.
     */
    suspend fun stacks(materialId: String): ApiResult<List<InventoryStack>>

    /**
     * Reads the entries inside one stack.
     *
     * The stack is passed whole rather than as four loose strings: the server addresses a stack by
     * (material, holder, place, quality, owning org unit) and every one of them is part of the key.
     * Dropping one does not widen the answer — it asks for a different stack.
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
     * Searches places.
     *
     * @param query what the member typed.
     * @return the matches.
     */
    suspend fun locations(query: String): ApiResult<List<LocationOption>>

    /**
     * Searches members, for a transfer.
     *
     * @param query what the member typed.
     * @return the matches.
     */
    suspend fun members(query: String): ApiResult<List<MemberOption>>

    /**
     * Reads the terminals that buy a material.
     *
     * @param materialId the material.
     * @return the terminals with their prices.
     */
    suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>>
}

/**
 * Reads the Lager from the backend.
 *
 * **Two reads, one per level of the tree.** The aggregate draws the group rows; the grouped read
 * fills a group the member actually opened. The flat `/inventory/all` was the alternative and is
 * wrong for a tree: it would pull every entry in the warehouse to draw a dozen headings, and the
 * member would wait for rows they may never expand.
 *
 * Which org unit's Lager this is follows from the `X-Active-Org-Unit-Id` header the interceptor
 * already sets.
 *
 * @property reader performs the calls and classifies their failures
 */
class InventoryRepository(
    private val reader: ApiReader,
) : InventorySource {
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
        return when (
            val result =
                reader.get(AGGREGATED_PATH, params, PageResponseAggregatedInventoryDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
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
                // The endpoint answers with a group per material asked for. One was asked for, so
                // the stacks of all of them are the stacks of that one — flattened rather than
                // indexed, so a server that answered with none simply yields none.
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
                // An `Integer` parameter, so a quality the server happened to render with a
                // decimal point comes back 400 TYPE_MISMATCH and the stack reads as "could not be
                // loaded" (found on a device, 2026-08-23).
                stack.quality?.wholeNumber()?.let { add(QUALITY_PARAM to it) }
                // Omitting this does not mean "any pool" — the query reads a missing id as "the
                // unpooled stack", so an org-owned stack answers with nothing at all.
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

    override suspend fun bookIn(draft: BookInDraft): ApiResult<Unit> =
        sendUnit(
            BOOK_IN_PATH,
            InventoryItemCreateDto(
                amount = draft.amount.toDoubleOrNull() ?: 0.0,
                locationId = draft.locationId,
                materialId = draft.materialId,
                quality = draft.quality,
                personal = draft.personal,
                mergeStock = draft.mergeStock,
            ),
            InventoryItemCreateDto.serializer(),
        )

    override suspend fun bookOut(
        id: String,
        version: Long?,
        draft: BookOutDraft,
    ): ApiResult<Unit> =
        sendUnit(
            "$BOOK_IN_PATH/$id/book-out",
            InventoryItemBookOutDto(
                amount = draft.amount.toDoubleOrNull() ?: 0.0,
                version = version ?: 0L,
                type = draft.kind.toWire(),
                targetUserId = draft.targetUserId,
                targetLocationId = draft.targetLocationId,
                terminal = draft.terminal,
                sellAmount = draft.sellAmount?.toBigDecimalOrNull()?.let(::KrtDecimal),
            ),
            InventoryItemBookOutDto.serializer(),
        )

    override suspend fun updateNote(
        id: String,
        version: Long?,
        note: String?,
    ): ApiResult<Unit> =
        when (
            val result =
                reader.put(
                    "$BOOK_IN_PATH/$id/note",
                    InventoryItemNoteUpdateRequest(version = version ?: 0L, note = note),
                    InventoryItemNoteUpdateRequest.serializer(),
                    InventoryItemDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    override suspend fun materials(query: String): ApiResult<List<MaterialOption>> {
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
                ApiResult.Success(result.value.content.orEmpty().mapNotNull { it.toOption() })
            }
        }
    }

    override suspend fun locations(query: String): ApiResult<List<LocationOption>> {
        val params =
            listOf(
                SEARCH_PARAM to query.trim(),
                PAGE_PARAM to "0",
                SIZE_PARAM to PICKER_PAGE_SIZE.toString(),
            )
        return when (
            val result =
                reader.get(LOCATIONS_PATH, params, PageResponseLocationReferenceDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.content.orEmpty().mapNotNull { it.toOption() })
            }
        }
    }

    override suspend fun members(query: String): ApiResult<List<MemberOption>> {
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
                ApiResult.Success(result.value.content.orEmpty().mapNotNull { it.toOption() })
            }
        }
    }

    override suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>> =
        when (
            val result =
                reader.get(
                    "/api/v1/materials/$materialId/terminals",
                    ListSerializer(MaterialSellingTerminalDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.mapNotNull { it.toOption() })
        }

    /**
     * Sends a booking whose answer the screen does not read.
     *
     * Every booking answers with the saved entry, and none of the three needs it: the screen
     * re-reads the tree afterwards, because a booking changes what a *stack* holds and not only
     * the entry that moved.
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
    ): ApiResult<Unit> =
        when (
            val result = reader.post(path, body, serializer, InventoryItemDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    companion object {
        /** Groups per page. */
        const val DEFAULT_PAGE_SIZE: Int = 50

        /** Log subsystem. A holder's name is member data and never reaches the log. */
        private const val LOG_TAG = "inventory"

        private const val AGGREGATED_PATH = "/api/v1/inventory/aggregated"
        private const val GROUPED_PATH = "/api/v1/inventory/all/grouped"
        private const val ENTRIES_PATH = "/api/v1/inventory/all/stack/entries"
        private const val BOOK_IN_PATH = "/api/v1/inventory"
        private const val MATERIALS_PATH = "/api/v1/materials/search"
        private const val LOCATIONS_PATH = "/api/v1/locations/search"
        private const val MEMBERS_PATH = "/api/v1/users/search"
        private const val MATERIAL_ID_PARAM = "materialId"
        private const val LOCATION_ID_PARAM = "locationId"
        private const val USER_ID_PARAM = "userId"
        private const val QUALITY_PARAM = "quality"
        private const val OWNING_ORG_UNIT_PARAM = "owningOrgUnitId"
        private const val SEARCH_PARAM = "search"
        private const val QUERY_PARAM = "query"

        /** How many entries one stack may hold before the screen has to page. */
        private const val ENTRY_PAGE_SIZE = 100

        /** How many rows a picker asks for. */
        private const val PICKER_PAGE_SIZE = 25
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
        groups = content.orEmpty().map { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one group row onto the model.
 *
 * A row with no material id is kept: it still states an amount the org unit holds, and dropping it
 * would quietly lower what the tree adds up to. It simply cannot be opened, which the screen
 * reflects by not offering the tap.
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
        note = note?.takeIf { it.isNotBlank() },
        version = version,
    )
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

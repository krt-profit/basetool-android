/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkOrgUnitChangeRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkOrgUnitChangeResultDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkRebookRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BulkRebookResultDto
import de.greluc.krt.profit.basetool.android.core.contract.model.GroupedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemOrgUnitChangeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemPersonalRebookDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseInventoryItemDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.flatMap
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient

/**
 * Reads the Lager tree for both scopes and writes the „Mein Lager" moves.
 *
 * The org unit of the Org-Lager follows from the `X-Active-Org-Unit-Id` header; „Mein Lager" is the
 * caller's own rows whatever unit is pinned.
 *
 * @property reader performs the calls and classifies their failures
 */
class LagerRepository(
    private val reader: ApiReader,
) : LagerTreeSource,
    StockMoveSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    override suspend fun grouped(
        scope: LagerScope,
        filter: LagerFilter,
    ): ApiResult<List<StockGroup>> =
        when (scope) {
            LagerScope.ORG -> {
                readGrouped(ORG_GROUPED_PATH, filter.orgParams())
            }

            LagerScope.MY -> {
                readGrouped(MY_GROUPED_PATH, filter.myParams()).flatMap { materials ->
                    readGrouped(MY_GROUPED_PATH, filter.myParams() + (CATALOG_PARAM to CATALOG_ITEM))
                        .map { items -> materials + items }
                }
            }
        }

    override suspend fun stackEntries(
        scope: LagerScope,
        group: InventoryGroup,
        stack: InventoryStack,
    ): ApiResult<List<InventoryEntry>> {
        val params =
            buildList {
                val itemId = group.gameItemId
                if (itemId != null) {
                    add(GAME_ITEM_ID_PARAM to itemId)
                    add(CATALOG_PARAM to CATALOG_ITEM)
                } else {
                    group.materialId?.let { add(MATERIAL_ID_PARAM to it) }
                    stack.quality?.wholeNumber()?.let { add(QUALITY_PARAM to it) }
                }
                stack.locationId?.let { add(LOCATION_ID_PARAM to it) }
                if (scope == LagerScope.ORG) {
                    stack.holderId?.let { add(USER_ID_PARAM to it) }
                } else {
                    add(PERSONAL_PARAM to stack.personal.toString())
                }
                stack.owningOrgUnitId?.let { add(OWNING_ORG_UNIT_PARAM to it) }
                if (stack.stolen) {
                    add(STOLEN_PARAM to "true")
                }
                add(PAGE_PARAM to "0")
                add(SIZE_PARAM to ENTRY_PAGE_SIZE.toString())
            }
        val path = if (scope == LagerScope.ORG) ORG_ENTRIES_PATH else MY_ENTRIES_PATH
        return reader.get(path, params, PageResponseInventoryItemDto.serializer())
            .map { page -> page.content.orEmpty().mapNotNull { it.toEntry() } }
    }

    override suspend fun myEntryIds(filter: LagerFilter): ApiResult<Map<String, Boolean>> {
        val kinds =
            when (filter.personal) {
                PersonalFilter.ALL -> listOf(true, false)
                PersonalFilter.PERSONAL_ONLY -> listOf(true)
                PersonalFilter.SHARED_ONLY -> listOf(false)
            }
        val base = filter.copy(personal = PersonalFilter.ALL).myParams()
        val found = mutableMapOf<String, Boolean>()
        for (personal in kinds) {
            val narrowed = base + ((if (personal) PERSONAL_ONLY_PARAM else SHARED_ONLY_PARAM) to "true")
            for (catalog in listOf(emptyList(), listOf(CATALOG_PARAM to CATALOG_ITEM))) {
                when (
                    val result =
                        reader.get(MY_ENTRY_IDS_PATH, narrowed + catalog, ListSerializer(String.serializer()))
                ) {
                    is ApiResult.Failure -> return result
                    is ApiResult.Success -> result.value.forEach { found[it] = personal }
                }
            }
        }
        return ApiResult.Success(found)
    }

    override suspend fun rebookPersonal(
        entry: InventoryEntry,
        amount: String,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<Unit> =
        reader.postUnit(
            "$INVENTORY_PATH/${entry.id}/personal-rebook",
            InventoryItemPersonalRebookDto(
                amount = parseTypedAmount(amount) ?: 0.0,
                version = entry.version ?: 0L,
                mergeStock = mergeStock,
                targetOwningOrgUnitId = targetOrgUnitId.takeIf { entry.personal },
            ),
            InventoryItemPersonalRebookDto.serializer(),
        )

    override suspend fun bulkRebookPersonal(
        entryIds: List<String>,
        personal: Boolean,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<BulkRebookResult> =
        reader.post(
            path = BULK_REBOOK_PATH,
            body =
                BulkRebookRequest(
                    itemIds = entryIds,
                    mode = if (personal) BulkRebookRequest.Mode.PERSONALIZE else BulkRebookRequest.Mode.DEPERSONALIZE,
                    mergeStock = mergeStock,
                    targetOwningOrgUnitId = targetOrgUnitId.takeUnless { personal },
                ),
            bodySerializer = BulkRebookRequest.serializer(),
            deserializer = BulkRebookResultDto.serializer(),
        ).map { BulkRebookResult(rebooked = it.rebooked ?: 0, skipped = it.skipped ?: 0) }

    override suspend fun changeOrgUnit(
        entry: InventoryEntry,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<Unit> =
        reader.postUnit(
            "$INVENTORY_PATH/${entry.id}/org-unit",
            InventoryItemOrgUnitChangeDto(
                mergeStock = mergeStock,
                targetOwningOrgUnitId = targetOrgUnitId,
                version = entry.version,
            ),
            InventoryItemOrgUnitChangeDto.serializer(),
        )

    override suspend fun bulkChangeOrgUnit(
        entryIds: List<String>,
        targetOrgUnitId: String?,
        mergeStock: Boolean,
    ): ApiResult<BulkChangeResult> =
        reader.post(
            path = BULK_ORG_UNIT_PATH,
            body =
                BulkOrgUnitChangeRequest(
                    itemIds = entryIds,
                    mergeStock = mergeStock,
                    targetOwningOrgUnitId = targetOrgUnitId,
                ),
            bodySerializer = BulkOrgUnitChangeRequest.serializer(),
            deserializer = BulkOrgUnitChangeResultDto.serializer(),
        ).map { BulkChangeResult(changed = it.changed ?: 0, skipped = it.skipped ?: 0) }

    /**
     * Reads one grouped endpoint and maps its groups.
     *
     * @param path which endpoint.
     * @param params its query.
     * @return the groups, or the classified failure.
     */
    private suspend fun readGrouped(
        path: String,
        params: List<Pair<String, String>>,
    ): ApiResult<List<StockGroup>> =
        reader.get(path, params, ListSerializer(GroupedInventoryDto.serializer()))
            .map { groups -> groups.mapNotNull { it.toStockGroup() } }

    private companion object {
        /** Log subsystem. A holder's name is member data and never reaches the log. */
        const val LOG_TAG = "lager"

        const val INVENTORY_PATH = "/api/v1/inventory"
        const val ORG_GROUPED_PATH = "/api/v1/inventory/all/grouped"
        const val ORG_ENTRIES_PATH = "/api/v1/inventory/all/stack/entries"
        const val MY_GROUPED_PATH = "/api/v1/inventory/my-inventory/grouped"
        const val MY_ENTRIES_PATH = "/api/v1/inventory/my-inventory/stack/entries"
        const val MY_ENTRY_IDS_PATH = "/api/v1/inventory/my-inventory/entry-ids"
        const val BULK_REBOOK_PATH = "/api/v1/inventory/bulk-rebook"
        const val BULK_ORG_UNIT_PATH = "/api/v1/inventory/bulk-org-unit"

        const val CATALOG_PARAM = "catalog"
        const val CATALOG_ITEM = "ITEM"
        const val MATERIAL_ID_PARAM = "materialId"
        const val GAME_ITEM_ID_PARAM = "gameItemId"
        const val LOCATION_ID_PARAM = "locationId"
        const val LOCATION_IDS_PARAM = "locationIds"
        const val USER_ID_PARAM = "userId"
        const val QUALITY_PARAM = "quality"
        const val PERSONAL_PARAM = "personal"
        const val STOLEN_PARAM = "stolen"
        const val OWNING_ORG_UNIT_PARAM = "owningOrgUnitId"
        const val PERSONAL_ONLY_PARAM = "personalOnly"
        const val SHARED_ONLY_PARAM = "nonPersonalOnly"
        const val STOLEN_ONLY_PARAM = "stolenOnly"
        const val WITHOUT_STOLEN_PARAM = "nonStolenOnly"
        const val PAGE_PARAM = "page"
        const val SIZE_PARAM = "size"

        /** How many entries one stack may hold before the screen has to page. */
        const val ENTRY_PAGE_SIZE = 100

        /**
         * The query parameters the Org-Lager's grouped read takes from a filter; it has no personal
         * dimension.
         *
         * @receiver the filter.
         * @return the parameters, in a stable order.
         */
        fun LagerFilter.orgParams(): List<Pair<String, String>> =
            buildList {
                locationIds.sorted().forEach { add(LOCATION_IDS_PARAM to it) }
                stolenParam()?.let(::add)
            }

        /**
         * The query parameters „Mein Lager" takes from a filter.
         *
         * @receiver the filter.
         * @return the parameters, in a stable order.
         */
        fun LagerFilter.myParams(): List<Pair<String, String>> =
            buildList {
                addAll(orgParams())
                when (personal) {
                    PersonalFilter.ALL -> Unit
                    PersonalFilter.PERSONAL_ONLY -> add(PERSONAL_ONLY_PARAM to "true")
                    PersonalFilter.SHARED_ONLY -> add(SHARED_ONLY_PARAM to "true")
                }
            }

        /**
         * The one parameter the stolen filter sends, or none.
         *
         * @receiver the filter.
         * @return the parameter, or `null` when stolen and regular stock are both wanted.
         */
        fun LagerFilter.stolenParam(): Pair<String, String>? =
            when (stolen) {
                StolenFilter.ALL -> null
                StolenFilter.WITHOUT -> WITHOUT_STOLEN_PARAM to "true"
                StolenFilter.ONLY -> STOLEN_ONLY_PARAM to "true"
            }
    }
}

/**
 * Maps one grouped row onto a group with its stacks.
 *
 * @receiver what the server sent.
 * @return the group, or `null` when it names neither a material nor a game item.
 */
internal fun GroupedInventoryDto.toStockGroup(): StockGroup? {
    val materialRef = material
    val itemRef = gameItem
    val group =
        when {
            materialRef?.id != null -> {
                InventoryGroup(
                    materialId = materialRef.id,
                    name = materialRef.name.orEmpty(),
                    unit = materialRef.quantityType?.value,
                    amount = totalAmount?.krtPlain(),
                    quality = averageQuality?.krtPlain(),
                    maxQuality = maxQuality?.toString(),
                )
            }

            itemRef?.id != null -> {
                InventoryGroup(
                    materialId = null,
                    gameItemId = itemRef.id,
                    name = itemRef.name.orEmpty(),
                    unit = PIECE_UNIT,
                    amount = totalAmount?.krtPlain(),
                    quality = null,
                    maxQuality = null,
                )
            }

            else -> {
                return null
            }
        }
    return StockGroup(group = group, stacks = stacks.orEmpty().map { it.toModel() })
}

/**
 * Renders a quantity without scientific notation, as every Lager figure is shown.
 *
 * @receiver the quantity.
 * @return the plain decimal form.
 */
private fun Double.krtPlain(): String = java.math.BigDecimal(this.toString()).toPlainString()

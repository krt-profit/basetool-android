/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AggregatedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.GroupedInventoryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryStackDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseAggregatedInventoryDto
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
 * @property quality the stack's quality, or `null`
 * @property entryCount how many individual entries it sums up
 */
data class InventoryStack(
    val holder: String?,
    val location: String?,
    val personal: Boolean,
    val amount: String?,
    val quality: String?,
    val entryCount: Int,
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
 * The Lager reads, as a seam.
 */
interface InventorySource {
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

    companion object {
        /** Groups per page. */
        const val DEFAULT_PAGE_SIZE: Int = 50

        /** Log subsystem. A holder's name is member data and never reaches the log. */
        private const val LOG_TAG = "inventory"

        private const val AGGREGATED_PATH = "/api/v1/inventory/aggregated"
        private const val GROUPED_PATH = "/api/v1/inventory/all/grouped"
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
        quality = averageQuality?.toPlainString(),
        entryCount = entryCount ?: 0,
    )

/**
 * Renders a quantity without scientific notation.
 *
 * A `Double` prints as `1.0E7` past seven digits, and a warehouse figure that reads like a physics
 * constant is a figure a member cannot check.
 *
 * @return the plain decimal form.
 */
private fun Double.toPlainString(): String = java.math.BigDecimal(this.toString()).toPlainString()

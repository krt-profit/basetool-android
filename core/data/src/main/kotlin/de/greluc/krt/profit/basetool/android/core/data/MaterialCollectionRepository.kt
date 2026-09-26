/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialCollectionEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateDeliveredRequest
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal

/**
 * One stock row linked to an Auftrag.
 *
 * @property entryId the inventory row, which every write on this page addresses.
 * @property version its optimistic lock; the delivered flag echoes it.
 * @property owner who holds it, or `null` where the answer redacted it for a requesting-side
 *   viewer.
 * @property ownerId the same by id, or `null` for the same reason.
 * @property location where it is, or `null`.
 * @property locationId the same by id.
 * @property materialName what it holds.
 * @property quality the quality reading, or `null` for an item.
 * @property quantity how much is on the row.
 * @property allocated how much of that is earmarked to this Auftrag.
 * @property delivered whether it has been marked delivered.
 */
data class MaterialCollectionRow(
    val entryId: String,
    val version: Long?,
    val owner: String?,
    val ownerId: String?,
    val location: String?,
    val locationId: String?,
    val materialName: String,
    val quality: BigDecimal?,
    val quantity: BigDecimal?,
    val allocated: BigDecimal?,
    val delivered: Boolean,
)

/**
 * „Materialsammelübersicht": the stock rows linked to one Auftrag, and the writes that change them.
 */
interface MaterialCollectionSource {
    /**
     * Reads the rows linked to one Auftrag; owners are redacted for a requesting-side viewer.
     *
     * @param orderId the Auftrag.
     * @return the rows, or the classified failure.
     */
    suspend fun rows(orderId: String): ApiResult<List<MaterialCollectionRow>>

    /**
     * Flips one row's delivered flag without going through the book-out.
     *
     * @param entryId the stock row.
     * @param orderId the Auftrag the flag is set in the context of.
     * @param delivered what it should become.
     * @param version the row's optimistic lock.
     * @return nothing on success, or the classified failure.
     */
    suspend fun setDelivered(
        entryId: String,
        orderId: String,
        delivered: Boolean,
        version: Long,
    ): ApiResult<Unit>

    /**
     * Removes one stock row's link to the Auftrag; the stock itself is untouched.
     *
     * @param orderId the Auftrag.
     * @param entryId the stock row.
     * @return nothing on success, or the classified failure.
     */
    suspend fun unlinkEntry(
        orderId: String,
        entryId: String,
    ): ApiResult<Unit>

    /**
     * Removes a whole material's link to the Auftrag, for a required material with no stock behind it.
     *
     * @param orderId the Auftrag.
     * @param materialId the material.
     * @return nothing on success, or the classified failure.
     */
    suspend fun unlinkMaterial(
        orderId: String,
        materialId: String,
    ): ApiResult<Unit>
}

/** The Materialsammelübersicht, over HTTP. */
class MaterialCollectionRepository(
    private val reader: ApiReader,
) : MaterialCollectionSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "MaterialCollection"),
    )

    override suspend fun rows(orderId: String): ApiResult<List<MaterialCollectionRow>> =
        when (
            val result =
                reader.get(
                    "/api/v1/orders/$orderId/material-collection",
                    ListSerializer(MaterialCollectionEntryDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.mapNotNull { dto -> dto.krtToModel() })
            }
        }

    override suspend fun setDelivered(
        entryId: String,
        orderId: String,
        delivered: Boolean,
        version: Long,
    ): ApiResult<Unit> =
        reader.send(
            "/api/v1/inventory/$entryId/delivered",
            "PATCH",
            UpdateDeliveredRequest(delivered = delivered, jobOrderId = orderId, version = version),
            UpdateDeliveredRequest.serializer(),
            InventoryItemDto.serializer(),
        )
            .map { }

    override suspend fun unlinkEntry(
        orderId: String,
        entryId: String,
    ): ApiResult<Unit> = reader.delete("/api/v1/orders/$orderId/inventory/$entryId/unlink")

    override suspend fun unlinkMaterial(
        orderId: String,
        materialId: String,
    ): ApiResult<Unit> = reader.delete("/api/v1/orders/$orderId/materials/$materialId")
}

/**
 * Maps one linked row onto the model, or drops it when it cannot be addressed.
 *
 * @receiver the server's entry.
 * @return the row, or `null` without an entry id — every write here is addressed by it.
 */
private fun MaterialCollectionEntryDto.krtToModel(): MaterialCollectionRow? {
    val id = inventoryEntryId ?: return null
    return MaterialCollectionRow(
        entryId = id,
        version = version,
        owner = ownerName?.takeIf { it.isNotBlank() },
        ownerId = ownerId,
        location = location?.takeIf { it.isNotBlank() },
        locationId = locationId,
        materialName = materialName.orEmpty(),
        quality = quality?.toBigDecimal(),
        quantity = quantity?.toBigDecimal(),
        allocated = allocatedQuantity?.toBigDecimal(),
        delivered = delivered == true,
    )
}

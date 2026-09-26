/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BookInDto
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverItemCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemHandoverCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemHandoverEntryCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemProductionConsumptionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemProductionCreateDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/**
 * One stock row the work on an Auftrag can be booked out of, by Übergabe or by Herstellung.
 *
 * @property id the inventory row's id, which the write sends.
 * @property owner who holds it, or `null` when the answer redacted it.
 * @property location where it is, or `null`.
 * @property quality the material's quality reading, or `null` for an item.
 * @property amount how much is on the row, as the server rendered it.
 * @property stock the same figure as a number, for the Herstellung's caps.
 * @property slice how much of this row is earmarked to this Auftrag; a production booking may only
 *   draw against it.
 * @property version the row's optimistic lock, echoed by the consumption.
 */
data class HandoverStockRow(
    val id: String,
    val owner: String?,
    val location: String?,
    val quality: Int?,
    val amount: String,
    val stock: Double = 0.0,
    val slice: Double = 0.0,
    val version: Long? = null,
) {
    /**
     * The most this row can give up: `min(slice, stock)`, never negative.
     */
    val available: Double
        get() = minOf(slice, stock).coerceAtLeast(0.0)
}

/**
 * Recording that material changed hands.
 *
 * > **Without this, an Auftrag can be taken on in the app but never finished** — in the web the
 * > handover is what closes it. The design handoff names it „der schwerste Punkt der Liste"
 * > (ch. 10 artboard 14).
 */
interface JobOrderHandoverSource {
    /**
     * The stock rows already associated with this order line that its material can be handed over
     * from.
     *
     * @param orderId the Auftrag.
     * @param materialId which line.
     * @return the rows, or the classified failure.
     */
    suspend fun stockFor(
        orderId: String,
        materialId: String,
    ): ApiResult<List<HandoverStockRow>>

    /**
     * Records an append-only material handover out of one stock row, which the server requires.
     *
     * @param orderId the Auftrag.
     * @param inventoryItemId the stock row it is booked out of.
     * @param amount how much, in the line's own unit.
     * @param recipientHandle who received it; must be non-blank.
     * @param recipientSquadron their unit, or `null`.
     * @param handoverTime when it happened, ISO-8601, from the device's clock.
     * @return what the server recorded, or the classified failure.
     */
    suspend fun record(
        orderId: String,
        inventoryItemId: String,
        amount: String,
        recipientHandle: String,
        recipientSquadron: String?,
        handoverTime: String,
    ): ApiResult<JobOrderHandoverDto>

    /**
     * Records an append-only handover of finished **items**, which moves the line's
     * `deliveredAmount` and closes the order once every line is fully delivered.
     *
     * The ceiling is `manufactured − delivered`; the server refuses more with a 400
     * (REQ-ORDERS-025).
     *
     * @param orderId the Auftrag.
     * @param itemId the ordered line whose units changed hands.
     * @param amount how many, at least one.
     * @param recipientHandle who received them; must be non-blank.
     * @param handoverTime when it happened, ISO-8601, from the device's clock.
     * @return what the server recorded, or the classified failure.
     */
    suspend fun recordItemHandover(
        orderId: String,
        itemId: String,
        amount: Int,
        recipientHandle: String,
        handoverTime: String,
    ): ApiResult<JobOrderItemHandoverDto>
}

/**
 * One draw of a production booking: this much of this material, out of this stock row.
 *
 * @property inventoryItemId the row drawn from.
 * @property materialId what it holds.
 * @property amount how much comes off it.
 * @property version the row's optimistic lock.
 */
data class ProductionDraw(
    val inventoryItemId: String,
    val materialId: String,
    val amount: Double,
    val version: Long,
)

/**
 * Where the manufactured units land in the Lager.
 *
 * @property locationId where the produced stock is booked in; required.
 * @property ownerUserId whose row it becomes; `null` means the acting member.
 * @property owningOrgUnitId which unit's pool it lands in; `null` only when the owner has exactly
 *   one membership (REQ-ORG-004).
 * @property personal whether it goes into the owner's personal pool; excludes [allocateToOrder].
 * @property allocateToOrder whether the produced units are earmarked back to the producing
 *   Auftrag.
 */
data class ProductionBookIn(
    val locationId: String,
    val ownerUserId: String?,
    val owningOrgUnitId: String?,
    val personal: Boolean,
    val allocateToOrder: Boolean,
)

/**
 * What one production booking carries, in the shape the wire takes.
 *
 * @property orderId the Auftrag.
 * @property itemId the item line being manufactured.
 * @property amount how many whole units this run produced; at least one.
 * @property version the line's optimistic lock.
 * @property consumption which stock rows are drawn, and how much from each.
 * @property skippedMaterialIds materials consumed outside the tool, excluded from the server's
 *   coverage check.
 * @property bookIn where the produced units are stored.
 */
data class ProductionBooking(
    val orderId: String,
    val itemId: String,
    val amount: Int,
    val version: Long,
    val consumption: List<ProductionDraw>,
    val skippedMaterialIds: List<String>,
    val bookIn: ProductionBookIn,
)

/**
 * Booking a production run — „Herstellung" — against one item line of an Auftrag.
 *
 * > **This is not the Übergabe with different words.** A handover moves finished goods to someone;
 * > a production run *consumes* the earmarked raw material and *creates* item stock. Both writes
 * > exist on an item Auftrag, and only this one moves the „hergestellt" figure.
 */
interface JobOrderProductionSource {
    /**
     * The rows of one material that are earmarked to this Auftrag, with each row's earmark and
     * version.
     *
     * @param orderId the Auftrag.
     * @param materialId which material.
     * @return the rows, or the classified failure.
     */
    suspend fun linkedStock(
        orderId: String,
        materialId: String,
    ): ApiResult<List<HandoverStockRow>>

    /**
     * Books a production run.
     *
     * The server requires every non-skipped material's demand to be covered exactly, or answers 400.
     *
     * @param booking the whole payload.
     * @return nothing on success, or the classified failure.
     */
    suspend fun bookProduction(booking: ProductionBooking): ApiResult<Unit>
}

/**
 * Both writes that record work done on an Auftrag, as one seam.
 *
 * They are separate interfaces because they are separate things — one hands goods over, the other
 * builds them — and one type here because a screen that offers either offers both, and threading
 * two collaborators through for one repository buys nothing.
 */
interface JobOrderWorkSource :
    JobOrderHandoverSource,
    JobOrderProductionSource

/**
 * The two writes that record work done on an Auftrag, over HTTP.
 *
 * One class because they share an edge: both read the order's linked stock from the same endpoint,
 * and both are work booked against the same Auftrag.
 */
class JobOrderWorkRepository(
    private val reader: ApiReader,
) : JobOrderWorkSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client, so the bearer, the correlation id and the org pin are
     *   already on every request.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "JobOrderWork"),
    )

    override suspend fun stockFor(
        orderId: String,
        materialId: String,
    ): ApiResult<List<HandoverStockRow>> = linkedStock(orderId, materialId)

    override suspend fun linkedStock(
        orderId: String,
        materialId: String,
    ): ApiResult<List<HandoverStockRow>> =
        when (
            val result =
                reader.get(
                    "/api/v1/orders/$orderId/materials/$materialId/inventory",
                    ListSerializer(InventoryItemDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.mapNotNull { dto -> dto.krtStockRow(orderId) })
            }
        }

    override suspend fun record(
        orderId: String,
        inventoryItemId: String,
        amount: String,
        recipientHandle: String,
        recipientSquadron: String?,
        handoverTime: String,
    ): ApiResult<JobOrderHandoverDto> =
        reader.post(
            "/api/v1/orders/$orderId/handovers",
            JobOrderHandoverCreateDto(
                handoverTime = handoverTime,
                recipientHandle = recipientHandle,
                recipientSquadron = recipientSquadron,
                items =
                    listOf(
                        JobOrderHandoverItemCreateDto(
                            inventoryItemId = inventoryItemId,
                            amount = amount.krtToDoubleOrNull() ?: 0.0,
                        ),
                    ),
            ),
            JobOrderHandoverCreateDto.serializer(),
            JobOrderHandoverDto.serializer(),
        )

    override suspend fun recordItemHandover(
        orderId: String,
        itemId: String,
        amount: Int,
        recipientHandle: String,
        handoverTime: String,
    ): ApiResult<JobOrderItemHandoverDto> =
        reader.post(
            "/api/v1/orders/$orderId/item-handovers",
            JobOrderItemHandoverCreateDto(
                handoverTime = handoverTime,
                recipientHandle = recipientHandle,
                propertyEntries =
                    listOf(JobOrderItemHandoverEntryCreateDto(jobOrderItemId = itemId, amount = amount)),
            ),
            JobOrderItemHandoverCreateDto.serializer(),
            JobOrderItemHandoverDto.serializer(),
        )

    override suspend fun bookProduction(booking: ProductionBooking): ApiResult<Unit> =
        reader.postAccepted(
            "/api/v1/orders/${booking.orderId}/items/${booking.itemId}/production",
            JobOrderItemProductionCreateDto(
                amount = booking.amount,
                version = booking.version,
                consumption =
                    booking.consumption.map { draw ->
                        JobOrderItemProductionConsumptionDto(
                            inventoryItemId = draw.inventoryItemId,
                            materialId = draw.materialId,
                            amount = draw.amount,
                            version = draw.version,
                        )
                    },
                skippedMaterialIds = booking.skippedMaterialIds,
                bookIn =
                    BookInDto(
                        locationId = booking.bookIn.locationId,
                        ownerUserId = booking.bookIn.ownerUserId,
                        owningOrgUnitId = booking.bookIn.owningOrgUnitId,
                        personal = booking.bookIn.personal,
                        allocateToOrder = booking.bookIn.allocateToOrder,
                    ),
            ),
            JobOrderItemProductionCreateDto.serializer(),
        )
}

/**
 * Maps one inventory answer onto a candidate row, or drops it when it cannot be addressed.
 *
 * @receiver the server's row.
 * @param orderId the Auftrag whose earmark is the one that counts.
 * @return the row, or `null` without an id.
 */
private fun InventoryItemDto.krtStockRow(orderId: String): HandoverStockRow? {
    val rowId = id ?: return null
    return HandoverStockRow(
        id = rowId,
        owner = user?.effectiveName ?: user?.displayName,
        location = location?.name,
        quality = quality,
        amount = amount?.krtPlain().orEmpty(),
        stock = amount ?: 0.0,
        slice = jobOrderAllocations.orEmpty().firstOrNull { it.jobOrderId == orderId }?.amount ?: 0.0,
        version = version,
    )
}

/**
 * Parses a typed amount, accepting `,` as well as `.` as the decimal separator.
 *
 * @receiver what was typed.
 * @return the value, or `null` when it is not a number.
 */
fun String.krtToDoubleOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Renders a quantity as a plain decimal without scientific notation.
 *
 * @receiver the amount.
 * @return the plain decimal.
 */
private fun Double.krtPlain(): String = java.math.BigDecimal(this.toString()).toPlainString()

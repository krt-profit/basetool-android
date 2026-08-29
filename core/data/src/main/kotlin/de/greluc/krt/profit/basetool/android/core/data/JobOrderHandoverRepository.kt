/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.InventoryItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverItemCreateDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/**
 * One stock row a handover can be booked out of.
 *
 * @property id the inventory row's id — what the write sends.
 * @property owner who holds it, or `null` when the answer redacted it.
 * @property location where it is, or `null`.
 * @property quality the material's quality reading, or `null` for an item.
 * @property amount how much is on the row, as the server rendered it.
 */
data class HandoverStockRow(
    val id: String,
    val owner: String?,
    val location: String?,
    val quality: Int?,
    val amount: String,
)

/**
 * Recording that material physically changed hands.
 *
 * > **Without this, an Auftrag can be taken on in the app but never finished** — in the web the
 * > handover is what closes it. The design handoff names it „der schwerste Punkt der Liste"
 * > (ch. 10 artboard 14).
 */
interface JobOrderHandoverSource {
    /**
     * The stock rows this order's material can be handed over from.
     *
     * The candidates are **the order's own**, not the whole Lager: the endpoint answers with the
     * rows already associated with this order line, which is what makes the handover a booking-out
     * rather than a search.
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
     * Records the handover.
     *
     * > **A stock row is mandatory on the wire.** `JobOrderHandoverItemCreateDto.inventoryItemId`
     * > is `@NotNull` on the server and the web's own form refuses to submit without one
     * > („Bitte mindestens ein Material für die Übergabe auswählen"). Design ch. 10 artboard 14
     * > draws an „Ohne Lagerbezug erfassen" alternative; that option cannot be served by this
     * > endpoint and is **not** built. Flagged rather than coded around.
     *
     * The write is **append-only**: nothing in this app takes a handover back.
     *
     * @param orderId the Auftrag.
     * @param inventoryItemId the stock row it is booked out of.
     * @param amount how much, in the line's own unit.
     * @param recipientHandle who received it — a handle, which the server requires non-blank.
     * @param recipientSquadron their unit, or `null`.
     * @param handoverTime when it happened, ISO-8601. Built on the device so the member's own
     *   clock decides, which is what the web does for the same reason.
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
}

/** The Übergabe, over HTTP. */
class JobOrderHandoverRepository(
    private val reader: ApiReader,
) : JobOrderHandoverSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client, so the bearer, the correlation id and the org pin are
     *   already on every request.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "JobOrderHandover"),
    )

    override suspend fun stockFor(
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
                ApiResult.Success(
                    result.value.mapNotNull { dto ->
                        val id = dto.id ?: return@mapNotNull null
                        HandoverStockRow(
                            id = id,
                            owner = dto.user?.effectiveName ?: dto.user?.displayName,
                            location = dto.location?.name,
                            quality = dto.quality,
                            amount = dto.amount?.krtPlain().orEmpty(),
                        )
                    },
                )
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
                            // A German keyboard sends a comma; the wire wants a point.
                            amount = amount.krtToDoubleOrNull() ?: 0.0,
                        ),
                    ),
            ),
            JobOrderHandoverCreateDto.serializer(),
            JobOrderHandoverDto.serializer(),
        )
}

/**
 * Parses an amount somebody typed, whichever separator their keyboard offers.
 *
 * A German keyboard's numeric pad sends `,` and the wire wants `.`; a member who types the number
 * their locale shows them must not have it rejected.
 *
 * @receiver what was typed.
 * @return the value, or `null` when it is not a number.
 */
fun String.krtToDoubleOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Renders a quantity without scientific notation.
 *
 * A `Double` prints as `1.0E7` past seven digits, and a warehouse figure that reads like a physics
 * paper is a figure nobody checks. Its own copy rather than a shared one: two modules already carry
 * this and making it `internal` collides with them.
 *
 * @receiver the amount.
 * @return the plain decimal.
 */
private fun Double.krtPlain(): String = java.math.BigDecimal(this.toString()).toPlainString()

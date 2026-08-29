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
 * One stock row the work on an Auftrag can be booked out of.
 *
 * The same row serves both writes on this edge — the Übergabe books it out to a recipient, the
 * Herstellung consumes it into a manufactured item — which is why it carries both the plain amount
 * and the earmark.
 *
 * @property id the inventory row's id — what the write sends.
 * @property owner who holds it, or `null` when the answer redacted it.
 * @property location where it is, or `null`.
 * @property quality the material's quality reading, or `null` for an item.
 * @property amount how much is on the row, as the server rendered it.
 * @property stock the same figure as a number, for the caps the Herstellung has to compute.
 * @property slice how much of this row is earmarked to **this** Auftrag. A production booking may
 *   only draw against the earmark, so a row with no slice is not a candidate at all.
 * @property version the row's optimistic lock, echoed by the consumption so a concurrent stock
 *   change surfaces as a 409 rather than as a silent double-spend.
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
     * The most this row can give up: never more than is on it, never more than is promised here.
     *
     * The web computes the same `min(slice, stock)` — an earmark can outlive the stock it was made
     * against, and offering the promise as if it were material would put a number on screen that
     * the server then refuses.
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

    /**
     * Records the handover of finished **items**.
     *
     * A different endpoint and a different record, not the material handover with a count: an item
     * order is counted in pieces, the server keeps the two logs apart, and this one moves the
     * line's `deliveredAmount` and closes the order once every line is fully delivered.
     *
     * > **The ceiling is `manufactured − delivered`, never `amount − delivered`.** A unit can only
     * > be handed over once it has been built (REQ-ORDERS-025); `JobOrderItemHandoverService`
     * > refuses anything above the manufactured-but-undelivered count with a 400. Offering the
     * > obvious subtraction would put a number on screen the server rejects for a reason the member
     * > cannot see.
     *
     * Append-only, like its material sibling: nothing in this app takes a handover back.
     *
     * @param orderId the Auftrag.
     * @param itemId the ordered line whose units changed hands.
     * @param amount how many, at least one.
     * @param recipientHandle who received them; the server requires a non-blank handle.
     * @param handoverTime when it happened, ISO-8601, from the device's own clock.
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
 * @property version the row's optimistic lock, so a concurrent change is a 409 and not a
 *   double-spend.
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
 * The artboard does not draw this section and the server cannot do without it —
 * `BookInDto.locationId` is `@NotNull` — so the sheet asks for it. See the design gap list.
 *
 * @property locationId where the produced stock is booked in; required.
 * @property ownerUserId whose row it becomes; `null` means the acting member, which is what the
 *   server defaults to.
 * @property owningOrgUnitId which unit's pool it lands in. `null` only when the owner has exactly
 *   one membership — with more, the server answers 400 rather than guessing (REQ-ORG-004).
 * @property personal whether it goes into the owner's personal pool. Mutually exclusive with
 *   [allocateToOrder]: personal stock never carries earmarks.
 * @property allocateToOrder whether the produced units are earmarked back to the Auftrag that
 *   produced them.
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
 * A single parameter object rather than seven positional ones: the write has a genuinely wide
 * payload, and threading it as one value keeps the call sites readable and the argument order
 * impossible to get wrong.
 *
 * @property orderId the Auftrag.
 * @property itemId the item line being manufactured.
 * @property amount how many whole units this run produced; at least one.
 * @property version the line's optimistic lock.
 * @property consumption which stock rows are drawn, and how much from each.
 * @property skippedMaterialIds materials deliberately not booked out — consumed outside the tool.
 *   Their demand drops out of the server's coverage check.
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
     * The rows of one material that are earmarked to this Auftrag.
     *
     * Same endpoint as the Übergabe's candidates; the Herstellung additionally needs each row's
     * earmark and version, which is why [HandoverStockRow] carries them.
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
     * Books the run.
     *
     * The server checks the consumption plan **exactly**: every non-skipped required material's
     * demand must be covered to the last unit, or it answers 400 („Zuweisung deckt den
     * Materialbedarf nicht exakt."). The sheet holds its own submit until that is true, so the
     * refusal is the second line of defence rather than the first.
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
                            // A German keyboard sends a comma; the wire wants a point.
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
                // One line per write. The wire takes a list, and a form that offered several at
                // once would have to reconcile several independent ceilings before it could tell
                // the member which one it broke.
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

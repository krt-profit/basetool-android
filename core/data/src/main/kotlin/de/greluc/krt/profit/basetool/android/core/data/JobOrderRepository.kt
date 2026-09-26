/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AssigneeNoteRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateJobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateJobOrderItemLineDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateJobOrderItemRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateJobOrderMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemStockGroupDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseGameItemReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseJobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.SystemSettingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateJobOrderStatusDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.time.Instant

/** Where a job order stands. */
enum class JobOrderStatus {
    /** Nobody has taken it on yet. */
    OPEN,

    /** Someone is working on it. */
    IN_PROGRESS,

    /** It was turned down. */
    REJECTED,

    /** It is done. */
    COMPLETED,

    /** A status this build does not know; rendered as the raw server value. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps a server status onto the enum.
         *
         * @param raw the wire value, possibly `null`.
         * @return the matching constant, or [UNKNOWN].
         */
        fun from(raw: String?): JobOrderStatus =
            entries.firstOrNull { it != UNKNOWN && it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * What one item line consumes of one material, as the blueprint derived it.
 *
 * @property materialId which material; the consumption plan is addressed by it.
 * @property name what it is called.
 * @property unit `SCU` or `PIECE`; a piece count is never booked in fractions.
 * @property requiredTotal how much the whole line needs, not a single unit.
 */
data class JobOrderItemRequirement(
    val materialId: String,
    val name: String,
    val unit: String?,
    val requiredTotal: Double,
)

/**
 * One item line of an order, with its figures as counts: asked for, built and handed over.
 *
 * @property id the line's id
 * @property name the item's name
 * @property gameItemId which finished item this line orders; fills the edit picker and addresses
 *   the write
 * @property blueprintName which blueprint it is built from, or `null` when the server named none
 * @property blueprintId that blueprint by id, as the write names it
 * @property amount how many were asked for
 * @property manufactured how many have been built
 * @property delivered how many have been handed over
 * @property blueprintStale whether the blueprint has changed since the order was raised
 * @property requirements what one whole line of this item consumes, as the server derived it; the
 *   Herstellung covers it per material
 * @property version the line's own optimistic lock, echoed by the Herstellung
 * @property parentItemId the line this one is a sub-assembly of, or `null` for a top-level line
 */
data class JobOrderItem(
    val id: String?,
    val gameItemId: String?,
    val name: String?,
    val blueprintName: String?,
    val blueprintId: String?,
    val amount: Int,
    val manufactured: Int,
    val delivered: Int,
    val blueprintStale: Boolean,
    val requirements: List<JobOrderItemRequirement> = emptyList(),
    val version: Long? = null,
    val parentItemId: String? = null,
) {
    /** How many of this line are still to be built — the cap on one Herstellung. */
    val remaining: Int
        get() = (amount - manufactured).coerceAtLeast(0)

    /**
     * How many are built and not yet handed over: the cap on one Übergabe (REQ-ORDERS-025).
     */
    val deliverable: Int
        get() = (manufactured - delivered).coerceAtLeast(0)

    /**
     * How far along this line is, built over asked-for, between 0 and 1, or `null` when nothing was
     * asked for.
     */
    val progress: Float?
        get() {
            if (amount <= 0) {
                return null
            }
            return (manufactured.toFloat() / amount).coerceIn(0f, 1f)
        }
}

/**
 * One material line of an order.
 *
 * @property materialId which material, addressing the handover's stock picker; a line without one
 *   cannot be handed over from the app
 * @property name the material's name
 * @property needed how much the order asks for, as the server rendered it
 * @property inStock how much the responsible unit already holds
 * @property claimCount how many separate promises exist
 * @property claimedAmount the sum of those promises, or `null` when there are none
 * @property open how much is still missing, as the server computed it
 * @property unit `SCU` or `PIECE`, or `null` when the server named none
 */
data class JobOrderMaterial(
    val materialId: String?,
    val name: String,
    val needed: String?,
    val inStock: String?,
    val claimCount: Int,
    val claimedAmount: String?,
    val open: String?,
    val unit: String? = null,
) {
    /**
     * How far along this line is, stock over need, between 0 and 1, as a bar length.
     *
     * `null` when the need is zero or the stock figure is missing.
     */
    val progress: Float?
        get() {
            val need = needed?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
            val have = inStock?.toDoubleOrNull() ?: return null
            return (have / need).coerceIn(0.0, 1.0).toFloat()
        }
}

/**
 * One material inside a handover: the only source for how much of a line has actually changed
 * hands.
 *
 * @property materialId which material, or `null` when the answer redacted it.
 * @property amount how much this handover carried, as the server rendered it.
 */
data class JobOrderHandoverLine(
    val materialId: String?,
    val amount: Double,
)

/**
 * One line of an item handover: how many of one ordered item changed hands.
 *
 * @property itemId which ordered line, or `null` when the answer did not name one.
 * @property itemName what was handed over.
 * @property amount how many — a count, because an item order is counted in pieces.
 */
data class JobOrderItemHandoverLine(
    val itemId: String?,
    val itemName: String,
    val amount: Int,
)

/**
 * One item handover already recorded against an order, kept apart from material handovers.
 *
 * @property id the handover's id.
 * @property recipient who received it, or `null`.
 * @property executor who handed it over, or `null`.
 * @property at when, in UTC.
 * @property lines what it carried.
 */
data class JobOrderItemHandover(
    val id: String,
    val recipient: String?,
    val executor: String?,
    val at: Instant?,
    val lines: List<JobOrderItemHandoverLine> = emptyList(),
)

/**
 * One handover already recorded against an order.
 *
 * @property id the handover's id
 * @property recipient who received it, or `null`
 * @property executor who handed it over, or `null`
 * @property at when, in UTC
 */
data class JobOrderHandover(
    val id: String,
    val recipient: String?,
    val executor: String?,
    val at: Instant?,
    val lines: List<JobOrderHandoverLine> = emptyList(),
)

/**
 * One member on an order.
 *
 * @property userId who they are, by id, as both writes on this edge address them
 * @property name how they read, or `null` for a row the server did not attribute
 * @property note their own note on what they take on
 * @property version the assignee edge's own optimistic lock, independent of the order's
 */
data class JobOrderAssignee(
    val userId: String,
    val name: String?,
    val note: String?,
    val version: Long?,
)

/**
 * One job order.
 *
 * @property id the order's id
 * @property displayId the human-facing number as an integer; the `#` and padding belong to the
 *   screen
 * @property status where it stands
 * @property rawStatus the untranslated server value, for [JobOrderStatus.UNKNOWN]
 * @property priority the queue priority; lower sorts first
 * @property type `MATERIAL` or `ITEM` as the server names it
 * @property requestingOrgUnit who asked for it
 * @property requestingOrgUnitId the same unit by id, which fills the edit form's customer picker
 * @property responsibleOrgUnit who is working on it
 * @property responsibleOrgUnitId the same unit by id, used to preselect the Herstellung's book-in
 * @property handle the in-game contact for this order, required by every rewriting write
 * @property comment the requester's note, or `null`
 * @property materials the material lines
 * @property items the item lines, for an order of type `ITEM`
 * @property handovers what material has already been handed over
 * @property itemHandovers what finished items have been handed over
 * @property assignees who is on it
 * @property createdAt when it was raised, in UTC
 * @property version the order's optimistic lock, echoed by the status write
 * @property redacted whether the server removed parts of this order for the caller
 *   (REQ-ORDERS-023)
 */
data class JobOrder(
    val id: String,
    val displayId: String,
    val status: JobOrderStatus,
    val rawStatus: String?,
    val priority: Int?,
    val type: String?,
    val requestingOrgUnit: String?,
    val requestingOrgUnitId: String?,
    val responsibleOrgUnit: String?,
    val responsibleOrgUnitId: String?,
    val handle: String?,
    val comment: String?,
    val materials: List<JobOrderMaterial>,
    val items: List<JobOrderItem>,
    val handovers: List<JobOrderHandover>,
    val itemHandovers: List<JobOrderItemHandover> = emptyList(),
    val assignees: List<JobOrderAssignee>,
    val createdAt: Instant?,
    val version: Long?,
    val redacted: Boolean,
    val canEdit: Boolean? = null,
)

/**
 * How much of one material line has actually changed hands: the sum of every handover item naming
 * it.
 *
 * @receiver the Auftrag.
 * @param materialId which line.
 * @return the delivered amount, `0.0` when nothing has.
 */
fun JobOrder.krtHandedOver(materialId: String?): Double {
    if (materialId == null) {
        return 0.0
    }
    return handovers.sumOf { handover ->
        handover.lines.filter { it.materialId == materialId }.sumOf { it.amount }
    }
}

/**
 * How much of one ordered item the Auftrag already holds as stock, behind the per-sub-assembly
 * availability chip.
 *
 * @property gameItemId which item.
 * @property name what it is called.
 * @property ordered how many the line asks for.
 * @property manufactured how many have been built.
 * @property allocated how many whole units are earmarked to this Auftrag.
 */
data class JobOrderItemStock(
    val gameItemId: String,
    val name: String,
    val ordered: Int,
    val manufactured: Int,
    val allocated: Long,
) {
    /** How many are still missing from the earmark, or `0` when it is covered. */
    val missing: Int
        get() = (ordered - allocated).coerceAtLeast(0L).toInt()
}

/**
 * One page of the queue.
 *
 * [Page.rows] holds the rows on this page.
 */
typealias JobOrderPage = Page<JobOrder>

/**
 * One material line on an order being raised.
 *
 * @property materialId which material.
 * @property materialName what to show for it, so a filled line survives the picker being reset.
 * @property amount how much, in the material's own unit.
 * @property minQuality the minimum quality, or `null` for „keine".
 */
data class JobOrderDraftLine(
    val materialId: String,
    val materialName: String,
    val amount: Double,
    val minQuality: Int? = null,
)

/**
 * An order about to be raised or edited.
 *
 * @property responsibleOrgUnitId who processes it; must be profit-eligible.
 * @property requestingOrgUnitId who it is for; any active unit.
 * @property handle the contact handle in the game.
 * @property comment free text, or `null`.
 * @property lines the materials wanted; never empty.
 * @property version the order's optimistic lock on an edit, `null` when it raises a new order.
 */
data class JobOrderDraft(
    val responsibleOrgUnitId: String,
    val requestingOrgUnitId: String,
    val handle: String,
    val comment: String?,
    val lines: List<JobOrderDraftLine>,
    val version: Long? = null,
)

/**
 * What one material search turned up.
 *
 * @property rows id-to-name pairs, in the server's order.
 * @property more whether the server holds further matches this page does not carry. The picker
 *   says so rather than pretending the list is the whole answer (ADR-0104).
 */
data class MaterialMatches(
    val rows: List<Pair<String, String>>,
    val more: Boolean,
)

/**
 * One line of an item order, asked for by blueprint; the server expands it into materials.
 *
 * @property gameItemId which finished item.
 * @property blueprintId which blueprint of it.
 * @property amount how many, greater than zero.
 */
data class JobOrderItemDraftLine(
    val gameItemId: String,
    val blueprintId: String,
    val amount: Int,
)

/**
 * An item order about to be raised or edited; the server derives each line's materials from its
 * blueprint.
 *
 * @property responsibleOrgUnitId who processes it; must be profit-eligible.
 * @property requestingOrgUnitId who it is for; any active unit.
 * @property handle the contact handle in the game.
 * @property comment free text, or `null`.
 * @property lines the items wanted; never empty.
 * @property version the order's optimistic lock on an edit, `null` when it raises a new one.
 */
data class JobOrderItemDraft(
    val responsibleOrgUnitId: String,
    val requestingOrgUnitId: String,
    val handle: String,
    val comment: String?,
    val lines: List<JobOrderItemDraftLine>,
    val version: Long? = null,
)

/** Raising a new material order, and the picker behind its lines. */
interface JobOrderCreateSource {
    /**
     * Searches the materials that may be ordered.
     *
     * @param query what the member typed.
     * @return the matches, or the classified failure.
     */
    suspend fun searchMaterials(query: String): ApiResult<MaterialMatches>

    /**
     * Raises the order.
     *
     * @param draft what to raise.
     * @return the new order's id, or the classified failure.
     */
    suspend fun create(draft: JobOrderDraft): ApiResult<String>

    /**
     * Rewrites a material order in full as a Logistician, replacing the details and the whole
     * material list.
     *
     * @param orderId the Auftrag.
     * @param draft what it should become, carrying the version it was read at.
     * @return nothing on success, or the classified failure; `409` when somebody saved first.
     */
    suspend fun update(
        orderId: String,
        draft: JobOrderDraft,
    ): ApiResult<Unit>

    /**
     * The requester's own edit of a material order (REQ-ORDERS-023).
     *
     * Needs no Logistician role; the server keeps the stored unit ids and handle. Refused with a 400
     * once anything on the order has been handed over.
     *
     * @param orderId the Auftrag.
     * @param draft what it should become, carrying the version it was read at.
     * @return nothing on success, or the classified failure.
     */
    suspend fun updateAsRequester(
        orderId: String,
        draft: JobOrderDraft,
    ): ApiResult<Unit>

    /**
     * Searches the finished items that may be ordered.
     *
     * @param query what the member typed.
     * @return id-to-name pairs, in the server's order, or the classified failure.
     */
    suspend fun searchItems(query: String): ApiResult<List<Pair<String, String>>>

    /**
     * Reads the blueprints that build one item; an item with none cannot be ordered.
     *
     * @param gameItemId which item.
     * @return id-to-name pairs, or the classified failure.
     */
    suspend fun blueprintsFor(gameItemId: String): ApiResult<List<Pair<String, String>>>

    /**
     * Raises an item order.
     *
     * @param draft what to raise.
     * @return the new order's id, or the classified failure.
     */
    suspend fun createItems(draft: JobOrderItemDraft): ApiResult<String>

    /**
     * Rewrites an item order's lines, re-deriving every material from each line's blueprint.
     *
     * The server withdraws claims the new lines no longer require, and refuses with a 400 once the
     * order has any item handover.
     *
     * @param orderId the Auftrag.
     * @param draft what it should become, carrying the version it was read at.
     * @return nothing on success, or the classified failure.
     */
    suspend fun updateItems(
        orderId: String,
        draft: JobOrderItemDraft,
    ): ApiResult<Unit>
}

/**
 * The job-order reads, as a seam.
 */
interface JobOrderSource {
    /**
     * Reads one page of the queue.
     *
     * @param statuses which statuses to include; empty means every status the caller may see.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun queue(
        statuses: Set<JobOrderStatus> = emptySet(),
        page: Int = 0,
        pageSize: Int = JobOrderRepository.DEFAULT_PAGE_SIZE,
        squadronIds: Set<String> = emptySet(),
    ): ApiResult<JobOrderPage>

    /**
     * Reads one order in full.
     *
     * @param id the order's id.
     * @return the order, or a failure.
     */
    suspend fun detail(id: String): ApiResult<JobOrder>

    /**
     * Reads the ages at which the queue starts colouring an order.
     *
     * @return the configured thresholds, or the seeded defaults when the settings cannot be read;
     *   never a failure.
     */
    suspend fun ageThresholds(): JobOrderAgeThresholds

    /**
     * Reads the game-item stock earmarked to one Auftrag, grouped per item.
     *
     * @param id the Auftrag.
     * @return one entry per ordered item, or the classified failure.
     */
    suspend fun itemStock(id: String): ApiResult<List<JobOrderItemStock>>

    /**
     * Puts a member on the order, or takes them off it; the app only passes the caller's own id.
     *
     * @param id the order.
     * @param userId the member.
     * @param assigned whether they should end up on it.
     * @return the refreshed order, or the classified failure.
     */
    suspend fun setAssigned(
        id: String,
        userId: String,
        assigned: Boolean,
    ): ApiResult<JobOrder>

    /**
     * Writes or clears one assignee's note.
     *
     * @param id the order.
     * @param userId whose note.
     * @param note the new text, or `null` to clear it.
     * @param version the **assignee edge's** version, echoed from the read.
     * @return the refreshed order, or the classified failure.
     */
    suspend fun setAssigneeNote(
        id: String,
        userId: String,
        note: String?,
        version: Long?,
    ): ApiResult<JobOrder>

    /**
     * Moves the order to another place in the queue.
     *
     * The server shifts every other order to keep the sequence contiguous, so the caller reloads the
     * queue.
     *
     * @param id which order.
     * @param priority the position it should take; 1 is the front.
     * @return the reordered order, or the classified failure; `Forbidden` when the caller is not a
     *   Logistician for it.
     */
    suspend fun setPriority(
        id: String,
        priority: Int,
    ): ApiResult<JobOrder>

    /**
     * Moves the order to another status.
     *
     * @param id the order.
     * @param status where it should stand.
     * @param version the order's version, echoed from the read.
     * @return the refreshed order, or the classified failure; `403` when the caller holds no grant for
     *   this order.
     */
    suspend fun setStatus(
        id: String,
        status: JobOrderStatus,
        version: Long?,
    ): ApiResult<JobOrder>
}

/**
 * Reads job orders from the backend.
 *
 * @property reader performs the calls and classifies their failures
 */
class JobOrderRepository(
    private val reader: ApiReader,
) : JobOrderSource,
    JobOrderCreateSource {
    /**
     * The operator's age thresholds once read, so they are fetched once per process.
     */
    @Volatile
    private var cachedThresholds: JobOrderAgeThresholds? = null

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
     * Reads one page of the queue; the org scope follows from memberships and the active-org-unit
     * header.
     *
     * @param statuses which statuses to include.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @param squadronIds narrow to these units; empty means every unit the pin admits.
     * @return the page, or the classified failure.
     */
    override suspend fun queue(
        statuses: Set<JobOrderStatus>,
        page: Int,
        pageSize: Int,
        squadronIds: Set<String>,
    ): ApiResult<JobOrderPage> {
        val params =
            buildList {
                statuses.filter { it != JobOrderStatus.UNKNOWN }
                    .forEach { add(STATUS_PARAM to it.name) }
                squadronIds.forEach { add(SQUADRON_PARAM to it) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
            }
        return reader.get(QUEUE_PATH, params, PageResponseJobOrderDto.serializer())
            .map { it.toModel(page) }
    }

    /**
     * Reads the operator's age thresholds, once per process.
     *
     * Never fails: a missing, unreadable or non-numeric value falls back to the seeded defaults.
     *
     * @return the thresholds.
     */
    override suspend fun searchMaterials(query: String): ApiResult<MaterialMatches> =
        when (
            val result =
                reader.get(
                    MATERIALS_PATH,
                    listOf(
                        SEARCH_PARAM to query.trim(),
                        JOB_ORDER_ONLY_PARAM to "true",
                        PAGE_PARAM to "0",
                        SIZE_PARAM to PICKER_PAGE_SIZE.toString(),
                    ),
                    PageResponseMaterialDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                val rows = result.value.content.orEmpty().mapNotNull { row -> row.id?.let { it to row.name.orEmpty() } }
                ApiResult.Success(
                    MaterialMatches(
                        rows = rows,
                        more = (result.value.totalElements ?: 0L) > rows.size.toLong(),
                    ),
                )
            }
        }

    override suspend fun updateItems(
        orderId: String,
        draft: JobOrderItemDraft,
    ): ApiResult<Unit> =
        reader.putAccepted(
            "$QUEUE_PATH/$orderId/items",
            draft.krtToWire(),
            CreateJobOrderItemRequestDto.serializer(),
        )

    override suspend fun update(
        orderId: String,
        draft: JobOrderDraft,
    ): ApiResult<Unit> = reader.putAccepted("$QUEUE_PATH/$orderId", draft.krtToWire(), CreateJobOrderDto.serializer())

    override suspend fun updateAsRequester(
        orderId: String,
        draft: JobOrderDraft,
    ): ApiResult<Unit> =
        reader.putAccepted(
            "$QUEUE_PATH/$orderId/requested",
            draft.krtToWire(),
            CreateJobOrderDto.serializer(),
        )

    override suspend fun create(draft: JobOrderDraft): ApiResult<String> {
        val dto = draft.krtToWire()
        return when (
            val result =
                reader.post(QUEUE_PATH, dto, CreateJobOrderDto.serializer(), JobOrderDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.id?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_CREATED))
            }
        }
    }

    override suspend fun searchItems(query: String): ApiResult<List<Pair<String, String>>> =
        when (
            val result =
                reader.get(
                    ITEM_CATALOG_PATH,
                    listOf(
                        SEARCH_PARAM to query.trim(),
                        PAGE_PARAM to "0",
                        SIZE_PARAM to PICKER_PAGE_SIZE.toString(),
                    ),
                    PageResponseGameItemReferenceDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.content.orEmpty().mapNotNull { row ->
                        row.id?.let { it to row.name.orEmpty() }
                    },
                )
            }
        }

    override suspend fun blueprintsFor(gameItemId: String): ApiResult<List<Pair<String, String>>> =
        when (
            val result =
                reader.get(
                    "$ITEM_CATALOG_PATH/$gameItemId/blueprints",
                    ListSerializer(BlueprintReferenceDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { row ->
                        row.id?.let { it to (row.outputName ?: row.scwikiKey ?: it) }
                    },
                )
            }
        }

    override suspend fun createItems(draft: JobOrderItemDraft): ApiResult<String> {
        val dto =
            CreateJobOrderItemRequestDto(
                responsibleOrgUnitId = draft.responsibleOrgUnitId,
                requestingOrgUnitId = draft.requestingOrgUnitId,
                handle = draft.handle,
                comment = draft.comment?.takeIf { it.isNotBlank() },
                items =
                    draft.lines.map {
                        CreateJobOrderItemLineDto(
                            gameItemId = it.gameItemId,
                            blueprintId = it.blueprintId,
                            amount = it.amount,
                        )
                    },
            )
        return when (
            val result =
                reader.post(
                    ITEMS_PATH,
                    dto,
                    CreateJobOrderItemRequestDto.serializer(),
                    JobOrderDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.id?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_CREATED))
            }
        }
    }

    override suspend fun ageThresholds(): JobOrderAgeThresholds {
        cachedThresholds?.let { return it }
        val resolved =
            JobOrderAgeThresholds(
                yellowDays =
                    settingDays(JobOrderAgeThresholds.KEY_YELLOW_DAYS)
                        ?: JobOrderAgeThresholds.DEFAULT_YELLOW_DAYS,
                redDays =
                    settingDays(JobOrderAgeThresholds.KEY_RED_DAYS)
                        ?: JobOrderAgeThresholds.DEFAULT_RED_DAYS,
            )
        cachedThresholds = resolved
        return resolved
    }

    /**
     * Reads one system setting as a day count.
     *
     * @param key the setting key.
     * @return the value, or `null` when the read failed or the value is not a positive number —
     *   both of which the caller answers with the default rather than with an error.
     */
    private suspend fun settingDays(key: String): Long? =
        when (val result = reader.get(settingPath(key), SystemSettingDto.serializer())) {
            is ApiResult.Failure -> {
                KrtLog.d(LOG_TAG) { "age threshold $key unreadable, using the default" }
                null
            }

            is ApiResult.Success -> {
                result.value.value.trim().toLongOrNull()?.takeIf { it > 0 }
            }
        }

    /**
     * Reads one order.
     *
     * @param id the order's id.
     * @return the order, or the classified failure.
     */
    override suspend fun itemStock(id: String): ApiResult<List<JobOrderItemStock>> =
        when (
            val result =
                reader.get(
                    "$QUEUE_PATH/$id/item-stock",
                    ListSerializer(JobOrderItemStockGroupDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { group ->
                        val gameItemId = group.gameItem?.id ?: return@mapNotNull null
                        JobOrderItemStock(
                            gameItemId = gameItemId,
                            name = group.gameItem?.name.orEmpty(),
                            ordered = group.orderedAmount ?: 0,
                            manufactured = group.manufacturedAmount ?: 0,
                            allocated = group.allocatedTotal ?: 0L,
                        )
                    },
                )
            }
        }

    override suspend fun detail(id: String): ApiResult<JobOrder> =
        when (val result = reader.get(orderPath(id), JobOrderDto.serializer())) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                val order = result.value.toModel()
                if (order == null) {
                    ApiResult.Failure(ApiError.NotFound())
                } else {
                    ApiResult.Success(order)
                }
            }
        }

    override suspend fun setAssigned(
        id: String,
        userId: String,
        assigned: Boolean,
    ): ApiResult<JobOrder> {
        val path = assigneePath(id, userId)
        return refreshed(
            if (assigned) {
                reader.post(path, JobOrderDto.serializer())
            } else {
                reader.delete(path, JobOrderDto.serializer())
            },
        )
    }

    override suspend fun setAssigneeNote(
        id: String,
        userId: String,
        note: String?,
        version: Long?,
    ): ApiResult<JobOrder> {
        val path = assigneePath(id, userId) + "/note"
        return refreshed(
            if (note == null) {
                reader.delete(
                    path,
                    version?.let { listOf(VERSION_PARAM to it.toString()) }.orEmpty(),
                    JobOrderDto.serializer(),
                )
            } else {
                reader.put(
                    path,
                    AssigneeNoteRequest(note = note, version = version),
                    AssigneeNoteRequest.serializer(),
                    JobOrderDto.serializer(),
                )
            },
        )
    }

    override suspend fun setPriority(
        id: String,
        priority: Int,
    ): ApiResult<JobOrder> =
        refreshed(
            reader.put(
                orderPath(id) + "/priority?priority=" + priority,
                JobOrderDto.serializer(),
            ),
        )

    override suspend fun setStatus(
        id: String,
        status: JobOrderStatus,
        version: Long?,
    ): ApiResult<JobOrder> {
        val wire = status.toWire() ?: return ApiResult.Failure(ApiError.Validation())
        return refreshed(
            reader.put(
                orderPath(id) + "/status",
                UpdateJobOrderStatusDto(status = wire, version = version ?: 0L),
                UpdateJobOrderStatusDto.serializer(),
                JobOrderDto.serializer(),
            ),
        )
    }

    /**
     * Turns a write's answer into the refreshed order the screen redraws from.
     *
     * @param result what the write returned.
     * @return the order, or the failure, including an answer without an id.
     */
    private fun refreshed(result: ApiResult<JobOrderDto>): ApiResult<JobOrder> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.NotFound())
            }
        }

    companion object {
        /** Rows per page. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /** Log subsystem. A comment is member input and never reaches the log. */
        private const val LOG_TAG = "orders"

        private const val QUEUE_PATH = "/api/v1/orders"

        /**
         * The picker behind a draft line.
         *
         * `search` with `jobOrderOnly` rather than `/materials/job-order`: the latter answers the
         * whole orderable catalogue in one unbounded list, which is a page the phone does not need
         * and a path the API vhost would have to be opened for. This one is already reachable.
         */
        private const val MATERIALS_PATH = "/api/v1/materials/search"
        private const val SEARCH_PARAM = "search"
        private const val JOB_ORDER_ONLY_PARAM = "jobOrderOnly"

        /** How many matches one search offers before it says there are more. */
        private const val PICKER_PAGE_SIZE = 25

        /** What a successful create answers with; reported when its body names no order. */
        private const val HTTP_CREATED = 201

        /** The finished items that may be ordered, and each one's blueprints. */
        private const val ITEM_CATALOG_PATH = "/api/v1/orders/item-catalog"

        /** Where an item order is raised. */
        private const val ITEMS_PATH = "/api/v1/orders/items"
        private const val STATUS_PARAM = "status"

        /**
         * Narrows the queue to one or more units.
         *
         * Not the same thing as the org pin: the pin decides what the whole app is showing, this
         * decides what **this list** shows within it. The web offers both, the app only the pin,
         * so a member on „Alle Org-Einheiten" could not look at one squadron's orders alone.
         */
        private const val SQUADRON_PARAM = "squadronId"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val VERSION_PARAM = "version"

        /**
         * One order's path.
         *
         * @param id the order's id.
         * @return the path.
         */
        private fun orderPath(id: String) = "/api/v1/orders/$id"

        /**
         * Path of one system setting.
         *
         * @param key the setting key.
         * @return the endpoint path.
         */
        private fun settingPath(key: String) = "/api/v1/settings/$key"

        /**
         * One member's edge on one order.
         *
         * @param id the order's id.
         * @param userId the member's id.
         * @return the path.
         */
        private fun assigneePath(
            id: String,
            userId: String,
        ) = "${orderPath(id)}/assignees/$userId"
    }
}

/**
 * Maps the app's status onto the wire enum.
 *
 * @return the wire constant, or `null` for [JobOrderStatus.UNKNOWN].
 */
private fun JobOrderStatus.toWire(): UpdateJobOrderStatusDto.Status? =
    when (this) {
        JobOrderStatus.OPEN -> UpdateJobOrderStatusDto.Status.OPEN
        JobOrderStatus.IN_PROGRESS -> UpdateJobOrderStatusDto.Status.IN_PROGRESS
        JobOrderStatus.REJECTED -> UpdateJobOrderStatusDto.Status.REJECTED
        JobOrderStatus.COMPLETED -> UpdateJobOrderStatusDto.Status.COMPLETED
        JobOrderStatus.UNKNOWN -> null
    }

/**
 * Maps a page of orders onto the model.
 *
 * @param page the page index that was requested.
 * @return the page, without rows the server sent without an id.
 */
private fun PageResponseJobOrderDto.toModel(page: Int): JobOrderPage =
    JobOrderPage(
        rows = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one order onto the model.
 *
 * @return the order, or `null` when it has no id.
 */
private fun JobOrderDto.toModel(): JobOrder? {
    val rowId = id ?: return null
    return JobOrder(
        id = rowId,
        displayId = displayId?.toString().orEmpty(),
        status = JobOrderStatus.from(status?.value),
        rawStatus = status?.value,
        priority = priority,
        type = type?.value,
        requestingOrgUnit = requestingOrgUnit?.name,
        requestingOrgUnitId = requestingOrgUnit?.id,
        responsibleOrgUnit = responsibleOrgUnit?.name,
        responsibleOrgUnitId = responsibleOrgUnit?.id,
        handle = handle?.trim()?.takeIf { it.isNotEmpty() },
        comment = comment?.trim()?.takeIf { it.isNotEmpty() },
        materials = materials.orEmpty().map { it.toModel() },
        items = items.orEmpty().map { it.toModel() },
        handovers = handovers.orEmpty().mapNotNull { it.krtToModel() },
        itemHandovers = itemHandovers.orEmpty().mapNotNull { it.krtToModel() },
        assignees =
            assignees.orEmpty().mapNotNull { assignee ->
                assignee.user?.id?.let {
                    JobOrderAssignee(
                        userId = it,
                        name = assignee.user?.effectiveName,
                        note = assignee.note?.trim()?.takeIf { note -> note.isNotEmpty() },
                        version = assignee.version,
                    )
                }
            },
        createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        version = version,
        redacted = redacted == true,
        canEdit = canEdit,
    )
}

/**
 * Maps one item line onto the model; the three counts default to zero because the server omits
 * them at zero.
 *
 * @receiver the wire line.
 * @return the model line.
 */
private fun JobOrderItemDto.toModel(): JobOrderItem =
    JobOrderItem(
        id = id,
        gameItemId = gameItem?.id,
        name = gameItem?.name,
        blueprintName = blueprint?.outputName ?: blueprint?.scwikiKey,
        blueprintId = blueprint?.id,
        amount = amount ?: 0,
        manufactured = manufacturedAmount ?: 0,
        delivered = deliveredAmount ?: 0,
        blueprintStale = blueprintStale == true,
        requirements =
            materials
                .orEmpty()
                .mapNotNull { line ->
                    val materialId = line.material?.id ?: return@mapNotNull null
                    JobOrderItemRequirement(
                        materialId = materialId,
                        name = line.material?.name.orEmpty(),
                        unit = line.material?.quantityType,
                        requiredTotal = line.requiredQuantity ?: 0.0,
                    )
                }
                .groupBy { it.materialId }
                .map { (_, rows) -> rows.first().copy(requiredTotal = rows.sumOf { it.requiredTotal }) },
        version = version,
        parentItemId = parentItemId,
    )

/**
 * The item order as the create and the edit both take it.
 *
 * @receiver what the form holds.
 * @return the payload.
 */
private fun JobOrderItemDraft.krtToWire(): CreateJobOrderItemRequestDto =
    CreateJobOrderItemRequestDto(
        responsibleOrgUnitId = responsibleOrgUnitId,
        requestingOrgUnitId = requestingOrgUnitId,
        handle = handle,
        comment = comment?.takeIf { it.isNotBlank() },
        items =
            lines.map {
                CreateJobOrderItemLineDto(
                    gameItemId = it.gameItemId,
                    blueprintId = it.blueprintId,
                    amount = it.amount,
                )
            },
        version = version,
    )

/**
 * The order in the shape the create and both edit endpoints take.
 *
 * @receiver what the form holds.
 * @return the payload.
 */
private fun JobOrderDraft.krtToWire(): CreateJobOrderDto =
    CreateJobOrderDto(
        responsibleOrgUnitId = responsibleOrgUnitId,
        requestingOrgUnitId = requestingOrgUnitId,
        handle = handle,
        comment = comment?.takeIf { it.isNotBlank() },
        materials =
            lines.map {
                CreateJobOrderMaterialDto(
                    materialId = it.materialId,
                    amount = it.amount,
                    minQuality = it.minQuality,
                )
            },
        version = version,
    )

/**
 * Maps one recorded material handover onto the model.
 *
 * @receiver the server's record.
 * @return it, or `null` for a row without an id — nothing on screen can address one.
 */
private fun JobOrderHandoverDto.krtToModel(): JobOrderHandover? {
    val handoverId = id ?: return null
    return JobOrderHandover(
        id = handoverId,
        recipient = recipientHandle,
        executor = executingUser?.effectiveName,
        at = handoverTime?.let { time -> runCatching { Instant.parse(time) }.getOrNull() },
        lines =
            items.orEmpty().map { line ->
                JobOrderHandoverLine(materialId = line.material?.id, amount = line.amount ?: 0.0)
            },
    )
}

/**
 * Maps one recorded item handover onto the model.
 *
 * @receiver the server's record.
 * @return it, or `null` for a row without an id.
 */
private fun JobOrderItemHandoverDto.krtToModel(): JobOrderItemHandover? {
    val handoverId = id ?: return null
    return JobOrderItemHandover(
        id = handoverId,
        recipient = recipientHandle,
        executor = executingUser?.effectiveName,
        at = handoverTime?.let { time -> runCatching { Instant.parse(time) }.getOrNull() },
        lines =
            propertyEntries.orEmpty().map { line ->
                JobOrderItemHandoverLine(
                    itemId = line.jobOrderItemId,
                    itemName = line.gameItem?.name.orEmpty(),
                    amount = line.amount ?: 0,
                )
            },
    )
}

/**
 * Maps one material line onto the model.
 *
 * @return the line.
 */
private fun JobOrderMaterialDto.toModel(): JobOrderMaterial =
    JobOrderMaterial(
        materialId = material?.id,
        name = material?.name.orEmpty(),
        needed = amount?.toPlainString(),
        inStock = currentStock?.toPlainString(),
        claimCount = claims.orEmpty().size,
        claimedAmount =
            claims
                .orEmpty()
                .mapNotNull { it.amount }
                .takeIf { it.isNotEmpty() }
                ?.sum()
                ?.let { java.math.BigDecimal(it.toString()).stripTrailingZeros().toPlainString() },
        open = openAmount?.toPlainString(),
        unit = material?.quantityType,
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

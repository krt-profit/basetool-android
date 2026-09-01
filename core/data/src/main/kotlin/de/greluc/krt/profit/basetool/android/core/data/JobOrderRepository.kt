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
 * `requiredTotal` is the demand of the **whole line**, not of a single unit: the server sends the
 * line's own figure and the web divides it by the line's count to price a partial run. Keeping the
 * server's number rather than a per-unit one avoids compounding a rounding error over every unit.
 *
 * @property materialId which material — the consumption plan is addressed by it.
 * @property name what it is called.
 * @property unit `SCU` or `PIECE`; a piece count is never booked in fractions.
 * @property requiredTotal how much the whole line needs.
 */
data class JobOrderItemRequirement(
    val materialId: String,
    val name: String,
    val unit: String?,
    val requiredTotal: Double,
)

/**
 * One item line of an order.
 *
 * An item order asks for finished items and the server derives their materials, so the figures here
 * are counts and not quantities: how many were asked for, how many have been built, how many have
 * been handed over.
 *
 * @property id the line's id
 * @property name the item's name
 * @property gameItemId which finished item this line orders — the edit form's picker is filled from
 *   it, and the write is addressed by it
 * @property blueprintName which blueprint it is built from, or `null` when the server named none
 * @property blueprintId that blueprint by id; the write names the variant, and a name cannot
 * @property amount how many were asked for
 * @property manufactured how many have been built
 * @property delivered how many have been handed over
 * @property blueprintStale whether the blueprint has changed since the order was raised, which the
 *   web flags because the derived material demand may no longer match what will be built
 * @property requirements what one whole line of this item consumes, as the server derived it from
 *   the blueprint. Carried because the Herstellung has to state a demand per material and cover it
 *   exactly; without it the booking would be a number typed against nothing
 * @property version the line's **own** optimistic lock. The Herstellung echoes it, not the order's:
 *   two members booking production on two different lines of the same Auftrag must not collide
 * @property parentItemId the line this one is a sub-assembly of, or `null` for a top-level line.
 *   The server models sub-assemblies as **real ordered lines with a parent**, which is what lets the
 *   design's Unterbaugruppen-Baum be drawn from the order alone
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
     * How many are built and not yet handed over — the cap on one Übergabe.
     *
     * **Not `amount - delivered`.** A unit can only be handed over once it has been manufactured
     * (REQ-ORDERS-025), and `JobOrderItemHandoverService` refuses anything above this with a 400.
     * The obvious subtraction would offer a count the server rejects, and the member would have no
     * way to see why.
     */
    val deliverable: Int
        get() = (manufactured - delivered).coerceAtLeast(0)

    /**
     * How far along this line is, between 0 and 1, or `null` when nothing was asked for.
     *
     * Built over asked-for, the same shape as a material line's bar. A count of zero yields `null`
     * rather than a full bar: nothing was asked for, so nothing can be complete.
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
 * **No "Zugesagt" figure.** The wire carries `claims` as a *list* of individual promises, not a
 * total, and adding them up here would be this client computing a quantity a member reads. The
 * server's own `openAmount` already accounts for them, and that is what the screen shows instead —
 * the design's "noch offen".
 *
 * @property materialId which material — the handover's stock picker is addressed by it, so a line
 *   without one cannot be handed over from the app
 * @property name the material's name
 * @property needed how much the order asks for, as the server rendered it
 * @property inStock how much the responsible unit already holds
 * @property claimCount how many separate promises exist, which is a count and not an amount
 * @property claimedAmount how much those promises add up to, or `null` when there are none —
 *   the figure artboard 10-2's position card states („Zugesagt: 300 SCU"), summed from the
 *   claims because the server sends one amount each and no total
 * @property open how much is still missing, as the server computed it
 * @property unit `SCU` or `PIECE` — the material's own unit, `null` when the server named none.
 *   Carried because a figure without it is a figure a member has to guess at: the screen printed
 *   „SCU" over every line, so an order for eight *pieces* read as eight SCU. The web switches per
 *   material and even splits its demand band into two numbers, because the two units cannot be
 *   added.
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
     * How far along this line is, between 0 and 1, or `null` when it cannot be told.
     *
     * Computed from stock over need because the server sends no percentage — this is a **bar
     * length**, not a figure the screen states, which is why deriving it here is not the
     * money-arithmetic the rest of this app refuses. A need of zero yields `null` rather than a
     * full bar — nothing was asked for, so nothing can be complete — and so does a missing stock
     * figure, because an empty bar would claim "none in stock" where the server stated nothing.
     */
    val progress: Float?
        get() {
            val need = needed?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
            // No stock figure means the server did not state one. Drawing an empty bar would say
            // "none in stock", which is a different claim from "not stated".
            val have = inStock?.toDoubleOrNull() ?: return null
            return (have / need).coerceIn(0.0, 1.0).toFloat()
        }
}

/**
 * One material inside a handover.
 *
 * Carried because it is the **only** honest source of „how much of this line has actually changed
 * hands". The obvious alternative — `amount - openAmount` — measures something else: the server's
 * `openRemaining` is `required - claimed` (`MaterialClaimService`), so it counts promises, and a
 * screen that showed it as delivered would overstate every line somebody had merely claimed.
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
 * One item handover already recorded against an order.
 *
 * A **separate** record from the material handover and not a variant of it: the server keeps them
 * on two endpoints, counts pieces rather than quantities, and moves a different figure.
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
 * @property userId who they are, by id — a name cannot be compared against the caller's own, and
 *   the two writes on this edge address the member by id
 * @property name how they read, or `null` for a row the server did not attribute
 * @property note their own note: when they work on it, which part they take
 * @property version the edge's **own** optimistic lock. Not the order's: a note edit that echoed
 *   the order's version would 409 against any unrelated change to it, and bumping the order's
 *   would 409 everyone else's screen for a note nobody else reads
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
 * @property displayId the human-facing number the web app prefixes with `#`; the server sends it
 *   as an integer, so the `#` and any padding belong to the screen, not here
 * @property status where it stands
 * @property rawStatus the untranslated server value, for [JobOrderStatus.UNKNOWN]
 * @property priority the queue priority; lower sorts first
 * @property type `MATERIAL` or `ITEM` as the server names it
 * @property requestingOrgUnit who asked for it
 * @property requestingOrgUnitId the same unit by id — the edit form's customer picker is filled
 *   from it, and a name cannot select an option
 * @property responsibleOrgUnit who is working on it
 * @property responsibleOrgUnitId the same unit by id — the Herstellung's book-in preselects it when
 *   the owner belongs to it, which is a comparison a name cannot make
 * @property handle the in-game contact for this order — required by every write that rewrites it,
 *   so an edit form that could not read it could never submit
 * @property comment the requester's note, or `null`
 * @property materials the material lines
 * @property items the item lines, for an order of type `ITEM`
 * @property handovers what material has already been handed over
 * @property itemHandovers what finished items have — the item order's own log, which the app was
 *   leaving unread until 2026-08-29
 * @property assignees who is on it
 * @property createdAt when it was raised, in UTC
 * @property version the order's optimistic lock, echoed by the status write
 * @property redacted whether the server removed parts of this order for the caller — a requester
 *   sees their own order without what is not theirs (REQ-ORDERS-023), and the screen has to say so
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
 * How much of one material line has actually changed hands.
 *
 * The sum of every handover item naming it — never `amount - openAmount`, which counts claims.
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
 * How much of one ordered item the Auftrag already holds as stock.
 *
 * The design's availability chip per sub-assembly — „Lager" when the earmark covers what was
 * ordered, „Fehlt n" otherwise. Both figures are the server's; the app subtracts them only to say
 * how many are missing, which is a count and not money.
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
 * @property orders the rows on this page
 * @property page the zero-based page index
 * @property totalPages how many pages exist
 * @property totalElements how many orders the filter matches
 */
data class JobOrderPage(
    val orders: List<JobOrder>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

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
 * An order about to be raised.
 *
 * @property responsibleOrgUnitId who processes it; must be profit-eligible.
 * @property requestingOrgUnitId who it is for; any active unit.
 * @property handle the contact handle in the game.
 * @property comment free text, or `null`.
 * @property lines the materials wanted; never empty.
 * @property version the order's optimistic lock when this is an **edit**, `null` when it raises a
 *   new order. Both writes take the same payload; only the edit has something to collide with.
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
 * One line of an item order, in the shape the wire takes.
 *
 * An item is asked for by blueprint, not by material: the server expands the blueprint into the
 * materials it needs. Every field is required, so a half-filled line never reaches here — the form
 * refuses the submit instead.
 *
 * @property gameItemId which finished item.
 * @property blueprintId which blueprint of it; the server derives the materials from this.
 * @property amount how many, greater than zero.
 */
data class JobOrderItemDraftLine(
    val gameItemId: String,
    val blueprintId: String,
    val amount: Int,
)

/**
 * An item order about to be raised.
 *
 * The same head as a material order — the two units, the handle, the comment — and finished-item
 * lines instead of raw materials. The server derives each line's materials from its blueprint, so
 * the client sends no quantities of its own.
 *
 * @property responsibleOrgUnitId who processes it; must be profit-eligible.
 * @property requestingOrgUnitId who it is for; any active unit.
 * @property handle the contact handle in the game.
 * @property comment free text, or `null`.
 * @property lines the items wanted; never empty.
 * @property version the order's optimistic lock on an **edit**, `null` when it raises a new one.
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
     * Rewrites a material order in full — a Logistician's edit.
     *
     * `PUT /orders/{id}` takes **the same payload as the create**: the write replaces the details
     * and the whole material list rather than patching either, which is why the form is the create
     * form pre-filled rather than a second layout.
     *
     * @param orderId the Auftrag.
     * @param draft what it should become, carrying the version it was read at.
     * @return nothing on success, or the classified failure — `409` when somebody saved first.
     */
    suspend fun update(
        orderId: String,
        draft: JobOrderDraft,
    ): ApiResult<Unit>

    /**
     * The requester's own, narrower edit of a material order.
     *
     * `PUT /orders/{id}/requested` (REQ-ORDERS-023). Same payload, different gate: **no**
     * Logistician role is required — a member of the *requesting* unit may change quantities, add
     * and remove lines, and edit the comment. The server takes the two unit ids and the handle from
     * the stored order rather than from the payload, so the form draws those fields locked.
     *
     * > **Only while nothing has been delivered.** The freeze is on the **whole order**, not per
     * > line: one handover anywhere closes this path for everything (`canEditJobOrderAsRequester`),
     * > and the attempt is a 400.
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
     * Reads the blueprints that build one item.
     *
     * An item with none cannot be ordered: the server derives the materials from the blueprint, so
     * a line without one has nothing to produce.
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
     * Rewrites an item order's lines.
     *
     * `PUT /orders/{id}/items` — the same payload as the item create, replacing the ordered lines
     * and re-deriving every material from each line's blueprint. A claim whose bucket the new lines
     * no longer require is withdrawn by the server.
     *
     * > **Only while the order has no item handover.** Once anything has been handed over the
     * > server refuses with a 400: the lines are what the delivery was measured against.
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
     * On this source rather than a settings repository of its own because that is what the two
     * numbers are: a property of how this queue is read. They belong to the operator (see
     * [JobOrderAgeThresholds]).
     *
     * @return the configured thresholds, or the seeded defaults when the settings cannot be read —
     *   never a failure, because a colour is not worth an error screen over a list that loaded.
     */
    suspend fun ageThresholds(): JobOrderAgeThresholds

    /**
     * Reads the game-item stock earmarked to one Auftrag.
     *
     * `GET /orders/{id}/item-stock`, grouped per item. Open to anyone who may see the order; the
     * per-entry owners are redacted for a requesting-side viewer, and this model keeps only the
     * three counts, which are never redacted.
     *
     * @param id the Auftrag.
     * @return one entry per ordered item, or the classified failure.
     */
    suspend fun itemStock(id: String): ApiResult<List<JobOrderItemStock>>

    /**
     * Puts a member on the order, or takes them off it.
     *
     * The app only ever passes the caller's own id: assigning anyone else needs LOGISTICIAN, and
     * the app has no surface that names another member here.
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
     * The server shifts every other order to keep the sequence contiguous, so the answer is the
     * whole order rather than a confirmation — and every other row's priority has changed too,
     * which is why the caller reloads the queue rather than patching one row.
     *
     * @param id which order.
     * @param priority the position it should take; 1 is the front.
     * @return the reordered order, or the classified failure — `Forbidden` when the caller is not
     *   a Logistician for it.
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
     * @return the refreshed order, or the classified failure. `403` here is ordinary rather than
     *   exceptional: the grant is per order, so a Logistician outside this order's slice is
     *   refused exactly like a member without the grant.
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
     * The operator's age thresholds once they have been read, so the queue asks for them once.
     *
     * `@Volatile` because the queue and the detail screen can load on different dispatchers and
     * both go through [ageThresholds]; a torn read here would cost one redundant request, which is
     * harmless, but the field is cheap to make correct.
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
     * Reads one page of the queue.
     *
     * The org scope is **not** sent: which orders a member sees follows from their memberships and
     * the active-org-unit header the interceptor already applies. `squadronId` exists on this
     * endpoint and is deliberately unused — a client-side scope would be a second, weaker copy of
     * a server-side rule.
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
                // Repeated, like the statuses: the endpoint takes a multi-select, which is how the
                // web's queue narrows to one or more units while the org pin stays on „alle".
                squadronIds.forEach { add(SQUADRON_PARAM to it) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
            }
        return when (val result = reader.get(QUEUE_PATH, params, PageResponseJobOrderDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    /**
     * Reads the operator's age thresholds, once per process.
     *
     * Cached because they are an operator setting that changes about never, and the alternative is
     * two extra requests per page of a list that already made one.
     *
     * **Never fails.** A missing, unreadable or non-numeric value falls back to the same defaults
     * the schema seeds, so the worst case is that the colours match a freshly installed server
     * rather than a tuned one — which is a far better outcome than an error over a colour.
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
                        // `totalElements`, not `rows.size == PICKER_PAGE_SIZE`: a page that happens
                        // to be exactly full is not evidence of more, and a row dropped for having
                        // no id would make the size comparison lie in the other direction.
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
                    // A 201 that names no order leaves the caller with nothing to navigate to.
                    // That is a server contract break, not an empty result, so it fails rather
                    // than reporting a success the screen cannot act on. The status is the one
                    // that actually arrived — the order may well have been raised.
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
                        // The name shown is the blueprint's own output name; a blueprint the server
                        // named with neither is still pickable, because its id is what the wire
                        // wants and hiding it would make the item unorderable.
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
                    // A payload with no id is not something a detail screen can be built from.
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
                // A query parameter, not a body — that is what the endpoint takes. And no
                // `version`: the service reorders the whole queue under a pessimistic write lock,
                // so the optimistic version this app echoes everywhere else has nothing to guard
                // here. Sending one would suggest a conflict check that does not happen.
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
     * Turns a write's answer into the refreshed order.
     *
     * Every one of these writes answers with the whole order, and the screen redraws from it
     * rather than patching what it holds: the server decides the assignee order and the version,
     * and guessing at either is how two screens start disagreeing.
     *
     * @param result what the write returned.
     * @return the order, or the failure — including the answer that carries no id, which a detail
     *   screen cannot be rebuilt from.
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
 * @return the wire constant, or `null` for [JobOrderStatus.UNKNOWN]. That one exists to carry a
 *   status this build does not know, so asking the server to move an order into it is not a
 *   request that means anything — and folding it into one of the four would move the order
 *   somewhere nobody asked for.
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
        orders = content.orEmpty().mapNotNull { it.toModel() },
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
                // No id, no row: the two writes on this edge address the member by id, and a row
                // that cannot be addressed would offer actions that always fail.
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
        // `null` is read as not redacted: the flag is an addition, and treating its absence as
        // "something is missing" would put a caveat on every order an older server sends.
        redacted = redacted == true,
        // Role AND scope, as the endpoint gates it — the flag the app used to hold covered only
        // the role half and was documented as a hint for exactly that reason (REQ-SEC-047).
        canEdit = canEdit,
    )
}

/**
 * Maps one item line onto the model.
 *
 * The three counts default to zero rather than to `null`: the server omits them at zero, and a
 * screen that had to tell "none built" from "not stated" would be drawing a distinction the wire
 * does not make.
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
                // A blueprint can list the same ingredient twice, and a resource plus a bridged
                // non-craftable item can map to the same material. The server merges the demand per
                // material id; two rows sharing an id here would give the sheet two cards for one
                // material, and the second could never be reconciled.
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
 * The order as the create and both edit endpoints all take it.
 *
 * One shape for three writes because the server takes one: the update **replaces** the details and
 * the whole material list rather than patching them, which is why the edit form is the create form
 * pre-filled.
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
        // Doubles, not decimals — the server declares these quantities as doubles, so the choice
        // of precision is already made upstream and mirroring it is the honest thing to do.
        needed = amount?.toPlainString(),
        inStock = currentStock?.toPlainString(),
        claimCount = claims.orEmpty().size,
        // The artboard's position card says „Zugesagt: 300 SCU", not „2 Zusagen": what is
        // already promised is a quantity against the need. Only the count was kept, so the
        // figure the card is about could not be drawn. Summed here rather than in the UI — the
        // server sends one amount per claim and no total.
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

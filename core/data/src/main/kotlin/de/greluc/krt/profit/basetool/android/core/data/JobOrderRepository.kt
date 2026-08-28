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
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateJobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateJobOrderMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseJobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.SystemSettingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateJobOrderStatusDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
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
 * One material line of an order.
 *
 * **No "Zugesagt" figure.** The wire carries `claims` as a *list* of individual promises, not a
 * total, and adding them up here would be this client computing a quantity a member reads. The
 * server's own `openAmount` already accounts for them, and that is what the screen shows instead —
 * the design's "noch offen".
 *
 * @property name the material's name
 * @property needed how much the order asks for, as the server rendered it
 * @property inStock how much the responsible unit already holds
 * @property claimCount how many separate promises exist, which is a count and not an amount
 * @property open how much is still missing, as the server computed it
 */
data class JobOrderMaterial(
    val name: String,
    val needed: String?,
    val inStock: String?,
    val claimCount: Int,
    val open: String?,
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
 * @property responsibleOrgUnit who is working on it
 * @property comment the requester's note, or `null`
 * @property materials the material lines
 * @property handovers what has already been handed over
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
    val responsibleOrgUnit: String?,
    val comment: String?,
    val materials: List<JobOrderMaterial>,
    val handovers: List<JobOrderHandover>,
    val assignees: List<JobOrderAssignee>,
    val createdAt: Instant?,
    val version: Long?,
    val redacted: Boolean,
)

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
 */
data class JobOrderDraft(
    val responsibleOrgUnitId: String,
    val requestingOrgUnitId: String,
    val handle: String,
    val comment: String?,
    val lines: List<JobOrderDraftLine>,
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
     * @return the page, or the classified failure.
     */
    override suspend fun queue(
        statuses: Set<JobOrderStatus>,
        page: Int,
        pageSize: Int,
    ): ApiResult<JobOrderPage> {
        val params =
            buildList {
                statuses.filter { it != JobOrderStatus.UNKNOWN }
                    .forEach { add(STATUS_PARAM to it.name) }
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

    override suspend fun create(draft: JobOrderDraft): ApiResult<String> {
        val dto =
            CreateJobOrderDto(
                responsibleOrgUnitId = draft.responsibleOrgUnitId,
                requestingOrgUnitId = draft.requestingOrgUnitId,
                handle = draft.handle,
                comment = draft.comment?.takeIf { it.isNotBlank() },
                materials =
                    draft.lines.map {
                        CreateJobOrderMaterialDto(
                            materialId = it.materialId,
                            amount = it.amount,
                            minQuality = it.minQuality,
                        )
                    },
            )
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
        private const val STATUS_PARAM = "status"
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
        responsibleOrgUnit = responsibleOrgUnit?.name,
        comment = comment?.trim()?.takeIf { it.isNotEmpty() },
        materials = materials.orEmpty().map { it.toModel() },
        handovers =
            handovers.orEmpty().mapNotNull { handover ->
                handover.id?.let {
                    JobOrderHandover(
                        id = it,
                        recipient = handover.recipientHandle,
                        executor = handover.executingUser?.effectiveName,
                        at = handover.handoverTime?.let { time -> runCatching { Instant.parse(time) }.getOrNull() },
                    )
                }
            },
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
    )
}

/**
 * Maps one material line onto the model.
 *
 * @return the line.
 */
private fun JobOrderMaterialDto.toModel(): JobOrderMaterial =
    JobOrderMaterial(
        name = material?.name.orEmpty(),
        // Doubles, not decimals — the server declares these quantities as doubles, so the choice
        // of precision is already made upstream and mirroring it is the honest thing to do.
        needed = amount?.toPlainString(),
        inStock = currentStock?.toPlainString(),
        claimCount = claims.orEmpty().size,
        open = openAmount?.toPlainString(),
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

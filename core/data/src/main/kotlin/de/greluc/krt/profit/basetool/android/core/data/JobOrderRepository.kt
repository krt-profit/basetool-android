/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseJobOrderDto
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
     * full bar: nothing was asked for, so nothing can be complete.
     */
    val progress: Float?
        get() {
            val need = needed?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
            val have = inStock?.toDoubleOrNull() ?: 0.0
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
 * @property assignees who is on it, by name
 * @property createdAt when it was raised, in UTC
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
    val assignees: List<String>,
    val createdAt: Instant?,
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
}

/**
 * Reads job orders from the backend.
 *
 * @property reader performs the calls and classifies their failures
 */
class JobOrderRepository(
    private val reader: ApiReader,
) : JobOrderSource {
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
                    ApiResult.Failure(
                        de.greluc.krt.profit.basetool.android.core.network.ApiError.NotFound(),
                    )
                } else {
                    ApiResult.Success(order)
                }
            }
        }

    companion object {
        /** Rows per page. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /** Log subsystem. A comment is member input and never reaches the log. */
        private const val LOG_TAG = "orders"

        private const val QUEUE_PATH = "/api/v1/orders"
        private const val STATUS_PARAM = "status"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"

        /**
         * One order's path.
         *
         * @param id the order's id.
         * @return the path.
         */
        private fun orderPath(id: String) = "/api/v1/orders/$id"
    }
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
        assignees = assignees.orEmpty().mapNotNull { it.user?.effectiveName },
        createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
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

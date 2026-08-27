/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseRefineryOrderListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryGoodDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderStoreDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderStoreItemDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import okhttp3.OkHttpClient
import java.time.OffsetDateTime

/**
 * One material coming out of a refining run.
 *
 * **[amount] is in the member's unit, not the wire's.** The server tracks `outputQuantity` in
 * *units*, and one SCU is a hundred of them — so a run yielding 288 SCU arrives as `28800`. The web
 * app divides by a hundred to show it and the app now does the same, because the two must read
 * alike and because the booking sends this number: shipping the raw value would have created a
 * Lager entry a hundred times too large, and there is no undo for that.
 *
 * A `PIECE` material is already counted in pieces and is not divided.
 *
 * @property materialId the refined material's id; `null` when the server named no output material,
 *   which is the one case a good cannot be booked into the Lager.
 * @property materialName what to call it.
 * @property amount how much, in the material's own unit — SCU or pieces, never wire units.
 * @property unitIsPiece whether that unit is pieces rather than SCU.
 * @property quality the refining quality, or `null` when the order does not record one.
 */
data class RefineryYield(
    val materialId: String?,
    val materialName: String,
    val amount: Double,
    val unitIsPiece: Boolean,
    val quality: Int?,
)

/**
 * How the member sees an order, which is not how the server stores it.
 *
 * The server has four statuses; the screen has the three of design chapter 11 plus the cancelled
 * one. The difference is [READY]: the server does not have a "ready to collect" status at all —
 * an order stays `IN_PROGRESS` until somebody books it in — so readiness is the run's end time
 * having passed, computed here.
 */
enum class RefineryPhase {
    /** Refining; the remaining time is counted down on the device. */
    RUNNING,

    /** The run has ended and nobody has booked the yield in yet. */
    READY,

    /** Booked into the Lager. Terminal. */
    STORED,

    /** Called off. Terminal, and shown rather than hidden — it is the member's own order. */
    CANCELLED,
}

/** What the server stores, kept apart from what the member sees. */
enum class RefineryServerStatus {
    /** Created but not yet started, as far as the server is concerned. */
    OPEN,

    /** Running. The server leaves an order here until somebody books its yield in. */
    IN_PROGRESS,

    /** Booked in. */
    COMPLETED,

    /** Called off. */
    CANCELED,

    /** A status this build does not know. Treated as running, the safe direction. */
    UNKNOWN,
}

/**
 * One refining order of the member's own.
 *
 * @property id the order's id.
 * @property locationId where it runs; the Lager entries a booking creates are stamped with it.
 * @property locationName the station's name.
 * @property methodName the refining method, as the server spells it.
 * @property startedAt when the run began, as the server rendered it; `null` when not recorded.
 * @property endsAt when it ends, as the server rendered it; `null` when it cannot be computed.
 * @property status what the server stores. The member-facing phase is [phaseAt], because
 *   "ready to collect" is the end time having passed and therefore changes without any server
 *   round trip — a phase frozen at mapping time would leave a finished run reading „In Arbeit"
 *   until the screen was reloaded.
 * @property yields the goods the run produces.
 * @property oreSales the ore-sales figure recorded on the order, as the server rendered it.
 * @property profit the recorded gain or loss, as the server rendered it.
 * @property version the optimistic lock, echoed on writes that take one.
 */
data class RefineryOrder(
    val id: String,
    val locationId: String?,
    val locationName: String,
    val methodName: String,
    val startedAt: String?,
    val endsAt: String?,
    val status: RefineryServerStatus,
    val yields: List<RefineryYield>,
    val oreSales: String?,
    val profit: String?,
    val version: Long?,
) {
    /**
     * Total output across every good, in the member's units.
     *
     * A sum across mixed units is a rough figure by nature — the list row uses it to say how big a
     * run was, not to state a quantity anybody acts on.
     */
    val totalAmount: Double get() = yields.sumOf { it.amount }

    /**
     * What the member sees at [now].
     *
     * `READY` exists only here: the server keeps an order `IN_PROGRESS` until somebody books it
     * in, so "ready to collect" is the end time having passed and nothing else. An unknown or
     * unparseable end time reads as still running — the safe direction, since offering „In Lager
     * buchen" on a run that has not finished books a yield that does not exist yet.
     *
     * @param now the moment to judge against; the screen passes a clock that ticks every minute.
     * @return the phase to show.
     */
    fun phaseAt(now: OffsetDateTime): RefineryPhase =
        when (status) {
            RefineryServerStatus.COMPLETED -> RefineryPhase.STORED
            RefineryServerStatus.CANCELED -> RefineryPhase.CANCELLED
            else -> if (hasEndedBy(endsAt, now)) RefineryPhase.READY else RefineryPhase.RUNNING
        }

    /**
     * Whether „In Lager buchen" may be offered at [now].
     *
     * Three conditions, and the last is the one that is easy to miss: the run must have finished,
     * the order must carry a location to book into, **and** at least one good must name an output
     * material. A booking addresses materials by id, so an order whose goods have none would send
     * an empty item list — and the endpoint marks the order stored whatever that list contains.
     *
     * @param now the moment to judge against.
     * @return whether the action belongs on screen.
     */
    fun canStoreAt(now: OffsetDateTime): Boolean =
        phaseAt(now) == RefineryPhase.READY &&
            locationId != null &&
            yields.any { it.materialId != null }
}

/**
 * One page of the member's own orders.
 *
 * @property orders the rows on this page.
 * @property page the zero-based page index.
 * @property totalPages how many pages exist.
 * @property totalElements how many orders the filter matches on the server.
 */
data class RefineryOrderPage(
    val orders: List<RefineryOrder>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/** The Raffinerie reads and the one write the app offers, as a seam. */
interface RefinerySource {
    /**
     * Reads one page of the member's own orders.
     *
     * @param statuses which server statuses to ask for; empty means every one of them.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    suspend fun myOrders(
        statuses: Set<RefineryServerStatus> = emptySet(),
        page: Int = 0,
        pageSize: Int = RefineryRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<RefineryOrderPage>

    /**
     * Reads one order in full.
     *
     * @param id the order's id.
     * @return the order, or the classified failure.
     */
    suspend fun detail(id: String): ApiResult<RefineryOrder>

    /**
     * Books an order's yield into the Lager and marks the order stored.
     *
     * @param order the order to book, already loaded — the payload is derived entirely from it.
     * @return success, or the classified failure.
     */
    suspend fun store(order: RefineryOrder): ApiResult<Unit>
}

/**
 * Maps one line onto the item the server books.
 *
 * @return the item, or `null` when the line names no location or carries no readable amount.
 */
private fun RefineryStoreLine.toItem(): RefineryOrderStoreItemDto? {
    val where = locationId
    val figure = parseTypedAmount(amount)?.takeIf { it > 0 }
    if (where == null || figure == null) {
        return null
    }
    return RefineryOrderStoreItemDto(
        materialId = materialId,
        locationId = where,
        quality = quality,
        // SCU, not wire units: the endpoint reads this as the member's own figure and writes it
        // into the Lager as-is.
        amount = figure,
        userId = userId,
        // The server refuses the pair; the form must not send it either, or the 400 arrives as a
        // mystery rather than as the rule it is.
        jobOrderId = jobOrderId?.takeIf { !personal },
        note = note.trim().takeIf { it.isNotEmpty() },
        owningOrgUnitId = owningOrgUnitId,
        personal = personal,
    )
}

/**
 * One material of a finished run, on its way into the Lager.
 *
 * @property materialId which material — fixed by the run, never chosen here.
 * @property materialName what to show.
 * @property computed what the run calculated, in SCU.
 * @property amount what is actually being booked, in SCU. Pre-filled with [computed] and meant to be
 *   overridden: that is the whole reason this form exists.
 * @property quality the grade, 0–1000.
 * @property locationId where it goes. Mandatory; pre-filled with the order's refinery.
 * @property personal whether it becomes the member's own entry rather than the unit's.
 * @property jobOrderId the Auftrag to earmark it against, or `null`. **Excludes [personal]** — the
 *   server answers 400 for the pair, and a personal line never inherits the order's mission earmark
 *   either.
 * @property note free text, at most 1000 characters.
 * @property userId who receives it, or `null` for the caller.
 * @property owningOrgUnitId which unit to book into. The server requires it when the receiver holds
 *   more than one membership; pre-filled with the order's unit.
 */
data class RefineryStoreLine(
    val materialId: String,
    val materialName: String,
    val computed: Double,
    val amount: String,
    val quality: Int,
    val locationId: String?,
    val personal: Boolean = false,
    val jobOrderId: String? = null,
    val note: String = "",
    val userId: String? = null,
    val owningOrgUnitId: String? = null,
) {
    /**
     * What identifies this line among the run's others.
     *
     * **Not the material alone.** A run can yield the same material at two grades — Agricium at 733
     * and at 874 — and keying on the material makes them one line, which Compose rejects outright as
     * a duplicate list key and which would otherwise edit and acknowledge both at once.
     */
    val key: String get() = "$materialId@$quality"
}

/** How long a store note may be, as the server counts it. */
const val REFINERY_NOTE_LIMIT: Int = 1000

/**
 * Booking a finished run's materials into the Lager.
 *
 * **One call for the whole run, not one per material.** The server books whatever the call carries
 * and then marks the order completed; every later call is refused with „Refinery order is already
 * completed and stored." A per-card submit therefore loses every material after the first — which
 * is what a device showed before this was one call.
 */
interface RefineryStoreSource {
    /**
     * Books every material of a run.
     *
     * @param orderId the run.
     * @param lines what to book. A line whose amount is not a figure is refused rather than sent,
     *   because the call closes the order and there is no second chance at it.
     * @return nothing usable beyond success, or the classified failure.
     */
    suspend fun storeLines(
        orderId: String,
        lines: List<RefineryStoreLine>,
    ): ApiResult<Unit>
}

/**
 * The member's own Raffinerie orders (REQ-APP-REF-001…006).
 *
 * **Only `my-orders`.** The controller also serves `/all`, `/users/{id}` and `/mission/{id}`, which
 * are the Logistik surface; the app stays on the member-facing one, in the same way the Bank slice
 * stays off the bank-employee endpoints.
 *
 * **The extractor import is deliberately absent** (owner decision, 2026-08-23). Design chapter 11
 * puts a scan icon on this screen; it moves to phase 5 with the other file flows, because all three
 * need a file picker plus the permission and privacy work they share. Recorded in
 * `docs/specs/refinery.md` rather than left as a silent difference from the design.
 *
 * @property reader performs the calls and classifies their failures.
 */
class RefineryRepository(
    private val reader: ApiReader,
) : RefinerySource,
    RefineryStoreSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers.
     * @param baseUrl the flavour's API origin.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /** {@inheritDoc} */
    override suspend fun myOrders(
        statuses: Set<RefineryServerStatus>,
        page: Int,
        pageSize: Int,
    ): ApiResult<RefineryOrderPage> {
        val params =
            buildList {
                // UNKNOWN is dropped rather than sent: it is this build's name for a status the
                // server introduced, and echoing it back would turn an unrecognised row into a
                // `400` on the whole page.
                statuses
                    .filter { it != RefineryServerStatus.UNKNOWN }
                    .forEach { add(STATUS_PARAM to it.name) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
            }
        return when (
            val result =
                reader.get(MY_ORDERS_PATH, params, PageResponseRefineryOrderListDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    /** {@inheritDoc} */
    override suspend fun detail(id: String): ApiResult<RefineryOrder> =
        when (val result = reader.get(orderPath(id), RefineryOrderDto.serializer())) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel(id)?.let { ApiResult.Success(it) } ?: result.value.missing(id)
            }
        }

    /** {@inheritDoc} */
    override suspend fun storeLines(
        orderId: String,
        lines: List<RefineryStoreLine>,
    ): ApiResult<Unit> {
        val items = lines.mapNotNull { it.toItem() }
        // All or nothing: the call closes the order, so a line the app could not read must stop the
        // whole submit rather than quietly leave one material behind.
        if (items.isEmpty() || items.size != lines.size) {
            return ApiResult.Failure(ApiError.Validation())
        }
        return reader.postAccepted(
            storePath(orderId),
            RefineryOrderStoreDto(items = items),
            RefineryOrderStoreDto.serializer(),
        )
    }

    override suspend fun store(order: RefineryOrder): ApiResult<Unit> {
        val locationId = order.locationId
        val items =
            if (locationId == null) {
                emptyList()
            } else {
                order.yields.mapNotNull { good ->
                    good.materialId?.let {
                        RefineryOrderStoreItemDto(
                            materialId = it,
                            locationId = locationId,
                            // The server requires a quality. An order that records none books at
                            // zero rather than refusing: the member's material exists either way,
                            // and a booking withheld over a missing grade loses the yield.
                            quality = good.quality ?: DEFAULT_QUALITY,
                            // SCU, not wire units: the endpoint reads this as the member's own
                            // figure, writes it into the Lager as-is and multiplies it back by a
                            // hundred into the order's good. Sending the raw `outputQuantity`
                            // would book a hundred times the yield.
                            amount = good.amount,
                        )
                    }
                }
            }
        if (items.isEmpty()) {
            // Refused here rather than sent. The endpoint marks the order stored whatever the item
            // list contains, so an empty one is the quiet way to lose a whole run's yield.
            return ApiResult.Failure(ApiError.Validation())
        }
        return reader.postAccepted(
            storePath(order.id),
            RefineryOrderStoreDto(items = items),
            RefineryOrderStoreDto.serializer(),
        )
    }

    companion object {
        /** One screenful and then some; the member's own orders are few. */
        const val DEFAULT_PAGE_SIZE: Int = 20

        /** Log subsystem. A member's yield is their business and never reaches the log. */
        private const val LOG_TAG = "refinery"

        private const val MY_ORDERS_PATH = "/api/v1/refinery-orders/my-orders"
        private const val STATUS_PARAM = "status"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val DEFAULT_QUALITY = 0

        /**
         * Path of one order.
         *
         * @param id the order's id.
         * @return the path.
         */
        private fun orderPath(id: String): String = "/api/v1/refinery-orders/$id"

        /**
         * Path of one order's booking.
         *
         * @param id the order's id.
         * @return the path.
         */
        private fun storePath(id: String): String = "${orderPath(id)}/store"
    }
}

/**
 * Reports a detail response that carried no id as a not-found rather than as a success.
 *
 * @param id the order that was asked for.
 * @return the failure.
 */
private fun RefineryOrderDto.missing(id: String): ApiResult.Failure {
    check(this.id == null) { "order $id has an id and should not be reported missing" }
    return ApiResult.Failure(ApiError.NotFound())
}

/**
 * Maps one page of the list.
 *
 * @param page the index that was asked for, used when the server omits its own.
 * @return the page, without rows the server sent without an id.
 */
private fun PageResponseRefineryOrderListDto.toModel(page: Int): RefineryOrderPage =
    RefineryOrderPage(
        orders = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one list row.
 *
 * @return the order, or `null` when it has no id — a row nothing can be opened by.
 */
private fun RefineryOrderListDto.toModel(): RefineryOrder? {
    val orderId = id ?: return null
    return buildOrder(
        id = orderId,
        locationId = location?.id,
        locationName = location?.name,
        methodName = refiningMethod?.name,
        startedAt = startedAt,
        durationMinutes = durationMinutes,
        endsAtRaw = endsAt,
        status = status,
        goods = goods,
        oreSales = oreSales?.toString(),
        profit = profit?.toString(),
        version = version,
    )
}

/**
 * Maps the detail response.
 *
 * @param requestedId the id that was asked for; the detail response may omit its own.
 * @return the order, or `null` when nothing identifies it.
 */
private fun RefineryOrderDto.toModel(requestedId: String): RefineryOrder? {
    val orderId = id ?: requestedId.takeIf { it.isNotBlank() } ?: return null
    return buildOrder(
        id = orderId,
        // Required on the detail DTO and optional on the list one, which is why the two mappings
        // differ here rather than sharing a line.
        locationId = location.id,
        locationName = location.name,
        methodName = refiningMethod?.name,
        startedAt = startedAt,
        durationMinutes = durationMinutes,
        // The detail DTO carries no `endsAt`; the list one does. Computing it from the start and
        // the duration is what makes the two screens agree — a detail that showed no end time
        // while the list counted down would read as a different order.
        endsAtRaw = null,
        status = status,
        goods = goods,
        oreSales = oreSales?.toString(),
        profit = profit?.toString(),
        version = version,
    )
}

/**
 * Assembles the model both responses share, including the phase the server does not have.
 *
 * @param id the order's id.
 * @param locationId where it runs.
 * @param locationName the station's name.
 * @param methodName the refining method.
 * @param startedAt when the run began.
 * @param durationMinutes how long it takes.
 * @param endsAtRaw the end time when the server sent one.
 * @param status the server's status.
 * @param goods the run's goods.
 * @param oreSales the recorded ore-sales figure.
 * @param profit the recorded gain or loss.
 * @param version the optimistic lock.
 * @return the assembled order.
 */
@Suppress("LongParameterList")
private fun buildOrder(
    id: String,
    locationId: String?,
    locationName: String?,
    methodName: String?,
    startedAt: String?,
    durationMinutes: Long?,
    endsAtRaw: String?,
    status: String?,
    goods: List<RefineryGoodDto>?,
    oreSales: String?,
    profit: String?,
    version: Long?,
): RefineryOrder =
    RefineryOrder(
        id = id,
        locationId = locationId,
        locationName = locationName?.takeIf { it.isNotBlank() }.orEmpty(),
        methodName = methodName?.takeIf { it.isNotBlank() }.orEmpty(),
        startedAt = startedAt?.takeIf { it.isNotBlank() },
        endsAt = endsAtRaw ?: computedEnd(startedAt, durationMinutes),
        status = serverStatusOf(status),
        yields = goods.orEmpty().map { it.toModel() },
        oreSales = oreSales?.takeIf { it.isNotBlank() },
        profit = profit?.takeIf { it.isNotBlank() },
        version = version,
    )

/**
 * Computes an end time from a start and a duration.
 *
 * @param startedAt the start, as the server rendered it.
 * @param durationMinutes the run length.
 * @return the end in the same ISO form, or `null` when either input is missing or unparseable.
 */
private fun computedEnd(
    startedAt: String?,
    durationMinutes: Long?,
): String? {
    val start = startedAt?.takeIf { it.isNotBlank() }
    return if (start == null || durationMinutes == null) {
        null
    } else {
        runCatching {
            OffsetDateTime.parse(start).plusMinutes(durationMinutes).toString()
        }.getOrNull()
    }
}

/**
 * Reads the server's status without letting an unknown one crash or vanish.
 *
 * @param status the status as the server spelled it.
 * @return the known status, or [RefineryServerStatus.UNKNOWN].
 */
private fun serverStatusOf(status: String?): RefineryServerStatus =
    when (status) {
        "OPEN" -> RefineryServerStatus.OPEN
        "IN_PROGRESS" -> RefineryServerStatus.IN_PROGRESS
        "COMPLETED" -> RefineryServerStatus.COMPLETED
        "CANCELED" -> RefineryServerStatus.CANCELED
        else -> RefineryServerStatus.UNKNOWN
    }

/**
 * Whether an end time lies at or before [now].
 *
 * @param endsAt the end time, as the server rendered it.
 * @param now the moment to judge against.
 * @return `true` only when it parses and has passed.
 */
private fun hasEndedBy(
    endsAt: String?,
    now: OffsetDateTime,
): Boolean {
    val end = endsAt?.takeIf { it.isNotBlank() } ?: return false
    return runCatching { !OffsetDateTime.parse(end).isAfter(now) }.getOrDefault(false)
}

/**
 * Maps one good.
 *
 * The **output** material is what gets booked, not the input: the ore went in, the refined material
 * comes out, and booking the input would put ore in the Lager that no longer exists.
 *
 * @return the yield row, with its amount already in the member's unit.
 */
private fun RefineryGoodDto.toModel(): RefineryYield {
    val piece = outputMaterial?.quantityType == "PIECE"
    return RefineryYield(
        materialId = outputMaterial?.id,
        materialName =
            outputMaterial
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?: inputMaterial.name?.takeIf { it.isNotBlank() }.orEmpty(),
        // Units to SCU. The one conversion in this file, and the one the booking depends on.
        amount = if (piece) outputQuantity.toDouble() else outputQuantity / UNITS_PER_SCU,
        unitIsPiece = piece,
        quality = quality,
    )
}

/** How many wire units make one SCU. The server tracks `outputQuantity` in the smaller one. */
private const val UNITS_PER_SCU = 100.0

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.LocationDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseRefineryOrderListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseRefiningMethodDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryGoodDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderStoreDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefineryOrderStoreItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RefiningMethodDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UserReferenceDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * One material coming out of a refining run.
 *
 * [amount] is in the member's unit: the server's `outputQuantity` counts units, a hundred to the
 * SCU, and is divided accordingly; a `PIECE` material is not divided.
 *
 * @property materialId the refined material's id; `null` when the server named no output material,
 *   in which case the good cannot be booked into the Lager.
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
 * How the member sees an order, as opposed to how the server stores it.
 *
 * [READY] has no server status: an order stays `IN_PROGRESS` until booked, so readiness is computed
 * from the run's end time having passed.
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
 * @property status what the server stores; the member-facing phase comes from [phaseAt], since it
 *   changes with time.
 * @property yields the goods the run produces.
 * @property oreSales the ore-sales figure recorded on the order, as the server rendered it.
 * @property profit the recorded gain or loss, as the server rendered it.
 * @property version the optimistic lock, echoed on writes that take one.
 */
data class RefineryOrder(
    val id: String,
    val ownerId: String?,
    val ownerName: String,
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
     * Total output across every good, in the member's units; a rough size figure, since units may be
     * mixed.
     */
    val totalAmount: Double get() = yields.sumOf { it.amount }

    /**
     * What the member sees at [now].
     *
     * `READY` means the end time has passed while the server still says `IN_PROGRESS`; an unknown or
     * unparseable end time reads as still running.
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
     * Requires a finished run, a location to book into, and at least one good naming an output
     * material, because the endpoint marks the order stored whatever the item list contains.
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
 * [Page.rows] holds the rows on this page.
 */
typealias RefineryOrderPage = Page<RefineryOrder>

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

/** Minutes in an hour, for the duration the form takes in two fields. */
private const val MINUTES_PER_HOUR = 60

/** What a new run's status is; the other two describe what happened to it later. */
private const val REFINERY_STATUS_IN_PROGRESS = "IN_PROGRESS"

/** The status a booked run carries, which is what locks its core and its goods in the form. */
private const val REFINERY_STATUS_COMPLETED = "COMPLETED"

/**
 * The duration the two fields add up to, in minutes.
 *
 * @return the total, or `null` when neither field carries a figure — the run then has no duration
 *   and „Endet" cannot be computed, which is a state the form allows.
 */
private fun RefineryOrderDraft.totalMinutes(): Int? {
    val hours = durationHours.trim().toIntOrNull()
    val minutes = durationMinutes.trim().toIntOrNull()
    return if (hours == null && minutes == null) {
        null
    } else {
        (hours ?: 0) * MINUTES_PER_HOUR + (minutes ?: 0)
    }
}

/**
 * Maps one good of the form onto the wire.
 *
 * The output material and the yield bonus are not sent: the server derives the former from the
 * input and ignores the latter.
 *
 * @return the good, or `null` without an input material.
 */
private fun RefineryGoodDraft.toDto(): RefineryGoodDto? =
    inputMaterialId?.let { input ->
        RefineryGoodDto(
            inputMaterial = MaterialDto(id = input, name = inputMaterialName),
            inputQuantity = inputQuantity.trim().toIntOrNull() ?: 0,
            outputMaterial = null,
            outputQuantity = outputQuantity.trim().toIntOrNull() ?: 0,
            quality = quality.trim().toIntOrNull(),
            yieldBonusPercent = null,
        )
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
        amount = figure,
        userId = userId,
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
 * @property amount what is actually being booked, in SCU; pre-filled with [computed] and editable.
 * @property quality the grade, 0–1000.
 * @property locationId where it goes. Mandatory; pre-filled with the order's refinery.
 * @property personal whether it becomes the member's own entry rather than the unit's.
 * @property jobOrderId the Auftrag to earmark it against, or `null`. Excludes [personal]; the server
 *   answers 400 for the pair.
 * @property note free text, at most 1000 characters.
 * @property userId who receives it, or `null` for the caller.
 * @property owningOrgUnitId which unit to book into; required when the receiver holds more than one
 *   membership, pre-filled with the order's unit.
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
    val userName: String? = null,
    val owningOrgUnitId: String? = null,
) {
    /**
     * This line's identity among the run's others: material and quality, since a run can yield the
     * same material at two grades.
     */
    val key: String get() = "$materialId@$quality"
}

/** How long a store note may be, as the server counts it. */
const val REFINERY_NOTE_LIMIT: Int = 1000

/**
 * One refining method, with the three ratings the picker shows as bars.
 *
 * @property id the method.
 * @property name what to show.
 * @property ratingYield how much it gets out, 0–3.
 * @property ratingCost what it costs, 0–3.
 * @property ratingSpeed how fast it is, 0–3.
 */
data class RefiningMethod(
    val id: String,
    val name: String,
    val ratingYield: Int,
    val ratingCost: Int,
    val ratingSpeed: Int,
)

/**
 * One ore a goods line may name, with the material it refines into.
 *
 * @property id the ore.
 * @property name what to show for it.
 * @property refinedId what it refines into, or `null` for an ore the catalogue names no output for.
 * @property refinedName what to show for that.
 */
data class RefineryInputMaterial(
    val id: String,
    val name: String,
    val refinedId: String? = null,
    val refinedName: String? = null,
)

/**
 * One line of a new order: what went in, what came out.
 *
 * The output material is derived from the input by the server and is carried here only for
 * display; it is never sent.
 *
 * @property inputMaterialId the ore.
 * @property inputMaterialName what to show for it.
 * @property inputQuantity how much went in, as typed, in UNITS (see [RefineryGoodDraft.outputScu]).
 * @property outputMaterialId the refined material the input resolves to, or `null` before a pick.
 * @property outputMaterialName what to show for it.
 * @property outputQuantity how much came out, as typed, in UNITS -- 100 units are one SCU.
 * @property quality the grade, as typed, 0–1000.
 * @property yieldBonusPercent the refinery's UEX bonus for this material, read-only; ignored on write.
 * @property key this line's stable identity on the device, used as the `LazyColumn` key; kept by
 *   `copy` and never sent.
 */
data class RefineryGoodDraft(
    val inputMaterialId: String? = null,
    val inputMaterialName: String = "",
    val inputQuantity: String = "",
    val outputMaterialId: String? = null,
    val outputMaterialName: String = "",
    val outputQuantity: String = "",
    val quality: String = "",
    val yieldBonusPercent: String = "",
    val key: String = UUID.randomUUID().toString(),
) {
    /**
     * Whether the server would accept this line.
     *
     * Input material and both quantities at 1 or more — the wire's own `@NotNull @Min(1)`. The
     * output material is genuinely optional; a run that yielded nothing nameable still consumed ore.
     */
    val complete: Boolean
        get() =
            inputMaterialId != null &&
                (inputQuantity.trim().toIntOrNull() ?: 0) >= 1 &&
                (outputQuantity.trim().toIntOrNull() ?: 0) >= 1

    /**
     * [outputQuantity] read back in SCU (a hundred units each), or `null` when it is not a number yet
     * (REQ-APP-REF-004a).
     */
    val outputScu: Double?
        get() = outputQuantity.trim().toIntOrNull()?.let { it / UNITS_PER_SCU }

    /**
     * [inputQuantity] read back in SCU, on the same rule as [outputScu].
     */
    val inputScu: Double?
        get() = inputQuantity.trim().toIntOrNull()?.let { it / UNITS_PER_SCU }
}

/**
 * A new refinery order as the form holds it.
 *
 * @property locationId the refinery. Mandatory.
 * @property locationName what to show for it.
 * @property methodId the refining method. Mandatory.
 * @property methodName what to show for it.
 * @property goods at least one line.
 * @property startedDate when the run began, as `TT.MM.JJJJ`, or blank.
 * @property startedTime the clock reading, as `SS:MM`, or blank.
 * @property durationHours how long it runs, as typed.
 * @property durationMinutes the remainder, as typed.
 * @property expenses what it cost, as typed.
 * @property otherExpenses anything else, as typed.
 * @property oreSales what the ore sold for, as typed.
 * @property missionId the Einsatz to link, or `null`.
 * @property missionName what to show for it.
 * @property version the order's optimistic lock when editing, `null` when raising one.
 * @property stored whether the run's yield has already been booked into the Lager, which is what
 *   locks the core and the goods lines (`REQ-APP-REF-011`).
 * @property status what the order's status is on the server, echoed unchanged by the edit; `null`
 *   when raising one, where the create picks „In Arbeit".
 */
data class RefineryOrderDraft(
    val locationId: String? = null,
    val locationName: String = "",
    val methodId: String? = null,
    val methodName: String = "",
    val goods: List<RefineryGoodDraft> = emptyList(),
    val startedDate: String = "",
    val startedTime: String = "",
    val durationHours: String = "",
    val durationMinutes: String = "",
    val expenses: String = "",
    val otherExpenses: String = "",
    val oreSales: String = "",
    val missionId: String? = null,
    val missionName: String = "",
    val version: Long? = null,
    val stored: Boolean = false,
    val status: String? = null,
) {
    /**
     * When the run began, as the wire wants it.
     *
     * The two fields are the member's; the instant is the server's. An unreadable pair is `null`
     * rather than a guess — a run whose start nobody recorded is a state the form allows.
     */
    val startedAt: Instant?
        get() =
            runCatching {
                LocalDateTime.parse(
                    "$startedDate $startedTime".trim(),
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
                ).atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()

    /**
     * Whether the form may be sent: a location, a method, and every goods line complete, since the
     * server refuses the whole order for one half-filled line.
     */
    val sendable: Boolean
        get() =
            locationId != null &&
                methodId != null &&
                goods.isNotEmpty() &&
                goods.all { it.complete }
}

/**
 * What a new refinery order needs, beyond the order itself.
 */
interface RefineryCreateSource : RefineryOrderDeleteSource {
    /**
     * Reads the refineries a run can be placed at.
     *
     * @return the locations, or the classified failure.
     */
    suspend fun refineries(): ApiResult<List<Pair<String, String>>>

    /**
     * Reads the refining methods with their ratings.
     *
     * @return the methods, or the classified failure.
     */
    suspend fun methods(): ApiResult<List<RefiningMethod>>

    /**
     * Searches the raw ores a goods line can name (`rawOnly=true`), each with the refined material it
     * resolves to.
     *
     * @param query what was typed; blank asks for the first page unfiltered.
     * @return the candidates and whether the catalogue holds more of them, or the classified
     *   failure.
     */
    suspend fun searchMaterials(query: String): ApiResult<PickerPage<RefineryInputMaterial>>

    /**
     * Creates the order the form describes.
     *
     * @param draft the form.
     * @return the new order's id, or the classified failure.
     */
    suspend fun createOrder(draft: RefineryOrderDraft): ApiResult<String>

    /**
     * Reads one order back as a pre-filled form, including the fields the detail model does not carry.
     *
     * @param orderId which order.
     * @return the pre-filled form, or the classified failure.
     */
    suspend fun orderDraft(orderId: String): ApiResult<RefineryOrderDraft>

    /**
     * Rewrites the order the form describes.
     *
     * The `version` is echoed, so a concurrent edit is a `409` rather than a silent overwrite, and
     * the status is echoed unchanged — the edit form does not move an order between states.
     *
     * @param orderId which order.
     * @param draft the form.
     * @return nothing on success, or the classified failure.
     */
    suspend fun updateOrder(
        orderId: String,
        draft: RefineryOrderDraft,
    ): ApiResult<Unit>
}

/**
 * Deleting one refinery order.
 *
 * Its own interface because the **detail** offers the action while the **form** performs the two
 * writes: a detail that took the whole form source would depend on the material search and the
 * picker lists it never asks for.
 */
interface RefineryOrderDeleteSource {
    /**
     * Deletes one order.
     *
     * The server soft-deletes it (`status` becomes `CANCELED`), booked or not; refusing a booked run is
     * the app's rule (`REQ-APP-REF-012`).
     *
     * @param orderId which order.
     * @return nothing on success, or the classified failure.
     */
    suspend fun deleteOrder(orderId: String): ApiResult<Unit>
}

/**
 * Books a finished run's materials into the Lager in one call for the whole run, since the server
 * marks the order completed after the first booking.
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
 * Uses only the member-facing endpoints; the Logistik surfaces (`/users/{id}`, `/mission/{id}`)
 * and the extractor import are not part of the app.
 *
 * @property reader performs the calls and classifies their failures.
 */
class RefineryRepository(
    private val reader: ApiReader,
) : RefinerySource,
    RefineryStoreSource,
    RefineryCreateSource {
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
                statuses
                    .filter { it != RefineryServerStatus.UNKNOWN }
                    .forEach { add(STATUS_PARAM to it.name) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
                add(SORT_PARAM to NEWEST_FIRST)
            }
        return reader.get(ALL_ORDERS_PATH, params, PageResponseRefineryOrderListDto.serializer())
            .map { it.toModel(page) }
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
    override suspend fun refineries(): ApiResult<List<Pair<String, String>>> =
        when (
            val result =
                reader.get(REFINERIES_PATH, ListSerializer(LocationDto.serializer()))
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { row ->
                        row.id?.let { it to row.name.orEmpty() }
                    },
                )
            }
        }

    override suspend fun methods(): ApiResult<List<RefiningMethod>> =
        when (
            val result =
                reader.get(METHODS_PATH, PageResponseRefiningMethodDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.content.orEmpty().mapNotNull { row ->
                        row.id?.let {
                            RefiningMethod(
                                id = it,
                                name = row.name.orEmpty(),
                                ratingYield = row.ratingYield ?: 0,
                                ratingCost = row.ratingCost ?: 0,
                                ratingSpeed = row.ratingSpeed ?: 0,
                            )
                        }
                    },
                )
            }
        }

    override suspend fun searchMaterials(query: String): ApiResult<PickerPage<RefineryInputMaterial>> =
        when (
            val result =
                reader.get(
                    MATERIALS_PATH,
                    listOf(
                        SEARCH_PARAM to query.trim(),
                        RAW_ONLY_PARAM to "true",
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
                val rows =
                    result.value.content.orEmpty().mapNotNull { row ->
                        row.id?.let {
                            RefineryInputMaterial(
                                id = it,
                                name = row.name.orEmpty(),
                                refinedId = row.refinedMaterial?.id,
                                refinedName = row.refinedMaterial?.name,
                            )
                        }
                    }
                ApiResult.Success(krtPickerPage(rows, result.value.totalElements))
            }
        }

    override suspend fun createOrder(draft: RefineryOrderDraft): ApiResult<String> {
        val body = draft.toWire() ?: return ApiResult.Failure(ApiError.Validation())
        return when (
            val result =
                reader.post(
                    path = ORDERS_PATH,
                    body = body,
                    bodySerializer = RefineryOrderDto.serializer(),
                    deserializer = RefineryOrderDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.id?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK))
            }
        }
    }

    override suspend fun orderDraft(orderId: String): ApiResult<RefineryOrderDraft> =
        reader.get(orderPath(orderId), emptyList(), RefineryOrderDto.serializer())
            .map { it.toDraft() }

    override suspend fun updateOrder(
        orderId: String,
        draft: RefineryOrderDraft,
    ): ApiResult<Unit> {
        val body = draft.toWire() ?: return ApiResult.Failure(ApiError.Validation())
        return reader.putAccepted(
            path = orderPath(orderId),
            body = body,
            bodySerializer = RefineryOrderDto.serializer(),
        )
    }

    override suspend fun deleteOrder(orderId: String): ApiResult<Unit> =
        reader.delete(orderPath(orderId))

    override suspend fun storeLines(
        orderId: String,
        lines: List<RefineryStoreLine>,
    ): ApiResult<Unit> {
        val items = lines.mapNotNull { it.toItem() }
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
                            quality = good.quality ?: DEFAULT_QUALITY,
                            amount = good.amount,
                        )
                    }
                }
            }
        if (items.isEmpty()) {
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

        /**
         * The squadron-wide list the screen shows.
         *
         * Readable for everyone authenticated, with rows scoped by the service; writing stays with the
         * owner.
         */
        private const val ALL_ORDERS_PATH = "/api/v1/refinery-orders/all"

        /** Where a new order is posted. */
        const val ORDERS_PATH = "/api/v1/refinery-orders"

        /** The refineries a run can be placed at. */
        const val REFINERIES_PATH = "/api/v1/locations/refineries"

        /** The refining methods, with the ratings the picker draws as bars. */
        const val METHODS_PATH = "/api/v1/refining-methods"

        /** The material search behind a goods line. */
        const val MATERIALS_PATH = "/api/v1/materials/search"

        /** What was typed into a material picker. */
        const val SEARCH_PARAM = "search"

        /**
         * Narrows the material search to refinery inputs.
         *
         * `type = RAW` or the manual raw flag, resolved in the server's own picker query. The web
         * form's input combobox sends the same thing.
         */
        const val RAW_ONLY_PARAM = "rawOnly"

        /**
         * How many candidates one search offers; the overflow is reported rather than trimmed (ADR-0104).
         */
        private const val PICKER_PAGE_SIZE = 50

        /** What a successful call that returned nothing usable is reported as. */
        private const val HTTP_OK = 200

        private const val STATUS_PARAM = "status"
        private const val SORT_PARAM = "sort"

        /**
         * Newest refinery order first.
         *
         * The server's fallback when no sort is sent is `startedAt` **ascending**, which opened the
         * member's own refinery orders on the oldest one and pushed anything still running onto the
         * last page. The web app sends `startedAt,desc`.
         */
        private const val NEWEST_FIRST = "startedAt,desc"
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
 * The form as the wire takes it, for both writes.
 *
 * @receiver the form.
 * @return the body, or `null` when the form names no refinery or no method — which the caller
 *   reports as a validation failure rather than sending a request the server will refuse.
 */
private fun RefineryOrderDraft.toWire(): RefineryOrderDto? {
    val where = locationId
    val method = methodId
    if (where == null || method == null) {
        return null
    }
    return RefineryOrderDto(
        location = LocationDto(id = where, name = locationName),
        refiningMethod = RefiningMethodDto(id = method, name = methodName),
        goods = goods.mapNotNull { it.toDto() },
        startedAt = startedAt?.toString(),
        durationMinutes = totalMinutes()?.toLong(),
        expenses = parseTypedAmount(expenses),
        otherExpenses = parseTypedAmount(otherExpenses),
        oreSales = parseTypedAmount(oreSales),
        mission = missionId?.let { MissionReferenceDto(id = it, name = missionName) },
        status = status ?: REFINERY_STATUS_IN_PROGRESS,
        version = version,
    )
}

/**
 * One order as a pre-filled form.
 *
 * @receiver what the server sent.
 * @return the form.
 */
private fun RefineryOrderDto.toDraft(): RefineryOrderDraft {
    val started = startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val local = started?.atZone(ZoneId.systemDefault())
    val minutes = durationMinutes ?: 0L
    return RefineryOrderDraft(
        locationId = location.id,
        locationName = location.name.orEmpty(),
        methodId = refiningMethod?.id,
        methodName = refiningMethod?.name.orEmpty(),
        goods = goods.orEmpty().map { it.toDraft() },
        startedDate = local?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty(),
        startedTime = local?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
        durationHours = if (durationMinutes == null) "" else (minutes / MINUTES_PER_HOUR).toString(),
        durationMinutes = if (durationMinutes == null) "" else (minutes % MINUTES_PER_HOUR).toString(),
        expenses = formatTypedAmount(expenses),
        otherExpenses = formatTypedAmount(otherExpenses),
        oreSales = formatTypedAmount(oreSales),
        missionId = mission?.id,
        missionName = mission?.name.orEmpty(),
        version = version,
        stored = status == REFINERY_STATUS_COMPLETED,
        status = status,
    )
}

/**
 * One goods line as the form holds it.
 *
 * @receiver what the server sent.
 * @return the line.
 */
private fun RefineryGoodDto.toDraft(): RefineryGoodDraft =
    RefineryGoodDraft(
        inputMaterialId = inputMaterial.id,
        inputMaterialName = inputMaterial.name.orEmpty(),
        inputQuantity = inputQuantity.toString(),
        outputMaterialId = outputMaterial?.id,
        outputMaterialName = outputMaterial?.name.orEmpty(),
        outputQuantity = outputQuantity.toString(),
        quality = quality?.toString().orEmpty(),
        yieldBonusPercent = yieldBonusPercent?.toString().orEmpty(),
    )

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
        rows = content.orEmpty().mapNotNull { it.toModel() },
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
        ownerId = owner?.id,
        ownerName = owner?.krtName().orEmpty(),
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
        ownerId = owner?.id,
        ownerName = owner?.krtName().orEmpty(),
        locationId = location.id,
        locationName = location.name,
        methodName = refiningMethod?.name,
        startedAt = startedAt,
        durationMinutes = durationMinutes,
        endsAtRaw = null,
        status = status,
        goods = goods,
        oreSales = oreSales?.toString(),
        profit = profit?.toString(),
        version = version,
    )
}

/**
 * The name to show for an order's owner: `effectiveName`, else its inputs in order.
 *
 * @return the name, or the empty string.
 */
private fun UserReferenceDto.krtName(): String =
    effectiveName?.takeIf { it.isNotBlank() }
        ?: displayName?.takeIf { it.isNotBlank() }
        ?: username.orEmpty()

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
    ownerId: String?,
    ownerName: String,
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
        ownerId = ownerId,
        ownerName = ownerName,
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
 * Maps one good, taking the output material as the one that gets booked.
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
        amount = if (piece) outputQuantity.toDouble() else outputQuantity / UNITS_PER_SCU,
        unitIsPiece = piece,
        quality = quality,
    )
}

/** How many wire units make one SCU. The server tracks `outputQuantity` in the smaller one. */
private const val UNITS_PER_SCU = 100.0

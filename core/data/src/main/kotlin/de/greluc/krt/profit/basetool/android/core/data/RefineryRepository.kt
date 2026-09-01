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
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
 * Two fields the draft holds are deliberately **not** sent, both because the server owns them:
 *
 *  * the **output material**, which `resolveGood` derives from the input's `refinedMaterial` when
 *    it is absent and rejects outright when it disagrees. Sending the derived value back would buy
 *    nothing and would turn a legacy row whose output no longer matches its input into a `400` the
 *    member cannot read. The web form omits it for the same reason.
 *  * the **yield bonus**, which is UEX-derived and read-only: the write path ignores it and the
 *    database persists nothing for it.
 *
 * @return the good, or `null` without an input material — a line that names nothing is not a line.
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
 * The output material is **derived, never chosen**. `RefineryOrderService.resolveGood` sets it from
 * the input material's `refinedMaterial` when the payload leaves it out, and refuses any other
 * value with a `400` whose message the server deliberately withholds -- so a picker here could only
 * ever produce a rejection nobody can read. It is carried on the draft to be shown, and left off
 * the wire, exactly as the web form does it.
 *
 * @property inputMaterialId the ore.
 * @property inputMaterialName what to show for it.
 * @property inputQuantity how much went in, as typed, in UNITS (see [RefineryGoodDraft.outputScu]).
 * @property outputMaterialId the refined material the input resolves to, or `null` before a pick.
 * @property outputMaterialName what to show for it.
 * @property outputQuantity how much came out, as typed, in UNITS -- 100 units are one SCU.
 * @property quality the grade, as typed, 0–1000.
 * @property yieldBonusPercent the refinery's UEX bonus for this material, read-only, as read back
 *   from the server. The wire ignores it on write and the database persists nothing for it.
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
     * [outputQuantity] read back in SCU, or `null` when it is not a number yet.
     *
     * The field on the wire counts **units**, a hundred to the SCU, and REQ-APP-REF-004a records
     * what assuming otherwise cost: a booking that would have created a Lager entry a hundred times
     * the yield. The form therefore labels its fields in units and shows this beside them, the way
     * the web form's read-only SCU box does -- so the member sees the figure they think in without
     * anything converting behind their back.
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
     * Whether the form may be sent.
     *
     * A location and a method, and **every** goods line complete. The server requires an input
     * material and both quantities at 1 or more on each line (`@NotNull @Min(1)`), so a half-filled
     * line is not an omission it tolerates — it refuses the whole order with a `goods[0]`-shaped
     * message nobody can act on. Requiring all of them keeps the refusal here, where the field is,
     * rather than there, where the field name is an index.
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
     * Searches the ores a goods line can name.
     *
     * **Not** the Lager's material search, which this once was. A refinery consumes raw ore, and
     * `RefineryOrderService.resolveGood` refuses anything else — with an
     * `IllegalArgumentException` the global handler deliberately strips of its message, so the
     * member is told a line is invalid and never which one or why. Asking the server for
     * `rawOnly=true` is what keeps that rejection off the screen: the same narrowing the web form's
     * `remote-materials-raw` combobox does, and the same definition behind it (`type = RAW` or the
     * manual raw flag).
     *
     * Each row carries the refined material the input resolves to, because the form shows it
     * instead of asking for it.
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
     * Reads one order back as a form.
     *
     * The detail model the list and the detail screen use keeps only what those screens draw, so
     * the edit reads the order again rather than filling a form from a model that never carried
     * the method id, the two cost fields or the linked Einsatz.
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
     * The server *cancels* it — the row is soft-deleted and `status` becomes `CANCELED` — and it
     * does so for a booked order too. The rule that a booked run may not be deleted is the app's
     * (`REQ-APP-REF-012`), because no gate enforces it.
     *
     * @param orderId which order.
     * @return nothing on success, or the classified failure.
     */
    suspend fun deleteOrder(orderId: String): ApiResult<Unit>
}

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
                // A page, not a list — `/locations/refineries` beside it answers with a bare
                // array and the two are easy to assume alike. Parsed as a list this yields nothing,
                // the picker renders empty, and the form is silently unsendable.
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
        when (
            val result =
                reader.get(orderPath(orderId), emptyList(), RefineryOrderDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toDraft())
        }

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
         * How many candidates one search offers.
         *
         * Fifty, the web combobox's render cap. The page is not the whole answer and never was, so
         * the search reports the overflow rather than trimming in silence (ADR-0104) — the
         * shipped 25 with nothing beside it read as "there is no such ore".
         */
        private const val PICKER_PAGE_SIZE = 50

        /** What a successful call that returned nothing usable is reported as. */
        private const val HTTP_OK = 200

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
        // The create has exactly one status to send — the other two describe what happened to the
        // run afterwards. The edit echoes whatever the order already carries: this form moves an
        // order's contents, never its state.
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

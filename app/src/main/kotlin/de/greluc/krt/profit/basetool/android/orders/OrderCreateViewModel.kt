/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderCreateSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderDraft
import de.greluc.krt.profit.basetool.android.core.data.JobOrderDraftLine
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemDraft
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemDraftLine
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.data.krtHandedOver
import de.greluc.krt.profit.basetool.android.core.data.parseTypedAmount
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which of the two orders is being raised.
 *
 * The web puts this on a radio pair at the head of the form and swaps the body under it; the app
 * uses the segmented control, which is the same choice in the design's own control. The two bodies
 * share the head — both units, the handle, the comment — and differ only in what is being asked
 * for.
 */
enum class OrderKind {
    /** Raw materials, by amount and minimum quality. */
    MATERIAL,

    /** Finished items, by blueprint. The server derives the materials from it. */
    ITEM,
}

/**
 * One material line as the form holds it, before it becomes a [JobOrderDraftLine].
 *
 * The amount stays a string: „12,5" is what a German keyboard produces, and parsing it early would
 * turn a half-typed „12," into a zero the member cannot see.
 *
 * @property materialId which material, once picked.
 * @property materialName what the picker shows for it.
 * @property query what is in the picker's text field.
 * @property amount what was typed for the quantity.
 * @property minQuality the minimum quality, or `null` for „keine". Starts at
 *   [DEFAULT_MIN_QUALITY], which is where `JobOrderForm.JobOrderMaterialForm` starts it: the same
 *   order raised on a phone and in a browser has to ask for the same ore, and it did not — the app
 *   defaulted to „keine" and quietly ordered ungraded material.
 */
data class OrderLineDraft(
    val materialId: String? = null,
    val materialName: String = "",
    val query: String = "",
    val amount: String = "",
    val minQuality: Int? = DEFAULT_MIN_QUALITY,
)

/**
 * The minimum quality a fresh material line asks for.
 *
 * 650, the web form's own default. It is a starting point and not a rule — the picker's other
 * entry is „Keine", one tap away.
 */
const val DEFAULT_MIN_QUALITY: Int = 650

/** Whether the line names a material and a positive amount. */
val OrderLineDraft.isComplete: Boolean
    get() = materialId != null && (parseTypedAmount(amount) ?: 0.0) > 0.0

/**
 * One item line as the form holds it, before it becomes a [JobOrderItemDraftLine].
 *
 * The picker's text is kept beside the picked id, because the two can disagree while the member is
 * typing and the form has to show what was typed, not what was last picked. The blueprints are held
 * per line, because two lines may name different items.
 *
 * @property gameItemId which item is picked, or `null`.
 * @property itemName the picked item's name; empty while nothing is picked.
 * @property query what is in the item picker's field.
 * @property blueprintId which blueprint is picked, or `null`.
 * @property blueprints the blueprints the picked item offers.
 * @property amount how many, as typed.
 */
data class OrderItemLineDraft(
    val gameItemId: String? = null,
    val itemName: String = "",
    val query: String = "",
    val blueprintId: String? = null,
    val blueprints: List<Pair<String, String>> = emptyList(),
    val amount: String = "",
)

/** Whether the item line names an item, a blueprint and a positive count. */
val OrderItemLineDraft.isComplete: Boolean
    get() = gameItemId != null && blueprintId != null && (amount.trim().toIntOrNull() ?: 0) > 0

/**
 * Whether the item line is half-filled.
 *
 * Same rule as a material line: an item picked but not finished is a mistake, not an empty row, and
 * dropping it silently would raise an order missing what the member asked for.
 */
val OrderItemLineDraft.isPartial: Boolean
    get() = !isComplete && (gameItemId != null || amount.isNotBlank())

/**
 * Whether the line is half-filled.
 *
 * A material with no amount, or an amount with no material, is a mistake rather than an empty line:
 * submitting would silently drop what the member typed. An entirely empty line is not partial — it
 * is the one the form always keeps at the end.
 */
val OrderLineDraft.isPartial: Boolean
    get() = !isComplete && (materialId != null || amount.isNotBlank())

/**
 * The „Neuer Auftrag" form (design round 8, §1 — chapter 10 has no artboard for it yet).
 *
 * @property responsibleId the unit that will process the order.
 * @property requestingId the unit the order is for.
 * @property handle the contact handle.
 * @property comment the free-text note.
 * @property kind which of the two orders is being raised.
 * @property lines the material lines; always at least one.
 * @property itemLines the item lines; always at least one.
 * @property items the candidates the open item picker shows.
 * @property responsibleOptions the units that may process an order.
 * @property requestingOptions every active unit.
 * @property materials the candidates the open picker shows.
 * @property materialsTruncated whether the server holds further matches than the picker shows.
 * @property loading whether the pickers are still arriving.
 * @property saving whether the creation is in flight.
 * @property created the new order's id once it exists.
 * @property error what the last read or write was refused with.
 */
data class OrderCreateState(
    val responsibleId: String? = null,
    val requestingId: String? = null,
    val handle: String = "",
    val comment: String = "",
    val kind: OrderKind = OrderKind.MATERIAL,
    val lines: List<OrderLineDraft> = listOf(OrderLineDraft()),
    val itemLines: List<OrderItemLineDraft> = listOf(OrderItemLineDraft()),
    val items: List<Pair<String, String>> = emptyList(),
    val responsibleOptions: List<OrgUnit> = emptyList(),
    val requestingOptions: List<OrgUnit> = emptyList(),
    val materials: List<Pair<String, String>> = emptyList(),
    val materialsTruncated: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val created: String? = null,
    val error: ApiError? = null,
    /** Whether this form raises an order or rewrites one, and under which of the two edit gates. */
    val mode: OrderFormMode = OrderFormMode.CREATE,
    /** The order being edited, or `null` while it is still being read. */
    val version: Long? = null,
    /**
     * How much of each material has already been handed over, keyed by material id.
     *
     * The floor under every line: „eine erfüllte Position lässt sich nicht unter die übergebene
     * Menge senken" (artboard 10), and the server answers 400 for the attempt. Summed from the
     * handover **lines**, never from `amount − openAmount`, which counts claims.
     */
    val delivered: Map<String, Double> = emptyMap(),
    /** Whether the edit's own read finished; `false` keeps the form as a skeleton. */
    val saved: Boolean = false,
) {
    /** How much of one line may not be undercut, or `0.0` when nothing has been delivered. */
    fun deliveredOf(materialId: String?): Double = materialId?.let { delivered[it] } ?: 0.0

    /**
     * The lines whose typed amount is below what has already changed hands.
     *
     * The server refuses the save outright, so the form names the offending lines rather than
     * letting somebody submit and read a 400 that does not say which one.
     */
    val underDelivered: List<OrderLineDraft>
        get() =
            lines.filter { line ->
                val floor = deliveredOf(line.materialId)
                floor > 0.0 && (parseTypedAmount(line.amount) ?: 0.0) < floor
            }

    /**
     * Whether the form may be submitted.
     *
     * Mirrors what the backend requires, so the member learns about a missing field from a disabled
     * button rather than from a 400: both units, a handle, and at least one line that names a
     * material and a positive amount. A half-filled line blocks the submit rather than being
     * dropped silently.
     */
    val submittable: Boolean
        get() =
            !saving &&
                responsibleId != null &&
                requestingId != null &&
                handle.isNotBlank() &&
                underDelivered.isEmpty() &&
                when (kind) {
                    OrderKind.MATERIAL -> lines.any { it.isComplete } && lines.none { it.isPartial }
                    OrderKind.ITEM -> itemLines.any { it.isComplete } && itemLines.none { it.isPartial }
                }

    /**
     * Turns an item form into what the API takes.
     *
     * @return the draft, or `null` when a required field is still missing.
     */
    fun toItemDraft(): JobOrderItemDraft? {
        val responsible = responsibleId
        val requesting = requestingId
        if (responsible == null || requesting == null) {
            return null
        }
        return JobOrderItemDraft(
            responsibleOrgUnitId = responsible,
            requestingOrgUnitId = requesting,
            handle = handle.trim(),
            comment = comment.trim().takeIf { it.isNotEmpty() },
            lines =
                itemLines.filter { it.isComplete }.map {
                    JobOrderItemDraftLine(
                        gameItemId = requireNotNull(it.gameItemId),
                        blueprintId = requireNotNull(it.blueprintId),
                        amount = requireNotNull(it.amount.trim().toIntOrNull()),
                    )
                },
            version = version,
        )
    }

    /**
     * Turns the form into what the API takes.
     *
     * @return the draft, or `null` when a required field is still missing.
     */
    fun toDraft(): JobOrderDraft? {
        val responsible = responsibleId
        val requesting = requestingId
        if (responsible == null || requesting == null) {
            return null
        }
        return JobOrderDraft(
            responsibleOrgUnitId = responsible,
            requestingOrgUnitId = requesting,
            handle = handle.trim(),
            comment = comment.trim().takeIf { it.isNotEmpty() },
            lines =
                lines.filter { it.isComplete }.map {
                    JobOrderDraftLine(
                        materialId = requireNotNull(it.materialId),
                        materialName = it.materialName,
                        amount = requireNotNull(parseTypedAmount(it.amount)),
                        minQuality = it.minQuality,
                    )
                },
            // `null` on a create; on an edit it is what the order was read at, and a mismatch is
            // the 409 that keeps two people from overwriting each other.
            version = version,
        )
    }
}

/**
 * What the order form is doing.
 *
 * One form, three writes. Design ch. 10 artboard 10 is explicit about it — „Dasselbe Formular wie
 * ‚Auftrag anlegen', vorbefüllt — kein zweites Layout" — and the server agrees: the update takes the
 * *same* payload as the create and replaces the details and the whole material list rather than
 * patching either.
 */
enum class OrderFormMode {
    /** Raising a new order. */
    CREATE,

    /** A Logistician rewriting one (`PUT /orders/{id}`). */
    EDIT,

    /**
     * The requester's own, narrower edit (`PUT /orders/{id}/requested`, REQ-ORDERS-023).
     *
     * No Logistician role needed, and three fields are **drawn locked rather than removed**: the
     * two units and the handle belong to the processing side. The server takes them from the stored
     * order whatever the payload says, so editing them would be a control that silently does
     * nothing — worse than one that says why it cannot.
     */
    EDIT_AS_REQUESTER,
    ;

    /** Whether this mode may change the two units and the handle. */
    val headEditable: Boolean
        get() = this != EDIT_AS_REQUESTER
}

/**
 * The call this form makes when it is submitted.
 *
 * Three writes behind one button: raising an order, a Logistician's rewrite, and the requester's
 * narrower one. Which it is follows from [OrderCreateState.mode] and nothing else — the app never
 * infers a permission from a role it read, it is told which form it opened.
 *
 * An **edit of an item order is not offered here**: `PUT /orders/{id}/items` takes a different
 * payload and needs the blueprint-variant picker and the sub-assembly tree of artboard 12, which is
 * its own screen. An item form in an edit mode therefore writes nothing rather than sending the
 * wrong shape.
 *
 * @receiver the form.
 * @param source where the writes go.
 * @param orderId the order being edited, or `null` on a create.
 * @return the call, or `null` when the form cannot be sent.
 */
private fun OrderCreateState.krtWrite(
    source: JobOrderCreateSource,
    orderId: String?,
): (suspend () -> ApiResult<String>)? {
    if (mode == OrderFormMode.CREATE) {
        return when (kind) {
            OrderKind.MATERIAL -> toDraft()?.let { draft -> suspend { source.create(draft) } }
            OrderKind.ITEM -> toItemDraft()?.let { draft -> suspend { source.createItems(draft) } }
        }
    }
    val id = orderId
    val write = if (id == null) null else krtRewrite(source, id)
    return if (id == null || write == null) {
        null
    } else {
        suspend {
            // The screen goes to the order it wrote — for an edit, the one it came from.
            when (val result = write()) {
                is ApiResult.Success -> ApiResult.Success(id)
                is ApiResult.Failure -> result
            }
        }
    }
}

/**
 * The rewrite behind an edit form — one of three endpoints, chosen by the mode and the kind.
 *
 * Its own function because the create's branch and the edit's are two different questions, and one
 * `when` holding both was past the complexity the codebase allows.
 *
 * > **An item order's edit is a Logistician's alone.** `PUT /{id}/items` is the only item edit the
 * > app makes; the requester's own item path (`/{id}/items/requested`) is not built, so a requester
 * > form on an item order writes nothing rather than sending the wrong shape.
 *
 * @receiver the form.
 * @param source where the writes go.
 * @param id the order being rewritten.
 * @return the call, or `null` when the form cannot be sent.
 */
private fun OrderCreateState.krtRewrite(
    source: JobOrderCreateSource,
    id: String,
): (suspend () -> ApiResult<Unit>)? {
    if (kind == OrderKind.ITEM) {
        return toItemDraft()
            ?.takeIf { mode == OrderFormMode.EDIT }
            ?.let { draft -> suspend { source.updateItems(id, draft) } }
    }
    return toDraft()?.let { draft ->
        if (mode == OrderFormMode.EDIT) {
            suspend { source.update(id, draft) }
        } else {
            suspend { source.updateAsRequester(id, draft) }
        }
    }
}

/**
 * Fills the form from the order it is editing.
 *
 * @receiver the empty form.
 * @param order what the server holds.
 * @return the form, pre-filled.
 */
private fun OrderCreateState.krtPrefilled(order: JobOrder): OrderCreateState =
    copy(
        responsibleId = order.responsibleOrgUnitId,
        requestingId = order.requestingOrgUnitId,
        handle = order.handle.orEmpty(),
        comment = order.comment.orEmpty(),
        kind = if (order.items.isNotEmpty()) OrderKind.ITEM else OrderKind.MATERIAL,
        itemLines =
            order.items
                .filter { it.parentItemId == null && it.gameItemId != null && it.blueprintId != null }
                .map { line ->
                    OrderItemLineDraft(
                        gameItemId = line.gameItemId,
                        query = line.name.orEmpty(),
                        // The variant this line was ordered with. Its alternatives are read on
                        // prefill, so the picker can offer them without the member re-picking the
                        // item first — that is the „blueprint variant counting" parity point.
                        blueprintId = line.blueprintId,
                        blueprints = listOfNotNull(line.blueprintId?.let { it to line.blueprintName.orEmpty() }),
                        amount = line.amount.toString(),
                    )
                }
                .ifEmpty { listOf(OrderItemLineDraft()) },
        lines =
            order.materials
                .filter { it.materialId != null }
                .map { material ->
                    OrderLineDraft(
                        materialId = material.materialId,
                        materialName = material.name,
                        query = material.name,
                        amount = material.needed.orEmpty(),
                    )
                }
                .ifEmpty { listOf(OrderLineDraft()) },
        version = order.version,
        // The floor under every line, summed from the handover lines rather than from the open
        // remainder — that one counts claims (`MaterialClaimService`), not deliveries.
        delivered =
            order.materials
                .mapNotNull { it.materialId }
                .associateWith { order.krtHandedOver(it) }
                .filterValues { it > 0.0 },
        saved = true,
    )

/**
 * Drives the „Neuer Auftrag" form, in both of its kinds.
 *
 * The two share the head — the units, the handle, the comment — and hold their lines apart, so
 * switching the kind never has to throw away what was already typed. What is submitted is decided
 * by [OrderCreateState.kind] alone.
 *
 * The **sub-assembly tree** the web draws under an item line — adopting a blueprint's own
 * components as further lines — is not offered here; the order is raised with the items named and
 * the server derives their materials. Design round 8 §1.3 carries it.
 *
 * @property source the creation and the pickers behind both kinds.
 * @property orgUnits the two unit pickers.
 * @property orders where an edit reads the order it is rewriting; `null` for a create.
 * @property orderId which order is being edited; `null` for a create.
 */
class OrderCreateViewModel(
    private val source: JobOrderCreateSource,
    private val orgUnits: OrgUnitSource,
    private val orders: JobOrderSource? = null,
    private val orderId: String? = null,
    mode: OrderFormMode = OrderFormMode.CREATE,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrderCreateState(mode = mode))

    /** What the screen draws. */
    val state: StateFlow<OrderCreateState> = mutableState.asStateFlow()

    init {
        load()
        if (mode != OrderFormMode.CREATE) {
            prefill()
        }
    }

    /**
     * Reads the order being edited and fills the form with it.
     *
     * The version arrives with it and travels back on the save; so does what has already been
     * handed over, which is the floor under every line.
     */
    private fun prefill() {
        val id = orderId ?: return
        val reader = orders ?: return
        viewModelScope.launch {
            when (val result = reader.detail(id)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.krtPrefilled(result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the order could not be read for editing: ${result.error}" }
                    mutableState.value = mutableState.value.copy(error = result.error, saved = true)
                }
            }
        }
    }

    /**
     * Fetches the two unit pickers.
     *
     * The responsible list is the profit-eligible subset of the same read, exactly as the web
     * derives it — one call, not two, and no chance of the two lists disagreeing.
     */
    fun load() {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = orgUnits.activeAllKinds()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            loading = false,
                            requestingOptions = result.value,
                            responsibleOptions = result.value.filter { it.profitEligible },
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the order form's unit pickers could not be read: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loading = false, error = result.error)
                }
            }
        }
    }

    /**
     * Picks the unit that will process the order.
     *
     * @param id the unit.
     */
    fun onResponsible(id: String) {
        mutableState.value = mutableState.value.copy(responsibleId = id)
    }

    /**
     * Picks the unit the order is for.
     *
     * @param id the unit.
     */
    fun onRequesting(id: String) {
        mutableState.value = mutableState.value.copy(requestingId = id)
    }

    /**
     * The contact handle was edited.
     *
     * @param value what is in the field.
     */
    fun onHandle(value: String) {
        mutableState.value = mutableState.value.copy(handle = value.take(HANDLE_MAX))
    }

    /**
     * The comment was edited.
     *
     * @param value what is in the field.
     */
    fun onComment(value: String) {
        mutableState.value = mutableState.value.copy(comment = value.take(COMMENT_MAX))
    }

    /** Appends an empty material line. */
    fun onAddLine() {
        mutableState.value = mutableState.value.copy(lines = mutableState.value.lines + OrderLineDraft())
    }

    /**
     * Removes one material line.
     *
     * The last line is emptied rather than removed: a form with no lines has no obvious way back to
     * having one, and the member almost certainly meant to clear it.
     *
     * @param index which line.
     */
    fun onRemoveLine(index: Int) {
        val lines = mutableState.value.lines
        val next = if (lines.size == 1) listOf(OrderLineDraft()) else lines.filterIndexed { i, _ -> i != index }
        mutableState.value = mutableState.value.copy(lines = next)
    }

    /**
     * Switches between a material and an item order.
     *
     * Both line sets survive the switch: a member who typed three materials, looked at the item
     * form and came back finds their three materials still there.
     *
     * @param kind which order to raise.
     */
    fun onKind(kind: OrderKind) {
        mutableState.value = mutableState.value.copy(kind = kind)
    }

    /** Appends an empty item line. */
    fun onAddItemLine() {
        mutableState.value = mutableState.value.copy(itemLines = mutableState.value.itemLines + OrderItemLineDraft())
    }

    /**
     * Removes one item line, emptying the last one rather than leaving none.
     *
     * @param index which line.
     */
    fun onRemoveItemLine(index: Int) {
        val lines = mutableState.value.itemLines
        val next = if (lines.size == 1) listOf(OrderItemLineDraft()) else lines.filterIndexed { i, _ -> i != index }
        mutableState.value = mutableState.value.copy(itemLines = next)
    }

    /**
     * Edits one item line.
     *
     * @param index which line.
     * @param edit what to change about it.
     */
    private fun editItemLine(
        index: Int,
        edit: (OrderItemLineDraft) -> OrderItemLineDraft,
    ) {
        val lines = mutableState.value.itemLines
        if (index !in lines.indices) {
            return
        }
        mutableState.value =
            mutableState.value.copy(itemLines = lines.mapIndexed { i, l -> if (i == index) edit(l) else l })
    }

    /**
     * The item picker's text changed without a pick.
     *
     * Clears the picked item *and* its blueprint: a blueprint belongs to one item, so leaving it
     * behind would submit a pairing the member never made.
     *
     * @param index which line.
     * @param query what was typed.
     */
    fun onItemQuery(
        index: Int,
        query: String,
    ) {
        editItemLine(index) {
            it.copy(query = query, gameItemId = null, itemName = "", blueprintId = null, blueprints = emptyList())
        }
        searchItems(query)
    }

    /**
     * An item was picked for a line.
     *
     * @param index which line.
     * @param item its id and name.
     */
    fun onItemPicked(
        index: Int,
        item: Pair<String, String>,
    ) {
        editItemLine(index) {
            it.copy(
                gameItemId = item.first,
                itemName = item.second,
                query = item.second,
                blueprintId = null,
                blueprints = emptyList(),
            )
        }
        loadBlueprints(index, item.first)
    }

    /**
     * A blueprint was picked for a line.
     *
     * @param index which line.
     * @param blueprintId which blueprint.
     */
    fun onBlueprintPicked(
        index: Int,
        blueprintId: String,
    ) {
        editItemLine(index) { it.copy(blueprintId = blueprintId) }
    }

    /**
     * An item line's count was edited.
     *
     * @param index which line.
     * @param value what was typed.
     */
    fun onItemAmount(
        index: Int,
        value: String,
    ) {
        editItemLine(index) { it.copy(amount = value) }
    }

    /**
     * Refills the item candidates.
     *
     * @param query what was typed.
     */
    private fun searchItems(query: String) {
        if (query.trim().length < MIN_QUERY) {
            mutableState.value = mutableState.value.copy(items = emptyList())
            return
        }
        viewModelScope.launch {
            when (val result = source.searchItems(query)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(items = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the item picker could not be filled: ${result.error}" }
                }
            }
        }
    }

    /**
     * Fills one line's blueprint picker.
     *
     * A single blueprint is picked outright: the member has no choice to make, and one more tap on
     * a one-entry dropdown is only a way to leave the line unfinished.
     *
     * @param index which line.
     * @param gameItemId the item whose blueprints to read.
     */
    private fun loadBlueprints(
        index: Int,
        gameItemId: String,
    ) {
        viewModelScope.launch {
            when (val result = source.blueprintsFor(gameItemId)) {
                is ApiResult.Success -> {
                    editItemLine(index) {
                        // Only if the line still names the item that was asked about: a slow answer
                        // must not fill the picker of an item the member has since typed past.
                        if (it.gameItemId == gameItemId) {
                            it.copy(blueprints = result.value, blueprintId = result.value.singleOrNull()?.first)
                        } else {
                            it
                        }
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the blueprints could not be read: ${result.error}" }
                }
            }
        }
    }

    /**
     * Edits one line.
     *
     * @param index which line.
     * @param edit what to change about it.
     */
    private fun editLine(
        index: Int,
        edit: (OrderLineDraft) -> OrderLineDraft,
    ) {
        val lines = mutableState.value.lines
        if (index !in lines.indices) {
            return
        }
        mutableState.value =
            mutableState.value.copy(lines = lines.mapIndexed { i, l -> if (i == index) edit(l) else l })
    }

    /**
     * A material was picked for a line.
     *
     * @param index which line.
     * @param material its id and name.
     */
    fun onMaterialPicked(
        index: Int,
        material: Pair<String, String>,
    ) {
        editLine(index) {
            it.copy(materialId = material.first, materialName = material.second, query = material.second)
        }
    }

    /**
     * The picker's text changed without a pick.
     *
     * The selection is cleared, because a query that no longer names the picked material must not
     * leave the form quietly holding the old id behind different-looking text.
     *
     * @param index which line.
     * @param query what was typed.
     */
    fun onMaterialQuery(
        index: Int,
        query: String,
    ) {
        editLine(index) { it.copy(query = query, materialId = null, materialName = "") }
        searchMaterials(query)
    }

    /**
     * A line's amount was edited.
     *
     * @param index which line.
     * @param value what was typed.
     */
    fun onAmount(
        index: Int,
        value: String,
    ) {
        editLine(index) { it.copy(amount = value) }
    }

    /**
     * A line's minimum quality was picked.
     *
     * @param index which line.
     * @param quality the quality, or `null` for „keine".
     */
    fun onMinQuality(
        index: Int,
        quality: Int?,
    ) {
        editLine(index) { it.copy(minQuality = quality) }
    }

    /**
     * Refills the material candidates.
     *
     * @param query what was typed.
     */
    private fun searchMaterials(query: String) {
        if (query.trim().length < MIN_QUERY) {
            mutableState.value = mutableState.value.copy(materials = emptyList(), materialsTruncated = false)
            return
        }
        viewModelScope.launch {
            when (val result = source.searchMaterials(query)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            materials = result.value.rows,
                            materialsTruncated = result.value.more,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the material picker could not be filled: ${result.error}" }
                }
            }
        }
    }

    /** Raises the order, in whichever kind the form is holding. */
    fun onSubmit() {
        val current = mutableState.value
        if (current.saving) {
            return
        }
        val raise: (suspend () -> ApiResult<String>)? = current.krtWrite(source, orderId)
        if (raise == null) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = raise()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(saving = false, created = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the order could not be raised: ${result.error}" }
                    mutableState.value = mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /** Clears the last refusal once the screen has shown it. */
    fun onErrorShown() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private companion object {
        /** Log subsystem. The handle and the comment are member input and never reach the log. */
        const val LOG_TAG = "orders"

        /** What the server accepts as a handle. */
        const val HANDLE_MAX = 200

        /** What the server accepts as a comment. */
        const val COMMENT_MAX = 1000

        /** How much has to be typed before the picker asks the server. */
        const val MIN_QUERY = 2
    }
}

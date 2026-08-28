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
import de.greluc.krt.profit.basetool.android.core.data.JobOrderCreateSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderDraft
import de.greluc.krt.profit.basetool.android.core.data.JobOrderDraftLine
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemDraft
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemDraftLine
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
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
 * @property minQuality the minimum quality, or `null` for „keine".
 */
data class OrderLineDraft(
    val materialId: String? = null,
    val materialName: String = "",
    val query: String = "",
    val amount: String = "",
    val minQuality: Int? = null,
)

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
) {
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
        )
    }
}

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
 */
class OrderCreateViewModel(
    private val source: JobOrderCreateSource,
    private val orgUnits: OrgUnitSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrderCreateState())

    /** What the screen draws. */
    val state: StateFlow<OrderCreateState> = mutableState.asStateFlow()

    init {
        load()
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
        val raise: (suspend () -> ApiResult<String>)? =
            when (current.kind) {
                OrderKind.MATERIAL -> current.toDraft()?.let { draft -> suspend { source.create(draft) } }
                OrderKind.ITEM -> current.toItemDraft()?.let { draft -> suspend { source.createItems(draft) } }
            }
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

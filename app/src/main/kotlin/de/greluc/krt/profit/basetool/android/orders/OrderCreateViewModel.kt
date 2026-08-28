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
 * @property lines the material lines; always at least one.
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
    val lines: List<OrderLineDraft> = listOf(OrderLineDraft()),
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
                lines.any { it.isComplete } &&
                lines.none { it.isPartial }

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
 * Drives the „Neuer Auftrag" form.
 *
 * **Material orders only.** An item order needs a game-item picker, a blueprint picker per item and
 * the derivation tree the web renders as nested lines; that is a screen of its own and is asked for
 * in design round 8 §1.3 rather than bolted onto this one.
 *
 * @property source the creation and the material picker.
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

    /** Raises the order. */
    fun onSubmit() {
        val draft = mutableState.value.toDraft() ?: return
        if (mutableState.value.saving) {
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = source.create(draft)) {
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

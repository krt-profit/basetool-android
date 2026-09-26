/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.RefineryCreateSource
import de.greluc.krt.profit.basetool.android.core.data.RefineryGoodDraft
import de.greluc.krt.profit.basetool.android.core.data.RefineryInputMaterial
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDraft
import de.greluc.krt.profit.basetool.android.core.data.RefiningMethod
import de.greluc.krt.profit.basetool.android.core.data.parseTypedAmount
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The state of the „Neuer Raffinerieauftrag" form.
 *
 * @property draft what has been entered.
 * @property refineries the locations a run can be placed at.
 * @property methods the refining methods, with the ratings the picker draws.
 * @property loading whether the pickers are still arriving.
 * @property saving whether the creation is in flight.
 * @property created the new order's id once it exists, which is what the screen navigates to.
 * @property materials the ores the goods lines' input pickers show.
 * @property moreMaterials whether the catalogue holds ores this page does not carry.
 * @property error what the last read or write was refused with.
 * @property editing whether this rewrites an order or raises one.
 */
data class RefineryCreateState(
    val draft: RefineryOrderDraft = RefineryOrderDraft(goods = listOf(RefineryGoodDraft())),
    val refineries: List<Pair<String, String>> = emptyList(),
    val methods: List<RefiningMethod> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val created: String? = null,
    val materials: List<RefineryInputMaterial> = emptyList(),
    val moreMaterials: Boolean = false,
    val error: ApiError? = null,
    val editing: Boolean = false,
) {
    /**
     * Whether the run's core and its goods are locked, because the yield is already booked into the
     * Lager (REQ-APP-REF-011).
     *
     * The server does not enforce this; the app draws it as a lock.
     */
    val coreLocked: Boolean get() = editing && draft.stored

    /**
     * When the run ends, computed from start and duration.
     *
     * Display, not a field — the design is explicit that „Endet" is derived. `null` while either
     * half is missing, which the form allows: a run whose duration nobody recorded still exists.
     */
    val endsAt: Instant?
        get() {
            val started = draft.startedAt
            val hours = draft.durationHours.trim().toLongOrNull()
            val minutes = draft.durationMinutes.trim().toLongOrNull()
            if (started == null || (hours == null && minutes == null)) {
                return null
            }
            return started
                .plus(Duration.ofHours(hours ?: 0))
                .plus(Duration.ofMinutes(minutes ?: 0))
        }

    /**
     * The profit the money block previews.
     *
     * The web's own wording for it is „Automatisch berechnet: Ore Sales abzüglich Kosten und
     * sonstiger Kosten des Raffinerieauftrags", and this computes exactly that — no rounding, no
     * fee, nothing the server would disagree with.
     */
    val profit: Double
        get() =
            (parseTypedAmount(draft.oreSales) ?: 0.0) -
                (parseTypedAmount(draft.expenses) ?: 0.0) -
                (parseTypedAmount(draft.otherExpenses) ?: 0.0)
}

/**
 * Drives the „Neuer Raffinerieauftrag" form, for a create or an edit; there is no extractor import.
 *
 * @property source the pickers and the two writes.
 * @property orderId the order being rewritten, or `null` when raising one; an edit uses the same
 *   form pre-filled.
 */
class RefineryCreateViewModel(
    private val source: RefineryCreateSource,
    private val orderId: String? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RefineryCreateState(editing = orderId != null))

    /** What the screen renders. */
    val state: StateFlow<RefineryCreateState> = mutableState.asStateFlow()

    private var loaded = false

    /** Reads the two picker lists once, on first composition. */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        onMaterialQuery("")
        viewModelScope.launch {
            val refineries = source.refineries()
            val methods = source.methods()
            val existing = orderId?.let { source.orderDraft(it) }
            mutableState.update { state ->
                state.copy(
                    draft =
                        (existing as? ApiResult.Success)?.value?.let { loaded ->
                            if (loaded.goods.isEmpty()) {
                                loaded.copy(goods = listOf(RefineryGoodDraft()))
                            } else {
                                loaded
                            }
                        } ?: state.draft,
                    refineries = (refineries as? ApiResult.Success)?.value.orEmpty(),
                    methods = (methods as? ApiResult.Success)?.value.orEmpty(),
                    loading = false,
                    error =
                        (existing as? ApiResult.Failure)?.error
                            ?: (refineries as? ApiResult.Failure)?.error
                            ?: (methods as? ApiResult.Failure)?.error,
                )
            }
        }
    }

    /**
     * Searches the ores a goods line can name, into one list shared by all lines.
     *
     * @param query what was typed.
     */
    fun onMaterialQuery(query: String) {
        viewModelScope.launch {
            when (val result = source.searchMaterials(query)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            materials = result.value.rows,
                            moreMaterials = result.value.more,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "material search failed: ${result.error}" }
                }
            }
        }
    }

    /**
     * Records the ore a goods line names, and derives what it refines into.
     *
     * The output is the ore's `refinedMaterial`, or the ore itself when the catalogue names none, as
     * `RefineryOrderService.resolveGood` does.
     *
     * @param index which line.
     * @param material the ore that was picked.
     */
    fun onInputMaterialPicked(
        index: Int,
        material: RefineryInputMaterial,
    ) {
        val goods = mutableState.value.draft.goods.toMutableList()
        val line = goods.getOrNull(index) ?: return
        goods[index] =
            line.copy(
                inputMaterialId = material.id,
                inputMaterialName = material.name,
                outputMaterialId = material.refinedId ?: material.id,
                outputMaterialName = material.refinedName ?: material.name,
            )
        onDraftChanged(mutableState.value.draft.copy(goods = goods))
    }

    /**
     * Clears a goods line's ore and derived output because its name was typed over.
     *
     * @param index which line.
     * @param typed what now stands in the field.
     */
    fun onInputMaterialTyped(
        index: Int,
        typed: String,
    ) {
        val goods = mutableState.value.draft.goods.toMutableList()
        val line = goods.getOrNull(index) ?: return
        goods[index] =
            line.copy(
                inputMaterialId = null,
                inputMaterialName = typed,
                outputMaterialId = null,
                outputMaterialName = "",
            )
        onDraftChanged(mutableState.value.draft.copy(goods = goods))
    }

    /**
     * Records a change to the form.
     *
     * @param draft the form as it now stands.
     */
    fun onDraftChanged(draft: RefineryOrderDraft) {
        mutableState.update { it.copy(draft = draft) }
    }

    /** Adds an empty goods line. */
    fun onAddGood() {
        val draft = mutableState.value.draft
        mutableState.update { it.copy(draft = draft.copy(goods = draft.goods + RefineryGoodDraft())) }
    }

    /**
     * Removes one goods line.
     *
     * The last one stays: the server requires at least one good, and a form with no line at all
     * offers nothing to type into.
     *
     * @param index which line.
     */
    fun onRemoveGood(index: Int) {
        val draft = mutableState.value.draft
        if (draft.goods.size <= 1) {
            return
        }
        mutableState.update { state ->
            state.copy(
                draft = draft.copy(goods = draft.goods.filterIndexed { i, _ -> i != index }),
            )
        }
    }

    /**
     * Replaces one goods line.
     *
     * @param index which line.
     * @param good the line as it now stands.
     */
    fun onGoodChanged(
        index: Int,
        good: RefineryGoodDraft,
    ) {
        val draft = mutableState.value.draft
        mutableState.update { state ->
            state.copy(
                draft =
                    draft.copy(goods = draft.goods.mapIndexed { i, old -> if (i == index) good else old }),
            )
        }
    }

    /** Sends the form — raising the order, or rewriting the one being edited. */
    fun onCreate() {
        val current = mutableState.value
        if (!current.draft.sendable || current.saving || current.loading) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val id = orderId
            val result =
                if (id == null) {
                    source.createOrder(current.draft)
                } else {
                    when (val write = source.updateOrder(id, current.draft)) {
                        is ApiResult.Success -> ApiResult.Success(id)
                        is ApiResult.Failure -> write
                    }
                }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(saving = false, created = result.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "writing the order was refused: ${result.error}" }
                    mutableState.update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. A member's yield is their business and never reaches the log. */
        const val LOG_TAG = "refinery"
    }
}

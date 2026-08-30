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
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDraft
import de.greluc.krt.profit.basetool.android.core.data.RefiningMethod
import de.greluc.krt.profit.basetool.android.core.data.parseTypedAmount
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The „Neuer Raffinerieauftrag" form — design chapter 11, artboards 4 and 5.
 *
 * @property draft what has been entered.
 * @property refineries the locations a run can be placed at.
 * @property methods the refining methods, with the ratings the picker draws.
 * @property loading whether the pickers are still arriving.
 * @property saving whether the creation is in flight.
 * @property created the new order's id once it exists, which is what the screen navigates to.
 * @property materials the candidates the goods lines' material pickers show.
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
    val materials: List<Pair<String, String>> = emptyList(),
    val error: ApiError? = null,
    val editing: Boolean = false,
) {
    /**
     * Whether the run's core and its goods are locked.
     *
     * Once the yield has been booked into the Lager, the goods describe rows that already exist
     * somewhere else, and moving them here would leave the two disagreeing. The **server does not
     * enforce this** — `PUT /refinery-orders/{id}` rewrites a booked order's goods without
     * complaint — so the rule is the app's, drawn as a lock rather than as an absence
     * (`REQ-APP-REF-011`).
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
 * Drives the „Neuer Raffinerieauftrag" form.
 *
 * **No extractor import.** The Extractor is a Windows desktop app whose handoff runs through the
 * ingest gateway and is consumed once in a browser; a phone cannot receive it (design chapter 11,
 * „Entscheidungen — Create"). The form is deliberately manual.
 *
 * @property source the pickers and the two writes.
 * @property orderId the order being rewritten, or `null` when raising one. The edit is the **same
 *   form pre-filled**, which design ch. 11 artboard 6 is explicit about: no second layout.
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
            mutableState.value =
                mutableState.value.copy(
                    draft =
                        (existing as? ApiResult.Success)?.value?.let { loaded ->
                            // A run with no goods line would leave the editor with nothing to edit;
                            // the create's own empty line is the right shape for that.
                            if (loaded.goods.isEmpty()) {
                                loaded.copy(goods = listOf(RefineryGoodDraft()))
                            } else {
                                loaded
                            }
                        } ?: mutableState.value.draft,
                    refineries = (refineries as? ApiResult.Success)?.value.orEmpty(),
                    methods = (methods as? ApiResult.Success)?.value.orEmpty(),
                    loading = false,
                    // Either list failing leaves the form unusable, so the failure is shown rather
                    // than an empty picker that looks like "there are none".
                    error =
                        (existing as? ApiResult.Failure)?.error
                            ?: (refineries as? ApiResult.Failure)?.error
                            ?: (methods as? ApiResult.Failure)?.error,
                )
        }
    }

    /**
     * Searches the materials a goods line can name.
     *
     * One shared list rather than one per line: every line asks the same question of the same
     * catalogue, and a per-line list would answer it several times over.
     *
     * @param query what was typed.
     */
    fun onMaterialQuery(query: String) {
        viewModelScope.launch {
            when (val result = source.searchMaterials(query)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(materials = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "material search failed: ${result.error}" }
                }
            }
        }
    }

    /**
     * Records a change to the form.
     *
     * @param draft the form as it now stands.
     */
    fun onDraftChanged(draft: RefineryOrderDraft) {
        mutableState.value = mutableState.value.copy(draft = draft)
    }

    /** Adds an empty goods line. */
    fun onAddGood() {
        val draft = mutableState.value.draft
        mutableState.value =
            mutableState.value.copy(draft = draft.copy(goods = draft.goods + RefineryGoodDraft()))
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
        mutableState.value =
            mutableState.value.copy(
                draft = draft.copy(goods = draft.goods.filterIndexed { i, _ -> i != index }),
            )
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
        mutableState.value =
            mutableState.value.copy(
                draft =
                    draft.copy(goods = draft.goods.mapIndexed { i, old -> if (i == index) good else old }),
            )
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
                    mutableState.value =
                        mutableState.value.copy(saving = false, created = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "writing the order was refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. A member's yield is their business and never reaches the log. */
        const val LOG_TAG = "refinery"
    }
}

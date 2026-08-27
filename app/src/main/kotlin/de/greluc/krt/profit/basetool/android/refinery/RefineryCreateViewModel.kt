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
 * @property error what the last read or write was refused with.
 */
data class RefineryCreateState(
    val draft: RefineryOrderDraft = RefineryOrderDraft(goods = listOf(RefineryGoodDraft())),
    val refineries: List<Pair<String, String>> = emptyList(),
    val methods: List<RefiningMethod> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val created: String? = null,
    val error: ApiError? = null,
) {
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
 * @property source the pickers and the creation.
 */
class RefineryCreateViewModel(
    private val source: RefineryCreateSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RefineryCreateState())

    /** What the screen renders. */
    val state: StateFlow<RefineryCreateState> = mutableState.asStateFlow()

    private var loaded = false

    /** Reads the two picker lists once, on first composition. */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        viewModelScope.launch {
            val refineries = source.refineries()
            val methods = source.methods()
            mutableState.value =
                mutableState.value.copy(
                    refineries = (refineries as? ApiResult.Success)?.value.orEmpty(),
                    methods = (methods as? ApiResult.Success)?.value.orEmpty(),
                    loading = false,
                    // Either list failing leaves the form unusable, so the failure is shown rather
                    // than an empty picker that looks like "there are none".
                    error =
                        (refineries as? ApiResult.Failure)?.error
                            ?: (methods as? ApiResult.Failure)?.error,
                )
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

    /** Creates the order the form describes. */
    fun onCreate() {
        val current = mutableState.value
        if (!current.draft.sendable || current.saving) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = source.createOrder(current.draft)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, created = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "creating the order was refused: ${result.error}" }
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

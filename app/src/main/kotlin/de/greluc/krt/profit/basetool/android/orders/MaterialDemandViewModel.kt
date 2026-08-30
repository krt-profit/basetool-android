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
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandGroup
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandRow
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Log subsystem. */
private const val LOG_TAG = "material-demand"

/** How far the read has got. */
sealed interface MaterialDemandPhase {
    /** On its way. */
    data object Loading : MaterialDemandPhase

    /** It arrived; an empty answer is a result. */
    data object Ready : MaterialDemandPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : MaterialDemandPhase
}

/**
 * How the list is narrowed and ordered.
 *
 * Three chips, no search field: twelve rows do not need one (design ch. 18 §1).
 */
enum class MaterialDemandFilter {
    /** Everything the orders ask for. */
    ALL,

    /** Only what is still open — the working mode. */
    UNCOVERED,

    /** Everything, largest outstanding amount first. */
    BY_AMOUNT,
}

/**
 * Everything the Materialbedarf screen draws.
 *
 * @property groups the demand as the server grouped it, by org unit.
 * @property filter which chip is active.
 * @property expanded which material has its orders open, or `null`.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 */
data class MaterialDemandState(
    val groups: List<MaterialDemandGroup> = emptyList(),
    val filter: MaterialDemandFilter = MaterialDemandFilter.ALL,
    val expanded: String? = null,
    val phase: MaterialDemandPhase = MaterialDemandPhase.Loading,
    val refreshing: Boolean = false,
) {
    /**
     * The groups as drawn, with the filter applied inside each.
     *
     * A group whose rows are all filtered away is dropped rather than left as an empty heading:
     * „Ungedeckt" on a fully covered unit would otherwise print its name over nothing.
     */
    val visible: List<MaterialDemandGroup>
        get() =
            groups
                .map { group -> group.copy(rows = group.rows.filtered(filter)) }
                .filter { it.rows.isNotEmpty() }

    /** How many materials are drawn, over every group. */
    val materialCount: Int get() = visible.sumOf { it.rows.size }

    /** How many of those still have something open — the number the lead line names. */
    val uncoveredCount: Int get() = visible.sumOf { group -> group.rows.count { it.uncovered } }

    /** Whether anything at all came back, before the filter narrowed it. */
    val empty: Boolean get() = groups.all { it.rows.isEmpty() }
}

/**
 * Applies one chip.
 *
 * @param filter the active chip.
 * @return the rows to draw, in the order to draw them.
 */
private fun List<MaterialDemandRow>.filtered(filter: MaterialDemandFilter): List<MaterialDemandRow> =
    when (filter) {
        MaterialDemandFilter.ALL -> sortedBy { it.materialName }
        MaterialDemandFilter.UNCOVERED -> filter { it.uncovered }.sortedBy { it.materialName }
        MaterialDemandFilter.BY_AMOUNT -> sortedByDescending { it.outstanding }
    }

/**
 * Drives „Materialbedarf" — what every open Auftrag together still needs (design ch. 18 §1).
 *
 * The planning surface, read before an Einsatz rather than daily, which is why it is reached from
 * the Auftragsliste's overflow and not from the navigation. It is **read-only for everyone**: the
 * rows are the same rows the order list already shows, so a member without Logistiker sees the
 * screen rather than a lock.
 *
 * @property source the one read behind it.
 */
class MaterialDemandViewModel(
    private val source: MaterialDemandSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MaterialDemandState())

    /** What the screen draws. */
    val state: StateFlow<MaterialDemandState> = mutableState.asStateFlow()

    private var loadedOnce = false

    /** Reads the demand, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        read()
    }

    /** Reads it again after a failure. */
    fun onRetry() {
        read()
    }

    /** Pull-to-refresh. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        read()
    }

    /**
     * A chip was tapped.
     *
     * @param filter which one.
     */
    fun onFilterChanged(filter: MaterialDemandFilter) {
        mutableState.value = mutableState.value.copy(filter = filter)
    }

    /**
     * A row was tapped: it opens **in place** and lists the orders that ask for this material.
     *
     * In place rather than as a jump, because the orders are already in the row's own answer — a
     * navigation would re-read what is on screen.
     *
     * @param row the material.
     */
    fun onToggleExpanded(row: MaterialDemandRow) {
        val current = mutableState.value.expanded
        mutableState.value =
            mutableState.value.copy(expanded = if (current == row.materialId) null else row.materialId)
    }

    /** Performs the read. */
    private fun read() {
        viewModelScope.launch {
            when (val result = source.demand()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            groups = result.value,
                            phase = MaterialDemandPhase.Ready,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the material demand could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = MaterialDemandPhase.Failed(result.error),
                            refreshing = false,
                        )
                }
            }
        }
    }
}

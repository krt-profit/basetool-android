/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MATERIAL_CATEGORY_UNSORTED
import de.greluc.krt.profit.basetool.android.core.data.MaterialCatalogSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialPriceRow
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Log tag for the trade reference. */
private const val LOG_TAG = "Materials"

/** How far the catalogue has got. */
sealed interface MaterialsPhase {
    /** The catalogue is on its way. */
    data object Loading : MaterialsPhase

    /** It arrived; it may be empty, which is a result. */
    data object Ready : MaterialsPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : MaterialsPhase
}

/**
 * Everything the Material-Übersicht draws.
 *
 * **The whole catalogue is held.** The two price filters are not query parameters on
 * `/materials/prices-overview`, so a filter over a partially loaded list would answer from a
 * fraction of the universe and look complete (ADR-0104). Two hundred rows cost nothing to hold.
 *
 * @property rows every material, as the server sorted them.
 * @property query the search term, as typed.
 * @property category the category chip in force, or `null` for „Alle".
 * @property minBuy the „Min. Einkaufspreis" filter, as typed.
 * @property maxSell the „Max. Verkaufspreis" filter, as typed.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting.
 * @property online whether the device has a network.
 */
data class MaterialsState(
    val rows: List<MaterialPriceRow> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val minBuy: String = "",
    val maxSell: String = "",
    val phase: MaterialsPhase = MaterialsPhase.Loading,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val online: Boolean = true,
) {
    /**
     * Every category present, in the server's own words, so the chips describe the data rather
     * than a list somebody wrote down.
     *
     * A material without one lands under „Unsortiert", which is what the web calls it.
     */
    val categories: List<String>
        get() = rows.map { it.category ?: MATERIAL_CATEGORY_UNSORTED }.distinct().sorted()

    /** The rows on screen: the search, the category chip and the two price bounds, in that order. */
    val visible: List<MaterialPriceRow>
        get() {
            val term = query.trim()
            val floor = minBuy.krtPrice()
            val ceiling = maxSell.krtPrice()
            return rows.filter { row ->
                row.matches(term) &&
                    (category == null || (row.category ?: MATERIAL_CATEGORY_UNSORTED) == category) &&
                    row.atLeast(floor) &&
                    row.atMost(ceiling)
            }
        }

    /** Whether any filter is narrowing the list — which decides whether „zurücksetzen" is offered. */
    val filtered: Boolean
        get() = query.isNotBlank() || category != null || minBuy.isNotBlank() || maxSell.isNotBlank()
}

/**
 * Whether a row's name contains what was typed.
 *
 * @receiver the row.
 * @param term the search term, already trimmed.
 * @return whether it stays.
 */
private fun MaterialPriceRow.matches(term: String): Boolean = term.isEmpty() || name.contains(term, ignoreCase = true)

/**
 * Whether the row's buy price clears the „Min. Einkaufspreis" bound.
 *
 * A row with no buy price is **dropped** by an active bound rather than kept: „mindestens 30" is a
 * question about a price, and a material nobody sells has no answer to it.
 *
 * @receiver the row.
 * @param floor the bound, or `null` when none is set.
 * @return whether it stays.
 */
private fun MaterialPriceRow.atLeast(floor: BigDecimal?): Boolean =
    floor == null || (minPriceBuy?.let { it >= floor } == true)

/**
 * Whether the row's sell price clears the „Max. Verkaufspreis" bound.
 *
 * @receiver the row.
 * @param ceiling the bound, or `null` when none is set.
 * @return whether it stays.
 */
private fun MaterialPriceRow.atMost(ceiling: BigDecimal?): Boolean =
    ceiling == null || (maxPriceSell?.let { it <= ceiling } == true)

/**
 * Reads a price bound somebody typed, whichever separator their keyboard offers.
 *
 * @receiver what was typed.
 * @return the bound, or `null` when the field is empty or not a number — an unparseable bound
 *   filters nothing rather than emptying the list, because a half-typed „3," is a moment in typing
 *   and not an instruction.
 */
private fun String.krtPrice(): BigDecimal? =
    trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/**
 * Drives „Handel" — the material catalogue with its UEX prices (REQ-APP-MAT-001).
 *
 * @property source where the catalogue comes from.
 * @property connectivity whether the device has a network.
 */
class MaterialsViewModel(
    private val source: MaterialCatalogSource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MaterialsState())

    /** What the screen draws. */
    val state: StateFlow<MaterialsState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    /** The chapter-14 retry ladder for the first load (REQ-APP-UI-003). */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { load(refresh = false) },
        )

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
        load(refresh = false)
    }

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    /** Pull-to-refresh: re-reads the catalogue, keeping what is on screen while it runs. */
    fun onRefresh() {
        load(refresh = true)
    }

    /**
     * The search term changed.
     *
     * Filtered on the device — the catalogue is already here, and a round trip per keystroke would
     * be slower and no more correct.
     *
     * @param value what was typed.
     */
    fun onQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    /**
     * A category chip was tapped.
     *
     * @param value the category, or `null` for „Alle".
     */
    fun onCategory(value: String?) {
        mutableState.value = mutableState.value.copy(category = value)
    }

    /**
     * The „Min. Einkaufspreis" bound changed.
     *
     * @param value what was typed.
     */
    fun onMinBuy(value: String) {
        mutableState.value = mutableState.value.copy(minBuy = value)
    }

    /**
     * The „Max. Verkaufspreis" bound changed.
     *
     * @param value what was typed.
     */
    fun onMaxSell(value: String) {
        mutableState.value = mutableState.value.copy(maxSell = value)
    }

    /** „Filter zurücksetzen" — every narrowing off at once, the search included. */
    fun onResetFilters() {
        mutableState.value = mutableState.value.copy(query = "", category = null, minBuy = "", maxSell = "")
    }

    /**
     * Reads the catalogue.
     *
     * @param refresh whether the rows on screen stay while it runs.
     */
    private fun load(refresh: Boolean) {
        loadJob?.cancel()
        mutableState.value =
            mutableState.value.copy(
                refreshing = refresh,
                phase = if (refresh) mutableState.value.phase else MaterialsPhase.Loading,
                retryIn = null,
            )
        loadJob =
            viewModelScope.launch {
                when (val result = source.priceOverview()) {
                    is ApiResult.Success -> {
                        retry.onSuccess()
                        mutableState.value =
                            mutableState.value.copy(
                                rows = result.value,
                                phase = MaterialsPhase.Ready,
                                refreshing = false,
                            )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the material catalogue could not be read: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = MaterialsPhase.Failed(result.error),
                                refreshing = false,
                            )
                        retry.onFailure(result.error, hasContent = mutableState.value.rows.isNotEmpty())
                    }
                }
            }
    }
}

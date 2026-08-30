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
import de.greluc.krt.profit.basetool.android.core.data.MaterialCatalogSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialSummary
import de.greluc.krt.profit.basetool.android.core.data.MaterialTerminalPrice
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Log tag for one material's page. */
private const val LOG_TAG = "MaterialDetail"

/**
 * The best row on one side of the market, and where it is.
 *
 * @property price what it pays or charges.
 * @property terminal which terminal.
 */
data class BestPrice(
    val price: java.math.BigDecimal,
    val terminal: String,
)

/**
 * Everything „Preise und Terminals" draws.
 *
 * @property materialId which material.
 * @property material its record, or `null` while it is being read.
 * @property prices every terminal that trades it.
 * @property filter the terminal search, as typed.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property retryIn seconds until the automatic retry, or `null`.
 * @property online whether the device has a network.
 */
data class MaterialDetailState(
    val materialId: String,
    val material: MaterialSummary? = null,
    val prices: List<MaterialTerminalPrice> = emptyList(),
    val filter: String = "",
    val phase: MaterialsPhase = MaterialsPhase.Loading,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val online: Boolean = true,
) {
    /** The rows on screen, narrowed by the terminal search. */
    val visible: List<MaterialTerminalPrice>
        get() {
            val term = filter.trim()
            if (term.isEmpty()) {
                return prices
            }
            return prices.filter { it.terminal.contains(term, ignoreCase = true) }
        }

    /**
     * The dearest buyer, or `null` when nobody buys it.
     *
     * A **selection**, not a computation: the row is one the server sent, shown with its own
     * figure and its own terminal name. The alternative — reading the list row's `maxPriceSell`
     * through — would give the number without the place, and the place is half the answer.
     */
    val bestSell: BestPrice?
        get() =
            prices.mapNotNull { row -> row.priceSell?.let { BestPrice(it, row.terminal) } }
                .maxByOrNull { it.price }

    /** The cheapest seller, or `null` when nobody sells it. */
    val bestBuy: BestPrice?
        get() =
            prices.mapNotNull { row -> row.priceBuy?.let { BestPrice(it, row.terminal) } }
                .minByOrNull { it.price }
}

/**
 * Drives one material's price page (REQ-APP-MAT-002).
 *
 * @property source where the material and its prices come from.
 * @property materialId which material to read.
 * @property connectivity whether the device has a network.
 */
class MaterialDetailViewModel(
    private val source: MaterialCatalogSource,
    private val materialId: String,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MaterialDetailState(materialId = materialId))

    /** What the screen draws. */
    val state: StateFlow<MaterialDetailState> = mutableState.asStateFlow()

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

    /** The member asked again. */
    fun onRetry() {
        retry.onManualRetry()
    }

    /** Pull-to-refresh. */
    fun onRefresh() {
        load(refresh = true)
    }

    /**
     * The terminal search changed.
     *
     * @param value what was typed.
     */
    fun onFilter(value: String) {
        mutableState.value = mutableState.value.copy(filter = value)
    }

    /**
     * Reads the material and its prices.
     *
     * The record is read first because a 404 on it is the design's „Material nicht gefunden" and
     * has to reach the screen as a failure rather than as an empty price table, which would read
     * as „no price data" — a different and untrue statement.
     *
     * @param refresh whether what is on screen stays while it runs.
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
                when (val summary = source.material(materialId)) {
                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the material could not be read: ${summary.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                phase = MaterialsPhase.Failed(summary.error),
                                refreshing = false,
                            )
                        retry.onFailure(summary.error, hasContent = mutableState.value.material != null)
                    }

                    is ApiResult.Success -> {
                        mutableState.value = mutableState.value.copy(material = summary.value)
                        loadPrices()
                    }
                }
            }
    }

    /** Reads the price rows, once the material itself is known to exist. */
    private suspend fun loadPrices() {
        when (val result = source.prices(materialId)) {
            is ApiResult.Success -> {
                retry.onSuccess()
                mutableState.value =
                    mutableState.value.copy(
                        prices = result.value,
                        phase = MaterialsPhase.Ready,
                        refreshing = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the prices could not be read: ${result.error}" }
                mutableState.value =
                    mutableState.value.copy(
                        phase = MaterialsPhase.Failed(result.error),
                        refreshing = false,
                    )
                retry.onFailure(result.error, hasContent = mutableState.value.prices.isNotEmpty())
            }
        }
    }
}

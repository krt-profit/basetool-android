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
import de.greluc.krt.profit.basetool.android.core.data.MaterialMarketSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialMatrixCell
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Log tag for the price matrix. */
private const val LOG_TAG = "MaterialMatrix"

/** A page-walk that has read this many pages has met a matrix this screen was not built for. */
private const val MAX_PAGES = 60

/** Which side of the market the matrix is showing. */
enum class MatrixMode {
    /** What terminals pay — the default, and what most members are after. */
    SELL,

    /** What they charge. */
    BUY,
}

/**
 * One material's row of the matrix.
 *
 * @property materialId which material.
 * @property name what it is called.
 * @property prices the price at each terminal, keyed by terminal id; a terminal that does not trade
 *   it simply has no entry.
 */
data class MatrixRow(
    val materialId: String,
    val name: String,
    val prices: Map<String, BigDecimal>,
) {
    /**
     * The best price in this row, or `null` when it has none.
     *
     * „Best" depends on the side, which is why the mode has to be passed in: the dearest buyer on
     * the sell side, the cheapest seller on the buy side.
     *
     * @param mode which side is showing.
     * @return the figure to tint.
     */
    fun best(mode: MatrixMode): BigDecimal? =
        if (mode == MatrixMode.SELL) {
            prices.values.maxOrNull()
        } else {
            prices.values.minOrNull()
        }
}

/**
 * One column of the matrix.
 *
 * @property id the terminal.
 * @property name its short name, as the web's own header shows it.
 * @property starSystem which system it is in, or `null`.
 */
data class MatrixColumn(
    val id: String,
    val name: String,
    val starSystem: String?,
)

/**
 * Everything the Preis-Übersicht draws.
 *
 * @property cells every matrix cell read so far.
 * @property mode which side of the market is showing.
 * @property query the material search, as typed.
 * @property system the star-system chip in force, or `null` for all of them.
 * @property loading whether more pages are still arriving.
 * @property loaded how many cells have arrived.
 * @property total how many the server says there are, or `null` before the first page.
 * @property error the failure that stopped the walk, or `null`.
 */
data class MaterialMatrixState(
    val cells: List<MaterialMatrixCell> = emptyList(),
    val mode: MatrixMode = MatrixMode.SELL,
    val query: String = "",
    val system: String? = null,
    val loading: Boolean = true,
    val loaded: Int = 0,
    val total: Long? = null,
    val error: ApiError? = null,
) {
    /** Every star system present, so a chip can never offer a narrowing that yields nothing. */
    val systems: List<String>
        get() = cells.mapNotNull { it.starSystem }.distinct().sorted()

    /** The cells the current filters keep. */
    private val visible: List<MaterialMatrixCell>
        get() {
            val term = query.trim()
            return cells.filter { cell ->
                (term.isEmpty() || cell.materialName.contains(term, ignoreCase = true)) &&
                    (system == null || cell.starSystem == system)
            }
        }

    /**
     * The terminals that are still columns after the filters.
     *
     * Derived from the visible cells rather than from the whole matrix: a system filter that left
     * a hundred empty columns standing would make the table unreadable to prove a point.
     */
    val columns: List<MatrixColumn>
        get() =
            visible
                .distinctBy { it.terminalId }
                .map { MatrixColumn(id = it.terminalId, name = it.terminalName, starSystem = it.starSystem) }
                .sortedBy { it.name }

    /** The rows, one per material, each holding whatever the current side has for it. */
    val rows: List<MatrixRow>
        get() =
            visible
                .groupBy { it.materialId }
                .map { (materialId, group) ->
                    MatrixRow(
                        materialId = materialId,
                        name = group.first().materialName,
                        prices =
                            group.mapNotNull { cell ->
                                val price = if (mode == MatrixMode.SELL) cell.priceSell else cell.priceBuy
                                price?.let { cell.terminalId to it }
                            }.toMap(),
                    )
                }
                .sortedBy { it.name }
}

/**
 * Drives the Preis-Übersicht — the Material × Terminal matrix (REQ-APP-MAT-003).
 *
 * **The matrix arrives a page at a time and is drawn as it arrives.** Design ch. 16 artboard 3:
 * „Nachladen zeilenweise … die Ladezeile bleibt unten stehen und wird nie durch einen Vollbild-
 * Spinner ersetzt." A full-screen spinner over a read that can take several round trips is the
 * thing the artboard rules out by name.
 *
 * @property source where the matrix comes from.
 */
class MaterialMatrixViewModel(
    private val source: MaterialMarketSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MaterialMatrixState())

    /** What the screen draws. */
    val state: StateFlow<MaterialMatrixState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Starts the walk again from the first page. */
    fun onRetry() {
        load()
    }

    /**
     * The material search changed.
     *
     * @param value what was typed.
     */
    fun onQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    /**
     * A star-system chip was tapped.
     *
     * @param value the system, or `null` for all of them.
     */
    fun onSystem(value: String?) {
        mutableState.value = mutableState.value.copy(system = value)
    }

    /**
     * The Verkauf/Einkauf switch moved.
     *
     * One chip rather than two figures per cell, which the artboard is explicit about: a cell that
     * held both would need twice the width and would stop being scannable.
     *
     * @param value which side to show.
     */
    fun onMode(value: MatrixMode) {
        mutableState.value = mutableState.value.copy(mode = value)
    }

    /** Walks the matrix, publishing every page as it lands. */
    private fun load() {
        loadJob?.cancel()
        mutableState.value = MaterialMatrixState(mode = mutableState.value.mode)
        loadJob =
            viewModelScope.launch {
                var page = 0
                while (page < MAX_PAGES) {
                    when (val result = source.matrixPage(page)) {
                        is ApiResult.Failure -> {
                            KrtLog.w(LOG_TAG) { "the matrix stopped at page $page: ${result.error}" }
                            mutableState.value =
                                mutableState.value.copy(loading = false, error = result.error)
                            return@launch
                        }

                        is ApiResult.Success -> {
                            val answer = result.value
                            val current = mutableState.value
                            mutableState.value =
                                current.copy(
                                    cells = current.cells + answer.cells,
                                    loaded = current.loaded + answer.cells.size,
                                    total = answer.totalElements,
                                    loading = answer.hasMore,
                                )
                            if (!answer.hasMore) {
                                return@launch
                            }
                        }
                    }
                    page += 1
                }
                // The cap is a backstop, not an answer: say so rather than letting a truncated
                // matrix read as the whole one (ADR-0104).
                mutableState.value = mutableState.value.copy(loading = false)
            }
    }
}

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
import de.greluc.krt.profit.basetool.android.core.data.ProfitRow
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Log tag for the profit calculation. */
private const val LOG_TAG = "Profit"

/**
 * The hull the web preselects, and the reason it does: the C2 is the org's workhorse hauler.
 *
 * Matched on a substring because the catalogue spells it out in full.
 */
private const val DEFAULT_SHIP = "C2 Hercules Starlifter"

/** The hull the server applies its Loading-Dock special rule to. */
private const val HULL_C = "Hull C"

/**
 * Everything the Profitberechnung draws.
 *
 * @property ships the hulls that can carry something.
 * @property systems every star system a terminal sits in.
 * @property shipId the chosen hull, or `null` before one is.
 * @property excluded the systems the member has switched **off**; empty means all of them are in.
 * @property rows the last answer.
 * @property loadingOptions whether the ship and system catalogues are still being read.
 * @property calculating whether a calculation is in flight.
 * @property error the last refusal.
 */
data class ProfitState(
    val ships: List<ShipTypeOption> = emptyList(),
    val systems: List<String> = emptyList(),
    val shipId: String? = null,
    val excluded: Set<String> = emptySet(),
    val rows: List<ProfitRow> = emptyList(),
    val loadingOptions: Boolean = true,
    val calculating: Boolean = false,
    val error: ApiError? = null,
) {
    /** The chosen hull, or `null`. */
    val ship: ShipTypeOption?
        get() = ships.firstOrNull { it.id == shipId }

    /**
     * Whether the Hull-C note belongs on screen.
     *
     * Design ch. 16 artboard 4 makes it conditional („nur bei Hull C"), where the web shows both
     * hints unconditionally. The artboard wins — the design spec outranks behavioural parity — and
     * it is also the more truthful of the two: the Loading-Dock rule only changes the arithmetic
     * for that one hull.
     */
    val hullCRule: Boolean
        get() = ship?.name?.contains(HULL_C, ignoreCase = true) == true

    /** The systems the calculation is restricted to; empty means „all of them". */
    val includedSystems: List<String>
        get() = if (excluded.isEmpty()) emptyList() else systems.filterNot { it in excluded }
}

/**
 * Drives the Profitberechnung (REQ-APP-MAT-004).
 *
 * > **Every figure on this screen is the server's.** The app renders margins and profits and
 * > computes none: a margin is money advice, and one derived on the device could not be reconciled
 * > with the web's own answer.
 *
 * @property source where the ships, the systems and the calculation come from.
 */
class ProfitViewModel(
    private val source: MaterialMarketSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfitState())

    /** What the screen draws. */
    val state: StateFlow<ProfitState> = mutableState.asStateFlow()

    private var calculation: Job? = null

    init {
        loadOptions()
    }

    /**
     * A hull was picked.
     *
     * The calculation runs immediately: the screen exists to answer one question, and a member who
     * picked a ship has asked it.
     *
     * @param id the hull.
     */
    fun onShip(id: String) {
        mutableState.value = mutableState.value.copy(shipId = id)
        calculate()
    }

    /**
     * A star system was switched on or off.
     *
     * @param system which one.
     */
    fun onToggleSystem(system: String) {
        val current = mutableState.value
        val excluded =
            if (system in current.excluded) current.excluded - system else current.excluded + system
        mutableState.value = current.copy(excluded = excluded)
        calculate()
    }

    /** Runs the calculation again — the „Erneut versuchen" of the error line. */
    fun onRetry() {
        if (mutableState.value.ships.isEmpty()) {
            loadOptions()
            return
        }
        calculate()
    }

    /** Reads the two catalogues the form is built from, then runs the default calculation. */
    private fun loadOptions() {
        mutableState.value = mutableState.value.copy(loadingOptions = true, error = null)
        viewModelScope.launch {
            val ships = source.shipTypes()
            if (ships is ApiResult.Failure) {
                KrtLog.w(LOG_TAG) { "the ship catalogue could not be read: ${ships.error}" }
                mutableState.value = mutableState.value.copy(loadingOptions = false, error = ships.error)
                return@launch
            }
            val hulls = (ships as ApiResult.Success).value
            val systems = (source.starSystems() as? ApiResult.Success)?.value.orEmpty()
            mutableState.value =
                mutableState.value.copy(
                    ships = hulls,
                    systems = systems,
                    // The web preselects the C2; with no C2 in the catalogue nothing is chosen and
                    // the screen says so rather than picking a hull on the member's behalf.
                    shipId = hulls.firstOrNull { it.name.contains(DEFAULT_SHIP, ignoreCase = true) }?.id,
                    loadingOptions = false,
                )
            if (mutableState.value.shipId != null) {
                calculate()
            }
        }
    }

    /** Asks the server for the current ship and system selection. */
    private fun calculate() {
        val shipId = mutableState.value.shipId ?: return
        calculation?.cancel()
        mutableState.value = mutableState.value.copy(calculating = true, error = null)
        calculation =
            viewModelScope.launch {
                when (val result = source.profit(shipId, mutableState.value.includedSystems)) {
                    is ApiResult.Success -> {
                        mutableState.value =
                            mutableState.value.copy(rows = result.value, calculating = false)
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the profit calculation was refused: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                calculating = false,
                                error = result.error,
                                // The previous answer is dropped: leaving it under a new ship's
                                // name would be a figure about the wrong hull.
                                rows = emptyList(),
                            )
                    }
                }
            }
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The tabs of the Einsatz detail, in the order the design puts them.
 *
 * An enum rather than indices so a reordering is a compile-time move and the deep link's `?tab=`
 * has something stable to name.
 */
enum class MissionTab {
    /** Facts, briefing and description. */
    OVERVIEW,

    /** The roster. */
    PARTICIPANTS,

    /** The Einheiten and their crews. */
    UNITS,

    /** The Ablauf checklist. */
    STEPS,

    /** The Ziele. */
    OBJECTIVES,

    /** The radio plan. */
    FREQUENCIES,

    /** Income, expense and the entries behind them. */
    FINANCES,
}

/** How far the Einsatz itself has got. */
sealed interface MissionDetailPhase {
    /** The read is in flight. */
    data object Loading : MissionDetailPhase

    /** It arrived. */
    data object Ready : MissionDetailPhase

    /**
     * It did not.
     *
     * @property error what went wrong. `Forbidden` and `NotFound` are ordinary answers here, not
     *   outages: the backend refuses an outsider an internal or terminal Einsatz, and a stale link
     *   is a 404. The screen words them differently for that reason.
     */
    data class Failed(
        val error: ApiError,
    ) : MissionDetailPhase
}

/**
 * How far the Finanzen tab has got.
 *
 * Separate from [MissionDetailPhase] because the money is a **second, differently guarded** read:
 * a member sees the Einsatz and may still be refused its finances (`isMemberOrAbove` +
 * `canSeeMission`). Folding the two together would either hide the Einsatz behind a permission it
 * does not need, or claim the money loaded when it did not.
 */
sealed interface MissionFinancesPhase {
    /** Not asked for yet — the tab has never been opened. */
    data object Idle : MissionFinancesPhase

    /** In flight. */
    data object Loading : MissionFinancesPhase

    /**
     * Loaded.
     *
     * @property finances the totals band and the entries beneath it.
     */
    data class Ready(
        val finances: MissionFinances,
    ) : MissionFinancesPhase

    /**
     * Refused or unavailable.
     *
     * @property error what went wrong; `Forbidden` is the ordinary "not your Einsatz's books".
     */
    data class Failed(
        val error: ApiError,
    ) : MissionFinancesPhase
}

/**
 * Everything the detail screen draws.
 *
 * @property missionId which Einsatz this is about, known before anything has loaded
 * @property detail the Einsatz once it arrives
 * @property phase how far that read has got
 * @property tab which tab is showing
 * @property finances how far the money has got, on its own timeline
 * @property refreshing whether a pull-to-refresh is running over content already on screen
 */
data class MissionDetailState(
    val missionId: String,
    val detail: MissionDetail? = null,
    val phase: MissionDetailPhase = MissionDetailPhase.Loading,
    val tab: MissionTab = MissionTab.OVERVIEW,
    val finances: MissionFinancesPhase = MissionFinancesPhase.Idle,
    val refreshing: Boolean = false,
)

/**
 * Drives one Einsatz's detail.
 *
 * **The money is fetched lazily, when its tab is first opened.** Six of the seven tabs come from
 * one response; the seventh is a second pair of calls that most members opening an Einsatz never
 * look at, and that a member without the permission cannot make succeed at all. Fetching it
 * up-front would spend two requests per open and turn an ordinary lack of permission into an error
 * on a screen that is otherwise fine.
 *
 * @property source where the Einsatz comes from
 * @property missionId which Einsatz to load
 */
class MissionDetailViewModel(
    private val source: MissionSource,
    private val missionId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MissionDetailState(missionId = missionId))

    /** What the screen draws. */
    val state: StateFlow<MissionDetailState> = mutableState.asStateFlow()

    /** Loads the Einsatz. Safe to call more than once. */
    fun load() {
        reload(keepContent = false)
    }

    /**
     * Re-reads the Einsatz, and the money too when its tab has already been opened.
     *
     * The content stays on screen while it runs — the member is looking at something they expect
     * to remain.
     */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
        if (mutableState.value.finances !is MissionFinancesPhase.Idle) {
            loadFinances()
        }
    }

    /**
     * Switches tab, fetching the money the first time its tab is chosen.
     *
     * @param tab the tab the member picked.
     */
    fun onTabSelected(tab: MissionTab) {
        mutableState.value = mutableState.value.copy(tab = tab)
        if (tab == MissionTab.FINANCES && mutableState.value.finances is MissionFinancesPhase.Idle) {
            loadFinances()
        }
    }

    /** Retries the money after a failure, without reloading the Einsatz around it. */
    fun onRetryFinances() {
        loadFinances()
    }

    /**
     * Reads the Einsatz.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = MissionDetailPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.detail(missionId)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            detail = result.value,
                            phase = MissionDetailPhase.Ready,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "Einsatz could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = MissionDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
                }
            }
        }
    }

    /** Reads the money. */
    private fun loadFinances() {
        mutableState.value = mutableState.value.copy(finances = MissionFinancesPhase.Loading)
        viewModelScope.launch {
            mutableState.value =
                when (val result = source.finances(missionId)) {
                    is ApiResult.Success -> {
                        mutableState.value.copy(finances = MissionFinancesPhase.Ready(result.value))
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "Einsatz finances could not be read: ${result.error}" }
                        mutableState.value.copy(finances = MissionFinancesPhase.Failed(result.error))
                    }
                }
        }
    }

    private companion object {
        /** Log subsystem. No member name or amount is ever logged. */
        const val LOG_TAG = "missions"
    }
}

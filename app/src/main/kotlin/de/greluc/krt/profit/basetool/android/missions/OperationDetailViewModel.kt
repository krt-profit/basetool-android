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
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.OperationOverview
import de.greluc.krt.profit.basetool.android.core.data.OperationPayout
import de.greluc.krt.profit.basetool.android.core.data.OperationSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far the Operation detail has got. */
sealed interface OperationDetailPhase {
    /** The reads are in flight. */
    data object Loading : OperationDetailPhase

    /** They arrived. */
    data object Ready : OperationDetailPhase

    /**
     * They did not.
     *
     * @property error what went wrong. `Forbidden` is an ordinary answer — an Operation outside the
     *   caller's scope — and `NotFound` is a stale link; the screen words them differently.
     */
    data class Failed(
        val error: ApiError,
    ) : OperationDetailPhase
}

/**
 * Everything the Operation detail draws.
 *
 * @property operationId which Operation, known before anything has loaded
 * @property overview the head, roll-up and payouts once they arrive
 * @property phase how far the read has got
 * @property myUserId the caller's backend user id, or `null` while unknown or unreadable
 * @property refreshing whether a pull-to-refresh is running over content already on screen
 */
data class OperationDetailState(
    val operationId: String,
    val overview: OperationOverview? = null,
    val phase: OperationDetailPhase = OperationDetailPhase.Loading,
    val myUserId: String? = null,
    val missionManager: Boolean = false,
    val saving: Boolean = false,
    val online: Boolean = true,
    val error: ApiError? = null,
    val refreshing: Boolean = false,
) {
    /**
     * The caller's own payout row, when it can be identified.
     *
     * `null` covers three different situations that the screen renders identically and correctly:
     * the identity read failed, the caller took part in no Einsatz of this Operation, or the rows
     * are not loaded yet. In all three "Dein Anteil" has nothing truthful to say, and guessing —
     * by name, say — would show a member someone else's money.
     */
    val myPayout: OperationPayout?
        get() = myUserId?.let { id -> overview?.payouts?.rows?.firstOrNull { it.participantId == id } }
}

/**
 * Drives one Operation's detail.
 *
 * **One load, three reads, one outcome.** Unlike the Einsatz detail — whose Finanzen tab is behind
 * a second permission and therefore has its own state — every endpoint here carries the identical
 * `canSeeOperation` gate. A member who may open the Operation may read all of it, so a split state
 * would model a case the server cannot produce; and the head itself is built from the payouts,
 * because the participant count and the per-head share come from there.
 *
 * **The identity read is separate and never fatal.** It answers "which of these rows is mine",
 * which is a nicety on a screen whose subject is the Operation. If it fails, the screen loses one
 * line and keeps everything else.
 *
 * @property source where the Operation comes from
 * @property identity supplies the caller's backend user id
 * @property operationId which Operation to load
 */
class OperationDetailViewModel(
    private val source: OperationSource,
    private val identity: IdentitySource,
    connectivity: Connectivity,
    private val operationId: String,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OperationDetailState(operationId = operationId))

    /** What the screen draws. */
    val state: StateFlow<OperationDetailState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
        observeLiveSync(liveSync, setOf(LiveSyncTopic.operation(operationId))) { _ ->
            // The Operation is one read, and every section of its room feeds it — including the two
            // that are cross-published from an Einsatz underneath it, which is exactly the case a
            // member on this screen cannot otherwise see happening.
            reload(keepContent = true)
        }
    }

    /**
     * Confirms one participant's payout, or takes that back.
     *
     * **The app cannot tell whether the caller may take one back.** Confirming needs the
     * mission-manager grant, which `/users/me` answers; rescinding needs an officer or an admin on
     * top, which it does not. So both are offered to a mission manager and a refusal on the second
     * is named rather than predicted.
     *
     * @param payout the row.
     */
    fun onTogglePaidOut(payout: OperationPayout) {
        val current = mutableState.value
        val key = payout.participantId
        val writable = current.online && !current.saving
        if (key == null || !current.missionManager || !writable) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = source.setPaidOut(operationId, key, !payout.paidOut)) {
                is ApiResult.Success -> {
                    // The Operation is re-read rather than the row patched: the payout totals move
                    // with a confirmation, and a patched row under a stale total is two numbers
                    // that disagree.
                    mutableState.value = mutableState.value.copy(saving = false, error = null)
                    reload(keepContent = true)
                    publishLiveSync(
                        liveSync,
                        LiveSyncTopic.operation(operationId),
                        LiveSyncSections.OPERATION_PAYOUT,
                        LiveSyncSections.OPERATION_FINANCE,
                    )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the payout could not be confirmed: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /** Loads the Operation. Safe to call more than once. */
    fun load() {
        reload(keepContent = false)
        resolveIdentity()
    }

    /**
     * Re-reads the Operation, keeping what is on screen while it runs.
     *
     * The identity is not re-read: it is cached for the process and cannot change without a new
     * session.
     */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
        if (mutableState.value.myUserId == null) {
            // Only retried when it is still missing — a member whose first attempt failed gets
            // another chance out of the gesture they already made.
            resolveIdentity()
        }
    }

    /**
     * Reads the Operation.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = OperationDetailPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.overview(operationId)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            overview = result.value,
                            phase = OperationDetailPhase.Ready,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "Operation could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = OperationDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
                }
            }
        }
    }

    /** Reads the caller's own user id, so their payout row can be pointed at. */
    private fun resolveIdentity() {
        viewModelScope.launch {
            when (val result = identity.me()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            myUserId = result.value.userId,
                            missionManager = result.value.missionManager,
                        )
                }

                is ApiResult.Failure -> {
                    // Deliberately not surfaced. The screen's subject is the Operation; losing the
                    // "Dein Anteil" line is a smaller failure than an error over content that
                    // loaded fine.
                    KrtLog.w(LOG_TAG) { "own user id could not be read: ${result.error}" }
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No member name or amount is ever logged. */
        const val LOG_TAG = "operations"
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.AccountGateSource
import de.greluc.krt.profit.basetool.android.core.data.ApprovalStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How often the gate re-asks while the member waits. Design ch. 04: "Automatische Prüfung alle 60 s". */
private val POLL_INTERVAL: Duration = 60.seconds

/**
 * Decides what a signed-in member is allowed to see, and keeps asking while the answer is "not yet".
 *
 * The polling is the whole reason this is a `ViewModel` rather than state hoisted into the
 * composable. The app has **no push channel** (resolved decision Q2), so an approval that lands
 * while the member is staring at the waiting screen reaches them only if something asks again — and
 * a loop tied to a composition would restart on every recomposition and die on every rotation.
 *
 * The loop runs **only** while the gate is closed. Polling an endpoint after being admitted would
 * be a request per minute, per install, forever, for an answer that no longer changes anything.
 *
 * @property source reads the approval status
 */
class AccountGateViewModel(
    private val source: AccountGateSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AccountGateState>(AccountGateState.Checking)

    /** What the gate currently knows. */
    val state: StateFlow<AccountGateState> = mutableState.asStateFlow()

    private var poll: Job? = null

    /**
     * Reads the gate once and starts polling if it is closed.
     *
     * Safe to call repeatedly — a second call while a poll is already running is ignored rather
     * than starting a competing loop, because the caller is a `LaunchedEffect` whose key may change
     * for reasons that have nothing to do with the gate.
     */
    fun start() {
        if (poll?.isActive == true) {
            return
        }
        poll =
            viewModelScope.launch {
                while (true) {
                    val open = readOnce()
                    if (open) {
                        return@launch
                    }
                    delay(POLL_INTERVAL)
                }
            }
    }

    /**
     * Re-reads the gate now, without disturbing the poll's schedule.
     *
     * This is what the "Status aktualisieren" button does. It deliberately does **not** restart the
     * timer: a member tapping it repeatedly would otherwise be able to shorten their own polling
     * interval to nothing.
     */
    fun refresh() {
        viewModelScope.launch {
            mutableState.update { current ->
                if (current is AccountGateState.Blocked) current.copy(refreshing = true) else current
            }
            readOnce()
        }
    }

    /**
     * Performs one read and publishes the result.
     *
     * A failed read while the gate is already known to be closed **keeps the last known state** and
     * only clears the spinner. The alternative — replacing the waiting screen with an error — would
     * make a lost minute of connectivity look like the account had been reset, which is the more
     * alarming of two readings and the wrong one.
     *
     * @return `true` when the member may pass and the poll should stop
     */
    private suspend fun readOnce(): Boolean {
        val result = source.registrationStatus()
        val next =
            when (result) {
                is ApiResult.Success -> {
                    if (result.value.isCleared) {
                        AccountGateState.Cleared
                    } else {
                        AccountGateState.Blocked(status = result.value, refreshing = false)
                    }
                }

                is ApiResult.Failure -> {
                    when (val previous = mutableState.value) {
                        is AccountGateState.Blocked -> previous.copy(refreshing = false)
                        else -> AccountGateState.Unavailable(result.error)
                    }
                }
            }
        mutableState.value = next
        return next is AccountGateState.Cleared
    }
}

/**
 * What the app may show a signed-in member.
 */
sealed interface AccountGateState {
    /** The first read has not answered yet. */
    data object Checking : AccountGateState

    /** Approved — the app proper may open. */
    data object Cleared : AccountGateState

    /**
     * Not approved. The waiting screen stays up and the poll keeps running.
     *
     * @property status why the member is held — pending, rejected, or a status this build predates
     * @property refreshing whether a manual re-check is in flight
     */
    data class Blocked(
        val status: ApprovalStatus,
        val refreshing: Boolean,
    ) : AccountGateState

    /**
     * The gate could not be read at all, and nothing better is known.
     *
     * Distinct from [Blocked]: this member may well be approved, so the screen must not claim they
     * are waiting for anything.
     *
     * @property error what went wrong
     */
    data class Unavailable(
        val error: ApiError,
    ) : AccountGateState
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.RetryBackoff
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
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How often the gate re-asks while the member waits. Design ch. 04: "Automatische Prüfung alle 60 s". */
private val POLL_INTERVAL: Duration = 60.seconds

/**
 * Decides what a signed-in member may see and keeps polling while the answer is "not yet".
 *
 * The app has no push channel, so an approval is noticed only by asking again. The loop lives in the
 * `ViewModel` to survive recomposition and rotation, and runs only while the gate is closed.
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
    private var attempts = 0

    /**
     * Reads the gate once and starts polling if it is closed.
     *
     * Idempotent: a call while a poll is already running is ignored.
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
                    if (mutableState.value is AccountGateState.Unavailable) {
                        countDown(RetryBackoff.next(attempts).inWholeSeconds.toInt())
                        attempts++
                    } else {
                        attempts = 0
                        delay(POLL_INTERVAL)
                    }
                }
            }
    }

    /**
     * Ticks the visible countdown down to the next automatic attempt, on the shared `RetryBackoff` ladder.
     *
     * @param seconds how long to wait before the caller asks again
     */
    private suspend fun countDown(seconds: Int) {
        for (left in seconds downTo 1) {
            mutableState.update { current ->
                if (current is AccountGateState.Unavailable) current.copy(secondsUntilRetry = left) else current
            }
            delay(SECOND_MS)
        }
        mutableState.update { current ->
            if (current is AccountGateState.Unavailable) current.copy(secondsUntilRetry = null) else current
        }
    }

    /**
     * Re-reads the gate now, for the "Status aktualisieren" button.
     *
     * While the account is waiting for approval the poll's schedule is left alone. While the gate is
     * unreachable the backoff ladder is reset and the poll restarted, never run alongside.
     */
    fun refresh() {
        if (state.value is AccountGateState.Unavailable) {
            attempts = 0
            mutableState.update { current ->
                if (current is AccountGateState.Unavailable) current.copy(failures = 0) else current
            }
            poll?.cancel()
            poll = null
            start()
            return
        }
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
     * A failed read while the gate is known to be closed keeps the last known state and only clears the
     * spinner.
     *
     * @return `true` when the member may pass and the poll should stop
     */
    private suspend fun readOnce(): Boolean {
        mutableState.update { current ->
            if (current is AccountGateState.Unavailable) {
                current.copy(attempting = true, secondsUntilRetry = null)
            } else {
                current
            }
        }
        val result =
            withTimeoutOrNull(ATTEMPT_TIMEOUT) { source.registrationStatus() }
                ?: ApiResult.Failure(ApiError.Network(SocketTimeoutException("gate attempt timed out")))
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
                        is AccountGateState.Blocked -> {
                            previous.copy(refreshing = false)
                        }

                        is AccountGateState.Unavailable -> {
                            previous.copy(
                                error = result.error,
                                attempting = false,
                                failures = previous.failures + 1,
                            )
                        }

                        else -> {
                            AccountGateState.Unavailable(result.error, failures = 1)
                        }
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
        /**
         * Seconds until the app asks again on its own, or `null` while an attempt is running.
         */
        val secondsUntilRetry: Int? = null,
        /**
         * Whether an attempt is in flight right now; distinct from a `null` countdown.
         */
        val attempting: Boolean = false,
        /**
         * How many attempts have failed in a row; from the third on, the screen names the Org-Discord as a
         * fallback channel.
         */
        val failures: Int = 0,
    ) : AccountGateState
}

/** One second of countdown. */
private const val SECOND_MS = 1_000L

/** Longest a single gate attempt is waited out before the ladder schedules the next. */
private val ATTEMPT_TIMEOUT = 10.seconds

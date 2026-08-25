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
    private var attempts = 0

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
                    if (mutableState.value is AccountGateState.Unavailable) {
                        // An unreachable gate is paced by the outage ladder, not by the approval
                        // poll's minute: a member who cannot get an answer at all should not have
                        // to wait a full minute to find out the outage is over.
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
     * Ticks the visible countdown down to the next automatic attempt.
     *
     * The ladder is `RetryBackoff`'s — 3 -> 6 -> 12 -> 30 s, the last step repeating — the same one
     * every other waiting state in this app uses, so the gate does not invent a second rhythm for
     * the same kind of wait (design ch. 14, artboard 3).
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
     * Re-reads the gate now, without disturbing the poll's schedule.
     *
     * This is what the "Status aktualisieren" button does. While the account is merely *waiting*
     * for approval it deliberately does **not** restart the timer: a member tapping it repeatedly
     * would otherwise be able to shorten their own polling interval to nothing.
     *
     * An **unreachable** gate is the opposite case and design ch. 14 says so — a manual attempt
     * resets the backoff ladder. Pressing the button is new information there, and inheriting a
     * thirty-second wait from automatic attempts the member did not make would make their own
     * attempt feel ignored. The poll is restarted rather than run alongside, so the app never has
     * two questions in flight for one answer.
     */
    fun refresh() {
        if (state.value is AccountGateState.Unavailable) {
            attempts = 0
            // The failure streak drives one line of copy, and a manual attempt is a fresh start for
            // it too: a member who just pressed the button has not "kept getting no answer" yet.
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
     * A failed read while the gate is already known to be closed **keeps the last known state** and
     * only clears the spinner. The alternative — replacing the waiting screen with an error — would
     * make a lost minute of connectivity look like the account had been reset, which is the more
     * alarming of two readings and the wrong one.
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
        // Ten seconds, then give up on THIS attempt and let the ladder schedule the next (design
        // ch. 14: "Versuch wartet max. 10 s"). The HTTP client's own timeout is longer, and a
        // member watching a countdown that has already reached zero has no way to tell a slow
        // answer from a dead one.
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
         *
         * Design ch. 14 artboard 3: the screen keeps trying without being told to. A state whose
         * only way forward is a button the member has to keep pressing turns a passing outage into
         * a chore, and this one is passing by definition — nothing the member did caused it.
         */
        val secondsUntilRetry: Int? = null,
        /**
         * Whether an attempt is in flight right now.
         *
         * Its own flag rather than "the countdown is null": the two are the same only by accident,
         * and the screen says different things about them — a wait is a wait, an attempt is work.
         */
        val attempting: Boolean = false,
        /**
         * How many attempts have failed in a row.
         *
         * Design ch. 14 adds one line after the third — the Org-Discord as a fallback channel — and
         * is emphatic that nothing else changes: no red, no error face. The state stays *waiting*,
         * not *blame*.
         */
        val failures: Int = 0,
    ) : AccountGateState
}

/** One second of countdown. */
private const val SECOND_MS = 1_000L

/** Longest a single gate attempt is waited out before the ladder schedules the next. */
private val ATTEMPT_TIMEOUT = 10.seconds

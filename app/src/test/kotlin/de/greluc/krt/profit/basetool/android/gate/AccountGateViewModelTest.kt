/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.AccountGateSource
import de.greluc.krt.profit.basetool.android.core.data.ApprovalStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the account gate's polling schedule against a scripted source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val started = mutableListOf<AccountGateViewModel>()

    /**
     * A source that answers from a script and counts how often it was asked.
     *
     * @property answers one entry per call; the last entry repeats once the script runs out
     */
    private class ScriptedSource(
        private val answers: List<ApiResult<ApprovalStatus>>,
    ) : AccountGateSource {
        var calls = 0
            private set

        override suspend fun registrationStatus(): ApiResult<ApprovalStatus> {
            val answer = answers.getOrElse(calls) { answers.last() }
            calls++
            return answer
        }
    }

    /**
     * Installs the test dispatcher as `Dispatchers.Main`, which `viewModelScope` uses.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * Restores the real main dispatcher.
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Runs a test body and then cancels every view model's scope inside `runTest`, so the gate's endless polling loop
     * cannot hang the test.
     *
     * @param body the test.
     */
    private fun gateTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                started.forEach { it.viewModelScope.cancel() }
                started.clear()
            }
        }

    /**
     * Builds a view model and registers it for cancellation.
     *
     * @param source the scripted source
     * @return the view model under test
     */
    private fun viewModelFor(source: AccountGateSource): AccountGateViewModel =
        AccountGateViewModel(source).also(started::add)

    /**
     * An approved account clears the gate on the first read.
     */
    @Test
    fun `an approved account clears the gate`() =
        gateTest {
            val viewModel = viewModelFor(ScriptedSource(listOf(ApiResult.Success(ApprovalStatus.ACTIVE))))

            viewModel.start()
            advanceTimeBy(1.seconds)

            assertEquals(AccountGateState.Cleared, viewModel.state.value)
        }

    /**
     * Once cleared, the gate stops polling.
     */
    @Test
    fun `polling stops once the member is in`() =
        gateTest {
            val source = ScriptedSource(listOf(ApiResult.Success(ApprovalStatus.ACTIVE)))
            val viewModel = viewModelFor(source)

            viewModel.start()
            advanceTimeBy(10.minutes)

            assertEquals(1, source.calls)
        }

    /**
     * A pending account keeps the gate closed and keeps asking.
     */
    @Test
    fun `a pending account is polled again`() =
        gateTest {
            val source = ScriptedSource(listOf(ApiResult.Success(ApprovalStatus.PENDING)))
            val viewModel = viewModelFor(source)

            viewModel.start()
            advanceTimeBy(1.seconds)
            assertEquals(AccountGateState.Blocked(ApprovalStatus.PENDING, refreshing = false), viewModel.state.value)

            val elapsedMinutes = 3
            advanceTimeBy(elapsedMinutes.minutes)
            assertEquals(1 + elapsedMinutes, source.calls)
        }

    /**
     * An approval that lands mid-wait is picked up by the poll and opens the gate.
     */
    @Test
    fun `an approval arriving later opens the gate`() =
        gateTest {
            val viewModel =
                viewModelFor(
                    ScriptedSource(
                        listOf(
                            ApiResult.Success(ApprovalStatus.PENDING),
                            ApiResult.Success(ApprovalStatus.ACTIVE),
                        ),
                    ),
                )

            viewModel.start()
            advanceTimeBy(1.seconds)
            assertTrue(viewModel.state.value is AccountGateState.Blocked)

            advanceTimeBy(1.minutes)

            assertEquals(AccountGateState.Cleared, viewModel.state.value)
        }

    /**
     * A failed re-read while waiting keeps the waiting screen rather than showing an error.
     */
    @Test
    fun `a failed re-read keeps the last known state`() =
        gateTest {
            val viewModel =
                viewModelFor(
                    ScriptedSource(
                        listOf(
                            ApiResult.Success(ApprovalStatus.PENDING),
                            ApiResult.Failure(ApiError.Network(IOException("offline"))),
                        ),
                    ),
                )

            viewModel.start()
            advanceTimeBy(1.seconds)
            advanceTimeBy(1.minutes)

            assertEquals(AccountGateState.Blocked(ApprovalStatus.PENDING, refreshing = false), viewModel.state.value)
        }

    /**
     * A failure on the very first read has nothing to fall back on, so it surfaces.
     */
    @Test
    fun `a failed first read is reported`() =
        gateTest {
            val failure = ApiError.Network(IOException("offline"))
            val viewModel = viewModelFor(ScriptedSource(listOf(ApiResult.Failure(failure))))

            viewModel.start()
            runCurrent()

            val state = viewModel.state.value
            assertTrue(state is AccountGateState.Unavailable)
            assertEquals(failure, (state as AccountGateState.Unavailable).error)
        }

    /**
     * An unreachable gate retries on its own along the 3, 6, 12, 30 s ladder (design ch. 14, artboard 3).
     */
    @Test
    fun `an unreachable gate retries on the backoff ladder`() =
        gateTest {
            val source = ScriptedSource(listOf(ApiResult.Failure(ApiError.Network(IOException("offline")))))
            val viewModel = viewModelFor(source)

            viewModel.start()
            runCurrent()
            assertEquals(1, source.calls)

            advanceTimeBy(3.seconds + 1.seconds)
            assertEquals(2, source.calls)

            advanceTimeBy(3.seconds)
            assertEquals(2, source.calls)

            advanceTimeBy(3.seconds + 1.seconds)
            assertEquals(THIRD_ATTEMPT, source.calls)
        }

    /**
     * The wait is counted down on screen, so the member can see the app is still working.
     */
    @Test
    fun `the wait until the next attempt is visible`() =
        gateTest {
            val viewModel =
                viewModelFor(ScriptedSource(listOf(ApiResult.Failure(ApiError.Network(IOException("offline"))))))

            viewModel.start()
            advanceTimeBy(1.seconds)

            val state = viewModel.state.value as AccountGateState.Unavailable
            assertTrue("counting down, saw ${state.secondsUntilRetry}", (state.secondsUntilRetry ?: 0) in FIRST_RUNG)
        }

    /**
     * A manual attempt starts the ladder over.
     *
     * The rung reached by automatic attempts the member did not make must not be inherited by the
     * one they did: pressing the button and then waiting thirty seconds reads as being ignored.
     */
    @Test
    fun `a manual retry resets the ladder`() =
        gateTest {
            val source = ScriptedSource(listOf(ApiResult.Failure(ApiError.Network(IOException("offline")))))
            val viewModel = viewModelFor(source)

            viewModel.start()
            advanceTimeBy(3.seconds + 6.seconds + 2.seconds)
            val climbed = source.calls
            assertEquals(THIRD_ATTEMPT, climbed)

            viewModel.refresh()
            runCurrent()
            assertEquals(climbed + 1, source.calls)

            advanceTimeBy(3.seconds + 1.seconds)
            assertEquals(climbed + 2, source.calls)
        }

    /**
     * The outage ladder does not survive the outage.
     *
     * A gate that answers again is polled on the approval interval, not every few seconds, or a
     * member waiting for approval behind a recovered outage would keep the fast rhythm forever.
     */
    @Test
    fun `a recovered gate returns to the approval interval`() =
        gateTest {
            val source =
                ScriptedSource(
                    listOf(
                        ApiResult.Failure(ApiError.Network(IOException("offline"))),
                        ApiResult.Success(ApprovalStatus.PENDING),
                    ),
                )
            val viewModel = viewModelFor(source)

            viewModel.start()
            advanceTimeBy(3.seconds + 1.seconds)
            assertTrue(viewModel.state.value is AccountGateState.Blocked)

            advanceTimeBy(30.seconds)
            assertEquals(2, source.calls)
        }

    /**
     * A second `start()` does not add a competing loop.
     *
     * The caller is a `LaunchedEffect` whose key can change for reasons unrelated to the gate, and
     * two loops would double the request rate for as long as the member waits.
     */
    @Test
    fun `start is idempotent while a poll is running`() =
        gateTest {
            val source = ScriptedSource(listOf(ApiResult.Success(ApprovalStatus.PENDING)))
            val viewModel = viewModelFor(source)

            viewModel.start()
            advanceTimeBy(1.seconds)
            viewModel.start()
            advanceTimeBy(1.minutes)

            assertEquals(2, source.calls)
        }
}

/** How often the gate has been asked once the ladder has climbed two rungs. */
private const val THIRD_ATTEMPT = 3

/** The seconds a countdown on the ladder's first rung can legitimately be showing. */
private val FIRST_RUNG = 1..3

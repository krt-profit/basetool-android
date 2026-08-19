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
 * The gate's polling behaviour, which is the part with no push channel behind it.
 *
 * The source is scripted rather than mocked: the test needs to count invocations, and a queue of
 * answers reads more directly than a stubbing DSL. No socket is involved, which is the point —
 * every property asserted here is about *scheduling*, and driving it through a real HTTP stack
 * would be testing OkHttp.
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
     * Runs a test body and then ends every view model's scope — **inside** `runTest`.
     *
     * Polling a closed gate is an endless loop by design: it runs until the member is approved or
     * the activity goes away. `runTest` waits for the coroutines on its scheduler before it
     * returns, so a loop still running does not fail the test — it **hangs** it, advancing virtual
     * time forever without the wall-clock timeout ever getting a look in. An `@After` block cannot
     * rescue that, because `@After` only runs once `runTest` has already returned. Cancelling here
     * is what the framework does to a real view model through `onCleared()`.
     *
     * @param body the test
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
     * Once cleared, nothing asks again.
     *
     * Without this the app would send one request per minute, per install, forever, for an answer
     * that can no longer change what is on screen.
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
            // The initial read plus one per elapsed minute.
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
     * A failed read while already waiting keeps the waiting screen.
     *
     * Replacing it with an error would make a lost minute of connectivity look like the account had
     * been reset — the more alarming of the two readings, and the wrong one.
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
            advanceTimeBy(1.seconds)

            assertEquals(AccountGateState.Unavailable(failure), viewModel.state.value)
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

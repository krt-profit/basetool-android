/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import de.greluc.krt.profit.basetool.android.core.data.AppVersionPolicy
import de.greluc.krt.profit.basetool.android.core.data.AppVersionSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.UpdateSignal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The forced-update gate's state machine (REQ-APP-UI-004, REQ-APP-API-010).
 *
 * The clock is a [TestTimeSource] and the lifecycle is the gate's own [UpdateGateViewModel.onForeground]
 * entry, so every re-read decision is driven explicitly. Each way a wall could appear by accident is
 * pinned, and so is each way a wall that should stand could be lifted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateGateTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = TestTimeSource()
    private val signals = MutableSharedFlow<UpdateSignal>(extraBufferCapacity = SIGNAL_BUFFER)

    private companion object {
        const val THIS_BUILD = 12
        const val OLDER_FLOOR = 8
        const val NEWER_FLOOR = 20
        const val LATEST = 25
        const val RELEASES = "https://example.invalid/releases"
        const val SIGNAL_BUFFER = 8
        const val STORM_SECONDS = 120
        const val INTERVAL_SECONDS = 60
        const val READS_AFTER_RETIREMENT_AND_RESUME = 3
        val OFFLINE: ApiResult<AppVersionPolicy> = ApiResult.Failure(ApiError.Network(IOException("offline")))
    }

    /**
     * Answers whatever [answer] holds at the time of each read, and counts the reads.
     *
     * @property answer what the next read returns.
     */
    private class ScriptedSource(
        var answer: ApiResult<AppVersionPolicy>,
    ) : AppVersionSource {
        var reads = 0

        /** When set, a read waits for it before answering. */
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun versionPolicy(): ApiResult<AppVersionPolicy> {
            reads++
            hold?.await()
            return answer
        }
    }

    /**
     * Builds a successful policy answer.
     *
     * @param floor the minimum served version.
     * @return the answer.
     */
    private fun served(floor: Int): ApiResult<AppVersionPolicy> =
        ApiResult.Success(
            AppVersionPolicy(minimumVersionCode = floor, latestVersionCode = LATEST, releasesUrl = RELEASES),
        )

    /**
     * Builds a gate on [source] with the test clock and the test signal flow.
     *
     * @param source the policy source.
     * @return the gate.
     */
    private fun gate(source: AppVersionSource) =
        UpdateGateViewModel(
            source = source,
            versionCode = THIS_BUILD,
            signals = signals,
            timeSource = clock,
        )

    /**
     * Brings the app to the foreground once and lets the read finish.
     *
     * @param model the gate.
     */
    private fun TestScope.resume(model: UpdateGateViewModel) {
        model.onForeground()
        advanceUntilIdle()
    }

    /**
     * Raises one transport signal and lets the gate react.
     *
     * @param signal the signal.
     */
    private fun TestScope.raise(signal: UpdateSignal) {
        signals.tryEmit(signal)
        advanceUntilIdle()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a build above the floor runs`() =
        runTest(dispatcher) {
            val model = gate(ScriptedSource(served(OLDER_FLOOR)))

            resume(model)

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a build below the floor is walled off and told where to go`() =
        runTest(dispatcher) {
            val model = gate(ScriptedSource(served(NEWER_FLOOR)))

            resume(model)

            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `an unconfigured server locks nobody out`() =
        runTest(dispatcher) {
            val model = gate(ScriptedSource(served(0)))

            resume(model)

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a failed first read runs the app rather than walling it off`() =
        runTest(dispatcher) {
            val model = gate(ScriptedSource(OFFLINE))

            resume(model)

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a newer build being available is not the same as this one being refused`() =
        runTest(dispatcher) {
            val model = gate(ScriptedSource(served(OLDER_FLOOR)))

            resume(model)

            assertTrue(LATEST > THIS_BUILD)
            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a resume within the interval does not read again`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)

            resume(model)
            clock += (INTERVAL_SECONDS - 1).seconds
            resume(model)

            assertEquals(1, source.reads)
        }

    @Test
    fun `a resume after the interval reads again and walls off a floor raised meanwhile`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)
            resume(model)

            source.answer = served(NEWER_FLOOR)
            clock += INTERVAL_SECONDS.seconds
            resume(model)

            assertEquals(2, source.reads)
            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `a resume storm reads once per interval`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)

            repeat(STORM_SECONDS) {
                resume(model)
                clock += 1.seconds
            }

            assertEquals(STORM_SECONDS / INTERVAL_SECONDS, source.reads)
        }

    @Test
    fun `a failed re-read keeps the wall standing`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(NEWER_FLOOR))
            val model = gate(source)
            resume(model)

            source.answer = OFFLINE
            clock += INTERVAL_SECONDS.seconds
            resume(model)

            assertEquals(2, source.reads)
            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `a failed re-read keeps the app running`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)
            resume(model)

            source.answer = OFFLINE
            clock += INTERVAL_SECONDS.seconds
            resume(model)

            assertEquals(2, source.reads)
            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a lowered floor lifts a floor wall`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(NEWER_FLOOR))
            val model = gate(source)
            resume(model)

            source.answer = served(OLDER_FLOOR)
            clock += INTERVAL_SECONDS.seconds
            resume(model)

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a 404 reads the policy again once the interval has passed`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)
            resume(model)

            source.answer = served(NEWER_FLOOR)
            clock += INTERVAL_SECONDS.seconds
            raise(UpdateSignal.NOT_FOUND)

            assertEquals(2, source.reads)
            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `a burst of 404s inside the interval reads nothing more`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)
            resume(model)

            repeat(SIGNAL_BUFFER) { raise(UpdateSignal.NOT_FOUND) }

            assertEquals(1, source.reads)
            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `APP_UPDATE_REQUIRED walls off at once and forces a read inside the interval`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)
            resume(model)

            raise(UpdateSignal.UPDATE_REQUIRED)

            assertEquals(2, source.reads)
            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `APP_UPDATE_REQUIRED is not lifted by a policy that still serves this build`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR))
            val model = gate(source)
            resume(model)
            raise(UpdateSignal.UPDATE_REQUIRED)

            clock += INTERVAL_SECONDS.seconds
            resume(model)

            assertEquals(READS_AFTER_RETIREMENT_AND_RESUME, source.reads)
            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `APP_UPDATE_REQUIRED walls off even when no read can answer`() =
        runTest(dispatcher) {
            val model = gate(ScriptedSource(OFFLINE))
            advanceUntilIdle()

            raise(UpdateSignal.UPDATE_REQUIRED)

            assertEquals(UpdateGateState.Blocked(AppVersionPolicy.DEFAULT_RELEASES_URL), model.state.value)
        }

    @Test
    fun `a read in flight is not doubled`() =
        runTest(dispatcher) {
            val source = ScriptedSource(served(OLDER_FLOOR)).apply { hold = CompletableDeferred() }
            val model = gate(source)

            resume(model)
            raise(UpdateSignal.UPDATE_REQUIRED)
            source.hold?.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, source.reads)
            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }
}

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * The forced-update gate.
 *
 * Every assertion here is about the gate **failing open**. A wall is the most destructive state
 * this app has — it takes the whole tool away — so each of the three ways it could appear by
 * accident is pinned rather than reasoned about.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateGateTest {
    private val dispatcher = StandardTestDispatcher()

    private companion object {
        const val THIS_BUILD = 12
        const val OLDER_FLOOR = 8
        const val NEWER_FLOOR = 20
        const val LATEST = 25
        const val RELEASES = "https://example.invalid/releases"
    }

    /**
     * Answers one fixed policy, or one fixed failure.
     *
     * @property answer what every read returns.
     */
    private class FixedSource(
        private val answer: ApiResult<AppVersionPolicy>,
    ) : AppVersionSource {
        var reads = 0

        override suspend fun versionPolicy(): ApiResult<AppVersionPolicy> {
            reads++
            return answer
        }
    }

    /**
     * Builds a policy.
     *
     * @param floor the minimum served version.
     * @return the policy.
     */
    private fun policy(floor: Int) =
        AppVersionPolicy(
            minimumVersionCode = floor,
            latestVersionCode = LATEST,
            releasesUrl = RELEASES,
        )

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
            val model =
                UpdateGateViewModel(FixedSource(ApiResult.Success(policy(OLDER_FLOOR))), THIS_BUILD)

            model.start()
            advanceUntilIdle()

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a build below the floor is walled off and told where to go`() =
        runTest(dispatcher) {
            val model =
                UpdateGateViewModel(FixedSource(ApiResult.Success(policy(NEWER_FLOOR))), THIS_BUILD)

            model.start()
            advanceUntilIdle()

            assertEquals(UpdateGateState.Blocked(RELEASES), model.state.value)
        }

    @Test
    fun `an unconfigured server locks nobody out`() =
        runTest(dispatcher) {
            // Zero means no floor, and it is what a server nobody has configured answers. Any other
            // reading of it would refuse every installed build the first time this code shipped.
            val model = UpdateGateViewModel(FixedSource(ApiResult.Success(policy(0))), THIS_BUILD)

            model.start()
            advanceUntilIdle()

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `a failed read runs the app rather than walling it off`() =
        runTest(dispatcher) {
            // A member on a train must not lose the whole tool because one request timed out.
            // There is no screen for "we could not check whether you may run", and inventing one
            // would be the same wall with a different sentence.
            val model =
                UpdateGateViewModel(
                    FixedSource(ApiResult.Failure(ApiError.Network(IOException("offline")))),
                    THIS_BUILD,
                )

            model.start()
            advanceUntilIdle()

            assertEquals(UpdateGateState.Allowed, model.state.value)
        }

    @Test
    fun `the policy is read once, not on a loop`() =
        runTest(dispatcher) {
            val source = FixedSource(ApiResult.Success(policy(OLDER_FLOOR)))
            val model = UpdateGateViewModel(source, THIS_BUILD)

            model.start()
            model.start()
            advanceUntilIdle()

            // A wall that appears mid-session, over work in progress, is worse than one that waits
            // for the next start — and the floor does not move often enough to justify polling.
            assertEquals(1, source.reads)
        }

    @Test
    fun `a newer build being available is not the same as this one being refused`() =
        runTest(dispatcher) {
            // floor 8, latest 25, this build 12: an update exists and this build is still served.
            // Collapsing the two numbers would make every release a forced one.
            val model =
                UpdateGateViewModel(FixedSource(ApiResult.Success(policy(OLDER_FLOOR))), THIS_BUILD)

            model.start()
            advanceUntilIdle()

            assertTrue(policy(OLDER_FLOOR).latestVersionCode > THIS_BUILD)
            assertEquals(UpdateGateState.Allowed, model.state.value)
        }
}

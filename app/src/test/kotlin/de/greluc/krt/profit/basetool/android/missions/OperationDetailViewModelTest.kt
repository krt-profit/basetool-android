/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.OperationDetail
import de.greluc.krt.profit.basetool.android.core.data.OperationOverview
import de.greluc.krt.profit.basetool.android.core.data.OperationPage
import de.greluc.krt.profit.basetool.android.core.data.OperationPayout
import de.greluc.krt.profit.basetool.android.core.data.OperationPayouts
import de.greluc.krt.profit.basetool.android.core.data.OperationQuery
import de.greluc.krt.profit.basetool.android.core.data.OperationRollup
import de.greluc.krt.profit.basetool.android.core.data.OperationSource
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The Operation detail's rules.
 *
 * The one that matters: "Dein Anteil" is found by the **backend user id**, never by name. The
 * server sends `displayName` when a member set one and `username` otherwise, so a name match would
 * quietly point a member at somebody else's money — or at nothing — depending on whether they had
 * personalised their profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers with whatever is queued and counts the calls.
     *
     * @property answers responses for [overview], the last one repeating once exhausted.
     */
    private class RecordingSource(
        private val answers: MutableList<ApiResult<OperationOverview>> = mutableListOf(),
    ) : OperationSource {
        var overviewCalls = 0

        fun queue(answer: ApiResult<OperationOverview>) = answers.add(answer)

        override suspend fun search(
            query: OperationQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<OperationPage> = error("the detail never searches")

        override suspend fun overview(id: String): ApiResult<OperationOverview> {
            overviewCalls++
            return if (answers.size > 1) answers.removeAt(0) else answers.first()
        }
    }

    /**
     * Answers the identity read once.
     *
     * @property answer what to return.
     */
    private class FixedIdentity(
        private val answer: ApiResult<String>,
    ) : IdentitySource {
        var calls = 0

        override suspend fun myUserId(): ApiResult<String> {
            calls++
            return answer
        }
    }

    private fun payout(
        id: String,
        name: String,
        donating: Boolean = false,
        paid: Boolean = false,
    ) = OperationPayout(
        participantId = id,
        participantName = name,
        donating = donating,
        share = "4150.0000",
        donated = if (donating) "4150.0000" else null,
        payout = if (donating) "0.0000" else "4129.2500",
        paidOut = paid,
    )

    private fun overview(vararg payouts: OperationPayout) =
        OperationOverview(
            detail =
                OperationDetail(
                    id = "o1",
                    name = "Operation Rotschild",
                    status = OperationStatus.ACTIVE,
                    rawStatus = "ACTIVE",
                    description = null,
                    payoutPreliminary = false,
                ),
            rollup = OperationRollup(total = "74700.0000", truncated = false, missions = emptyList()),
            payouts = OperationPayouts(totalDonations = "0.0000", rows = payouts.toList()),
        )

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = RecordingSource()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(identity: IdentitySource) = OperationDetailViewModel(source, identity, "o1")

    @Test
    fun `the Operation loads`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(overview()))
            val model = viewModel(FixedIdentity(ApiResult.Success("u1")))

            model.load()
            advanceUntilIdle()

            assertEquals(OperationDetailPhase.Ready, model.state.value.phase)
            assertEquals("Operation Rotschild", model.state.value.overview?.detail?.name)
        }

    @Test
    fun `the caller's row is found by id, not by name`() =
        runTest(dispatcher) {
            // Two members whose display names are identical -- entirely possible, since the name is
            // free text -- and only the id tells them apart.
            source.queue(ApiResult.Success(overview(payout("u1", "Rhea"), payout("u2", "Rhea"))))
            val model = viewModel(FixedIdentity(ApiResult.Success("u2")))

            model.load()
            advanceUntilIdle()

            assertEquals("u2", model.state.value.myPayout?.participantId)
        }

    @Test
    fun `a caller who did not take part has no row`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(overview(payout("u1", "Rhea"))))
            val model = viewModel(FixedIdentity(ApiResult.Success("u9")))

            model.load()
            advanceUntilIdle()

            assertNull(model.state.value.myPayout)
            assertEquals("u9", model.state.value.myUserId)
        }

    @Test
    fun `a failed identity read costs one line, not the screen`() =
        runTest(dispatcher) {
            // The screen's subject is the Operation. Turning a failed nicety into a failed screen
            // would hide content that loaded perfectly well.
            source.queue(ApiResult.Success(overview(payout("u1", "Rhea"))))
            val model = viewModel(FixedIdentity(ApiResult.Failure(ApiError.Network(IOException("offline")))))

            model.load()
            advanceUntilIdle()

            assertEquals(OperationDetailPhase.Ready, model.state.value.phase)
            assertNull(model.state.value.myUserId)
            assertNull(model.state.value.myPayout)
        }

    @Test
    fun `a refused Operation is reported with its cause, so the screen can word it`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel(FixedIdentity(ApiResult.Success("u1")))

            model.load()
            advanceUntilIdle()

            val phase = model.state.value.phase
            assertTrue(phase is OperationDetailPhase.Failed)
            assertTrue((phase as OperationDetailPhase.Failed).error is ApiError.Forbidden)
        }

    @Test
    fun `a refresh keeps the Operation on screen while it runs`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(overview(payout("u1", "Rhea"))))
            source.queue(ApiResult.Success(overview(payout("u1", "Rhea", paid = true))))
            val model = viewModel(FixedIdentity(ApiResult.Success("u1")))
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            assertEquals(OperationDetailPhase.Ready, model.state.value.phase)
            assertEquals(false, model.state.value.myPayout?.paidOut)

            advanceUntilIdle()
            assertEquals(true, model.state.value.myPayout?.paidOut)
        }

    @Test
    fun `a refresh does not re-read an identity it already has`() =
        runTest(dispatcher) {
            // It cannot change without a new session, and the repository caches it anyway; asking
            // again would be a round trip for a value already known.
            source.queue(ApiResult.Success(overview()))
            val identity = FixedIdentity(ApiResult.Success("u1"))
            val model = viewModel(identity)
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            advanceUntilIdle()

            assertEquals(1, identity.calls)
        }

    @Test
    fun `a refresh retries an identity that is still missing`() =
        runTest(dispatcher) {
            // The member already made the gesture; spending it on the one thing still absent is
            // better than making them find another way to ask.
            source.queue(ApiResult.Success(overview()))
            val identity = FixedIdentity(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            val model = viewModel(identity)
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            advanceUntilIdle()

            assertEquals(2, identity.calls)
        }
}

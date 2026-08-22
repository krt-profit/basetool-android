/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionPage
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
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
 * The detail screen's own rules, and the one that matters most: the Einsatz and its money load on
 * **separate timelines**.
 *
 * A member can be allowed to see an Einsatz and still be refused its books
 * (`isMemberOrAbove` + `canSeeMission` guard the Finanzen endpoints alone). Folding the two reads
 * together would either hide the Einsatz behind a permission it does not need, or claim the money
 * loaded when it did not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers with whatever is queued and counts what was asked for.
     *
     * @property detailAnswers responses for [detail], the last one repeating once exhausted.
     * @property financeAnswers responses for [finances], likewise.
     */
    private class RecordingSource(
        private val detailAnswers: MutableList<ApiResult<MissionDetail>> = mutableListOf(),
        private val financeAnswers: MutableList<ApiResult<MissionFinances>> = mutableListOf(),
    ) : MissionSource {
        var detailCalls = 0
        var financeCalls = 0

        fun queueDetail(answer: ApiResult<MissionDetail>) = detailAnswers.add(answer)

        fun queueFinances(answer: ApiResult<MissionFinances>) = financeAnswers.add(answer)

        override suspend fun search(
            query: MissionQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<MissionPage> = error("the detail screen never searches")

        override suspend fun detail(id: String): ApiResult<MissionDetail> {
            detailCalls++
            return if (detailAnswers.size > 1) detailAnswers.removeAt(0) else detailAnswers.first()
        }

        override suspend fun finances(missionId: String): ApiResult<MissionFinances> {
            financeCalls++
            return if (financeAnswers.size > 1) financeAnswers.removeAt(0) else financeAnswers.first()
        }
    }

    private fun detail(name: String = "Vertikaler Abbau") =
        MissionDetail(
            id = "m1",
            name = name,
            description = null,
            status = MissionStatus.PLANNED,
            rawStatus = "PLANNED",
            meetingTime = null,
            plannedStartTime = null,
            actualStartTime = null,
            plannedEndTime = null,
            isInternal = false,
            meetingPoint = null,
            operationName = null,
            orgUnitName = null,
            orgUnitShorthand = null,
            partyLeadName = null,
            registeredParticipants = 0,
            checkedInParticipants = 0,
            participants = emptyList(),
            units = emptyList(),
            steps = emptyList(),
            objectives = emptyList(),
            frequencies = emptyList(),
        )

    private fun finances() =
        MissionFinances(
            total = "74700",
            incomeSum = "86400",
            incomeCount = 3,
            expenseSum = "11700",
            expenseCount = 2,
            entries = emptyList(),
            totalEntries = 0,
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

    private fun viewModel() = MissionDetailViewModel(source, "m1")

    @Test
    fun `the Einsatz loads and the Uebersicht tab is the one showing`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()

            model.load()
            advanceUntilIdle()

            assertEquals(MissionDetailPhase.Ready, model.state.value.phase)
            assertEquals("Vertikaler Abbau", model.state.value.detail?.name)
            assertEquals(MissionTab.OVERVIEW, model.state.value.tab)
        }

    @Test
    fun `the money is not fetched until its tab is opened`() =
        runTest(dispatcher) {
            // Six tabs come from one response. The seventh is two more calls most members never
            // look at, and one a member without the permission cannot make succeed at all.
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()

            model.load()
            advanceUntilIdle()

            assertEquals(0, source.financeCalls)
            assertEquals(MissionFinancesPhase.Idle, model.state.value.finances)
        }

    @Test
    fun `opening the Finanzen tab fetches it once, and only once`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            model.onTabSelected(MissionTab.PARTICIPANTS)
            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()

            assertEquals("switching back must not re-fetch", 1, source.financeCalls)
            assertTrue(model.state.value.finances is MissionFinancesPhase.Ready)
        }

    @Test
    fun `a refused Finanzen tab leaves the Einsatz intact`() =
        runTest(dispatcher) {
            // The ordinary case for a member who may see the Einsatz but not its books. Turning
            // that into a failed screen would hide an Einsatz behind a permission it does not need.
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()

            assertEquals(MissionDetailPhase.Ready, model.state.value.phase)
            assertTrue(model.state.value.finances is MissionFinancesPhase.Failed)
        }

    @Test
    fun `the Finanzen tab can be retried without reloading the Einsatz around it`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            val detailCallsBefore = source.detailCalls

            model.onRetryFinances()
            advanceUntilIdle()

            assertTrue(model.state.value.finances is MissionFinancesPhase.Ready)
            assertEquals("the Einsatz was not re-read", detailCallsBefore, source.detailCalls)
        }

    @Test
    fun `a refused Einsatz is reported with its cause, so the screen can word it`() =
        runTest(dispatcher) {
            // What an outsider gets for an internal or terminal Einsatz. Distinguishable from an
            // outage, which is why the error is carried rather than flattened to a boolean.
            source.queueDetail(ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel()

            model.load()
            advanceUntilIdle()

            val phase = model.state.value.phase
            assertTrue(phase is MissionDetailPhase.Failed)
            assertTrue((phase as MissionDetailPhase.Failed).error is ApiError.Forbidden)
        }

    @Test
    fun `a refresh keeps the Einsatz on screen while it runs`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail("Alt")))
            source.queueDetail(ApiResult.Success(detail("Neu")))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            assertEquals(MissionDetailPhase.Ready, model.state.value.phase)
            assertEquals("Alt", model.state.value.detail?.name)

            advanceUntilIdle()
            assertEquals("Neu", model.state.value.detail?.name)
        }

    @Test
    fun `a refresh re-reads the money only when its tab was already opened`() =
        runTest(dispatcher) {
            // Refreshing must not silently acquire a permission-dependent read the member never
            // asked for -- nor skip one they are looking at.
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            advanceUntilIdle()
            assertEquals("never opened, so never fetched", 0, source.financeCalls)

            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            model.onRefresh()
            advanceUntilIdle()
            assertEquals("opened, so refreshed with the rest", 2, source.financeCalls)
        }
}

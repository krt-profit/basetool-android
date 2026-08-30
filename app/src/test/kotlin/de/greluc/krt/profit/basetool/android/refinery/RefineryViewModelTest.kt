/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncEvent
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrder
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDeleteSource
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderPage
import de.greluc.krt.profit.basetool.android.core.data.RefineryPhase
import de.greluc.krt.profit.basetool.android.core.data.RefineryServerStatus
import de.greluc.krt.profit.basetool.android.core.data.RefinerySource
import de.greluc.krt.profit.basetool.android.core.data.RefineryYield
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/**
 * The Raffinerie list and detail rules.
 *
 * Two of them cannot be checked anywhere else. The list's „In Arbeit"/„Abholbereit" split is a
 * device-side reading of one server answer, so only a test that moves the clock can tell it works;
 * and a booking has to announce three rooms, because it changes the order, the queue and the Lager
 * it just wrote entries into.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefineryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    // Against a server that stays busy the retry ladder never stops, by design: the member is
    // looking at the screen and chapter 14 keeps telling them how long. That makes it a coroutine
    // the test has to end itself -- `runTest` drains the scheduler at teardown, so a live ladder
    // hangs the run instead of failing it. Every test below that starts one cancels the scope.

    private companion object {
        val BEFORE: OffsetDateTime = OffsetDateTime.parse("2026-08-16T23:00:00Z")
        val AFTER: OffsetDateTime = OffsetDateTime.parse("2026-08-17T12:00:00Z")

        /** The ladder's first rung, plus a moment, in the units the virtual clock takes. */
        const val FIRST_STEP_MS = 3_100L

        /** The ladder's first rung, in seconds. */
        const val FIRST_RUNG = 3

        /** Its second. */
        const val SECOND_RUNG = 6

        /**
         * Builds an order.
         *
         * @param id its id.
         * @param status the server's status.
         * @param endsAt when the run ends.
         * @param materialId the yield's material, or `null` for an unbookable good.
         * @return the order.
         */
        fun order(
            id: String,
            status: RefineryServerStatus = RefineryServerStatus.IN_PROGRESS,
            endsAt: String? = "2026-08-17T03:41:00Z",
            materialId: String? = "m1",
        ): RefineryOrder =
            RefineryOrder(
                id = id,
                locationId = "loc1",
                locationName = "ARC-L1",
                methodName = "Dinyx",
                startedAt = "2026-08-16T22:41:00Z",
                endsAt = endsAt,
                status = status,
                yields =
                    listOf(
                        RefineryYield(
                            materialId = materialId,
                            materialName = "Quantainium",
                            amount = 622.0,
                            unitIsPiece = false,
                            quality = 3,
                        ),
                    ),
                oreSales = "96900",
                profit = "84200",
                version = 2,
            )
    }

    /**
     * Answers the reads and records what was asked and written.
     *
     * @property orders what every list read returns.
     * @property detail what every detail read returns.
     */
    private class RecordingSource(
        private val orders: List<RefineryOrder> = emptyList(),
        private val detail: RefineryOrder? = null,
        private val listFailure: ApiError? = null,
        private val storeFailure: ApiError? = null,
    ) : RefinerySource {
        val requestedStatuses = mutableListOf<Set<RefineryServerStatus>>()
        val stored = mutableListOf<String>()

        override suspend fun myOrders(
            statuses: Set<RefineryServerStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<RefineryOrderPage> {
            requestedStatuses += statuses
            listFailure?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(
                RefineryOrderPage(orders = orders, page = page, totalPages = 1, totalElements = orders.size.toLong()),
            )
        }

        override suspend fun detail(id: String): ApiResult<RefineryOrder> =
            detail?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.NotFound())

        override suspend fun store(order: RefineryOrder): ApiResult<Unit> {
            storeFailure?.let { return ApiResult.Failure(it) }
            stored += order.id
            return ApiResult.Success(Unit)
        }
    }

    /**
     * Records what a screen announced.
     *
     * Local to this class rather than shared: the wiring test's copy also drives the receive
     * direction, and a double that does both would hide which half a failure came from.
     */
    private class RecordingLiveSync : LiveSyncSource {
        val announced = mutableListOf<Pair<String, Set<String>>>()

        override fun observe(topics: Set<LiveSyncTopic>): Flow<LiveSyncEvent> = emptyFlow()

        override suspend fun publish(
            topic: LiveSyncTopic,
            sections: Set<String>,
        ): ApiResult<Unit> {
            announced += topic.toString() to sections
            return ApiResult.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Records the deletions.
     *
     * @property answer what the call returns.
     */
    private class RecordingDelete(
        private val answer: ApiResult<Unit> = ApiResult.Success(Unit),
    ) : RefineryOrderDeleteSource {
        val deleted = mutableListOf<String>()

        override suspend fun deleteOrder(orderId: String): ApiResult<Unit> {
            deleted.add(orderId)
            return answer
        }
    }

    @Test
    fun `the two live filters ask the server for the same pair`() =
        runTest(dispatcher) {
            val source = RecordingSource(orders = listOf(order("r1")))
            val model = RefineryViewModel(source, clock = emptyFlow())

            model.loadOnce()
            advanceUntilIdle()
            model.onFilterChanged(RefineryFilter.RUNNING)
            advanceUntilIdle()
            model.onFilterChanged(RefineryFilter.READY)
            advanceUntilIdle()

            // The server has no "ready" status, so asking for one of the pair would drop half the
            // rows either filter is made of.
            val expected = setOf(RefineryServerStatus.OPEN, RefineryServerStatus.IN_PROGRESS)
            assertEquals(expected, source.requestedStatuses[1])
            assertEquals(expected, source.requestedStatuses[2])
        }

    @Test
    fun `the split follows the clock`() =
        runTest(dispatcher) {
            val source = RecordingSource(orders = listOf(order("r1")))
            val model = RefineryViewModel(source, clock = emptyFlow())
            model.loadOnce()
            advanceUntilIdle()

            model.onFilterChanged(RefineryFilter.RUNNING)
            advanceUntilIdle()
            // Same row, same server answer; only the clock differs. Before the end it is running.
            assertEquals(1, model.state.value.copy(now = BEFORE).orders.size)
            // After it, the RUNNING filter must no longer show it — which is the whole point of
            // recomputing the phase rather than freezing it at mapping time.
            assertTrue(model.state.value.copy(now = AFTER).orders.isEmpty())
        }

    @Test
    fun `the stored filter asks only for completed orders`() =
        runTest(dispatcher) {
            val source = RecordingSource(orders = emptyList())
            val model = RefineryViewModel(source, clock = emptyFlow())
            model.loadOnce()
            advanceUntilIdle()

            model.onFilterChanged(RefineryFilter.STORED)
            advanceUntilIdle()

            assertEquals(setOf(RefineryServerStatus.COMPLETED), source.requestedStatuses.last())
        }

    @Test
    fun `a busy server starts the chapter-14 countdown`() =
        runTest(dispatcher) {
            val source = RecordingSource(listFailure = ApiError.ServiceUnavailable())
            val model = RefineryViewModel(source, clock = emptyFlow())

            model.loadOnce()
            // runCurrent, NOT advanceUntilIdle: against a server that stays busy the ladder is
            // deliberately endless, so advancing the virtual clock until idle never returns and
            // the test hangs instead of failing. Measured — it is why this rule had no test
            // before. Running the tasks already queued is enough: the first countdown value is
            // published before the first delay.
            runCurrent()

            // The countdown, not the empty state: 503 is the one class of failure where asking
            // again is meaningful, and chapter 14 wants the member told how long.
            assertTrue(model.state.value.phase is RefineryPhaseState.Failed)
            assertEquals(FIRST_RUNG, model.state.value.retryIn)
            model.viewModelScope.cancel()
        }

    @Test
    fun `the ladder climbs and a manual retry resets it`() =
        runTest(dispatcher) {
            val source = RecordingSource(listFailure = ApiError.ServiceUnavailable())
            val model = RefineryViewModel(source, clock = emptyFlow())
            model.loadOnce()
            runCurrent()

            // Let the first wait elapse; the automatic retry fails again and the ladder steps up.
            advanceTimeBy(FIRST_STEP_MS)
            runCurrent()
            assertEquals(SECOND_RUNG, model.state.value.retryIn)

            model.onRetry()
            runCurrent()

            // Back to the bottom. A member pressing the button is new information, and inheriting
            // a longer wait from an attempt they did not make would punish them for waiting.
            assertEquals(FIRST_RUNG, model.state.value.retryIn)
            model.viewModelScope.cancel()
        }

    @Test
    fun `a refusal gets no countdown`() =
        runTest(dispatcher) {
            val source = RecordingSource(listFailure = ApiError.Forbidden())
            val model = RefineryViewModel(source, clock = emptyFlow())

            model.loadOnce()
            advanceUntilIdle()

            // A 403 answers the same in three seconds. A countdown in front of it promises the
            // member something that will not happen — and, unlike the 503 above, nothing is
            // scheduled, so advancing until idle is safe here.
            assertNull(model.state.value.retryIn)
        }

    @Test
    fun `a booking announces the order, the queue and the Lager`() =
        runTest(dispatcher) {
            val source = RecordingSource(detail = order("r1"))
            val liveSync = RecordingLiveSync()
            val model = RefineryDetailViewModel(source, null, "r1", liveSync, clock = emptyFlow())
            advanceUntilIdle()

            model.onStoreRequested()
            model.onStoreConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("r1"), source.stored)
            // Three rooms. Announcing only the order would leave every open Lager — browser tab or
            // phone — showing a stock figure the booking has already made wrong.
            assertEquals(
                listOf("refinery-order:r1", "refinery", "inventory"),
                liveSync.announced.map { it.first },
            )
        }

    @Test
    fun `an unfinished run offers no booking`() =
        runTest(dispatcher) {
            val source = RecordingSource(detail = order("r1"))
            val model = RefineryDetailViewModel(source, null, "r1", clock = emptyFlow())
            advanceUntilIdle()

            // Booking a run that has not ended books a yield that does not exist yet, and the
            // server would happily mark the order stored.
            assertFalse(model.state.value.copy(now = BEFORE).storable)
            assertTrue(model.state.value.copy(now = AFTER).storable)
            assertEquals(
                RefineryPhase.RUNNING,
                model.state.value.order?.phaseAt(BEFORE),
            )
        }

    @Test
    fun `deleting the run reports it once and only for a run that may go`() =
        runTest(dispatcher) {
            val source = RecordingSource(detail = order("r1"))
            val deletes = RecordingDelete()
            val model =
                RefineryDetailViewModel(
                    source,
                    null,
                    "r1",
                    writes = RefineryDetailWrites(delete = deletes),
                    clock = emptyFlow(),
                )
            advanceUntilIdle()

            model.onDeleteRequested()
            assertTrue(model.state.value.confirmingDelete)
            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("r1"), deletes.deleted)
            assertTrue(model.state.value.deleted)
            assertFalse(model.state.value.confirmingDelete)
        }

    @Test
    fun `a failed booking reports and leaves the action available`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(detail = order("r1"), storeFailure = ApiError.OptimisticLock())
            val model = RefineryDetailViewModel(source, null, "r1", clock = emptyFlow())
            advanceUntilIdle()

            model.onStoreRequested()
            model.onStoreConfirmed()
            advanceUntilIdle()

            assertTrue(model.state.value.error is ApiError.OptimisticLock)
            assertFalse(model.state.value.stored)
        }
}

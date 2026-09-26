/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
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
 * Tests the Raffinerie list and detail: the device-side „In Arbeit"/„Abholbereit" split as the clock moves, and a
 * booking that announces changes to the order, the queue and the Lager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefineryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

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
                ownerId = "u1",
                ownerName = "Rhea",
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
                RefineryOrderPage(rows = orders, page = page, totalPages = 1, totalElements = orders.size.toLong()),
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
            assertEquals(1, model.state.value.copy(now = BEFORE).orders.size)
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
            runCurrent()

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

            advanceTimeBy(FIRST_STEP_MS)
            runCurrent()
            assertEquals(SECOND_RUNG, model.state.value.retryIn)

            model.onRetry()
            runCurrent()

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

            assertFalse(model.state.value.copy(now = BEFORE).storable)
            assertTrue(model.state.value.copy(now = AFTER).storable)
            assertEquals(
                RefineryPhase.RUNNING,
                model.state.value.order?.phaseAt(BEFORE),
            )
        }

    /**
     * „Aktiv" is the screen's default, and it is a compound rather than a server status.
     *
     * Stored runs are finished and flood the list as the months pass, so the screen opens on the
     * ones there is something to do about. The web has defaulted to the same pair all along.
     */
    @Test
    fun `the default filter is Aktiv and it is running plus ready`() {
        assertEquals(RefineryFilter.ACTIVE, RefineryListState().filter)
        assertEquals(RefineryFilter.ACTIVE, RefineryFilter.entries.first())

        val running = order("r1", status = RefineryServerStatus.IN_PROGRESS)
        val stored = order("r2", status = RefineryServerStatus.COMPLETED)
        val state =
            RefineryListState(
                filter = RefineryFilter.ACTIVE,
                loaded = listOf(running, stored),
                now = OffsetDateTime.parse("2026-08-17T01:00:00Z"),
            )

        assertEquals(listOf("r1"), state.orders.map { it.id })
        assertEquals(
            listOf("r1", "r2"),
            state.copy(filter = RefineryFilter.ALL).orders.map { it.id },
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
                    seams = RefineryDetailSeams(delete = deletes, identity = FixedIdentity("u1")),
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

/**
 * An identity that is simply known, for the cases whose subject is not the identity read.
 *
 * @property id the caller's backend user id.
 */
private class FixedIdentity(
    private val id: String,
) : IdentitySource {
    override suspend fun myUserId(): ApiResult<String> = ApiResult.Success(id)

    override suspend fun me(): ApiResult<Identity> = ApiResult.Success(Identity(id, false))

    override fun forget() = Unit
}

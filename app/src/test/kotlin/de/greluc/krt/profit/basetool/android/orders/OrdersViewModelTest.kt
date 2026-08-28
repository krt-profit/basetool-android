/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderPage
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
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
 * The queue's rules.
 *
 * The one that is easy to get wrong: the open/closed state of a row's material list belongs to the
 * screen's state, not to the row. A `LazyColumn` disposes what leaves the viewport, and a member who
 * opened three rows would find them shut on the way back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrdersViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers the queue and records the filters it was asked for.
     *
     * @property answers responses, the last repeating.
     */
    private class RecordingSource(
        private val answers: MutableList<ApiResult<JobOrderPage>> = mutableListOf(),
    ) : JobOrderSource {
        val filters = mutableListOf<Set<JobOrderStatus>>()
        val pages = mutableListOf<Int>()

        override suspend fun queue(
            statuses: Set<JobOrderStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<JobOrderPage> {
            filters.add(statuses)
            pages.add(page)
            return if (answers.size > 1) answers.removeAt(0) else answers.first()
        }

        /** The queue's age thresholds; the defaults, since no test tunes them. */
        override suspend fun ageThresholds(): JobOrderAgeThresholds = JobOrderAgeThresholds()

        override suspend fun detail(id: String): ApiResult<JobOrder> = error("the queue never opens one")

        override suspend fun setAssigned(
            id: String,
            userId: String,
            assigned: Boolean,
        ): ApiResult<JobOrder> = error("the queue writes nothing")

        override suspend fun setAssigneeNote(
            id: String,
            userId: String,
            note: String?,
            version: Long?,
        ): ApiResult<JobOrder> = error("the queue writes nothing")

        override suspend fun setStatus(
            id: String,
            status: JobOrderStatus,
            version: Long?,
        ): ApiResult<JobOrder> = error("the queue writes nothing")

        override suspend fun setPriority(
            id: String,
            priority: Int,
        ): ApiResult<JobOrder> = error("the queue writes nothing")
    }

    private fun order(id: String) =
        JobOrder(
            id = id,
            displayId = "1042",
            status = JobOrderStatus.OPEN,
            rawStatus = "OPEN",
            priority = 1,
            type = "MATERIAL",
            requestingOrgUnit = null,
            responsibleOrgUnit = null,
            comment = null,
            materials = emptyList(),
            handovers = emptyList(),
            assignees = emptyList(),
            createdAt = null,
            version = 1L,
            redacted = false,
        )

    private fun page(
        vararg rows: JobOrder,
        page: Int = 0,
        totalPages: Int = 1,
    ) = JobOrderPage(rows.toList(), page = page, totalPages = totalPages, totalElements = rows.size.toLong())

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = RecordingSource(mutableListOf(ApiResult.Success(page(order("o1")))))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the queue loads once`() =
        runTest(dispatcher) {
            val model = OrdersViewModel(source)

            model.loadOnce()
            advanceUntilIdle()
            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, source.pages.size)
            assertEquals(OrdersPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `a status chip narrows on the server`() =
        runTest(dispatcher) {
            // Filtering a page the server already truncated would make the stated count wrong.
            val model = OrdersViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onStatusesChanged(setOf(JobOrderStatus.OPEN))
            advanceUntilIdle()

            assertEquals(setOf(JobOrderStatus.OPEN), source.filters.last())
            assertEquals(0, source.pages.last())
        }

    @Test
    fun `selecting the same filter again does not re-read`() =
        runTest(dispatcher) {
            val model = OrdersViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.onStatusesChanged(setOf(JobOrderStatus.OPEN))
            advanceUntilIdle()
            val before = source.pages.size

            model.onStatusesChanged(setOf(JobOrderStatus.OPEN))
            advanceUntilIdle()

            assertEquals(before, source.pages.size)
        }

    @Test
    fun `the expanded set lives in the state, so a scroll cannot close a row`() =
        runTest(dispatcher) {
            val model = OrdersViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggleMaterials("o1")
            assertTrue("o1" in model.state.value.expanded)

            model.onToggleMaterials("o1")
            assertTrue("o1" !in model.state.value.expanded)
        }

    @Test
    fun `the next page is appended`() =
        runTest(dispatcher) {
            val paged =
                RecordingSource(
                    mutableListOf(
                        ApiResult.Success(page(order("o1"), totalPages = TWO_PAGES)),
                        ApiResult.Success(page(order("o2"), page = 1, totalPages = TWO_PAGES)),
                    ),
                )
            val model = OrdersViewModel(paged)
            model.loadOnce()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("o1", "o2"), model.state.value.orders.map { it.id })
        }

    @Test
    fun `a failed queue is a failure, not an empty list`() =
        runTest(dispatcher) {
            val failing =
                RecordingSource(mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))))
            val model = OrdersViewModel(failing)

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is OrdersPhase.Failed)
        }

    private companion object {
        /** A two-page result. */
        const val TWO_PAGES = 2
    }
}

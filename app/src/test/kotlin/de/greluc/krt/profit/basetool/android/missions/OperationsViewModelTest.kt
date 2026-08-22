/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.Operation
import de.greluc.krt.profit.basetool.android.core.data.OperationOverview
import de.greluc.krt.profit.basetool.android.core.data.OperationPage
import de.greluc.krt.profit.basetool.android.core.data.OperationQuery
import de.greluc.krt.profit.basetool.android.core.data.OperationSource
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * The Operationen list's rules.
 *
 * The two that carry a scar: the list must not load until the segment is actually opened, and the
 * search field must hold what was typed rather than what was last sent — the second is the defect
 * that shipped on the Einsatz list and was only found on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers with whatever is queued and records what was asked.
     *
     * @property answers responses, the last one repeating once exhausted.
     */
    private class RecordingSource(
        private val answers: MutableList<ApiResult<OperationPage>> = mutableListOf(),
    ) : OperationSource {
        val queries = mutableListOf<OperationQuery>()
        val pages = mutableListOf<Int>()

        fun queue(answer: ApiResult<OperationPage>) = answers.add(answer)

        override suspend fun search(
            query: OperationQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<OperationPage> {
            queries.add(query)
            pages.add(page)
            return if (answers.size > 1) answers.removeAt(0) else answers.first()
        }

        override suspend fun overview(id: String): ApiResult<OperationOverview> =
            error("the list never opens an overview")
    }

    private fun operation(
        id: String,
        name: String = "Operation Rotschild",
        status: OperationStatus = OperationStatus.ACTIVE,
    ) = Operation(id = id, name = name, status = status, rawStatus = status.name, description = null)

    private fun page(
        vararg operations: Operation,
        page: Int = 0,
        totalPages: Int = 1,
        total: Long = operations.size.toLong(),
    ) = OperationPage(operations = operations.toList(), page = page, totalPages = totalPages, totalElements = total)

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

    private fun viewModel() = OperationsViewModel(source)

    @Test
    fun `nothing is read until the segment is opened`() =
        runTest(dispatcher) {
            // The list lives behind a segment. A member who never taps "Operationen" must not pay
            // a request for it on every app start.
            source.queue(ApiResult.Success(page(operation("o1"))))
            viewModel()

            advanceUntilIdle()

            assertEquals(0, source.pages.size)
        }

    @Test
    fun `opening the segment twice reads once`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(page(operation("o1"))))
            val model = viewModel()

            model.loadOnce()
            advanceUntilIdle()
            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, source.pages.size)
            assertEquals(OperationsPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `the search field holds what was typed, before the debounce elapses`() =
        runTest(dispatcher) {
            // The field is a controlled component. Binding it to the debounced term feeds the old
            // value back and every character vanishes as it is typed -- measured on a device on
            // the Einsatz list, and the reason this assertion exists here from the start.
            source.queue(ApiResult.Success(page(operation("o1"))))
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()

            model.onSearchChanged("Rot")

            assertEquals("Rot", model.state.value.searchText)
            assertEquals("", model.state.value.query.text)
        }

    @Test
    fun `typing is one request, not one per keystroke`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(page(operation("o1"))))
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val before = source.pages.size

            model.onSearchChanged("R")
            model.onSearchChanged("Ro")
            model.onSearchChanged("Rot")
            advanceTimeBy(DEBOUNCE_SETTLE_MS)
            advanceUntilIdle()

            assertEquals(1, source.pages.size - before)
            assertEquals("Rot", source.queries.last().text)
        }

    @Test
    fun `a status chip narrows immediately`() =
        runTest(dispatcher) {
            // A tapped chip is one deliberate act and should feel instant; only typing is debounced.
            source.queue(ApiResult.Success(page(operation("o1"))))
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()

            model.onStatusesChanged(setOf(OperationStatus.COMPLETED))
            advanceUntilIdle()

            assertEquals(setOf(OperationStatus.COMPLETED), source.queries.last().statuses)
        }

    @Test
    fun `the next page is appended, not replaced`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(page(operation("o1"), totalPages = TWO_PAGES, total = TWO_ROWS)))
            source.queue(
                ApiResult.Success(
                    page(operation("o2"), page = 1, totalPages = TWO_PAGES, total = TWO_ROWS),
                ),
            )
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("o1", "o2"), model.state.value.operations.map { it.id })
            assertEquals(listOf(0, 1), source.pages)
        }

    @Test
    fun `a failed next page leaves the rows alone`() =
        runTest(dispatcher) {
            source.queue(ApiResult.Success(page(operation("o1"), totalPages = TWO_PAGES, total = TWO_ROWS)))
            source.queue(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("o1"), model.state.value.operations.map { it.id })
            assertEquals(OperationsPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `a failed first page is reported as a failure and not as an empty list`() =
        runTest(dispatcher) {
            // "Nothing is planned" and "the app could not ask" are different facts, and the second
            // must never be shown as the first.
            source.queue(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            val model = viewModel()

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is OperationsPhase.Failed)
        }

    @Test
    fun `reset clears the field as well as the filter`() =
        runTest(dispatcher) {
            // Clearing only the query would leave the old term visible and restore it on the next
            // keystroke, from a value the member can no longer see.
            source.queue(ApiResult.Success(page(operation("o1"))))
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            model.onSearchChanged("Rot")
            advanceTimeBy(DEBOUNCE_SETTLE_MS)
            advanceUntilIdle()

            model.onResetFilters()
            advanceUntilIdle()

            assertEquals("", model.state.value.searchText)
            assertEquals(OperationQuery.NONE, source.queries.last())
        }

    private companion object {
        /** Comfortably past the 300 ms debounce. */
        const val DEBOUNCE_SETTLE_MS = 400L

        /** A two-page result. */
        const val TWO_PAGES = 2

        /** Its total. */
        const val TWO_ROWS = 2L
    }
}

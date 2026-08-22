/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryPage
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
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
 * The Lager tree's rules.
 *
 * The one worth the most: a group's stacks are fetched when it is opened, never before. The tree's
 * first level is one request, and pre-fetching every group's holdings would pull the whole warehouse
 * to draw a dozen headings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers both levels and counts the reads.
     *
     * @property groupAnswers responses for [groups], the last repeating.
     * @property stackAnswers responses for [stacks], likewise.
     */
    private class RecordingSource(
        private val groupAnswers: MutableList<ApiResult<InventoryPage>> = mutableListOf(),
        private val stackAnswers: MutableList<ApiResult<List<InventoryStack>>> = mutableListOf(),
    ) : InventorySource {
        val stackRequests = mutableListOf<String>()
        var groupCalls = 0

        override suspend fun groups(
            page: Int,
            pageSize: Int,
        ): ApiResult<InventoryPage> {
            groupCalls++
            return if (groupAnswers.size > 1) groupAnswers.removeAt(0) else groupAnswers.first()
        }

        override suspend fun stacks(materialId: String): ApiResult<List<InventoryStack>> {
            stackRequests.add(materialId)
            return if (stackAnswers.size > 1) stackAnswers.removeAt(0) else stackAnswers.first()
        }
    }

    private fun group(
        id: String?,
        amount: String? = "10",
    ) = InventoryGroup(
        materialId = id,
        name = "Quantainium",
        unit = "SCU",
        amount = amount,
        quality = "880",
        maxQuality = "940",
    )

    private fun stack() =
        InventoryStack(
            holder = "Rhea",
            location = "ARC-L1",
            personal = false,
            amount = "10",
            quality = "880",
            entryCount = 1,
        )

    private fun page(vararg rows: InventoryGroup) =
        InventoryPage(rows.toList(), page = 0, totalPages = 1, totalElements = rows.size.toLong())

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source =
            RecordingSource(
                mutableListOf(ApiResult.Success(page(group("m1")))),
                mutableListOf(ApiResult.Success(listOf(stack()))),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the tree loads its first level only`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, source.groupCalls)
            assertTrue("no group may be fetched before it is opened", source.stackRequests.isEmpty())
        }

    @Test
    fun `opening a group fetches exactly that group`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggleGroup("m1")
            advanceUntilIdle()

            assertEquals(listOf("m1"), source.stackRequests)
            assertTrue(model.state.value.opened["m1"] is StackPhase.Ready)
        }

    @Test
    fun `closing and re-opening does not fetch twice`() =
        runTest(dispatcher) {
            // The Lager changes slowly enough that a member re-opening a group within one visit
            // expects what they just saw; pull-to-refresh is how they ask for more.
            val model = InventoryViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.onToggleGroup("m1")
            advanceUntilIdle()

            model.onToggleGroup("m1")
            model.onToggleGroup("m1")
            advanceUntilIdle()

            assertEquals(listOf("m1", "m1"), source.stackRequests)
        }

    @Test
    fun `a group closed while its read is in flight does not spring open`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggleGroup("m1")
            model.onToggleGroup("m1")
            advanceUntilIdle()

            assertTrue("m1" !in model.state.value.opened)
        }

    @Test
    fun `a failed group stays open and says so`() =
        runTest(dispatcher) {
            // Closing it would look like the tap did not register, and the member would try again.
            val failing =
                RecordingSource(
                    mutableListOf(ApiResult.Success(page(group("m1")))),
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))),
                )
            val model = InventoryViewModel(failing)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggleGroup("m1")
            advanceUntilIdle()

            assertEquals(StackPhase.Failed, model.state.value.opened["m1"])
        }

    @Test
    fun `a refresh drops what was loaded, because the holdings may have moved`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.onToggleGroup("m1")
            advanceUntilIdle()

            model.onRefresh()
            advanceUntilIdle()

            assertTrue(model.state.value.opened.isEmpty())
        }

    @Test
    fun `the stock filter hides rows from the page, and says nothing about the rest`() =
        runTest(dispatcher) {
            // The endpoint has no such parameter. What makes the chip honest is that the count
            // below the list keeps stating the server's total.
            val mixed =
                RecordingSource(
                    mutableListOf(ApiResult.Success(page(group("m1"), group("m2", amount = "0")))),
                    mutableListOf(ApiResult.Success(emptyList())),
                )
            val model = InventoryViewModel(mixed)
            model.loadOnce()
            advanceUntilIdle()

            model.onWithStockOnlyChanged(true)

            assertEquals(1, model.state.value.visibleGroups.size)
            assertEquals(TWO, model.state.value.total)
        }

    @Test
    fun `a failed tree is a failure, not an empty Lager`() =
        runTest(dispatcher) {
            val failing =
                RecordingSource(
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))),
                    mutableListOf(ApiResult.Success(emptyList())),
                )
            val model = InventoryViewModel(failing)

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is InventoryPhase.Failed)
        }

    private companion object {
        /** Two groups on the page. */
        const val TWO = 2L
    }
}

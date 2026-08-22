/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import de.greluc.krt.profit.basetool.android.core.data.HangarSource
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipPage
import de.greluc.krt.profit.basetool.android.core.data.ShipTypePage
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
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
 * The Hangar's two halves.
 *
 * The rule with teeth: the halves keep separate rows and separate failures. Sharing them would let
 * a switch show the other half's content for a frame, and a failure on one present itself as a
 * failure of the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HangarViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers both halves and records what was asked.
     *
     * @property shipAnswers responses for [myShips], the last repeating.
     * @property typeAnswers responses for [orgOverview], likewise.
     */
    private class RecordingSource(
        private val shipAnswers: MutableList<ApiResult<ShipPage>> = mutableListOf(),
        private val typeAnswers: MutableList<ApiResult<ShipTypePage>> = mutableListOf(),
    ) : HangarSource {
        val shipSearches = mutableListOf<String>()
        val typeSearches = mutableListOf<String>()
        val shipPages = mutableListOf<Int>()

        override suspend fun myShips(
            search: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<ShipPage> {
            shipSearches.add(search)
            shipPages.add(page)
            return if (shipAnswers.size > 1) shipAnswers.removeAt(0) else shipAnswers.first()
        }

        override suspend fun orgOverview(
            search: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<ShipTypePage> {
            typeSearches.add(search)
            return if (typeAnswers.size > 1) typeAnswers.removeAt(0) else typeAnswers.first()
        }
    }

    private fun ship(id: String) =
        Ship(
            id = id,
            name = "Meridian",
            typeName = "Carrack",
            manufacturerName = "Anvil Aerospace",
            insurance = "LTI",
            locationName = "ARC-L1",
            fitted = true,
        )

    private fun shipPage(
        vararg rows: Ship,
        page: Int = 0,
        totalPages: Int = 1,
    ) = ShipPage(rows.toList(), page = page, totalPages = totalPages, totalElements = rows.size.toLong())

    private fun typePage(vararg rows: ShipTypeSummary) =
        ShipTypePage(rows.toList(), page = 0, totalPages = 1, totalElements = rows.size.toLong())

    private fun summary(name: String) =
        ShipTypeSummary(typeName = name, manufacturerName = null, count = 3, fittedCount = 2)

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source =
            RecordingSource(
                mutableListOf(ApiResult.Success(shipPage(ship("s1")))),
                mutableListOf(ApiResult.Success(typePage(summary("Carrack")))),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HangarViewModel(source)

    @Test
    fun `the screen opens on the member's own ships`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(HangarSegment.MINE, model.state.value.segment)
            assertEquals(1, model.state.value.ships.size)
            assertEquals(0, source.typeSearches.size)
        }

    @Test
    fun `switching to the org half reads it from page zero`() =
        runTest(dispatcher) {
            // Keeping whatever was last loaded would show a member an aggregate from ten minutes
            // ago under a header that says it is current.
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()

            model.onSegmentSelected(HangarSegment.ORG)
            advanceUntilIdle()

            assertEquals(HangarSegment.ORG, model.state.value.segment)
            assertEquals(1, model.state.value.types.size)
            assertEquals(HangarPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `tapping the half already showing does nothing`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val before = source.shipPages.size

            model.onSegmentSelected(HangarSegment.MINE)
            advanceUntilIdle()

            assertEquals(before, source.shipPages.size)
        }

    @Test
    fun `the two halves keep separate rows`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            model.onSegmentSelected(HangarSegment.ORG)
            advanceUntilIdle()

            // The ships are still there, untouched, and the aggregate did not overwrite them.
            assertEquals(1, model.state.value.ships.size)
            assertEquals(1, model.state.value.types.size)
        }

    @Test
    fun `the filter is typed immediately and sent once`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val before = source.shipSearches.size

            model.onSearchChanged("Car")
            assertEquals("Car", model.state.value.searchText)

            advanceTimeBy(DEBOUNCE_SETTLE_MS)
            advanceUntilIdle()

            assertEquals(1, source.shipSearches.size - before)
            assertEquals("Car", source.shipSearches.last())
        }

    @Test
    fun `the filter applies to whichever half is showing`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            model.onSegmentSelected(HangarSegment.ORG)
            advanceUntilIdle()
            val shipsBefore = source.shipSearches.size

            model.onSearchChanged("Car")
            advanceTimeBy(DEBOUNCE_SETTLE_MS)
            advanceUntilIdle()

            assertEquals("Car", source.typeSearches.last())
            assertEquals("the other half must not be re-read", shipsBefore, source.shipSearches.size)
        }

    @Test
    fun `a failure is reported, never shown as an empty hangar`() =
        runTest(dispatcher) {
            val failing =
                RecordingSource(
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("offline")))),
                    mutableListOf(ApiResult.Success(typePage())),
                )
            val model = HangarViewModel(failing)

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is HangarPhase.Failed)
        }

    @Test
    fun `the next page is appended`() =
        runTest(dispatcher) {
            val paged =
                RecordingSource(
                    mutableListOf(
                        ApiResult.Success(shipPage(ship("s1"), totalPages = TWO_PAGES)),
                        ApiResult.Success(shipPage(ship("s2"), page = 1, totalPages = TWO_PAGES)),
                    ),
                    mutableListOf(ApiResult.Success(typePage())),
                )
            val model = HangarViewModel(paged)
            model.loadOnce()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("s1", "s2"), model.state.value.ships.map { it.id })
        }

    private companion object {
        /** Comfortably past the 300 ms debounce. */
        const val DEBOUNCE_SETTLE_MS = 400L

        /** A two-page result. */
        const val TWO_PAGES = 2
    }
}

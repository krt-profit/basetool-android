/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import de.greluc.krt.profit.basetool.android.core.data.MaterialMarketSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialMatrixCell
import de.greluc.krt.profit.basetool.android.core.data.MaterialMatrixPage
import de.greluc.krt.profit.basetool.android.core.data.ProfitRow
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The two surfaces behind „Handel"'s overflow: the price matrix and the profit calculation.
 *
 * The properties worth a class: the matrix is **drawn as it arrives** rather than behind a
 * full-screen spinner, its filters can never offer a narrowing that yields nothing, and the profit
 * screen picks no ship on the member's behalf when the one it wanted is absent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialMarketViewModelTest {
    private companion object {
        /** The dearest terminal for Quantainium in the fixture. */
        val ARC_SELL: BigDecimal = BigDecimal("88.10")

        /** And the second one. */
        val LORVILLE_SELL: BigDecimal = BigDecimal("84.60")

        /** How many cells the fixture's matrix holds across its two pages. */
        const val MATRIX_CELLS = 3L

        /** The C2's hold. */
        const val C2_SCU = 696

        /** The Hull C's. */
        const val HULL_C_SCU = 4608

        /** The status of a server that broke. */
        const val SERVER_ERROR = 500
    }

    private val dispatcher = StandardTestDispatcher()

    private var pages: List<List<MaterialMatrixCell>> =
        listOf(
            listOf(
                cell("q", "Quantainium", "t1", "ARC-L1", "Stanton", sell = ARC_SELL),
                cell("q", "Quantainium", "t2", "Lorville", "Stanton", sell = LORVILLE_SELL),
            ),
            listOf(cell("b", "Bexalit", "t3", "Ruin Station", "Pyro", sell = BigDecimal("40.50"))),
        )
    private var matrixFailsAt: Int? = null
    private var ships: ApiResult<List<ShipTypeOption>> =
        ApiResult.Success(
            listOf(
                ShipTypeOption("s1", "Caterpillar", "Drake", C2_SCU),
                ShipTypeOption("s2", "C2 Hercules Starlifter", "Crusader", C2_SCU),
                ShipTypeOption("s3", "Hull C", "MISC", HULL_C_SCU),
            ),
        )
    private var profit: ApiResult<List<ProfitRow>> =
        ApiResult.Success(
            listOf(
                ProfitRow(
                    materialName = "Quantainium",
                    minBuy = BigDecimal("79.40"),
                    maxSell = ARC_SELL,
                    profitPerScu = BigDecimal("8.70"),
                    fullLoadCost = BigDecimal("55262.40"),
                    maxProfitFullLoad = BigDecimal("6055.20"),
                    marginPercent = BigDecimal("10.9"),
                ),
            ),
        )
    private val profitCalls = mutableListOf<Pair<String, List<String>>>()

    private fun cell(
        materialId: String,
        materialName: String,
        terminalId: String,
        terminalName: String,
        system: String?,
        sell: BigDecimal,
    ) = MaterialMatrixCell(
        materialId = materialId,
        materialName = materialName,
        terminalId = terminalId,
        terminalName = terminalName,
        starSystem = system,
        priceBuy = null,
        priceSell = sell,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The first page is on screen before the second arrives.
     *
     * Chapter 16 rules out a full-screen spinner by name; the loading line sits under whatever has
     * already been read, and that is only true if the state carries rows while `loading` is set.
     */
    @Test
    fun `the matrix is drawn while it is still arriving`() =
        runTest(dispatcher) {
            val vm = MaterialMatrixViewModel(FakeMarket())

            advanceUntilIdle()

            assertFalse(vm.state.value.loading)
            assertEquals(listOf("Bexalit", "Quantainium"), vm.state.value.rows.map { it.name })
            assertEquals(MATRIX_CELLS, vm.state.value.total)
        }

    /** The best price of a row is tinted, and „best" flips with the side being shown. */
    @Test
    fun `the best price of a row follows the mode`() =
        runTest(dispatcher) {
            val vm = MaterialMatrixViewModel(FakeMarket())
            advanceUntilIdle()

            val row = vm.state.value.rows.first { it.name == "Quantainium" }

            assertEquals(ARC_SELL, row.best(MatrixMode.SELL))
            // Nothing in the fixture has a buy price, so the buy side has no best at all rather
            // than falling back to the sell figure.
            assertNull(vm.state.value.copy(mode = MatrixMode.BUY).rows.first().best(MatrixMode.BUY))
        }

    /**
     * A system filter drops the columns it emptied.
     *
     * Leaving a hundred all-dash columns standing to prove the terminals exist would make the table
     * unreadable to make a point nobody asked for.
     */
    @Test
    fun `filtering by system drops the columns it emptied`() =
        runTest(dispatcher) {
            val vm = MaterialMatrixViewModel(FakeMarket())
            advanceUntilIdle()

            vm.onSystem("Pyro")

            assertEquals(listOf("Ruin Station"), vm.state.value.columns.map { it.name })
            assertEquals(listOf("Bexalit"), vm.state.value.rows.map { it.name })
        }

    /** A failure part-way through keeps what arrived and says the read stopped. */
    @Test
    fun `a refused page keeps the rows already read`() =
        runTest(dispatcher) {
            matrixFailsAt = 1
            val vm = MaterialMatrixViewModel(FakeMarket())

            advanceUntilIdle()

            assertEquals(listOf("Quantainium"), vm.state.value.rows.map { it.name })
            assertTrue(vm.state.value.error is ApiError.Server)
            assertFalse(vm.state.value.loading)
        }

    /** The web preselects the C2, and so does this. */
    @Test
    fun `the default ship is chosen and calculated straight away`() =
        runTest(dispatcher) {
            val vm = ProfitViewModel(FakeMarket())

            advanceUntilIdle()

            assertEquals("s2", vm.state.value.shipId)
            assertEquals(listOf("s2" to emptyList<String>()), profitCalls)
            assertFalse("the C2 is not a Hull C", vm.state.value.hullCRule)
        }

    /** The Hull-C note appears only for the hull whose arithmetic the rule changes. */
    @Test
    fun `the hull-c note is tied to the hull`() =
        runTest(dispatcher) {
            val vm = ProfitViewModel(FakeMarket())
            advanceUntilIdle()

            vm.onShip("s3")
            advanceUntilIdle()

            assertTrue(vm.state.value.hullCRule)
        }

    /**
     * Switching a system off restricts the calculation to the rest.
     *
     * An empty list means „every system" on the wire, so the restriction only travels once
     * something is actually excluded.
     */
    @Test
    fun `excluding a system sends the remaining ones`() =
        runTest(dispatcher) {
            val vm = ProfitViewModel(FakeMarket())
            advanceUntilIdle()
            profitCalls.clear()

            vm.onToggleSystem("Pyro")
            advanceUntilIdle()

            assertEquals(listOf("s2" to listOf("Stanton")), profitCalls)
        }

    /** With no C2 in the catalogue nothing is chosen, and the screen asks rather than guessing. */
    @Test
    fun `an absent default leaves the ship unpicked`() =
        runTest(dispatcher) {
            ships = ApiResult.Success(listOf(ShipTypeOption("s1", "Caterpillar", "Drake", C2_SCU)))
            val vm = ProfitViewModel(FakeMarket())

            advanceUntilIdle()

            assertNull(vm.state.value.shipId)
            assertTrue(profitCalls.isEmpty())
        }

    /** A refused calculation drops the previous answer — it was about a different ship. */
    @Test
    fun `a refused calculation does not leave the old rows under the new ship`() =
        runTest(dispatcher) {
            val vm = ProfitViewModel(FakeMarket())
            advanceUntilIdle()
            profit = ApiResult.Failure(ApiError.Server(SERVER_ERROR, null))

            vm.onShip("s3")
            advanceUntilIdle()

            assertTrue(vm.state.value.rows.isEmpty())
            assertTrue(vm.state.value.error is ApiError.Server)
        }

    /** The market, as the test set it. */
    private inner class FakeMarket : MaterialMarketSource {
        override suspend fun matrixPage(page: Int): ApiResult<MaterialMatrixPage> {
            if (page == matrixFailsAt) {
                return ApiResult.Failure(ApiError.Server(SERVER_ERROR, null))
            }
            val cells = pages.getOrElse(page) { emptyList() }
            return ApiResult.Success(
                MaterialMatrixPage(
                    cells = cells,
                    page = page,
                    totalPages = pages.size,
                    totalElements = pages.sumOf { it.size }.toLong(),
                ),
            )
        }

        override suspend fun shipTypes(): ApiResult<List<ShipTypeOption>> = ships

        override suspend fun starSystems(): ApiResult<List<String>> =
            ApiResult.Success(listOf("Pyro", "Stanton"))

        override suspend fun profit(
            shipId: String,
            starSystemNames: List<String>,
        ): ApiResult<List<ProfitRow>> {
            profitCalls.add(shipId to starSystemNames)
            return profit
        }
    }
}

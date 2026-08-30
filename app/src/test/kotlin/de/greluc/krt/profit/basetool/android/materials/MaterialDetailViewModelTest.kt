/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import de.greluc.krt.profit.basetool.android.core.data.MaterialCatalogSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialPriceRow
import de.greluc.krt.profit.basetool.android.core.data.MaterialSummary
import de.greluc.krt.profit.basetool.android.core.data.MaterialTerminalPrice
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import java.math.BigDecimal

/**
 * „Preise und Terminals" — one material's market.
 *
 * The two figures at the head are a **selection**, not a computation: the dearest buyer and the
 * cheapest seller are rows the server sent, shown with their own terminal names. A number without
 * the place it applies at answers half the question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialDetailViewModelTest {
    private companion object {
        /** The dearest buyer in the fixture. */
        val BEST_SELL: BigDecimal = BigDecimal("88.10")

        /** And the cheapest seller. */
        val BEST_BUY: BigDecimal = BigDecimal("79.40")
    }

    private val dispatcher = StandardTestDispatcher()

    private var summary: ApiResult<MaterialSummary> =
        ApiResult.Success(
            MaterialSummary(
                id = "q",
                name = "Quantainium",
                type = "REFINED",
                unit = "SCU",
                category = "Veredelt",
                illegal = false,
            ),
        )
    private var prices: ApiResult<List<MaterialTerminalPrice>> =
        ApiResult.Success(
            listOf(
                MaterialTerminalPrice("1", "ARC-L1 · Refinery", BigDecimal("81.00"), BEST_SELL),
                MaterialTerminalPrice("2", "Lorville · TDD", BEST_BUY, BigDecimal("84.60")),
                // Buys but does not sell — a row that must not become the "cheapest seller".
                MaterialTerminalPrice("3", "HUR-L2 · Refinery", null, BigDecimal("80.20")),
            ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() = MaterialDetailViewModel(FakeCatalog(), "q", AlwaysOnline)

    /** The head answers „where do I sell it" and „where do I buy it", with the place. */
    @Test
    fun `the two best prices name their terminals`() =
        runTest(dispatcher) {
            val vm = model()

            advanceUntilIdle()

            assertEquals(BEST_SELL, vm.state.value.bestSell?.price)
            assertEquals("ARC-L1 · Refinery", vm.state.value.bestSell?.terminal)
            assertEquals(BEST_BUY, vm.state.value.bestBuy?.price)
            assertEquals("Lorville · TDD", vm.state.value.bestBuy?.terminal)
        }

    /** A terminal that does not sell cannot be the cheapest seller, however low its other side is. */
    @Test
    fun `a row without a buy price never becomes the cheapest seller`() =
        runTest(dispatcher) {
            prices =
                ApiResult.Success(
                    listOf(MaterialTerminalPrice("3", "HUR-L2 · Refinery", null, BigDecimal("80.20"))),
                )
            val vm = model()

            advanceUntilIdle()

            assertNull(vm.state.value.bestBuy)
            assertEquals(BigDecimal("80.20"), vm.state.value.bestSell?.price)
        }

    /** The terminal search narrows the table and nothing else. */
    @Test
    fun `the terminal filter narrows the rows but not the head`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onFilter("lorville")

            assertEquals(listOf("Lorville · TDD"), vm.state.value.visible.map { it.terminal })
            assertEquals(BEST_SELL, vm.state.value.bestSell?.price)
        }

    /**
     * A material that does not exist is a failure, never an empty price table.
     *
     * The empty table says „keine Preisdaten verfügbar", which is a statement about a material that
     * does exist — a different and untrue thing to tell somebody who followed a dead link.
     */
    @Test
    fun `a missing material fails rather than showing an empty table`() =
        runTest(dispatcher) {
            summary = ApiResult.Failure(ApiError.NotFound(null))
            val vm = model()

            advanceUntilIdle()

            assertTrue(vm.state.value.phase is MaterialsPhase.Failed)
            assertTrue(vm.state.value.prices.isEmpty())
        }

    /** A material with no prices at all is a result, and the page reaches Ready to say so. */
    @Test
    fun `a material nobody trades is a result, not a failure`() =
        runTest(dispatcher) {
            prices = ApiResult.Success(emptyList())
            val vm = model()

            advanceUntilIdle()

            assertEquals(MaterialsPhase.Ready, vm.state.value.phase)
            assertNull(vm.state.value.bestSell)
        }

    /** The material and its prices, as the test set them. */
    private inner class FakeCatalog : MaterialCatalogSource {
        override suspend fun priceOverview(): ApiResult<List<MaterialPriceRow>> =
            error("the detail does not read the catalogue")

        override suspend fun material(materialId: String): ApiResult<MaterialSummary> = summary

        override suspend fun prices(materialId: String): ApiResult<List<MaterialTerminalPrice>> = prices
    }

    /** A device that always has a network. */
    private object AlwaysOnline : Connectivity {
        override val online: Flow<Boolean> = flowOf(true)
    }
}

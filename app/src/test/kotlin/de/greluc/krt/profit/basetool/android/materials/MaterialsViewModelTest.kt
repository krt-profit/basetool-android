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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * „Handel" — the material catalogue and the four ways it narrows.
 *
 * The assertion that carries the most: a material **nobody sells** is dropped by an active
 * „Min. Einkaufspreis" rather than kept. Keeping it would answer „at least 30 aBUEC" with rows that
 * have no price at all, which reads as a match and is not one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialsViewModelTest {
    private companion object {
        /** Laranite's buy price in the fixture. */
        val LARANITE_BUY: BigDecimal = BigDecimal("28.10")

        /** And what it sells for. */
        val LARANITE_SELL: BigDecimal = BigDecimal("31.20")

        /** A bound above Laranite's buy price and below Quantainium's. */
        const val BETWEEN = "50"

        /** How many materials the fixture's catalogue holds. */
        const val CATALOGUE_SIZE = 3

        /** The status of a server that broke. */
        const val SERVER_ERROR = 500
    }

    private val dispatcher = StandardTestDispatcher()

    private var catalogue: ApiResult<List<MaterialPriceRow>> =
        ApiResult.Success(
            listOf(
                row("q", "Quantainium", "Veredelt", BigDecimal("79.40"), BigDecimal("88.10")),
                row("l", "Laranite", "Veredelt", LARANITE_BUY, LARANITE_SELL),
                // Nobody sells it, so it has no answer to „mindestens 50".
                row("m", "Medpen (Hemozal)", null, null, null),
            ),
        )

    private fun row(
        id: String,
        name: String,
        category: String?,
        buy: BigDecimal?,
        sell: BigDecimal?,
    ) = MaterialPriceRow(
        id = id,
        name = name,
        category = category,
        minPriceBuy = buy,
        maxPriceSell = sell,
        illegal = false,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() = MaterialsViewModel(FakeCatalog(), AlwaysOnline)

    /** The catalogue arrives whole, and the chips describe what actually came. */
    @Test
    fun `the whole catalogue is read and the chips are its own categories`() =
        runTest(dispatcher) {
            val vm = model()

            advanceUntilIdle()

            assertEquals(MaterialsPhase.Ready, vm.state.value.phase)
            assertEquals(CATALOGUE_SIZE, vm.state.value.visible.size)
            // „Unsortiert" is the web's own fallback, so a material without a category still gets a
            // chip rather than falling out of every one of them.
            assertEquals(listOf("Unsortiert", "Veredelt"), vm.state.value.categories)
        }

    /** The search is a local narrowing, because the whole list is already here. */
    @Test
    fun `the search narrows by name, case-insensitively`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onQuery("laran")

            assertEquals(listOf("Laranite"), vm.state.value.visible.map { it.name })
        }

    /**
     * A bound is a question about a price, and a material without one cannot answer it.
     *
     * Keeping the price-less row would make „mindestens 50" list a material nobody trades.
     */
    @Test
    fun `a price bound drops the rows that have no such price`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onMinBuy(BETWEEN)

            assertEquals(listOf("Quantainium"), vm.state.value.visible.map { it.name })
        }

    /** The other bound cuts from the other side. */
    @Test
    fun `the sell bound keeps only what stays under it`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onMaxSell(BETWEEN)

            assertEquals(listOf("Laranite"), vm.state.value.visible.map { it.name })
        }

    /**
     * A half-typed bound is a moment in typing, not an instruction to empty the list.
     *
     * A separator typed before its first digit is the case that actually reaches here — „3," parses
     * as three and is a real bound, but a lone „," is not a number and must narrow nothing.
     */
    @Test
    fun `an unparseable bound filters nothing`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onMinBuy(",")

            assertEquals(CATALOGUE_SIZE, vm.state.value.visible.size)
        }

    /** The category chip and the search compose rather than replacing each other. */
    @Test
    fun `the category chip narrows alongside the search`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onCategory("Unsortiert")

            assertEquals(listOf("Medpen (Hemozal)"), vm.state.value.visible.map { it.name })
            assertTrue(vm.state.value.filtered)

            vm.onResetFilters()

            assertEquals(CATALOGUE_SIZE, vm.state.value.visible.size)
            assertFalse(vm.state.value.filtered)
        }

    /** A failure is a failure, not an empty catalogue — the two say opposite things. */
    @Test
    fun `a refused read reports failure rather than an empty list`() =
        runTest(dispatcher) {
            catalogue = ApiResult.Failure(ApiError.Server(SERVER_ERROR, null))
            val vm = model()

            advanceUntilIdle()

            assertTrue(vm.state.value.phase is MaterialsPhase.Failed)
            assertTrue(vm.state.value.rows.isEmpty())
        }

    /** The catalogue, as the test set it. */
    private inner class FakeCatalog : MaterialCatalogSource {
        override suspend fun priceOverview(): ApiResult<List<MaterialPriceRow>> = catalogue

        override suspend fun material(materialId: String): ApiResult<MaterialSummary> =
            error("the list does not read one material")

        override suspend fun prices(materialId: String): ApiResult<List<MaterialTerminalPrice>> =
            error("the list does not read prices")
    }

    /** A device that always has a network. */
    private object AlwaysOnline : Connectivity {
        override val online: Flow<Boolean> = flowOf(true)
    }
}

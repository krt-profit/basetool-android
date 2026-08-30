/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.JobOrderPage
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.data.MaterialCollectionRow
import de.greluc.krt.profit.basetool.android.core.data.MaterialCollectionSource
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * „Materialsammelübersicht" — the stock rows linked to one Auftrag.
 *
 * The property that carries the class: **only a link with an amount behind it asks first.** The
 * unlink removes the earmark and leaves the stock alone, so a row that promised nothing has nothing
 * to warn about, and asking anyway would teach members to click through confirmations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderCollectionTest {
    private companion object {
        /** The row's own optimistic lock. */
        const val ROW_VERSION = 4L

        /** How much of the row is earmarked here. */
        val ALLOCATED: BigDecimal = BigDecimal("150")
    }

    private val dispatcher = StandardTestDispatcher()

    private val deliveredWrites = mutableListOf<Triple<String, Boolean, Long>>()
    private val unlinkedEntries = mutableListOf<String>()
    private val unlinkedMaterials = mutableListOf<String>()

    private var rows: List<MaterialCollectionRow> = listOf(row())

    private fun row(allocated: BigDecimal? = ALLOCATED) =
        MaterialCollectionRow(
            entryId = "e1",
            version = ROW_VERSION,
            owner = "Rhea",
            ownerId = "u1",
            location = "ARC-L1",
            locationId = "l1",
            materialName = "Laranite",
            quality = BigDecimal("874"),
            quantity = BigDecimal("442"),
            allocated = allocated,
            delivered = false,
        )

    private fun order() =
        JobOrder(
            id = "o1",
            displayId = "1042",
            status = JobOrderStatus.IN_PROGRESS,
            rawStatus = "IN_PROGRESS",
            priority = 1,
            type = "MATERIAL",
            requestingOrgUnit = "Staffel 1",
            requestingOrgUnitId = "s1",
            responsibleOrgUnit = "SK Vanguard",
            responsibleOrgUnitId = "sk1",
            handle = "Rhea",
            comment = null,
            materials =
                listOf(
                    JobOrderMaterial("m1", "Laranite", "400", "120", 1, "220"),
                    // Required, and no linked row covers it — the design's second section.
                    JobOrderMaterial("m2", "Bexalit", "50", null, 0, "50"),
                ),
            items = emptyList(),
            handovers = emptyList(),
            assignees = emptyList(),
            createdAt = null,
            version = 1L,
            redacted = false,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() = OrderCollectionViewModel(FakeCollection(), FakeOrders(), "o1")

    /**
     * The second section is derived, because the server has no endpoint for it.
     *
     * A material the order requires that no linked row covers is exactly the difference between two
     * answers.
     */
    @Test
    fun `a required material with no row lands in the unbacked section`() =
        runTest(dispatcher) {
            val vm = model()

            advanceUntilIdle()

            assertEquals(listOf("Laranite"), vm.state.value.rows.map { it.materialName })
            assertEquals(listOf("Bexalit"), vm.state.value.unbacked.map { it.name })
            assertEquals("1042", vm.state.value.displayId)
        }

    /** A link with an amount behind it asks first, and names what goes and what stays. */
    @Test
    fun `unlinking a row with an earmark asks first`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onUnlink(vm.state.value.rows.single())

            assertNotNull(vm.state.value.confirming)
            assertEquals(ALLOCATED, vm.state.value.confirming?.amount)
            assertTrue("nothing is written until it is answered", unlinkedEntries.isEmpty())

            vm.onConfirmUnlink()
            advanceUntilIdle()

            assertEquals(listOf("e1"), unlinkedEntries)
            assertNull(vm.state.value.confirming)
        }

    /** A link with nothing behind it does not ask: there is no amount to lose. */
    @Test
    fun `unlinking a row without an earmark asks nothing`() =
        runTest(dispatcher) {
            rows = listOf(row(allocated = BigDecimal.ZERO))
            val vm = model()
            advanceUntilIdle()

            vm.onUnlink(vm.state.value.rows.single())
            advanceUntilIdle()

            assertNull(vm.state.value.confirming)
            assertEquals(listOf("e1"), unlinkedEntries)
        }

    /** The delivered flag echoes the row's own version, so a concurrent change is a 409. */
    @Test
    fun `the delivered flag is flipped with the row's version`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onDelivered(vm.state.value.rows.single())
            advanceUntilIdle()

            assertEquals(listOf(Triple("e1", true, ROW_VERSION)), deliveredWrites)
        }

    /** A material with no stock behind it is unlinked without a question. */
    @Test
    fun `an unbacked material is unlinked directly`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onUnlinkMaterial(vm.state.value.unbacked.single())
            advanceUntilIdle()

            assertEquals(listOf("m2"), unlinkedMaterials)
            assertNull(vm.state.value.confirming)
        }

    /** Records the three writes. */
    private inner class FakeCollection : MaterialCollectionSource {
        override suspend fun rows(orderId: String): ApiResult<List<MaterialCollectionRow>> =
            ApiResult.Success(rows)

        override suspend fun setDelivered(
            entryId: String,
            orderId: String,
            delivered: Boolean,
            version: Long,
        ): ApiResult<Unit> {
            deliveredWrites.add(Triple(entryId, delivered, version))
            return ApiResult.Success(Unit)
        }

        override suspend fun unlinkEntry(
            orderId: String,
            entryId: String,
        ): ApiResult<Unit> {
            unlinkedEntries.add(entryId)
            return ApiResult.Success(Unit)
        }

        override suspend fun unlinkMaterial(
            orderId: String,
            materialId: String,
        ): ApiResult<Unit> {
            unlinkedMaterials.add(materialId)
            return ApiResult.Success(Unit)
        }
    }

    /** The order, for its number and its required materials. */
    private inner class FakeOrders : JobOrderSource {
        override suspend fun queue(
            statuses: Set<JobOrderStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<JobOrderPage> = error("the collection reads one order")

        override suspend fun detail(id: String): ApiResult<JobOrder> = ApiResult.Success(order())

        override suspend fun ageThresholds(): JobOrderAgeThresholds = error("not this screen")

        override suspend fun setAssigned(
            id: String,
            userId: String,
            assigned: Boolean,
        ): ApiResult<JobOrder> = error("not this screen")

        override suspend fun setAssigneeNote(
            id: String,
            userId: String,
            note: String?,
            version: Long?,
        ): ApiResult<JobOrder> = error("not this screen")

        override suspend fun setPriority(
            id: String,
            priority: Int,
        ): ApiResult<JobOrder> = error("not this screen")

        override suspend fun setStatus(
            id: String,
            status: JobOrderStatus,
            version: Long?,
        ): ApiResult<JobOrder> = error("not this screen")
    }
}

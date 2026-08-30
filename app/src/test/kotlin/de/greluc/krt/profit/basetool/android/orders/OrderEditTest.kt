/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderCreateSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderDraft
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandover
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandoverLine
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemDraft
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.JobOrderPage
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.data.MaterialMatches
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Auftrag bearbeiten" — the create form, pre-filled, writing one of two endpoints.
 *
 * The property that carries the class: **a line may not fall below what has already been handed
 * over.** The server refuses it, and the figure it compares against is the sum of the handover
 * lines — never `amount − openAmount`, which counts claims.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderEditTest {
    private companion object {
        /** The order's optimistic lock, which the save has to echo. */
        const val VERSION = 7L

        /** What the Laranite line asks for. */
        const val NEEDED = "400"

        /** And how much of it has already changed hands. */
        const val DELIVERED = 180.0
    }

    private val dispatcher = StandardTestDispatcher()
    private val writes = mutableListOf<Pair<String, JobOrderDraft>>()

    private var order: JobOrder = order()

    private fun order(handedOver: Double = DELIVERED) =
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
            comment = "Abgabe an ARC-L1.",
            materials =
                listOf(
                    JobOrderMaterial(
                        materialId = "m1",
                        name = "Laranite",
                        needed = NEEDED,
                        inStock = "120",
                        claimCount = 1,
                        open = "220",
                    ),
                ),
            items = emptyList(),
            handovers =
                listOf(
                    JobOrderHandover(
                        id = "h1",
                        recipient = "Vex",
                        executor = "Rhea",
                        at = null,
                        lines = listOf(JobOrderHandoverLine(materialId = "m1", amount = handedOver)),
                    ),
                ).filter { handedOver > 0.0 },
            assignees = emptyList(),
            createdAt = null,
            version = VERSION,
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

    private fun model(mode: OrderFormMode = OrderFormMode.EDIT) =
        OrderCreateViewModel(
            source = RecordingSource(),
            orgUnits = FakeUnits(),
            orders = RecordingSource(),
            orderId = "o1",
            mode = mode,
        )

    /** The form arrives as the order, not as an empty sheet. */
    @Test
    fun `the form is filled from the order it edits`() =
        runTest(dispatcher) {
            val vm = model()

            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("sk1", state.responsibleId)
            assertEquals("s1", state.requestingId)
            assertEquals("Abgabe an ARC-L1.", state.comment)
            assertEquals(listOf("Laranite"), state.lines.map { it.materialName })
            assertEquals(NEEDED, state.lines.single().amount)
            assertEquals(VERSION, state.version)
        }

    /**
     * A line may not fall below what has already changed hands.
     *
     * The floor is the sum of the handover **lines**. Using the server's open remainder would count
     * claims instead and put the floor in the wrong place.
     */
    @Test
    fun `a line below what was delivered blocks the save`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onAmount(0, "100")

            assertEquals(DELIVERED, vm.state.value.deliveredOf("m1"), 0.0)
            assertFalse(vm.state.value.submittable)
            assertEquals(listOf("Laranite"), vm.state.value.underDelivered.map { it.materialName })

            vm.onAmount(0, "180")

            assertTrue("exactly what was delivered is allowed", vm.state.value.submittable)
        }

    /** An order nothing has been delivered on has no floor at all. */
    @Test
    fun `an undelivered order can be lowered freely`() =
        runTest(dispatcher) {
            order = order(handedOver = 0.0)
            val vm = model()
            advanceUntilIdle()

            vm.onAmount(0, "1")

            assertTrue(vm.state.value.submittable)
        }

    /** A Logistician's save is the full rewrite, and it echoes the version it read. */
    @Test
    fun `a logistician save rewrites the order with its version`() =
        runTest(dispatcher) {
            val vm = model()
            advanceUntilIdle()

            vm.onSubmit()
            advanceUntilIdle()

            val (path, draft) = writes.single()
            assertEquals("update", path)
            assertEquals(VERSION, draft.version)
            assertEquals("o1", vm.state.value.created)
        }

    /** The requester's save goes to the other endpoint, and the head fields are locked. */
    @Test
    fun `a requester save uses the narrower endpoint`() =
        runTest(dispatcher) {
            val vm = model(OrderFormMode.EDIT_AS_REQUESTER)
            advanceUntilIdle()

            assertFalse(vm.state.value.mode.headEditable)

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals("requested", writes.single().first)
        }

    /** Records which endpoint was written and what it carried. */
    private inner class RecordingSource :
        JobOrderCreateSource,
        JobOrderSource {
        override suspend fun searchMaterials(query: String) =
            ApiResult.Success(MaterialMatches(rows = emptyList(), more = false))

        override suspend fun create(draft: JobOrderDraft): ApiResult<String> = error("not a create")

        override suspend fun update(
            orderId: String,
            draft: JobOrderDraft,
        ): ApiResult<Unit> {
            writes.add("update" to draft)
            return ApiResult.Success(Unit)
        }

        override suspend fun updateAsRequester(
            orderId: String,
            draft: JobOrderDraft,
        ): ApiResult<Unit> {
            writes.add("requested" to draft)
            return ApiResult.Success(Unit)
        }

        override suspend fun searchItems(query: String) = ApiResult.Success(emptyList<Pair<String, String>>())

        override suspend fun blueprintsFor(gameItemId: String) =
            ApiResult.Success(emptyList<Pair<String, String>>())

        override suspend fun createItems(draft: JobOrderItemDraft): ApiResult<String> =
            error("not an item create")

        override suspend fun queue(
            statuses: Set<JobOrderStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<JobOrderPage> = error("the edit reads one order")

        override suspend fun detail(id: String): ApiResult<JobOrder> = ApiResult.Success(order)

        override suspend fun ageThresholds(): JobOrderAgeThresholds = error("the edit reads no thresholds")

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

    /** The two unit pickers. */
    private inner class FakeUnits : OrgUnitSource {
        override suspend fun memberships(): ApiResult<List<OrgUnit>> = activeAllKinds()

        override suspend fun activeAllKinds(): ApiResult<List<OrgUnit>> =
            ApiResult.Success(
                listOf(
                    OrgUnit("s1", "Staffel 1", "S1", OrgUnitKind.SQUADRON, profitEligible = true),
                    OrgUnit("sk1", "SK Vanguard", "SKV", OrgUnitKind.SPECIAL_COMMAND, profitEligible = true),
                ),
            )

        override suspend fun serverDefault(): ApiResult<String?> = ApiResult.Success(null)
    }
}

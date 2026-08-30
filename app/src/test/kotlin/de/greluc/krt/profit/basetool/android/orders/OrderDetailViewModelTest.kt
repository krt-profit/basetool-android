/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemHandoverDto
import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.ClaimBucket
import de.greluc.krt.profit.basetool.android.core.data.ClaimQuality
import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAssignee
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemStock
import de.greluc.krt.profit.basetool.android.core.data.JobOrderPage
import de.greluc.krt.profit.basetool.android.core.data.JobOrderSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.data.JobOrderWorkSource
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialClaimSource
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.data.ProductionBooking
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The Zusagen, which this class does not exercise. */
private object NoClaimSource : MaterialClaimSource {
    override suspend fun buckets(orderId: String): ApiResult<List<ClaimBucket>> =
        ApiResult.Success(emptyList())

    override suspend fun upsert(
        orderId: String,
        materialId: String,
        quality: ClaimQuality,
        orgUnitId: String,
        amount: Double,
    ): ApiResult<Unit> = error("the Zusagen have their own test")

    override suspend fun withdraw(
        orderId: String,
        claimId: String,
    ): ApiResult<Unit> = error("the Zusagen have their own test")
}

/** The caller's units — never asked here. */
private object NoOrgUnits : OrgUnitSource {
    override suspend fun memberships(): ApiResult<List<OrgUnit>> = ApiResult.Success(emptyList())

    override suspend fun activeAllKinds(): ApiResult<List<OrgUnit>> = ApiResult.Success(emptyList())

    override suspend fun serverDefault(): ApiResult<String?> = ApiResult.Success(null)
}

/** Where produced stock could land — never asked here. */
private object NoBookInOptions : BookInOptions {
    override suspend fun locations(query: String): ApiResult<List<LocationOption>> = ApiResult.Success(emptyList())

    override suspend fun members(query: String): ApiResult<List<MemberOption>> = ApiResult.Success(emptyList())

    override suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>> = ApiResult.Success(emptyList())
}

/**
 * The two work seams, neither of which this class exercises.
 *
 * `OrderHandoverTest` and `OrderProductionTest` cover them; here they exist so the view model can
 * be built.
 */
private object NoWorkSource : JobOrderWorkSource {
    override suspend fun stockFor(
        orderId: String,
        materialId: String,
    ): ApiResult<List<HandoverStockRow>> = error("the handover has its own test")

    override suspend fun record(
        orderId: String,
        inventoryItemId: String,
        amount: String,
        recipientHandle: String,
        recipientSquadron: String?,
        handoverTime: String,
    ): ApiResult<JobOrderHandoverDto> = error("the handover has its own test")

    override suspend fun recordItemHandover(
        orderId: String,
        itemId: String,
        amount: Int,
        recipientHandle: String,
        handoverTime: String,
    ): ApiResult<JobOrderItemHandoverDto> = error("the item handover has its own test")

    override suspend fun linkedStock(
        orderId: String,
        materialId: String,
    ): ApiResult<List<HandoverStockRow>> = error("the Herstellung has its own test")

    override suspend fun bookProduction(booking: ProductionBooking): ApiResult<Unit> =
        error("the Herstellung has its own test")
}

/**
 * What one order's screen may do.
 *
 * The rules with teeth: a write is only offered once the app knows who the caller is, the note is
 * locked on the **assignee edge's** version rather than the order's, and a refusal keeps the
 * editor open with what was typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderDetailViewModelTest {
    private companion object {
        const val ORDER_VERSION = 3L
        const val EDGE_VERSION = 7L
    }

    private val dispatcher = StandardTestDispatcher()

    private class FakeConnectivity(
        initial: Boolean = true,
    ) : Connectivity {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> get() = state
    }

    /**
     * Answers the identity read.
     *
     * @property answer what to return.
     */
    private class FakeIdentity(
        private val answer: ApiResult<Identity>,
    ) : IdentitySource {
        override fun forget() = Unit

        override suspend fun myUserId(): ApiResult<String> =
            when (answer) {
                is ApiResult.Failure -> answer
                is ApiResult.Success -> ApiResult.Success(answer.value.userId)
            }

        override suspend fun me(): ApiResult<Identity> = answer
    }

    /** Records every write and answers with whatever the test set up. */
    private class FakeSource(
        var order: JobOrder,
    ) : JobOrderSource {
        val assignments = mutableListOf<Triple<String, String, Boolean>>()
        val notes = mutableListOf<List<Any?>>()
        val statuses = mutableListOf<Pair<JobOrderStatus, Long?>>()
        val priorities = mutableListOf<Int>()
        var answer: ApiResult<JobOrder>? = null

        override suspend fun queue(
            statuses: Set<JobOrderStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<JobOrderPage> = error("the detail never reads the queue")

        /** The queue's age thresholds; the defaults, since no test tunes them. */
        override suspend fun ageThresholds(): JobOrderAgeThresholds = JobOrderAgeThresholds()

        override suspend fun itemStock(id: String): ApiResult<List<JobOrderItemStock>> =
            ApiResult.Success(emptyList())

        override suspend fun detail(id: String): ApiResult<JobOrder> = ApiResult.Success(order)

        override suspend fun setAssigned(
            id: String,
            userId: String,
            assigned: Boolean,
        ): ApiResult<JobOrder> {
            assignments.add(Triple(id, userId, assigned))
            return answer ?: ApiResult.Success(order)
        }

        override suspend fun setAssigneeNote(
            id: String,
            userId: String,
            note: String?,
            version: Long?,
        ): ApiResult<JobOrder> {
            notes.add(listOf(id, userId, note, version))
            return answer ?: ApiResult.Success(order)
        }

        override suspend fun setStatus(
            id: String,
            status: JobOrderStatus,
            version: Long?,
        ): ApiResult<JobOrder> {
            statuses.add(status to version)
            return answer ?: ApiResult.Success(order)
        }

        override suspend fun setPriority(
            id: String,
            priority: Int,
        ): ApiResult<JobOrder> {
            priorities.add(priority)
            return answer ?: ApiResult.Success(order)
        }
    }

    private lateinit var source: FakeSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = FakeSource(order())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun order(vararg assignees: JobOrderAssignee) =
        JobOrder(
            id = "o1",
            displayId = "1042",
            status = JobOrderStatus.OPEN,
            rawStatus = "OPEN",
            priority = 1,
            type = "MATERIAL",
            requestingOrgUnit = null,
            requestingOrgUnitId = null,
            responsibleOrgUnit = null,
            responsibleOrgUnitId = null,
            handle = "Rhea",
            comment = null,
            materials = emptyList(),
            items = emptyList(),
            handovers = emptyList(),
            assignees = assignees.toList(),
            createdAt = null,
            version = ORDER_VERSION,
            redacted = false,
        )

    private fun mine(note: String? = null) =
        JobOrderAssignee(userId = "u1", name = "Rhea", note = note, version = EDGE_VERSION)

    private fun model(
        identity: ApiResult<Identity> = ApiResult.Success(Identity("u1", logistician = false)),
        connectivity: Connectivity = FakeConnectivity(),
    ) = OrderDetailViewModel(
        OrderDetailSources(
            orders = source,
            work = NoWorkSource,
            bookIn = NoBookInOptions,
            claims = NoClaimSource,
            orgUnits = NoOrgUnits,
            identity = FakeIdentity(identity),
        ),
        connectivity,
        "o1",
    )

    @Test
    fun `the caller's own row is the one that offers anything`() =
        runTest(dispatcher) {
            source = FakeSource(order(mine(), JobOrderAssignee("u2", "Kell", null, 1L)))
            val vm = model()
            vm.load()
            advanceUntilIdle()

            assertEquals("u1", vm.state.value.myAssignment?.userId)
        }

    @Test
    fun `nothing is offered while the app does not know who the caller is`() =
        runTest(dispatcher) {
            // An assignment addresses a member by id, and there is no id to address. Offering the
            // action anyway would put the wrong name on the order or fail.
            val vm = model(identity = ApiResult.Failure(ApiError.NotFound()))
            vm.load()
            advanceUntilIdle()

            assertEquals(false, vm.state.value.writable)
        }

    @Test
    fun `taking the order on adds the caller, and stepping off removes them`() =
        runTest(dispatcher) {
            val vm = model()
            vm.load()
            advanceUntilIdle()

            vm.onToggleAssignment()
            advanceUntilIdle()

            assertEquals(Triple("o1", "u1", true), source.assignments.single())
        }

    @Test
    fun `stepping off is the same action once the caller is on it`() =
        runTest(dispatcher) {
            source = FakeSource(order(mine()))
            val vm = model()
            vm.load()
            advanceUntilIdle()

            vm.onToggleAssignment()
            advanceUntilIdle()

            assertEquals(false, source.assignments.single().third)
        }

    @Test
    fun `the note is locked on the assignee edge, not on the order`() =
        runTest(dispatcher) {
            // Echoing the order's version would 409 the note against any unrelated change to the
            // order, and bumping the order's would 409 everyone else's screen.
            source = FakeSource(order(mine(note = "alt")))
            val vm = model()
            vm.load()
            advanceUntilIdle()
            vm.onEditNote()
            vm.onNoteChanged("Nachtschicht")

            vm.onSaveNote()
            advanceUntilIdle()

            assertEquals(listOf("o1", "u1", "Nachtschicht", EDGE_VERSION), source.notes.single())
        }

    @Test
    fun `an emptied editor clears the note rather than writing a blank one`() =
        runTest(dispatcher) {
            source = FakeSource(order(mine(note = "alt")))
            val vm = model()
            vm.load()
            advanceUntilIdle()
            vm.onEditNote()
            vm.onNoteChanged("   ")

            vm.onSaveNote()
            advanceUntilIdle()

            assertNull(source.notes.single()[2])
        }

    @Test
    fun `a refused note is offered back rather than discarded`() =
        runTest(dispatcher) {
            source = FakeSource(order(mine()))
            source.answer = ApiResult.Failure(ApiError.OptimisticLock())
            val vm = model()
            vm.load()
            advanceUntilIdle()
            vm.onEditNote()
            vm.onNoteChanged("Nachtschicht")

            vm.onSaveNote()
            advanceUntilIdle()

            // Design ch. 10 artboard 7: the typed text is never discarded. The reload succeeds
            // here, so the field shows what the order now says and the refused text is held beside
            // it — which is the pair the sheet draws.
            assertEquals("", vm.state.value.noteDraft)
            assertEquals("Nachtschicht", vm.state.value.rejectedNote)
            assertTrue(vm.state.value.error is ApiError.OptimisticLock)

            vm.onReapplyRejectedNote()

            assertEquals("Nachtschicht", vm.state.value.noteDraft)
            assertNull("re-applying consumes it, so the block does not linger", vm.state.value.rejectedNote)
        }

    @Test
    fun `a lost race puts the winner's note in the field`() =
        runTest(dispatcher) {
            source = FakeSource(order(mine(note = "Frühschicht")))
            source.answer = ApiResult.Failure(ApiError.OptimisticLock())
            val vm = model()
            vm.load()
            advanceUntilIdle()
            vm.onEditNote()
            vm.onNoteChanged("Nachtschicht")

            vm.onSaveNote()
            advanceUntilIdle()

            assertEquals("Frühschicht", vm.state.value.noteDraft)
            assertEquals("Nachtschicht", vm.state.value.rejectedNote)
        }

    @Test
    fun `the status control belongs to a Logistician alone`() =
        runTest(dispatcher) {
            val vm = model()
            vm.load()
            advanceUntilIdle()

            assertEquals(false, vm.state.value.statusChangeable)

            vm.onOpenStatusPicker()

            assertEquals(false, vm.state.value.statusPickerOpen)
        }

    @Test
    fun `a Logistician moves the order, echoing its version`() =
        runTest(dispatcher) {
            val vm = model(identity = ApiResult.Success(Identity("u1", logistician = true)))
            vm.load()
            advanceUntilIdle()
            vm.onOpenStatusPicker()

            vm.onStatusChosen(JobOrderStatus.COMPLETED)
            advanceUntilIdle()

            assertEquals(JobOrderStatus.COMPLETED to ORDER_VERSION, source.statuses.single())
            assertEquals(false, vm.state.value.statusPickerOpen)
        }

    @Test
    fun `the priority control belongs to a Logistician alone`() =
        runTest(dispatcher) {
            val vm = model()
            vm.load()
            advanceUntilIdle()

            assertEquals(false, vm.state.value.priorityChangeable)

            vm.onLowerPriority()
            advanceUntilIdle()

            assertEquals(emptyList<Int>(), source.priorities)
        }

    @Test
    fun `moving down sends the next position`() =
        runTest(dispatcher) {
            val vm = model(identity = ApiResult.Success(Identity("u1", logistician = true)))
            vm.load()
            advanceUntilIdle()

            vm.onLowerPriority()
            advanceUntilIdle()

            assertEquals(listOf(2), source.priorities)
        }

    @Test
    fun `an order already at the front does not move up`() =
        runTest(dispatcher) {
            // The fixture sits at priority 1. „Höher" and „An den Anfang" would both send 1 again,
            // which the server would happily accept and reorder the whole queue for — a write that
            // changes nothing is still a write.
            val vm = model(identity = ApiResult.Success(Identity("u1", logistician = true)))
            vm.load()
            advanceUntilIdle()

            vm.onRaisePriority(true)
            vm.onRaisePriority(false)
            advanceUntilIdle()

            assertEquals(emptyList<Int>(), source.priorities)
        }

    @Test
    fun `an order out of the queue offers no priority control`() =
        runTest(dispatcher) {
            // A completed or rejected order has no position. Offering „move it up" would be an
            // instruction to put it back into a queue it has left.
            source.order = order().copy(priority = null)
            val vm = model(identity = ApiResult.Success(Identity("u1", logistician = true)))
            vm.load()
            advanceUntilIdle()

            assertEquals(false, vm.state.value.priorityChangeable)
        }

    @Test
    fun `a refusal on the status is named rather than swallowed`() =
        runTest(dispatcher) {
            // The grant is per order, so a Logistician outside this order's slice is refused
            // exactly like a member without it.
            source.answer = ApiResult.Failure(ApiError.Forbidden())
            val vm = model(identity = ApiResult.Success(Identity("u1", logistician = true)))
            vm.load()
            advanceUntilIdle()

            vm.onStatusChosen(JobOrderStatus.COMPLETED)
            advanceUntilIdle()

            assertTrue(vm.state.value.error is ApiError.Forbidden)
        }

    @Test
    fun `nothing is written while the device has no network`() =
        runTest(dispatcher) {
            val vm = model(connectivity = FakeConnectivity(initial = false))
            vm.load()
            advanceUntilIdle()

            vm.onToggleAssignment()
            advanceUntilIdle()

            assertTrue(source.assignments.isEmpty())
            assertEquals(false, vm.state.value.online)
        }
}

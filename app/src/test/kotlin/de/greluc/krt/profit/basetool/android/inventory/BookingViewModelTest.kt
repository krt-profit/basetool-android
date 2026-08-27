/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.BookInDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutDraft
import de.greluc.krt.profit.basetool.android.core.data.BookOutKind
import de.greluc.krt.profit.basetool.android.core.data.BulkRebookResult
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryPage
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
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

/**
 * The booking form's rules.
 *
 * The three modes are three different events in the ledger, and the form's job is to make each of
 * them impossible to send half-specified: a transfer with nobody to transfer to, a sale with no
 * terminal, an amount of nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookingViewModelTest {
    private companion object {
        const val VERSION = 5L
    }

    private val dispatcher = StandardTestDispatcher()

    private class FakeConnectivity(
        initial: Boolean = true,
    ) : Connectivity {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> get() = state
    }

    /** Records every booking and answers the pickers. */
    private class FakeSource : InventorySource {
        val bookedIn = mutableListOf<BookInDraft>()
        val bookedOut = mutableListOf<Triple<String, Long?, BookOutDraft>>()
        var answer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun groups(
            page: Int,
            pageSize: Int,
        ): ApiResult<InventoryPage> =
            ApiResult.Success(
                InventoryPage(emptyList(), page = 0, totalPages = 0, totalElements = 0),
            )

        override suspend fun stacks(materialId: String): ApiResult<List<InventoryStack>> =
            ApiResult.Success(emptyList())

        override suspend fun entries(
            materialId: String,
            stack: InventoryStack,
        ): ApiResult<List<InventoryEntry>> = ApiResult.Success(emptyList())

        override suspend fun bookIn(draft: BookInDraft): ApiResult<Unit> {
            bookedIn.add(draft)
            return answer
        }

        override suspend fun bookOut(
            id: String,
            version: Long?,
            draft: BookOutDraft,
        ): ApiResult<Unit> {
            bookedOut.add(Triple(id, version, draft))
            return answer
        }

        val notes = mutableListOf<Triple<String, Long?, String?>>()

        override suspend fun updateNote(
            id: String,
            version: Long?,
            note: String?,
        ): ApiResult<Unit> {
            notes.add(Triple(id, version, note))
            return answer
        }

        override suspend fun materials(query: String): ApiResult<List<MaterialOption>> =
            ApiResult.Success(listOf(MaterialOption("m1", "Quantainium", "SCU")))

        override suspend fun locations(query: String): ApiResult<List<LocationOption>> =
            ApiResult.Success(listOf(LocationOption("l1", "ARC-L1")))

        override suspend fun members(query: String): ApiResult<List<MemberOption>> =
            ApiResult.Success(listOf(MemberOption("u1", "Rhea")))

        var orgUnitAnswer: List<OrgUnitOption> =
            listOf(OrgUnitOption("ou1", "Bereich Profit"), OrgUnitOption("ou2", "SK Nebelkraehe"))

        var orgUnitsAskedFor: String? = null

        override suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>> {
            orgUnitsAskedFor = userId
            return ApiResult.Success(orgUnitAnswer)
        }

        override suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>> =
            ApiResult.Success(listOf(TerminalOption("t1", "Area18 TDD", "5.75")))

        override suspend fun bulkRebook(
            entryIds: List<String>,
            locationId: String,
        ): ApiResult<BulkRebookResult> = ApiResult.Success(BulkRebookResult(entryIds.size, 0))

        override suspend fun setAllocation(
            entryId: String,
            kind: AllocationKind,
            targetId: String,
            amount: String,
            existing: Boolean,
            version: Long?,
        ): ApiResult<InventoryEntry> = error("not used")

        override suspend fun orderTargets(): ApiResult<List<AllocationTarget>> =
            ApiResult.Success(emptyList())

        override suspend fun missionTargets(): ApiResult<List<AllocationTarget>> =
            ApiResult.Success(emptyList())
    }

    private lateinit var source: FakeSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = FakeSource()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The pool picker asks about the receiving member, not the caller.
     *
     * The server validates the choice against the destination user's memberships, so a picker
     * filled from anyone else offers units the write then refuses.
     */
    @Test
    fun `the pool options are the receiving member's, not the caller's`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()

            // Nobody picked yet: the entry's own holder keeps it, so it is their pools that apply.
            assertEquals("u1", source.orgUnitsAskedFor)

            vm.onMemberChosen(MemberOption("u2", "Kell"))
            advanceUntilIdle()

            assertEquals("u2", source.orgUnitsAskedFor)
        }

    /**
     * The preset is what keeps a place-only transfer from re-pooling the row.
     *
     * Without it a member who changes only the location would move the stock out of the org unit
     * it belongs to, and everyone scoped to that unit would lose sight of it — with nothing on
     * screen having said so.
     */
    @Test
    fun `the pool presets to the one the entry is already in`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(owningOrgUnitId = "ou2"), BookingMode.OUT) {}
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()

            assertEquals("ou2", vm.state.value?.orgUnit?.id)
        }

    @Test
    fun `a pool the member picked survives a re-read that still offers it`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()
            vm.onOrgUnitChosen(OrgUnitOption("ou1", "Bereich Profit"))

            vm.onMemberChosen(MemberOption("u2", "Kell"))
            advanceUntilIdle()

            assertEquals("ou1", vm.state.value?.orgUnit?.id)
        }

    @Test
    fun `a receiver with no membership leaves the row unpooled rather than guessing`() =
        runTest(dispatcher) {
            source.orgUnitAnswer = emptyList()
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()

            assertNull(vm.state.value?.orgUnit)
        }

    @Test
    fun `a transfer sends the pool and the merge opt-in`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()
            vm.onMemberChosen(MemberOption("u2", "Kell"))
            advanceUntilIdle()
            vm.onOrgUnitChosen(OrgUnitOption("ou1", "Bereich Profit"))
            vm.onMergeStockChanged(true)
            vm.onSave()
            advanceUntilIdle()

            val sent = source.bookedOut.single().third
            assertEquals("ou1", sent.targetOwningOrgUnitId)
            assertTrue(sent.mergeStock)
        }

    /**
     * A `PIECE` transfer merges server-side whatever the client says.
     *
     * Sending the flag anyway would not break the write, but it would let the form claim a choice
     * the member does not have — and the sheet hides the toggle for exactly the same reason.
     */
    @Test
    fun `a piece material never sends the merge opt-in`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(unit = "PIECE"), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()
            vm.onMemberChosen(MemberOption("u2", "Kell"))
            advanceUntilIdle()
            vm.onMergeStockChanged(true)

            // Asserted before the save: a booking that lands closes the form, so the state is
            // gone by the time the draft can be read.
            assertEquals(false, vm.state.value?.materialIsScu)

            vm.onSave()
            advanceUntilIdle()

            assertEquals(false, source.bookedOut.single().third.mergeStock)
        }

    /**
     * Discarding and selling take no pool.
     *
     * Both terminate the row; neither creates an ownership stamp, and the server ignores the field.
     * Sending it would put a value on the wire that describes nothing.
     */
    @Test
    fun `discarding sends no pool even when one was picked`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            advanceUntilIdle()
            vm.onOrgUnitChosen(OrgUnitOption("ou1", "Bereich Profit"))
            vm.onOutKindChanged(BookOutKind.DISCARD)
            vm.onSave()
            advanceUntilIdle()

            assertNull(source.bookedOut.single().third.targetOwningOrgUnitId)
        }

    private fun entry(
        personal: Boolean = false,
        note: String? = null,
        unit: String = "SCU",
        owningOrgUnitId: String? = "ou2",
    ) = InventoryEntry(
        id = "e1",
        materialName = "Quantainium",
        materialId = "m1",
        unit = unit,
        locationName = "ARC-L1",
        locationId = "l1",
        holder = "Rhea",
        holderId = "u1",
        amount = "12.5",
        quality = "880",
        personal = personal,
        note = note,
        version = VERSION,
        owningOrgUnitId = owningOrgUnitId,
    )

    private fun model(connectivity: Connectivity = FakeConnectivity()) =
        BookingViewModel(source, connectivity)

    @Test
    fun `booking in needs a material, a place and an amount`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openBookIn {}

            assertEquals(false, vm.state.value?.submittable)

            vm.onMaterialChosen(MaterialOption("m1", "Quantainium", "SCU"))
            vm.onPlaceChosen(LocationOption("l1", "ARC-L1"))
            vm.onAmountChanged("12.5")

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `an amount of nothing is not an amount`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}

            vm.onAmountChanged("0")

            assertEquals(false, vm.state.value?.submittable)
        }

    @Test
    fun `a quantity keeps one separator, because SCU has fractions`() =
        runTest(dispatcher) {
            // cSCU and µSCU are real quantities. A member typing 1.2.5 is typing a typo, and the
            // field takes the first separator and drops the rest.
            val vm = model()
            vm.openBookIn {}

            vm.onAmountChanged("1,2a3.5")

            assertEquals("1.235", vm.state.value?.amount)
        }

    @Test
    fun `a transfer needs somewhere or someone to transfer to`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)

            assertEquals(false, vm.state.value?.submittable)

            vm.onMemberChosen(MemberOption("u2", "Kell"))

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `handing an entry to whoever already holds it moves nothing`() =
        runTest {
            // The picker offers the entry's own holder like anyone else, and the server refuses
            // the transfer that results. The form knows the rule too, rather than letting the
            // member find out from a refusal (found on a device, 2026-08-23).
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)

            vm.onMemberChosen(MemberOption("u1", "Rhea"))

            assertEquals(false, vm.state.value?.submittable)
        }

    @Test
    fun `handing it to the same holder at another place does move it`() =
        runTest {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)

            vm.onMemberChosen(MemberOption("u1", "Rhea"))
            vm.onPlaceChosen(LocationOption("l2", "Lorville"))

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `a sale needs a terminal`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.SELL)

            assertEquals(false, vm.state.value?.submittable)

            vm.onTerminalChosen(TerminalOption("t1", "Area18 TDD", "5.75"))

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `discarding needs nothing more than an amount`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `the amount survives a change of mode`() =
        runTest(dispatcher) {
            // A member who typed 12 before realising they meant the note should not have to type
            // it again when they switch back.
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("12")

            vm.onModeChanged(BookingMode.NOTE)

            assertEquals("12", vm.state.value?.amount)
        }

    @Test
    fun `a booking out echoes the version and names what happens`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onOutKindChanged(BookOutKind.TRANSFER)
            vm.onPlaceChosen(LocationOption("l2", "Lorville"))
            vm.onSave()
            advanceUntilIdle()

            val (id, version, draft) = source.bookedOut.single()
            assertEquals("e1", id)
            assertEquals(VERSION, version)
            assertEquals(BookOutKind.TRANSFER, draft.kind)
            assertEquals("l2", draft.targetLocationId)
        }

    @Test
    fun `a successful booking closes the form and tells the tree`() =
        runTest(dispatcher) {
            var reloaded = 0
            val vm = model()
            vm.openBookIn { reloaded++ }
            vm.onMaterialChosen(MaterialOption("m1", "Quantainium", "SCU"))
            vm.onPlaceChosen(LocationOption("l1", "ARC-L1"))
            vm.onAmountChanged("3")
            vm.onSave()
            advanceUntilIdle()

            assertNull(vm.state.value)
            assertEquals(1, reloaded)
        }

    @Test
    fun `a refused booking keeps every field`() =
        runTest(dispatcher) {
            source.answer = ApiResult.Failure(ApiError.OptimisticLock())
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")
            vm.onSave()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("2", state?.amount)
            assertTrue(state?.error is ApiError.OptimisticLock)
        }

    @Test
    fun `nothing is booked while the device has no network`() =
        runTest(dispatcher) {
            val vm = model(FakeConnectivity(initial = false))
            advanceUntilIdle()
            vm.openForEntry(entry(), BookingMode.OUT) {}
            vm.onAmountChanged("2")

            vm.onSave()
            advanceUntilIdle()

            assertTrue(source.bookedOut.isEmpty())
            assertEquals(false, vm.state.value?.online)
        }

    @Test
    fun `a sale offers the terminals of the entry's material`() =
        runTest(dispatcher) {
            // The entry carries a material id for exactly this: without it the sale would show an
            // empty list and read as a failed load.
            val vm = model()
            vm.openForEntry(entry(), BookingMode.OUT) {}

            vm.onOutKindChanged(BookOutKind.SELL)
            advanceUntilIdle()

            assertEquals(listOf("Area18 TDD"), vm.state.value?.terminals?.map { it.name })
        }

    @Test
    fun `a note opens with what the entry already says`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(note = "für Auftrag 42"), BookingMode.NOTE) {}

            assertEquals("für Auftrag 42", vm.state.value?.note)
            assertEquals(false, vm.state.value?.submittable)
        }

    @Test
    fun `an emptied note is a change worth sending`() =
        runTest(dispatcher) {
            // Clearing a note is a deliberate edit. Requiring text to save would leave the member
            // with no way to remove one.
            val vm = model()
            vm.openForEntry(entry(note = "alt"), BookingMode.NOTE) {}

            vm.onNoteChanged("")

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `a note needs no amount`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(), BookingMode.NOTE) {}

            vm.onNoteChanged("Reserviert")

            assertEquals(true, vm.state.value?.submittable)
        }

    @Test
    fun `saving an emptied note sends no note at all`() =
        runTest(dispatcher) {
            val vm = model()
            vm.openForEntry(entry(note = "alt"), BookingMode.NOTE) {}
            vm.onNoteChanged("")
            vm.onSave()
            advanceUntilIdle()

            assertEquals(Triple("e1", VERSION, null), source.notes.single())
        }
}

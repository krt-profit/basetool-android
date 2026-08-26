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
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryPage
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
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

        override suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>> =
            ApiResult.Success(listOf(TerminalOption("t1", "Area18 TDD", "5.75")))

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

    private fun entry(
        personal: Boolean = false,
        note: String? = null,
    ) = InventoryEntry(
        id = "e1",
        materialName = "Quantainium",
        materialId = "m1",
        unit = "SCU",
        locationName = "ARC-L1",
        locationId = "l1",
        holder = "Rhea",
        holderId = "u1",
        amount = "12.5",
        quality = "880",
        personal = personal,
        note = note,
        version = VERSION,
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

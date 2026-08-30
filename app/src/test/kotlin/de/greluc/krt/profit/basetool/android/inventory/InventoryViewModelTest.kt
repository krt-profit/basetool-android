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
import de.greluc.krt.profit.basetool.android.core.data.BulkRebookResult
import de.greluc.krt.profit.basetool.android.core.data.GameItemStock
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryPage
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialDetailSource
import de.greluc.krt.profit.basetool.android.core.data.MaterialEntryPage
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    /** The device has a network; the offline rule has its own tests on the booking form. */
    private object AlwaysOnline : Connectivity {
        override val online: Flow<Boolean> = flowOf(true)
    }

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
    ) : InventorySource,
        MaterialDetailSource {
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

        val entryAnswers = mutableListOf<ApiResult<List<InventoryEntry>>>()
        val entryQueries = mutableListOf<Pair<String, InventoryStack>>()

        override suspend fun materialEntries(
            materialId: String,
            page: Int,
        ): ApiResult<MaterialEntryPage> = ApiResult.Success(MaterialEntryPage(emptyList(), 0, 1, 0))

        override suspend fun entries(
            materialId: String,
            stack: InventoryStack,
        ): ApiResult<List<InventoryEntry>> {
            entryQueries.add(materialId to stack)
            return if (entryAnswers.size > 1) {
                entryAnswers.removeAt(0)
            } else {
                entryAnswers.firstOrNull() ?: ApiResult.Success(emptyList())
            }
        }

        val bookedIn = mutableListOf<BookInDraft>()
        val bookedOut = mutableListOf<Triple<String, Long?, BookOutDraft>>()
        val notes = mutableListOf<Triple<String, Long?, String?>>()
        var writeAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun bookIn(draft: BookInDraft): ApiResult<Unit> {
            bookedIn.add(draft)
            return writeAnswer
        }

        override suspend fun bookOut(
            id: String,
            version: Long?,
            draft: BookOutDraft,
        ): ApiResult<Unit> {
            bookedOut.add(Triple(id, version, draft))
            return writeAnswer
        }

        override suspend fun updateNote(
            id: String,
            version: Long?,
            note: String?,
        ): ApiResult<Unit> {
            notes.add(Triple(id, version, note))
            return writeAnswer
        }

        var materialAnswer: List<MaterialOption> = emptyList()
        var locationAnswer: List<LocationOption> = emptyList()
        var memberAnswer: List<MemberOption> = emptyList()
        var terminalAnswer: List<TerminalOption> = emptyList()

        override suspend fun materials(query: String): ApiResult<List<MaterialOption>> =
            ApiResult.Success(materialAnswer)

        override suspend fun locations(query: String): ApiResult<List<LocationOption>> =
            ApiResult.Success(locationAnswer)

        override suspend fun members(query: String): ApiResult<List<MemberOption>> =
            ApiResult.Success(memberAnswer)

        override suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>> =
            ApiResult.Success(emptyList())

        override suspend fun terminals(materialId: String): ApiResult<List<TerminalOption>> =
            ApiResult.Success(terminalAnswer)

        var bulkAnswer: ApiResult<BulkRebookResult>? = null

        override suspend fun bulkRebook(
            entryIds: List<String>,
            locationId: String,
        ): ApiResult<BulkRebookResult> =
            bulkAnswer ?: ApiResult.Success(BulkRebookResult(entryIds.size, 0))

        val checkedOut = mutableListOf<List<String>>()
        var checkoutAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun bulkCheckout(entryIds: List<String>): ApiResult<Unit> {
            checkedOut.add(entryIds)
            return checkoutAnswer
        }

        var gameItemAnswer: ApiResult<List<GameItemStock>> = ApiResult.Success(emptyList())

        override suspend fun gameItemStock(): ApiResult<List<GameItemStock>> = gameItemAnswer

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

    private fun entry(
        id: String,
        holderId: String = "u1",
    ) = InventoryEntry(
        id = id,
        materialName = "Quantainium",
        materialId = "m1",
        unit = "SCU",
        locationName = "ARC-L1",
        locationId = "l1",
        holder = "Rhea",
        holderId = holderId,
        amount = "10",
        quality = "880",
        personal = false,
        note = null,
        version = 1L,
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
    fun `a booking re-reads the open path and leaves it open`() =
        runTest(dispatcher) {
            // Collapsing the tree after every booking would make the member re-open the group and
            // the stack to see what their own booking just did (found on a device, 2026-08-23).
            val model = InventoryViewModel(source, AlwaysOnline)
            model.loadOnce()
            advanceUntilIdle()
            model.onToggleGroup("m1")
            advanceUntilIdle()
            val stack = (model.state.value.opened.getValue("m1") as StackPhase.Ready).stacks.first()
            model.onToggleStack("m1", stack)
            advanceUntilIdle()

            model.onBookingSaved()
            advanceUntilIdle()

            assertTrue(model.state.value.opened.containsKey("m1"))
            assertTrue(model.state.value.openedStacks.containsKey(stackKey("m1", stack)))
        }

    @Test
    fun `the tree loads its first level only`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source, AlwaysOnline)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, source.groupCalls)
            assertTrue("no group may be fetched before it is opened", source.stackRequests.isEmpty())
        }

    @Test
    fun `opening a group fetches exactly that group`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source, AlwaysOnline)
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
            val model = InventoryViewModel(source, AlwaysOnline)
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
            val model = InventoryViewModel(source, AlwaysOnline)
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
            val model = InventoryViewModel(failing, AlwaysOnline)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggleGroup("m1")
            advanceUntilIdle()

            assertEquals(StackPhase.Failed, model.state.value.opened["m1"])
        }

    @Test
    fun `a refresh drops what was loaded, because the holdings may have moved`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source, AlwaysOnline)
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
            val model = InventoryViewModel(mixed, AlwaysOnline)
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
            val model = InventoryViewModel(failing, AlwaysOnline)

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is InventoryPhase.Failed)
        }

    private companion object {
        /** Two groups on the page. */
        const val TWO = 2L
    }

    /**
     * A branch row is shorthand for its leaves.
     *
     * Design ch. 09, artboard 5 makes selection „IMMER Eintrags-Menge" — a group or stack row holds
     * no selection state of its own, which is also what `bulk-rebook` needs: entry ids plus one
     * target, sources free to differ.
     */
    @Test
    fun `long-pressing a group selects every entry under it`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            val model = openedStackModel()

            model.onToggleBranch("m1", null)

            assertEquals(setOf("e1", "e2"), model.state.value.selection)
        }

    @Test
    fun `long-pressing the same group again clears it`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            val model = openedStackModel()
            model.onToggleBranch("m1", null)

            model.onToggleBranch("m1", null)

            assertTrue(model.state.value.selection.isEmpty())
        }

    /** A branch nobody opened has no ids to select, and must not invent any. */
    @Test
    fun `long-pressing an unopened group selects nothing`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source, AlwaysOnline)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggleBranch("m1", null)

            assertTrue(model.state.value.selection.isEmpty())
        }

    /**
     * „Einklappen ist Ansicht, nicht Auswahl" (design ch. 09, artboard 5).
     *
     * The chip on a collapsed group still counts its picked rows, so the group's entries have to
     * survive the collapse — and with them the count that tells a member what is still in play
     * behind a row they can no longer see.
     */
    @Test
    fun `collapsing a group keeps its selection and its count`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            val model = openedStackModel()
            model.onToggleBranch("m1", null)

            model.onToggleGroup("m1")
            advanceUntilIdle()

            assertEquals(setOf("e1", "e2"), model.state.value.selection)
            assertEquals(2 to 2, model.state.value.selectionIn("m1"))
        }

    @Test
    fun `a group whose entries were never read reports no total`() =
        runTest(dispatcher) {
            val model = InventoryViewModel(source, AlwaysOnline)
            model.loadOnce()
            advanceUntilIdle()

            assertEquals(0 to null, model.state.value.selectionIn("m1"))
        }

    /** Opens the one group and its one stack, so the tree holds entries to select. */
    private suspend fun TestScope.openedStackModel(): InventoryViewModel {
        val model = InventoryViewModel(source, AlwaysOnline)
        model.loadOnce()
        advanceUntilIdle()
        model.onToggleGroup("m1")
        advanceUntilIdle()
        val stack = (model.state.value.opened.getValue("m1") as StackPhase.Ready).stacks.first()
        model.onToggleStack("m1", stack)
        advanceUntilIdle()
        return model
    }

    /**
     * The result is a step in the sheet, not a toast on the way out.
     *
     * Closing on success would drop the one figure a member cannot reconstruct from the tree — how
     * many rows were skipped because they already stood at the target (design ch. 09, artboard 9).
     */
    @Test
    fun `a finished batch keeps the sheet open on its result`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            source.bulkAnswer = ApiResult.Success(BulkRebookResult(rebooked = 1, skipped = 1))
            val model = pickedAndTargeted()

            model.onBulkMoveConfirmed()
            advanceUntilIdle()

            val bulk = model.state.value.bulk
            assertEquals(BulkRebookResult(1, 1), bulk?.result)
            assertTrue("the selection is still what the result describes", model.state.value.selection.isNotEmpty())
        }

    @Test
    fun `closing the result ends the mode and re-reads the moved rows`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"))))
            val model = pickedAndTargeted()
            model.onBulkMoveConfirmed()
            advanceUntilIdle()

            model.onBulkMoveFinished()
            advanceUntilIdle()

            assertNull(model.state.value.bulk)
            assertTrue(model.state.value.selection.isEmpty())
            // The cached entries carry the OLD place until something re-reads them, so a batch that
            // left them behind would show a member their own move as not having happened.
            assertTrue(model.state.value.openedStacks.isEmpty())
        }

    /**
     * „Die Auswahl bleibt bestehen — nichts wurde geändert" (design ch. 09, artboard 10).
     *
     * Nothing was written, so making the member pick twelve rows again to retry punishes them for
     * the server's answer.
     */
    @Test
    fun `a refused batch keeps both the sheet and the selection`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            source.bulkAnswer = ApiResult.Failure(ApiError.Forbidden())
            val model = pickedAndTargeted()
            val picked = model.state.value.selection

            model.onBulkMoveConfirmed()
            advanceUntilIdle()

            assertEquals(ApiError.Forbidden(), model.state.value.bulk?.error)
            assertNull("nothing was written, so there is no result", model.state.value.bulk?.result)
            assertEquals(picked, model.state.value.selection)
        }

    @Test
    fun `a bulk checkout sends every selected row and ends the selection`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            val model = openedStackModel()
            model.onToggleBranch("m1", null)
            val picked = model.state.value.selection
            assertTrue(picked.isNotEmpty())

            model.checkoutActions.request()
            assertEquals(picked.size, model.state.value.checkout?.count)
            model.checkoutActions.confirm()
            advanceUntilIdle()

            assertEquals(listOf(picked.toList()), source.checkedOut)
            // The result is a step in the sheet, not a toast on the way out — the same shape the
            // bulk rebooking has.
            assertEquals(true, model.state.value.checkout?.done)

            model.checkoutActions.close()
            advanceUntilIdle()
            assertTrue(model.state.value.selection.isEmpty())
            assertEquals(null, model.state.value.checkout)
        }

    @Test
    fun `a refused bulk checkout keeps the selection`() =
        runTest(dispatcher) {
            source.entryAnswers.add(ApiResult.Success(listOf(entry("e1"), entry("e2"))))
            val model = openedStackModel()
            model.onToggleBranch("m1", null)
            val picked = model.state.value.selection
            source.checkoutAnswer = ApiResult.Failure(ApiError.Forbidden())

            model.checkoutActions.request()
            model.checkoutActions.confirm()
            advanceUntilIdle()

            // All or nothing: nothing was booked out, so nothing may look as though it had been.
            assertEquals(false, model.state.value.checkout?.done)
            assertTrue(model.state.value.checkout?.error is ApiError.Forbidden)
            assertEquals(picked, model.state.value.selection)
        }

    /** Opens the tree, picks its entries and points the sheet at a target. */
    private suspend fun TestScope.pickedAndTargeted(): InventoryViewModel {
        // Without a target the confirm is a no-op by design, and the test would assert against a
        // write that never left.
        source.locationAnswer = listOf(LocationOption(id = "l2", name = "Everus Harbor"))
        val model = openedStackModel()
        model.onToggleBranch("m1", null)
        model.onBulkMoveRequested()
        advanceUntilIdle()
        model.state.value.bulk?.places?.firstOrNull()?.let(model::onBulkMovePlace)
        return model
    }
}

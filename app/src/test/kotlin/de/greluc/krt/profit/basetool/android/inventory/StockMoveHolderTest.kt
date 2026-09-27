/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/**
 * The „Mein Lager" moves (design ch. 19, artboards 4 to 7; REQ-INV-007, -036, -052).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockMoveHolderTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tree: FakeTree
    private lateinit var moves: FakeMoves
    private lateinit var inventory: StubInventory

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tree = FakeTree(groups = listOf(QUANTAINIUM))
        moves = FakeMoves()
        inventory = StubInventory().apply { units = FOUR_UNITS }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.myLager(): InventoryViewModel {
        val model =
            InventoryViewModel(
                inventory,
                LagerOnline,
                lager = LagerSources(tree = tree, moves = moves, identity = FakeIdentity),
            )
        model.controls.scope(LagerScope.MY)
        advanceUntilIdle()
        return model
    }

    @Test
    fun `a personal row rebooks into the shared Lager with a pool that is never empty`() =
        runTest(dispatcher) {
            val model = myLager()

            model.moves.openRebook(lagerEntry("p1", personal = true))
            advanceUntilIdle()

            val sheet = model.state.value.move!!
            assertFalse(sheet.toPersonal)
            assertEquals("80", sheet.amount)
            assertEquals("iri", sheet.unitId)
            assertEquals(listOf("u1"), inventory.unitLookups)
            assertTrue(sheet.submittable)
        }

    @Test
    fun `a rebooking keeps the row's own unit as the preset when the caller belongs to it`() =
        runTest(dispatcher) {
            val model = myLager()

            model.moves.openRebook(lagerEntry("p1", personal = true, unit = "van"))
            advanceUntilIdle()

            assertEquals("van", model.state.value.move!!.unitId)
        }

    @Test
    fun `a shared row becomes personal without asking for a unit`() =
        runTest(dispatcher) {
            val model = myLager()

            model.moves.openRebook(lagerEntry("s1", personal = false, unit = "pro"))
            advanceUntilIdle()

            assertTrue(model.state.value.move!!.toPersonal)
            assertTrue(inventory.unitLookups.isEmpty())
        }

    @Test
    fun `more than the row holds cannot be rebooked, a part can`() =
        runTest(dispatcher) {
            val model = myLager()
            model.moves.openRebook(lagerEntry("p1", personal = true))
            advanceUntilIdle()

            model.moves.amount("120")
            assertFalse(model.state.value.move!!.submittable)
            model.moves.amount("40")
            model.moves.confirm()
            advanceUntilIdle()

            assertEquals(listOf(Triple("p1", "40", "iri")), moves.rebooked)
            assertNull(model.state.value.move)
        }

    @Test
    fun `a refused rebooking keeps the sheet open with the refusal`() =
        runTest(dispatcher) {
            moves.answer = ApiResult.Failure(ApiError.OptimisticLock())
            val model = myLager()
            model.moves.openRebook(lagerEntry("p1", personal = true))
            advanceUntilIdle()

            model.moves.confirm()
            advanceUntilIdle()

            assertTrue(model.state.value.move!!.error is ApiError.OptimisticLock)
        }

    @Test
    fun `the org-unit change offers no unit at all and waits for a change`() =
        runTest(dispatcher) {
            val model = myLager()
            model.moves.openOrgUnit(lagerEntry("p1", personal = true, unit = "iri"))
            advanceUntilIdle()
            assertFalse(model.state.value.move!!.submittable)

            model.moves.unit(null)
            model.moves.confirm()
            advanceUntilIdle()

            assertEquals(listOf("p1" to null), moves.unitChanges)
        }

    @Test
    fun `a selection with a shared row is refused before the picker, and the way out keeps the rest`() =
        runTest(dispatcher) {
            tree.entries = listOf(lagerEntry("p1", personal = true), lagerEntry("s1", personal = false))
            val model = myLager()
            model.onToggleBranch("m1")
            advanceUntilIdle()

            model.moves.openBulkOrgUnit()
            advanceUntilIdle()
            val refused = model.state.value.move!!
            assertTrue(refused.refused)
            assertEquals(1, refused.sharedInSelection)
            assertFalse(refused.submittable)

            model.moves.dropShared()
            val kept = model.state.value
            assertEquals(setOf("p1"), kept.selection)
            assertFalse(kept.move!!.refused)

            model.moves.unit("pro")
            model.moves.confirm()
            advanceUntilIdle()
            assertEquals(listOf(listOf("p1") to "pro"), moves.bulkUnitChanges)
            assertEquals(1, model.state.value.move!!.changed!!.changed)
        }

    @Test
    fun `the selection rebooks whole rows into one pool and shows its result until closed`() =
        runTest(dispatcher) {
            tree.entries = listOf(lagerEntry("p1", personal = true))
            val model = myLager()
            model.onToggleBranch("m1")
            advanceUntilIdle()

            model.moves.openBulkRebook(toPersonal = false)
            advanceUntilIdle()
            model.moves.unit("van")
            model.moves.confirm()
            advanceUntilIdle()

            assertEquals(Triple(listOf("p1"), false, "van"), moves.bulkRebooked.single())
            assertTrue(model.state.value.move!!.finished)
            model.moves.close()
            assertTrue(model.state.value.selection.isEmpty())
        }

    @Test
    fun `a part of a row is marked stolen, the rest of it stays regular`() =
        runTest(dispatcher) {
            val model = myLager()
            model.moves.openStolen(lagerEntry("p1", personal = true), stolen = true)
            model.moves.amount("40")
            model.moves.confirm()
            advanceUntilIdle()

            assertEquals(listOf(Triple("p1", true, "40")), moves.marked)
            assertNull(model.state.value.move)
        }

    @Test
    fun `a selection is unmarked whole and shows its result`() =
        runTest(dispatcher) {
            tree.entries = listOf(lagerEntry("p1", personal = true))
            val model = myLager()
            model.onToggleBranch("m1")
            advanceUntilIdle()

            model.moves.openBulkStolen(stolen = false)
            model.moves.confirm()
            advanceUntilIdle()

            assertEquals(listOf(listOf("p1") to false), moves.bulkMarked)
            assertEquals(1, model.state.value.move!!.changed!!.changed)
        }

    @Test
    fun `the merge opt-in applies to SCU rows only`() =
        runTest(dispatcher) {
            val model = myLager()
            model.moves.openRebook(lagerEntry("p1", personal = true).copy(unit = "PIECE"))
            advanceUntilIdle()

            assertFalse(model.state.value.move!!.scu)
        }
}

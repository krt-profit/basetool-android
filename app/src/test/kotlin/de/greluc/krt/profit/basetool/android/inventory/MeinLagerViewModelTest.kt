/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.data.PersonalFilter
import de.greluc.krt.profit.basetool.android.core.data.StolenFilter
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Mein Lager" as a mode of the Lager screen (design ch. 19, artboards 1 and 2): the scope, the
 * filters, the grouped read and the selection that knows its composition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeinLagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tree: FakeTree

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tree = FakeTree(groups = listOf(QUANTAINIUM))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() =
        InventoryViewModel(
            StubInventory(),
            LagerOnline,
            lager = LagerSources(tree = tree, moves = FakeMoves(), identity = FakeIdentity),
        )

    @Test
    fun `switching to Mein Lager reads the caller's grouped stock and opens groups without a second read`() =
        runTest(dispatcher) {
            val model = model()
            model.loadOnce()
            advanceUntilIdle()

            model.controls.scope(LagerScope.MY)
            advanceUntilIdle()
            model.onToggleGroup("m1")

            val state = model.state.value
            assertEquals(LagerScope.MY, tree.groupedCalls.last().first)
            assertEquals(listOf("Quantainium"), state.groups.map { it.name })
            assertEquals(2, (state.opened.getValue("m1") as StackPhase.Ready).stacks.size)
            assertEquals(1, tree.groupedCalls.size)
            assertEquals(listOf("l1"), state.locationOptions.map { it.id })
        }

    @Test
    fun `the personal filter is exclusive and tapping it again resets it to all`() =
        runTest(dispatcher) {
            val model = model()
            model.controls.scope(LagerScope.MY)
            advanceUntilIdle()

            model.controls.personal(PersonalFilter.PERSONAL_ONLY)
            advanceUntilIdle()
            assertEquals(PersonalFilter.PERSONAL_ONLY, tree.groupedCalls.last().second.personal)

            model.controls.personal(PersonalFilter.SHARED_ONLY)
            advanceUntilIdle()
            assertEquals(PersonalFilter.SHARED_ONLY, model.state.value.filter.personal)

            model.controls.personal(PersonalFilter.SHARED_ONLY)
            advanceUntilIdle()
            assertEquals(PersonalFilter.ALL, model.state.value.filter.personal)
        }

    @Test
    fun `a filtered Org-Lager reads the grouped view, an unfiltered one keeps the aggregate`() =
        runTest(dispatcher) {
            val model = model()
            model.loadOnce()
            advanceUntilIdle()
            assertTrue(tree.groupedCalls.isEmpty())

            model.controls.stolen(StolenFilter.WITHOUT)
            advanceUntilIdle()

            val (scope, filter) = tree.groupedCalls.first()
            assertEquals(LagerScope.ORG, scope)
            assertEquals(StolenFilter.WITHOUT, filter.stolen)
            assertFalse("the place options come from an unfiltered read", tree.groupedCalls.last().second.active)
            assertEquals(listOf("l1"), model.state.value.locationOptions.map { it.id })
        }

    @Test
    fun `a personal and a shared stack of the same place and grade are opened under different keys`() {
        val shared = lagerStack(personal = false)
        val personal = lagerStack(personal = true)
        val stolen = lagerStack(personal = true, stolen = true)

        assertNotEquals(stackKey("m1", shared), stackKey("m1", personal))
        assertNotEquals(stackKey("m1", personal), stackKey("m1", stolen))
    }

    @Test
    fun `a long-press on a collapsed group loads its stacks and selects every row with its kind`() =
        runTest(dispatcher) {
            tree.entries = listOf(lagerEntry("p1", personal = true))
            val model = model()
            model.controls.scope(LagerScope.MY)
            advanceUntilIdle()

            model.onToggleBranch("m1")
            advanceUntilIdle()

            assertEquals(setOf("p1"), model.state.value.selection)
            assertEquals(1 to 0, model.state.value.selectionComposition())
            assertEquals(2, tree.stackCalls.size)
        }

    @Test
    fun `select all takes the server's filtered set, not the loaded rows`() =
        runTest(dispatcher) {
            tree.ids = mapOf("p1" to true, "s1" to false, "s2" to false)
            val model = model()
            model.controls.scope(LagerScope.MY)
            advanceUntilIdle()

            model.controls.selectAll()
            advanceUntilIdle()

            assertEquals(tree.ids.keys, model.state.value.selection)
            assertEquals(1 to 2, model.state.value.selectionComposition())
        }

    @Test
    fun `switching scope drops the selection and the place filter`() =
        runTest(dispatcher) {
            tree.ids = mapOf("p1" to true)
            val model = model()
            model.controls.scope(LagerScope.MY)
            advanceUntilIdle()
            model.controls.locations(setOf("l1"))
            model.controls.selectAll()
            advanceUntilIdle()

            model.controls.scope(LagerScope.ORG)
            advanceUntilIdle()

            assertTrue(model.state.value.selection.isEmpty())
            assertTrue(model.state.value.filter.locationIds.isEmpty())
            assertFalse(model.state.value.grouped)
        }

    @Test
    fun `a personal book-in from the Org-Lager says where it went, from Mein Lager it does not`() =
        runTest(dispatcher) {
            val model = model()
            model.controls.personalBooked()
            assertTrue(model.state.value.personalNotice)
            model.controls.personalNoticeShown()

            model.controls.scope(LagerScope.MY)
            advanceUntilIdle()
            model.controls.personalBooked()
            assertFalse(model.state.value.personalNotice)
        }
}

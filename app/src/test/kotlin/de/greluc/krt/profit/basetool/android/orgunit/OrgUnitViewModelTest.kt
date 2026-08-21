/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orgunit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.greluc.krt.profit.basetool.android.core.auth.ActiveOrgUnitStore
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Which org unit the app acts in — the three-step rule, and the two ways a stored answer goes bad.
 *
 * The rule matters more than it looks. The pin becomes `X-Active-Org-Unit-Id` on **every** request,
 * so getting it wrong does not produce an error message: it produces a screen full of somebody
 * else's data, or an empty one, with no indication that the scope is the reason.
 *
 * The store is the **real** one over the app's preference file rather than a fake. Its whole job
 * is to survive a restart and to answer synchronously off an OkHttp thread; a fake would assert the
 * view model's arithmetic and none of that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrgUnitViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val staffel = OrgUnit("a1", "Staffel 1", "S1", OrgUnitKind.SQUADRON)
    private val kommando = OrgUnit("b2", "SK Vanguard", "SKV", OrgUnitKind.SPECIAL_COMMAND)

    private lateinit var store: ActiveOrgUnitStore

    /**
     * A source whose two answers the test dictates.
     *
     * @property units what the memberships read returns.
     * @property default what the server names as the active unit.
     * @property fails whether the memberships read fails outright.
     */
    private class FakeSource(
        var units: List<OrgUnit> = emptyList(),
        var default: String? = null,
        var fails: Boolean = false,
    ) : OrgUnitSource {
        override suspend fun memberships(): ApiResult<List<OrgUnit>> =
            if (fails) ApiResult.Failure(ApiError.Network(IOException("offline"))) else ApiResult.Success(units)

        override suspend fun serverDefault(): ApiResult<String?> = ApiResult.Success(default)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = ActiveOrgUnitStore(ApplicationProvider.getApplicationContext<Application>())
        store.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `with nothing pinned the server decides`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel, kommando), default = kommando.id)
            val viewModel = OrgUnitViewModel(source, store)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(kommando.id, viewModel.state.value.activeId)
            // Written back, or the badge would name one unit while the header carried none.
            assertEquals(kommando.id, store.current())
        }

    @Test
    fun `a pin on this device wins over the server`() =
        runTest(dispatcher) {
            store.pin(staffel.id)
            val source = FakeSource(units = listOf(staffel, kommando), default = kommando.id)
            val viewModel = OrgUnitViewModel(source, store)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
        }

    @Test
    fun `a pin naming a membership that is gone is dropped`() =
        runTest(dispatcher) {
            // An administrator can remove a membership. Keeping the pin would send the header for
            // a unit the backend refuses, which reads as "everything is empty" rather than as
            // "you are not in that unit any more".
            store.pin("removed-unit")
            val source = FakeSource(units = listOf(staffel), default = null)
            val viewModel = OrgUnitViewModel(source, store)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
            assertEquals(staffel.id, store.current())
        }

    @Test
    fun `a server default the member does not belong to is not taken`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel), default = "some-other-unit")
            val viewModel = OrgUnitViewModel(source, store)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
        }

    @Test
    fun `a member with no units at all leaves the badge empty rather than guessing`() =
        runTest(dispatcher) {
            val viewModel = OrgUnitViewModel(FakeSource(units = emptyList()), store)

            viewModel.load()
            advanceUntilIdle()

            assertNull(viewModel.state.value.activeId)
            assertNull(viewModel.state.value.active)
            assertTrue(viewModel.state.value.loaded)
        }

    @Test
    fun `one unit is not a choice, so no switcher is offered`() =
        runTest(dispatcher) {
            val viewModel = OrgUnitViewModel(FakeSource(units = listOf(staffel)), store)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.switchable)
        }

    @Test
    fun `choosing a unit pins it and survives a restart`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel, kommando), default = staffel.id)
            val viewModel = OrgUnitViewModel(source, store)
            viewModel.load()
            advanceUntilIdle()

            viewModel.select(kommando.id)
            advanceUntilIdle()

            assertEquals(kommando.id, viewModel.state.value.activeId)
            // A fresh store over the same file is what the next cold start does.
            assertEquals(
                kommando.id,
                ActiveOrgUnitStore(ApplicationProvider.getApplicationContext<Application>()).current(),
            )
        }

    @Test
    fun `a unit the member does not belong to cannot be pinned`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel), default = staffel.id)
            val viewModel = OrgUnitViewModel(source, store)
            viewModel.load()
            advanceUntilIdle()

            viewModel.select("not-mine")
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
            assertEquals(staffel.id, store.current())
        }

    @Test
    fun `a failed read leaves the shell usable`() =
        runTest(dispatcher) {
            // The switcher is part of the frame around every screen. Blocking on it would turn one
            // failed request into an app that cannot be opened.
            val viewModel = OrgUnitViewModel(FakeSource(fails = true), store)

            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.loaded)
            assertEquals(emptyList<OrgUnit>(), viewModel.state.value.units)
        }
}

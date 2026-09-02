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
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
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
import org.junit.Assert.assertFalse
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

        // The switcher never reads the all-kinds catalogue; only the order form does.
        override suspend fun activeAllKinds(): ApiResult<List<OrgUnit>> = ApiResult.Success(units)
    }

    /**
     * An identity whose answer the test dictates.
     *
     * @property admin what `me()` reports for the admin flag.
     * @property fails whether the read fails outright, which must land on the member behaviour.
     */
    private class FakeIdentity(
        private val admin: Boolean = false,
        private val fails: Boolean = false,
    ) : IdentitySource {
        override suspend fun myUserId(): ApiResult<String> = ApiResult.Success("u1")

        override suspend fun me(): ApiResult<Identity> =
            if (fails) {
                ApiResult.Failure(ApiError.Network(IOException("offline")))
            } else {
                ApiResult.Success(Identity(userId = "u1", logistician = false, admin = admin))
            }

        override fun forget() = Unit
    }

    private val member = FakeIdentity()
    private val admin = FakeIdentity(admin = true)

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
            val viewModel = OrgUnitViewModel(source, store, member)

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
            val viewModel = OrgUnitViewModel(source, store, member)

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
            val viewModel = OrgUnitViewModel(source, store, member)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
            assertEquals(staffel.id, store.current())
        }

    @Test
    fun `a server default the member does not belong to is not taken`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel), default = "some-other-unit")
            val viewModel = OrgUnitViewModel(source, store, member)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
        }

    @Test
    fun `a member with no units at all leaves the badge empty rather than guessing`() =
        runTest(dispatcher) {
            val viewModel = OrgUnitViewModel(FakeSource(units = emptyList()), store, member)

            viewModel.load()
            advanceUntilIdle()

            assertNull(viewModel.state.value.activeId)
            assertNull(viewModel.state.value.active)
            assertTrue(viewModel.state.value.loaded)
        }

    @Test
    fun `one unit is not a choice, so no switcher is offered`() =
        runTest(dispatcher) {
            val viewModel = OrgUnitViewModel(FakeSource(units = listOf(staffel)), store, member)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.switchable)
        }

    @Test
    fun `choosing a unit pins it and survives a restart`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel, kommando), default = staffel.id)
            val viewModel = OrgUnitViewModel(source, store, member)
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
            val viewModel = OrgUnitViewModel(source, store, member)
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
            val viewModel = OrgUnitViewModel(FakeSource(fails = true), store, member)

            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.loaded)
            assertEquals(emptyList<OrgUnit>(), viewModel.state.value.units)
        }

    @Test
    fun `choosing all units drops the pin and sends no scope`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel, kommando), default = staffel.id)
            val viewModel = OrgUnitViewModel(source, store, member)
            viewModel.load()
            advanceUntilIdle()

            viewModel.selectAll()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.allChosen)
            assertNull("no unit is active, so the interceptor sends no header", viewModel.state.value.activeId)
            assertNull(store.current())
        }

    /**
     * The reason "all" is a stored choice rather than a cleared pin.
     *
     * With the pin merely removed, this second load would fall through to the server default and
     * put the member back into one Staffel they never chose — a scope change with no interaction.
     */
    @Test
    fun `all units survives a restart instead of collapsing back to one`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel, kommando), default = staffel.id)
            OrgUnitViewModel(source, store, member).also {
                it.load()
                advanceUntilIdle()
                it.selectAll()
            }

            val restarted = OrgUnitViewModel(source, store, member)
            restarted.load()
            advanceUntilIdle()

            assertTrue(restarted.state.value.allChosen)
            assertNull(restarted.state.value.activeId)
        }

    @Test
    fun `picking a unit again leaves the all-units state`() =
        runTest(dispatcher) {
            val source = FakeSource(units = listOf(staffel, kommando), default = staffel.id)
            val viewModel = OrgUnitViewModel(source, store, member)
            viewModel.load()
            advanceUntilIdle()
            viewModel.selectAll()

            viewModel.select(kommando.id)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.allChosen)
            assertEquals(kommando.id, viewModel.state.value.activeId)
        }

    @Test
    fun `an admin with nothing pinned starts on all org units, not on the first of the catalogue`() =
        runTest(dispatcher) {
            // The defect this pins is the one that defeats the whole point of giving the app the
            // Admin role. An admin is offered the entire catalogue rather than a membership list,
            // ordered top-down, so the "first membership" fallback would pin the
            // Organisationsleitung on the very first launch — and a pinned admin is then excluded
            // from ownerless rows, because those are granted only while the header is absent. The
            // administrator would start narrower than a plain member and nothing on screen would
            // say why. The server names no default for them either, which is what lets the
            // fallback fire.
            val ol = OrgUnit("z9", "Organisationsleitung", "OL", OrgUnitKind.ORGANISATIONSLEITUNG)
            val source = FakeSource(units = listOf(ol, staffel, kommando), default = null)
            val viewModel = OrgUnitViewModel(source, store, admin)

            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.allChosen)
            assertNull(viewModel.state.value.activeId)
        }

    @Test
    fun `the admin default is written to the store, not only to the state`() =
        runTest(dispatcher) {
            // Otherwise the badge would say „Alle" while the interceptor still sent a header, or
            // the next cold start would resolve a unit again. The two must not be able to disagree.
            val ol = OrgUnit("z9", "Organisationsleitung", "OL", OrgUnitKind.ORGANISATIONSLEITUNG)
            val viewModel =
                OrgUnitViewModel(FakeSource(units = listOf(ol, staffel)), store, admin)

            viewModel.load()
            advanceUntilIdle()

            assertTrue(store.isAllChosen())
            assertNull(store.current())
        }

    @Test
    fun `an admin who has pinned a unit keeps it`() =
        runTest(dispatcher) {
            // The widening is a default, not an override: choosing one unit is still a choice, and
            // re-widening it on every launch would make the switcher useless to the one caller who
            // has the most units to choose between.
            store.pin(staffel.id)
            val viewModel =
                OrgUnitViewModel(FakeSource(units = listOf(staffel, kommando)), store, admin)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
            assertFalse(viewModel.state.value.allChosen)
        }

    @Test
    fun `a member with nothing pinned still lands on their first unit`() =
        runTest(dispatcher) {
            // The unchanged half. A single-unit member should see their unit's name, not „Alle" for
            // a scope that was never in doubt — the widening must not leak onto them.
            val viewModel =
                OrgUnitViewModel(FakeSource(units = listOf(staffel, kommando)), store, member)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
            assertFalse(viewModel.state.value.allChosen)
        }

    @Test
    fun `a failed identity read does not widen the default`() =
        runTest(dispatcher) {
            // Unknown takes the narrower path. Widening on the strength of a request that did not
            // come back would hand an ordinary member the all-units read on every offline start.
            val viewModel =
                OrgUnitViewModel(
                    FakeSource(units = listOf(staffel, kommando)),
                    store,
                    FakeIdentity(fails = true),
                )

            viewModel.load()
            advanceUntilIdle()

            assertEquals(staffel.id, viewModel.state.value.activeId)
            assertFalse(viewModel.state.value.allChosen)
        }
}

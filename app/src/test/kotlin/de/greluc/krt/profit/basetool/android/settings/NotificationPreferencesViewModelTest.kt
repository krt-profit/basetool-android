/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import de.greluc.krt.profit.basetool.android.core.data.NotificationPreference
import de.greluc.krt.profit.basetool.android.core.data.NotificationPreferencesSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Tests the notification switches: the optimistic flip, the rollback to what the server holds, the
 * rows that cannot be switched, and a type this build has never seen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPreferencesViewModelTest {
    private companion object {
        const val ORDER = "JOB_ORDER_CREATED"
        const val BANK = "BANK_BOOKING_REQUEST_CREATED"
        const val LOCKED = "ACCOUNT_DELETION_REQUESTED"
        const val NEW_TYPE = "SOMETHING_THE_SERVER_ADDED_LATER"

        /** How many types the fake server lists. */
        const val LISTED = 4
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * A backend holding one switch per type.
     *
     * @property stored the server's rows, by type.
     * @property failWrites when set, every write is refused with this error and nothing is stored.
     * @property gate when set, a write waits on it, so a test can look at the state in between.
     */
    private class Server(
        val stored: MutableMap<String, NotificationPreference> =
            linkedMapOf(
                ORDER to NotificationPreference(ORDER, mutable = true, muted = false),
                BANK to NotificationPreference(BANK, mutable = true, muted = true),
                LOCKED to NotificationPreference(LOCKED, mutable = false, muted = false),
                NEW_TYPE to NotificationPreference(NEW_TYPE, mutable = true, muted = false),
            ),
        var failWrites: ApiError? = null,
        var failReads: ApiError? = null,
        var gate: CompletableDeferred<Unit>? = null,
    ) : NotificationPreferencesSource {
        var reads = 0
        val writes = mutableListOf<Pair<String, Boolean>>()

        override suspend fun preferences(): ApiResult<List<NotificationPreference>> {
            reads++
            val refusal = failReads
            return if (refusal != null) ApiResult.Failure(refusal) else ApiResult.Success(stored.values.toList())
        }

        override suspend fun setMuted(
            type: String,
            muted: Boolean,
        ): ApiResult<NotificationPreference> {
            gate?.await()
            writes += type to muted
            val refusal = failWrites
            if (refusal != null) {
                return ApiResult.Failure(refusal)
            }
            val row = stored.getValue(type).copy(muted = muted)
            stored[type] = row
            return ApiResult.Success(row)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun NotificationPreferencesViewModel.row(type: String): NotificationPreference =
        state.value.rows.first { it.type == type }

    @Test
    fun `the first read lists every type in the server's order`() =
        runTest(dispatcher) {
            val model = NotificationPreferencesViewModel(Server())

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.read)
            assertFalse(model.state.value.reading)
            assertEquals(listOf(ORDER, BANK, LOCKED, NEW_TYPE), model.state.value.rows.map { it.type })
        }

    @Test
    fun `loadOnce reads once, refresh reads again`() =
        runTest(dispatcher) {
            val server = Server()
            val model = NotificationPreferencesViewModel(server)

            model.loadOnce()
            model.loadOnce()
            advanceUntilIdle()
            assertEquals(1, server.reads)

            model.refresh()
            advanceUntilIdle()
            assertEquals(2, server.reads)
        }

    @Test
    fun `a refused read is not an empty list`() =
        runTest(dispatcher) {
            val model = NotificationPreferencesViewModel(Server(failReads = ApiError.Server(status = 500)))

            model.loadOnce()
            advanceUntilIdle()

            assertNotNull(model.state.value.readError)
            assertFalse("nothing was read", model.state.value.read)
            assertTrue(model.state.value.rows.isEmpty())
        }

    @Test
    fun `a retry that succeeds clears the failure`() =
        runTest(dispatcher) {
            val server = Server(failReads = ApiError.Server(status = 500))
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            server.failReads = null
            model.refresh()
            advanceUntilIdle()

            assertNull(model.state.value.readError)
            assertTrue(model.state.value.read)
            assertEquals(LISTED, model.state.value.rows.size)
        }

    @Test
    fun `a tap flips the switch before the server answers and keeps it afterwards`() =
        runTest(dispatcher) {
            val server = Server(gate = CompletableDeferred())
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(ORDER, receive = false)
            advanceUntilIdle()

            assertTrue("flipped at once", model.row(ORDER).muted)
            assertEquals(setOf(ORDER), model.state.value.pending)
            assertTrue("nothing written yet", server.writes.isEmpty())

            server.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(ORDER to true), server.writes)
            assertTrue(model.row(ORDER).muted)
            assertTrue(model.state.value.pending.isEmpty())
            assertNull(model.state.value.writeError)
        }

    @Test
    fun `turning a muted type back on writes muted false`() =
        runTest(dispatcher) {
            val server = Server()
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(BANK, receive = true)
            advanceUntilIdle()

            assertEquals(listOf(BANK to false), server.writes)
            assertFalse(model.row(BANK).muted)
        }

    @Test
    fun `a refused write puts the switch back and says so`() =
        runTest(dispatcher) {
            val server = Server(failWrites = ApiError.Network(IOException("offline")))
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(ORDER, receive = false)
            advanceUntilIdle()

            assertFalse("the row shows what the server holds", model.row(ORDER).muted)
            assertNotNull(model.state.value.writeError)
            assertTrue(model.state.value.pending.isEmpty())
        }

    @Test
    fun `a refused write is followed by a read, so the screen adopts the server's flags`() =
        runTest(dispatcher) {
            val server = Server(failWrites = ApiError.Validation())
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()
            server.stored[ORDER] = NotificationPreference(ORDER, mutable = false, muted = false)

            model.onToggle(ORDER, receive = false)
            advanceUntilIdle()

            assertEquals("one read at start-up, one after the refusal", 2, server.reads)
            assertFalse("the server now says it cannot be muted", model.row(ORDER).mutable)
        }

    @Test
    fun `the next attempt clears the last refusal`() =
        runTest(dispatcher) {
            val server = Server(failWrites = ApiError.Network(IOException("offline")))
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()
            model.onToggle(ORDER, receive = false)
            advanceUntilIdle()
            assertNotNull(model.state.value.writeError)

            server.failWrites = null
            model.onToggle(ORDER, receive = false)
            advanceUntilIdle()

            assertNull(model.state.value.writeError)
            assertTrue(model.row(ORDER).muted)
        }

    @Test
    fun `a type that cannot be muted is never written`() =
        runTest(dispatcher) {
            val server = Server()
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(LOCKED, receive = false)
            advanceUntilIdle()

            assertTrue(server.writes.isEmpty())
            assertFalse(model.row(LOCKED).muted)
        }

    @Test
    fun `a tap that changes nothing writes nothing`() =
        runTest(dispatcher) {
            val server = Server()
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(ORDER, receive = true)
            advanceUntilIdle()

            assertTrue(server.writes.isEmpty())
        }

    @Test
    fun `a second tap on a type whose write is in flight is ignored`() =
        runTest(dispatcher) {
            val server = Server(gate = CompletableDeferred())
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(ORDER, receive = false)
            model.onToggle(ORDER, receive = true)
            server.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(ORDER to true), server.writes)
            assertTrue(model.row(ORDER).muted)
        }

    @Test
    fun `two types are written independently`() =
        runTest(dispatcher) {
            val server = Server(gate = CompletableDeferred())
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(ORDER, receive = false)
            model.onToggle(BANK, receive = true)
            assertEquals(setOf(ORDER, BANK), model.state.value.pending)
            server.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(setOf(ORDER to true, BANK to false), server.writes.toSet())
            assertTrue(model.row(ORDER).muted)
            assertFalse(model.row(BANK).muted)
        }

    @Test
    fun `a type this build has never seen is listed and can be switched`() =
        runTest(dispatcher) {
            val server = Server()
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle(NEW_TYPE, receive = false)
            advanceUntilIdle()

            assertEquals(listOf(NEW_TYPE to true), server.writes)
            assertTrue(model.row(NEW_TYPE).muted)
        }

    @Test
    fun `a tap on a type that is not listed does nothing`() =
        runTest(dispatcher) {
            val server = Server()
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()

            model.onToggle("NOT_LISTED", receive = false)
            advanceUntilIdle()

            assertTrue(server.writes.isEmpty())
        }

    @Test
    fun `a read that lands during a write does not undo the flip`() =
        runTest(dispatcher) {
            val server = Server(gate = CompletableDeferred())
            val model = NotificationPreferencesViewModel(server)
            model.loadOnce()
            advanceUntilIdle()
            model.onToggle(ORDER, receive = false)

            model.refresh()
            advanceUntilIdle()

            assertTrue("the pending row keeps the member's choice", model.row(ORDER).muted)
        }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import de.greluc.krt.profit.basetool.android.core.data.BlueprintSharing
import de.greluc.krt.profit.basetool.android.core.data.MemberPreferencesSource
import de.greluc.krt.profit.basetool.android.core.data.PayoutPreference
import de.greluc.krt.profit.basetool.android.core.data.PayoutSetting
import de.greluc.krt.profit.basetool.android.core.data.RsiHandle
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.ProblemDetail
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

/**
 * Tests the server-side Einstellungen rows, whose fake models one version for all three settings, as the backend
 * stores them on the same `User` row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemberPreferencesViewModelTest {
    private companion object {
        /** An arbitrary version the row is already at, to prove nothing resets it to zero. */
        const val STORED_VERSION = 7L

        /** A second one, for the tests that need two distinguishable numbers. */
        const val OTHER_VERSION = 3L

        /** What a second `loadOnce` must NOT pick up. */
        const val MOVED_VERSION = 9L

        /** What the first `loadOnce` did pick up in that test. */
        const val FIRST_VERSION = 5L

        /** One pass reads all three values — the retry has to be a real second pass, not a redraw. */
        const val READS_PER_PASS = 3

        /** A handle another profile already carries, in the fake's lower case. */
        const val TAKEN_HANDLE = "someone_else"

        /** The server's handle alphabet, as REQ-SEC-072 states it. */
        val HANDLE_SHAPE = Regex("^[A-Za-z0-9_-]{3,60}$")
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * A backend whose three preferences share one row, and which refuses a stale version.
     *
     * @property payout the stored choice.
     * @property sharing the stored flag.
     * @property handle the stored RSI handle.
     * @property version the row's version — one counter for all, bumped by any write.
     */
    private class SharedRow(
        var payout: PayoutPreference? = null,
        var sharing: Boolean = false,
        var handle: String? = null,
        var version: Long = 1L,
    ) : MemberPreferencesSource {
        var refusals = 0
        val sentHandles = mutableListOf<String>()

        override suspend fun rsiHandle() = ApiResult.Success(RsiHandle(handle, version))

        override suspend fun setRsiHandle(
            handle: String,
            version: Long,
        ): ApiResult<RsiHandle> {
            sentHandles += handle
            return when {
                version != this.version -> {
                    refusals += 1
                    ApiResult.Failure(ApiError.OptimisticLock())
                }

                handle.lowercase() == TAKEN_HANDLE -> {
                    ApiResult.Failure(ApiError.Conflict(ProblemDetail(code = "DUPLICATE_ENTITY")))
                }

                handle.isNotEmpty() && !HANDLE_SHAPE.matches(handle) -> {
                    ApiResult.Failure(ApiError.Validation())
                }

                else -> {
                    this.handle = handle.ifEmpty { null }
                    this.version += 1
                    ApiResult.Success(RsiHandle(this.handle, this.version))
                }
            }
        }

        override suspend fun payoutPreference() =
            ApiResult.Success(PayoutSetting(payout, version))

        override suspend fun setPayoutPreference(
            preference: PayoutPreference,
            version: Long,
        ): ApiResult<PayoutSetting> {
            if (version != this.version) {
                refusals += 1
                return ApiResult.Failure(ApiError.OptimisticLock())
            }
            payout = preference
            this.version += 1
            return ApiResult.Success(PayoutSetting(payout, this.version))
        }

        override suspend fun blueprintSharing() =
            ApiResult.Success(BlueprintSharing(sharing, version))

        override suspend fun setBlueprintSharing(
            sharing: Boolean,
            version: Long,
        ): ApiResult<BlueprintSharing> {
            if (version != this.version) {
                refusals += 1
                return ApiResult.Failure(ApiError.OptimisticLock())
            }
            this.sharing = sharing
            this.version += 1
            return ApiResult.Success(BlueprintSharing(this.sharing, this.version))
        }
    }

    /**
     * A backend that refuses both reads, which is what the API vhost did for months.
     *
     * The writes are never reached in these tests — a row whose value did not arrive stays shut —
     * so they answer the same refusal rather than pretending to work.
     */
    private class UnreadableRow : MemberPreferencesSource {
        var reads = 0

        override suspend fun rsiHandle(): ApiResult<RsiHandle> {
            reads += 1
            return ApiResult.Failure(ApiError.NotFound())
        }

        override suspend fun setRsiHandle(
            handle: String,
            version: Long,
        ): ApiResult<RsiHandle> = ApiResult.Failure(ApiError.NotFound())

        override suspend fun payoutPreference(): ApiResult<PayoutSetting> {
            reads += 1
            return ApiResult.Failure(ApiError.NotFound())
        }

        override suspend fun setPayoutPreference(
            preference: PayoutPreference,
            version: Long,
        ): ApiResult<PayoutSetting> = ApiResult.Failure(ApiError.NotFound())

        override suspend fun blueprintSharing(): ApiResult<BlueprintSharing> {
            reads += 1
            return ApiResult.Failure(ApiError.NotFound())
        }

        override suspend fun setBlueprintSharing(
            sharing: Boolean,
            version: Long,
        ): ApiResult<BlueprintSharing> = ApiResult.Failure(ApiError.NotFound())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A refused read sets its own failure field while both values stay `null`, so it is distinguishable from a value
     * nobody has set.
     */
    @Test
    fun `a refused read is distinguishable from a value nobody has set`() =
        runTest(dispatcher) {
            val model = MemberPreferencesViewModel(UnreadableRow())

            model.loadOnce()
            advanceUntilIdle()

            assertNotNull("the failure has to reach the screen", model.state.value.readError)
            assertNull(model.state.value.payout)
            assertFalse("an unread payout keeps its row shut", model.state.value.payoutRead)
            assertNull(model.state.value.sharing)
            assertFalse(model.state.value.reading)
        }

    /**
     * A payout the member never chose reads as `null`, and that read is still a read: the row opens
     * and the first choice is written with the version it returned.
     */
    @Test
    fun `a read but never chosen payout can be written`() =
        runTest(dispatcher) {
            val source = SharedRow(payout = null, version = STORED_VERSION)
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            assertNull(model.state.value.payout)
            assertTrue("a read null is a value, not a missing one", model.state.value.payoutRead)

            model.onPayout(PayoutPreference.PAYOUT)
            advanceUntilIdle()

            assertEquals(0, source.refusals)
            assertEquals(PayoutPreference.PAYOUT, source.payout)
            assertEquals(PayoutPreference.PAYOUT, model.state.value.payout)
            assertEquals(STORED_VERSION + 1, model.state.value.version)
        }

    /** A read that lands leaves no failure behind. */
    @Test
    fun `a successful read carries no failure`() =
        runTest(dispatcher) {
            val model = MemberPreferencesViewModel(SharedRow(version = STORED_VERSION))

            model.loadOnce()
            advanceUntilIdle()

            assertNull(model.state.value.readError)
            assertFalse(model.state.value.reading)
        }

    /**
     * The retry clears the old message before it starts.
     *
     * A stale failure standing beside a running attempt reads as a fresh one, which is how a
     * retry that is working looks like a retry that keeps failing.
     */
    @Test
    fun `retrying clears the previous failure and re-reads both values`() =
        runTest(dispatcher) {
            val source = UnreadableRow()
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            assertNotNull(model.state.value.readError)
            assertEquals(READS_PER_PASS, source.reads)

            model.refresh()
            advanceUntilIdle()

            assertEquals(READS_PER_PASS * 2, source.reads)
            assertNotNull("still refused, so the message stands", model.state.value.readError)
        }

    @Test
    fun `both values arrive on one read pass`() =
        runTest(dispatcher) {
            val source = SharedRow(payout = PayoutPreference.DONATE, sharing = true, version = STORED_VERSION)
            val model = MemberPreferencesViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(PayoutPreference.DONATE, model.state.value.payout)
            assertEquals(true, model.state.value.sharing)
            assertEquals(STORED_VERSION, model.state.value.version)
        }

    /**
     * Writing one setting leaves the other writable, because the other adopts the row's new version.
     */
    @Test
    fun `writing one setting leaves the other writable`() =
        runTest(dispatcher) {
            val source = SharedRow()
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()
            model.onSharing(sharing = true)
            advanceUntilIdle()

            assertEquals("no write may be refused in this sequence", 0, source.refusals)
            assertEquals(PayoutPreference.DONATE, source.payout)
            assertEquals(true, source.sharing)
            assertNull(model.state.value.error)
        }

    @Test
    fun `a refusal is shown and the row keeps what the server confirmed`() =
        runTest(dispatcher) {
            val source = SharedRow(payout = PayoutPreference.PAYOUT)
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            source.version += 1

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()

            assertEquals(ApiError.OptimisticLock(), model.state.value.error)
            assertEquals(
                "the row shows what the server confirmed, not what was refused",
                PayoutPreference.PAYOUT,
                model.state.value.payout,
            )
        }

    @Test
    fun `setting a value to what it already is writes nothing`() =
        runTest(dispatcher) {
            val source = SharedRow(sharing = true, version = OTHER_VERSION)
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onSharing(sharing = true)
            advanceUntilIdle()

            assertEquals("the version must not move", OTHER_VERSION, source.version)
        }

    @Test
    fun `a second visit does not read again`() =
        runTest(dispatcher) {
            val source = SharedRow(version = FIRST_VERSION)
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            source.version = MOVED_VERSION

            model.loadOnce()
            advanceUntilIdle()

            assertEquals("loadOnce reads once", FIRST_VERSION, model.state.value.version)
        }

    /**
     * Saving the handle adopts the row's new version, so the payout row written next is not refused, and the toast
     * is due.
     */
    @Test
    fun `a saved handle moves the shared version and announces itself`() =
        runTest(dispatcher) {
            val source = SharedRow(version = STORED_VERSION)
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRsiDraft("  GrafRotz_SC ")
            model.onRsiSave()
            advanceUntilIdle()
            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()

            assertEquals("the handle goes out trimmed", listOf("GrafRotz_SC"), source.sentHandles)
            assertEquals("GrafRotz_SC", model.state.value.rsi.confirmed)
            assertTrue(model.state.value.rsi.saved)
            assertEquals("no write may be refused in this sequence", 0, source.refusals)
            assertEquals(PayoutPreference.DONATE, source.payout)

            model.onRsiSavedShown()
            assertFalse(model.state.value.rsi.saved)
        }

    /**
     * A taken handle is refused at the field: the input stays, the group shows no second message, and typing clears
     * the refusal.
     */
    @Test
    fun `a taken handle is refused at the field and the input stays`() =
        runTest(dispatcher) {
            val source = SharedRow(handle = "GrafRotz")
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRsiDraft(TAKEN_HANDLE)
            model.onRsiSave()
            advanceUntilIdle()

            assertEquals(RsiHandleRefusal.TAKEN, model.state.value.rsi.refusal)
            assertEquals(TAKEN_HANDLE, model.state.value.rsi.draft)
            assertEquals("GrafRotz", model.state.value.rsi.confirmed)
            assertNull("the field says it, the group does not", model.state.value.error)
            assertFalse(model.state.value.saving)

            model.onRsiDraft("GrafRotz_2")
            assertNull(model.state.value.rsi.refusal)
        }

    @Test
    fun `a malformed handle is refused at the field`() =
        runTest(dispatcher) {
            val model = MemberPreferencesViewModel(SharedRow())
            model.loadOnce()
            advanceUntilIdle()

            model.onRsiDraft("a b")
            model.onRsiSave()
            advanceUntilIdle()

            assertEquals(RsiHandleRefusal.INVALID, model.state.value.rsi.refusal)
            assertNull(model.state.value.error)
        }

    /** An emptied field clears the handle without asking — it is reversible. */
    @Test
    fun `an emptied field clears the handle`() =
        runTest(dispatcher) {
            val source = SharedRow(handle = "GrafRotz")
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            assertEquals("GrafRotz", model.state.value.rsi.draft)

            model.onRsiDraft("")
            model.onRsiSave()
            advanceUntilIdle()

            assertEquals(listOf(""), source.sentHandles)
            assertNull(source.handle)
            assertNull(model.state.value.rsi.confirmed)
        }

    @Test
    fun `an unchanged handle writes nothing`() =
        runTest(dispatcher) {
            val source = SharedRow(handle = "GrafRotz")
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRsiDraft(" GrafRotz ")
            model.onRsiSave()
            advanceUntilIdle()

            assertTrue(source.sentHandles.isEmpty())
        }

    /** Before the handle is read no version is known, so the row cannot write. */
    @Test
    fun `an unread handle cannot be saved`() =
        runTest(dispatcher) {
            val source = UnreadableRow()
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRsiDraft("GrafRotz")
            model.onRsiSave()
            advanceUntilIdle()

            assertFalse(model.state.value.rsi.read)
            assertFalse(model.state.value.saving)
        }
}

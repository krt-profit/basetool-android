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
 * Tests the server-side Einstellungen rows, whose fake keeps one independent version per setting, as the
 * backend's own tables for the payout preference and the blueprint sharing and the user row for the RSI handle do.
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

        /** The payout row's version where the three differ; the last row read must not be able to stand in for it. */
        const val PAYOUT_AT = 4L

        /** The sharing row's version where the three differ. */
        const val SHARING_AT = 8L

        /** The RSI-handle row's version where the three differ; it is read last. */
        const val RSI_AT = 12L

        /** One pass reads all three values — the retry has to be a real second pass, not a redraw. */
        const val READS_PER_PASS = 3

        /** A handle another profile already carries, in the fake's lower case. */
        const val TAKEN_HANDLE = "someone_else"

        /** The server's handle alphabet, as REQ-SEC-072 states it. */
        val HANDLE_SHAPE = Regex("^[A-Za-z0-9_-]{3,60}$")
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * One setting's optimistic-lock counter.
     *
     * @property version the counter a read reports and a write must echo.
     * @property exists whether the setting has a stored row; a missing one reads as `version` and its first write
     *   keeps that number, as the backend's tables do.
     */
    private class Counter(
        var version: Long = 1L,
        var exists: Boolean = true,
    ) {
        /** Moves the counter the way a successful write does. */
        fun advance() {
            if (exists) {
                version += 1
            } else {
                exists = true
            }
        }
    }

    /**
     * A backend that keeps one independent counter per setting and refuses a write that echoes any other number.
     *
     * @property payout the stored choice.
     * @property sharing the stored flag.
     * @property handle the stored RSI handle.
     * @property payoutRow the payout setting's counter.
     * @property sharingRow the sharing setting's counter.
     * @property rsiRow the RSI-handle counter.
     */
    private class PerRowBackend(
        var payout: PayoutPreference? = null,
        var sharing: Boolean = false,
        var handle: String? = null,
        val payoutRow: Counter = Counter(),
        val sharingRow: Counter = Counter(),
        val rsiRow: Counter = Counter(),
    ) : MemberPreferencesSource {
        var refusals = 0
        val sentHandles = mutableListOf<String>()

        override suspend fun rsiHandle() = ApiResult.Success(RsiHandle(handle, rsiRow.version))

        override suspend fun setRsiHandle(
            handle: String,
            version: Long,
        ): ApiResult<RsiHandle> {
            sentHandles += handle
            return when {
                version != rsiRow.version -> {
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
                    rsiRow.advance()
                    ApiResult.Success(RsiHandle(this.handle, rsiRow.version))
                }
            }
        }

        override suspend fun payoutPreference() =
            ApiResult.Success(PayoutSetting(payout, payoutRow.version))

        override suspend fun setPayoutPreference(
            preference: PayoutPreference,
            version: Long,
        ): ApiResult<PayoutSetting> {
            if (version != payoutRow.version) {
                refusals += 1
                return ApiResult.Failure(ApiError.OptimisticLock())
            }
            payout = preference
            payoutRow.advance()
            return ApiResult.Success(PayoutSetting(payout, payoutRow.version))
        }

        override suspend fun blueprintSharing() =
            ApiResult.Success(BlueprintSharing(sharing, sharingRow.version))

        override suspend fun setBlueprintSharing(
            sharing: Boolean,
            version: Long,
        ): ApiResult<BlueprintSharing> {
            if (version != sharingRow.version) {
                refusals += 1
                return ApiResult.Failure(ApiError.OptimisticLock())
            }
            this.sharing = sharing
            sharingRow.advance()
            return ApiResult.Success(BlueprintSharing(this.sharing, sharingRow.version))
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
            val source = PerRowBackend(payout = null, payoutRow = Counter(STORED_VERSION))
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
            assertEquals(STORED_VERSION + 1, model.state.value.payoutVersion)
        }

    /** A read that lands leaves no failure behind. */
    @Test
    fun `a successful read carries no failure`() =
        runTest(dispatcher) {
            val model = MemberPreferencesViewModel(PerRowBackend(payoutRow = Counter(STORED_VERSION)))

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

    /** Each read fills only its own row's version, so three different numbers all survive one pass. */
    @Test
    fun `every value arrives on one read pass with its own version`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payout = PayoutPreference.DONATE,
                    sharing = true,
                    handle = "GrafRotz",
                    payoutRow = Counter(PAYOUT_AT),
                    sharingRow = Counter(SHARING_AT),
                    rsiRow = Counter(RSI_AT),
                )
            val model = MemberPreferencesViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(PayoutPreference.DONATE, model.state.value.payout)
            assertEquals(true, model.state.value.sharing)
            assertEquals("GrafRotz", model.state.value.rsi.confirmed)
            assertEquals(PAYOUT_AT, model.state.value.payoutVersion)
            assertEquals(SHARING_AT, model.state.value.sharingVersion)
            assertEquals(RSI_AT, model.state.value.rsiVersion)
        }

    /**
     * With three different versions, each write echoes the version its own row was read at, not the one the last
     * row read left behind.
     */
    @Test
    fun `each write echoes the version of its own row`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payoutRow = Counter(PAYOUT_AT),
                    sharingRow = Counter(SHARING_AT),
                    rsiRow = Counter(RSI_AT),
                )
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()
            model.onSharing(sharing = true)
            advanceUntilIdle()
            model.onRsiDraft("GrafRotz")
            model.onRsiSave()
            advanceUntilIdle()

            assertEquals("no write may be refused in this sequence", 0, source.refusals)
            assertNull(model.state.value.error)
            assertEquals(PayoutPreference.DONATE, source.payout)
            assertEquals(true, source.sharing)
            assertEquals("GrafRotz", source.handle)
        }

    /**
     * A write moves only its own row's version: after the payout write the sharing and RSI-handle writes still
     * carry the versions they were read at, and the payout write's answer is not adopted by either.
     */
    @Test
    fun `a successful write adopts its own version and leaves the others alone`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payoutRow = Counter(PAYOUT_AT),
                    sharingRow = Counter(SHARING_AT),
                    rsiRow = Counter(RSI_AT),
                )
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()

            assertEquals(PAYOUT_AT + 1, model.state.value.payoutVersion)
            assertEquals(SHARING_AT, model.state.value.sharingVersion)
            assertEquals(RSI_AT, model.state.value.rsiVersion)

            model.onSharing(sharing = true)
            advanceUntilIdle()

            assertEquals(PAYOUT_AT + 1, model.state.value.payoutVersion)
            assertEquals(SHARING_AT + 1, model.state.value.sharingVersion)
            assertEquals(RSI_AT, model.state.value.rsiVersion)
        }

    /** The same setting written twice in a row echoes the answer to the first write the second time. */
    @Test
    fun `a setting written twice echoes the answer to its first write`() =
        runTest(dispatcher) {
            val source = PerRowBackend(payoutRow = Counter(PAYOUT_AT), sharingRow = Counter(SHARING_AT))
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()
            model.onSharing(sharing = true)
            advanceUntilIdle()
            model.onPayout(PayoutPreference.PAYOUT)
            advanceUntilIdle()

            assertEquals(0, source.refusals)
            assertEquals(PayoutPreference.PAYOUT, source.payout)
            assertEquals(PAYOUT_AT + 2, model.state.value.payoutVersion)
        }

    /**
     * A member without a stored row reads version 0 on both settings, and each first write echoes that 0; the
     * answer to the first write is the number the next one has to echo.
     */
    @Test
    fun `settings without a stored row are written from version zero`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payoutRow = Counter(version = 0L, exists = false),
                    sharingRow = Counter(version = 0L, exists = false),
                )
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            assertEquals(0L, model.state.value.payoutVersion)
            assertEquals(0L, model.state.value.sharingVersion)

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()
            model.onSharing(sharing = true)
            advanceUntilIdle()
            model.onPayout(PayoutPreference.PAYOUT)
            advanceUntilIdle()
            model.onSharing(sharing = false)
            advanceUntilIdle()

            assertEquals(0, source.refusals)
            assertEquals(PayoutPreference.PAYOUT, source.payout)
            assertEquals(false, source.sharing)
            assertEquals(1L, model.state.value.payoutVersion)
            assertEquals(1L, model.state.value.sharingVersion)
        }

    @Test
    fun `a refusal is shown and the row keeps what the server confirmed`() =
        runTest(dispatcher) {
            val source = PerRowBackend(payout = PayoutPreference.PAYOUT)
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            source.payoutRow.version += 1

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
            val source = PerRowBackend(sharing = true, sharingRow = Counter(OTHER_VERSION))
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onSharing(sharing = true)
            advanceUntilIdle()

            assertEquals("the version must not move", OTHER_VERSION, source.sharingRow.version)
        }

    @Test
    fun `a second visit does not read again`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payoutRow = Counter(FIRST_VERSION),
                    sharingRow = Counter(FIRST_VERSION),
                    rsiRow = Counter(FIRST_VERSION),
                )
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            source.payoutRow.version = MOVED_VERSION
            source.sharingRow.version = MOVED_VERSION
            source.rsiRow.version = MOVED_VERSION

            model.loadOnce()
            advanceUntilIdle()

            assertEquals("loadOnce reads once", FIRST_VERSION, model.state.value.payoutVersion)
            assertEquals("loadOnce reads once", FIRST_VERSION, model.state.value.sharingVersion)
            assertEquals("loadOnce reads once", FIRST_VERSION, model.state.value.rsiVersion)
        }

    /** A refused write on one setting leaves the other settings' versions as they were read. */
    @Test
    fun `a refused write does not disturb the other versions`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payoutRow = Counter(PAYOUT_AT),
                    sharingRow = Counter(SHARING_AT),
                    rsiRow = Counter(RSI_AT),
                )
            val model = MemberPreferencesViewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            source.payoutRow.version += 1

            model.onPayout(PayoutPreference.DONATE)
            advanceUntilIdle()

            assertEquals(1, source.refusals)
            assertEquals(PAYOUT_AT, model.state.value.payoutVersion)
            assertEquals(SHARING_AT, model.state.value.sharingVersion)
            assertEquals(RSI_AT, model.state.value.rsiVersion)

            model.onSharing(sharing = true)
            advanceUntilIdle()

            assertEquals("the other setting is still writable", 1, source.refusals)
            assertEquals(true, source.sharing)
        }

    /**
     * Saving the handle adopts the handle's new version and leaves the payout and sharing versions alone, so the
     * rows written next are not refused, and the toast is due.
     */
    @Test
    fun `a saved handle moves only its own version and announces itself`() =
        runTest(dispatcher) {
            val source =
                PerRowBackend(
                    payoutRow = Counter(PAYOUT_AT),
                    sharingRow = Counter(SHARING_AT),
                    rsiRow = Counter(RSI_AT),
                )
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
            assertEquals(RSI_AT + 1, model.state.value.rsiVersion)
            assertEquals(SHARING_AT, model.state.value.sharingVersion)
            assertEquals(PAYOUT_AT + 1, model.state.value.payoutVersion)

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
            val source = PerRowBackend(handle = "GrafRotz")
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
            val model = MemberPreferencesViewModel(PerRowBackend())
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
            val source = PerRowBackend(handle = "GrafRotz")
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
            val source = PerRowBackend(handle = "GrafRotz")
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

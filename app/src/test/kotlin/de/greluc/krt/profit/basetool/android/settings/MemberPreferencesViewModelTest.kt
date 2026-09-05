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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two Einstellungen rows that live on the server.
 *
 * The fake below is the whole point of this class: it models **one** version for both settings,
 * because that is what the backend does — they are columns of the same `User` row. An earlier
 * version of this view model kept a version per setting, and every fake that did the same agreed
 * with it. The device did not: after one payout write, every blueprint-sharing write was refused
 * with `expected=1 persisted=2` forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Robolectric, and the reason is worth keeping: `refresh()`'s failure branches call KrtLog,
// which reaches `android.util.Log`. A plain JVM unit test does not stub it (this module sets
// no `returnDefaultValues`), so the call throws inside `viewModelScope` — where the exception
// is swallowed and the only symptom is a coroutine that never finishes. Every test here
// predates the first failing READ, which is why it never came up.
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

        /** One pass reads both values — the retry has to be a real second pass, not a redraw. */
        const val READS_PER_PASS = 2
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * A backend whose two preferences share one row, and which refuses a stale version.
     *
     * @property payout the stored choice.
     * @property sharing the stored flag.
     * @property version the row's version — one counter for both, bumped by either write.
     */
    private class SharedRow(
        var payout: PayoutPreference? = null,
        var sharing: Boolean = false,
        var version: Long = 1L,
    ) : MemberPreferencesSource {
        var refusals = 0

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
     * The state the screen could not see for months.
     *
     * A refused read left both values `null`, which is exactly what a never-set value looks like —
     * so both rows sat greyed out on „Noch nicht gewählt" and the reason lived only in the log.
     * The failure is now its own field, and the values stay `null` beside it: they really are
     * unknown, and pretending otherwise would put a figure on screen the server never confirmed.
     */
    @Test
    fun `a refused read is distinguishable from a value nobody has set`() =
        runTest(dispatcher) {
            val model = MemberPreferencesViewModel(UnreadableRow())

            model.loadOnce()
            advanceUntilIdle()

            assertNotNull("the failure has to reach the screen", model.state.value.readError)
            assertNull(model.state.value.payout)
            assertNull(model.state.value.sharing)
            assertFalse(model.state.value.reading)
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

            // Both values are asked for again — the retry is a real second pass, not a redraw.
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
     * The regression the device found.
     *
     * Writing one setting bumps the row both settings sit on, so the other must adopt the new
     * version. With a version per setting the second write here is refused — and stays refused,
     * because the row keeps re-sending the number it read at start-up.
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
            // Somebody else writes the row in between — a browser session, say.
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
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.data.BankAccountDetail
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSettings
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
import de.greluc.krt.profit.basetool.android.core.data.BankBookingPage
import de.greluc.krt.profit.basetool.android.core.data.BankSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * The bank's two screens.
 *
 * The rule with teeth on the detail: the account and its first ledger page fail **together**. A
 * balance over a missing ledger reads as an account with no history rather than one that did not
 * load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankViewModelTest {
    /** The device has a network; the offline rule has its own test. */
    private object AlwaysOnline : Connectivity {
        override val online: Flow<Boolean> = flowOf(true)
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers all three reads and counts them.
     *
     * @property balanceAnswers responses for [balances], the last repeating.
     * @property accountAnswers responses for [account], likewise.
     * @property ledgerAnswers responses for [bookings], likewise.
     */
    private class RecordingSource(
        private val balanceAnswers: MutableList<ApiResult<List<BankAccountSummary>>> = mutableListOf(),
        private val accountAnswers: MutableList<ApiResult<BankAccountDetail>> = mutableListOf(),
        private val ledgerAnswers: MutableList<ApiResult<BankBookingPage>> = mutableListOf(),
    ) : BankSource {
        var balanceCalls = 0
        val ledgerPages = mutableListOf<Int>()

        override suspend fun balances(): ApiResult<List<BankAccountSummary>> {
            balanceCalls++
            return if (balanceAnswers.size > 1) balanceAnswers.removeAt(0) else balanceAnswers.first()
        }

        override suspend fun account(id: String): ApiResult<BankAccountDetail> =
            if (accountAnswers.size > 1) accountAnswers.removeAt(0) else accountAnswers.first()

        override suspend fun bookings(
            id: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<BankBookingPage> {
            ledgerPages.add(page)
            return if (ledgerAnswers.size > 1) ledgerAnswers.removeAt(0) else ledgerAnswers.first()
        }

        val settingsAnswers = mutableListOf<ApiResult<BankAccountSettings>>()
        val targets = mutableListOf<Pair<String?, Long?>>()
        val roles = mutableListOf<Pair<String, Boolean>>()
        val allMembers = mutableListOf<Boolean>()
        var writeAnswer: ApiResult<BankAccountSettings>? = null

        override suspend fun settings(id: String): ApiResult<BankAccountSettings> =
            if (settingsAnswers.size > 1) {
                settingsAnswers.removeAt(0)
            } else {
                settingsAnswers.firstOrNull() ?: ApiResult.Failure(ApiError.NotFound())
            }

        override suspend fun setBalanceTarget(
            id: String,
            target: String?,
            version: Long?,
        ): ApiResult<BankAccountSettings> {
            targets.add(target to version)
            return writeAnswer ?: settings(id)
        }

        override suspend fun setRoleVisibility(
            id: String,
            roleCode: String,
            granted: Boolean,
        ): ApiResult<BankAccountSettings> {
            roles.add(roleCode to granted)
            return writeAnswer ?: settings(id)
        }

        override suspend fun setAllMembersVisibility(
            id: String,
            granted: Boolean,
        ): ApiResult<BankAccountSettings> {
            allMembers.add(granted)
            return writeAnswer ?: settings(id)
        }
    }

    private fun summary(id: String) =
        BankAccountSummary(id, "K-001", "Einsatzkasse", "Bereich Profit", "1.0", null, emptyList())

    private fun detail() = BankAccountDetail("a1", "K-001", "Einsatzkasse", "84200.0000", null, TWO)

    private fun booking(id: String) = BankBooking(id, "DEPOSIT", "1.0", null, null, null)

    private fun ledger(
        vararg rows: BankBooking,
        page: Int = 0,
        totalPages: Int = 1,
    ) = BankBookingPage(rows.toList(), page = page, totalPages = totalPages, totalElements = TWO)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the accounts load once`() =
        runTest(dispatcher) {
            val source = RecordingSource(mutableListOf(ApiResult.Success(listOf(summary("a1")))))
            val model = BankViewModel(source)

            model.loadOnce()
            advanceUntilIdle()
            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, source.balanceCalls)
            assertEquals(BankPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `no visible account is a result, not a failure`() =
        runTest(dispatcher) {
            val source = RecordingSource(mutableListOf(ApiResult.Success(emptyList())))
            val model = BankViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(BankPhase.Ready, model.state.value.phase)
            assertTrue(model.state.value.accounts.isEmpty())
        }

    @Test
    fun `a failed list is a failure`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))))
            val model = BankViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is BankPhase.Failed)
        }

    @Test
    fun `the account and its first ledger page arrive together`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(ApiResult.Success(ledger(booking("p1")))),
                )
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")

            model.load()
            advanceUntilIdle()

            assertEquals(BankPhase.Ready, model.state.value.phase)
            assertEquals("Einsatzkasse", model.state.value.account?.name)
            assertEquals(1, model.state.value.bookings.size)
        }

    @Test
    fun `a failed ledger fails the screen rather than showing a history-less account`() =
        runTest(dispatcher) {
            // Both reads carry the same gate, so a split state would model a case the server cannot
            // produce — and a balance over a missing ledger reads as an account with no history.
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))),
                )
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")

            model.load()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is BankPhase.Failed)
            assertNull(model.state.value.account)
        }

    @Test
    fun `a refused account carries its cause so the screen can word it`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Failure(ApiError.Forbidden())),
                    mutableListOf(ApiResult.Success(ledger())),
                )
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")

            model.load()
            advanceUntilIdle()

            val phase = model.state.value.phase
            assertTrue(phase is BankPhase.Failed)
            assertTrue((phase as BankPhase.Failed).error is ApiError.Forbidden)
        }

    @Test
    fun `older ledger lines are appended`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(
                        ApiResult.Success(ledger(booking("p1"), totalPages = TWO_PAGES)),
                        ApiResult.Success(ledger(booking("p2"), page = 1, totalPages = TWO_PAGES)),
                    ),
                )
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("p1", "p2"), model.state.value.bookings.map { it.id })
            assertEquals(listOf(0, 1), source.ledgerPages)
        }

    @Test
    fun `a failed continuation keeps the ledger on screen`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(
                        ApiResult.Success(ledger(booking("p1"), totalPages = TWO_PAGES)),
                        ApiResult.Failure(ApiError.Network(IOException("x"))),
                    ),
                )
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(1, model.state.value.bookings.size)
            assertEquals(BankPhase.Ready, model.state.value.phase)
        }

    private companion object {
        /** The settings snapshot's optimistic lock. */
        const val SETTINGS_VERSION = 9L

        /** A two-line ledger. */
        const val TWO = 2L

        /** Its page count. */
        const val TWO_PAGES = 2
    }

    /**
     * A source with an account and one ledger page, ready for a settings test.
     *
     * @return the fake.
     */
    private fun accountSource() =
        RecordingSource(
            mutableListOf(ApiResult.Success(emptyList())),
            mutableListOf(ApiResult.Success(detail())),
            mutableListOf(ApiResult.Success(ledger(booking("p1")))),
        )

    /**
     * The settings snapshot the server sends.
     *
     * @param canSetTarget whether the caller may change the target.
     * @param canConfigureVisibility whether they may change who sees the account.
     * @param target the current target.
     * @param granted the role buckets already granted.
     * @param allMembers whether every member already sees it.
     * @return the settings.
     */
    private fun settings(
        canSetTarget: Boolean = true,
        canConfigureVisibility: Boolean = true,
        target: String? = "250000.0000",
        granted: List<String> = emptyList(),
        allMembers: Boolean = false,
    ) = BankAccountSettings(
        accountId = "a1",
        accountName = "Einsatzkasse",
        balanceTarget = target,
        version = SETTINGS_VERSION,
        canSetTarget = canSetTarget,
        canConfigureVisibility = canConfigureVisibility,
        visibilityConfigurable = true,
        allMembersSupported = true,
        allMembersGranted = allMembers,
        availableRoleCodes = listOf("OFFICER", "LOGISTICIAN"),
        grantedRoleCodes = granted,
    )

    @Test
    fun `the settings editor opens on a target the field can hold`() =
        runTest(dispatcher) {
            // The wire carries `250000.0000` and the field takes digits alone.
            val source = accountSource()
            source.settingsAnswers.add(ApiResult.Success(settings()))
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()

            model.onOpenSettings()

            assertEquals("250000", model.state.value.targetDraft)
        }

    @Test
    fun `a target write echoes the version it read`() =
        runTest(dispatcher) {
            val source = accountSource()
            source.settingsAnswers.add(ApiResult.Success(settings()))
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()
            model.onOpenSettings()
            model.onTargetChanged("300000")

            model.onSaveTarget()
            advanceUntilIdle()

            assertEquals("300000" to SETTINGS_VERSION, source.targets.single())
        }

    @Test
    fun `an emptied target clears it rather than setting a target of nothing`() =
        runTest(dispatcher) {
            val source = accountSource()
            source.settingsAnswers.add(ApiResult.Success(settings()))
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()
            model.onOpenSettings()
            model.onTargetChanged("")

            model.onSaveTarget()
            advanceUntilIdle()

            assertNull(source.targets.single().first)
        }

    @Test
    fun `nothing is written when the server says the caller may not`() =
        runTest(dispatcher) {
            // The flags are per-account facts the server states. The app works out no role of its
            // own, and a member who is not the responsible holder writes nothing.
            val source = accountSource()
            source.settingsAnswers.add(
                ApiResult.Success(settings(canSetTarget = false, canConfigureVisibility = false)),
            )
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()

            model.onSaveTarget()
            advanceUntilIdle()
            model.onToggleRole("OFFICER")
            advanceUntilIdle()
            model.onToggleAllMembers()
            advanceUntilIdle()

            assertTrue(source.targets.isEmpty())
            assertTrue(source.roles.isEmpty())
            assertTrue(source.allMembers.isEmpty())
        }

    @Test
    fun `a role bucket toggles against what the account already grants`() =
        runTest(dispatcher) {
            val source = accountSource()
            source.settingsAnswers.add(ApiResult.Success(settings(granted = listOf("OFFICER"))))
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()

            // One at a time: a second write while the first is in flight is dropped, which is
            // the same guard that keeps a double tap from booking twice.
            model.onToggleRole("OFFICER")
            advanceUntilIdle()
            model.onToggleRole("LOGISTICIAN")
            advanceUntilIdle()

            assertEquals(listOf("OFFICER" to false, "LOGISTICIAN" to true), source.roles)
        }

    @Test
    fun `a refused settings write is kept and named`() =
        runTest(dispatcher) {
            val source = accountSource()
            source.settingsAnswers.add(ApiResult.Success(settings()))
            source.writeAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()
            model.onOpenSettings()
            model.onTargetChanged("300000")

            model.onSaveTarget()
            advanceUntilIdle()

            assertEquals("300000", model.state.value.targetDraft)
            assertTrue(model.state.value.error is ApiError.OptimisticLock)
        }

    @Test
    fun `a settings read that fails costs the controls, not the screen`() =
        runTest(dispatcher) {
            // The account and its ledger are the screen's subject. Losing the settings leaves the
            // flags at "may not", which is the safe direction.
            val source = accountSource()
            source.settingsAnswers.add(ApiResult.Failure(ApiError.Forbidden()))
            val model = BankAccountViewModel(source, AlwaysOnline, "a1")
            model.load()
            advanceUntilIdle()

            assertNull(model.state.value.settings)
            assertEquals(BankPhase.Ready, model.state.value.phase)
        }
}

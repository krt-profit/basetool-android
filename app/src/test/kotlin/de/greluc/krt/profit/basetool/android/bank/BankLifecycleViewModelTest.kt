/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankConfirmation
import de.greluc.krt.profit.basetool.android.core.data.BankGrant
import de.greluc.krt.profit.basetool.android.core.data.BankGrantSource
import de.greluc.krt.profit.basetool.android.core.data.BankGrantee
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankLifecycleSource
import de.greluc.krt.profit.basetool.android.core.data.BankManagedAccount
import de.greluc.krt.profit.basetool.android.core.data.BankRequestPage
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffDashboard
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.DirectBooking
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
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

/**
 * The account lifecycle and the holder register.
 *
 * The rules with teeth: every write echoes the version it read, a creation without a single pinned
 * org unit is refused rather than guessed at, and deactivating a holder is **not** a removal —
 * it flips one flag and the wording says what that flag does.
 *
 * Robolectric for the reason the sibling tests are: `KrtLog` reaches `android.util.Log`, and an
 * unmocked one throws inside `viewModelScope`, whose supervisor swallows it and leaves the state
 * stuck mid-write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankLifecycleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Answers the lifecycle calls and records every write. */
    private class RecordingLifecycle : BankLifecycleSource {
        var accounts: ApiResult<List<BankManagedAccount>> = ApiResult.Success(emptyList())
        var writeAnswer: ApiResult<BankManagedAccount>? = null

        val created = mutableListOf<Pair<String, String>>()
        val renamed = mutableListOf<Triple<String, String, Long>>()
        val lifecycle = mutableListOf<Triple<String, Boolean, Long>>()
        val holderChanges = mutableListOf<Triple<String, Boolean, Long>>()

        override suspend fun managedAccounts(
            page: Int,
            pageSize: Int,
        ): ApiResult<List<BankManagedAccount>> = accounts

        override suspend fun createAccount(
            name: String,
            orgUnitId: String,
        ): ApiResult<BankManagedAccount> {
            created.add(name to orgUnitId)
            return writeAnswer ?: ApiResult.Success(account("new"))
        }

        override suspend fun renameAccount(
            id: String,
            name: String,
            version: Long,
        ): ApiResult<BankManagedAccount> {
            renamed.add(Triple(id, name, version))
            return writeAnswer ?: ApiResult.Success(account(id))
        }

        override suspend fun setAccountOpen(
            id: String,
            open: Boolean,
            version: Long,
        ): ApiResult<BankManagedAccount> {
            lifecycle.add(Triple(id, open, version))
            return writeAnswer ?: ApiResult.Success(account(id))
        }

        override suspend fun registerHolder(userId: String): ApiResult<BankHolder> =
            ApiResult.Success(holder("h-new"))

        override suspend fun setHolderActive(
            id: String,
            active: Boolean,
            version: Long,
        ): ApiResult<BankHolder> {
            holderChanges.add(Triple(id, active, version))
            return ApiResult.Success(holder(id, active = active))
        }
    }

    /** Supplies only the holder read the lifecycle tab shares with the queue. */
    private class HolderOnlyStaff(
        var holders: ApiResult<List<BankHolder>> = ApiResult.Success(emptyList()),
    ) : BankStaffSource {
        override suspend fun transferFeeRate(): ApiResult<KrtDecimal> =
            ApiResult.Success(KrtDecimal(java.math.BigDecimal("0.05")))

        override suspend fun staffDashboard(): ApiResult<BankStaffDashboard> =
            ApiResult.Failure(ApiError.Forbidden())

        override suspend fun requestQueue(
            statuses: Set<BankRequestStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<BankRequestPage> =
            ApiResult.Success(BankRequestPage(emptyList(), 0, 1, 0))

        override suspend fun holders(): ApiResult<List<BankHolder>> = holders

        override suspend fun confirmRequest(
            confirmation: BankConfirmation,
        ): ApiResult<BankBookingRequest> = ApiResult.Failure(ApiError.Forbidden())

        val directBookings = mutableListOf<DirectBooking>()
        var directAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun bookDirectly(booking: DirectBooking): ApiResult<Unit> {
            directBookings.add(booking)
            return directAnswer
        }

        override suspend fun rejectRequest(
            id: String,
            reason: String,
            version: Long,
        ): ApiResult<BankBookingRequest> = ApiResult.Failure(ApiError.Forbidden())
    }

    /** Answers the grants matrix and records every change. */
    private class RecordingGrants : BankGrantSource {
        var matrix: ApiResult<List<BankGrant>> = ApiResult.Success(emptyList())
        var answer: ApiResult<BankGrant>? = null
        val candidates = mutableMapOf<String, ApiResult<PickerPage<BankGrantee>>>()
        val searched = mutableListOf<String>()
        val written = mutableListOf<BankGrant>()
        val revoked = mutableListOf<Pair<String, String>>()

        override suspend fun grants(accountId: String): ApiResult<List<BankGrant>> = matrix

        override suspend fun setGrant(grant: BankGrant): ApiResult<BankGrant> {
            written.add(grant)
            return answer ?: ApiResult.Success(grant)
        }

        override suspend fun revokeGrant(
            userId: String,
            accountId: String,
        ): ApiResult<Unit> {
            revoked.add(userId to accountId)
            return ApiResult.Success(Unit)
        }

        override suspend fun searchGrantees(query: String): ApiResult<PickerPage<BankGrantee>> {
            searched.add(query)
            return candidates[query] ?: ApiResult.Success(PickerPage())
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

    private fun model(
        source: BankLifecycleSource,
        staff: BankStaffSource = HolderOnlyStaff(),
        grants: BankGrantSource = RecordingGrants(),
        orgUnit: String? = ORG_UNIT,
    ) = BankLifecycleViewModel(
        source = source,
        staff = staff,
        grantSource = grants,
        activeOrgUnitId = { orgUnit },
    )

    @Test
    fun `closing echoes the version it read`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            source.accounts = ApiResult.Success(listOf(account("a1", version = VERSION)))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Close(viewModel.state.value.accounts.single()))
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertEquals(listOf(Triple("a1", false, VERSION)), source.lifecycle)
            assertNull(viewModel.state.value.prompt)
        }

    @Test
    fun `reopening sends the same call with open set`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            val closed =
                account("a1", version = VERSION).copy(status = BankAccountStatus.CLOSED)
            source.accounts = ApiResult.Success(listOf(closed))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Reopen(closed))
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertEquals(listOf(Triple("a1", true, VERSION)), source.lifecycle)
        }

    @Test
    fun `a rename sends the trimmed name and the version`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            val existing = account("a1", version = VERSION)
            source.accounts = ApiResult.Success(listOf(existing))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Rename(existing, existing.name))
            viewModel.onNameChanged("  Rücklage  ")
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertEquals(listOf(Triple("a1", "Rücklage", VERSION)), source.renamed)
        }

    @Test
    fun `a rename to nothing is not sent`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            val existing = account("a1")
            source.accounts = ApiResult.Success(listOf(existing))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Rename(existing, existing.name))
            viewModel.onNameChanged("   ")
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertTrue(source.renamed.isEmpty())
        }

    @Test
    fun `a creation names the pinned org unit`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Create(""))
            viewModel.onNameChanged("Rücklage")
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertEquals(listOf("Rücklage" to ORG_UNIT), source.created)
        }

    @Test
    fun `with all units pinned there is no single owner, so nothing is created`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            // The store reports null when the caller pinned "all". Guessing a unit here would open
            // an account against one the member never chose.
            val viewModel = model(source, orgUnit = null)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Create("Rücklage"))
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertTrue(source.created.isEmpty())
        }

    @Test
    fun `deactivating a holder flips one flag and echoes their version`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            val staff = HolderOnlyStaff(ApiResult.Success(listOf(holder("h1", version = VERSION))))
            val viewModel = model(source, staff = staff)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(
                BankLifecyclePrompt.HolderActivation(viewModel.state.value.holders.single(), false),
            )
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertEquals(listOf(Triple("h1", false, VERSION)), source.holderChanges)
        }

    @Test
    fun `a refused write keeps the prompt open and states why`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            source.accounts = ApiResult.Success(listOf(account("a1")))
            source.writeAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onPrompt(BankLifecyclePrompt.Close(viewModel.state.value.accounts.single()))
            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.prompt is BankLifecyclePrompt.Close)
            assertTrue(viewModel.state.value.error is ApiError.OptimisticLock)
        }

    @Test
    fun `a holder register that cannot be read leaves the accounts standing`() =
        runTest(dispatcher) {
            val source = RecordingLifecycle()
            source.accounts = ApiResult.Success(listOf(account("a1")))
            val staff = HolderOnlyStaff(ApiResult.Failure(ApiError.Server(status = HTTP_ERROR)))
            val viewModel = model(source, staff = staff)
            viewModel.loadOnce()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.phase is BankPhase.Ready)
            assertEquals(1, viewModel.state.value.accounts.size)
            assertTrue(viewModel.state.value.holders.isEmpty())
        }

    @Test
    fun `a grant with every flag off is kept, because the row itself is the view grant`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.matrix = ApiResult.Success(listOf(grant(canDeposit = true)))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            advanceUntilIdle()

            val shown = viewModel.state.value.grants.single()
            viewModel.onSetGrant(shown.copy(canDeposit = false))
            advanceUntilIdle()

            // REQ-BANK-009: a row with all three flags false lets the member SEE the account and
            // book nothing. Deleting it here would silently take their sight away too.
            val sent = grants.written.single()
            assertEquals(false, sent.canDeposit)
            assertTrue(grants.revoked.isEmpty())
        }

    @Test
    fun `taking sight away is a revoke, not a fourth flag`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.matrix = ApiResult.Success(listOf(grant()))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            advanceUntilIdle()

            // A removal is destructive — the handoff asks for the danger modal, so nothing is sent
            // until it is confirmed.
            viewModel.onPrompt(
                BankLifecyclePrompt.RevokeGrant(
                    viewModel.state.value.grants.single(),
                    sightSurvives = false,
                ),
            )
            advanceUntilIdle()
            assertTrue(grants.revoked.isEmpty())

            viewModel.onConfirmPrompt()
            advanceUntilIdle()

            assertEquals(listOf("u1" to "a1"), grants.revoked)
        }

    @Test
    fun `a flag change echoes the version it read`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.matrix = ApiResult.Success(listOf(grant(version = VERSION)))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            advanceUntilIdle()

            viewModel.onSetGrant(viewModel.state.value.grants.single().copy(canTransfer = true))
            advanceUntilIdle()

            assertEquals(VERSION, grants.written.single().version)
        }

    @Test
    fun `picking a different account replaces the matrix rather than appending to it`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.matrix = ApiResult.Success(listOf(grant()))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            advanceUntilIdle()
            grants.matrix = ApiResult.Success(emptyList())
            viewModel.onSelectGrantAccount("a2")
            advanceUntilIdle()

            assertEquals("a2", viewModel.state.value.grantAccountId)
            assertTrue(viewModel.state.value.grants.isEmpty())
        }

    @Test
    fun `a row the server already holds is patched even when its version is zero`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            // A freshly inserted row carries version 0. Deciding create-vs-patch on that number
            // sent every first edit of an untouched grant as a creation — 409 DUPLICATE_ENTITY.
            grants.matrix = ApiResult.Success(listOf(grant(version = 0)))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            advanceUntilIdle()

            viewModel.onSetGrant(viewModel.state.value.grants.single().copy(canTransfer = true))
            advanceUntilIdle()

            assertTrue(grants.written.single().exists)
        }

    @Test
    fun `a refused flag change is reported rather than swallowed`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.matrix = ApiResult.Success(listOf(grant()))
            grants.answer = ApiResult.Failure(ApiError.Server(status = 409))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            advanceUntilIdle()

            viewModel.onSetGrant(viewModel.state.value.grants.single().copy(canDeposit = true))
            advanceUntilIdle()

            // Without this the checkbox just snaps back, which reads as a broken app.
            assertTrue(viewModel.state.value.error is ApiError.Server)
        }

    @Test
    fun `opening the sheet offers candidates before anything is typed`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.candidates[""] = ApiResult.Success(PickerPage(listOf(BankGrantee("u2", "Dorn"))))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onAddGrant()
            advanceUntilIdle()

            // An empty picker on open reads as "nobody to grant to", which is never true.
            assertEquals(listOf(""), grants.searched)
            assertEquals("Dorn", viewModel.state.value.granteeDraft?.options?.single()?.handle)
        }

    @Test
    fun `a search answer that arrives after the query moved on is dropped`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.candidates[""] = ApiResult.Success(PickerPage(listOf(BankGrantee("u2", "Dorn"))))
            grants.candidates["rh"] = ApiResult.Success(PickerPage(listOf(BankGrantee("u1", "Rhea"))))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onAddGrant()
            viewModel.onGranteeQuery("rh")
            advanceUntilIdle()

            // Both calls run; only the one whose query still stands may write. Otherwise the list
            // flickers back to the older answer and the member picks a name they did not search.
            val draft = viewModel.state.value.granteeDraft
            assertEquals("rh", draft?.query)
            assertEquals("Rhea", draft?.options?.single()?.handle)
        }

    @Test
    fun `a creation is sent as a creation, on the account that is showing`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.candidates[""] = ApiResult.Success(PickerPage(listOf(BankGrantee("u2", "Dorn"))))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            viewModel.onAddGrant()
            advanceUntilIdle()
            viewModel.onGranteeSelected(BankGrantee("u2", "Dorn"))
            viewModel.onGrantDraftChanged(
                requireNotNull(viewModel.state.value.granteeDraft).copy(canWithdraw = true),
            )
            viewModel.onCreateGrant()
            advanceUntilIdle()

            val sent = grants.written.single()
            assertEquals("u2", sent.userId)
            assertEquals("a1", sent.accountId)
            assertEquals(true, sent.canWithdraw)
            // `exists` false is what makes this a POST rather than a PATCH against a row that is
            // not there.
            assertTrue(!sent.exists)
            assertNull(viewModel.state.value.granteeDraft)
        }

    @Test
    fun `a creation with nobody picked is not sent`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            viewModel.onAddGrant()
            advanceUntilIdle()
            viewModel.onCreateGrant()
            advanceUntilIdle()

            assertTrue(grants.written.isEmpty())
        }

    @Test
    fun `a refused creation keeps the sheet open and says why`() =
        runTest(dispatcher) {
            val grants = RecordingGrants()
            grants.candidates[""] = ApiResult.Success(PickerPage(listOf(BankGrantee("u2", "Dorn"))))
            grants.answer = ApiResult.Failure(ApiError.Server(status = 409))
            val viewModel = model(RecordingLifecycle(), grants = grants)
            viewModel.onSelectGrantAccount("a1")
            viewModel.onAddGrant()
            advanceUntilIdle()
            viewModel.onGranteeSelected(BankGrantee("u2", "Dorn"))
            viewModel.onCreateGrant()
            advanceUntilIdle()

            // Closing the sheet on a refusal would throw away the pick and the flags with it.
            assertEquals("Dorn", viewModel.state.value.granteeDraft?.selected?.handle)
            assertTrue(viewModel.state.value.error is ApiError.Server)
        }

    private companion object {
        const val HTTP_ERROR = 500
        const val VERSION = 7L

        /** The pinned org unit a creation names. */
        const val ORG_UNIT = "00000000-0000-0000-0000-000000000001"

        /**
         * One grant.
         *
         * @param canDeposit whether they may book money in.
         * @param version its optimistic-locking version.
         * @return the grant.
         */
        fun grant(
            canDeposit: Boolean = false,
            version: Long = 1,
        ) = BankGrant(
            userId = "u1",
            handle = "Rhea",
            accountId = "a1",
            canDeposit = canDeposit,
            canWithdraw = false,
            canTransfer = false,
            version = version,
        )

        /**
         * One managed account.
         *
         * @param id the account.
         * @param version its optimistic-locking version.
         * @return the account.
         */
        fun account(
            id: String,
            version: Long = 1,
        ) = BankManagedAccount(
            id = id,
            accountNo = "KB-0001",
            name = "Einsatzkasse",
            type = "ORG_UNIT",
            status = BankAccountStatus.ACTIVE,
            balance = "0",
            orgUnitName = "IRIDIUM",
            version = version,
        )

        /**
         * One holder.
         *
         * @param id the holder.
         * @param active whether they still hold.
         * @param version their optimistic-locking version.
         * @return the holder.
         */
        fun holder(
            id: String,
            active: Boolean = true,
            version: Long = 1,
        ) = BankHolder(
            id = id,
            handle = "Halter $id",
            active = active,
            totalHeld = "1000",
            version = version,
        )
    }
}

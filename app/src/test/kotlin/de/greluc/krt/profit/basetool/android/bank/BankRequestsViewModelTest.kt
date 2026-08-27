/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankRequestApprover
import de.greluc.krt.profit.basetool.android.core.data.BankRequestDraft
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestSource
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankTransferTarget
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Anträge tab.
 *
 * The rules with teeth: a request the caller raised is **theirs** even when it sits on an account
 * they are responsible for — nobody approves their own — and the badge counts only what is still
 * undecided.
 *
 * Robolectric rather than plain JUnit, and not for the resources: the view model logs a refused
 * write through `KrtLog`, which reaches `android.util.Log`. Unmocked, that throws inside
 * `viewModelScope`, whose supervisor swallows it — leaving the state stuck mid-write with no
 * failure anywhere. The refusal test was green-looking nonsense until the runner was right.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankRequestsViewModelTest {
    /** The device has a network; the offline rule has its own test. */
    private object AlwaysOnline : Connectivity {
        override val online: Flow<Boolean> = flowOf(true)
    }

    /** The device has none. */
    private object Offline : Connectivity {
        override val online: Flow<Boolean> = flowOf(false)
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers the request calls and records the writes.
     *
     * @property own what [ownRequests] returns.
     * @property foreign what [foreignRequests] returns.
     */
    private class RecordingRequests(
        var own: ApiResult<List<BankBookingRequest>> = ApiResult.Success(emptyList()),
        var foreign: ApiResult<List<BankBookingRequest>> = ApiResult.Success(emptyList()),
    ) : BankRequestSource {
        val created = mutableListOf<BankRequestDraft>()
        val cancelled = mutableListOf<Pair<String, Long>>()
        val approvals = mutableListOf<Triple<String, Boolean, Long>>()
        val edits = mutableListOf<Triple<String, String, Long>>()
        var writeAnswer: ApiResult<BankBookingRequest>? = null

        override suspend fun ownRequests(): ApiResult<List<BankBookingRequest>> = own

        override suspend fun foreignRequests(): ApiResult<List<BankBookingRequest>> = foreign

        override suspend fun transferTargets(): ApiResult<List<BankTransferTarget>> =
            ApiResult.Success(listOf(BankTransferTarget("t1", "Rücklage")))

        override suspend fun createRequest(draft: BankRequestDraft): ApiResult<BankBookingRequest> {
            created.add(draft)
            return writeAnswer ?: ApiResult.Success(request("new"))
        }

        override suspend fun updateRequest(
            id: String,
            version: Long,
            amount: String,
            note: String?,
            targetAccountId: String?,
        ): ApiResult<BankBookingRequest> {
            edits.add(Triple(id, amount, version))
            return writeAnswer ?: ApiResult.Success(request(id))
        }

        override suspend fun cancelRequest(
            id: String,
            version: Long,
        ): ApiResult<BankBookingRequest> {
            cancelled.add(id to version)
            return writeAnswer ?: ApiResult.Success(request(id))
        }

        override suspend fun setOwnerApproval(
            id: String,
            granted: Boolean,
            version: Long,
        ): ApiResult<BankBookingRequest> {
            approvals.add(Triple(id, granted, version))
            return writeAnswer ?: ApiResult.Success(request(id))
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
        source: BankRequestSource,
        connectivity: Connectivity = AlwaysOnline,
        accounts: List<BankAccountSummary> = listOf(account("a1")),
    ) = BankRequestsViewModel(
        source = source,
        accountSource = { ApiResult.Success(accounts) },
        connectivity = connectivity,
    )

    @Test
    fun `own and foreign requests become one list, own ones not actionable`() =
        runTest(dispatcher) {
            val source =
                RecordingRequests(
                    own = ApiResult.Success(listOf(request("r1", createdAt = "2026-08-02T00:00:00Z"))),
                    foreign = ApiResult.Success(listOf(request("r2", createdAt = "2026-08-01T00:00:00Z"))),
                )
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            val rows = viewModel.state.value.rows
            assertEquals(listOf("r1", "r2"), rows.map { it.request.id })
            assertTrue(rows[0].mine)
            assertFalse(rows[0].actionable)
            assertFalse(rows[1].mine)
            assertTrue(rows[1].actionable)
        }

    @Test
    fun `a request on both reads stays the callers own, so they cannot approve it`() =
        runTest(dispatcher) {
            val both = request("r1")
            val source =
                RecordingRequests(
                    own = ApiResult.Success(listOf(both)),
                    foreign = ApiResult.Success(listOf(both)),
                )
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            val rows = viewModel.state.value.rows
            assertEquals(1, rows.size)
            assertTrue(rows.single().mine)
            assertFalse(rows.single().actionable)
        }

    @Test
    fun `the badge counts only undecided requests`() =
        runTest(dispatcher) {
            val source =
                RecordingRequests(
                    own =
                        ApiResult.Success(
                            listOf(
                                request("r1", status = BankRequestStatus.PENDING),
                                request("r2", status = BankRequestStatus.CONFIRMED),
                                request("r3", status = BankRequestStatus.REJECTED),
                                request("r4", status = BankRequestStatus.CANCELLED),
                            ),
                        ),
                )
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.pendingCount)
        }

    @Test
    fun `granting an approval echoes the version it read`() =
        runTest(dispatcher) {
            val source =
                RecordingRequests(foreign = ApiResult.Success(listOf(request("r9", version = APPROVAL_VERSION))))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onSetApproval(viewModel.state.value.rows.single().request, granted = true)
            advanceUntilIdle()

            assertEquals(listOf(Triple("r9", true, APPROVAL_VERSION)), source.approvals)
        }

    @Test
    fun `withdrawing echoes the version too`() =
        runTest(dispatcher) {
            val source = RecordingRequests(own = ApiResult.Success(listOf(request("r4", version = WITHDRAW_VERSION))))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onWithdraw(viewModel.state.value.rows.single().request)
            advanceUntilIdle()

            assertEquals(listOf("r4" to WITHDRAW_VERSION), source.cancelled)
        }

    @Test
    fun `an offline device sends no approval at all`() =
        runTest(dispatcher) {
            val source = RecordingRequests(foreign = ApiResult.Success(listOf(request("r9"))))
            val viewModel = model(source, connectivity = Offline)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onSetApproval(viewModel.state.value.rows.single().request, granted = true)
            advanceUntilIdle()

            assertTrue(source.approvals.isEmpty())
        }

    @Test
    fun `a new request carries the kind, the account and the transfer target`() =
        runTest(dispatcher) {
            val source = RecordingRequests()
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onCompose()
            viewModel.onDraftChanged {
                it.copy(kind = BankRequestKind.TRANSFER, targetAccountId = "t1", amount = "500")
            }
            viewModel.onSubmit()
            advanceUntilIdle()

            val draft = source.created.single()
            assertEquals(BankRequestKind.TRANSFER, draft.kind)
            assertEquals("a1", draft.accountId)
            assertEquals("t1", draft.targetAccountId)
            assertNull(viewModel.state.value.draft)
        }

    @Test
    fun `editing sends an update rather than a second request`() =
        runTest(dispatcher) {
            val existing = request("r1", version = EDIT_VERSION)
            val source = RecordingRequests(own = ApiResult.Success(listOf(existing)))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onEdit(existing)
            viewModel.onDraftChanged { it.copy(amount = "999") }
            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(listOf(Triple("r1", "999", EDIT_VERSION)), source.edits)
            assertTrue(source.created.isEmpty())
        }

    @Test
    fun `editing opens on a typeable amount, not the servers storage scale`() =
        runTest(dispatcher) {
            val existing = request("r1").copy(amount = "120000.0000")
            val source = RecordingRequests(own = ApiResult.Success(listOf(existing)))
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onEdit(existing)

            assertEquals("120000", viewModel.state.value.draft?.amount)
        }

    @Test
    fun `a refused write keeps the sheet open and states why`() =
        runTest(dispatcher) {
            val source = RecordingRequests()
            source.writeAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onCompose()
            viewModel.onDraftChanged { it.copy(amount = "500") }
            viewModel.onSubmit()
            advanceUntilIdle()

            val draft = viewModel.state.value.draft
            assertTrue(draft?.error is ApiError.OptimisticLock)
            assertFalse(draft?.saving ?: true)
        }

    @Test
    fun `a transfer without a target cannot be submitted`() {
        val draft =
            BankRequestDraftState(kind = BankRequestKind.TRANSFER, accountId = "a1", amount = "500")
        assertFalse(draft.submittable)
        assertTrue(draft.copy(targetAccountId = "t1").submittable)
    }

    @Test
    fun `an amount of zero cannot be submitted`() {
        val draft = BankRequestDraftState(accountId = "a1", amount = "0")
        assertFalse(draft.submittable)
        assertTrue(draft.copy(amount = "0.5").submittable)
    }

    private companion object {
        /** The version each write must echo, distinct per test so a mix-up shows up. */
        const val APPROVAL_VERSION = 7L
        const val WITHDRAW_VERSION = 3L
        const val EDIT_VERSION = 11L

        /**
         * A request, with only what a test cares about spelled out.
         *
         * @param id the request.
         * @param status where it stands.
         * @param version its optimistic-locking version.
         * @param createdAt when it was raised, which is what the merged list sorts on.
         * @return the request.
         */
        fun request(
            id: String,
            status: BankRequestStatus = BankRequestStatus.PENDING,
            version: Long = 1,
            createdAt: String = "2026-08-01T00:00:00Z",
        ) = BankBookingRequest(
            id = id,
            accountId = "a1",
            accountName = "Einsatzkasse",
            targetAccountId = null,
            kind = BankRequestKind.WITHDRAWAL,
            amount = "120000",
            note = "Operation Rotschild",
            status = status,
            requester = "Rhea",
            rejectReason = null,
            applicableLimit = "100000",
            requiresOwnerApproval = true,
            ownerApprovalGranted = false,
            ownerApprovalBy = null,
            requiredApprover = BankRequestApprover.RESPONSIBLE_HOLDER,
            createdAt = createdAt,
            version = version,
        )

        /**
         * An account the sheet may pick.
         *
         * @param id the account.
         * @return the summary.
         */
        fun account(id: String) =
            BankAccountSummary(
                id = id,
                accountNo = "K-001",
                name = "Einsatzkasse",
                orgUnitName = "Bereich Profit",
                balance = "84200",
                delta30d = null,
                sparkline = emptyList(),
                canRequest = true,
                approvalLimit = "100000",
            )
    }
}

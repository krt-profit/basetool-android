/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankConfirmation
import de.greluc.krt.profit.basetool.android.core.data.BankDirectOutcome
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestPage
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.BankStaffDashboard
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
import de.greluc.krt.profit.basetool.android.core.data.DirectBooking
import de.greluc.krt.profit.basetool.android.core.data.DirectBookingKind
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
import java.math.BigDecimal

/**
 * The Verwaltung scope's Übersicht tab.
 *
 * Two things carry the weight. The per-account request counter is aggregated **client-side** from
 * the queue, which is what artboard 4's handoff asks for — so it has to survive paging and has to
 * admit when it gave up. And an account the caller reaches only through their office is marked as
 * such, which is derived by subtracting the member-visible list from the staff one.
 *
 * Robolectric rather than plain JUnit for the same reason `BankRequestsViewModelTest` is: the view
 * model logs refusals through `KrtLog`, and an unmocked `android.util.Log` throws inside
 * `viewModelScope`, whose supervisor swallows it and leaves the state stuck mid-read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankStaffViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `a direct deposit sends the mode, the holder and the amount`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.directBooking.open("acc-1")
            model.directBooking.edit { it.copy(amount = "5000", holderId = "h1", note = "Bargeld") }
            model.directBooking.confirm(null)
            advanceUntilIdle()

            assertEquals(1, source.directBookings.size)
            val sent = source.directBookings.first()
            assertEquals(DirectBookingKind.DEPOSIT, sent.kind)
            assertEquals("acc-1", sent.accountId)
            assertEquals("h1", sent.holderId)
            assertEquals("5000", sent.amount)
            // The sheet closes on success; the dashboard is re-read rather than patched.
            assertNull(model.state.value.direct)
        }

    /**
     * A `202` closes the sheet like a booking and must not read like one.
     *
     * Over the KRT employee ceiling the server files the attempt as an approval request instead of
     * booking it (REQ-BANK-047, ADR-0109). The balance does not move, so a member who is told
     * nothing finds the old figure and reads it as a fault — the one outcome on this screen that
     * looks like success and is not.
     */
    @Test
    fun `a filed withdrawal raises the notice that nothing was booked`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            source.directAnswer = ApiResult.Success(BankDirectOutcome.REQUEST_FILED)
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(kind = DirectBookingKind.WITHDRAWAL, amount = "5000000", holderId = "h1")
            }
            model.directBooking.confirm(null)
            advanceUntilIdle()

            assertNull("the sheet still closes — it was accepted", model.state.value.direct)
            assertTrue("but the screen has to say it was only filed", model.state.value.filed)

            model.directBooking.acknowledgeFiled()
            assertFalse(model.state.value.filed)
        }

    /** An ordinary booking raises no such notice; the balance speaks for itself. */
    @Test
    fun `a booked withdrawal raises no notice`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            source.directAnswer = ApiResult.Success(BankDirectOutcome.BOOKED)
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(kind = DirectBookingKind.WITHDRAWAL, amount = "5000", holderId = "h1")
            }
            model.directBooking.confirm(null)
            advanceUntilIdle()

            assertFalse(model.state.value.filed)
        }

    @Test
    fun `a withdrawal says what actually leaves the account`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            advanceUntilIdle()
            model.directBooking.edit {
                it.copy(kind = DirectBookingKind.WITHDRAWAL, amount = "100000", holderId = "h1")
            }

            // The default is on-top (ADR-0052): the typed figure is what the recipient RECEIVES
            // and the account is debited the gross. The app used to show none of this and to
            // preview the balance as if the fee did not exist.
            val open = requireNotNull(model.state.value.direct)
            assertEquals(BigDecimal("5000"), open.fee)
            assertEquals(BigDecimal("105000"), open.debited)
            assertEquals(BigDecimal("100000"), open.arrives)
            assertEquals(BigDecimal("895000"), open.preview(BigDecimal("1000000")))
        }

    @Test
    fun `fee-inclusive flips which side of the fee the typed figure is on`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            advanceUntilIdle()
            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.WITHDRAWAL,
                    amount = "100000",
                    holderId = "h1",
                    feeInclusive = true,
                )
            }

            val open = requireNotNull(model.state.value.direct)
            assertEquals(BigDecimal("100000"), open.debited)
            assertEquals(BigDecimal("95000"), open.arrives)
        }

    @Test
    fun `the coverage check runs against the gross, not the typed figure`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            advanceUntilIdle()
            model.directBooking.edit {
                it.copy(kind = DirectBookingKind.WITHDRAWAL, amount = "100000", holderId = "h1")
            }

            // 100 000 typed is within a 102 000 balance, but 105 000 leaves the account and the
            // server's overdraft guard runs against that. Checking the typed figure would invite
            // a booking the server refuses.
            assertEquals(false, model.state.value.direct?.submittable(BigDecimal("102000")))
            assertEquals(true, model.state.value.direct?.submittable(BigDecimal("110000")))
        }

    @Test
    fun `a deposit carries no fee, and a same-holder transfer none either`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            advanceUntilIdle()

            model.directBooking.edit { it.copy(amount = "100000", holderId = "h1") }
            assertNull("a deposit is fee-free", model.state.value.direct?.fee)

            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.TRANSFER,
                    destinationAccountId = "acc-2",
                    destinationHolderId = "h1",
                )
            }
            assertNull("a same-holder transfer is fee-free", model.state.value.direct?.fee)

            model.directBooking.edit { it.copy(destinationHolderId = "h2") }
            assertEquals(BigDecimal("5000"), model.state.value.direct?.fee)
        }

    @Test
    fun `the fee flag travels only where a fee applies`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            advanceUntilIdle()
            model.directBooking.edit {
                it.copy(amount = "5000", holderId = "h1", feeInclusive = true)
            }
            model.directBooking.confirm(null)
            advanceUntilIdle()

            // The draft may carry the flag; what matters is that a deposit takes no fee, so the
            // repository leaves it off the wire (asserted in BankRepositoryTest).
            assertEquals(DirectBookingKind.DEPOSIT, source.directBookings.single().kind)
            assertEquals(false, source.directBookings.single().feeApplies)
        }

    @Test
    fun `a withdrawal over the balance cannot be sent`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.WITHDRAWAL,
                    amount = "5000",
                    holderId = "h1",
                )
            }
            // Validation, not a lock: the figure is simply larger than the account holds.
            assertFalse(model.state.value.direct!!.submittable(BigDecimal("100")))
            model.directBooking.confirm(BigDecimal("100"))
            advanceUntilIdle()
            assertTrue(source.directBookings.isEmpty())

            assertTrue(model.state.value.direct!!.submittable(BigDecimal("9000")))
        }

    @Test
    fun `without a holder nothing is sent, in any mode`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.directBooking.open("acc-1")
            model.directBooking.edit { it.copy(amount = "5000") }

            // The server requires it too: custody is kept per org unit, so a balance without a
            // holder is money nobody is accountable for.
            assertFalse(model.state.value.direct!!.submittable(null))
            model.directBooking.confirm(null)
            advanceUntilIdle()
            assertTrue(source.directBookings.isEmpty())
        }

    @Test
    fun `a transfer needs both halves of its target`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.TRANSFER,
                    amount = "5000",
                    holderId = "h1",
                    destinationAccountId = "acc-2",
                )
            }
            assertFalse(model.state.value.direct!!.submittable(null))

            model.directBooking.edit { it.copy(destinationHolderId = "h2") }
            assertTrue(model.state.value.direct!!.submittable(null))
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the per-account counter is aggregated from the queue`() =
        runTest(dispatcher) {
            val source =
                RecordingStaff(
                    dashboard =
                        ApiResult.Success(
                            BankStaffDashboard(
                                management = true,
                                accounts = listOf(account("a1"), account("a2")),
                                totals = BankStaffTotals("1000", 2, 0),
                            ),
                        ),
                    pages =
                        listOf(
                            ApiResult.Success(
                                BankRequestPage(
                                    requests =
                                        listOf(bankRequest("a1"), bankRequest("a1"), bankRequest("a2")),
                                    page = 0,
                                    totalPages = 1,
                                    totalElements = THREE_REQUESTS.toLong(),
                                ),
                            ),
                        ),
                )
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            val rows = viewModel.state.value.rows.associateBy { it.account.id }
            assertEquals(2, rows.getValue("a1").openRequests)
            assertEquals(1, rows.getValue("a2").openRequests)
            assertEquals(THREE_REQUESTS, viewModel.state.value.openRequestTotal)
            assertFalse(viewModel.state.value.countsPartial)
        }

    @Test
    fun `the counter walks every page the queue reports`() =
        runTest(dispatcher) {
            val source =
                RecordingStaff(
                    dashboard = ApiResult.Success(dashboardOf(account("a1"))),
                    pages =
                        listOf(
                            ApiResult.Success(
                                BankRequestPage(
                                    listOf(bankRequest("a1")),
                                    page = 0,
                                    totalPages = TWO_PAGES,
                                    totalElements = TWO_PAGES.toLong(),
                                ),
                            ),
                            ApiResult.Success(
                                BankRequestPage(
                                    listOf(bankRequest("a1")),
                                    page = 1,
                                    totalPages = TWO_PAGES,
                                    totalElements = TWO_PAGES.toLong(),
                                ),
                            ),
                        ),
                )
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.rows.single().openRequests)
            assertEquals(TWO_PAGES, source.queueCalls)
            assertFalse(viewModel.state.value.countsPartial)
        }

    @Test
    fun `a queue read that fails leaves the counts marked partial, not wrong`() =
        runTest(dispatcher) {
            val source =
                RecordingStaff(
                    dashboard = ApiResult.Success(dashboardOf(account("a1"))),
                    pages = listOf(ApiResult.Failure(ApiError.Server(status = HTTP_ERROR))),
                )
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            // The dashboard still rendered — a decoration that could not be read must not take the
            // screen down with it.
            assertTrue(viewModel.state.value.phase is BankPhase.Ready)
            assertTrue(viewModel.state.value.countsPartial)
        }

    @Test
    fun `an account the caller sees only through their office is marked`() =
        runTest(dispatcher) {
            val source =
                RecordingStaff(
                    dashboard = ApiResult.Success(dashboardOf(account("a1"), account("a2"))),
                )
            val viewModel = staffModel(source, memberVisible = listOf(summary("a1")))
            viewModel.loadOnce()
            advanceUntilIdle()

            val rows = viewModel.state.value.rows.associateBy { it.account.id }
            assertTrue(rows.getValue("a1").viewable)
            assertFalse(rows.getValue("a2").viewable)
        }

    @Test
    fun `when the member list cannot be read, nothing is marked`() =
        runTest(dispatcher) {
            val source = RecordingStaff(dashboard = ApiResult.Success(dashboardOf(account("a1"))))
            val viewModel =
                BankStaffViewModel(
                    source = source,
                    memberAccounts = { ApiResult.Failure(ApiError.Server(status = HTTP_ERROR)) },
                )
            viewModel.loadOnce()
            advanceUntilIdle()

            // Marking every row as reached-by-office would be a louder claim than the app can
            // support from a failed read.
            assertTrue(viewModel.state.value.rows.single().viewable)
        }

    @Test
    fun `a caller without the role gets a refusal, not a crash`() =
        runTest(dispatcher) {
            val source = RecordingStaff(dashboard = ApiResult.Failure(ApiError.Forbidden()))
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            val phase = viewModel.state.value.phase
            assertTrue(phase is BankPhase.Failed)
            assertTrue((phase as BankPhase.Failed).error is ApiError.Forbidden)
        }

    @Test
    fun `management comes from the server, not from anything the app worked out`() =
        runTest(dispatcher) {
            val source =
                RecordingStaff(
                    dashboard =
                        ApiResult.Success(
                            BankStaffDashboard(true, listOf(account("a1")), BankStaffTotals("1", 1, 0)),
                        ),
                )
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.management)
        }

    @Test
    fun `confirming needs a holder, which is why it cannot be a button`() =
        runTest(dispatcher) {
            val request = bankRequest("a1")
            val state = BankConfirmState(request)

            // Artboard 5 draws a bare CTA. ConfirmBankBookingRequest.holderId is @NotNull, so a
            // bare CTA would post a body the server rejects.
            assertFalse(state.submittable)
            assertTrue(state.copy(holderId = "h1").submittable)
        }

    @Test
    fun `an over-limit request additionally needs the attestation`() =
        runTest(dispatcher) {
            val flagged = bankRequest("a1").copy(requiresOwnerApproval = true)
            val state = BankConfirmState(flagged, holderId = "h1")

            // Without it the server answers BANK_OWNER_APPROVAL_REQUIRED (REQ-BANK-041).
            assertFalse(state.submittable)
            assertTrue(state.copy(approvalAttested = true).submittable)
        }

    @Test
    fun `a transfer needs the receiving holder too`() =
        runTest(dispatcher) {
            val transfer = bankRequest("a1").copy(kind = BankRequestKind.TRANSFER)
            val state = BankConfirmState(transfer, holderId = "h1")

            assertFalse(state.submittable)
            assertTrue(state.copy(destinationHolderId = "h2").submittable)
        }

    @Test
    fun `a confirmation sends what the employee recorded, with the version it read`() =
        runTest(dispatcher) {
            val source =
                RecordingStaff(
                    dashboard = ApiResult.Success(dashboardOf(account("a1"))),
                    pages =
                        listOf(
                            ApiResult.Success(
                                BankRequestPage(
                                    listOf(bankRequest("a1")),
                                    page = 0,
                                    totalPages = 1,
                                    totalElements = 1,
                                ),
                            ),
                        ),
                )
            source.holderAnswer = ApiResult.Success(listOf(holder("h1"), holder("h2")))
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onConfirmOpen(viewModel.state.value.queue.single())
            viewModel.onConfirmChanged { it.copy(holderId = "h1", staffNote = "bar auf Port Olisar") }
            viewModel.onConfirmSubmit()
            advanceUntilIdle()

            val sent = source.confirmations.single()
            assertEquals("h1", sent.holderId)
            assertEquals("bar auf Port Olisar", sent.staffNote)
            assertEquals(1L, sent.version)
            assertNull(viewModel.state.value.confirming)
        }

    @Test
    fun `a refusal without a reason is not sent at all`() =
        runTest(dispatcher) {
            val source = RecordingStaff(dashboard = ApiResult.Success(dashboardOf(account("a1"))))
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onRejectOpen(bankRequest("a1"))
            viewModel.onRejectSubmit()
            advanceUntilIdle()

            // The server requires one and the requester is shown it; an empty reason would be a
            // rejection nobody can act on.
            assertTrue(source.rejections.isEmpty())
        }

    @Test
    fun `a refusal sends its reason trimmed, with the version it read`() =
        runTest(dispatcher) {
            val source = RecordingStaff(dashboard = ApiResult.Success(dashboardOf(account("a1"))))
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onRejectOpen(bankRequest("a1"))
            viewModel.onRejectReason("  Beleg fehlt  ")
            viewModel.onRejectSubmit()
            advanceUntilIdle()

            assertEquals(listOf(Triple("r-a1-${"a1".hashCode()}", "Beleg fehlt", 1L)), source.rejections)
            assertNull(viewModel.state.value.rejecting)
        }

    @Test
    fun `only active holders are offered`() =
        runTest(dispatcher) {
            val source = RecordingStaff(dashboard = ApiResult.Success(dashboardOf(account("a1"))))
            source.holderAnswer =
                ApiResult.Success(listOf(holder("h1"), holder("h2", active = false)))
            val viewModel = staffModel(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            // An inactive holder is kept for the ledger's sake, not for a new booking.
            assertEquals(listOf("h1"), viewModel.state.value.holders.map { it.id })
        }

    private companion object {
        const val HTTP_ERROR = 500
        const val TWO_PAGES = 2
        const val THREE_REQUESTS = 3

        /**
         * A staff account row.
         *
         * @param id the account.
         * @param status whether it is open.
         * @return the row.
         */
        fun account(
            id: String,
            status: BankAccountStatus = BankAccountStatus.ACTIVE,
        ) = BankStaffAccount(
            id = id,
            accountNo = "K-$id",
            name = "Einsatzkasse $id",
            type = "ORG_UNIT",
            status = status,
            balance = "84200.0000",
            delta30d = "1200.0000",
            sparkline = emptyList(),
        )

        /**
         * A dashboard holding exactly these accounts.
         *
         * @param accounts the rows.
         * @return the dashboard.
         */
        fun dashboardOf(vararg accounts: BankStaffAccount) =
            BankStaffDashboard(
                management = false,
                accounts = accounts.toList(),
                totals = BankStaffTotals("84200", accounts.size.toLong(), 0),
            )

        /**
         * The member-visible summary of one account.
         *
         * @param id the account.
         * @return the summary.
         */
        fun summary(id: String) =
            BankAccountSummary(
                id = id,
                accountNo = "K-$id",
                name = "Einsatzkasse $id",
                orgUnitName = "Bereich Profit",
                balance = "84200",
                delta30d = null,
                sparkline = emptyList(),
            )

        /**
         * One holder.
         *
         * @param id the holder.
         * @param active whether they still hold.
         * @return the holder.
         */
        fun holder(
            id: String,
            active: Boolean = true,
        ) = BankHolder(id = id, handle = "Halter $id", active = active, totalHeld = "1000")

        /** An empty last page. */
        fun emptyRequestPage() = BankRequestPage(emptyList(), page = 0, totalPages = 1, totalElements = 0)
    }
}

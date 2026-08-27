/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestPage
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.BankStaffDashboard
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    /**
     * Answers the two staff reads.
     *
     * @property dashboard what [staffDashboard] returns.
     * @property pages the queue, one entry per page; the walk stops when a page says it is last.
     */
    private class RecordingStaff(
        var dashboard: ApiResult<BankStaffDashboard> =
            ApiResult.Success(BankStaffDashboard(false, emptyList(), BankStaffTotals(null, 0, 0))),
        var pages: List<ApiResult<BankRequestPage>> = listOf(ApiResult.Success(emptyPage())),
    ) : BankStaffSource {
        var queueCalls = 0

        override suspend fun staffDashboard(): ApiResult<BankStaffDashboard> = dashboard

        override suspend fun requestQueue(
            statuses: Set<BankRequestStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<BankRequestPage> {
            queueCalls++
            return pages.getOrElse(page) { ApiResult.Success(emptyPage()) }
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
        source: BankStaffSource,
        memberVisible: List<BankAccountSummary> = emptyList(),
    ) = BankStaffViewModel(source = source, memberAccounts = { ApiResult.Success(memberVisible) })

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
                                        listOf(request("a1"), request("a1"), request("a2")),
                                    page = 0,
                                    totalPages = 1,
                                    totalElements = THREE_REQUESTS.toLong(),
                                ),
                            ),
                        ),
                )
            val viewModel = model(source)
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
                                    listOf(request("a1")),
                                    page = 0,
                                    totalPages = TWO_PAGES,
                                    totalElements = TWO_PAGES.toLong(),
                                ),
                            ),
                            ApiResult.Success(
                                BankRequestPage(
                                    listOf(request("a1")),
                                    page = 1,
                                    totalPages = TWO_PAGES,
                                    totalElements = TWO_PAGES.toLong(),
                                ),
                            ),
                        ),
                )
            val viewModel = model(source)
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
            val viewModel = model(source)
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
            val viewModel = model(source, memberVisible = listOf(summary("a1")))
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
            val viewModel = model(source)
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
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.management)
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
         * A pending request against one account.
         *
         * @param accountId which account.
         * @return the request.
         */
        fun request(accountId: String) =
            BankBookingRequest(
                id = "r-$accountId-${accountId.hashCode()}",
                accountId = accountId,
                accountName = "Einsatzkasse",
                targetAccountId = null,
                kind = BankRequestKind.WITHDRAWAL,
                amount = "1000",
                note = null,
                status = BankRequestStatus.PENDING,
                requester = "Rhea",
                rejectReason = null,
                applicableLimit = null,
                requiresOwnerApproval = false,
                ownerApprovalGranted = false,
                ownerApprovalBy = null,
                requiredApprover = null,
                createdAt = "2026-08-01T00:00:00Z",
                version = 1,
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

        /** An empty last page. */
        fun emptyPage() = BankRequestPage(emptyList(), page = 0, totalPages = 1, totalElements = 0)
    }
}

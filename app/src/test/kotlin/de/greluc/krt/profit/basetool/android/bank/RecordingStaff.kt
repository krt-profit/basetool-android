/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankConfirmation
import de.greluc.krt.profit.basetool.android.core.data.BankDirectOutcome
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestPage
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffDashboard
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
import de.greluc.krt.profit.basetool.android.core.data.DirectBooking
import de.greluc.krt.profit.basetool.android.core.network.ApiResult

/**
 * Answers the two staff reads.
 *
 * At file scope rather than nested in one test class: `BankStaffViewModelTest` and
 * `BankDirectBookingFieldsTest` drive the same view model from two directions — the queue and the
 * booking form — and a second copy of this would be a second place for the recorded calls to drift
 * from what the seam actually offers.
 *
 * @property dashboard what [staffDashboard] returns.
 * @property pages the queue, one entry per page; the walk stops when a page says it is last.
 */
internal class RecordingStaff(
    var dashboard: ApiResult<BankStaffDashboard> =
        ApiResult.Success(BankStaffDashboard(false, emptyList(), BankStaffTotals(null, 0, 0))),
    var pages: List<ApiResult<BankRequestPage>> = listOf(ApiResult.Success(emptyRequestPage())),
) : BankStaffSource {
    var queueCalls = 0

    override suspend fun transferFeeRate(): ApiResult<KrtDecimal> =

        ApiResult.Success(KrtDecimal(java.math.BigDecimal("0.05")))

    override suspend fun staffDashboard(): ApiResult<BankStaffDashboard> = dashboard

    override suspend fun requestQueue(
        statuses: Set<BankRequestStatus>,
        page: Int,
        pageSize: Int,
    ): ApiResult<BankRequestPage> {
        queueCalls++
        return pages.getOrElse(page) { ApiResult.Success(emptyRequestPage()) }
    }

    var holderAnswer: ApiResult<List<BankHolder>> = ApiResult.Success(emptyList())
    val confirmations = mutableListOf<BankConfirmation>()
    val rejections = mutableListOf<Triple<String, String, Long>>()
    var decisionAnswer: ApiResult<BankBookingRequest>? = null

    override suspend fun holders(): ApiResult<List<BankHolder>> = holderAnswer

    override suspend fun confirmRequest(
        confirmation: BankConfirmation,
    ): ApiResult<BankBookingRequest> {
        confirmations.add(confirmation)
        return decisionAnswer ?: ApiResult.Success(bankRequest("a1"))
    }

    val directBookings = mutableListOf<DirectBooking>()
    var directAnswer: ApiResult<BankDirectOutcome> =
        ApiResult.Success(BankDirectOutcome.BOOKED)

    override suspend fun bookDirectly(booking: DirectBooking): ApiResult<BankDirectOutcome> {
        directBookings.add(booking)
        return directAnswer
    }

    override suspend fun rejectRequest(
        id: String,
        reason: String,
        version: Long,
    ): ApiResult<BankBookingRequest> {
        rejections.add(Triple(id, reason, version))
        return decisionAnswer ?: ApiResult.Success(bankRequest("a1"))
    }
}

/**
 * A staff view model on the double.
 *
 * @param source the seam.
 * @param memberVisible the accounts the caller can see WITHOUT their office, which is what makes
 *   „reached only through the office" answerable.
 * @return the view model.
 */
internal fun staffModel(
    source: BankStaffSource,
    memberVisible: List<BankAccountSummary> = emptyList(),
) = BankStaffViewModel(source = source, memberAccounts = { ApiResult.Success(memberVisible) })

/**
 * One pending request against an account.
 *
 * Shared with the double because the double answers a confirmation with one, and a fixture that
 * lived in only one of the two test classes would make the other one's answers a different shape.
 *
 * @param accountId which account it stands against.
 * @return the request.
 */
internal fun bankRequest(accountId: String) =
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

/** An empty page of the request queue. */
internal fun emptyRequestPage() =
    BankRequestPage(emptyList(), page = 0, totalPages = 1, totalElements = 0)

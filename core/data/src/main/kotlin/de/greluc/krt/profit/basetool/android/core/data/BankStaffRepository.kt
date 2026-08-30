/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountDetailDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountLifecycleRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountRefDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankBookingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankBookingRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankDashboardAccountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankDashboardDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankDepositRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BankGrantDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankHolderBookingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankHolderDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankHolderTransferRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BankTransactionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankTransferRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BankWithdrawalRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.CancelBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.ConfirmBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateBankAccountRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateBankGrantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBalanceTargetRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBankAccountDetailDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBankAccountSettingsDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBankBalanceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseBankAccountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseBankBookingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseBankBookingRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseBankHolderBookingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseUserDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RegisterBankHolderRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.RejectBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.RenameBankAccountRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.ReverseBankTransactionRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankGrantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankHolderRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UserDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.DownloadedFile
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.time.Instant

/**
 * Reads the bank's staff surface from the backend.
 *
 * **The paths are what separate this from [BankRepository].** Everything here lives under
 * `/api/v1/bank/…`, which lists every account in the organisation and is gated on a bank role;
 * the member surface under `/api/v1/org-units/bank/…` answers only with what the caller may see
 * and stays there. The two were one class until it carried five interfaces at once.
 *
 * Reads are the employee's and writes are Bank-Management's, but **this class enforces neither** —
 * the server does, and the screens draw what it answers (ADR-0016). Nothing here derives a
 * permission from a role name.
 *
 * `/api/v1/bank/admin` is reached by neither class and never will be: that is the admin area,
 * which is web-only by owner decision.
 *
 * @property reader performs the calls and classifies their failures
 */
class BankStaffRepository(
    private val reader: ApiReader,
) : BankStaffSource,
    BankLifecycleSource,
    BankGrantSource,
    BankHolderSource,
    BankReversalSource,
    BankStaffAccountSource,
    BankReportSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    override suspend fun staffDashboard(): ApiResult<BankStaffDashboard> =
        when (val result = reader.get(STAFF_DASHBOARD_PATH, BankDashboardDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun requestQueue(
        statuses: Set<BankRequestStatus>,
        page: Int,
        pageSize: Int,
    ): ApiResult<BankRequestPage> {
        val query =
            buildList {
                statuses.forEach { add(STATUS_PARAM to it.name) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
            }
        return when (
            val result =
                reader.get(
                    STAFF_REQUESTS_PATH,
                    query,
                    PageResponseBankBookingRequestDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    BankRequestPage(
                        requests = result.value.content.orEmpty().mapNotNull { it.toModel() },
                        page = result.value.page ?: page,
                        totalPages = result.value.totalPages ?: 0,
                        totalElements = result.value.totalElements ?: 0,
                    ),
                )
            }
        }
    }

    override suspend fun holders(): ApiResult<List<BankHolder>> =
        when (
            val result =
                reader.get(STAFF_HOLDERS_PATH, ListSerializer(BankHolderDto.serializer()))
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.mapNotNull { it.toModel() })
            }
        }

    override suspend fun confirmRequest(
        confirmation: BankConfirmation,
    ): ApiResult<BankBookingRequest> =
        single(
            reader.post(
                path = "$STAFF_REQUESTS_PATH/${confirmation.requestId}/confirm",
                body =
                    ConfirmBankBookingRequest(
                        holderId = confirmation.holderId,
                        version = confirmation.version,
                        destinationHolderId = confirmation.destinationHolderId,
                        ownerApprovalConfirmed = confirmation.ownerApprovalConfirmed,
                        staffNote = confirmation.staffNote?.takeIf { it.isNotBlank() },
                    ),
                bodySerializer = ConfirmBankBookingRequest.serializer(),
                deserializer = BankBookingRequestDto.serializer(),
            ),
        )

    override suspend fun bookDirectly(booking: DirectBooking): ApiResult<Unit> {
        val figure = parseTypedDecimal(booking.amount)
        val target = booking.destinationAccountId
        val targetHolder = booking.destinationHolderId
        // A transfer needs both halves of its target; refusing here keeps the message at the field
        // rather than fetching a 400 to say the same thing.
        val transferReady =
            booking.kind != DirectBookingKind.TRANSFER || (target != null && targetHolder != null)
        if (figure == null || !transferReady) {
            return ApiResult.Failure(ApiError.Validation())
        }
        val amount = KrtDecimal(figure)
        val note = booking.note?.takeIf { it.isNotBlank() }
        return when (booking.kind) {
            DirectBookingKind.DEPOSIT -> {
                reader.postAccepted(
                    DEPOSITS_PATH,
                    BankDepositRequest(
                        accountId = booking.accountId,
                        amount = amount,
                        holderId = booking.holderId,
                        note = note,
                    ),
                    BankDepositRequest.serializer(),
                )
            }

            DirectBookingKind.WITHDRAWAL -> {
                reader.postAccepted(
                    WITHDRAWALS_PATH,
                    BankWithdrawalRequest(
                        accountId = booking.accountId,
                        amount = amount,
                        holderId = booking.holderId,
                        note = note,
                    ),
                    BankWithdrawalRequest.serializer(),
                )
            }

            DirectBookingKind.TRANSFER -> {
                reader.postAccepted(
                    TRANSFERS_PATH,
                    BankTransferRequest(
                        sourceAccountId = booking.accountId,
                        sourceHolderId = booking.holderId,
                        destinationAccountId = target.orEmpty(),
                        destinationHolderId = targetHolder.orEmpty(),
                        amount = amount,
                        note = note,
                    ),
                    BankTransferRequest.serializer(),
                )
            }
        }
    }

    override suspend fun rejectRequest(
        id: String,
        reason: String,
        version: Long,
    ): ApiResult<BankBookingRequest> =
        single(
            reader.post(
                path = "$STAFF_REQUESTS_PATH/$id/reject",
                body = RejectBankBookingRequest(reason = reason, version = version),
                bodySerializer = RejectBankBookingRequest.serializer(),
                deserializer = BankBookingRequestDto.serializer(),
            ),
        )

    override suspend fun managedAccounts(
        page: Int,
        pageSize: Int,
    ): ApiResult<List<BankManagedAccount>> =
        when (
            val result =
                reader.get(
                    STAFF_ACCOUNTS_PATH,
                    listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString()),
                    PageResponseBankAccountDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.content.orEmpty().mapNotNull { it.toModel() })
            }
        }

    override suspend fun createAccount(
        name: String,
        orgUnitId: String,
    ): ApiResult<BankManagedAccount> =
        account(
            reader.post(
                path = STAFF_ACCOUNTS_PATH,
                body =
                    CreateBankAccountRequest(
                        name = name,
                        // The app opens accounts for an org unit and nothing else. AREA, CARTEL,
                        // CARTEL_BANK and SPECIAL exist on the wire and are the web's to create.
                        type = CreateBankAccountRequest.Type.ORG_UNIT,
                        orgUnitId = orgUnitId,
                    ),
                bodySerializer = CreateBankAccountRequest.serializer(),
                deserializer = BankAccountDto.serializer(),
            ),
        )

    override suspend fun renameAccount(
        id: String,
        name: String,
        version: Long,
    ): ApiResult<BankManagedAccount> =
        account(
            reader.send(
                path = "$STAFF_ACCOUNTS_PATH/$id",
                method = "PATCH",
                body = RenameBankAccountRequest(name = name, version = version),
                bodySerializer = RenameBankAccountRequest.serializer(),
                deserializer = BankAccountDto.serializer(),
            ),
        )

    override suspend fun setAccountOpen(
        id: String,
        open: Boolean,
        version: Long,
    ): ApiResult<BankManagedAccount> =
        account(
            reader.post(
                path = "$STAFF_ACCOUNTS_PATH/$id/" + if (open) "reopen" else "close",
                body = BankAccountLifecycleRequest(version = version),
                bodySerializer = BankAccountLifecycleRequest.serializer(),
                deserializer = BankAccountDto.serializer(),
            ),
        )

    override suspend fun registerHolder(userId: String): ApiResult<BankHolder> =
        holder(
            reader.post(
                path = STAFF_HOLDERS_PATH,
                body = RegisterBankHolderRequest(userId = userId),
                bodySerializer = RegisterBankHolderRequest.serializer(),
                deserializer = BankHolderDto.serializer(),
            ),
        )

    override suspend fun setHolderActive(
        id: String,
        active: Boolean,
        version: Long,
    ): ApiResult<BankHolder> =
        holder(
            reader.send(
                path = "$STAFF_HOLDERS_PATH/$id",
                method = "PATCH",
                body = UpdateBankHolderRequest(active = active, version = version),
                bodySerializer = UpdateBankHolderRequest.serializer(),
                deserializer = BankHolderDto.serializer(),
            ),
        )

    /**
     * Unwraps a single account answer.
     *
     * @param result what the call returned.
     * @return the account, or the classified failure.
     */
    private fun account(result: ApiResult<BankAccountDto>): ApiResult<BankManagedAccount> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK))
            }
        }

    /**
     * Unwraps a single holder answer.
     *
     * @param result what the call returned.
     * @return the holder, or the classified failure.
     */
    private fun holder(result: ApiResult<BankHolderDto>): ApiResult<BankHolder> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK))
            }
        }

    override suspend fun grants(accountId: String): ApiResult<List<BankGrant>> =
        when (
            val result =
                reader.get(
                    STAFF_GRANTS_PATH,
                    listOf(ACCOUNT_PARAM to accountId),
                    ListSerializer(BankGrantDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.mapNotNull { it.toModel() })
        }

    override suspend fun setGrant(grant: BankGrant): ApiResult<BankGrant> {
        val result =
            if (!grant.exists) {
                reader.post(
                    path = STAFF_GRANTS_PATH,
                    body =
                        CreateBankGrantRequest(
                            userId = grant.userId,
                            accountId = grant.accountId,
                            canDeposit = grant.canDeposit,
                            canWithdraw = grant.canWithdraw,
                            canTransfer = grant.canTransfer,
                        ),
                    bodySerializer = CreateBankGrantRequest.serializer(),
                    deserializer = BankGrantDto.serializer(),
                )
            } else {
                reader.send(
                    path = "$STAFF_GRANTS_PATH/${grant.userId}/${grant.accountId}",
                    method = "PATCH",
                    body =
                        UpdateBankGrantRequest(
                            canDeposit = grant.canDeposit,
                            canWithdraw = grant.canWithdraw,
                            canTransfer = grant.canTransfer,
                            version = grant.version,
                        ),
                    bodySerializer = UpdateBankGrantRequest.serializer(),
                    deserializer = BankGrantDto.serializer(),
                )
            }
        return when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK))
            }
        }
    }

    override suspend fun staffAccount(id: String): ApiResult<BankAccountDetail> =
        when (
            val result =
                reader.get("$STAFF_ACCOUNTS_PATH/$id", BankAccountDetailDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toDetail())
        }

    override suspend fun staffBookings(
        id: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<BankBookingPage> =
        when (
            val result =
                reader.get(
                    "$STAFF_ACCOUNTS_PATH/$id/transactions",
                    listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString()),
                    PageResponseBankBookingDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }

    override suspend fun statement(
        accountId: String,
        from: String,
        to: String,
    ): ApiResult<DownloadedFile> =
        reader.getBytes(
            "$STAFF_ACCOUNTS_PATH/$accountId/statement",
            listOf(FROM_PARAM to from, TO_PARAM to to),
        )

    override suspend fun threeMonthReport(zoneId: String): ApiResult<DownloadedFile> =
        reader.getBytes(
            THREE_MONTH_PATH,
            // The endpoint declares the header, so it is sent here as well as by the client's own
            // interceptor: a report whose month boundaries fall in the wrong zone is wrong by a day
            // at each end and looks like a data problem.
            headers = listOf(USER_ZONE_HEADER to zoneId),
        )

    override suspend fun reverse(
        transactionId: String,
        note: String?,
    ): ApiResult<Unit> =
        when (
            val result =
                reader.post(
                    path = "$REVERSAL_PATH/$transactionId/reversal",
                    body = ReverseBankTransactionRequest(note = note?.takeIf { it.isNotBlank() }),
                    bodySerializer = ReverseBankTransactionRequest.serializer(),
                    deserializer = BankTransactionDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    override suspend fun holder(id: String): ApiResult<BankHolder> =
        holder(reader.get("$STAFF_HOLDERS_PATH/$id", BankHolderDto.serializer()))

    override suspend fun holderBookings(
        id: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<BankHolderBookingPage> =
        when (
            val result =
                reader.get(
                    "$STAFF_HOLDERS_PATH/$id/transactions",
                    listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString()),
                    PageResponseBankHolderBookingDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }

    override suspend fun transferCustody(
        sourceHolderId: String,
        destinationHolderId: String,
        amount: String,
        note: String?,
    ): ApiResult<Unit> =
        when (
            val result =
                reader.post(
                    path = "$STAFF_HOLDERS_PATH/transfer",
                    body =
                        BankHolderTransferRequest(
                            sourceHolderId = sourceHolderId,
                            destinationHolderId = destinationHolderId,
                            amount = KrtDecimal(parseTypedDecimal(amount) ?: BigDecimal.ZERO),
                            note = note?.takeIf { it.isNotBlank() },
                        ),
                    bodySerializer = BankHolderTransferRequest.serializer(),
                    deserializer = BankTransactionDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    override suspend fun searchGrantees(query: String): ApiResult<List<BankGrantee>> =
        when (
            val result =
                reader.get(
                    GRANTEE_SEARCH_PATH,
                    listOf(
                        QUERY_PARAM to query,
                        PAGE_PARAM to "0",
                        SIZE_PARAM to GRANTEE_PAGE_SIZE.toString(),
                    ),
                    PageResponseUserDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.content.orEmpty().mapNotNull { it.toGrantee() })
            }
        }

    override suspend fun revokeGrant(
        userId: String,
        accountId: String,
    ): ApiResult<Unit> = reader.delete("$STAFF_GRANTS_PATH/$userId/$accountId")

    /**
     * Unwraps a single request answer.
     *
     * @param result what the call returned.
     * @return the request, or the classified failure.
     */
    private fun single(result: ApiResult<BankBookingRequestDto>): ApiResult<BankBookingRequest> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK))
            }
        }

    companion object {
        /** The staff dashboard: every account of the unit plus the KPI band. */
        const val STAFF_DASHBOARD_PATH = "/api/v1/bank/dashboard"

        /** The staff request queue. */
        const val STAFF_REQUESTS_PATH = "/api/v1/bank/requests"

        /** The Verwaltung's three direct bookings (design ch. 12 artboard 9). */
        private const val DEPOSITS_PATH = "/api/v1/bank/deposits"

        /** Its counterpart. */
        private const val WITHDRAWALS_PATH = "/api/v1/bank/withdrawals"

        /** And the move between two of the unit's accounts. */
        private const val TRANSFERS_PATH = "/api/v1/bank/transfers"

        /** The per-account grants matrix. */
        const val STAFF_GRANTS_PATH = "/api/v1/bank/grants"

        /**
         * The grantee picker.
         *
         * The bank twin of `/users/search`, not that path: the two are identical but for the role
         * gate, and only this one admits a bank manager who holds no org role.
         */
        const val GRANTEE_SEARCH_PATH = "/api/v1/users/search-bank"

        /** What was typed into the grantee picker. */
        const val QUERY_PARAM = "query"

        /** How many candidates one search offers. */
        private const val GRANTEE_PAGE_SIZE = 25

        /** The stem a Storno addresses; the transaction's id and `/reversal` complete it. */
        const val REVERSAL_PATH = "/api/v1/bank/transactions"

        /** The quarter report every bank employee may pull. */
        const val THREE_MONTH_PATH = "/api/v1/bank/export/three-month-report"

        /** The start of a statement's period. */
        const val FROM_PARAM = "from"

        /** Its end. */
        const val TO_PARAM = "to"

        /** The zone the report's month boundaries are drawn in. */
        const val USER_ZONE_HEADER = "X-User-Time-Zone"

        /** Which account's matrix to read. */
        const val ACCOUNT_PARAM = "accountId"

        /** The staff account list, which carries the version every lifecycle write echoes. */
        const val STAFF_ACCOUNTS_PATH = "/api/v1/bank/accounts"

        /** The unit's holders, which a confirmation has to name one of. */
        const val STAFF_HOLDERS_PATH = "/api/v1/bank/holders"

        /** The queue's status filter; repeated once per state. */
        const val STATUS_PARAM = "status"

        /** Log subsystem. No amount, handle or note is ever logged. */
        private const val LOG_TAG = "bank"

        /** What a successful call that returned nothing usable is reported as. */
        private const val HTTP_OK = 200

        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
    }
}

/**
 * Maps the staff dashboard onto the model.
 *
 * @return the dashboard; a missing `totals` reads as a bank with nothing in it rather than a
 *   failure, because an organisation that has closed every account is a real state.
 */
private fun BankDashboardDto.toModel(): BankStaffDashboard =
    BankStaffDashboard(
        management = management == true,
        accounts = accounts.orEmpty().mapNotNull { it.toModel() },
        // Null stays null. The server omits the strip for a non-management caller, and folding
        // that into zeroes would have the screen assert an empty bank.
        totals =
            totals?.let {
                BankStaffTotals(
                    totalBalance = it.totalBalance?.toString(),
                    activeAccounts = it.activeAccounts ?: 0,
                    closedAccounts = it.closedAccounts ?: 0,
                )
            },
    )

/**
 * Maps one dashboard row onto the model.
 *
 * @return the account, or `null` without an id - one nothing could open.
 */
private fun BankDashboardAccountDto.toModel(): BankStaffAccount? {
    val accountId = id ?: return null
    return BankStaffAccount(
        id = accountId,
        accountNo = accountNo,
        name = name.orEmpty(),
        type = type?.value,
        // An unknown status reads as active: a row that takes bookings is the one a staff member
        // must not be talked out of acting on by a value this build predates.
        status =
            if (status == BankDashboardAccountDto.Status.CLOSED) {
                BankAccountStatus.CLOSED
            } else {
                BankAccountStatus.ACTIVE
            },
        balance = balance?.toString(),
        delta30d = delta30d?.toString(),
        sparkline = sparkline.orEmpty().mapNotNull { it.toString().toDoubleOrNull() },
    )
}

/**
 * Maps a staff account row onto the model.
 *
 * @return the account, or `null` without an id or a version — one no lifecycle write could
 *   address.
 */
private fun BankAccountDto.toModel(): BankManagedAccount? {
    // An id with no version is as unusable as no id: every lifecycle write echoes one, so a row
    // missing either could be listed and never acted on.
    val accountId = id ?: return null
    return version?.let { lock ->
        BankManagedAccount(
            id = accountId,
            accountNo = accountNo,
            name = name.orEmpty(),
            type = type?.value,
            status =
                if (status == BankAccountDto.Status.CLOSED) {
                    BankAccountStatus.CLOSED
                } else {
                    BankAccountStatus.ACTIVE
                },
            balance = balance?.toString(),
            orgUnitName = orgUnit?.name,
            version = lock,
        )
    }
}

/**
 * Maps a holder onto the model.
 *
 * @return the holder, or `null` without an id.
 */
private fun BankHolderDto.toModel(): BankHolder? {
    val holderId = id ?: return null
    return BankHolder(
        id = holderId,
        handle = handle.orEmpty(),
        active = active != false,
        totalHeld = totalHeld?.toString(),
        version = version ?: 0,
    )
}

/**
 * Maps a staff account row onto the detail the screen draws.
 *
 * The staff shape carries no ledger of its own — that arrives from `/transactions` — so the detail
 * is assembled from what this row does carry.
 *
 * @return the detail.
 */
private fun BankAccountDetailDto.toDetail(): BankAccountDetail =
    BankAccountDetail(
        id = account?.id.orEmpty(),
        accountNo = account?.accountNo,
        name = account?.name.orEmpty(),
        balance = account?.balance?.toString(),
        delta30d = delta30d?.toString(),
        bookingCount = bookingCount ?: 0,
    )

/**
 * Maps a page of holder postings onto the model.
 *
 * @param page which page was asked for; the server does not always echo it.
 * @return the page.
 */
private fun PageResponseBankHolderBookingDto.toModel(page: Int): BankHolderBookingPage =
    BankHolderBookingPage(
        rows = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalElements = totalElements ?: 0,
        totalPages = totalPages ?: 0,
    )

/**
 * Maps one holder posting onto the model.
 *
 * @return the posting, or `null` without an id — one nothing could address.
 */
private fun BankHolderBookingDto.toModel(): BankHolderBooking? =
    postingId?.let {
        BankHolderBooking(
            id = it,
            transactionId = transactionId,
            type = type?.name,
            amount = amount?.toString(),
            note = note,
            createdAt = createdAt,
            counterAccount = counterAccountName ?: counterAccountNo,
            counterHolder = counterHolderHandle,
            reversed = reversedTransactionId != null,
        )
    }

/**
 * Maps one search hit onto a grantee.
 *
 * @return the candidate, or `null` without an id — one no grant could name.
 */
private fun UserDto.toGrantee(): BankGrantee? =
    id?.let {
        BankGrantee(
            id = it,
            // Never the e-mail or the rank the search also carries.
            handle = effectiveName ?: displayName ?: username.orEmpty(),
        )
    }

/**
 * Maps one grant onto the model.
 *
 * @return the grant, or `null` without both ids — one no change could address.
 */
private fun BankGrantDto.toModel(): BankGrant? {
    val user = userId ?: return null
    return accountId?.let { account ->
        BankGrant(
            userId = user,
            handle = userHandle.orEmpty(),
            accountId = account,
            canDeposit = canDeposit == true,
            canWithdraw = canWithdraw == true,
            canTransfer = canTransfer == true,
            // Absent means zero here, which is what a freshly inserted row carries. That is exactly
            // why `exists` is a field of its own rather than a test on this number.
            version = version ?: 0,
            exists = true,
        )
    }
}

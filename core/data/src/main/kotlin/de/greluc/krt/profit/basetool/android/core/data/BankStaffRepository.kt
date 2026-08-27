/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountLifecycleRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountRefDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankBookingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankBookingRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankDashboardAccountDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankDashboardDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankGrantDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankHolderDto
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
import de.greluc.krt.profit.basetool.android.core.contract.model.RegisterBankHolderRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.RejectBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.RenameBankAccountRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankGrantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankHolderRequest
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
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
    BankGrantSource {
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

        /** The per-account grants matrix. */
        const val STAFF_GRANTS_PATH = "/api/v1/bank/grants"

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

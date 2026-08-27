/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BankAccountRefDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankBookingDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BankBookingRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CancelBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBalanceTargetRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBankAccountDetailDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBankAccountSettingsDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitBankBalanceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseBankBookingDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * One account on the Konten list.
 *
 * @property id the account's id
 * @property accountNo the account number the member quotes when asking about it
 * @property name the account's display name
 * @property orgUnitName which org unit owns it
 * @property balance the current balance as the server rendered it, unformatted
 * @property delta30d how it moved over thirty days, or `null` when the server sent none
 * @property sparkline the balance points the design draws as a polyline; empty when none came
 */
data class BankAccountSummary(
    val id: String,
    val accountNo: String?,
    val name: String,
    val orgUnitName: String?,
    val balance: String?,
    val delta30d: String?,
    val sparkline: List<Double>,
)

/**
 * One account in full.
 *
 * @property id the account's id
 * @property accountNo the account number
 * @property name the account's display name
 * @property balance the current balance, unformatted
 * @property delta30d the thirty-day move, or `null`
 * @property bookingCount how many bookings the ledger holds in total
 */
data class BankAccountDetail(
    val id: String,
    val accountNo: String?,
    val name: String,
    val balance: String?,
    val delta30d: String?,
    val bookingCount: Long,
    val canRequest: Boolean = false,
    val applicableLimit: String? = null,
    val approvalExempt: Boolean = false,
)

/**
 * What kind of money movement a request asks for.
 *
 * The three the member surface offers. A `TRANSFER` is the only one that names a second account.
 */
enum class BankRequestKind {
    /** Money in. */
    DEPOSIT,

    /** Money out. */
    WITHDRAWAL,

    /** Money to another account. */
    TRANSFER,
}

/**
 * Where a transfer may send money.
 *
 * @property id the account.
 * @property label how it reads in the picker.
 */
data class BankTransferTarget(
    val id: String,
    val label: String,
)

/**
 * A booking request as the member sees it.
 *
 * @property id the request.
 * @property accountName which account it moves.
 * @property kind what it asks for.
 * @property amount how much, unformatted and always positive.
 * @property note what it is for, or `null`.
 * @property status where it stands, as the server names it.
 * @property requester who raised it, by handle.
 * @property approvalsGiven how many approvals it already carries.
 * @property approvalsNeeded how many it needs — the staggered ladder, decided by the server.
 * @property ownApproverGrant whether this member may act on it at all. The approve and reject
 *   actions appear only for a holder of an approver grant, never for every reader of the account.
 * @property ownRequest whether this member raised it. Nobody approves their own.
 * @property createdAt when it was raised, in UTC.
 * @property version the optimistic-locking version. Every write against a request echoes it, so
 *   two approvers acting on the same request at the same moment collide with a 409 instead of one
 *   silently overwriting the other.
 */
data class BankBookingRequest(
    val id: String,
    val accountName: String?,
    val kind: BankRequestKind?,
    val amount: String?,
    val note: String?,
    val status: String?,
    val requester: String?,
    val approvalsGiven: Int,
    val approvalsNeeded: Int,
    val ownApproverGrant: Boolean,
    val ownRequest: Boolean,
    val createdAt: String?,
    val version: Long,
)

/**
 * What raising a request carries.
 *
 * @property accountId the account the money moves on.
 * @property kind what is being asked for.
 * @property amount how much, as typed.
 * @property targetAccountId where a transfer goes; `null` for the other two.
 * @property note the „Verwendungszweck", or `null`.
 */
data class BankRequestDraft(
    val accountId: String,
    val kind: BankRequestKind,
    val amount: String,
    val targetAccountId: String? = null,
    val note: String? = null,
)

/**
 * One line of the append-only ledger.
 *
 * @property id the posting id — the ledger is append-only, so a posting is never rewritten
 * @property type the booking kind as the server names it, e.g. `DEPOSIT`
 * @property amount the amount, unformatted and always positive; the sign follows from [type]
 * @property note what it was for, or `null`
 * @property holder whose holding it moved, or `null`
 * @property createdAt when it was posted, in UTC
 */
data class BankBooking(
    val id: String,
    val type: String,
    val amount: String?,
    val note: String?,
    val holder: String?,
    val createdAt: Instant?,
) {
    /**
     * Whether this line adds to the account.
     *
     * Derived from the **kind**, never from the digits: the ledger stores every amount as a
     * positive magnitude, so reading the sign off the number would show every withdrawal as a
     * deposit. A kind this build does not know is treated as neither — it renders without a sign
     * rather than guessing one.
     */
    val incoming: Boolean? get() =
        when (type) {
            "DEPOSIT" -> true
            "WITHDRAWAL" -> false
            else -> null
        }
}

/**
 * One page of the ledger.
 *
 * @property bookings the lines on this page, newest first
 * @property page the zero-based page index
 * @property totalPages how many pages exist
 * @property totalElements how many lines the ledger holds
 */
data class BankBookingPage(
    val bookings: List<BankBooking>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * What the holder of an account may change about it, and what it currently says.
 *
 * **The two `can*` flags come from the server, not from a role the app worked out.** Which member
 * is responsible for an account is a per-account fact, and the settings answer states it — so the
 * screen offers exactly the controls the server would accept and guesses at nothing.
 *
 * @property accountId which account
 * @property accountName how it reads
 * @property balanceTarget the target balance, or `null` when none is set
 * @property version the optimistic lock, echoed by the target write
 * @property canSetTarget whether the caller may change the target
 * @property canConfigureVisibility whether they may change who sees the account
 * @property visibilityConfigurable whether this account type supports it at all — a different fact
 *   from whether the caller may
 * @property allMembersSupported whether the all-members switch applies to this account
 * @property allMembersGranted whether it is on
 * @property availableRoleCodes the role buckets that can be granted, in server order
 * @property grantedRoleCodes the ones that are
 */
data class BankAccountSettings(
    val accountId: String,
    val accountName: String?,
    val balanceTarget: String?,
    val version: Long?,
    val canSetTarget: Boolean,
    val canConfigureVisibility: Boolean,
    val visibilityConfigurable: Boolean,
    val allMembersSupported: Boolean,
    val allMembersGranted: Boolean,
    val availableRoleCodes: List<String>,
    val grantedRoleCodes: List<String>,
)

/**
 * The org bank reads a member may make, as a seam.
 */
interface BankSource {
    /**
     * Reads the accounts the caller may see.
     *
     * @return the list, or a failure. An empty list is an ordinary answer: a member with no view
     *   grant sees only the accounts that are public to everyone.
     */
    suspend fun balances(): ApiResult<List<BankAccountSummary>>

    /**
     * Reads one account.
     *
     * @param id the account's id.
     * @return the account, or a failure. `Forbidden` is the ordinary answer for an account the
     *   caller has no grant for.
     */
    suspend fun account(id: String): ApiResult<BankAccountDetail>

    /**
     * Reads what the caller may change about one account.
     *
     * @param id the account.
     * @return the settings, or the classified failure.
     */
    suspend fun settings(id: String): ApiResult<BankAccountSettings>

    /**
     * Sets or clears the account's target balance.
     *
     * @param id the account.
     * @param target the new target, or `null` to clear it.
     * @param version the version echoed from the read.
     * @return the refreshed settings, or the classified failure.
     */
    suspend fun setBalanceTarget(
        id: String,
        target: String?,
        version: Long?,
    ): ApiResult<BankAccountSettings>

    /**
     * Grants or revokes one role bucket's view of the account.
     *
     * @param id the account.
     * @param roleCode the bucket.
     * @param granted whether it should end up granted.
     * @return the refreshed settings, or the classified failure.
     */
    suspend fun setRoleVisibility(
        id: String,
        roleCode: String,
        granted: Boolean,
    ): ApiResult<BankAccountSettings>

    /**
     * Opens the account to every member of its org unit, or closes it again.
     *
     * @param id the account.
     * @param granted whether every member should see it.
     * @return the refreshed settings, or the classified failure.
     */
    suspend fun setAllMembersVisibility(
        id: String,
        granted: Boolean,
    ): ApiResult<BankAccountSettings>

    /**
     * Reads one page of an account's ledger.
     *
     * @param id the account's id.
     * @param page the zero-based page index.
     * @param pageSize how many lines to ask for.
     * @return the page, or a failure.
     */
    suspend fun bookings(
        id: String,
        page: Int = 0,
        pageSize: Int = BankRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<BankBookingPage>
}

/**
 * The booking-request half of the member's bank.
 *
 * Its own seam rather than more methods on [BankSource]: reading an account and asking for money to
 * move are different jobs with different gates, and the request calls are the only ones that echo a
 * version. [BankRepository] serves both.
 */
interface BankRequestSource {
    /**
     * Reads the requests this member raised.
     *
     * @return the requests, newest first as the server orders them.
     */
    suspend fun ownRequests(): ApiResult<List<BankBookingRequest>>

    /**
     * Reads the requests waiting on **this** member's approval.
     *
     * A separate call rather than a filter on the list above: the server decides who may approve
     * what, and a client-side filter would have to reimplement the grant rules to get it right.
     *
     * @return the requests awaiting this member.
     */
    suspend fun foreignRequests(): ApiResult<List<BankBookingRequest>>

    /**
     * Where a transfer may send money.
     *
     * @return the accounts the server will accept as a target.
     */
    suspend fun transferTargets(): ApiResult<List<BankTransferTarget>>

    /**
     * Raises a booking request.
     *
     * @param draft what the member filled in.
     * @return the request as the server recorded it, or the classified failure.
     */
    suspend fun createRequest(draft: BankRequestDraft): ApiResult<BankBookingRequest>

    /**
     * Withdraws one's own request.
     *
     * @param id the request.
     * @param version the version it was read at; echoed so a concurrent change 409s.
     * @return the request in its new state.
     */
    suspend fun cancelRequest(
        id: String,
        version: Long,
    ): ApiResult<BankBookingRequest>

    /**
     * Grants or revokes this member's approval on someone else's request.
     *
     * @param id the request.
     * @param granted whether to grant.
     * @param version the version it was read at; echoed so a concurrent change 409s.
     * @return the request in its new state.
     */
    suspend fun setOwnerApproval(
        id: String,
        granted: Boolean,
        version: Long,
    ): ApiResult<BankBookingRequest>
}

/**
 * Reads the org bank from the backend.
 *
 * **The member-facing bank paths, never the bank-employee ones.** `/bank/accounts/…` lists every
 * account in the organisation and is gated on a bank role; `/org-units/bank/…` answers with the
 * accounts this caller may actually see — the ones public to everyone plus those they hold a view
 * grant for.
 *
 * @property reader performs the calls and classifies their failures
 */

class BankRepository(
    private val reader: ApiReader,
) : BankSource,
    BankRequestSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads the visible accounts.
     *
     * @return the list, or the classified failure.
     */
    override suspend fun balances(): ApiResult<List<BankAccountSummary>> =
        when (
            val result =
                reader.get(BALANCES_PATH, ListSerializer(OrgUnitBankBalanceDto.serializer()))
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.mapNotNull { it.toModel() })
        }

    /**
     * Reads one account.
     *
     * @param id the account's id.
     * @return the account, or the classified failure.
     */
    override suspend fun account(id: String): ApiResult<BankAccountDetail> =
        when (
            val result = reader.get(accountPath(id), OrgUnitBankAccountDetailDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(id))
        }

    /**
     * Reads one page of the ledger.
     *
     * @param id the account's id.
     * @param page the zero-based page index.
     * @param pageSize how many lines to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun settings(id: String): ApiResult<BankAccountSettings> =
        mapped(
            reader.get(
                "${accountPath(id)}/settings",
                OrgUnitBankAccountSettingsDto.serializer(),
            ),
        )

    override suspend fun setBalanceTarget(
        id: String,
        target: String?,
        version: Long?,
    ): ApiResult<BankAccountSettings> =
        mapped(
            reader.put(
                "${accountPath(id)}/balance-target",
                OrgUnitBalanceTargetRequest(
                    // No target IS the clear. Sending zero would set a target of nothing, which is
                    // a different instruction and one the screen never offers.
                    target = target?.toBigDecimalOrNull()?.let(::KrtDecimal),
                    version = version ?: 0L,
                ),
                OrgUnitBalanceTargetRequest.serializer(),
                OrgUnitBankAccountSettingsDto.serializer(),
            ),
        )

    override suspend fun setRoleVisibility(
        id: String,
        roleCode: String,
        granted: Boolean,
    ): ApiResult<BankAccountSettings> {
        val path = "${accountPath(id)}/visibility/role/$roleCode"
        return mapped(
            if (granted) {
                reader.post(path, OrgUnitBankAccountSettingsDto.serializer())
            } else {
                reader.delete(path, OrgUnitBankAccountSettingsDto.serializer())
            },
        )
    }

    override suspend fun setAllMembersVisibility(
        id: String,
        granted: Boolean,
    ): ApiResult<BankAccountSettings> =
        mapped(
            reader.put(
                "${accountPath(id)}/visibility/all-members/$granted",
                OrgUnitBankAccountSettingsDto.serializer(),
            ),
        )

    /**
     * Maps a settings answer onto the model.
     *
     * Every one of these calls answers with the whole settings snapshot, and the screen redraws
     * from it: the version moves with each write, and so does what the caller may do next.
     *
     * @param result what the call returned.
     * @return the settings, or the failure.
     */
    private fun mapped(
        result: ApiResult<OrgUnitBankAccountSettingsDto>,
    ): ApiResult<BankAccountSettings> =
        when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun ownRequests(): ApiResult<List<BankBookingRequest>> =
        requestList("$REQUESTS_PATH")

    override suspend fun foreignRequests(): ApiResult<List<BankBookingRequest>> =
        requestList("$REQUESTS_PATH/foreign")

    /**
     * Reads a list of requests from one of the two endpoints that serve them.
     *
     * @param path which list.
     * @return the requests, or the classified failure.
     */
    private suspend fun requestList(path: String): ApiResult<List<BankBookingRequest>> =
        when (
            val result = reader.get(path, ListSerializer(BankBookingRequestDto.serializer()))
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.mapNotNull { it.toModel() })
        }

    override suspend fun transferTargets(): ApiResult<List<BankTransferTarget>> =
        when (
            val result =
                reader.get(
                    "$ORG_UNIT_BANK/transfer-targets",
                    ListSerializer(BankAccountRefDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { ref ->
                        ref.id?.let {
                            BankTransferTarget(
                                id = it,
                                label = ref.name.orEmpty().ifBlank { ref.accountNo.orEmpty() },
                            )
                        }
                    },
                )
            }
        }

    override suspend fun createRequest(draft: BankRequestDraft): ApiResult<BankBookingRequest> =
        single(
            reader.post(
                path = REQUESTS_PATH,
                body =
                    CreateBankBookingRequest(
                        sourceAccountId = draft.accountId,
                        type = draft.kind.toWire(),
                        amount = KrtDecimal(draft.amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO),
                        // Only a transfer names a second account; the server ignores it otherwise,
                        // and sending it anyway would put a value on the wire describing nothing.
                        targetAccountId = draft.targetAccountId.takeIf { draft.kind == BankRequestKind.TRANSFER },
                        note = draft.note?.takeIf { it.isNotBlank() },
                    ),
                bodySerializer = CreateBankBookingRequest.serializer(),
                deserializer = BankBookingRequestDto.serializer(),
            ),
        )

    override suspend fun cancelRequest(
        id: String,
        version: Long,
    ): ApiResult<BankBookingRequest> =
        single(
            reader.post(
                path = "$REQUESTS_PATH/$id/cancel",
                body = CancelBankBookingRequest(version = version),
                bodySerializer = CancelBankBookingRequest.serializer(),
                deserializer = BankBookingRequestDto.serializer(),
            ),
        )

    override suspend fun setOwnerApproval(
        id: String,
        granted: Boolean,
        version: Long,
    ): ApiResult<BankBookingRequest> {
        val path = "$REQUESTS_PATH/$id/owner-approval"
        val result =
            if (granted) {
                reader.post(
                    path = path,
                    body = CancelBankBookingRequest(version = version),
                    bodySerializer = CancelBankBookingRequest.serializer(),
                    deserializer = BankBookingRequestDto.serializer(),
                )
            } else {
                reader.send(
                    path = path,
                    method = "DELETE",
                    body = CancelBankBookingRequest(version = version),
                    bodySerializer = CancelBankBookingRequest.serializer(),
                    deserializer = BankBookingRequestDto.serializer(),
                )
            }
        return single(result)
    }

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

    override suspend fun bookings(
        id: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<BankBookingPage> {
        val params = listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString())
        return when (
            val result =
                reader.get(bookingsPath(id), params, PageResponseBankBookingDto.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    companion object {
        /** Ledger lines per page. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /** Log subsystem. No amount, handle or note is ever logged. */
        private const val LOG_TAG = "bank"

        /** The member surface's prefix; the staff bank lives under `/api/v1/bank`. */
        private const val ORG_UNIT_BANK = "/api/v1/org-units/bank"

        private const val REQUESTS_PATH = "$ORG_UNIT_BANK/requests"

        /** What a successful call that returned nothing usable is reported as. */
        private const val HTTP_OK = 200

        private const val BALANCES_PATH = "/api/v1/org-units/bank/balances"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"

        /**
         * One account's path.
         *
         * @param id the account's id.
         * @return the path.
         */
        private fun accountPath(id: String) = "/api/v1/org-units/bank/accounts/$id"

        /**
         * One account's ledger path.
         *
         * @param id the account's id.
         * @return the path.
         */
        private fun bookingsPath(id: String) = "/api/v1/org-units/bank/accounts/$id/transactions"
    }
}

/**
 * Maps one balance row onto the model.
 *
 * @return the account, or `null` when it has no id and therefore cannot be opened.
 */
private fun OrgUnitBankBalanceDto.toModel(): BankAccountSummary? {
    val id = accountId ?: return null
    return BankAccountSummary(
        id = id,
        accountNo = accountNo,
        name = accountName.orEmpty(),
        orgUnitName = orgUnitName,
        balance = balance?.toString(),
        delta30d = delta30d?.toString(),
        // Kept as doubles on purpose: these are the polyline's coordinates, not money to state.
        // Nothing is ever printed from them, so the precision a decimal buys has no reader.
        sparkline = sparkline.orEmpty().map { it.value.toDouble() },
    )
}

/**
 * Maps the account detail onto the model.
 *
 * @param requestedId the id that was asked for, used because the payload nests its own.
 * @return the account.
 */
private fun OrgUnitBankAccountDetailDto.toModel(requestedId: String): BankAccountDetail =
    BankAccountDetail(
        id = detail?.account?.id ?: requestedId,
        accountNo = detail?.account?.accountNo,
        name = detail?.account?.name.orEmpty(),
        balance = detail?.account?.balance?.toString(),
        delta30d = detail?.delta30d?.toString(),
        bookingCount = detail?.bookingCount ?: 0L,
        canRequest = canRequest == true,
        // The threshold the request form explains live under the amount. It is per caller and per
        // account and comes from the server, which is why the form must not carry a constant.
        applicableLimit = applicableLimit?.toString(),
        approvalExempt = approvalExempt == true,
    )

/**
 * Maps a request onto the model.
 *
 * @return the request, or `null` without an id — one no action could address.
 */
private fun BankBookingRequestDto.toModel(): BankBookingRequest? {
    val requestId = id ?: return null
    return BankBookingRequest(
        id = requestId,
        accountName = accountName,
        kind =
            when (type) {
                BankBookingRequestDto.Type.DEPOSIT -> BankRequestKind.DEPOSIT
                BankBookingRequestDto.Type.WITHDRAWAL -> BankRequestKind.WITHDRAWAL
                BankBookingRequestDto.Type.TRANSFER -> BankRequestKind.TRANSFER
                else -> null
            },
        amount = amount?.toString(),
        note = note?.takeIf { it.isNotBlank() } ?: justification?.takeIf { it.isNotBlank() },
        status = status?.value,
        requester = requesterHandle,
        // The server reports the ladder per request rather than per account: the same account can
        // demand two approvals of one amount and three of another.
        approvalsGiven = if (ownerApprovalGranted == true) 1 else 0,
        approvalsNeeded = if (requiresOwnerApproval == true) 1 else 0,
        ownApproverGrant = requiredApprover != null,
        ownRequest = false,
        createdAt = createdAt,
        version = version ?: 0L,
    )
}

/**
 * Maps the app's request kind onto the wire enum.
 *
 * @return the wire value.
 */
private fun BankRequestKind.toWire(): CreateBankBookingRequest.Type =
    when (this) {
        BankRequestKind.DEPOSIT -> CreateBankBookingRequest.Type.DEPOSIT
        BankRequestKind.WITHDRAWAL -> CreateBankBookingRequest.Type.WITHDRAWAL
        BankRequestKind.TRANSFER -> CreateBankBookingRequest.Type.TRANSFER
    }

/**
 * Maps a page of the ledger onto the model.
 *
 * @param page the page index that was requested.
 * @return the page, without lines the server sent without a posting id.
 */
private fun PageResponseBankBookingDto.toModel(page: Int): BankBookingPage =
    BankBookingPage(
        bookings = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one ledger line onto the model.
 *
 * @return the line, or `null` when it carries no posting id.
 */
private fun BankBookingDto.toModel(): BankBooking? {
    val id = postingId ?: return null
    return BankBooking(
        id = id,
        type = type?.value.orEmpty(),
        amount = amount?.toString(),
        note = note?.trim()?.takeIf { it.isNotEmpty() },
        holder = holderHandle,
        createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}

/**
 * Maps the settings snapshot onto the model.
 *
 * @return the settings; an id the server did not send would make every write unaddressable, so it
 *   falls back to the empty string and the screen's flags keep every control off.
 */
private fun OrgUnitBankAccountSettingsDto.toModel(): BankAccountSettings =
    BankAccountSettings(
        accountId = accountId.orEmpty(),
        accountName = accountName,
        balanceTarget = balanceTarget?.toString(),
        version = version,
        canSetTarget = canSetTarget == true,
        canConfigureVisibility = canConfigureVisibility == true,
        visibilityConfigurable = visibilityConfigurable == true,
        allMembersSupported = allMembersSupported == true,
        allMembersGranted = allMembersGranted == true,
        availableRoleCodes = availableRoleCodes.orEmpty(),
        grantedRoleCodes = grantedRoleCodes.orEmpty(),
    )

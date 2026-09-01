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
import de.greluc.krt.profit.basetool.android.core.contract.model.BankApprovalLimitsDto
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
import de.greluc.krt.profit.basetool.android.core.contract.model.SetBankApprovalLimitRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankBookingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankGrantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateBankHolderRequest
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.DownloadedFile
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal
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
 * @property canRequest whether this caller may raise a withdrawal or transfer against it. A
 *   deposit is not gated by it (REQ-BANK-042): every active account accepts one.
 * @property approvalLimit the amount this caller may move on it without the responsible holder's
 *   approval, unformatted, or `null` when the account sets none
 * @property approvalExempt whether this caller is exempt from that threshold, which is why the
 *   request sheet must not state a limit it has read but that will never bind
 */
data class BankAccountSummary(
    val id: String,
    val accountNo: String?,
    val name: String,
    val orgUnitName: String?,
    val balance: String?,
    val delta30d: String?,
    val sparkline: List<Double>,
    val canRequest: Boolean = false,
    val approvalLimit: String? = null,
    val approvalExempt: Boolean = false,
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
 * Where a request stands.
 *
 * Terminal in three of the four cases: only [PENDING] still moves.
 */
enum class BankRequestStatus {
    /** Raised and undecided. */
    PENDING,

    /** A bank employee booked it; the money has moved. */
    CONFIRMED,

    /** A bank employee refused it. */
    REJECTED,

    /** The requester withdrew it before any decision; no ledger effect. */
    CANCELLED,
}

/**
 * Which class of approver a flagged request waits on.
 *
 * Decided by the server when the request is raised and immutable afterwards. For every
 * request-capable account except the KRT one it is [RESPONSIBLE_HOLDER]; only the KRT account
 * escalates by amount (REQ-BANK-047), and that ladder escalates **who** must approve, never how
 * many must. There is no approval count anywhere in this flow.
 */
enum class BankRequestApprover {
    /** The account's responsible holder — Staffelleiter / SK-Lead, or Bereichsleiter. */
    RESPONSIBLE_HOLDER,

    /** The Bankleitung, for the middle band of the KRT account's amount ladder. */
    BANK_MANAGEMENT,

    /** The Organisationsleitung, for the top band. */
    ORGANISATIONSLEITUNG,
}

/**
 * A booking request as the member sees it.
 *
 * The approval model is **two-step and single-vote** (REQ-BANK-041): a request above the caller's
 * limit is flagged, one holder of [requiredApprover] grants the owner approval, and only then may
 * a bank employee confirm it. [ownerApprovalGranted] is therefore a gate that has or has not been
 * passed — not a tally.
 *
 * @property id the request.
 * @property accountId which account it moves, needed to reopen the sheet on it.
 * @property accountName that account by name.
 * @property targetAccountId where a transfer goes; `null` for the other two kinds.
 * @property kind what it asks for.
 * @property amount how much, unformatted and always positive.
 * @property note what it is for, or `null`.
 * @property status where it stands, or `null` if the server sent a value this build predates.
 * @property requester who raised it, by handle.
 * @property rejectReason why a bank employee refused it, or `null`. Shown on the row, because a
 *   rejection without its reason leaves the requester nothing to correct.
 * @property applicableLimit the threshold that flagged it, as the server snapshotted it at
 *   creation. Kept for display: it is what makes the approval line state a number rather than a
 *   vague warning.
 * @property requiresOwnerApproval whether it was flagged as needing an owner approval before a
 *   bank employee may act. `false` means a bank employee can confirm it straight away.
 * @property ownerApprovalGranted whether that approval has been given. Meaningless while
 *   [requiresOwnerApproval] is `false`.
 * @property ownerApprovalBy who granted it, by handle, or `null` while it is outstanding.
 * @property requiredApprover which class must grant it; `null` when none is needed.
 * @property createdAt when it was raised, in UTC.
 * @property version the optimistic-locking version. Every write against a request echoes it, so
 *   two approvers acting on the same request at the same moment collide with a 409 instead of one
 *   silently overwriting the other.
 */
data class BankBookingRequest(
    val id: String,
    val accountId: String?,
    val accountName: String?,
    val targetAccountId: String?,
    val kind: BankRequestKind?,
    val amount: String?,
    val note: String?,
    val status: BankRequestStatus?,
    val requester: String?,
    val rejectReason: String?,
    val applicableLimit: String?,
    val requiresOwnerApproval: Boolean,
    val ownerApprovalGranted: Boolean,
    val ownerApprovalBy: String?,
    val requiredApprover: BankRequestApprover?,
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
    val transactionId: String?,
    val type: String,
    val amount: String?,
    val note: String?,
    val holder: String?,
    val createdAt: Instant?,
    val reversesTransactionId: String? = null,
) {
    /**
     * Whether this row is itself a counter-booking.
     *
     * **Not** "has been reversed": the wire field names the transaction this one negates, so it is
     * set on the Storno and absent on the original. Reading it the other way round labels the
     * counter-booking as reversed and leaves the original offering an action the server refuses.
     */
    val isReversal: Boolean get() = reversesTransactionId != null

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
 * One member with a limit of their own.
 *
 * @property userId who.
 * @property displayName what to show for them.
 * @property limit how much they may book without a further approval, as the server rendered it.
 */
data class BankApprovalLimitUser(
    val userId: String,
    val displayName: String,
    val limit: String,
)

/**
 * The account's Freigabe-Limits (design ch. 12 artboard 10).
 *
 * **Limits per tier, not „approval steps by amount".** Up to the limit a booking may be requested
 * without a further approval; above it the account's owner has to release it. A **user** limit
 * beats the tier limit.
 *
 * @property canEdit whether the caller may change them.
 * @property configurable whether this account has them at all.
 * @property allMembersSupported whether „Alle Mitglieder der Org-Einheit" applies here.
 * @property areaMembersSupported whether the Bereich tier applies here — a dimension the artboard
 *   does not draw and the server does support, so it is shown when the server says it exists.
 * @property allMembersLimit the limit for everyone, or `null` when none is set.
 * @property areaMembersLimit the same for the Bereich.
 * @property roleLimits the limit per role code, in the server's own map.
 * @property availableRoleCodes the roles a limit may be set for.
 * @property userLimits the individual limits.
 */
data class BankApprovalLimits(
    val canEdit: Boolean = false,
    val configurable: Boolean = false,
    val allMembersSupported: Boolean = false,
    val areaMembersSupported: Boolean = false,
    val allMembersLimit: String? = null,
    val areaMembersLimit: String? = null,
    val roleLimits: Map<String, String> = emptyMap(),
    val availableRoleCodes: List<String> = emptyList(),
    val userLimits: List<BankApprovalLimitUser> = emptyList(),
)

/**
 * Which limit a write addresses.
 *
 * Four dimensions, four endpoints under `…/approval-limit/`, and one shape: `PUT {limit}` sets,
 * `DELETE` removes. The artboard's words for the two actions are „Setzen" and „Entfernen" — not
 * „Speichern" and „Löschen", which would promise a form and a deletion respectively.
 */
sealed interface BankLimitTarget {
    /** Everyone in the org unit — the artboard's bottom row of the tier list. */
    data object AllMembers : BankLimitTarget

    /** Everyone in the Bereich. */
    data object AreaMembers : BankLimitTarget

    /**
     * One role.
     *
     * @property roleCode which role.
     */
    data class Role(
        val roleCode: String,
    ) : BankLimitTarget

    /**
     * One member.
     *
     * @property userId who.
     */
    data class User(
        val userId: String,
    ) : BankLimitTarget
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
    val approvalLimits: BankApprovalLimits = BankApprovalLimits(),
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
     * Sets one Freigabe-Limit (design ch. 12 artboard 10).
     *
     * @param id the account.
     * @param target which limit — everyone, the Bereich, one role, or one member.
     * @param limit how much, as typed.
     * @return the account's settings as they now stand, or the classified failure. `409` when
     *   somebody changed them first.
     */
    suspend fun setApprovalLimit(
        id: String,
        target: BankLimitTarget,
        limit: String,
    ): ApiResult<BankAccountSettings>

    /**
     * Removes one.
     *
     * The tier below it then applies, which the confirmation names — removing a limit is not the
     * same as setting it to zero, and a member has to be told which one takes over.
     *
     * @param id the account.
     * @param target which limit.
     * @return the account's settings as they now stand, or the classified failure.
     */
    suspend fun clearApprovalLimit(
        id: String,
        target: BankLimitTarget,
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
     * Corrects one of the caller's own pending, unapproved requests.
     *
     * The account and the kind are deliberately absent: the server refuses a change to either, so
     * a signature that accepted them would promise something the API does not do. Correcting those
     * means withdrawing the request and raising a new one.
     *
     * @param id the request.
     * @param version the optimistic-locking version to echo.
     * @param amount the corrected amount, as typed.
     * @param note the corrected purpose, or `null` to clear it.
     * @param targetAccountId where a transfer goes, unchanged for the other two kinds.
     * @return the request as the server recorded it, or the classified failure — a 409 when
     *   somebody approved or booked it while the sheet was open.
     */
    suspend fun updateRequest(
        id: String,
        version: Long,
        amount: String,
        note: String?,
        targetAccountId: String? = null,
    ): ApiResult<BankBookingRequest>

    /**
     * Grants or revokes this member's approval on someone else's request.
     *
     * **No version, unlike every other write here.** The server takes no body on either verb of
     * `…/owner-approval`, so there is nothing to echo: the grant is idempotent and the state it
     * sets does not depend on what the client last read.
     *
     * @param id the request.
     * @param granted whether to grant.
     * @return the request in its new state.
     */
    suspend fun setOwnerApproval(
        id: String,
        granted: Boolean,
    ): ApiResult<BankBookingRequest>
}

/**
 * Where an account stands in its life.
 *
 * A closed account still appears on the staff dashboard, dimmed. That is a **data** difference
 * rather than a rights lock, which is why the design draws it without a padlock.
 */
enum class BankAccountStatus {
    /** Takes bookings. */
    ACTIVE,

    /** Takes none, and can be reopened. */
    CLOSED,
}

/**
 * One account as the staff dashboard lists it.
 *
 * Staff see **every** account of the unit, including ones they hold no view grant on and ones that
 * are closed — the delta to the member list, which shows only what the caller may see.
 *
 * @property id the account.
 * @property accountNo the number a member quotes when asking about it.
 * @property name its display name.
 * @property type the account kind as the server names it; `CARTEL` is the one visible to everyone
 *   (REQ-BANK-037), which is what earns the row its visible-to-all chip.
 * @property status active or closed.
 * @property balance the balance as the server rendered it, unformatted.
 * @property delta30d how it moved over thirty days, or `null`.
 * @property sparkline the points the design draws as a polyline; empty when none came.
 */
data class BankStaffAccount(
    val id: String,
    val accountNo: String?,
    val name: String,
    val type: String?,
    val status: BankAccountStatus,
    val balance: String?,
    val delta30d: String?,
    val sparkline: List<Double>,
)

/**
 * What the dashboard's KPI band states.
 *
 * @property totalBalance the sum over the **open** accounts, as the server computed it.
 * @property activeAccounts how many are open.
 * @property closedAccounts how many are closed.
 */
data class BankStaffTotals(
    val totalBalance: String?,
    val activeAccounts: Long,
    val closedAccounts: Long,
)

/**
 * The staff dashboard.
 *
 * @property management whether the **server** grants this caller Bank-Management. It decides what
 *   the dashboard even contains (REQ-BANK-010): management sees every account plus the aggregate
 *   strip, a plain bank employee sees exactly the accounts they hold a grant for and no strip at
 *   all.
 * @property accounts the accounts this caller may see — every one for management, the granted ones
 *   for an employee.
 * @property totals the KPI band, or **`null` when the caller is not management**. Absent means
 *   "not for you", which is a different statement from zero and must not be rendered as one.
 */
data class BankStaffDashboard(
    val management: Boolean,
    val accounts: List<BankStaffAccount>,
    val totals: BankStaffTotals?,
)

/**
 * One page of the bank-staff request queue.
 *
 * @property requests the rows.
 * @property page which page this is, zero-based.
 * @property totalPages how many exist.
 * @property totalElements how many rows the whole queue holds.
 */
data class BankRequestPage(
    val requests: List<BankBookingRequest>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page follows. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * One holder of the bank's money, as the confirmation picker offers them.
 *
 * A "Verwahrer" holds cash on the organisation's behalf; a booked deposit or withdrawal records
 * which one received or paid it out. Verwahrung is kept at unit level and is **not** mapped to
 * individual accounts.
 *
 * @property id the holder.
 * @property handle their in-game name.
 * @property active whether they still hold; an inactive one is kept for the ledger's sake and is
 *   not offered.
 * @property totalHeld how much they hold altogether, unformatted.
 * @property version the optimistic-locking version an activation change echoes.
 */
data class BankHolder(
    val id: String,
    val handle: String,
    val active: Boolean,
    val totalHeld: String?,
    val version: Long = 0,
)

/**
 * What confirming a request records.
 *
 * @property requestId which request.
 * @property version the version it was read at.
 * @property holderId who received or paid out the money. **Required by the server**, which is why
 *   confirming is a sheet rather than a button.
 * @property destinationHolderId the receiving holder of a transfer; `null` for the other kinds.
 * @property ownerApprovalConfirmed the employee's attestation that the responsible holder's
 *   approval was obtained. An over-limit request is refused with `BANK_OWNER_APPROVAL_REQUIRED`
 *   without it (REQ-BANK-041); for a request that needs none it carries no meaning.
 * @property staffNote the employee's own note on the booking (REQ-BANK-054), or `null`.
 */
data class BankConfirmation(
    val requestId: String,
    val version: Long,
    val holderId: String,
    val destinationHolderId: String? = null,
    val ownerApprovalConfirmed: Boolean = false,
    val staffNote: String? = null,
)

/**
 * One account as the lifecycle tab lists it.
 *
 * Distinct from [BankStaffAccount], which the dashboard supplies: that one carries a balance line
 * and no `version`, and every write here echoes one.
 *
 * @property id the account.
 * @property accountNo the number.
 * @property name its display name.
 * @property type the account kind as the server names it.
 * @property status active or closed.
 * @property balance the balance, unformatted. **Closing needs it to be zero** — the server refuses
 *   otherwise, and the row says so rather than letting the button answer for it.
 * @property orgUnitName which unit owns it, or `null`.
 * @property version the optimistic-locking version every lifecycle write echoes.
 */
data class BankManagedAccount(
    val id: String,
    val accountNo: String?,
    val name: String,
    val type: String?,
    val status: BankAccountStatus,
    val balance: String?,
    val orgUnitName: String?,
    val version: Long,
)

/**
 * The account lifecycle and the unit's holders.
 *
 * Every write here is `BANK_MANAGEMENT`, not merely `BANK_EMPLOYEE` — the reads are the employee's,
 * the changes are the leadership's. The screen offers them from what the server said rather than
 * from a role the app worked out.
 */
interface BankLifecycleSource {
    /**
     * Reads one page of the account list.
     *
     * @param page which page, zero-based.
     * @param pageSize how many rows.
     * @return the accounts, or the classified failure.
     */
    suspend fun managedAccounts(
        page: Int = 0,
        pageSize: Int = ACCOUNTS_PAGE_SIZE,
    ): ApiResult<List<BankManagedAccount>>

    /**
     * Opens a new account.
     *
     * @param name what to call it.
     * @param orgUnitId which unit owns it.
     * @return the account as the server recorded it.
     */
    suspend fun createAccount(
        name: String,
        orgUnitId: String,
    ): ApiResult<BankManagedAccount>

    /**
     * Renames an account.
     *
     * @param id the account.
     * @param name the new name.
     * @param version the version it was read at.
     * @return the account in its new state.
     */
    suspend fun renameAccount(
        id: String,
        name: String,
        version: Long,
    ): ApiResult<BankManagedAccount>

    /**
     * Closes or reopens an account.
     *
     * Reversible on purpose, which is why it carries no type-to-confirm: a closed account simply
     * takes no further bookings.
     *
     * @param id the account.
     * @param open whether to reopen it; `false` closes it.
     * @param version the version it was read at.
     * @return the account in its new state, or the classified failure — a 409 when it still holds
     *   a balance or has undecided requests against it.
     */
    suspend fun setAccountOpen(
        id: String,
        open: Boolean,
        version: Long,
    ): ApiResult<BankManagedAccount>

    /**
     * Registers a member as a holder.
     *
     * @param userId the member; only a registered tool user may hold.
     * @return the holder as the server recorded them.
     */
    suspend fun registerHolder(userId: String): ApiResult<BankHolder>

    /**
     * Activates or deactivates a holder.
     *
     * **Not a removal.** An inactive holder can have no *new* money assigned to them; what they
     * already hold stays withdrawable. Nothing about their rights on an account changes — that is
     * what a grant does, and it lives elsewhere.
     *
     * @param id the holder.
     * @param active whether they may take new money.
     * @param version the version they were read at.
     * @return the holder in their new state.
     */
    suspend fun setHolderActive(
        id: String,
        active: Boolean,
        version: Long,
    ): ApiResult<BankHolder>
}

/** How many accounts one page of the lifecycle list carries. */
const val ACCOUNTS_PAGE_SIZE: Int = 100

/**
 * One member's standing on one account.
 *
 * **The row's existence is the view grant** (REQ-BANK-009): a row with all three flags false lets
 * the member see the account and book nothing. Revoking sight means deleting the row, not clearing
 * a fourth flag.
 *
 * @property userId the member.
 * @property handle their in-game name.
 * @property accountId the account.
 * @property canDeposit whether they may book money in.
 * @property canWithdraw whether they may book money out.
 * @property canTransfer whether they may move money to another account, judged on the **source**.
 * @property version the optimistic-locking version a flag change echoes.
 * @property exists whether the server already holds this row, which decides whether a change is a
 *   creation or a patch. **Not derivable from [version]**: a freshly inserted row's `@Version` is
 *   zero, so treating zero as "new" sends every first edit of an untouched grant as a creation and
 *   earns a 409.
 */
data class BankGrant(
    val userId: String,
    val handle: String,
    val accountId: String,
    val canDeposit: Boolean,
    val canWithdraw: Boolean,
    val canTransfer: Boolean,
    val version: Long,
    val exists: Boolean = true,
)

/**
 * One account as the **office** sees it — `BANK_EMPLOYEE`.
 *
 * Distinct from [BankSource]'s pair of the same shape, and not interchangeable with it: the member
 * paths answer with what this caller may see, so a bank manager holding no view grant gets 403 on
 * an account they are nevertheless responsible for. These paths answer for every account of the
 * organisation, including closed ones.
 */
interface BankStaffAccountSource {
    /**
     * Reads one account.
     *
     * @param id which account.
     * @return the account, or the classified failure.
     */
    suspend fun staffAccount(id: String): ApiResult<BankAccountDetail>

    /**
     * Reads a page of one account's ledger.
     *
     * @param id which account.
     * @param page which page, zero-based.
     * @param pageSize how many rows.
     * @return the page, or the classified failure.
     */
    suspend fun staffBookings(
        id: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<BankBookingPage>
}

/**
 * The bank's two reports.
 *
 * Both answer a **binary** body — a PDF and a spreadsheet — with the server's own file name in
 * `Content-Disposition`. Nothing here decodes a schema, and nothing here invents a name: two
 * systems calling the same download different things is a support conversation waiting to happen.
 */
interface BankReportSource {
    /**
     * Fetches one account's statement for a period.
     *
     * @param accountId which account.
     * @param from the start of the period, ISO-8601 in UTC.
     * @param to its end.
     * @return the PDF, or the classified failure.
     */
    suspend fun statement(
        accountId: String,
        from: String,
        to: String,
    ): ApiResult<DownloadedFile>

    /**
     * Fetches the three-month report.
     *
     * @param zoneId the reader's zone, which decides where the report's month boundaries fall.
     * @return the file, or the classified failure.
     */
    suspend fun threeMonthReport(zoneId: String): ApiResult<DownloadedFile>
}

/**
 * Reversing a booking — `BANK_EMPLOYEE`, and the one destructive-looking act in the ledger that is
 * not destructive at all.
 */
interface BankReversalSource {
    /**
     * Reverses one transaction.
     *
     * Writes a **negated counter-booking**; the original stays in the ledger unchanged. The server
     * refuses a second one on the same transaction with `BANK_ALREADY_REVERSED`, so the screen must
     * not offer it on a row that already carries a reversal.
     *
     * @param transactionId which transaction — not the posting id.
     * @param note what to record about it, or `null`.
     * @return nothing usable beyond success, or the classified failure.
     */
    suspend fun reverse(
        transactionId: String,
        note: String?,
    ): ApiResult<Unit>
}

/**
 * One posting against a holder's custody.
 *
 * @property id the posting.
 * @property transactionId the transaction it belongs to, which a reversal would name.
 * @property type what kind of movement it was.
 * @property amount the signed amount, as the server wrote it.
 * @property note what was said about it, if anything.
 * @property createdAt when it was booked, in UTC.
 * @property counterAccount the account on the other side, or `null` for a holder-to-holder move —
 *   custody is kept at org-unit level and those transfers touch no account at all.
 * @property counterHolder the holder on the other side, or `null`.
 * @property reversed whether this posting is itself a counter-booking.
 */
data class BankHolderBooking(
    val id: String,
    val transactionId: String?,
    val type: String?,
    val amount: String?,
    val note: String?,
    val createdAt: String?,
    val counterAccount: String?,
    val counterHolder: String?,
    val reversed: Boolean,
)

/**
 * One page of a holder's postings.
 *
 * @property rows the postings.
 * @property page which page this is, zero-based.
 * @property totalElements how many postings there are in total.
 * @property totalPages how many pages that makes.
 */
data class BankHolderBookingPage(
    val rows: List<BankHolderBooking>,
    val page: Int,
    val totalElements: Long,
    val totalPages: Int,
)

/**
 * The holder register's detail — `BANK_EMPLOYEE` to read, `BANK_MANAGEMENT` to move custody.
 */
interface BankHolderSource {
    /**
     * Reads one holder.
     *
     * @param id which holder.
     * @return the holder, or the classified failure.
     */
    suspend fun holder(id: String): ApiResult<BankHolder>

    /**
     * Reads a page of one holder's postings.
     *
     * @param id which holder.
     * @param page which page, zero-based.
     * @param pageSize how many rows.
     * @return the page, or the classified failure.
     */
    suspend fun holderBookings(
        id: String,
        page: Int = 0,
        pageSize: Int = HOLDER_PAGE_SIZE,
    ): ApiResult<BankHolderBookingPage>

    /**
     * Moves custody from one holder to another.
     *
     * Touches **no account**: custody is kept at org-unit level, so this only re-attributes who is
     * holding it. The source may go negative, which is deliberate and which the screen says.
     *
     * @param sourceHolderId who gives.
     * @param destinationHolderId who receives.
     * @param amount how much, as a decimal string.
     * @param note what to record about it, or `null`.
     * @return nothing usable beyond success, or the classified failure.
     */
    suspend fun transferCustody(
        sourceHolderId: String,
        destinationHolderId: String,
        amount: String,
        note: String?,
    ): ApiResult<Unit>
}

/** How many postings one page of the holder detail carries. */
const val HOLDER_PAGE_SIZE: Int = 25

/**
 * A member the grants matrix can be extended to.
 *
 * @property id the user.
 * @property handle their in-game name, which is the only field of theirs this screen shows — the
 *   search answers with e-mail and rank too, and neither belongs on a grants picker.
 */
data class BankGrantee(
    val id: String,
    val handle: String,
)

/**
 * The grants matrix — `BANK_MANAGEMENT` throughout.
 *
 * Org-unit membership of the grantee is irrelevant in both directions (REQ-BANK-008); what the
 * server does require is that the grantee holds the Bank Employee role, and it refuses a creation
 * for anyone else.
 */
interface BankGrantSource {
    /**
     * Reads the grants on one account.
     *
     * @param accountId which account's matrix.
     * @return the grants, or the classified failure.
     */
    suspend fun grants(accountId: String): ApiResult<List<BankGrant>>

    /**
     * Gives a member a standing on an account, or changes the one they have.
     *
     * Creating with all three flags false is the deliberate "may see, may book nothing" case.
     *
     * @param grant what the matrix now says. A grant whose `exists` is false is created rather
     *   than patched — the version cannot say, because a new row's version is zero too.
     * @return the grant as the server recorded it.
     */
    suspend fun setGrant(grant: BankGrant): ApiResult<BankGrant>

    /**
     * Takes a member's standing away entirely.
     *
     * This is what revokes **sight** — there is no view flag to clear.
     *
     * @param userId the member.
     * @param accountId the account.
     * @return nothing, or the classified failure.
     */
    suspend fun revokeGrant(
        userId: String,
        accountId: String,
    ): ApiResult<Unit>

    /**
     * Searches the members a grant can be given to.
     *
     * Answers over the **whole** user base rather than only over bank employees, because the server
     * does: the same search backs the holder register and the approval limits. A member without the
     * Bank Employee role can therefore be picked, and the creation then fails with
     * `BANK_GRANTEE_MISSING_ROLE` — the screen says so rather than pretending the pick was
     * impossible.
     *
     * @param query what was typed; blank asks for the first page unfiltered.
     * @return one page of candidates and whether the roster holds more (ADR-0104), or the
     *   classified failure.
     */
    suspend fun searchGrantees(query: String): ApiResult<PickerPage<BankGrantee>>
}

/** Which of the three direct bookings the Verwaltung is making. */
enum class DirectBookingKind {
    /** Money comes in. */
    DEPOSIT,

    /** Money goes out; the amount is checked against the balance before the CTA. */
    WITHDRAWAL,

    /** Money moves between two accounts of the unit. */
    TRANSFER,
}

/**
 * A booking the Verwaltung makes **without a request** (design ch. 12 artboard 9).
 *
 * It exists for the case nobody files a request for — cash handed over in-game, a correction of
 * somebody else's booking — and it lives only in the Verwaltung. There is **no second approval**:
 * a wrong direct booking is corrected with a reversal, not edited.
 *
 * @property kind which of the three.
 * @property accountId the account it lands on; the **source** account for a transfer.
 * @property amount how much, as typed.
 * @property holderId who physically holds the money. Required in all three modes, and by the
 *   server — custody is kept per org unit, not per account, so a balance without a holder would
 *   be money nobody is accountable for.
 * @property note the Verwendungszweck.
 * @property destinationAccountId the receiving account, for a transfer.
 * @property destinationHolderId who holds it afterwards, for a transfer.
 */
data class DirectBooking(
    val kind: DirectBookingKind,
    val accountId: String,
    val amount: String,
    val holderId: String,
    val note: String? = null,
    val destinationAccountId: String? = null,
    val destinationHolderId: String? = null,
)

/**
 * The bank-staff surface — design chapter 12, artboards 4 to 8.
 *
 * Everything here is `hasRole(BANK_EMPLOYEE)` or narrower, and everything here was out of the
 * app's reach until `REQ-APP-BANK-007` was amended. Everything under `/api/v1/bank/admin` stays
 * out permanently.
 */
interface BankStaffSource {
    /**
     * Reads the staff dashboard: every account of the unit, the KPI band, and whether the server
     * grants this caller Bank-Management.
     *
     * @return the dashboard, or the classified failure - `Forbidden` for a caller who is not a
     *   bank employee, which is the ordinary answer rather than a defect.
     */
    suspend fun staffDashboard(): ApiResult<BankStaffDashboard>

    /**
     * Reads one page of the request queue.
     *
     * @param statuses which states to include; empty asks the server for its default, which is
     *   `PENDING` alone.
     * @param page which page, zero-based.
     * @param pageSize how many rows.
     * @return the page, or the classified failure.
     */
    suspend fun requestQueue(
        statuses: Set<BankRequestStatus> = emptySet(),
        page: Int = 0,
        pageSize: Int = QUEUE_PAGE_SIZE,
    ): ApiResult<BankRequestPage>

    /**
     * Reads the unit's holders.
     *
     * @return the holders, or the classified failure.
     */
    suspend fun holders(): ApiResult<List<BankHolder>>

    /**
     * Confirms a pending request and books it onto the ledger.
     *
     * @param confirmation what the employee recorded.
     * @return the request in its booked state, or the classified failure — a 409 when somebody
     *   decided it first, or when an over-limit request was confirmed without the attestation.
     */
    suspend fun confirmRequest(confirmation: BankConfirmation): ApiResult<BankBookingRequest>

    /**
     * Books directly, without a request (`REQ-APP-BANK-*`).
     *
     * Three endpoints behind one call: `POST /bank/deposits`, `/bank/withdrawals` and
     * `/bank/transfers`. They agree on the fields that matter here and differ only in which
     * account the amount leaves — which is the same distinction the sheet's segment draws.
     *
     * @param booking what to book.
     * @return nothing on success, or the classified failure. `403` is ordinary: direct booking
     *   needs the bank-management grant, which `/users/me` reports as `canManageBank`.
     */
    suspend fun bookDirectly(booking: DirectBooking): ApiResult<Unit>

    /**
     * Refuses a pending request. No money moves.
     *
     * @param id the request.
     * @param reason why; the server requires one and the requester is shown it.
     * @param version the version it was read at.
     * @return the request in its refused state, or the classified failure.
     */
    suspend fun rejectRequest(
        id: String,
        reason: String,
        version: Long,
    ): ApiResult<BankBookingRequest>
}

/** How many queue rows one page carries; the counter walks whole pages of this size. */
const val QUEUE_PAGE_SIZE: Int = 50

/**
 * Reads the org bank from the backend.
 *
 * **The member surface only.** `/org-units/bank/…` answers with the accounts this caller may
 * actually see — the ones public to everyone plus those they hold a view grant for. The staff
 * paths under `/bank/…` list every account in the organisation behind a bank role and belong to
 * [BankStaffRepository]; the split follows the two surfaces rather than the one file.
 *
 * Everything under `/api/v1/bank/admin` is reached by neither and never will be — that is the
 * admin area, which is web-only by owner decision.
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
                    target = parseTypedDecimal(target)?.let(::KrtDecimal),
                    version = version ?: 0L,
                ),
                OrgUnitBalanceTargetRequest.serializer(),
                OrgUnitBankAccountSettingsDto.serializer(),
            ),
        )

    override suspend fun setApprovalLimit(
        id: String,
        target: BankLimitTarget,
        limit: String,
    ): ApiResult<BankAccountSettings> {
        val figure = parseTypedDecimal(limit) ?: return ApiResult.Failure(ApiError.Validation())
        return mapped(
            reader.put(
                path = limitPath(id, target),
                body = SetBankApprovalLimitRequest(limit = KrtDecimal(figure)),
                bodySerializer = SetBankApprovalLimitRequest.serializer(),
                deserializer = OrgUnitBankAccountSettingsDto.serializer(),
            ),
        )
    }

    override suspend fun clearApprovalLimit(
        id: String,
        target: BankLimitTarget,
    ): ApiResult<BankAccountSettings> =
        mapped(
            reader.delete(limitPath(id, target), OrgUnitBankAccountSettingsDto.serializer()),
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
                        amount = KrtDecimal(parseTypedDecimal(draft.amount) ?: BigDecimal.ZERO),
                        // Only a transfer names a second account; the server ignores it otherwise,
                        // and sending it anyway would put a value on the wire describing nothing.
                        targetAccountId = draft.targetAccountId.takeIf { draft.kind == BankRequestKind.TRANSFER },
                        note = draft.note?.takeIf { it.isNotBlank() },
                    ),
                bodySerializer = CreateBankBookingRequest.serializer(),
                deserializer = BankBookingRequestDto.serializer(),
            ),
        )

    override suspend fun updateRequest(
        id: String,
        version: Long,
        amount: String,
        note: String?,
        targetAccountId: String?,
    ): ApiResult<BankBookingRequest> =
        single(
            reader.send(
                path = "$REQUESTS_PATH/$id",
                method = "PUT",
                body =
                    UpdateBankBookingRequest(
                        amount = KrtDecimal(parseTypedDecimal(amount) ?: BigDecimal.ZERO),
                        note = note?.takeIf { it.isNotBlank() },
                        targetAccountId = targetAccountId,
                        version = version,
                    ),
                bodySerializer = UpdateBankBookingRequest.serializer(),
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
    ): ApiResult<BankBookingRequest> {
        val path = "$REQUESTS_PATH/$id/owner-approval"
        val result =
            if (granted) {
                reader.post(path = path, deserializer = BankBookingRequestDto.serializer())
            } else {
                reader.delete(path = path, deserializer = BankBookingRequestDto.serializer())
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
         * One limit's path.
         *
         * @param id the account.
         * @param target which limit.
         * @return the path.
         */
        private fun limitPath(
            id: String,
            target: BankLimitTarget,
        ): String {
            val leaf =
                when (target) {
                    BankLimitTarget.AllMembers -> "all-members"
                    BankLimitTarget.AreaMembers -> "area-members"
                    is BankLimitTarget.Role -> "role/${target.roleCode}"
                    is BankLimitTarget.User -> "user/${target.userId}"
                }
            return "${accountPath(id)}/approval-limit/$leaf"
        }

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
        canRequest = canRequest == true,
        approvalLimit = approvalLimit?.toString(),
        approvalExempt = approvalExempt == true,
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
internal fun BankBookingRequestDto.toModel(): BankBookingRequest? {
    val requestId = id ?: return null
    return BankBookingRequest(
        id = requestId,
        accountId = accountId,
        accountName = accountName,
        targetAccountId = targetAccountId,
        kind =
            when (type) {
                BankBookingRequestDto.Type.DEPOSIT -> BankRequestKind.DEPOSIT
                BankBookingRequestDto.Type.WITHDRAWAL -> BankRequestKind.WITHDRAWAL
                BankBookingRequestDto.Type.TRANSFER -> BankRequestKind.TRANSFER
                else -> null
            },
        amount = amount?.toString(),
        note = note?.takeIf { it.isNotBlank() } ?: justification?.takeIf { it.isNotBlank() },
        status = status.toModel(),
        requester = requesterHandle,
        rejectReason = rejectReason?.takeIf { it.isNotBlank() },
        applicableLimit = applicableLimit?.toString(),
        requiresOwnerApproval = requiresOwnerApproval == true,
        ownerApprovalGranted = ownerApprovalGranted == true,
        ownerApprovalBy = ownerApprovalGrantedByHandle?.takeIf { it.isNotBlank() },
        requiredApprover = requiredApprover.toApprover(),
        createdAt = createdAt,
        version = version ?: 0L,
    )
}

/**
 * Maps where a request stands onto the model.
 *
 * @return the status, or `null` when the server sent one this build does not know.
 */
internal fun BankBookingRequestDto.Status?.toModel(): BankRequestStatus? =
    when (this) {
        BankBookingRequestDto.Status.PENDING -> BankRequestStatus.PENDING
        BankBookingRequestDto.Status.CONFIRMED -> BankRequestStatus.CONFIRMED
        BankBookingRequestDto.Status.REJECTED -> BankRequestStatus.REJECTED
        BankBookingRequestDto.Status.CANCELLED -> BankRequestStatus.CANCELLED
        null -> null
    }

/**
 * Maps the approver class onto the model.
 *
 * The contract types this one as a bare string rather than an enum, so an unknown value has to
 * stay possible: it maps to `null`, which reads as "no approver named" and hides the chip rather
 * than crashing on a band this build predates.
 *
 * @return the approver class, or `null`.
 */
private fun String?.toApprover(): BankRequestApprover? =
    when (this) {
        "RESPONSIBLE_HOLDER" -> BankRequestApprover.RESPONSIBLE_HOLDER
        "BANK_MANAGEMENT" -> BankRequestApprover.BANK_MANAGEMENT
        "ORGANISATIONSLEITUNG" -> BankRequestApprover.ORGANISATIONSLEITUNG
        else -> null
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
internal fun PageResponseBankBookingDto.toModel(page: Int): BankBookingPage =
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
        // A Storno addresses the transaction, not the posting: one transaction can carry several
        // postings and the reversal negates all of them together.
        transactionId = transactionId,
        type = type?.value.orEmpty(),
        amount = amount?.toString(),
        note = note?.trim()?.takeIf { it.isNotEmpty() },
        holder = holderHandle,
        createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        reversesTransactionId = reversedTransactionId,
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
        approvalLimits =
            approvalLimits?.toModel(canConfigureApprovalLimits == true) ?: BankApprovalLimits(),
    )

/**
 * The account's Freigabe-Limits as the sheet holds them.
 *
 * @receiver what the server sent.
 * @param canEdit whether the caller may change them; the flag sits on the settings, not on the
 *   limits themselves.
 * @return the limits.
 */
private fun BankApprovalLimitsDto.toModel(canEdit: Boolean): BankApprovalLimits =
    BankApprovalLimits(
        canEdit = canEdit,
        configurable = configurable == true,
        allMembersSupported = allMembersSupported == true,
        areaMembersSupported = areaMembersSupported == true,
        allMembersLimit = allMembersLimit?.toString(),
        areaMembersLimit = areaMembersLimit?.toString(),
        roleLimits =
            roleLimits.orEmpty().map { (code, limit) -> code to limit.toString() }.toMap(),
        availableRoleCodes = availableRoleCodes.orEmpty(),
        userLimits =
            userLimits.orEmpty().mapNotNull { row ->
                row.userId?.let {
                    BankApprovalLimitUser(
                        userId = it,
                        displayName = row.displayName.orEmpty(),
                        limit = row.limitAmount?.toString().orEmpty(),
                    )
                }
            },
    )

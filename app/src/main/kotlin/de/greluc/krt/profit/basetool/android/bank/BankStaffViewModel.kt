/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankConfirmation
import de.greluc.krt.profit.basetool.android.core.data.BankDirectOutcome
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
import de.greluc.krt.profit.basetool.android.core.data.DirectBooking
import de.greluc.krt.profit.basetool.android.core.data.DirectBookingKind
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.parseTypedDecimal
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One row of the staff dashboard, with the two facts the row cannot work out for itself.
 *
 * @property account the account.
 * @property openRequests how many undecided requests stand against it, counted from the queue.
 *   Artboard 4's handoff is explicit that this is aggregated client-side and needs no DTO field.
 * @property viewable whether this caller could see the account **without** their staff role. A
 *   staff member sees every account of the unit; the ones they hold no view grant on are marked,
 *   because reading someone's balance by virtue of an office is a different act from reading one
 *   they were given sight of.
 */
data class BankStaffRow(
    val account: BankStaffAccount,
    val openRequests: Int,
    val viewable: Boolean,
)

/**
 * What the confirmation sheet holds while it is open.
 *
 * **Confirming is a sheet, not a button.** `ConfirmBankBookingRequest.holderId` is required by the
 * server, and an over-limit request is additionally refused without the employee's attestation
 * that the responsible holder approved. Artboard 5 draws a bare CTA; the web frontend has a modal
 * for exactly this, and so does the app.
 *
 * @property request which request is being booked.
 * @property holderId who received or paid the money out.
 * @property destinationHolderId the receiving holder of a transfer.
 * @property approvalAttested the employee's attestation. Only meaningful — and only shown — when
 *   the request carries [BankBookingRequest.requiresOwnerApproval].
 * @property staffNote the employee's own note on the booking, or blank.
 * @property saving whether the write is in flight.
 * @property error what the last attempt refused with.
 */
data class BankConfirmState(
    val request: BankBookingRequest,
    val holderId: String? = null,
    val destinationHolderId: String? = null,
    val approvalAttested: Boolean = false,
    val staffNote: String = "",
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /** Whether the server would accept what has been filled in. */
    val submittable: Boolean
        get() =
            holderId != null &&
                (request.kind != BankRequestKind.TRANSFER || destinationHolderId != null) &&
                (!request.requiresOwnerApproval || approvalAttested)
}

/**
 * What the refusal dialog holds.
 *
 * @property request which request is being refused.
 * @property reason why; the server requires one and shows it to the requester.
 * @property saving whether the write is in flight.
 * @property error what the last attempt refused with.
 */
data class BankRejectState(
    val request: BankBookingRequest,
    val reason: String = "",
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /** Whether there is a reason to send. */
    val submittable: Boolean get() = reason.isNotBlank()
}

/**
 * „Direktbuchung" — design ch. 12 artboard 9.
 *
 * One sheet with three modes rather than the web's three forms. The Verwaltung books **without a
 * request**, which is the case nobody files one for: cash handed over in-game, or a correction of
 * somebody else's booking. There is no second approval, and the sheet says so before the member
 * types — a wrong direct booking is corrected with a reversal, not edited.
 *
 * @property kind which of the three modes.
 * @property accountId the account it books onto, or the source for a transfer.
 * @property amount as typed.
 * @property holderId who holds the money afterwards. Required in **all three** modes, the same
 *   rule the request confirmation carries: custody is kept per org unit, so a balance without a
 *   holder is money nobody is accountable for.
 * @property note the Verwendungszweck.
 * @property destinationAccountId the receiving account, for a transfer.
 * @property destinationHolderId who holds it there, for a transfer.
 * @property saving whether the write is in flight.
 * @property error what it was refused with.
 * @property feeRate the org-wide in-game transfer fee, as a fraction, or `null` while it has not
 *   been read. Guidance only — the authoritative fee is computed server-side at booking time.
 * @property feeInclusive whether [amount] is the **debited gross** rather than what the recipient
 *   receives. `false` is the server's own default and means the fee is added on top.
 */
data class DirectBookingState(
    val kind: DirectBookingKind = DirectBookingKind.DEPOSIT,
    val accountId: String? = null,
    val amount: String = "",
    val holderId: String? = null,
    val note: String = "",
    val destinationAccountId: String? = null,
    val destinationHolderId: String? = null,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val feeRate: java.math.BigDecimal? = null,
    val feeInclusive: Boolean = false,
) {
    /** The amount as a figure, or `null` when what was typed is not one. */
    val figure: java.math.BigDecimal? get() = parseTypedDecimal(amount)

    /**
     * Whether the in-game transfer fee applies to what is being booked.
     *
     * A withdrawal always, a transfer only when it changes holder, a deposit never (ADR-0052).
     * The fee block is shown only here — a preview that appears on every mode teaches the member
     * to ignore it.
     */
    val feeApplies: Boolean
        get() =
            when (kind) {
                DirectBookingKind.DEPOSIT -> false
                DirectBookingKind.WITHDRAWAL -> true
                DirectBookingKind.TRANSFER -> holderId != null && holderId != destinationHolderId
            }

    /** The fee this booking would carry, or `null` when none applies or nothing is typed yet. */
    val fee: java.math.BigDecimal?
        get() =
            figure
                ?.takeIf { feeApplies && it.signum() > 0 }
                ?.let { typed -> feeRate?.let { rate -> (typed * rate).setScale(0, java.math.RoundingMode.HALF_UP) } }
                ?.takeIf { it.signum() > 0 }

    /**
     * What actually leaves the account.
     *
     * The figure the member typed is **not** it in the default mode: the fee is added on top and
     * the account is debited the gross (`REQ-BANK-033`). This is the number the overdraft guard
     * runs against server-side, so it is the one the form has to check and to show.
     */
    val debited: java.math.BigDecimal?
        get() = figure?.let { typed -> if (feeInclusive) typed else typed + (fee ?: java.math.BigDecimal.ZERO) }

    /** What the recipient ends up with. */
    val arrives: java.math.BigDecimal?
        get() = figure?.let { typed -> if (feeInclusive) typed - (fee ?: java.math.BigDecimal.ZERO) else typed }

    /**
     * Whether the CTA may be pressed.
     *
     * A withdrawal over the balance is **validation-dimmed**, not locked: nothing is forbidden
     * here, the figure is simply wrong, and the design draws that difference deliberately. The
     * bound itself is applied by the screen, which is where the balance is.
     */
    fun submittable(balance: java.math.BigDecimal?): Boolean {
        val positive = figure?.let { it > java.math.BigDecimal.ZERO } == true
        val addressed = accountId != null && holderId != null
        val targeted =
            kind != DirectBookingKind.TRANSFER ||
                (destinationAccountId != null && destinationHolderId != null)
        return positive && addressed && targeted && covers(balance) && reachesRecipient && !saving
    }

    /**
     * Whether the account can carry what this booking takes out of it.
     *
     * Against what is **debited**, not against what was typed: with the fee on top the gross is
     * the figure the server's own overdraft guard uses, so checking the typed one would let the
     * form invite a booking the server then refuses.
     *
     * @param balance what the account stands at, or `null` when the screen does not know.
     * @return whether the withdrawal fits, and `true` for every other mode.
     */
    private fun covers(balance: java.math.BigDecimal?): Boolean {
        val leaving = debited
        return kind != DirectBookingKind.WITHDRAWAL ||
            balance == null ||
            leaving == null ||
            leaving <= balance
    }

    /**
     * Whether anything would actually arrive.
     *
     * In fee-inclusive mode the fee comes **out** of the amount, so a figure at or below the fee
     * would leave the recipient nothing; the server answers `BANK_FEE_EXCEEDS_AMOUNT`.
     */
    private val reachesRecipient: Boolean
        get() = !feeInclusive || arrives?.let { it.signum() > 0 } != false

    /**
     * What the source account stands at afterwards — the artboard's live preview.
     *
     * @param balance what it stands at now, or `null` when the screen does not know.
     * @return the figure after this booking, or `null` when either half is missing.
     */
    fun preview(balance: java.math.BigDecimal?): java.math.BigDecimal? =
        // `debited`, not the typed figure: on a fee-bearing booking more leaves the account than
        // was typed, and a preview that ignored that showed a balance the account never reaches.
        debited?.let { leaving ->
            balance?.let { current ->
                if (kind == DirectBookingKind.DEPOSIT) current + leaving else current - leaving
            }
        }
}

/**
 * The Verwaltung scope's Übersicht tab.
 *
 * @property rows every account of the unit.
 * @property totals the KPI band, or `null` when the server withheld it — which it does for
 *   every caller who is not Bank-Management (REQ-BANK-010).
 * @property management whether the **server** grants this caller Bank-Management.
 * @property openRequestTotal how many undecided requests the queue holds in total.
 * @property queue the undecided requests, in the order the server returned them. The same read
 *   the per-account counter is aggregated from, so the badge cannot disagree with the list.
 * @property holders the unit's holders, which a confirmation has to name one of.
 * @property filed set when a direct booking came back **filed** rather than booked — over
 *   the KRT employee ceiling the server raises an approval request instead (REQ-BANK-047,
 *   ADR-0109) and answers 202. The balance has not moved, so the screen has to say so;
 *   closing the sheet on it in silence is how a member reads an unchanged figure as a bug.
 * @property confirming the open confirmation sheet, or `null`.
 * @property rejecting the open refusal dialog, or `null`.
 * @property busyId the request a decision is currently in flight for.
 * @property countsPartial whether the per-account counters are known to be incomplete — the queue
 *   is paged, and a queue longer than [MAX_COUNTED_PAGES] pages is not walked to the end. The
 *   number is then a floor, and the screen says so rather than showing a total that is quietly
 *   wrong (ADR-0104: no silent caps).
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 */
data class BankStaffState(
    val rows: List<BankStaffRow> = emptyList(),
    val totals: BankStaffTotals? = null,
    val management: Boolean = false,
    val filed: Boolean = false,
    val openRequestTotal: Int = 0,
    val countsPartial: Boolean = false,
    val phase: BankPhase = BankPhase.Loading,
    val refreshing: Boolean = false,
    val queue: List<BankBookingRequest> = emptyList(),
    val holders: List<BankHolder> = emptyList(),
    val confirming: BankConfirmState? = null,
    val rejecting: BankRejectState? = null,
    val busyId: String? = null,
    val direct: DirectBookingState? = null,
)

/**
 * Drives the bank's Verwaltung scope.
 *
 * **A `Forbidden` here is an ordinary answer, not a defect.** The scope segment is drawn for every
 * member — locked for those without the role, per the design's chapter-09 pattern — and a member
 * who taps into it anyway is told what the server said rather than shown a crash.
 *
 * @property source the staff calls.
 * @property memberAccounts the member-visible account list, which is what makes the
 *   "ohne eigenen View-Grant" mark possible: an account on the staff list but not on this one is
 *   an account this caller reaches only through their office.
 * @property liveSync the peer bridge, or `null`.
 */
class BankStaffViewModel(
    private val source: BankStaffSource,
    private val memberAccounts: suspend () -> ApiResult<List<BankAccountSummary>>,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BankStaffState())

    /** What the tab draws. */
    val state: StateFlow<BankStaffState> = mutableState.asStateFlow()

    /**
     * The Verwaltung's direct booking, as one object rather than six methods.
     *
     * Grouped the way the Lager's Sammel-Ausbuchen is, and for the same reason: one interaction,
     * and a view model that already carries its share of functions.
     */
    val directBooking: DirectBookingActions = DirectBookingActions()

    /**
     * Opening, editing and sending a direct booking (design ch. 12 artboard 9).
     *
     * An inner class so it reaches the same state the rest of the view model writes.
     */
    inner class DirectBookingActions {
        /**
         * Opens the sheet on one account.
         *
         * @param accountId which account it books onto, or `null` to let the sheet ask.
         */
        fun open(accountId: String? = null) {
            mutableState.value =
                mutableState.value.copy(direct = DirectBookingState(accountId = accountId))
            loadFeeRate()
        }

        /**
         * Reads the org-wide transfer-fee rate for the preview.
         *
         * A failure leaves the rate `null`, which shows **no** fee block — the same degradation the
         * web chose. Saying nothing is right here: an invented rate would be a figure the member
         * could act on, and the server computes the binding one anyway.
         */
        private fun loadFeeRate() {
            viewModelScope.launch {
                (source.transferFeeRate() as? ApiResult.Success)?.let { result ->
                    mutableState.value =
                        mutableState.value.copy(
                            direct = mutableState.value.direct?.copy(feeRate = result.value.value),
                        )
                }
            }
        }

        /** Closes it. */
        fun close() {
            mutableState.value = mutableState.value.copy(direct = null)
        }

        /**
         * Acknowledges the notice that the last attempt was filed rather than booked.
         *
         * Its own action rather than a timeout: the notice says the money has **not** moved, and a
         * message that disappears on its own is one a member can miss entirely.
         */
        fun acknowledgeFiled() {
            mutableState.value = mutableState.value.copy(filed = false)
        }

        /**
         * Changes what the sheet holds.
         *
         * @param edit what to change.
         */
        fun edit(edit: (DirectBookingState) -> DirectBookingState) {
            val open = mutableState.value.direct ?: return
            mutableState.value = mutableState.value.copy(direct = edit(open).copy(error = null))
        }

        /**
         * Sends it.
         *
         * The queue and the dashboard are re-read afterwards rather than patched: a direct booking
         * moves a balance, and the totals band above it would otherwise keep the old figure.
         *
         * @param balance the source account's balance, for the coverage check.
         */
        fun confirm(balance: java.math.BigDecimal?) {
            val open = mutableState.value.direct ?: return
            val account = open.accountId
            val holder = open.holderId
            if (account == null || holder == null || !open.submittable(balance)) {
                return
            }
            mutableState.value =
                mutableState.value.copy(direct = open.copy(saving = true, error = null))
            viewModelScope.launch {
                val booking =
                    DirectBooking(
                        kind = open.kind,
                        accountId = account,
                        amount = open.amount,
                        holderId = holder,
                        note = open.note,
                        destinationAccountId = open.destinationAccountId,
                        destinationHolderId = open.destinationHolderId,
                        feeInclusive = open.feeInclusive,
                    )
                when (val result = source.bookDirectly(booking)) {
                    is ApiResult.Success -> {
                        mutableState.value =
                            mutableState.value.copy(
                                direct = null,
                                filed = result.value == BankDirectOutcome.REQUEST_FILED,
                            )
                        reload(keepContent = true)
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "the direct booking was refused: ${result.error}" }
                        mutableState.value =
                            mutableState.value.copy(
                                direct = open.copy(saving = false, error = result.error),
                            )
                    }
                }
            }
        }
    }

    private var loadedOnce = false

    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.ORGUNIT_BANK)) { _ ->
            if (loadedOnce) {
                reload(keepContent = true)
            }
        }
    }

    /** Loads the dashboard, the first time the scope is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepContent = false)
    }

    /** Re-reads it, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepContent = true)
    }

    /**
     * Reads the dashboard, then the two things the rows are annotated from.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            when (val dashboard = source.staffDashboard()) {
                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "staff dashboard could not be read: ${dashboard.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = BankPhase.Failed(dashboard.error),
                            refreshing = false,
                        )
                }

                is ApiResult.Success -> {
                    val counts = countOpenRequests()
                    val viewable = readViewableIds()
                    mutableState.value =
                        BankStaffState(
                            rows =
                                dashboard.value.accounts.map { account ->
                                    BankStaffRow(
                                        account = account,
                                        openRequests = counts.perAccount[account.id] ?: 0,
                                        // Unknown means the member read failed; marking every row
                                        // as reached-by-office would be a louder claim than the app
                                        // can support, so nothing is marked.
                                        viewable = viewable == null || account.id in viewable,
                                    )
                                },
                            totals = dashboard.value.totals,
                            management = dashboard.value.management,
                            openRequestTotal = counts.total,
                            countsPartial = counts.partial,
                            phase = BankPhase.Ready,
                            queue = counts.rows,
                            holders = mutableState.value.holders,
                            // Carried across the rebuild, like the holders above. A direct
                            // booking triggers this reload itself, so a fresh object would
                            // drop the very notice that write raised — and the notice is the
                            // only thing telling the member the balance below it is correct
                            // and their withdrawal is merely filed.
                            filed = mutableState.value.filed,
                        )
                    readHolders()
                }
            }
        }
    }

    /**
     * What the per-account counters came to, and whether they are complete.
     *
     * @property perAccount how many undecided requests stand against each account.
     * @property total how many the queue holds altogether.
     * @property partial whether the walk stopped before the end of the queue.
     * @property rows the requests themselves, which the Anträge tab lists.
     */
    private data class OpenRequestCounts(
        val perAccount: Map<String, Int>,
        val total: Int,
        val partial: Boolean,
        val rows: List<BankBookingRequest> = emptyList(),
    )

    /**
     * Walks the pending queue and counts it by account.
     *
     * Bounded on purpose. A queue deep enough to need more than [MAX_COUNTED_PAGES] pages says
     * something has gone badly wrong upstream, and spending that many round trips to decorate a
     * dashboard would be the wrong trade — but a truncated count is reported as truncated rather
     * than shown as if it were the whole (ADR-0104).
     *
     * @return the counts, and whether they are complete.
     */
    private suspend fun countOpenRequests(): OpenRequestCounts {
        val perAccount = mutableMapOf<String, Int>()
        val rows = mutableListOf<BankBookingRequest>()
        var total = 0
        var page = 0
        var complete = false
        while (!complete && page < MAX_COUNTED_PAGES) {
            when (val result = source.requestQueue(page = page)) {
                is ApiResult.Failure -> {
                    // The dashboard still renders. A decoration that could not be read must not
                    // take the screen down with it — but it must not pretend to be complete.
                    KrtLog.w(LOG_TAG) { "request queue unavailable: ${result.error}" }
                    return OpenRequestCounts(perAccount, total, partial = true, rows = rows)
                }

                is ApiResult.Success -> {
                    result.value.requests.forEach { request ->
                        total++
                        rows.add(request)
                        request.accountId?.let { perAccount[it] = (perAccount[it] ?: 0) + 1 }
                    }
                    complete = !result.value.hasMore
                    page++
                }
            }
        }
        if (!complete) {
            KrtLog.w(LOG_TAG) { "queue walk stopped at $MAX_COUNTED_PAGES pages; counts are a floor" }
        }
        return OpenRequestCounts(perAccount, total, partial = !complete, rows = rows)
    }

    /**
     * Which accounts this caller could see without their staff role.
     *
     * @return the ids, or `null` when the member read failed — in which case nothing is marked.
     */
    private suspend fun readViewableIds(): Set<String>? =
        when (val result = memberAccounts()) {
            is ApiResult.Success -> {
                result.value.map { it.id }.toSet()
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "member account list unavailable: ${result.error}" }
                null
            }
        }

    /**
     * Reads the unit's holders, which the confirmation sheet has to offer.
     *
     * Its failure is not the scope's failure: the dashboard and the queue still render, and a
     * confirmation simply cannot be submitted until the list arrives.
     */
    private fun readHolders() {
        viewModelScope.launch {
            when (val result = source.holders()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(holders = result.value.filter { it.active })
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "holders unavailable: ${result.error}" }
                }
            }
        }
    }

    /**
     * Opens the confirmation sheet on a request.
     *
     * @param request the request to book.
     */
    fun onConfirmOpen(request: BankBookingRequest) {
        mutableState.value = mutableState.value.copy(confirming = BankConfirmState(request))
    }

    /** Closes the confirmation sheet, discarding what was filled in. */
    fun onConfirmDismiss() {
        mutableState.value = mutableState.value.copy(confirming = null)
    }

    /**
     * Applies one edit to the open confirmation.
     *
     * @param change what to change about it.
     */
    fun onConfirmChanged(change: (BankConfirmState) -> BankConfirmState) {
        val open = mutableState.value.confirming ?: return
        mutableState.value = mutableState.value.copy(confirming = change(open).copy(error = null))
    }

    /** Sends the open confirmation. */
    fun onConfirmSubmit() {
        val open = mutableState.value.confirming ?: return
        val holderId = open.holderId ?: return
        mutableState.value = mutableState.value.copy(confirming = open.copy(saving = true))
        viewModelScope.launch {
            val result =
                source.confirmRequest(
                    BankConfirmation(
                        requestId = open.request.id,
                        version = open.request.version,
                        holderId = holderId,
                        destinationHolderId = open.destinationHolderId,
                        ownerApprovalConfirmed = open.approvalAttested,
                        staffNote = open.staffNote.takeIf { it.isNotBlank() },
                    ),
                )
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(confirming = null)
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "confirmation refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            confirming =
                                mutableState.value.confirming?.copy(
                                    saving = false,
                                    error = result.error,
                                ),
                        )
                }
            }
        }
    }

    /**
     * Opens the refusal dialog on a request.
     *
     * @param request the request to refuse.
     */
    fun onRejectOpen(request: BankBookingRequest) {
        mutableState.value = mutableState.value.copy(rejecting = BankRejectState(request))
    }

    /** Closes the refusal dialog. */
    fun onRejectDismiss() {
        mutableState.value = mutableState.value.copy(rejecting = null)
    }

    /**
     * Types the refusal's reason.
     *
     * @param reason what to say.
     */
    fun onRejectReason(reason: String) {
        val open = mutableState.value.rejecting ?: return
        mutableState.value = mutableState.value.copy(rejecting = open.copy(reason = reason, error = null))
    }

    /** Sends the refusal. */
    fun onRejectSubmit() {
        val open = mutableState.value.rejecting ?: return
        if (!open.submittable) {
            return
        }
        mutableState.value = mutableState.value.copy(rejecting = open.copy(saving = true))
        viewModelScope.launch {
            val result =
                source.rejectRequest(open.request.id, open.reason.trim(), open.request.version)
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(rejecting = null)
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "refusal refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            rejecting =
                                mutableState.value.rejecting?.copy(
                                    saving = false,
                                    error = result.error,
                                ),
                        )
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"

        /** How many queue pages the counter walks before it settles for a floor. */
        const val MAX_COUNTED_PAGES = 20
    }
}

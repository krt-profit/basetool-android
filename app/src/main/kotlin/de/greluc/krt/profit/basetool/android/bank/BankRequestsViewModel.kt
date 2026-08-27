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
import de.greluc.krt.profit.basetool.android.core.data.BankRequestDraft
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestSource
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankTransferTarget
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One row of the Anträge tab, with the two facts the row cannot work out for itself.
 *
 * @property request the request.
 * @property mine whether the caller raised it, which decides between the requester's actions
 *   (edit, withdraw) and the holder's one (approve).
 * @property actionable whether the caller may grant or revoke the owner approval on it. True only
 *   for a request the server returned on the "Fremde Anträge" read, because that read *is* the
 *   authority on who is responsible for which account — the app works out no grant of its own.
 */
data class BankRequestRow(
    val request: BankBookingRequest,
    val mine: Boolean,
    val actionable: Boolean,
)

/**
 * What the request sheet holds while it is open.
 *
 * @property editing the request being corrected, or `null` when a new one is being raised. The
 *   server refuses an edit of anything but the caller's own pending, unapproved request, and it
 *   refuses a change of account or kind — hence both are locked while this is set.
 * @property kind which movement.
 * @property accountId the account the money moves on.
 * @property targetAccountId where a transfer goes.
 * @property amount the amount as typed, never parsed for display.
 * @property note the purpose.
 * @property saving whether the write is in flight.
 * @property error what the last attempt refused with.
 */
data class BankRequestDraftState(
    val editing: BankBookingRequest? = null,
    val kind: BankRequestKind = BankRequestKind.DEPOSIT,
    val accountId: String? = null,
    val targetAccountId: String? = null,
    val amount: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /** Whether the form holds enough for the server to accept it. */
    val submittable: Boolean
        get() =
            accountId != null &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (kind != BankRequestKind.TRANSFER || targetAccountId != null)
}

/**
 * The Anträge tab.
 *
 * @property rows every request the caller may see, own and foreign, newest first.
 * @property accounts the accounts the request sheet may pick from.
 * @property targets where a transfer may go.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property draft the open sheet, or `null`.
 * @property online whether a write may be sent at all.
 * @property busyId the request a grant, revoke or withdrawal is currently in flight for.
 */
data class BankRequestsState(
    val rows: List<BankRequestRow> = emptyList(),
    val accounts: List<BankAccountSummary> = emptyList(),
    val targets: List<BankTransferTarget> = emptyList(),
    val phase: BankPhase = BankPhase.Loading,
    val refreshing: Boolean = false,
    val draft: BankRequestDraftState? = null,
    val online: Boolean = true,
    val busyId: String? = null,
) {
    /**
     * What the tab badge counts.
     *
     * Undecided requests only. A confirmed or rejected one is a record rather than a task, and
     * counting it would leave a badge on the tab that nothing the member does can clear.
     */
    val pendingCount: Int
        get() = rows.count { it.request.status == BankRequestStatus.PENDING }
}

/**
 * Drives the Anträge tab and the request sheet.
 *
 * **Own and foreign requests are read as two calls and shown as one list.** The server decides who
 * may approve what, so the split is what tells the tab which rows carry an approve action — a
 * single merged read would force the app to reimplement the grant rules to work that out, and it
 * would get them wrong the first time an account changed hands.
 *
 * @property source the request calls.
 * @property accountSource the accounts the sheet picks from, which are the same ones the Konten
 *   tab shows.
 * @property liveSync the peer bridge, or `null`.
 */
class BankRequestsViewModel(
    private val source: BankRequestSource,
    private val accountSource: suspend () -> ApiResult<List<BankAccountSummary>>,
    connectivity: Connectivity,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BankRequestsState())

    /** What the tab draws. */
    val state: StateFlow<BankRequestsState> = mutableState.asStateFlow()

    private var loadedOnce = false

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
        observeLiveSync(liveSync, setOf(LiveSyncTopic.ORGUNIT_BANK)) { _ ->
            // Both sections of this room end in the same read: a balance moving means a request
            // was booked, and a settings change can move a limit that decides who must approve.
            if (loadedOnce) {
                reload(keepContent = true)
            }
        }
    }

    /** Loads the requests, the first time the tab is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepContent = false)
    }

    /** Re-reads the requests, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepContent = true)
    }

    /**
     * Reads both request lists and the accounts the sheet needs.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            val own = source.ownRequests()
            val foreign = source.foreignRequests()
            val failure = (own as? ApiResult.Failure) ?: (foreign as? ApiResult.Failure)
            if (failure != null) {
                KrtLog.w(LOG_TAG) { "bank requests could not be read: ${failure.error}" }
                mutableState.value =
                    mutableState.value.copy(
                        phase = BankPhase.Failed(failure.error),
                        refreshing = false,
                    )
                return@launch
            }
            val ownRows =
                (own as ApiResult.Success).value.map {
                    BankRequestRow(request = it, mine = true, actionable = false)
                }
            // A request can be on both reads at once — one raised by a holder against their own
            // account. It is theirs, and nobody approves their own, so the own read wins.
            val ownIds = ownRows.map { it.request.id }.toSet()
            val foreignRows =
                (foreign as ApiResult.Success).value
                    .filterNot { it.id in ownIds }
                    .map { BankRequestRow(request = it, mine = false, actionable = true) }
            mutableState.value =
                mutableState.value.copy(
                    rows = (ownRows + foreignRows).sortedByDescending { it.request.createdAt },
                    phase = BankPhase.Ready,
                    refreshing = false,
                )
            readAccounts()
        }
    }

    /**
     * Reads what the sheet's two pickers offer.
     *
     * A failure here is not the tab's failure: the list of requests loaded, and an empty picker is
     * a sheet that cannot be submitted rather than a screen that cannot be read.
     */
    private fun readAccounts() {
        viewModelScope.launch {
            val accounts = accountSource()
            if (accounts is ApiResult.Success) {
                mutableState.value =
                    mutableState.value.copy(
                        accounts = accounts.value,
                        // A sheet opened straight from the CTA gets here before the accounts do,
                        // and would otherwise sit with an empty, unsubmittable picker until the
                        // member noticed and chose one by hand.
                        draft =
                            mutableState.value.draft?.let { open ->
                                if (open.accountId == null) {
                                    open.copy(accountId = accounts.value.firstOrNull()?.id)
                                } else {
                                    open
                                }
                            },
                    )
            }
            when (val targets = source.transferTargets()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(targets = targets.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "transfer targets unavailable: ${targets.error}" }
                }
            }
        }
    }

    /** Opens the sheet on a blank request. */
    fun onCompose() {
        mutableState.value =
            mutableState.value.copy(
                draft = BankRequestDraftState(accountId = mutableState.value.accounts.firstOrNull()?.id),
            )
    }

    /**
     * Opens the sheet on an existing request to correct it.
     *
     * @param request the caller's own pending, unapproved request.
     */
    fun onEdit(request: BankBookingRequest) {
        mutableState.value =
            mutableState.value.copy(
                draft =
                    BankRequestDraftState(
                        editing = request,
                        kind = request.kind ?: BankRequestKind.DEPOSIT,
                        accountId = request.accountId,
                        targetAccountId = request.targetAccountId,
                        amount = request.amount.forEditing(),
                        note = request.note.orEmpty(),
                    ),
            )
    }

    /** Closes the sheet, discarding what was typed. */
    fun onDismissSheet() {
        mutableState.value = mutableState.value.copy(draft = null)
    }

    /**
     * Applies one edit to the open sheet.
     *
     * @param change what to change about it.
     */
    fun onDraftChanged(change: (BankRequestDraftState) -> BankRequestDraftState) {
        val draft = mutableState.value.draft ?: return
        mutableState.value = mutableState.value.copy(draft = change(draft).copy(error = null))
    }

    /** Sends the open sheet, as a new request or as a correction of one. */
    fun onSubmit() {
        val draft = mutableState.value.draft ?: return
        val existing = draft.editing
        // A new request needs an account; a correction already has one the server will not let
        // anyone change, so the picker is absent from that sheet and nothing to check here.
        if (existing == null && draft.accountId == null) {
            return
        }
        mutableState.value = mutableState.value.copy(draft = draft.copy(saving = true))
        viewModelScope.launch {
            val result =
                if (existing != null) {
                    source.updateRequest(
                        id = existing.id,
                        version = existing.version,
                        amount = draft.amount,
                        note = draft.note.takeIf { it.isNotBlank() },
                        targetAccountId = draft.targetAccountId,
                    )
                } else {
                    source.createRequest(
                        BankRequestDraft(
                            accountId = requireNotNull(draft.accountId),
                            kind = draft.kind,
                            amount = draft.amount,
                            targetAccountId = draft.targetAccountId,
                            note = draft.note.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(draft = null)
                    announce()
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "request write refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            draft = mutableState.value.draft?.copy(saving = false, error = result.error),
                        )
                }
            }
        }
    }

    /**
     * Withdraws one of the caller's own requests.
     *
     * @param request the request to withdraw.
     */
    fun onWithdraw(request: BankBookingRequest) {
        write(request.id) { source.cancelRequest(request.id, request.version) }
    }

    /**
     * Grants or revokes the owner approval on somebody else's request.
     *
     * @param request the request.
     * @param granted whether to grant it.
     */
    fun onSetApproval(
        request: BankBookingRequest,
        granted: Boolean,
    ) {
        write(request.id) { source.setOwnerApproval(request.id, granted) }
    }

    /**
     * Runs a single-request write and re-reads the tab behind it.
     *
     * @param id the request the write is against, so its row can show that it is busy.
     * @param call the write.
     */
    private fun write(
        id: String,
        call: suspend () -> ApiResult<BankBookingRequest>,
    ) {
        if (!mutableState.value.online || mutableState.value.busyId != null) {
            return
        }
        mutableState.value = mutableState.value.copy(busyId = id)
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(busyId = null)
                    announce()
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "request action refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            busyId = null,
                            phase = BankPhase.Failed(result.error),
                        )
                }
            }
        }
    }

    /**
     * Tells the peers a request moved.
     *
     * Nothing on the server publishes into this room, so this announcement is the only reason a
     * second member's screen updates without them pulling to refresh.
     */
    private fun announce() {
        publishLiveSync(
            liveSync,
            LiveSyncTopic.ORGUNIT_BANK,
            LiveSyncSections.ORGUNIT_BANK_OVERVIEW,
        )
    }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"
    }
}

/**
 * The amount as a field can sensibly hold it.
 *
 * The server renders money at its storage scale, so a request of 120.000 aUEC comes back as
 * `120000.0000`. Grouping it is wrong here — an input with separators fights the caret — but so is
 * showing four zeros nobody typed, which is what a device run rejected.
 *
 * @return the plain number without trailing zeros, or an empty field when there is nothing to edit.
 */
private fun String?.forEditing(): String =
    this?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: orEmpty()

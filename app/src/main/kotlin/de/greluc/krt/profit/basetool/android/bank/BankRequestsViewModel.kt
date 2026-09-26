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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the Anträge tab, with the two facts the row cannot work out for itself.
 *
 * @property request the request.
 * @property mine whether the caller raised it, which selects the requester's actions (edit,
 *   withdraw) over the holder's (approve).
 * @property actionable whether the caller may grant or revoke the owner approval; true only for a
 *   request returned by the "Fremde Anträge" read.
 */
data class BankRequestRow(
    val request: BankBookingRequest,
    val mine: Boolean,
    val actionable: Boolean,
)

/**
 * What the request sheet holds while it is open.
 *
 * @property editing the request being corrected, or `null` for a new one; while set, account and kind
 *   are locked because the server refuses changing them.
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
 * Own and foreign requests are read as two calls and shown as one list; the foreign read decides
 * which rows carry an approve action, so the app never derives grant rules itself.
 *
 * @property source the request calls.
 * @property accountSource the accounts the sheet picks from, the same the Konten tab shows.
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
                mutableState.update { it.copy(online = online) }
            }
        }
        observeLiveSync(liveSync, setOf(LiveSyncTopic.ORGUNIT_BANK)) { _ ->
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
        mutableState.update { it.copy(refreshing = true) }
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
            mutableState.update { it.copy(phase = BankPhase.Loading) }
        }
        viewModelScope.launch {
            val own = source.ownRequests()
            val foreign = source.foreignRequests()
            val failure = (own as? ApiResult.Failure) ?: (foreign as? ApiResult.Failure)
            if (failure != null) {
                KrtLog.w(LOG_TAG) { "bank requests could not be read: ${failure.error}" }
                mutableState.update {
                    it.copy(
                        phase = BankPhase.Failed(failure.error),
                        refreshing = false,
                    )
                }
                return@launch
            }
            val ownRows =
                (own as ApiResult.Success).value.map {
                    BankRequestRow(request = it, mine = true, actionable = false)
                }
            val ownIds = ownRows.map { it.request.id }.toSet()
            val foreignRows =
                (foreign as ApiResult.Success).value
                    .filterNot { it.id in ownIds }
                    .map { BankRequestRow(request = it, mine = false, actionable = true) }
            mutableState.update { state ->
                state.copy(
                    rows = (ownRows + foreignRows).sortedByDescending { it.request.createdAt },
                    phase = BankPhase.Ready,
                    refreshing = false,
                )
            }
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
                mutableState.update { state ->
                    state.copy(
                        accounts = accounts.value,
                        draft =
                            state.draft?.let { open ->
                                if (open.accountId == null) {
                                    open.copy(accountId = accounts.value.firstOrNull()?.id)
                                } else {
                                    open
                                }
                            },
                    )
                }
            }
            when (val targets = source.transferTargets()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(targets = targets.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "transfer targets unavailable: ${targets.error}" }
                }
            }
        }
    }

    /** Opens the sheet on a blank request. */
    fun onCompose() {
        mutableState.update { state ->
            state.copy(
                draft = BankRequestDraftState(accountId = state.accounts.firstOrNull()?.id),
            )
        }
    }

    /**
     * Opens the sheet on an existing request to correct it.
     *
     * @param request the caller's own pending, unapproved request.
     */
    fun onEdit(request: BankBookingRequest) {
        mutableState.update {
            it.copy(
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
    }

    /** Closes the sheet, discarding what was typed. */
    fun onDismissSheet() {
        mutableState.update { it.copy(draft = null) }
    }

    /**
     * Applies one edit to the open sheet.
     *
     * @param change what to change about it.
     */
    fun onDraftChanged(change: (BankRequestDraftState) -> BankRequestDraftState) {
        val draft = mutableState.value.draft ?: return
        mutableState.update { it.copy(draft = change(draft).copy(error = null)) }
    }

    /** Sends the open sheet, as a new request or as a correction of one. */
    fun onSubmit() {
        val draft = mutableState.value.draft ?: return
        val existing = draft.editing
        if (existing == null && draft.accountId == null) {
            return
        }
        mutableState.update { it.copy(draft = draft.copy(saving = true)) }
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
                    mutableState.update { it.copy(draft = null) }
                    announce()
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "request write refused: ${result.error}" }
                    mutableState.update { state ->
                        state.copy(
                            draft = state.draft?.copy(saving = false, error = result.error),
                        )
                    }
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
        mutableState.update { it.copy(busyId = id) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(busyId = null) }
                    announce()
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "request action refused: ${result.error}" }
                    mutableState.update {
                        it.copy(
                            busyId = null,
                            phase = BankPhase.Failed(result.error),
                        )
                    }
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
 * The amount as an input field holds it: a plain number without grouping or trailing zeros.
 *
 * @return the plain number without trailing zeros, or an empty field when there is nothing to edit.
 */
private fun String?.forEditing(): String =
    this?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: orEmpty()

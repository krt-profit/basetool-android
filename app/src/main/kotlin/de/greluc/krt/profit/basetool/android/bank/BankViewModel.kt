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
import de.greluc.krt.profit.basetool.android.core.data.BankAccountDetail
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSettings
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
import de.greluc.krt.profit.basetool.android.core.data.BankBookingPage
import de.greluc.krt.profit.basetool.android.core.data.BankRepository
import de.greluc.krt.profit.basetool.android.core.data.BankReversalSource
import de.greluc.krt.profit.basetool.android.core.data.BankSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccountSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far a bank read has got. */
sealed interface BankPhase {
    /** In flight. */
    data object Loading : BankPhase

    /** It arrived; it may be empty, which is a result. */
    data object Ready : BankPhase

    /**
     * It did not.
     *
     * @property error what went wrong; `Forbidden` is the ordinary answer for an account the
     *   caller holds no grant for.
     */
    data class Failed(
        val error: ApiError,
    ) : BankPhase
}

/**
 * The Konten list.
 *
 * @property accounts the accounts the caller may see
 * @property phase how far the read has got
 * @property refreshing whether a pull-to-refresh is running over content already on screen
 */
data class BankAccountsState(
    val accounts: List<BankAccountSummary> = emptyList(),
    val phase: BankPhase = BankPhase.Loading,
    val refreshing: Boolean = false,
    /**
     * Seconds until the automatic retry, or `null` when none is pending.
     *
     * Only ever set while the **first** load has failed with a retryable status. A screen that
     * already has accounts on it keeps them and gets its banner instead — replacing loaded content
     * with a countdown would take away what the member was reading to tell them something they can
     * see without losing their place (design chapter 14).
     */
    val retryIn: Int? = null,
)

/**
 * One account with its ledger.
 *
 * @property accountId which account, known before anything has loaded
 * @property account the account once it arrives
 * @property bookings the ledger lines loaded so far, newest first
 * @property bookingTotal how many lines the ledger holds in total
 * @property phase how far the account read has got
 * @property page the last ledger page that arrived
 * @property hasMore whether the ledger has another page
 * @property loadingMore whether that page is in flight
 * @property refreshing whether a pull-to-refresh is running
 * @property reversal the booking whose Storno is being confirmed, or `null`
 * @property reversalNote what to record about it
 */
data class BankAccountState(
    val accountId: String,
    val account: BankAccountDetail? = null,
    val bookings: List<BankBooking> = emptyList(),
    val bookingTotal: Long = 0,
    val phase: BankPhase = BankPhase.Loading,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val settings: BankAccountSettings? = null,
    val settingsOpen: Boolean = false,
    val targetDraft: String? = null,
    val saving: Boolean = false,
    val online: Boolean = true,
    val error: ApiError? = null,
    val reversal: BankBooking? = null,
    val reversalNote: String = "",
) {
    /** Whether a settings write may be sent at all. */
    val writable: Boolean
        get() = online && !saving
}

/**
 * Drives the Konten list.
 *
 * @property source where the accounts come from
 */
class BankViewModel(
    private val source: BankSource,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BankAccountsState())

    /** What the screen draws. */
    val state: StateFlow<BankAccountsState> = mutableState.asStateFlow()

    init {
        observeLiveSync(liveSync, setOf(LiveSyncTopic.ORGUNIT_BANK)) { _ ->
            // Every section of this room ends in the same read, so the keys are not inspected:
            // a balance moving and a setting changing both mean the overview is out of date.
            if (loadedOnce) {
                reload(keepContent = true)
            }
        }
    }

    private var loadedOnce = false

    /** Loads the accounts, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepContent = false)
    }

    /**
     * The chapter-14 retry ladder for this screen's first load.
     *
     * Shared rather than re-derived: the conditions under which a countdown is right are the same
     * on every screen, and the copy this class used to hold is what [FirstLoadRetry] was extracted
     * from.
     */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.value = mutableState.value.copy(retryIn = left) },
            onRetry = { reload(keepContent = false) },
        )

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    /** Re-reads the accounts, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepContent = true)
    }

    /**
     * Reads the accounts.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            when (val result = source.balances()) {
                is ApiResult.Success -> {
                    retry.onSuccess()
                    mutableState.value =
                        BankAccountsState(accounts = result.value, phase = BankPhase.Ready)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "bank accounts could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = BankPhase.Failed(result.error),
                            refreshing = false,
                        )
                    retry.onFailure(result.error, hasContent = keepContent)
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"
    }
}

/**
 * Drives one account and its ledger.
 *
 * **The account and its first ledger page are read together and fail together.** Both carry the
 * same `canSee` gate, so a split state would model a case the server cannot produce — and a balance
 * shown over a missing ledger reads as an account with no history rather than one that failed to
 * load.
 *
 * @property source where the account comes from
 * @property accountId which account to load
 */
class BankAccountViewModel(
    private val source: BankSource,
    connectivity: Connectivity,
    private val accountId: String,
    private val liveSync: LiveSyncSource? = null,
    private val reversalSource: BankReversalSource? = null,
    private val staffSource: BankStaffAccountSource? = null,
    private val throughTheOffice: () -> Boolean = { false },
) : ViewModel() {
    /**
     * Whether to read this account through the office rather than as a member.
     *
     * A bank employee holding no view grant gets **403** on the member path for an account they are
     * nevertheless responsible for — found on a device, where opening an account from the
     * Verwaltung scope answered „Dieses Konto ist für dich nicht einsehbar." The office path answers
     * for every account of the organisation, closed ones included.
     */
    private val viaOffice: Boolean
        get() = staffSource != null && throughTheOffice()
    private val mutableState = MutableStateFlow(BankAccountState(accountId = accountId))

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
        observeLiveSync(
            liveSync,
            setOf(LiveSyncTopic.bankAccount(accountId), LiveSyncTopic.ORGUNIT_BANK),
        ) { _ ->
            // A booking lands in the account's own room, a settings change in the org-unit one,
            // and the screen shows both — so either re-reads both, in place.
            reload(keepContent = true)
            readSettings()
        }
    }

    /** What the screen draws. */
    val state: StateFlow<BankAccountState> = mutableState.asStateFlow()

    /** Loads the account and its first ledger page. */
    fun load() {
        reload(keepContent = false)
        readSettings()
    }

    /** Re-reads both, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
        readSettings()
    }

    /**
     * Reads what the caller may change about this account.
     *
     * A failure is not surfaced: the account and its ledger are the screen's subject, and losing
     * the settings costs the holder their controls, not the member their page. The controls then
     * stay away, which is the safe direction — the flags default to "may not".
     */
    private fun readSettings() {
        viewModelScope.launch {
            when (val result = source.settings(accountId)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(settings = result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the account settings could not be read: ${result.error}" }
                }
            }
        }
    }

    /** Opens the settings sheet. */
    fun onOpenSettings() {
        val current = mutableState.value
        current.settings?.let {
            mutableState.value =
                current.copy(
                    settingsOpen = true,
                    // The wire carries `250000.0000`, and the field takes digits.
                    targetDraft = it.balanceTarget?.substringBefore('.')?.filter(Char::isDigit).orEmpty(),
                    error = null,
                )
        }
    }

    /** Closes it, discarding what was typed. */
    fun onDismissSettings() {
        mutableState.value = mutableState.value.copy(settingsOpen = false, targetDraft = null, error = null)
    }

    /**
     * Sets the target as typed.
     *
     * @param value what the member typed, unparsed.
     */
    fun onTargetChanged(value: String) {
        mutableState.value =
            mutableState.value.copy(targetDraft = value.filter(Char::isDigit), error = null)
    }

    /**
     * Saves the target.
     *
     * An emptied field **clears** the target rather than setting it to zero: a target of nothing is
     * a different instruction from having none, and the screen never offers the first.
     */
    fun onSaveTarget() {
        val current = mutableState.value
        val settings = current.settings
        if (settings == null || !settings.canSetTarget || !current.writable) {
            return
        }
        write { source.setBalanceTarget(accountId, current.targetDraft?.takeIf { it.isNotEmpty() }, settings.version) }
    }

    /**
     * Grants or revokes one role bucket's view.
     *
     * @param roleCode the bucket.
     */
    fun onToggleRole(roleCode: String) {
        val current = mutableState.value
        val settings = current.settings
        if (settings == null || !settings.canConfigureVisibility || !current.writable) {
            return
        }
        write { source.setRoleVisibility(accountId, roleCode, roleCode !in settings.grantedRoleCodes) }
    }

    /** Opens the account to every member of its org unit, or closes it again. */
    fun onToggleAllMembers() {
        val current = mutableState.value
        val settings = current.settings
        if (settings == null || !settings.canConfigureVisibility || !current.writable) {
            return
        }
        write { source.setAllMembersVisibility(accountId, !settings.allMembersGranted) }
    }

    /**
     * Runs one settings write and redraws from the answer.
     *
     * Every one of them answers with the whole snapshot — the version moves, and so does what the
     * caller may do next — so nothing here is patched.
     *
     * @param request the call.
     */
    private fun write(request: suspend () -> ApiResult<BankAccountSettings>) {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            settings = result.value,
                            targetDraft =
                                result.value.balanceTarget
                                    ?.substringBefore('.')
                                    ?.filter(Char::isDigit)
                                    .orEmpty(),
                            saving = false,
                            error = null,
                        )
                    // The settings region lives in the org-unit room, not the account's: it is what
                    // the overview renders, and a peer looking at the list is who needs to know.
                    publishLiveSync(
                        liveSync,
                        LiveSyncTopic.ORGUNIT_BANK,
                        LiveSyncSections.ORGUNIT_BANK_SETTINGS,
                    )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the account settings could not be written: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /** Appends the next ledger page. */
    fun onLoadMore() {
        val current = mutableState.value
        if (current.loadingMore || !current.hasMore || current.phase !is BankPhase.Ready) {
            return
        }
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = readLedger(current.page + 1)) {
                is ApiResult.Success -> {
                    val latest = mutableState.value
                    mutableState.value =
                        latest.copy(
                            bookings = latest.bookings + result.value.bookings,
                            bookingTotal = result.value.totalElements,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                }

                is ApiResult.Failure -> {
                    // The lines on screen stay: a failed continuation is not a reason to replace a
                    // working ledger with an error.
                    KrtLog.w(LOG_TAG) { "next ledger page failed: ${result.error}" }
                    mutableState.value = mutableState.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Reads the account, then its first ledger page.
     *
     * @param keepContent whether what is on screen survives until the answers arrive.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            when (val account = readAccount()) {
                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "bank account could not be read: ${account.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = BankPhase.Failed(account.error),
                            refreshing = false,
                        )
                }

                is ApiResult.Success -> {
                    loadLedger(account.value)
                }
            }
        }
    }

    /**
     * Reads the first ledger page and completes the screen.
     *
     * @param account the account already read.
     */
    private suspend fun loadLedger(account: BankAccountDetail) {
        when (val ledger = readLedger(0)) {
            is ApiResult.Success -> {
                mutableState.value =
                    mutableState.value.copy(
                        account = account,
                        bookings = ledger.value.bookings,
                        bookingTotal = ledger.value.totalElements,
                        page = ledger.value.page,
                        hasMore = ledger.value.hasMore,
                        phase = BankPhase.Ready,
                        loadingMore = false,
                        refreshing = false,
                    )
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "bank ledger could not be read: ${ledger.error}" }
                mutableState.value =
                    mutableState.value.copy(
                        phase = BankPhase.Failed(ledger.error),
                        refreshing = false,
                    )
            }
        }
    }

    /**
     * Asks to reverse one booking.
     *
     * Refused for a row that already carries a reversal — the server answers
     * `BANK_ALREADY_REVERSED` and the row says so, so offering it would be a button that cannot
     * work. Refused too without a transaction id, which is what the reversal addresses.
     *
     * @param booking which one.
     */
    fun onReverse(booking: BankBooking) {
        if (booking.isReversal || booking.transactionId == null) {
            return
        }
        mutableState.value = mutableState.value.copy(reversal = booking, reversalNote = "", error = null)
    }

    /** Closes the Storno confirmation without sending it. */
    fun onDismissReversal() {
        mutableState.value = mutableState.value.copy(reversal = null)
    }

    /**
     * Records what is to be noted on the counter-booking.
     *
     * @param note the text.
     */
    fun onReversalNote(note: String) {
        mutableState.value = mutableState.value.copy(reversalNote = note)
    }

    /** Sends the Storno the confirmation stands for. */
    fun onConfirmReversal() {
        val current = mutableState.value
        val transactionId = current.reversal?.transactionId
        val writer = reversalSource
        if (transactionId == null || writer == null || current.saving) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = writer.reverse(transactionId, current.reversalNote)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, reversal = null)
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "reversal refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Reads the account through whichever surface applies to this caller.
     *
     * @return the account, or the classified failure.
     */
    private suspend fun readAccount(): ApiResult<BankAccountDetail> =
        if (viaOffice) {
            requireNotNull(staffSource).staffAccount(accountId)
        } else {
            source.account(accountId)
        }

    /**
     * Reads one ledger page through whichever surface applies to this caller.
     *
     * @param page which page, zero-based.
     * @return the page, or the classified failure.
     */
    private suspend fun readLedger(page: Int): ApiResult<BankBookingPage> =
        if (viaOffice) {
            requireNotNull(staffSource).staffBookings(accountId, page, BankRepository.DEFAULT_PAGE_SIZE)
        } else {
            source.bookings(accountId, page = page)
        }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"
    }
}

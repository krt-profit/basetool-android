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
import de.greluc.krt.profit.basetool.android.core.data.BankGrant
import de.greluc.krt.profit.basetool.android.core.data.BankGrantSource
import de.greluc.krt.profit.basetool.android.core.data.BankGrantee
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankLifecycleSource
import de.greluc.krt.profit.basetool.android.core.data.BankManagedAccount
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which lifecycle decision is being confirmed.
 *
 * Each carries its own consequence, and none of them is destructive: closing is reversible,
 * deactivating a holder leaves their holdings withdrawable. That is why none asks the member to
 * type anything — the type-to-confirm hurdle is reserved for what cannot be undone.
 */
sealed interface BankLifecyclePrompt {
    /**
     * Close an account.
     *
     * @property account which one.
     */
    data class Close(
        val account: BankManagedAccount,
    ) : BankLifecyclePrompt

    /**
     * Reopen a closed account.
     *
     * @property account which one.
     */
    data class Reopen(
        val account: BankManagedAccount,
    ) : BankLifecyclePrompt

    /**
     * Rename an account.
     *
     * @property account which one.
     * @property name what to call it.
     */
    data class Rename(
        val account: BankManagedAccount,
        val name: String,
    ) : BankLifecyclePrompt

    /**
     * Open a new account.
     *
     * @property name what to call it.
     */
    data class Create(
        val name: String,
    ) : BankLifecyclePrompt

    /**
     * Turn a holder's activation on or off.
     *
     * @property holder which one.
     * @property active what to set it to.
     */
    data class HolderActivation(
        val holder: BankHolder,
        val active: Boolean,
    ) : BankLifecyclePrompt

    /**
     * A member's standing on an account is about to be removed.
     *
     * Its own prompt because removing the entry is what takes the member's **sight** of the account
     * away — `canSee` on the server is "a row exists" (REQ-BANK-009), which no checkbox on the card
     * says.
     *
     * @property grant whose standing is going.
     * @property sightSurvives whether they keep seeing the account regardless — true on the CARTEL
     *   account, which every KRT member sees by rule (REQ-BANK-037) and where the entry therefore
     *   only ever carried booking rights.
     */
    data class RevokeGrant(
        val grant: BankGrant,
        val sightSurvives: Boolean,
    ) : BankLifecyclePrompt
}

/**
 * The Verwaltung scope's Konten tab.
 *
 * @property accounts every account, newest read first.
 * @property holders the unit's holders, active and inactive.
 * @property expandedId which account row is open, or `null`.
 * @property prompt the confirmation currently on screen.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property saving whether a write is in flight.
 * @property error what the last write refused with.
 * @property grantAccountId which account's matrix the Grants tab is showing, or `null` before one
 *   is picked.
 * @property grants the standings on that account.
 * @property grantsLoading whether the matrix is being read.
 * @property granteeDraft the „+ Grant hinzufügen" sheet, or `null` while it is closed.
 * @property holderDraft the „+ Halter registrieren" sheet, or `null` while it is closed. Separate
 *   from [granteeDraft] because both can be reached from the Konten tab and neither may inherit the
 *   other's pick.
 */
data class BankLifecycleState(
    val accounts: List<BankManagedAccount> = emptyList(),
    val holders: List<BankHolder> = emptyList(),
    val expandedId: String? = null,
    val prompt: BankLifecyclePrompt? = null,
    val phase: BankPhase = BankPhase.Loading,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val grantAccountId: String? = null,
    val grants: List<BankGrant> = emptyList(),
    val grantsLoading: Boolean = false,
    val granteeDraft: BankGranteeDraft? = null,
    val holderDraft: BankGranteeDraft? = null,
)

/**
 * What the „+ Grant hinzufügen" sheet holds.
 *
 * @property query what has been typed into the member picker.
 * @property options the candidates the last search answered with.
 * @property moreOptions whether the roster holds members this page does not carry.
 * @property searching whether a search is in flight.
 * @property selected the member picked, or `null` before one is.
 * @property canDeposit whether the new grant may book money in.
 * @property canWithdraw whether it may book money out.
 * @property canTransfer whether it may move money to another account.
 */
data class BankGranteeDraft(
    val query: String = "",
    val options: List<BankGrantee> = emptyList(),
    val moreOptions: Boolean = false,
    val searching: Boolean = false,
    val selected: BankGrantee? = null,
    val canDeposit: Boolean = false,
    val canWithdraw: Boolean = false,
    val canTransfer: Boolean = false,
)

/**
 * Drives the account lifecycle and the holder register — design chapter 12, artboard 6.
 *
 * Separate from [BankStaffViewModel] because the two answer to different roles: the dashboard and
 * the queue are a bank employee's, everything here is Bank-Management's. Keeping them apart also
 * keeps a refused lifecycle write from putting the queue into a failure state.
 *
 * @property source the lifecycle calls.
 * @property staff the holder read, which the queue's confirmation sheet already needs.
 * @property grantSource the per-account grants matrix.
 * @property activeOrgUnitId which unit a new account is opened for — the caller's pinned context,
 *   which is what „Einheit (vorbelegt)" means. A caller who has pinned *all* units has no single
 *   answer, and the creation is then refused rather than guessed at.
 */
class BankLifecycleViewModel(
    private val source: BankLifecycleSource,
    private val staff: BankStaffSource,
    private val grantSource: BankGrantSource,
    private val activeOrgUnitId: () -> String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BankLifecycleState())

    /** What the tab draws. */
    val state: StateFlow<BankLifecycleState> = mutableState.asStateFlow()

    private var loadedOnce = false

    /** Loads the accounts and the holders, the first time the tab is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        reload(keepContent = false)
    }

    /** Re-reads them, keeping what is on screen while it runs. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        loadedOnce = true
        reload(keepContent = true)
    }

    /**
     * Opens or closes one account's action row.
     *
     * @param id the account, or `null` to close whatever is open.
     */
    fun onExpand(id: String?) {
        mutableState.value =
            mutableState.value.copy(expandedId = id.takeIf { it != mutableState.value.expandedId })
    }

    /**
     * Puts a confirmation on screen.
     *
     * @param prompt what is being confirmed.
     */
    fun onPrompt(prompt: BankLifecyclePrompt) {
        mutableState.value = mutableState.value.copy(prompt = prompt, error = null)
    }

    /** Takes it away again. */
    fun onDismissPrompt() {
        mutableState.value = mutableState.value.copy(prompt = null)
    }

    /**
     * Edits the name a rename or a creation carries.
     *
     * @param name what to call it.
     */
    fun onNameChanged(name: String) {
        val prompt = mutableState.value.prompt ?: return
        val updated =
            when (prompt) {
                is BankLifecyclePrompt.Rename -> prompt.copy(name = name)
                is BankLifecyclePrompt.Create -> prompt.copy(name = name)
                else -> return
            }
        mutableState.value = mutableState.value.copy(prompt = updated, error = null)
    }

    /** Carries out whatever is on screen. */
    fun onConfirmPrompt() {
        val prompt = mutableState.value.prompt ?: return
        if (mutableState.value.saving) {
            return
        }
        mutableState.value = mutableState.value.copy(saving = true)
        viewModelScope.launch {
            val result = carryOut(prompt)
            if (result == null) {
                mutableState.value = mutableState.value.copy(saving = false)
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, prompt = null, error = null)
                    reload(keepContent = true)
                    if (prompt is BankLifecyclePrompt.RevokeGrant) {
                        mutableState.value.grantAccountId?.let { readGrants(it) }
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "lifecycle write refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Sends the one call the prompt stands for.
     *
     * @param prompt what is being confirmed.
     * @return the answer, or `null` when the prompt cannot be sent as it stands.
     */
    private suspend fun carryOut(prompt: BankLifecyclePrompt): ApiResult<*>? =
        when (prompt) {
            is BankLifecyclePrompt.Close -> {
                source.setAccountOpen(prompt.account.id, open = false, version = prompt.account.version)
            }

            is BankLifecyclePrompt.Reopen -> {
                source.setAccountOpen(prompt.account.id, open = true, version = prompt.account.version)
            }

            is BankLifecyclePrompt.Rename -> {
                prompt.name.trim().takeIf { it.isNotBlank() }?.let {
                    source.renameAccount(prompt.account.id, it, prompt.account.version)
                }
            }

            is BankLifecyclePrompt.Create -> {
                val owner = activeOrgUnitId()
                prompt.name.trim().takeIf { it.isNotBlank() && owner != null }?.let {
                    source.createAccount(it, requireNotNull(owner))
                }
            }

            is BankLifecyclePrompt.HolderActivation -> {
                source.setHolderActive(prompt.holder.id, prompt.active, prompt.holder.version)
            }

            is BankLifecyclePrompt.RevokeGrant -> {
                grantSource.revokeGrant(prompt.grant.userId, prompt.grant.accountId)
            }
        }

    /**
     * Reads the accounts and the holders.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            when (val accounts = source.managedAccounts()) {
                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "account list could not be read: ${accounts.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = BankPhase.Failed(accounts.error),
                            refreshing = false,
                        )
                }

                is ApiResult.Success -> {
                    // The holder list failing is not the tab's failure: the accounts rendered, and
                    // a register that could not be read is a section that stays empty.
                    val holders = staff.holders()
                    mutableState.value =
                        mutableState.value.copy(
                            accounts = accounts.value,
                            holders = (holders as? ApiResult.Success)?.value.orEmpty(),
                            phase = BankPhase.Ready,
                            refreshing = false,
                        )
                }
            }
        }
    }

    /**
     * Shows one account's grants matrix.
     *
     * @param accountId which account, or `null` to show none.
     */
    fun onSelectGrantAccount(accountId: String?) {
        mutableState.value =
            mutableState.value.copy(grantAccountId = accountId, grants = emptyList())
        accountId?.let { readGrants(it) }
    }

    /**
     * Sets one member's three capabilities on the shown account.
     *
     * A grant whose three flags are all false is kept rather than deleted: it is the deliberate
     * "may see, may book nothing" case (REQ-BANK-009). Taking sight away is [onRevokeGrant].
     *
     * @param grant what the matrix now says.
     */
    fun onSetGrant(grant: BankGrant) {
        if (mutableState.value.saving) {
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = grantSource.setGrant(grant)) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(saving = false)
                    mutableState.value.grantAccountId?.let { readGrants(it) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "grant change refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Reads one account's matrix.
     *
     * @param accountId which account.
     */
    private fun readGrants(accountId: String) {
        mutableState.value = mutableState.value.copy(grantsLoading = true)
        viewModelScope.launch {
            when (val result = grantSource.grants(accountId)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(grants = result.value, grantsLoading = false)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "grants matrix unavailable: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(grantsLoading = false, error = result.error)
                }
            }
        }
    }

    /** Opens the „+ Halter registrieren" sheet and offers the first candidates unfiltered. */
    fun onAddHolder() {
        mutableState.value = mutableState.value.copy(holderDraft = BankGranteeDraft(), error = null)
        searchHolderCandidates("")
    }

    /** Closes it, discarding what was typed. */
    fun onDismissHolderDraft() {
        mutableState.value = mutableState.value.copy(holderDraft = null)
    }

    /**
     * Records what was typed into the holder picker and asks the server for candidates.
     *
     * @param query the new text.
     */
    fun onHolderQuery(query: String) {
        val draft = mutableState.value.holderDraft ?: return
        mutableState.value =
            mutableState.value.copy(holderDraft = draft.copy(query = query, selected = null))
        searchHolderCandidates(query)
    }

    /**
     * Picks the member to register as a holder.
     *
     * @param member who.
     */
    fun onHolderSelected(member: BankGrantee) {
        val draft = mutableState.value.holderDraft ?: return
        mutableState.value =
            mutableState.value.copy(
                holderDraft = draft.copy(selected = member, query = member.handle),
            )
    }

    /** Registers the picked member as a holder of the unit's custody. */
    fun onRegisterHolder() {
        val member = mutableState.value.holderDraft?.selected
        if (member == null || mutableState.value.saving) {
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = source.registerHolder(member.id)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, holderDraft = null)
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "holder registration refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Asks the server for members who could become holders.
     *
     * @param query the search text.
     */
    private fun searchHolderCandidates(query: String) {
        val draft = mutableState.value.holderDraft ?: return
        mutableState.value = mutableState.value.copy(holderDraft = draft.copy(searching = true))
        viewModelScope.launch {
            val result = grantSource.searchGrantees(query)
            val current = mutableState.value.holderDraft ?: return@launch
            if (current.query != query) {
                return@launch
            }
            mutableState.value =
                when (result) {
                    is ApiResult.Success -> {
                        mutableState.value.copy(
                            holderDraft =
                                current.copy(
                                    options = result.value.rows,
                                    moreOptions = result.value.more,
                                    searching = false,
                                ),
                        )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "holder candidate search failed: ${result.error}" }
                        mutableState.value.copy(
                            holderDraft = current.copy(searching = false),
                            error = result.error,
                        )
                    }
                }
        }
    }

    /** Opens the „+ Grant hinzufügen" sheet and offers the first candidates unfiltered. */
    fun onAddGrant() {
        mutableState.value = mutableState.value.copy(granteeDraft = BankGranteeDraft(), error = null)
        searchGrantees("")
    }

    /** Closes the sheet, discarding what was typed. */
    fun onDismissGrantDraft() {
        mutableState.value = mutableState.value.copy(granteeDraft = null)
    }

    /**
     * Records what was typed into the member picker and asks the server for candidates.
     *
     * @param query the new text.
     */
    fun onGranteeQuery(query: String) {
        val draft = mutableState.value.granteeDraft ?: return
        mutableState.value =
            mutableState.value.copy(granteeDraft = draft.copy(query = query, selected = null))
        searchGrantees(query)
    }

    /**
     * Picks a member out of the candidates.
     *
     * @param grantee who.
     */
    fun onGranteeSelected(grantee: BankGrantee) {
        val draft = mutableState.value.granteeDraft ?: return
        mutableState.value =
            mutableState.value.copy(
                granteeDraft = draft.copy(selected = grantee, query = grantee.handle),
            )
    }

    /**
     * Sets the flags the new grant will carry.
     *
     * All three may stay false: that is the deliberate „darf sehen, darf nichts buchen" entry.
     *
     * @param draft the sheet as it now stands.
     */
    fun onGrantDraftChanged(draft: BankGranteeDraft) {
        mutableState.value = mutableState.value.copy(granteeDraft = draft)
    }

    /**
     * Creates the grant the sheet describes.
     *
     * Refused when no member is picked or no account is showing — neither is guessable, and the
     * server would answer 404 for the second.
     */
    fun onCreateGrant() {
        val state = mutableState.value
        val member = state.granteeDraft?.selected
        val accountId = state.grantAccountId
        if (member == null || accountId == null || state.saving) {
            return
        }
        val draft = requireNotNull(state.granteeDraft)
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val result =
                grantSource.setGrant(
                    BankGrant(
                        userId = member.id,
                        handle = member.handle,
                        accountId = accountId,
                        canDeposit = draft.canDeposit,
                        canWithdraw = draft.canWithdraw,
                        canTransfer = draft.canTransfer,
                        version = 0,
                        exists = false,
                    ),
                )
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, granteeDraft = null)
                    readGrants(accountId)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "grant creation refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Asks the server for members matching what was typed.
     *
     * @param query the search text.
     */
    private fun searchGrantees(query: String) {
        val draft = mutableState.value.granteeDraft ?: return
        mutableState.value = mutableState.value.copy(granteeDraft = draft.copy(searching = true))
        viewModelScope.launch {
            val result = grantSource.searchGrantees(query)
            val current = mutableState.value.granteeDraft ?: return@launch
            // A later keystroke may have replaced the query while this call was in flight; its own
            // answer will land, and letting this one overwrite it would show stale candidates.
            if (current.query != query) {
                return@launch
            }
            mutableState.value =
                when (result) {
                    is ApiResult.Success -> {
                        mutableState.value.copy(
                            granteeDraft =
                                current.copy(
                                    options = result.value.rows,
                                    moreOptions = result.value.more,
                                    searching = false,
                                ),
                        )
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "grantee search failed: ${result.error}" }
                        mutableState.value.copy(
                            granteeDraft = current.copy(searching = false),
                            error = result.error,
                        )
                    }
                }
        }
    }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"
    }
}

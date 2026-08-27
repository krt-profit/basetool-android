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
 * @property activeOrgUnitId which unit a new account is opened for — the caller's pinned context,
 *   which is what „Einheit (vorbelegt)" means. A caller who has pinned *all* units has no single
 *   answer, and the creation is then refused rather than guessed at.
 */
class BankLifecycleViewModel(
    private val source: BankLifecycleSource,
    private val staff: BankStaffSource,
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
                            holders = (holders as? ApiResult.Success)?.value ?: emptyList(),
                            phase = BankPhase.Ready,
                            refreshing = false,
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

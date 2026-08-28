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
import de.greluc.krt.profit.basetool.android.core.data.BankHolderBooking
import de.greluc.krt.profit.basetool.android.core.data.BankHolderSource
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the custody transfer sheet holds.
 *
 * @property destinationId the holder to move custody to, or `null` before one is picked.
 * @property amount what to move, as typed.
 * @property note what to record about it.
 */
data class BankCustodyDraft(
    val destinationId: String? = null,
    val amount: String = "",
    val note: String = "",
)

/**
 * One holder's custody — design chapter 12, artboard 8.
 *
 * @property holder who is being shown, or `null` while the first read is running.
 * @property bookings the postings behind their custody.
 * @property peers the unit's other holders, which the transfer sheet picks from.
 * @property page which page of postings is showing, zero-based.
 * @property totalElements how many postings there are.
 * @property totalPages how many pages that makes.
 * @property phase how far the first read got.
 * @property refreshing whether a pull-to-refresh is running.
 * @property saving whether a transfer is in flight.
 * @property draft the transfer sheet, or `null` while it is closed.
 * @property error what the last write or read was refused with.
 */
data class BankHolderState(
    val holder: BankHolder? = null,
    val bookings: List<BankHolderBooking> = emptyList(),
    val peers: List<BankHolder> = emptyList(),
    val page: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val phase: BankPhase = BankPhase.Loading,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val draft: BankCustodyDraft? = null,
    val error: ApiError? = null,
)

/**
 * Drives one holder's custody detail.
 *
 * **Custody is kept at org-unit level and is not allocated to accounts** (design handoff correction
 * of 27.08.2026). That is why this screen has no account column, why the transfer touches no
 * account at all, and why the source holder may go negative — the total across the unit is
 * unchanged either way, only the attribution moves.
 *
 * @property source the holder reads and the transfer.
 * @property staff the holder register, which supplies the transfer's counterparties.
 * @property holderId whose custody to show.
 */
class BankHolderViewModel(
    private val source: BankHolderSource,
    private val staff: BankStaffSource,
    private val holderId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BankHolderState())

    /** What the screen renders. */
    val state: StateFlow<BankHolderState> = mutableState.asStateFlow()

    private var loaded = false

    /** Reads the holder once, on first composition. */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        reload(keepContent = false)
    }

    /** Re-reads everything, keeping what is on screen until the answer arrives. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        reload(keepContent = true)
    }

    /**
     * Shows a different page of postings.
     *
     * @param page which page, zero-based.
     */
    fun onPage(page: Int) {
        if (page < 0 || (mutableState.value.totalPages > 0 && page >= mutableState.value.totalPages)) {
            return
        }
        readBookings(page)
    }

    /** Opens the custody transfer sheet. */
    fun onTransfer() {
        mutableState.value = mutableState.value.copy(draft = BankCustodyDraft(), error = null)
    }

    /** Closes it, discarding what was typed. */
    fun onDismissTransfer() {
        mutableState.value = mutableState.value.copy(draft = null)
    }

    /**
     * Records a change to the transfer sheet.
     *
     * @param draft the sheet as it now stands.
     */
    fun onDraftChanged(draft: BankCustodyDraft) {
        mutableState.value = mutableState.value.copy(draft = draft)
    }

    /**
     * Sends the transfer the sheet describes.
     *
     * Refused without a destination or a positive amount — neither is guessable, and an empty
     * amount would otherwise reach the server as a zero-value posting.
     */
    fun onConfirmTransfer() {
        val current = mutableState.value
        val draft = current.draft
        val destination = draft?.destinationId
        val amount = draft?.amount?.trim().orEmpty()
        if (destination == null || amount.isEmpty() || current.saving) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val result =
                source.transferCustody(
                    sourceHolderId = holderId,
                    destinationHolderId = destination,
                    amount = amount,
                    note = draft.note,
                )
            when (result) {
                is ApiResult.Success -> {
                    mutableState.value = mutableState.value.copy(saving = false, draft = null)
                    reload(keepContent = true)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "custody transfer refused: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    /**
     * Reads the holder, their postings and the register.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.value = mutableState.value.copy(phase = BankPhase.Loading)
        }
        viewModelScope.launch {
            when (val holder = source.holder(holderId)) {
                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "holder unavailable: ${holder.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = BankPhase.Failed(holder.error),
                            refreshing = false,
                        )
                }

                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            holder = holder.value,
                            phase = BankPhase.Ready,
                            refreshing = false,
                        )
                    readPeers()
                    readBookings(mutableState.value.page)
                }
            }
        }
    }

    /**
     * Reads the unit's holders, which the transfer picks its counterparty from.
     *
     * A failure here leaves the detail standing: the custody figure and the postings are what the
     * screen is for, and only the transfer needs the register.
     */
    private fun readPeers() {
        viewModelScope.launch {
            when (val result = staff.holders()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            peers = result.value.filter { it.id != holderId && it.active },
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "holder register unavailable: ${result.error}" }
                }
            }
        }
    }

    /**
     * Reads one page of postings.
     *
     * @param page which page, zero-based.
     */
    private fun readBookings(page: Int) {
        viewModelScope.launch {
            when (val result = source.holderBookings(holderId, page)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            bookings = result.value.rows,
                            page = result.value.page,
                            totalElements = result.value.totalElements,
                            totalPages = result.value.totalPages,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "holder postings unavailable: ${result.error}" }
                    mutableState.value = mutableState.value.copy(error = result.error)
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No amount, handle or note is ever logged. */
        const val LOG_TAG = "bank"
    }
}

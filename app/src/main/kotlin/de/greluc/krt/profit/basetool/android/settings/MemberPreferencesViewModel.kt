/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MemberPreferencesSource
import de.greluc.krt.profit.basetool.android.core.data.PayoutPreference
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The two Einstellungen rows that live on the server rather than on the device.
 *
 * Both are columns of the backend's `User` entity, so they share one optimistic-lock version.
 *
 * @property payout the standing payout choice, or `null` until the read lands.
 * @property sharing whether blueprints are shared, or `null` until the read lands.
 * @property version the entity's version as the last read or write left it; both writes echo it.
 * @property saving whether a write is in flight; both rows are disabled meanwhile.
 * @property error the last refusal of a write, kept until the next attempt.
 * @property readError why the two values could not be read, or `null` when they arrived; while set,
 *   every control stays disabled because no valid version is known.
 * @property reading whether a read is in flight, so a retry can say it is doing something.
 */
data class MemberPreferencesState(
    val payout: PayoutPreference? = null,
    val sharing: Boolean? = null,
    val version: Long = 0L,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val readError: ApiError? = null,
    val reading: Boolean = false,
)

/**
 * Reads and writes the member's own server-side choices for Einstellungen.
 *
 * Both writes echo the version; after a refusal the row keeps the last server-confirmed value. A
 * value not yet read renders as unset, and a failed read is shown with a retry.
 *
 * @property source the me-scoped preference endpoints.
 */
class MemberPreferencesViewModel(
    private val source: MemberPreferencesSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MemberPreferencesState())

    /** What the two rows draw. */
    val state: StateFlow<MemberPreferencesState> = mutableState.asStateFlow()

    private var loaded = false

    /**
     * Reads both values once; later calls do nothing.
     */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        refresh()
    }

    /**
     * Re-reads both values, whatever has been read before.
     *
     * Also the retry the screen offers, which is why it clears [MemberPreferencesState.readError]
     * before it starts: a stale message beside a running attempt reads as a fresh failure.
     */
    fun refresh() {
        mutableState.update { it.copy(reading = true, readError = null) }
        viewModelScope.launch {
            var failure: ApiError? = null
            when (val result = source.payoutPreference()) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            payout = result.value.preference,
                            version = result.value.version,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the payout preference could not be read: ${result.error}" }
                    failure = result.error
                }
            }
            when (val result = source.blueprintSharing()) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            sharing = result.value.sharing,
                            version = result.value.version,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the blueprint-sharing flag could not be read: ${result.error}" }
                    failure = failure ?: result.error
                }
            }
            mutableState.update { it.copy(reading = false, readError = failure) }
        }
    }

    /**
     * Sets where the member's share goes by default.
     *
     * @param preference the new choice.
     */
    fun onPayout(preference: PayoutPreference) {
        val current = mutableState.value
        if (current.saving || current.payout == preference) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = source.setPayoutPreference(preference, current.version)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            payout = result.value.preference,
                            version = result.value.version,
                            saving = false,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    mutableState.update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Shares — or stops sharing — the member's blueprints with the organisation.
     *
     * @param sharing whether to share.
     */
    fun onSharing(sharing: Boolean) {
        val current = mutableState.value
        if (current.saving || current.sharing == sharing) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = source.setBlueprintSharing(sharing, current.version)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            sharing = result.value.sharing,
                            version = result.value.version,
                            saving = false,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    mutableState.update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "member-prefs"
    }
}

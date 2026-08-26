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
import kotlinx.coroutines.launch

/**
 * The two Einstellungen rows that live on the server rather than on the device.
 *
 * **They share one version, because they share one row.** Both are columns of the backend's `User`
 * entity, so writing either one bumps the same counter. Keeping a version per setting looked
 * tidier and was wrong: setting the payout preference took the entity from 1 to 2, and every later
 * blueprint-sharing write was refused with `expected=1 persisted=2` — permanently, because the row
 * kept re-sending the version it had read at start-up. Found on a device; a unit test with one
 * fake per setting would never have shown it.
 *
 * @property payout the standing payout choice, or `null` until the read lands.
 * @property sharing whether blueprints are shared, or `null` until the read lands.
 * @property version the entity's version, as the **last** read or write left it. Both writes echo
 *   this one value.
 * @property saving whether a write is in flight — both rows disable together, because a second
 *   write while the first is unanswered would send a version that is already stale.
 * @property error the last refusal, kept until the next attempt.
 */
data class MemberPreferencesState(
    val payout: PayoutPreference? = null,
    val sharing: Boolean? = null,
    val version: Long = 0L,
    val saving: Boolean = false,
    val error: ApiError? = null,
)

/**
 * Reads and writes the member's own standing choices for Einstellungen.
 *
 * **Both writes echo a version.** These are not device preferences: the same member can change the
 * same value in a browser, and the server is entitled to refuse the second write. A refusal is shown
 * and the row keeps the value the server last confirmed, rather than the one that was refused —
 * a settings row that shows what the member tapped after the server said no is lying about the
 * state of their account.
 *
 * The reads are **not** blocking: a row whose value has not arrived renders as unset rather than as
 * a spinner, because the rest of the screen is device-local and works either way.
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
     * Reads both values once.
     *
     * Idempotent: Einstellungen is reached repeatedly through „Mehr" and a second visit should not
     * spend two more round trips on values nothing else can have changed in between.
     */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        refresh()
    }

    /** Re-reads both values, whatever has been read before. */
    fun refresh() {
        // Sequential, not concurrent: the two reads return the same entity's version, and a race
        // between them would leave whichever answered second in charge of it for no reason.
        viewModelScope.launch {
            when (val result = source.payoutPreference()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            payout = result.value.preference,
                            version = result.value.version,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the payout preference could not be read: ${result.error}" }
                }
            }
            when (val result = source.blueprintSharing()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            sharing = result.value.sharing,
                            version = result.value.version,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the blueprint-sharing flag could not be read: ${result.error}" }
                }
            }
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
                    // The new version belongs to BOTH rows: the sibling's next write has to send it
                    // or the server refuses a change the member can see nothing wrong with.
                    mutableState.value =
                        mutableState.value.copy(
                            payout = result.value.preference,
                            version = result.value.version,
                            saving = false,
                        )
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
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
                    mutableState.value =
                        mutableState.value.copy(
                            sharing = result.value.sharing,
                            version = result.value.version,
                            saving = false,
                        )
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        mutableState.value.copy(saving = false, error = result.error)
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "member-prefs"
    }
}

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

/** Why the server refused an RSI handle, said at the field rather than below the group. */
enum class RsiHandleRefusal {
    /** Another profile carries it; the server does not say which. */
    TAKEN,

    /** It is outside the handle alphabet or length. */
    INVALID,
}

/**
 * The member's RSI handle as the KONTO row edits it.
 *
 * @property confirmed the handle the server last confirmed, or `null` when none is set.
 * @property read whether the handle has arrived; the field stays disabled until it has.
 * @property draft what the field currently holds.
 * @property saving whether this row's write is in flight; its button shows the spinner.
 * @property refusal why the last save was refused at the field, or `null`.
 * @property saved whether the success toast is due.
 */
data class RsiHandleState(
    val confirmed: String? = null,
    val read: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val refusal: RsiHandleRefusal? = null,
    val saved: Boolean = false,
) {
    /** Whether „Speichern" would change anything the server holds. */
    val changed: Boolean
        get() = draft.trim() != confirmed.orEmpty()
}

/**
 * What the RSI-handle row reports back.
 *
 * @property onDraft the field changed.
 * @property onSave „Speichern" was tapped.
 * @property onSavedShown the success toast has run its course.
 */
data class RsiHandleActions(
    val onDraft: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSavedShown: () -> Unit = {},
)

/**
 * The Einstellungen rows that live on the server rather than on the device.
 *
 * All three are columns of the backend's `User` entity, so they share one optimistic-lock version.
 *
 * @property payout the standing payout choice, or `null` while unread or never chosen.
 * @property payoutRead whether a read of [payout] has landed; `null` with this set means the member
 *   has not chosen yet, and the row is writable.
 * @property sharing whether blueprints are shared, or `null` until the read lands.
 * @property rsi the RSI-handle row.
 * @property version the entity's version as the last read or write left it; every write echoes it.
 * @property saving whether a write is in flight; every row is disabled meanwhile.
 * @property error the last refusal of a write, kept until the next attempt.
 * @property readError why the values could not be read, or `null` when they arrived; while set,
 *   every control stays disabled because no valid version is known.
 * @property reading whether a read is in flight, so a retry can say it is doing something.
 */
data class MemberPreferencesState(
    val payout: PayoutPreference? = null,
    val payoutRead: Boolean = false,
    val sharing: Boolean? = null,
    val rsi: RsiHandleState = RsiHandleState(),
    val version: Long = 0L,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val readError: ApiError? = null,
    val reading: Boolean = false,
)

/**
 * Reads and writes the member's own server-side choices for Einstellungen.
 *
 * Every write echoes the version; after a refusal the row keeps the last server-confirmed value. A
 * value not yet read renders as unset, and a failed read is shown with a retry.
 *
 * @property source the me-scoped preference endpoints.
 */
class MemberPreferencesViewModel(
    private val source: MemberPreferencesSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MemberPreferencesState())

    /** What the rows draw. */
    val state: StateFlow<MemberPreferencesState> = mutableState.asStateFlow()

    private var loaded = false

    /**
     * Reads every value once; later calls do nothing.
     */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        refresh()
    }

    /**
     * Re-reads every value, whatever has been read before.
     *
     * Also the retry the screen offers, which is why it clears [MemberPreferencesState.readError]
     * before it starts: a stale message beside a running attempt reads as a fresh failure.
     */
    fun refresh() {
        mutableState.update { it.copy(reading = true, readError = null) }
        viewModelScope.launch {
            val payoutFailure = readPayout()
            val sharingFailure = readSharing()
            val rsiFailure = readRsiHandle()
            mutableState.update {
                it.copy(reading = false, readError = payoutFailure ?: sharingFailure ?: rsiFailure)
            }
        }
    }

    private suspend fun readPayout(): ApiError? =
        when (val result = source.payoutPreference()) {
            is ApiResult.Success -> {
                mutableState.update {
                    it.copy(
                        payout = result.value.preference,
                        payoutRead = true,
                        version = result.value.version,
                    )
                }
                null
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the payout preference could not be read: ${result.error}" }
                result.error
            }
        }

    private suspend fun readSharing(): ApiError? =
        when (val result = source.blueprintSharing()) {
            is ApiResult.Success -> {
                mutableState.update {
                    it.copy(sharing = result.value.sharing, version = result.value.version)
                }
                null
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the blueprint-sharing flag could not be read: ${result.error}" }
                result.error
            }
        }

    private suspend fun readRsiHandle(): ApiError? =
        when (val result = source.rsiHandle()) {
            is ApiResult.Success -> {
                val handle = result.value.handle
                mutableState.update {
                    it.copy(
                        rsi = RsiHandleState(confirmed = handle, read = true, draft = handle.orEmpty()),
                        version = result.value.version,
                    )
                }
                null
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the RSI handle could not be read: ${result.error}" }
                result.error
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
        mutableState.update { it.copy(saving = true, error = null) }
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
        mutableState.update { it.copy(saving = true, error = null) }
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

    /**
     * Takes what the member typed into the RSI-handle field; a refusal shown there is cleared.
     *
     * @param text the field's new content.
     */
    fun onRsiDraft(text: String) {
        mutableState.update { it.copy(rsi = it.rsi.copy(draft = text, refusal = null)) }
    }

    /**
     * Saves the RSI handle as the field holds it; an empty field clears it.
     *
     * Does nothing before the handle was read, while any write runs, or when nothing changed. A
     * taken or malformed handle is refused at the field and the input stays; any other refusal is
     * the group's.
     */
    fun onRsiSave() {
        val current = mutableState.value
        if (current.saving || !current.rsi.read || !current.rsi.changed) {
            return
        }
        mutableState.update {
            it.copy(saving = true, error = null, rsi = it.rsi.copy(saving = true, refusal = null))
        }
        viewModelScope.launch {
            val result = source.setRsiHandle(current.rsi.draft.trim(), current.version)
            mutableState.update { state ->
                when (result) {
                    is ApiResult.Success -> {
                        val handle = result.value.handle
                        state.copy(
                            version = result.value.version,
                            saving = false,
                            rsi =
                                RsiHandleState(
                                    confirmed = handle,
                                    read = true,
                                    draft = handle.orEmpty(),
                                    saved = true,
                                ),
                        )
                    }

                    is ApiResult.Failure -> {
                        val refusal = result.error.rsiRefusal()
                        state.copy(
                            saving = false,
                            error = if (refusal == null) result.error else null,
                            rsi = state.rsi.copy(saving = false, refusal = refusal),
                        )
                    }
                }
            }
        }
    }

    /** The success toast has been shown. */
    fun onRsiSavedShown() {
        mutableState.update { it.copy(rsi = it.rsi.copy(saved = false)) }
    }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "member-prefs"

        /** The server's code for a handle another profile carries (REQ-SEC-072). */
        const val DUPLICATE_ENTITY = "DUPLICATE_ENTITY"

        /**
         * Reads a refusal the field answers itself.
         *
         * @return the field's reason, or `null` when the refusal belongs to the group.
         */
        fun ApiError.rsiRefusal(): RsiHandleRefusal? =
            when {
                this is ApiError.Conflict && problem?.code == DUPLICATE_ENTITY -> RsiHandleRefusal.TAKEN
                this is ApiError.Validation -> RsiHandleRefusal.INVALID
                else -> null
            }
    }
}

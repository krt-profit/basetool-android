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
import de.greluc.krt.profit.basetool.android.core.data.NotificationPreference
import de.greluc.krt.profit.basetool.android.core.data.NotificationPreferencesSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The notification switches in Einstellungen.
 *
 * @property rows every notification type with the member's switch, in the order the server listed
 *   them; empty until a read has landed.
 * @property read whether a read has succeeded, which tells an empty list the server sent from one
 *   that has not arrived.
 * @property reading whether a read is in flight, so the retry can say it is doing something.
 * @property readError why the types could not be read, or `null`; while set and nothing was read,
 *   there is nothing to switch.
 * @property writeError the last refusal of a write, kept until the next attempt.
 * @property pending the types whose write is in flight; a second tap on one is ignored.
 */
data class NotificationPreferencesState(
    val rows: List<NotificationPreference> = emptyList(),
    val read: Boolean = false,
    val reading: Boolean = false,
    val readError: ApiError? = null,
    val writeError: ApiError? = null,
    val pending: Set<String> = emptySet(),
)

/**
 * What the notification group reports back.
 *
 * @property onToggle a switch was tapped: the type and whether the member now wants to receive it.
 * @property onRetry the read is to be repeated.
 */
data class NotificationPreferenceActions(
    val onToggle: (type: String, receive: Boolean) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {},
)

/**
 * Reads and writes the member's per-type notification switches (REQ-APP-NOTIF-017).
 *
 * A switch flips at once and is written on its own: there is no version and no shared row, so two
 * types never wait on each other. A refused write puts the row back as it was and re-reads the
 * list, so what the screen shows is what the server holds.
 *
 * @property source the preference endpoints.
 */
class NotificationPreferencesViewModel(
    private val source: NotificationPreferencesSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationPreferencesState())

    /** What the group draws. */
    val state: StateFlow<NotificationPreferencesState> = mutableState.asStateFlow()

    private var loaded = false

    /**
     * Reads the list once; later calls do nothing.
     */
    fun loadOnce() {
        if (loaded) {
            return
        }
        loaded = true
        refresh()
    }

    /**
     * Re-reads the list, whatever has been read before; also the retry the group offers.
     */
    fun refresh() {
        mutableState.update { it.copy(reading = true, readError = null) }
        viewModelScope.launch {
            val failure = readRows()
            mutableState.update { it.copy(reading = false, readError = failure) }
        }
    }

    private suspend fun readRows(): ApiError? =
        when (val result = source.preferences()) {
            is ApiResult.Success -> {
                mutableState.update { state ->
                    val local = state.rows.associateBy { it.type }
                    state.copy(
                        read = true,
                        rows =
                            result.value.map { row ->
                                if (row.type in
                                    state.pending
                                ) {
                                    local[row.type] ?: row
                                } else {
                                    row
                                }
                            },
                    )
                }
                null
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the notification switches could not be read: ${result.error}" }
                result.error
            }
        }

    /**
     * Turns one notification type on or off.
     *
     * Does nothing for a type that cannot be muted, for one whose write is still in flight, or when
     * the switch already stands where it was tapped.
     *
     * @param type the type constant, as the list carries it.
     * @param receive whether the member wants to receive the type from now on.
     */
    fun onToggle(
        type: String,
        receive: Boolean,
    ) {
        val before = mutableState.value.rows.firstOrNull { it.type == type } ?: return
        if (!before.mutable || type in mutableState.value.pending || before.muted == !receive) {
            return
        }
        mutableState.update {
            it.copy(
                rows = it.rows.replacing(before.copy(muted = !receive)),
                pending = it.pending + type,
                writeError = null,
            )
        }
        viewModelScope.launch {
            when (val result = source.setMuted(type, muted = !receive)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(rows = it.rows.replacing(result.value), pending = it.pending - type)
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "a notification switch was not saved: ${result.error}" }
                    mutableState.update {
                        it.copy(
                            rows = it.rows.replacing(before),
                            pending = it.pending - type,
                            writeError = result.error,
                        )
                    }
                    readRows()
                }
            }
        }
    }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "notification-prefs"

        /**
         * Swaps one row for its new state, keeping the order.
         *
         * @param row the row to put in; matched by its type.
         * @return the list with the row replaced.
         */
        fun List<NotificationPreference>.replacing(row: NotificationPreference): List<NotificationPreference> =
            map { if (it.type == row.type) row else it }
    }
}

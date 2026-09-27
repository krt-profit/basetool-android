/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.TermsDocument
import de.greluc.krt.profit.basetool.android.core.data.TermsSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The re-consent overlay as it draws.
 *
 * @property open whether the overlay stands over the current screen.
 * @property document the wording in force, or `null` until it has arrived.
 * @property loading whether the wording is being fetched.
 * @property accepting whether the confirmation is in flight; the CTA shows its spinner.
 * @property errorRes the error line inside the overlay, or `null`.
 * @property offline whether the device has no connection; the CTA is dimmed with the reason.
 */
data class ReconsentState(
    val open: Boolean = false,
    val document: TermsDocument? = null,
    val loading: Boolean = false,
    val accepting: Boolean = false,
    val errorRes: Int? = null,
    val offline: Boolean = false,
) {
    /** Whether „Bestätigen" can be sent: the wording is on screen, the device online, nothing in flight. */
    val confirmable: Boolean
        get() = open && document != null && !accepting && !offline
}

/**
 * Drives the overlay that asks for consent again when any call is refused for it (design ch. 19
 * artboard 11, ADR-0025).
 *
 * Opens when [broker] reports a waiting call, fetches the wording the first-run gate shows, and on
 * „Bestätigen" records consent and releases every waiting call.
 *
 * @property broker the calls waiting for consent.
 * @property source reads the wording and records consent.
 * @property connectivity whether the device is online; the wording is fetched again on reconnect.
 */
class ReconsentViewModel(
    private val broker: ReconsentBroker,
    private val source: TermsSource,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReconsentState())

    /** What the overlay draws. */
    val state: StateFlow<ReconsentState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            broker.required.collect { due ->
                if (due) {
                    mutableState.update { ReconsentState(open = true, offline = it.offline) }
                    load()
                } else {
                    mutableState.update { ReconsentState(offline = it.offline) }
                }
            }
        }
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.update { it.copy(offline = !online) }
                if (online) {
                    retry()
                }
            }
        }
    }

    /** The overlay can be shown from now on: calls refused for consent wait for it. */
    fun arm() = broker.arm()

    /** The overlay can no longer be shown: waiting calls return their refusal. */
    fun disarm() = broker.disarm()

    /** Fetches the wording again after a failed read. */
    fun retry() {
        if (mutableState.value.open && mutableState.value.document == null && !mutableState.value.loading) {
            viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        mutableState.update { it.copy(loading = true, errorRes = null) }
        val result = source.document()
        mutableState.update {
            when (result) {
                is ApiResult.Success -> {
                    it.copy(document = result.value, loading = false)
                }

                is ApiResult.Failure -> {
                    it.copy(
                        loading = false,
                        errorRes =
                            if (result.error is ApiError.Network) {
                                R.string.reconsent_load_offline
                            } else {
                                R.string.reconsent_load_failed
                            },
                    )
                }
            }
        }
    }

    /**
     * Records consent for the wording in force; on success every waiting call is issued once more.
     *
     * A failure keeps the overlay open with its error line.
     */
    fun confirm() {
        val current = mutableState.value
        if (!current.confirmable) {
            return
        }
        mutableState.update { it.copy(accepting = true, errorRes = null) }
        viewModelScope.launch {
            val result = source.accept()
            if (result is ApiResult.Success && result.value.accepted) {
                broker.consented()
                return@launch
            }
            val network = result is ApiResult.Failure && result.error is ApiError.Network
            mutableState.update {
                it.copy(
                    accepting = false,
                    errorRes = if (network) R.string.terms_error_offline else R.string.terms_error,
                )
            }
        }
    }

    /** The member signs out instead; every waiting call keeps its refusal. */
    fun decline() = broker.declined()
}

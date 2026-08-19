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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decides whether the consent gate stands in the member's way, and gets them past it.
 *
 * The order of the two reads is the part worth stating. The status is asked first, and the document
 * is fetched **only if consent is missing** — a member who accepted months ago should not pay for a
 * document download on every app start to be told they already agreed.
 *
 * @property source reads the status and the document, and records consent
 */
class TermsGateViewModel(
    private val source: TermsSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow<TermsGateState>(TermsGateState.Checking)

    /** What the gate currently knows. */
    val state: StateFlow<TermsGateState> = mutableState.asStateFlow()

    /**
     * Reads the consent status and, if needed, the wording.
     *
     * Safe to call repeatedly; a call while a read is already running restarts it, which is what a
     * retry button should do.
     */
    fun start() {
        viewModelScope.launch {
            mutableState.value = TermsGateState.Checking
            when (val status = source.status()) {
                is ApiResult.Failure -> {
                    mutableState.value = TermsGateState.Unavailable(status.error)
                    return@launch
                }

                is ApiResult.Success -> {
                    if (status.value.accepted) {
                        mutableState.value = TermsGateState.Cleared
                        return@launch
                    }
                }
            }

            // Consent is missing, so the wording has to be shown — and a document that cannot be
            // read is a hard stop rather than an emptier gate. Asking somebody to agree to a blank
            // page is not asking for consent at all.
            when (val document = source.document()) {
                is ApiResult.Success -> {
                    mutableState.value = TermsGateState.Required(document.value, accepting = false, errorRes = null)
                }

                is ApiResult.Failure -> {
                    mutableState.value = TermsGateState.Unavailable(document.error)
                }
            }
        }
    }

    /**
     * Records the member's consent.
     *
     * A failure keeps the document on screen with a message rather than dropping the member into an
     * error page: the text they just read is exactly what they need in front of them to try again.
     */
    fun accept() {
        val current = mutableState.value as? TermsGateState.Required ?: return
        mutableState.value = current.copy(accepting = true, errorRes = null)
        viewModelScope.launch {
            when (val result = source.accept()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        if (result.value.accepted) {
                            TermsGateState.Cleared
                        } else {
                            // The server answered 200 and still reports no consent. Nothing the
                            // member can do differently, so say so rather than looping them back
                            // through the same button.
                            current.copy(accepting = false, errorRes = R.string.terms_error)
                        }
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        current.copy(
                            accepting = false,
                            errorRes =
                                if (result.error is ApiError.Network) {
                                    R.string.terms_error_offline
                                } else {
                                    R.string.terms_error
                                },
                        )
                }
            }
        }
    }
}

/**
 * What the consent gate has decided.
 */
sealed interface TermsGateState {
    /** The status has not been read yet. */
    data object Checking : TermsGateState

    /** Consent is on record — the app may open. */
    data object Cleared : TermsGateState

    /**
     * Consent is missing and the wording is on screen.
     *
     * @property document the wording in force
     * @property accepting whether an acceptance is in flight
     * @property errorRes a message from a failed acceptance, or `null`
     */
    data class Required(
        val document: TermsDocument,
        val accepting: Boolean,
        val errorRes: Int?,
    ) : TermsGateState

    /**
     * The gate could not be read, or the wording could not be fetched.
     *
     * @property error what went wrong
     */
    data class Unavailable(
        val error: ApiError,
    ) : TermsGateState
}

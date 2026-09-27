/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import de.greluc.krt.profit.basetool.android.core.network.ConsentRecovery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collects every call the server refused for missing consent behind one re-consent overlay (ADR-0025).
 *
 * The first refusal opens the overlay; later ones join the same wait. The member's answer releases
 * them all at once. While no overlay can be shown — before the first-run gate has cleared, or after
 * sign-out — a refusal is not held and returns to its caller as it is.
 */
class ReconsentBroker : ConsentRecovery {
    private val lock = Any()
    private var pending: CompletableDeferred<Boolean>? = null
    private var armed = false
    private val mutableRequired = MutableStateFlow(false)

    /** Whether a refused call is waiting for the member's answer, i.e. whether the overlay is due. */
    val required: StateFlow<Boolean> = mutableRequired.asStateFlow()

    override suspend fun awaitConsent(): Boolean {
        val waiting =
            synchronized(lock) {
                if (!armed) {
                    null
                } else {
                    pending ?: CompletableDeferred<Boolean>().also {
                        pending = it
                        mutableRequired.value = true
                    }
                }
            }
        return waiting?.await() ?: false
    }

    /** The overlay can be shown from now on; refusals wait for it. */
    fun arm() {
        synchronized(lock) { armed = true }
    }

    /** The overlay is gone; refusals return as they are, and any that wait are released unanswered. */
    fun disarm() {
        synchronized(lock) { armed = false }
        resolve(consented = false)
    }

    /** Consent is on record; every waiting call is issued once more. */
    fun consented() = resolve(consented = true)

    /** The member signs out instead; every waiting call keeps its refusal. */
    fun declined() = resolve(consented = false)

    private fun resolve(consented: Boolean) {
        val waiting =
            synchronized(lock) {
                pending.also {
                    pending = null
                    mutableRequired.value = false
                }
            }
        waiting?.complete(consented)
    }
}

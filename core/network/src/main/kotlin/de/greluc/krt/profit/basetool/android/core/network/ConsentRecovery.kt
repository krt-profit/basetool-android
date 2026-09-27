/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

/**
 * Lets a call the server refused with `TERMS_NOT_ACCEPTED` wait for the member's consent.
 *
 * [ApiReader] asks it once per refused call and re-issues the call once when it answers `true`
 * (ADR-0025).
 */
fun interface ConsentRecovery {
    /**
     * Suspends until the member has confirmed the terms in force, or until that will not happen.
     *
     * @return `true` when consent is now on record and the refused call may be issued again;
     *   `false` when the refusal stands, e.g. the member signed out or nobody can ask them
     */
    suspend fun awaitConsent(): Boolean

    /** Default recoveries. */
    companion object {
        /** No recovery: the refusal is returned to the caller as it is. */
        val None: ConsentRecovery = ConsentRecovery { false }
    }
}

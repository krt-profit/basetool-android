/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.greluc.krt.profit.basetool.android.MainActivity
import de.greluc.krt.profit.basetool.android.core.common.KrtLog

/**
 * Catches the browser's login redirect and hands it back to the running `MainActivity`.
 *
 * `singleTask` clears the Custom Tab off the task, and `MainActivity` is relaunched with `CLEAR_TOP`
 * so the existing instance receives it. It has no UI and finishes immediately. It is exported, but
 * the code is worthless without the PKCE verifier in `PendingAuthorization`, and a mismatched
 * `state` is discarded in `AuthorizationRequest.readRedirect`.
 */
class AuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deliver(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deliver(intent)
    }

    /**
     * Forwards the redirect to [MainActivity] and finishes.
     *
     * @param source the intent the browser delivered; its data is the redirect URI
     */
    private fun deliver(source: Intent?) {
        val redirect = source?.data
        if (redirect == null) {
            KrtLog.w(LOG_TAG) { "auth redirect activity started without a redirect URI" }
        }
        val next =
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_AUTH_REDIRECT)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_REDIRECT_URI, redirect?.toString())
        startActivity(next)
        finish()
    }

    companion object {
        /** Log subsystem; the redirect's query — which carries the code — is never logged. */
        private const val LOG_TAG = "auth"

        /** Marks the intent `MainActivity` receives as an authorization redirect. */
        const val ACTION_AUTH_REDIRECT = "de.greluc.krt.profit.basetool.android.AUTH_REDIRECT"

        /** Extra holding the full redirect URI as a string. */
        const val EXTRA_REDIRECT_URI = "de.greluc.krt.profit.basetool.android.REDIRECT_URI"
    }
}

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
 * Catches the redirect the browser sends at the end of a login and hands it back to the app.
 *
 * **Why a separate activity rather than another intent filter on `MainActivity`.** When the Custom
 * Tab is open it sits on top of this task, so `MainActivity` is not the activity the redirect would
 * be delivered to; with `singleTop` the system would create a *second* `MainActivity` on top of the
 * browser instead of returning to the running one. `singleTask` here brings the task forward and
 * clears the Custom Tab off it, and re-launching `MainActivity` with `CLEAR_TOP` lands on the
 * instance that was already there — or creates one, which is exactly right after the process was
 * killed behind the browser. Putting `singleTask` on `MainActivity` itself would have changed the
 * launch semantics of every deep link and notification in the app to fix one flow.
 *
 * **It has no UI and finishes immediately.** Anything drawn here would flash between the browser
 * closing and the app appearing.
 *
 * The redirect carries the authorization code, which is worthless without the PKCE verifier held in
 * `PendingAuthorization` — so this activity being exported (it must be, the browser starts it) does
 * not let another app complete a login. A redirect that does not match the pending attempt's `state`
 * is discarded further along, in `AuthorizationRequest.readRedirect`.
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
            // Nothing to act on. Reached by a manual launch of an exported activity, not by the
            // browser; opening the app empty-handed is a better answer than a crash.
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

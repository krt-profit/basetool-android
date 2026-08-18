/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import de.greluc.krt.profit.basetool.android.core.common.KrtLog

/**
 * Opens the realm's login page in a Custom Tab.
 *
 * **Never a WebView** (RFC 8252 §8.12, design spec ch. 04, security concept §4). A WebView would put
 * the member's Keycloak password inside a surface this app controls and can read, it shares no
 * session with the browser so every login would start from scratch, and it is the pattern a
 * phishing app is indistinguishable from. A Custom Tab is the real browser: same cookie jar, same
 * password manager, an address bar the member can check.
 *
 * The toolbar is `#141414` because the realm's login page is themed dark; the default light chrome
 * around it reads as a different app (design spec ch. 04).
 *
 * If no browser supports Custom Tabs, [CustomTabsIntent] falls back to an ordinary `ACTION_VIEW`,
 * which still completes the flow — a plain browser is a worse experience, not a broken one. Only a
 * device with no browser at all fails, and that is reported rather than swallowed.
 */
object CustomTabLauncher {
    /** Toolbar colour of the realm's dark theme; a light toolbar reads as a different app. */
    private const val TOOLBAR_COLOR = 0xFF141414.toInt()

    /** Log subsystem; the authorization URL is not logged — it carries the PKCE challenge. */
    private const val LOG_TAG = "auth"

    /**
     * Opens [url] in a Custom Tab.
     *
     * @param context the activity starting the flow
     * @param url the authorization URL from `AuthorizationRequestFactory`
     * @return `true` when a browser took it; `false` when the device has none, which the caller
     *   surfaces as an error rather than a login that silently never returns
     */
    fun launch(
        context: Context,
        url: String,
    ): Boolean =
        try {
            intent().launchUrl(context, url.toUri())
            true
        } catch (missing: ActivityNotFoundException) {
            KrtLog.e(LOG_TAG, missing) { "no browser available to complete the login" }
            false
        }

    /**
     * Builds the Custom Tabs intent.
     *
     * @return the configured intent
     */
    private fun intent(): CustomTabsIntent =
        CustomTabsIntent
            .Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams
                    .Builder()
                    .setToolbarColor(TOOLBAR_COLOR)
                    .build(),
            ).setShowTitle(false)
            .setUrlBarHidingEnabled(false)
            .build()
            .apply {
                // The login tab belongs to this task, so backing out of it returns to the login
                // screen rather than to whatever was behind the app.
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

    /**
     * Reads the redirect URI out of an intent delivered by [AuthRedirectActivity].
     *
     * @param intent the intent `MainActivity` received
     * @return the redirect URI, or `null` when this intent is not an authorization redirect
     */
    fun redirectOf(intent: Intent?): Uri? =
        intent
            ?.takeIf { it.action == AuthRedirectActivity.ACTION_AUTH_REDIRECT }
            ?.getStringExtra(AuthRedirectActivity.EXTRA_REDIRECT_URI)
            ?.toUri()
}

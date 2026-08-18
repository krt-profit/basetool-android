/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import de.greluc.krt.profit.basetool.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The redirect URI this flavour was compiled with must be one this app can actually receive.
 *
 * This is the second failure in this app with no symptom of its own. If `BuildConfig
 * .OIDC_REDIRECT_URI` and the flavour's intent filter drift apart, nothing fails to build and
 * nothing fails at launch: the login works right up to the moment the browser tries to come back,
 * and then simply doesn't. The member sees a browser tab sitting on the realm's success page and an
 * app that never noticed. The realm would also refuse a redirect it has not registered, so the
 * value is pinned on both ends and only this end can be checked here.
 *
 * The test runs once per flavour — `testDevDebugUnitTest` sees the custom scheme,
 * `testProdDebugUnitTest` the verified App Link — so both filters are covered by one assertion
 * against whatever that flavour was built with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRedirectFilterTest {
    @Test
    fun `the configured redirect resolves to the redirect activity`() {
        val matches = resolve(BuildConfig.OIDC_REDIRECT_URI)

        assertEquals(
            "exactly one activity of this app must claim ${BuildConfig.OIDC_REDIRECT_URI}",
            1,
            matches.size,
        )
        assertEquals(AuthRedirectActivity::class.java.name, matches.single())
    }

    @Test
    fun `the post-logout redirect comes back to the app too`() {
        // Without this the member is left looking at a browser tab after logging out. It resolves
        // to the same activity, which reads it as a redirect carrying no code and simply brings the
        // app forward.
        assertTrue(
            "${BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI} must resolve to this app",
            resolve(BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI).isNotEmpty(),
        )
    }

    @Test
    fun `the post-logout redirect is one the realm accepts`() {
        // The client sets post.logout.redirect.uris = "+", which in Keycloak means "the same list
        // as redirectUris" (main repo scripts/provision-keycloak-mobile-client.py). Any other value
        // is refused with "Invalid post logout redirect uri" — at the realm, before the browser
        // comes back, so nothing on this side can observe it. Pinning the equality here is the only
        // check this repo can make, and it stops the two from being tidied apart.
        assertEquals(
            "post-logout must equal the redirect URI while the client uses \"+\"",
            BuildConfig.OIDC_REDIRECT_URI,
            BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI,
        )
    }

    @Test
    fun `the redirect activity is the only exported auth surface`() {
        // It has to be exported — the browser starts it. What keeps that safe is that the code it
        // carries is worthless without the PKCE verifier, which never leaves the app.
        val matches = resolve(BuildConfig.OIDC_REDIRECT_URI)

        assertTrue(
            "no other activity may claim the redirect",
            matches.none { it != AuthRedirectActivity::class.java.name },
        )
    }

    /**
     * Asks the package manager which of this app's activities claim a URI.
     *
     * @param uri the URI a browser would deliver
     * @return the matching activity class names
     */
    private fun resolve(uri: String): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val intent =
            Intent(Intent.ACTION_VIEW, uri.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(context.packageName)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.name }
    }
}

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
 * Checks that the flavour's compiled `BuildConfig.OIDC_REDIRECT_URI` is one its intent filter can receive.
 *
 * Runs once per flavour, covering both the dev custom scheme and the prod App Link.
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
        assertTrue(
            "${BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI} must resolve to this app",
            resolve(BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI).isNotEmpty(),
        )
    }

    @Test
    fun `the post-logout redirect is one the realm accepts`() {
        assertEquals(
            "post-logout must equal the redirect URI while the client uses \"+\"",
            BuildConfig.OIDC_REDIRECT_URI,
            BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI,
        )
    }

    @Test
    fun `the redirect activity is the only exported auth surface`() {
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

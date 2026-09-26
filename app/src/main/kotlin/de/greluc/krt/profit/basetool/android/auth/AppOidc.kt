/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import de.greluc.krt.profit.basetool.android.BuildConfig
import de.greluc.krt.profit.basetool.android.core.auth.OidcConfiguration

/**
 * The OIDC realm this build talks to.
 *
 * Endpoints come only from the flavour's `BuildConfig`; there is no runtime switch. The client id is
 * the same in both realms and is therefore a constant.
 */
object AppOidc {
    /**
     * The public client registered in the realm (security concept §3).
     *
     * Public means no secret, which is not an omission: a secret shipped inside an open-source APK
     * is readable by anyone who downloads it, so PKCE and the DPoP-bound refresh token do the work
     * a client secret pretends to.
     */
    const val CLIENT_ID: String = "basetool-android"

    /**
     * Builds the OIDC configuration for this build.
     *
     * @return the realm endpoints, client id and redirects this flavour was compiled with
     */
    fun configuration(): OidcConfiguration =
        OidcConfiguration(
            issuer = BuildConfig.OIDC_ISSUER,
            clientId = CLIENT_ID,
            redirectUri = BuildConfig.OIDC_REDIRECT_URI,
            postLogoutRedirectUri = BuildConfig.OIDC_POST_LOGOUT_REDIRECT_URI,
        )
}

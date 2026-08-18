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
 * The realm this build talks to.
 *
 * The values come from `BuildConfig`, which the flavours set, and from nowhere else — there is no
 * runtime switch and no debug menu that changes them. A release build that can be pointed at
 * another server is a gift to anyone who gets hold of a device (DEV_CI §6): the dev flavour reaches
 * the local test stack, the prod flavour reaches production, and which one a member has is decided
 * by which APK they installed.
 *
 * The client id is the same in both realms, so it is a constant here rather than a fourth
 * `BuildConfig` field that could drift between flavours.
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

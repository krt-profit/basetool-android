/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

/**
 * Where the realm lives and who the app claims to be.
 *
 * The endpoints are derived from Keycloak's fixed URL layout rather than discovered. There is
 * deliberately no `userinfo` endpoint, which answers HTTP 500 under the refresh-only DPoP policy.
 *
 * @property issuer realm base URL, e.g. `https://keycloak.example/realms/iri`; a trailing slash is
 *   tolerated and normalised away
 * @property clientId the public client id registered in the realm (`basetool-android`)
 * @property redirectUri the exact, wildcard-free redirect the realm has registered — the verified
 *   App Link in production, the custom scheme only on the dev/test realm
 * @property postLogoutRedirectUri where Keycloak sends the browser after the end-session call
 */
data class OidcConfiguration(
    val issuer: String,
    val clientId: String,
    val redirectUri: String,
    val postLogoutRedirectUri: String,
) {
    private val base: String = issuer.trimEnd('/')

    /** Authorization endpoint the Custom Tab opens; carries PKCE `S256` and `dpop_jkt`. */
    val authorizationEndpoint: String get() = "$base$PROTOCOL_PATH/auth"

    /** Token endpoint — the only URL the app ever sends a DPoP proof to. */
    val tokenEndpoint: String get() = "$base$PROTOCOL_PATH/token"

    /** Revocation endpoint used for the best-effort refresh-token revocation on logout. */
    val revocationEndpoint: String get() = "$base$PROTOCOL_PATH/revoke"

    /** RP-initiated logout endpoint; opened in the browser so the realm's SSO cookie dies too. */
    val endSessionEndpoint: String get() = "$base$PROTOCOL_PATH/logout"

    private companion object {
        /** Path Keycloak mounts every OIDC endpoint of a realm under. */
        const val PROTOCOL_PATH = "/protocol/openid-connect"
    }
}

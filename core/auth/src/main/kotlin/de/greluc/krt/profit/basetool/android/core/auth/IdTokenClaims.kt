/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The claims the app reads out of an ID token.
 *
 * **This is the only source of profile claims.** Under the realm's refresh-only DPoP policy
 * Keycloak answers `/userinfo` with **HTTP 500** for this client (security concept §4,
 * constraint 1), so the endpoint that would normally serve them is not merely redundant here — it
 * is broken by design, and `OidcConfiguration` deliberately does not name it.
 *
 * @property subject the stable user id (`sub`); the same value the backend scopes data by
 * @property nonce the value the realm copied from the authorization request
 * @property preferredUsername the member's login name, when the realm sends one
 * @property email the member's e-mail, when the realm sends one
 */
@Serializable
data class IdTokenClaims(
    @SerialName("sub") val subject: String? = null,
    val nonce: String? = null,
    @SerialName("preferred_username") val preferredUsername: String? = null,
    val email: String? = null,
) {
    /**
     * Renders the claims **without** the member's name or address.
     *
     * The logging rule this app inherits (main repo REQ-OBS-004) forbids names and e-mail
     * addresses in any log sink, and a data class's generated `toString` is the quiet way they get
     * there.
     *
     * @return a description carrying only the opaque subject
     */
    override fun toString(): String = "IdTokenClaims(subject=$subject)"

    companion object {
        /** Log subsystem; token material never appears in a message. */
        private const val LOG_TAG = "auth"

        /** Tolerates the many claims a Keycloak ID token carries that the app does not read. */
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Reads the payload of an ID token.
         *
         * **The signature is not verified, and that is a decision rather than an omission.** OIDC
         * Core §3.1.3.7 permits skipping it when the token was received directly from the token
         * endpoint over TLS — which is the only way this app ever obtains one. The token never
         * travels through the browser, so there is no untrusted hop between the realm and here.
         * Verifying it would mean fetching and caching JWKS and tracking key rotation to re-prove a
         * property TLS already gives.
         *
         * A token that cannot be read is `null` rather than an exception: it means the same thing
         * as an absent one — no claims — and the caller has to handle that case regardless.
         *
         * @param idToken the compact JWT as issued
         * @return the parsed claims, or `null` when the token is not a readable JWT
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun parse(idToken: String): IdTokenClaims? =
            try {
                val payload = idToken.split(".").getOrNull(1)
                payload?.let {
                    val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(it)
                    JSON.decodeFromString(serializer(), decoded.decodeToString())
                }
            } catch (malformed: IllegalArgumentException) {
                KrtLog.w(LOG_TAG, malformed) { "ID token payload could not be read" }
                null
            }
    }
}

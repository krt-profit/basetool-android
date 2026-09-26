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
 * The claims the app reads out of an ID token, its only source of profile claims.
 *
 * `/userinfo` answers HTTP 500 under the realm's refresh-only DPoP policy and is never called.
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
     * Renders the claims with only the opaque subject, keeping names and e-mail addresses out of logs (REQ-OBS-004).
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
         * Reads the payload of an ID token without verifying its signature.
         *
         * OIDC Core §3.1.3.7 permits this for a token received directly from the token endpoint over TLS,
         * the only way this app obtains one.
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

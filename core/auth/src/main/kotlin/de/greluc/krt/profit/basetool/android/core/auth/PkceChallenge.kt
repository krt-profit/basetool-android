/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A PKCE verifier and the challenge derived from it (RFC 7636).
 *
 * PKCE is what makes the authorization code useless to anyone who intercepts it: the code is
 * redeemed only together with the verifier, and the verifier never leaves the app. On Android that
 * matters concretely — the redirect travels through the OS, and on the dev realm it travels through
 * a custom scheme any installed app may claim.
 *
 * **S256 only.** The `plain` method puts the verifier in the authorization request, which is the
 * one place the code's protection must not be. The realm enforces S256 for this client
 * (security concept §3); sending anything else would be refused, and offering the option in code
 * would only create a way to get it wrong.
 *
 * @property verifier the high-entropy secret, kept until the code is redeemed
 * @property challenge the base64url SHA-256 of [verifier], sent in the authorization request
 */
data class PkceChallenge(
    val verifier: String,
    val challenge: String,
) {
    /**
     * Renders the pair **without** the verifier.
     *
     * The generated `toString` of a data class would print it, and the verifier is exactly as
     * sensitive as the code it unlocks.
     *
     * @return a description carrying only the public half
     */
    override fun toString(): String = "PkceChallenge(challenge=$challenge)"

    companion object {
        /**
         * Verifier entropy in bytes.
         *
         * 32 bytes encode to a 43-character verifier — the minimum RFC 7636 §4.1 allows, and the
         * length its security considerations recommend. Fewer bytes would still be a legal
         * verifier and a weaker one.
         */
        private const val VERIFIER_BYTES = 32

        /**
         * Generates a fresh verifier and its challenge.
         *
         * @param random source of entropy; the default is the platform CSPRNG and a test may
         *   substitute a seeded one to make a request reproducible
         * @return the pair, to be kept until the authorization code is redeemed
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun generate(random: SecureRandom = SecureRandom()): PkceChallenge {
            val bytes = ByteArray(VERIFIER_BYTES).also(random::nextBytes)
            val verifier = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            val challenge = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(digest)
            return PkceChallenge(verifier = verifier, challenge = challenge)
        }
    }
}

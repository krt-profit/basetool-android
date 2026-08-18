/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * PKCE is what makes an intercepted authorization code worthless, so the properties asserted here
 * are the ones that would silently weaken it: too little entropy, a challenge that does not
 * actually derive from the verifier, or a verifier reused across attempts.
 *
 * The challenge is recomputed independently rather than compared to a fixture — a fixture would
 * only prove the implementation still does what it did, not that it does what RFC 7636 says.
 */
class PkceChallengeTest {
    @Test
    fun `the verifier is the length RFC 7636 recommends and uses only unreserved characters`() {
        val pkce = PkceChallenge.generate()

        assertEquals(VERIFIER_LENGTH, pkce.verifier.length)
        assertTrue(
            "verifier must be unreserved characters only, was ${pkce.verifier}",
            pkce.verifier.all { it.isLetterOrDigit() || it in "-._~" },
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `the challenge is the base64url SHA-256 of the verifier`() {
        val pkce = PkceChallenge.generate()

        val expected =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray(Charsets.US_ASCII)))

        assertEquals(expected, pkce.challenge)
    }

    @Test
    fun `every attempt gets its own verifier`() {
        // A reused verifier would mean an intercepted code from an earlier attempt stays
        // redeemable, which is precisely what PKCE is supposed to prevent.
        assertNotEquals(PkceChallenge.generate().verifier, PkceChallenge.generate().verifier)
    }

    @Test
    fun `the verifier never appears in toString`() {
        // The verifier is exactly as sensitive as the code it unlocks, and a data class's
        // generated toString is the quiet way a secret reaches a log line.
        val pkce = PkceChallenge.generate()

        assertFalse(pkce.toString().contains(pkce.verifier))
        assertTrue(pkce.toString().contains(pkce.challenge))
    }

    private companion object {
        /** 32 bytes of entropy, base64url-encoded without padding — RFC 7636 section 4.1's minimum. */
        const val VERIFIER_LENGTH = 43
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outer layer that carries the app lock down to the refresh token at rest.
 *
 * Two properties matter more than the round trip. **A sealed blob must not be readable without an
 * unlock** — that is the entire point, and a `seal` that quietly passed data through when the
 * session key was missing would leave a green build guarding nothing. And **a locked read must be
 * distinguishable from a broken one**, because the caller discards what it cannot decrypt: confuse
 * the two and arming the lock deletes the member's session.
 */
class SessionEnvelopeTest {
    private val inner = "token-cipher-output".toByteArray()

    /** AES-256, matching the token cipher's key size. */
    private val sessionKeyBytes = 32

    /**
     * With no lock armed the blob passes through untouched.
     *
     * Byte-identical, not merely equivalent: a build with the lock off must store exactly what a
     * build without any of this stored, or existing sessions break on upgrade.
     */
    @Test
    fun `an unarmed envelope changes nothing`() {
        val envelope = SessionEnvelope()

        assertArrayEquals(inner, envelope.seal(inner))
        assertArrayEquals(inner, envelope.open(inner))
    }

    /**
     * Sealing and opening round-trips with the session key.
     */
    @Test
    fun `a sealed blob opens again with the same session key`() {
        val envelope = SessionEnvelope()
        envelope.unlocked(envelope.newSessionKey())

        val sealed = envelope.seal(inner)

        assertNotEquals("the sealed blob must not be the plaintext", inner.toList(), sealed.toList())
        assertArrayEquals(inner, envelope.open(sealed))
    }

    /**
     * **A sealed blob cannot be opened without an unlock.**
     *
     * The property the whole change exists for: the refresh token at rest is unreadable until the
     * member has authenticated.
     */
    @Test
    fun `a sealed blob is unreadable before an unlock`() {
        val armed = SessionEnvelope().apply { unlocked(newSessionKey()) }
        val sealed = armed.seal(inner)

        val coldStart = SessionEnvelope()

        assertThrows(AppLockedException::class.java) { coldStart.open(sealed) }
    }

    /**
     * **A locked read is not a corrupt read**, and the type says so.
     *
     * `RefreshTokenStore` wipes the stored token on `SecretCipherException`. If a locked read
     * arrived as a plain one, arming the lock would delete the member's session — so the subtype is
     * load-bearing, not decoration.
     */
    @Test
    fun `the locked failure is a distinguishable subtype`() {
        val sealed = SessionEnvelope().apply { unlocked(newSessionKey()) }.seal(inner)

        val thrown = assertThrows(SecretCipherException::class.java) { SessionEnvelope().open(sealed) }

        assertTrue("must be recognisable as 'locked', not 'broken'", thrown is AppLockedException)
    }

    /**
     * A blob sealed under one session key is not readable under another.
     */
    @Test
    fun `another session key cannot open the blob`() {
        val sealed = SessionEnvelope().apply { unlocked(newSessionKey()) }.seal(inner)
        val other = SessionEnvelope().apply { unlocked(newSessionKey()) }

        val thrown = assertThrows(SecretCipherException::class.java) { other.open(sealed) }

        assertTrue("a wrong key means unusable, not locked", thrown !is AppLockedException)
    }

    /**
     * Closing the envelope makes sealed blobs unreadable again.
     *
     * What logout relies on: after it, nothing in the process can remove the outer layer.
     */
    @Test
    fun `closing forgets the session key`() {
        val envelope = SessionEnvelope()
        envelope.unlocked(envelope.newSessionKey())
        val sealed = envelope.seal(inner)

        envelope.close()

        assertTrue(!envelope.isOpen)
        assertThrows(AppLockedException::class.java) { envelope.open(sealed) }
    }

    /**
     * Two seals of the same plaintext differ.
     *
     * A fresh IV per seal, which GCM requires: reusing one under the same key would leak the XOR of
     * two token blobs and void the authentication guarantee.
     */
    @Test
    fun `every seal uses a fresh iv`() {
        val envelope = SessionEnvelope()
        envelope.unlocked(envelope.newSessionKey())

        val first = envelope.seal(inner)
        val second = envelope.seal(inner)

        assertNotEquals(first.toList(), second.toList())
        assertArrayEquals(inner, envelope.open(first))
        assertArrayEquals(inner, envelope.open(second))
    }

    /**
     * An unsealed blob is still recognised as such once the lock is armed.
     *
     * This is what lets a member arm the lock mid-session: the token they already had was written
     * without an outer layer, and the envelope has to hand it through rather than refuse it.
     */
    @Test
    fun `an unsealed blob still passes through an open envelope`() {
        val envelope = SessionEnvelope()
        envelope.unlocked(envelope.newSessionKey())

        assertTrue(!envelope.isSealed(inner))
        assertArrayEquals(inner, envelope.open(inner))
    }

    /**
     * A session key is 256 bits.
     */
    @Test
    fun `the session key matches the token cipher's strength`() {
        assertEquals(sessionKeyBytes, SessionEnvelope().newSessionKey().size)
    }
}

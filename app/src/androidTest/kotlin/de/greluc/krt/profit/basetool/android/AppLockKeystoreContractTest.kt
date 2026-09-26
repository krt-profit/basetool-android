/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.auth.AppLockKey
import de.greluc.krt.profit.basetool.android.core.auth.KeystoreSecretCipher
import de.greluc.krt.profit.basetool.android.core.auth.SecretCipherException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts the Keystore contract the app lock rests on against a real Keystore (REQ-APP-AUTH-010).
 *
 * Covers creating the auth-bound key, obtaining both ciphers from it before any authentication, and
 * the refresh-token cipher surfacing platform failures as [SecretCipherException]. The
 * `BiometricPrompt` itself is out of scope, since it needs an activity and a human.
 */
@RunWith(AndroidJUnit4::class)
class AppLockKeystoreContractTest {
    /**
     * A screen lock for the length of each test, on a device that has none — a fresh CI emulator.
     * The auth-bound key cannot be created without one; see [SecureLockScreenRule].
     */
    @get:Rule
    val lockScreen = SecureLockScreenRule()

    private val key = AppLockKey()

    /**
     * Creating the key and obtaining the encrypt cipher both succeed before any authentication.
     *
     * An auth-per-use key permits `init` and defers authorisation to the `CryptoObject`; a failure here
     * means the lock cannot be switched on.
     */
    @Test
    fun armingProducesACipherBeforeAnyAuthentication() {
        val cipher = key.sealCipher()

        assertNotNull("the device must be able to create an auth-bound key", cipher)
        assertTrue("the encrypt cipher must carry a GCM IV", cipher.iv?.isNotEmpty() == true)
        assertEquals("GCM's IV length", GCM_IV_BYTES, cipher.iv.size)
    }

    /**
     * A freshly armed key does not report itself unsatisfiable.
     *
     * `unlockCipher` answers `null` only for a key that is gone or invalidated by a new biometric
     * enrolment.
     */
    @Test
    fun aFreshlyArmedKeyCanBeOpened() {
        val sealCipher = key.sealCipher()
        assertTrue("the key must exist right after it was created", key.exists())

        val sealed = ByteArray(sealCipher.iv.size + SEALED_BODY_BYTES)
        sealCipher.iv.copyInto(sealed)

        assertNotNull(
            "a key created moments ago must not report itself unsatisfiable",
            key.unlockCipher(sealed),
        )
    }

    /**
     * Arming twice leaves exactly one usable key.
     *
     * Switching the lock off and on again replaces the entry. If the old key survived, the sealed
     * session key written under the new one would be unopenable by either.
     */
    @Test
    fun armingAgainReplacesTheKey() {
        key.sealCipher()
        val second = key.sealCipher()

        assertTrue(key.exists())
        val sealed = ByteArray(second.iv.size + SEALED_BODY_BYTES)
        second.iv.copyInto(sealed)
        assertNotNull("the surviving key must be the one just created", key.unlockCipher(sealed))
    }

    /**
     * Disarming removes the key, so nothing is left that once guarded the session.
     */
    @Test
    fun disarmingRemovesTheKey() {
        key.sealCipher()
        assertTrue(key.exists())

        key.disarm()

        assertTrue("a disarmed lock must leave no key behind", !key.exists())
    }

    /**
     * The refresh-token cipher round-trips on this device without escaping as a `RuntimeException`.
     *
     * A device without a screen lock must arrive at [SecretCipherException] rather than a
     * `ProviderException`.
     */
    @Test
    fun theRefreshTokenCipherNeverEscapesAsARuntimeException() {
        val cipher = KeystoreSecretCipher(alias = "krt.test.secret.contract")
        val plaintext = "refresh-token-stand-in".toByteArray()
        try {
            val restored = cipher.decrypt(cipher.encrypt(plaintext))
            assertTrue(
                "a device that can make the key must round-trip the value",
                plaintext.contentEquals(restored),
            )
        } catch (expected: SecretCipherException) {
            assertNotNull("the cause is kept for the log", expected.cause)
        } finally {
            cipher.deleteKey()
        }
    }

    private companion object {
        /** Any non-empty body: these tests exercise cipher setup, never the plaintext. */
        const val SEALED_BODY_BYTES = 32

        /** GCM's IV length, which the sealed blob carries in its leading bytes. */
        const val GCM_IV_BYTES = 12
    }
}

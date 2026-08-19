/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.auth.AppLockKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Keystore contract the app lock rests on, asserted against a real Keystore.
 *
 * Every claim `REQ-APP-AUTH-010` makes about auth-bound keys was, before these tests existed,
 * asserted only by reading Android's documentation — and that gap hid two defects that each made
 * the lock completely unusable on a whole platform range. Both were invisible to unit tests,
 * because the Keystore is not exercised off a device, and neither is reproducible in Robolectric.
 *
 * They are worth naming, because they are what these tests are for:
 *
 * 1. Arming sealed the session key inline while creating the key. Auth-per-use means per *use*, and
 *    encrypting is a use, so Keystore refused with `Key user not authenticated` — **the lock could
 *    not be switched on at all** on API 30+.
 * 2. On the since-dropped API 29 the key was time-bound, `Cipher.init` threw until an
 *    authentication existed, and a broad catch turned that into the value already meaning "this
 *    lock can never be opened again". Dropping the platform (ADR-0006) removed the path; these
 *    tests are what would catch its return.
 *
 * They deliberately stop short of the prompt: a `BiometricPrompt` needs an activity and a human.
 * What they pin is everything either side of it — that the key can be created on this device, and
 * that both ciphers the prompt is meant to vouch for can be obtained from it.
 */
@RunWith(AndroidJUnit4::class)
class AppLockKeystoreContractTest {
    private val key = AppLockKey()

    /**
     * Creating the key and obtaining the encrypt cipher both succeed before any authentication.
     *
     * This is the half of arming that runs *before* the prompt, and it is the exact operation that
     * used to throw. An auth-per-use key permits `init` and defers authorisation to the
     * `CryptoObject`, so a failure here means the platform changed that contract — and the lock can
     * no longer be switched on.
     */
    @Test
    fun armingProducesACipherBeforeAnyAuthentication() {
        val cipher = key.sealCipher()

        assertNotNull("the device must be able to create an auth-bound key", cipher)
        assertTrue("the encrypt cipher must carry a GCM IV", cipher.iv?.isNotEmpty() == true)
        assertEquals("GCM's IV length", GCM_IV_BYTES, cipher.iv.size)
    }

    /**
     * A key created moments ago does not report itself unsatisfiable.
     *
     * `unlockCipher` answers `null` when the key is gone or was invalidated by a new biometric
     * enrolment — a lock with no way past it but signing out. Directly after arming it must not say
     * that, or switching the lock on would lock the member out of their own session.
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

    private companion object {
        /** Any non-empty body: these tests exercise cipher setup, never the plaintext. */
        const val SEALED_BODY_BYTES = 32

        /** GCM's IV length, which the sealed blob carries in its leading bytes. */
        const val GCM_IV_BYTES = 12
    }
}

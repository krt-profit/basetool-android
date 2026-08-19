/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.auth.AppLockKey
import de.greluc.krt.profit.basetool.android.core.auth.AuthenticatedCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Keystore contract the app lock rests on, asserted against a real Keystore.
 *
 * Every claim in `REQ-APP-AUTH-010` about auth-bound keys was, until this ran, asserted only by
 * reading Android's documentation. That gap is not academic: it hid a defect that made the lock
 * **impossible to switch on** for the entire API 30+ range. Sealing the session key inline while
 * creating the key threw `Key user not authenticated` — auth-per-use means per *use*, and
 * encrypting is a use — and every unit test stayed green, because the Keystore is not exercised off
 * a device.
 *
 * The two platform paths differ in kind, not degree, so both need a device to be believed:
 *
 * - **API 30+** — `setUserAuthenticationParameters(0, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`, an
 *   auth-per-use key, the only kind a `CryptoObject` accepts.
 * - **API 29** — no such method. `setUserAuthenticationValidityDurationSeconds` yields a *time-bound*
 *   key that cannot be paired with a `CryptoObject` at all.
 *
 * These tests deliberately stop short of the prompt: a `BiometricPrompt` needs an activity and a
 * human. What they pin is everything either side of it — that the key can be created on this
 * platform, that the cipher the prompt is meant to vouch for can be obtained, and that the
 * `CryptoObject` decision matches the API level rather than a stale assumption.
 */
@RunWith(AndroidJUnit4::class)
class AppLockKeystoreContractTest {
    private val key = AppLockKey()

    /**
     * An auth-bound key can be created on this device, and arming yields a usable cipher.
     *
     * `sealCipher()` is the half of arming that runs *before* the prompt. If the platform refuses
     * here, the lock cannot be switched on at all — which is exactly the defect this test exists to
     * catch, on whichever API level reintroduces it.
     */
    @Test
    fun armingProducesACipherOnThisPlatform() {
        val request = key.sealCipher()

        assertNotNull("the device must be able to create an auth-bound key", request)
        assertEquals(
            "the shape of the request must follow the platform, not an assumption",
            AppLockKey.SUPPORTS_CRYPTO_OBJECT,
            request is AuthenticatedCipher.Bound,
        )
    }

    /**
     * The `CryptoObject` decision follows the API level, not an assumption.
     *
     * A wrong answer here is silent in both directions: claiming support on API 29 makes the prompt
     * throw, and denying it on API 30+ quietly downgrades the binding from *this* authentication to
     * *a recent* one.
     */
    @Test
    fun cryptoObjectSupportMatchesTheApiLevel() {
        assertEquals(
            "CryptoObject support must track API 30, not a hardcoded expectation",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            AppLockKey.SUPPORTS_CRYPTO_OBJECT,
        )
    }

    /**
     * A freshly armed key exists, and a cipher for opening it can be obtained.
     *
     * `unlockCipher` answering `null` means the key is gone or was invalidated by a new biometric
     * enrolment — a lock that can never be opened again. Directly after arming it must not say that,
     * or a member would be shown the unsatisfiable state the moment they switched the lock on.
     */
    @Test
    fun aFreshlyArmedKeyCanBeOpened() {
        val request = key.sealCipher()
        assertTrue("the key must exist right after it was created", key.exists())

        val iv = (request as? AuthenticatedCipher.Bound)?.cipher?.iv ?: ByteArray(GCM_IV_BYTES)
        val sealed = ByteArray(iv.size + SEALED_BODY_BYTES)
        iv.copyInto(sealed)

        assertNotNull(
            "a key created moments ago must not report itself unsatisfiable",
            key.unlockCipher(sealed),
        )
    }

    private companion object {
        /** Any non-empty body: these tests exercise cipher setup, never the plaintext. */
        const val SEALED_BODY_BYTES = 32

        /** GCM's IV length, needed to shape a blob when the platform defers the cipher. */
        const val GCM_IV_BYTES = 12
    }
}

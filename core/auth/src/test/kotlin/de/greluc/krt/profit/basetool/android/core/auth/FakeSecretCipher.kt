/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with an ordinary in-memory key, standing in for the Keystore one.
 *
 * This is the fake the whole test strategy of `core:auth` rests on (ADR-0002): the Android Keystore
 * cannot run on a JVM, so the seam is at the *cipher* and everything above it — what is written,
 * what a wipe removes, what happens when the key is gone — is the real implementation under test.
 *
 * Real AES rather than a pass-through on purpose. A fake that returned its input would let a store
 * that forgot to encrypt pass every test in this module.
 *
 * @property key the throwaway key; a fresh one per instance, so two fakes cannot read each other's
 *   blobs — which is what a restored-from-another-device ciphertext looks like
 */
class FakeSecretCipher(
    private var key: SecretKey = newKey(),
) : SecretCipher {
    /** Flipped by tests that need the "key invalidated by a new biometric enrolment" state. */
    var failDecryption: Boolean = false

    /** Set by [deleteKey], so a test can assert the wipe reached the key and not only the blob. */
    var keyDeleted: Boolean = false
        private set

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (failDecryption) throw SecretCipherException("key invalidated")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES))
        return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
    }

    /**
     * Replaces the key, exactly as the Keystore implementation destroys it.
     *
     * Replaced rather than nulled so a later call still behaves like the real one: encryption keeps
     * working for the next session, and anything written under the old key is now undecryptable —
     * which is the property the wipe exists for.
     */
    override fun deleteKey() {
        key = newKey()
        keyDeleted = true
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        /**
         * Generates a throwaway AES key.
         *
         * @return the key
         */
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(KEY_SIZE) }.generateKey()
    }
}

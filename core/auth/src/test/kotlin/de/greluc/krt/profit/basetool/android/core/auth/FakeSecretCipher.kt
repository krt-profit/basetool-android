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
 * AES-256-GCM with an in-memory key, standing in for the Keystore cipher (ADR-0002).
 *
 * Real encryption rather than a pass-through, so a store that forgot to encrypt fails its tests.
 *
 * @property key the throwaway key; fresh per instance, so two fakes cannot read each other's blobs
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
     * Replaces the key, so blobs written under the old one become undecryptable while encryption keeps working.
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

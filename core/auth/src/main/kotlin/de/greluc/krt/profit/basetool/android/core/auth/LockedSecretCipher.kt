/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

/**
 * The token cipher with the app lock's outer layer around it, leaving the inner [KeystoreSecretCipher] key unchanged.
 *
 * With the lock off, [SessionEnvelope.seal] passes the blob through and this decorator is inert.
 *
 * @property inner the Keystore-backed token cipher
 * @property envelope the outer layer; inert until the lock is armed and opened
 */
class LockedSecretCipher(
    private val inner: SecretCipher,
    private val envelope: SessionEnvelope,
) : SecretCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = envelope.seal(inner.encrypt(plaintext))

    override fun decrypt(ciphertext: ByteArray): ByteArray = inner.decrypt(envelope.open(ciphertext))

    /**
     * Destroys the inner key.
     *
     * The lock's own key is not touched here. It belongs to the lock's lifecycle — arming and
     * disarming — and a logout deletes it separately, so that wiping a session and switching off a
     * setting stay two different acts.
     */
    override fun deleteKey() {
        inner.deleteKey()
    }
}

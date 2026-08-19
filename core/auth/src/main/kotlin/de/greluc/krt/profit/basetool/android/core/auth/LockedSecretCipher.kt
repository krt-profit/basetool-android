/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

/**
 * The token cipher with the app lock's outer layer around it.
 *
 * A decorator rather than a change to [KeystoreSecretCipher], and that is the point: the inner key
 * keeps every property that makes storing a refresh token defensible at all — non-exportable,
 * device-bound, `setUnlockedDeviceRequired`, StrongBox where available. The lock adds a second
 * layer around its output; it does not weaken, replace or re-key the first.
 *
 * The alternative — making the token key itself auth-bound — was rejected for a concrete reason:
 * an auth-per-use Keystore key cannot be used unattended, and the app rewrites the refresh token
 * whenever the realm issues a new one. A background refresh cannot raise a fingerprint prompt, so
 * that design would have traded a working session for a locked drawer.
 *
 * With the lock off, [SessionEnvelope.seal] passes the blob through untouched, so this decorator is
 * inert and the stored form is byte-identical to what the app wrote before the lock existed.
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

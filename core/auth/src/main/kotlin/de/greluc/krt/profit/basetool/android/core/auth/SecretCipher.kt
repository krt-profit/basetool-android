/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

/**
 * Encrypts and decrypts the one secret this app persists: the refresh token.
 *
 * The interface exists so the storage logic can be tested without a device. Its production
 * implementation ([KeystoreSecretCipher]) keeps the key inside the Android Keystore, where key
 * material never enters the app process and cannot be exercised by a JVM unit test; a test fake
 * supplies the same contract with an in-memory key. The seam is deliberately at the *cipher*, not
 * at the store: what a test must be able to fake is the hardware-bound part, and everything above
 * it — what is written, when it is wiped — stays real.
 *
 * Implementations must be safe to call from any thread.
 */
interface SecretCipher {
    /**
     * Encrypts [plaintext].
     *
     * @param plaintext the secret; the caller is responsible for not logging it
     * @return an opaque blob that carries whatever the implementation needs to decrypt it (for
     *   AES-GCM, the IV precedes the ciphertext) — never a bare ciphertext the caller must pair
     *   with anything
     * @throws SecretCipherException if the key is unavailable or the operation fails
     */
    fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Decrypts a blob produced by [encrypt].
     *
     * @param ciphertext the blob as stored
     * @return the original plaintext
     * @throws SecretCipherException if the key is gone, the device is locked, or the blob does not
     *   authenticate — all three are ordinary states rather than programming errors, and the caller
     *   is expected to treat them as "no usable secret"
     */
    fun decrypt(ciphertext: ByteArray): ByteArray

    /**
     * Destroys the key, making every blob this cipher ever produced permanently undecryptable.
     *
     * Part of the contract rather than an implementation detail: a logout that deletes the stored
     * ciphertext but leaves the key behind still leaves a key that can decrypt any copy of that
     * ciphertext which escaped the device — a backup that slipped through, a forensic image. A
     * cipher that cannot be wiped cannot back a logout.
     *
     * Implementations must not throw: a key that cannot be deleted is not worth failing a logout
     * over, and the next login overwrites the entry anyway.
     */
    fun deleteKey()
}

/**
 * A cipher operation could not be completed.
 *
 * Thrown for every failure the caller cannot distinguish and should not try to: a key invalidated by
 * a new biometric enrolment, a locked device, a restored-from-backup blob that was never decryptable
 * on this device, or an authentication-tag mismatch. All of them mean the same thing upstream — the
 * stored secret is unusable and the member has to log in again.
 *
 * `open` so the one failure that is NOT of that kind can be a subtype: [AppLockedException] means
 * the stored secret is fine and merely sealed behind an app lock nobody has opened yet. Callers that
 * discard on failure must branch on it first, or they discard a good session.
 *
 * @param message what failed, never including key or plaintext material
 * @param cause the underlying JCA failure
 */
open class SecretCipherException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

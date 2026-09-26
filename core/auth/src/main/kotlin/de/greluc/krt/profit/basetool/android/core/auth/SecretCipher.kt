/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

/**
 * Encrypts and decrypts the refresh token, the one secret this app persists.
 *
 * Production uses [KeystoreSecretCipher]; tests supply an in-memory fake. Implementations must be
 * safe to call from any thread.
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
     *   authenticate; callers treat all three as no usable secret
     */
    fun decrypt(ciphertext: ByteArray): ByteArray

    /**
     * Destroys the key, making every blob this cipher produced permanently undecryptable; part of logout.
     *
     * Implementations must not throw.
     */
    fun deleteKey()
}

/**
 * A cipher operation could not be completed, meaning the stored secret is unusable and the member has to log in again.
 *
 * `open` for [AppLockedException], which means the secret is fine but sealed; callers that discard
 * on failure must branch on it first.
 *
 * @param message what failed, never including key or plaintext material
 * @param cause the underlying JCA failure
 */
open class SecretCipherException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

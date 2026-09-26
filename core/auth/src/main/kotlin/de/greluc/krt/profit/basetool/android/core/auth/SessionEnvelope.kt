/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The outer layer that makes the app lock protect the refresh token at rest.
 *
 * The auth-bound key seals a random 256-bit session key; unlocking recovers it into memory, where
 * the process can read and write the token until the next cold start. [MAGIC] marks sealed blobs,
 * so a blob written before the lock was armed stays readable.
 */
class SessionEnvelope {
    /**
     * The session key, present only between an unlock and the end of the process.
     *
     * Deliberately not persisted anywhere: persisting it would recreate exactly the property this
     * class exists to remove, namely a token blob readable without an authentication.
     */
    @Volatile
    private var sessionKey: ByteArray? = null

    /** Whether the envelope can currently open a sealed blob. */
    val isOpen: Boolean get() = sessionKey != null

    /**
     * Adopts a session key recovered from the auth-bound lock key.
     *
     * @param key the 256-bit key the unlock decrypted
     */
    fun unlocked(key: ByteArray) {
        sessionKey = key.copyOf()
    }

    /**
     * Forgets the session key.
     *
     * Called on logout and when the lock is disarmed. Re-locking on the background timeout does
     * **not** call this: the member is the same person coming back to the same process, and forcing
     * a full token re-read would buy nothing while adding a failure mode.
     */
    fun close() {
        sessionKey?.fill(0)
        sessionKey = null
    }

    /**
     * Mints a fresh session key.
     *
     * @return 32 random bytes, to be sealed with the auth-bound key and adopted here
     */
    fun newSessionKey(): ByteArray = ByteArray(KEY_LENGTH_BYTES).also(RANDOM::nextBytes)

    /**
     * Adds the outer layer, or passes [inner] through unchanged while the lock is off.
     *
     * @param inner the token cipher's output
     * @return the sealed blob, or [inner] unchanged when the lock is off
     * @throws SecretCipherException if sealing fails, so nothing half-written reaches the store
     */
    fun seal(inner: ByteArray): ByteArray {
        val key = sessionKey ?: return inner
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            val iv = cipher.iv
            require(iv.size == IV_LENGTH_BYTES) { "unexpected GCM IV length" }
            MAGIC + iv + cipher.doFinal(inner)
        } catch (failure: GeneralSecurityException) {
            throw SecretCipherException("session envelope could not be sealed", failure)
        }
    }

    /**
     * Removes the outer layer, if there is one.
     *
     * @param stored the blob as it came out of the store
     * @return the token cipher's ciphertext
     * @throws AppLockedException when the blob is sealed and no unlock has happened; the token is fine
     * @throws SecretCipherException when the sealed blob cannot be opened with the session key
     */
    fun open(stored: ByteArray): ByteArray {
        if (!isSealed(stored)) {
            return stored
        }
        val key = sessionKey ?: throw AppLockedException("the app lock has not been opened yet")
        return unseal(stored, key)
    }

    /**
     * Decrypts a sealed blob with a known session key.
     *
     * @param stored the sealed blob
     * @param key the session key
     * @return the token cipher's ciphertext
     * @throws SecretCipherException when the blob does not authenticate under [key], or is too short
     *   to carry its own IV
     */
    private fun unseal(
        stored: ByteArray,
        key: ByteArray,
    ): ByteArray =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, ALGORITHM),
                GCMParameterSpec(TAG_LENGTH_BITS, stored, MAGIC.size, IV_LENGTH_BYTES),
            )
            val offset = MAGIC.size + IV_LENGTH_BYTES
            cipher.doFinal(stored, offset, stored.size - offset)
        } catch (failure: GeneralSecurityException) {
            throw SecretCipherException("session envelope could not be opened", failure)
        } catch (malformed: IllegalArgumentException) {
            throw SecretCipherException("sealed blob is malformed", malformed)
        }

    /**
     * Whether a stored blob carries the outer layer.
     *
     * @param stored the blob to inspect
     * @return `true` when it begins with [MAGIC] and is long enough to hold an IV
     */
    fun isSealed(stored: ByteArray): Boolean =
        stored.size > MAGIC.size + IV_LENGTH_BYTES &&
            MAGIC.indices.all { stored[it] == MAGIC[it] }

    private companion object {
        /**
         * The process-wide [SecureRandom], shared because `nextBytes` is thread-safe.
         */
        val RANDOM = SecureRandom()

        const val ALGORITHM = "AES"

        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** 256-bit session key, matching the token cipher's strength. */
        const val KEY_LENGTH_BYTES = 32

        const val IV_LENGTH_BYTES = 12

        const val TAG_LENGTH_BITS = 128

        /** Marks a blob as carrying the outer layer. `KRT` plus a format version. */
        val MAGIC = byteArrayOf('K'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte(), 1)
    }
}

/**
 * The stored token is sealed and the app lock has not been opened.
 *
 * Unlike a plain [SecretCipherException], the token is good and must not be discarded.
 *
 * @param message what could not be done
 */
class AppLockedException(
    message: String,
) : SecretCipherException(message, null)

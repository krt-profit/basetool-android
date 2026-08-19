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
 * The outer layer that makes the app lock reach the refresh token at rest.
 *
 * Without it the lock guards the *screen*: the token blob is still decryptable by anything that can
 * ask the Keystore, and the lock stops someone holding the phone rather than someone reading
 * app-private storage. With it the blob at rest is `lock(token-key(refresh token))`, and the outer
 * layer cannot be removed without a user authentication.
 *
 * **Why a session key instead of the auth-bound key doing the work directly.** An auth-per-use
 * Keystore key cannot *write* unattended, and the app writes the refresh token whenever the realm
 * issues a new one — a background refresh cannot raise a fingerprint prompt. So the auth-bound key
 * seals one thing, once: a random 256-bit session key. Unlocking recovers that key; from then on the
 * process holds it and can both read and write. The next cold start has to authenticate again,
 * because the session key lives only in memory.
 *
 * That also upgrades what the authentication *is*. The previous design decrypted a fixed sentinel —
 * true, verifiable, and still a gesture. Here the authentication produces the material without which
 * the session cannot be read at all.
 *
 * **The session key in memory is not a new exposure.** The refresh token itself and the access token
 * already live there for the same process lifetime; anything able to read this process's heap has
 * them already. What changes is the value of the bytes **at rest**, which is where the lock was
 * missing.
 *
 * **Self-describing, because both forms must coexist.** A blob written before the lock was armed has
 * no outer layer, and one written after does. [MAGIC] distinguishes them so a member who arms the
 * lock mid-session is not locked out of the token they already had. A four-byte collision with a
 * random inner blob is possible at 2⁻³², and its consequence is an unwrap that fails — read as an
 * unusable token, i.e. one more login.
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
     * Adds the outer layer, when there is a session key to add it with.
     *
     * Passing the blob through unchanged while the lock is off is what keeps this a decorator rather
     * than a mode: the store above does not know which of the two it is holding.
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
     * @throws AppLockedException when the blob is sealed and no unlock has happened — a state the
     *   caller must **not** treat as a corrupt token, because the token is fine and the member has
     *   simply not authenticated yet
     * @throws SecretCipherException when the blob is sealed and cannot be opened with the session
     *   key, which does mean an unusable token
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
     * Split out so [open] carries exactly one throw — the "locked" one, which is the branch a caller
     * must treat differently. Everything below means the same thing to that caller: unusable token.
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
         * One instance for the process, rather than one per key.
         *
         * Not a predictability fix — a fresh [SecureRandom] seeds from the system entropy source, so
         * the per-call form was sound, unlike the same pattern with `java.util.Random`. It is simply
         * the correct shape: constructing one can re-seed, `nextBytes` is thread-safe, and a shared
         * instance removes a cost paid for nothing.
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
 * A subclass of [SecretCipherException] so existing handling still catches it, but distinct because
 * the two mean opposite things: a plain [SecretCipherException] says the stored token is unusable
 * and should be discarded, this one says it is perfectly good and simply not available yet.
 * Discarding on this would log a member out for the crime of not having authenticated.
 *
 * @param message what could not be done
 */
class AppLockedException(
    message: String,
) : SecretCipherException(message, null)

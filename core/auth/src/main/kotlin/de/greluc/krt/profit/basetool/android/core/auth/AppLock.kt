/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import javax.crypto.Cipher

/**
 * The app lock, as its caller needs it: armed or not, a cipher to authenticate, a sentinel to open.
 *
 * An interface because the implementation is Keystore-bound and therefore untestable off a device,
 * while the *rules* around it — when to lock, what a failed decrypt means, that opening requires a
 * real decrypt — are exactly what has to be pinned.
 */
interface AppLock {
    /**
     * Whether a lock stands.
     *
     * @return `true` when both the sealed sentinel and its key are present
     */
    suspend fun isArmed(): Boolean

    /**
     * Creates the key and seals a sentinel with it.
     *
     * @throws SecretCipherException if the device cannot create or use an auth-bound key
     */
    suspend fun arm()

    /**
     * Removes the key and the sentinel.
     */
    suspend fun disarm()

    /**
     * Prepares the decrypt cipher a prompt has to authenticate.
     *
     * @return the initialised cipher, or `null` when the lock can no longer be opened — the key was
     *   invalidated by a new biometric enrolment, or is gone
     */
    suspend fun unlockCipher(): Cipher?

    /**
     * Decrypts the sentinel with an authenticated cipher.
     *
     * @param cipher the cipher the platform vouched for
     * @return `true` when the sentinel came back intact, which is the only thing that opens the gate
     */
    suspend fun open(cipher: Cipher): Boolean
}

/**
 * The real lock: an auth-bound Keystore key ([AppLockKey]) plus the sealed sentinel beside the
 * refresh token ([AppLockSetting]).
 *
 * The two are kept in step here rather than by the caller, because they can disagree in ways that
 * strand a member: a sentinel with no key cannot be opened, and a key with no sentinel guards
 * nothing. [isArmed] therefore requires both, and [arm] writes them together.
 *
 * @property key the Keystore side
 * @property setting the stored side
 */
class KeystoreAppLock(
    private val key: AppLockKey,
    private val setting: AppLockSetting,
) : AppLock {
    override suspend fun isArmed(): Boolean = setting.sealedSentinel() != null && key.exists()

    override suspend fun arm() {
        setting.arm(key.arm())
    }

    override suspend fun disarm() {
        setting.disarm()
        key.disarm()
    }

    override suspend fun unlockCipher(): Cipher? =
        setting.sealedSentinel()?.let(key::unlockCipher)

    override suspend fun open(cipher: Cipher): Boolean {
        val sealed = setting.sealedSentinel() ?: return false
        return key.open(cipher, sealed)
    }
}

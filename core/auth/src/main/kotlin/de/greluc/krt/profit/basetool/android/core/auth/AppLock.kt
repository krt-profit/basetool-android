/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import javax.crypto.Cipher

/**
 * The app lock, as its caller needs it: armed or not, a cipher to authenticate, a session to open.
 *
 * An interface because the implementation is Keystore-bound and therefore untestable off a device,
 * while the *rules* around it — when to lock, what a failed decrypt means, that opening requires a
 * real decrypt, that arming must not strand an existing session — are exactly what has to be pinned.
 */
interface AppLock {
    /**
     * Whether a lock stands.
     *
     * @return `true` when both the sealed session key and its Keystore key are present
     */
    suspend fun isArmed(): Boolean

    /**
     * Creates the key and returns the cipher a prompt must authenticate before [completeArm].
     *
     * Two-phase because the lock key is auth-per-use: Keystore refuses to encrypt with it without
     * an authentication, exactly as it refuses to decrypt. Arming is therefore a prompt, not a
     * silent toggle — which also makes "armed" imply "satisfiable".
     *
     * @return the cipher to hand to the prompt
     * @throws SecretCipherException if the device cannot create an auth-bound key at all
     */
    suspend fun prepareArm(): Cipher

    /**
     * Seals a fresh session key with the authenticated cipher and re-seals the stored token.
     *
     * @param cipher the cipher the platform vouched for
     * @throws SecretCipherException if sealing fails despite the authentication
     */
    suspend fun completeArm(cipher: Cipher)

    /**
     * Removes the outer layer from the stored token, then the key and the sealed session key.
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
     * Recovers the session key with an authenticated cipher.
     *
     * @param cipher the cipher the platform vouched for
     * @return `true` when the session key came back, which is the only thing that opens the gate
     *   **and** the only thing that makes the stored refresh token readable
     */
    suspend fun open(cipher: Cipher): Boolean
}

/**
 * The real lock: an auth-bound Keystore key sealing the session key that the token store's outer
 * layer is built from.
 *
 * **What the authentication buys, precisely.** [SessionEnvelope] wraps the token cipher's output
 * with a random session key; that session key exists on disk only as ciphertext under the auth-bound
 * Keystore key, and in memory only after an unlock. So the refresh token at rest is unreadable
 * without a user authentication — which is the half of `REQ-APP-AUTH-010` a screen-only lock left
 * open. Nothing about the inner token key changes: it stays non-exportable, device-bound and
 * `setUnlockedDeviceRequired`.
 *
 * **Arming and disarming rewrite the stored token, and that is the delicate part.** A member arming
 * the lock mid-session already has an unsealed blob; one disarming has a sealed one. Both are
 * rewritten through the store so the two forms never disagree with the setting. Every failure path
 * below prefers *losing the stored session* over leaving an unreadable blob behind — the cost of the
 * first is one login, and the cost of the second is a member who cannot get in at all.
 *
 * @property key the Keystore side
 * @property setting the sealed session key, stored beside the refresh token
 * @property envelope the in-memory session key and the wrap format
 * @property tokenStore rewritten on arm and disarm so its form matches the setting
 */
class KeystoreAppLock(
    private val key: AppLockKey,
    private val setting: AppLockSetting,
    private val envelope: SessionEnvelope,
    private val tokenStore: RefreshTokenStore,
) : AppLock {
    override suspend fun isArmed(): Boolean = setting.sealedSessionKey() != null && key.exists()

    /**
     * Seals the session key and re-seals the stored token.
     *
     * The order matters. The refresh token is read **before** the envelope is opened, while the
     * stored blob is still unsealed; it is written back **after**, which seals it. Reversing the two
     * would try to open a blob that has no outer layer yet.
     *
     * A token that cannot be read here is not an error to report: the member simply has no usable
     * session, and arming a lock over nothing is perfectly valid.
     */
    override suspend fun prepareArm(): Cipher = key.sealCipher()

    override suspend fun completeArm(cipher: Cipher) {
        val existing = tokenStore.read()
        val sessionKey = envelope.newSessionKey()
        setting.arm(key.seal(cipher, sessionKey))
        envelope.unlocked(sessionKey)
        if (existing != null) {
            tokenStore.write(existing)
        }
    }

    /**
     * Disarms the lock.
     *
     * Mirrors [arm]: read while the envelope is still open, close it, write back unsealed. If the
     * envelope is **not** open — the member disarmed from a state this process never unlocked, which
     * the gate makes unlikely but not impossible — the sealed blob would be unreadable afterwards,
     * so it is cleared instead. One login, rather than a token nothing can open.
     */
    override suspend fun disarm() {
        val existing =
            if (envelope.isOpen) {
                tokenStore.read()
            } else {
                KrtLog.w(LOG_TAG) { "disarming without an open envelope; dropping the sealed token" }
                null
            }
        envelope.close()
        setting.disarm()
        key.disarm()
        if (existing != null) {
            tokenStore.write(existing)
        } else {
            tokenStore.clear()
        }
    }

    override suspend fun unlockCipher(): Cipher? = setting.sealedSessionKey()?.let(key::unlockCipher)

    override suspend fun open(cipher: Cipher): Boolean {
        val sessionKey = setting.sealedSessionKey()?.let { key.open(cipher, it) }
        sessionKey?.let(envelope::unlocked)
        return sessionKey != null
    }

    private companion object {
        /** Log subsystem; no key material ever appears in a message. */
        const val LOG_TAG = "lock"
    }
}

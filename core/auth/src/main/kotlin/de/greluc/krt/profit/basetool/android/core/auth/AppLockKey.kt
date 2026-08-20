/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Keystore key that makes the app lock real rather than a boolean.
 *
 * **Why this exists at all.** A lock implemented as "the prompt said yes, so set `locked = false`"
 * never uses the authentication for anything — the platform's answer is read and thrown away, and
 * the gate is one mis-ordered state transition away from opening on its own. CodeQL names this
 * exactly ("Insecure local authentication: this authentication callback does not use its result for
 * a cryptographic operation"), and it is right: the fix is not to silence the query but to make the
 * gate depend on an operation the platform will only permit after a real authentication.
 *
 * So the lock owns an AES-256-GCM key created with
 * [KeyGenParameterSpec.Builder.setUserAuthenticationRequired]. When the member switches the lock on,
 * it seals the **session key** that [SessionEnvelope] wraps the stored refresh token with; opening
 * the app requires decrypting that session key back. Keystore refuses the operation unless the user
 * authenticated, so the gate cannot open without one — and neither can the token at rest, which is
 * what separates this from a lock that only guards the screen.
 *
 * **One path, and that is the point of the minSdk floor.** The key is created with
 * `setUserAuthenticationParameters(0, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`, which produces an
 * *auth-per-use* key — the only kind `BiometricPrompt.CryptoObject` accepts. The cipher that
 * decrypts the session key is therefore the very one the prompt vouched for, the tightest binding
 * the platform offers.
 *
 * API 29 needed a second, weaker path: no `setUserAuthenticationParameters`, a *time-bound* key no
 * `CryptoObject` accepts, and an authentication the operation could only follow rather than be
 * bound to. It is gone with the floor (ADR-0006), and with it an ordering hazard that had nothing
 * to do with strength — on a time-bound key `Cipher.init` throws until an authentication exists,
 * so every call here ran in the opposite order from the one above.
 *
 * **What this deliberately does not do** is re-key or replace [KeystoreSecretCipher]. Making the
 * token key itself auth-bound would look tidier and would break the app: an auth-per-use Keystore
 * key cannot be used unattended, and the refresh token is rewritten whenever the realm issues a new
 * one — a background refresh cannot raise a fingerprint prompt. The outer layer exists precisely so
 * the inner key keeps working the way it must.
 *
 * @property alias Keystore entry name; separate from the token cipher's so either can be wiped alone
 */
class AppLockKey(
    private val alias: String = DEFAULT_ALIAS,
) {
    /**
     * Whether a lock key exists, i.e. the member has armed the lock.
     *
     * @return `true` when the entry is present
     */
    fun exists(): Boolean = keyStore().containsAlias(alias)

    /**
     * Creates the key and returns the cipher that will seal the session key.
     *
     * **Arming needs an authentication, and that is not a UX choice.** The key is auth-per-use, so
     * Keystore refuses to ENCRYPT with it as firmly as it refuses to decrypt — an earlier revision
     * sealed the session key inline here and failed on every device with `Key user not
     * authenticated`, while every unit test stayed green because the Keystore is not exercised off
     * a device. The returned cipher therefore goes into a `BiometricPrompt.CryptoObject` exactly
     * like the unlock one, and [seal] finishes the job with what the prompt hands back.
     *
     * The prompt is worth having on its own terms: it makes "armed" imply "satisfiable". Without
     * it a member could switch the lock on for a credential they cannot produce, and find out at
     * the next cold start.
     *
     * A key already present is replaced, so enabling the lock twice cannot leave a sealed blob that
     * no key can open.
     *
     * @return the initialised encrypt cipher, to be authenticated before [seal]
     * @throws SecretCipherException if the device cannot create the key at all
     */
    fun sealCipher(): Cipher =
        try {
            keyStore().deleteEntry(alias)
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, generateKey()) }
        } catch (failure: GeneralSecurityException) {
            throw SecretCipherException("app-lock key could not be created", failure)
        }

    /**
     * Seals [sessionKey] with the cipher the platform authenticated.
     *
     * @param cipher the cipher from [sealCipher], vouched for by the prompt
     * @param sessionKey the freshly minted session key the token store's outer layer is built from
     * @return the sealed session key, to be stored beside the setting
     * @throws SecretCipherException if the operation fails, which on this path means the
     *   authentication did not actually cover it
     */
    fun seal(
        cipher: Cipher,
        sessionKey: ByteArray,
    ): ByteArray =
        try {
            val iv = cipher.iv
            require(iv.size == IV_LENGTH_BYTES) { "unexpected GCM IV length" }
            iv + cipher.doFinal(sessionKey)
        } catch (failure: GeneralSecurityException) {
            throw SecretCipherException("app-lock key could not be armed", failure)
        }

    /**
     * Prepares the decrypt cipher the prompt has to vouch for.
     *
     * Handing this to `BiometricPrompt.CryptoObject` is what ties the authentication to the
     * operation; the caller then passes the **returned** cipher to [open].
     *
     * @param sealed the sentinel produced by [arm]
     * @return the initialised cipher, or `null` when the key is gone or no longer valid — a new
     *   biometric enrolment invalidates it, and the member then has no way past the lock except
     *   signing out
     */
    fun unlockCipher(sealed: ByteArray): Cipher? =
        try {
            require(sealed.size > IV_LENGTH_BYTES) { "sealed sentinel is too short to be valid" }
            val key = keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key.secretKey,
                    GCMParameterSpec(TAG_LENGTH_BITS, sealed, 0, IV_LENGTH_BYTES),
                )
            }
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            // A new fingerprint was enrolled. The key is gone for good; the app-lock cannot be
            // satisfied any more and the member's only route is a fresh login (security concept §4).
            KrtLog.w(LOG_TAG, invalidated) { "app-lock key was invalidated by a new enrolment" }
            null
        } catch (failure: GeneralSecurityException) {
            KrtLog.w(LOG_TAG, failure) { "app-lock cipher could not be prepared" }
            null
        } catch (malformed: IllegalArgumentException) {
            KrtLog.w(LOG_TAG, malformed) { "sealed sentinel is malformed" }
            null
        }

    /**
     * Recovers the session key with an authenticated cipher.
     *
     * **This is the operation the whole lock rests on**, and it is no longer a gesture: what comes
     * back is the key the refresh token's outer layer is built from, so an app that skipped this
     * step cannot read the stored session at all. The cipher must be the one the prompt returned,
     * so Keystore performs the operation only for an authenticated user.
     *
     * @param cipher the cipher from [unlockCipher], authenticated by the platform
     * @param sealed the same blob that was passed to [unlockCipher]
     * @return the session key, or `null` when it could not be recovered
     */
    fun open(
        cipher: Cipher,
        sealed: ByteArray,
    ): ByteArray? =
        try {
            cipher.doFinal(sealed, IV_LENGTH_BYTES, sealed.size - IV_LENGTH_BYTES)
        } catch (failure: GeneralSecurityException) {
            // Includes UserNotAuthenticatedException: whatever the prompt reported, the
            // authentication did not actually cover this operation. Refusing is correct.
            KrtLog.w(LOG_TAG, failure) { "app-lock session key did not open" }
            null
        }

    /**
     * Removes the key, disarming the lock.
     *
     * Part of logout as well as of switching the setting off: a device handed on with the app signed
     * out should carry no key that once guarded it.
     */
    fun disarm() {
        try {
            keyStore().deleteEntry(alias)
        } catch (failure: GeneralSecurityException) {
            KrtLog.w(LOG_TAG, failure) { "app-lock key could not be deleted" }
        }
    }

    /**
     * Generates the key, retrying without StrongBox when the device has no secure element.
     *
     * @return the freshly generated key
     */
    private fun generateKey(): SecretKey =
        try {
            generateKey(useStrongBox = true)
        } catch (unavailable: StrongBoxUnavailableException) {
            KrtLog.w(LOG_TAG, unavailable) { "StrongBox unavailable, falling back to a TEE-backed lock key" }
            generateKey(useStrongBox = false)
        }

    /**
     * Generates the auth-bound key for this platform level.
     *
     * @param useStrongBox whether to request the secure element
     * @return the generated key
     */
    private fun generateKey(useStrongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val builder =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(true)
                // A new fingerprint must not inherit the old key's authority. The cost is that the
                // lock can no longer be opened afterwards, which is why the screen offers a way out.
                .setInvalidatedByBiometricEnrollment(true)
                .apply { if (useStrongBox) setIsStrongBoxBacked(true) }

        // Auth-per-use (timeout 0) — the only kind a CryptoObject accepts.
        builder.setUserAuthenticationParameters(
            0,
            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
        )

        generator.init(builder.build())
        return generator.generateKey()
    }

    /**
     * Opens the Android Keystore.
     *
     * @return the loaded keystore
     */
    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        /** Log subsystem; no key material or sentinel ever appears in a message. */
        private const val LOG_TAG = "lock"

        private const val PROVIDER = "AndroidKeyStore"

        /** Entry name for the lock key; distinct from the token cipher's and the DPoP key's. */
        private const val DEFAULT_ALIAS = "krt.app-lock"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_SIZE_BITS = 256

        /** GCM's standard IV length; also what the Keystore provider emits. */
        private const val IV_LENGTH_BYTES = 12

        private const val TAG_LENGTH_BITS = 128
    }
}

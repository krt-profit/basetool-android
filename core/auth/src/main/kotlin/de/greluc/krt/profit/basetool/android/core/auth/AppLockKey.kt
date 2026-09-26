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
 * The auth-per-use AES-256-GCM Keystore key that seals the session key [SessionEnvelope] wraps the refresh token with.
 *
 * Opening the app decrypts the session key through a `BiometricPrompt.CryptoObject`, so neither the
 * gate nor the token at rest opens without a real authentication. It does not replace
 * [KeystoreSecretCipher], whose key must stay usable for unattended token refreshes.
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
     * Creates the key, replacing any existing one, and returns the cipher that will seal the session key.
     *
     * The key is auth-per-use, so the cipher must be authenticated through a `BiometricPrompt.CryptoObject`
     * before [seal].
     *
     * @return the initialised encrypt cipher, to be authenticated before [seal]
     * @throws SecretCipherException if the device cannot create the key
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
     * Prepares the decrypt cipher the prompt has to vouch for; the cipher the prompt returns goes to [open].
     *
     * @param sealed the sealed session key produced by [arm]
     * @return the initialised cipher, or `null` when the key is gone or invalidated by a new biometric
     *   enrolment
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
     * Recovers the session key with the cipher the prompt authenticated.
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
            KrtLog.w(LOG_TAG, failure) { "app-lock session key did not open" }
            null
        }

    /**
     * Removes the key, disarming the lock; also called on logout.
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
                .setInvalidatedByBiometricEnrollment(true)
                .apply { if (useStrongBox) setIsStrongBoxBacked(true) }

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

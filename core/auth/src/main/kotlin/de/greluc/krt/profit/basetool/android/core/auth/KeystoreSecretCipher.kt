/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM backed by a non-exportable key in the Android Keystore.
 *
 * This is the only place the app has a secret at rest, and three properties of the key are the
 * reason the refresh token may be stored at all (security concept §4):
 *
 * - **Non-exportable.** The key never enters the app process, so a ciphertext copied off the device
 *   — by a backup that slipped through, by a restore onto another phone — cannot be decrypted
 *   anywhere else.
 * - **[KeyGenParameterSpec.Builder.setUnlockedDeviceRequired].** While the device is locked the
 *   refresh token is cryptographically unusable, which is exactly the "stolen locked phone" case.
 *   It costs nothing here because the app only refreshes in the foreground — there is no push
 *   channel to wake it (decision Q2).
 * - **StrongBox where available**, with a fallback: [StrongBoxUnavailableException] is caught and
 *   the key regenerated without it. A device without a secure element still gets a TEE-backed key,
 *   which is strictly better than refusing to store anything and asking for a full login every
 *   time.
 *
 * `androidx.security:security-crypto` is deliberately **not** used — it is deprecated with no
 * successor (final release 1.1.0), which is a poor foundation for the one secret that matters.
 *
 * @property alias Keystore entry name; one per purpose so a wipe can be scoped
 */
class KeystoreSecretCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            require(iv.size == IV_LENGTH_BYTES) { "unexpected GCM IV length" }
            iv + cipher.doFinal(plaintext)
        } catch (failure: GeneralSecurityException) {
            throw SecretCipherException("encryption failed", failure)
        } catch (unavailable: ProviderException) {
            // The provider could not produce the key at all — distinct from a cryptographic
            // failure, and NOT a GeneralSecurityException: `ProviderException` extends
            // RuntimeException, so without this branch it walks past every caller's handler and
            // takes the process down.
            //
            // The case that produced it: `setUnlockedDeviceRequired(true)` on a device with no
            // secure lock screen. Android 12's keystore2 answers "User ECDH key missing" because
            // the per-user super-encryption key only exists once a lock is set. A member who has
            // never set one would have lost the app on the sign-in button.
            throw SecretCipherException("the Keystore could not provide a key", unavailable)
        }

    override fun decrypt(ciphertext: ByteArray): ByteArray =
        try {
            require(ciphertext.size > IV_LENGTH_BYTES) { "stored blob is too short to be valid" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, ciphertext, 0, IV_LENGTH_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), spec)
            cipher.doFinal(ciphertext, IV_LENGTH_BYTES, ciphertext.size - IV_LENGTH_BYTES)
        } catch (failure: GeneralSecurityException) {
            // Includes the ordinary states: key invalidated by a new biometric enrolment, device
            // locked under setUnlockedDeviceRequired, blob restored from another device. The
            // exception type is never surfaced further — all of them mean "log in again".
            throw SecretCipherException("decryption failed", failure)
        } catch (malformed: IllegalArgumentException) {
            throw SecretCipherException("stored blob is malformed", malformed)
        } catch (unavailable: ProviderException) {
            // Same reasoning as in `encrypt`: a provider that cannot hand over the key is a
            // "log in again" state, not a crash.
            throw SecretCipherException("the Keystore could not provide a key", unavailable)
        }

    /**
     * Removes the key, making every stored ciphertext permanently undecryptable.
     *
     * Called on logout in addition to deleting the stored blob: deleting the blob alone leaves a key
     * behind that could decrypt a copy of it, and a wipe should not depend on having found every
     * copy.
     */
    override fun deleteKey() {
        try {
            keyStore().deleteEntry(alias)
        } catch (failure: GeneralSecurityException) {
            // A key that cannot be deleted is not worth crashing a logout over; the blob is gone
            // either way and the next login overwrites the entry.
            KrtLog.w(LOG_TAG, failure) { "keystore entry could not be deleted" }
        }
    }

    /**
     * Loads the key, creating it on first use.
     *
     * @return the AES key bound to this device
     */
    private fun secretKey(): SecretKey {
        val store = keyStore()
        val existing = store.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return existing?.secretKey ?: generateKey()
    }

    /**
     * Generates the key, retrying without StrongBox when the device has no secure element.
     *
     * @return the freshly generated key
     */
    private fun generateKey(): SecretKey =
        try {
            // Always asked for: StrongBox is API 28+ and minSdk is 29, so a version guard here
            // would be dead code. Whether the device HAS a secure element is answered by the
            // exception below, not by an SDK level.
            generateKey(useStrongBox = true)
        } catch (unavailable: StrongBoxUnavailableException) {
            KrtLog.w(LOG_TAG, unavailable) { "StrongBox unavailable, falling back to a TEE-backed key" }
            generateKey(useStrongBox = false)
        }

    /**
     * Generates the Keystore key.
     *
     * @param useStrongBox whether to request the secure element
     * @return the generated key
     */
    private fun generateKey(useStrongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUnlockedDeviceRequired(true)
                .apply { if (useStrongBox) setIsStrongBoxBacked(true) }
                .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * Opens the Android Keystore.
     *
     * @return the loaded keystore
     */
    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        /** Log subsystem for key-lifecycle events; never for token material. */
        const val LOG_TAG = "auth"

        /** The Android Keystore provider name. */
        const val PROVIDER = "AndroidKeyStore"

        /** Default entry name for the refresh-token key. */
        const val DEFAULT_ALIAS = "krt.refresh-token"

        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** AES-256, per the security concept. */
        const val KEY_SIZE_BITS = 256

        /** The 12-byte IV GCM is specified for; anything else is a bug, not a variation. */
        const val IV_LENGTH_BYTES = 12

        /** Full 128-bit authentication tag. */
        const val TAG_LENGTH_BITS = 128
    }
}

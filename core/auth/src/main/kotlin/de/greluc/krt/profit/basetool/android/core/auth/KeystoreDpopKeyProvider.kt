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
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The per-install DPoP signing key, generated once inside the Android Keystore so it never leaves the device.
 *
 * [keyPair] returns the existing entry whenever there is one, since a regenerated key invalidates
 * the refresh token bound to the old one. Deleting it belongs to logout, beside
 * [SecretCipher.deleteKey].
 *
 * @property alias Keystore entry name; separate from the token cipher's so each can be wiped alone
 */
class KeystoreDpopKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) {
    /**
     * Returns this install's DPoP key pair, creating it on first use.
     *
     * @return the pair; the private half is a Keystore handle, not key material
     */
    fun keyPair(): DpopKeyPair {
        val store = keyStore()
        val existing = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        return existing?.let {
            DpopKeyPair(it.privateKey, it.certificate.publicKey as ECPublicKey)
        } ?: generate()
    }

    /**
     * Removes the key, ending every binding made with it.
     *
     * Never throws: a key that cannot be deleted is not worth failing a logout over, and the next
     * login replaces the entry.
     */
    fun deleteKey() {
        try {
            keyStore().deleteEntry(alias)
        } catch (failure: java.security.GeneralSecurityException) {
            KrtLog.w(LOG_TAG, failure) { "DPoP key could not be deleted" }
        }
    }

    /**
     * Generates the key, retrying without StrongBox when the device has no secure element.
     *
     * @return the freshly generated pair
     */
    private fun generate(): DpopKeyPair =
        try {
            generate(useStrongBox = true)
        } catch (unavailable: StrongBoxUnavailableException) {
            KrtLog.w(LOG_TAG, unavailable) { "StrongBox unavailable, falling back to a TEE-backed DPoP key" }
            generate(useStrongBox = false)
        }

    /**
     * Generates the P-256 signing key, without `setUnlockedDeviceRequired` so a locked screen delays a refresh rather
     * than failing it.
     *
     * @param useStrongBox whether to request the secure element
     * @return the generated pair
     */
    private fun generate(useStrongBox: Boolean): DpopKeyPair {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .apply { if (useStrongBox) setIsStrongBoxBacked(true) }
                .build()
        generator.initialize(spec)
        val pair = generator.generateKeyPair()
        return DpopKeyPair(pair.private, pair.public as ECPublicKey)
    }

    /**
     * Opens the Android Keystore.
     *
     * @return the loaded keystore
     */
    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        /** Log subsystem; no key material ever appears in a message. */
        const val LOG_TAG = "auth"

        const val PROVIDER = "AndroidKeyStore"

        /** Entry name for the DPoP key; distinct from the refresh-token cipher's. */
        const val DEFAULT_ALIAS = "krt.dpop-key"

        /** P-256, the curve RFC 9449's ES256 requires. */
        const val CURVE = "secp256r1"
    }
}

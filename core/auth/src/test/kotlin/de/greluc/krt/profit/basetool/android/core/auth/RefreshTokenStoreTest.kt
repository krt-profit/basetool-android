/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What the store must guarantee about the one secret this app keeps at rest.
 *
 * The cipher is faked — the Android Keystore cannot be exercised on a JVM, and the seam is
 * deliberately at the cipher so that everything above it (what is written, what happens when the
 * key is gone, what a wipe removes) is the real implementation. The hardware binding itself is an
 * instrumented concern and is marked open in `REQ-APP-AUTH-002`.
 *
 * The case worth the most here is the third: a token that cannot be decrypted must read as "no
 * session" and be cleared, not throw. It is the state a member lands in after a new fingerprint
 * enrolment, and it must present as a login prompt rather than a crash loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshTokenStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** An AES-GCM cipher with an ordinary in-memory key, standing in for the Keystore one. */
    private class FakeCipher(
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(KEY_SIZE) }.generateKey(),
    ) : SecretCipher {
        /** Flipped by tests that need the "key is gone" state. */
        var failDecryption: Boolean = false

        override fun encrypt(plaintext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.iv + cipher.doFinal(plaintext)
        }

        override fun decrypt(ciphertext: ByteArray): ByteArray {
            if (failDecryption) throw SecretCipherException("key invalidated")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES),
            )
            return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
        }

        private companion object {
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val KEY_SIZE = 256
            const val IV_BYTES = 12
            const val TAG_BITS = 128
        }
    }

    /**
     * Builds a store over a throwaway DataStore file.
     *
     * @param cipher the cipher to use
     * @return the store under test
     */
    private fun storeWith(cipher: SecretCipher): RefreshTokenStore {
        val file = File(temporaryFolder.newFolder(), "auth.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        return RefreshTokenStore(dataStore, cipher)
    }

    @Test
    fun `round-trips a token`() =
        runTest {
            val store = storeWith(FakeCipher())

            store.write(TOKEN)

            assertEquals(TOKEN, store.read())
        }

    @Test
    fun `reads null when nothing was ever written`() =
        runTest {
            assertNull(storeWith(FakeCipher()).read())
        }

    @Test
    fun `an undecryptable token reads as no session and is cleared`() =
        runTest {
            // The state after a new biometric enrolment invalidates the Keystore key, or after a
            // blob is restored onto a different device. A throw here would be a crash on start-up;
            // the correct behaviour is a login prompt.
            val cipher = FakeCipher()
            val store = storeWith(cipher)
            store.write(TOKEN)
            cipher.failDecryption = true

            assertNull(store.read())

            cipher.failDecryption = false
            assertNull("the unusable blob must not survive the failed read", store.read())
        }

    @Test
    fun `clear removes the token`() =
        runTest {
            val store = storeWith(FakeCipher())
            store.write(TOKEN)

            store.clear()

            assertNull(store.read())
        }

    @Test
    fun `writing twice keeps only the newer token`() =
        runTest {
            val store = storeWith(FakeCipher())

            store.write(TOKEN)
            store.write(OTHER_TOKEN)

            assertEquals(OTHER_TOKEN, store.read())
        }

    private companion object {
        const val TOKEN = "refresh-token-value"
        const val OTHER_TOKEN = "rotated-refresh-token"
    }
}

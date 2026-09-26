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

/**
 * Tests the refresh-token store over [FakeSecretCipher]: what is written, what a wipe removes, and that an
 * undecryptable token reads as no session and is cleared rather than thrown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshTokenStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
            val store = storeWith(FakeSecretCipher())

            store.write(TOKEN)

            assertEquals(StoredRefreshToken.Present(TOKEN), store.read())
        }

    @Test
    fun `reads null when nothing was ever written`() =
        runTest {
            assertEquals(StoredRefreshToken.Absent, storeWith(FakeSecretCipher()).read())
        }

    @Test
    fun `an undecryptable token reads as no session and is cleared`() =
        runTest {
            val cipher = FakeSecretCipher()
            val store = storeWith(cipher)
            store.write(TOKEN)
            cipher.failDecryption = true

            assertEquals(StoredRefreshToken.Absent, store.read())

            cipher.failDecryption = false
            assertEquals(
                "the unusable blob must not survive the failed read",
                StoredRefreshToken.Absent,
                store.read(),
            )
        }

    @Test
    fun `clear removes the token`() =
        runTest {
            val store = storeWith(FakeSecretCipher())
            store.write(TOKEN)

            store.clear()

            assertEquals(StoredRefreshToken.Absent, store.read())
        }

    @Test
    fun `writing twice keeps only the newer token`() =
        runTest {
            val store = storeWith(FakeSecretCipher())

            store.write(TOKEN)
            store.write(OTHER_TOKEN)

            assertEquals(StoredRefreshToken.Present(OTHER_TOKEN), store.read())
        }

    /**
     * A token sealed behind an unopened app lock is kept, not wiped.
     */
    @Test
    fun `a token sealed behind a closed lock is neither read nor destroyed`() =
        runTest {
            val envelope = SessionEnvelope()
            envelope.unlocked(envelope.newSessionKey())
            val store = storeWith(LockedSecretCipher(FakeSecretCipher(), envelope))
            store.write(TOKEN)

            envelope.close()

            assertEquals("a closed lock yields no token", StoredRefreshToken.Locked, store.read())

            envelope.unlocked(envelope.newSessionKey())
            assertEquals(
                "a different session key cannot open it either",
                StoredRefreshToken.Absent,
                store.read(),
            )
        }

    /**
     * A sealed token survives a locked read and opens once the right session key is restored.
     */
    @Test
    fun `a sealed token survives a locked read and opens afterwards`() =
        runTest {
            val envelope = SessionEnvelope()
            val sessionKey = envelope.newSessionKey()
            envelope.unlocked(sessionKey)
            val store = storeWith(LockedSecretCipher(FakeSecretCipher(), envelope))
            store.write(TOKEN)

            envelope.close()
            assertEquals(StoredRefreshToken.Locked, store.read())

            envelope.unlocked(sessionKey)

            assertEquals(StoredRefreshToken.Present(TOKEN), store.read())
        }

    private companion object {
        const val TOKEN = "refresh-token-value"
        const val OTHER_TOKEN = "rotated-refresh-token"
    }
}

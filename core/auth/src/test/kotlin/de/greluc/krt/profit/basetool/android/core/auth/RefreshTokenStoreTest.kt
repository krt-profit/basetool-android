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
 * What the store must guarantee about the one secret this app keeps at rest.
 *
 * The cipher is [FakeSecretCipher] — the Android Keystore cannot be exercised on a JVM, and the
 * seam is deliberately at the cipher so that everything above it (what is written, what happens
 * when the key is gone, what a wipe removes) is the real implementation. The hardware binding
 * itself is an instrumented concern and is marked open in `REQ-APP-AUTH-002`.
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
            // The state after a new biometric enrolment invalidates the Keystore key, or after a
            // blob is restored onto a different device. A throw here would be a crash on start-up;
            // the correct behaviour is a login prompt.
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
     * **A token sealed behind an unopened app lock is kept, not wiped.**
     *
     * The one failure here that must not behave like the others. Every other unreadable blob is
     * discarded, because it means the member has to log in again anyway — but a sealed blob is
     * perfectly good and merely waiting for a fingerprint. Wiping it would log somebody out for not
     * having authenticated yet, and it would do so silently, on the very first read after they armed
     * the lock.
     */
    @Test
    fun `a token sealed behind a closed lock is neither read nor destroyed`() =
        runTest {
            val envelope = SessionEnvelope()
            envelope.unlocked(envelope.newSessionKey())
            val store = storeWith(LockedSecretCipher(FakeSecretCipher(), envelope))
            store.write(TOKEN)

            // A cold start: same stored bytes, an envelope that has not been unlocked.
            envelope.close()

            // Locked, not Absent: the whole defect was that these two arrived as the same
            // value and every caller had to guess which had happened.
            assertEquals("a closed lock yields no token", StoredRefreshToken.Locked, store.read())

            envelope.unlocked(envelope.newSessionKey())
            assertEquals(
                "a different session key cannot open it either",
                StoredRefreshToken.Absent,
                store.read(),
            )
        }

    /**
     * The same token comes back once the right session key is restored.
     *
     * The counterpart to the test above: the blob survived being read while locked, so the member
     * who unlocks gets their session rather than a login screen.
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

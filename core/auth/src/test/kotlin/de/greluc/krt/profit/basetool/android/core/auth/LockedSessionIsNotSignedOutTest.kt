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
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * A session behind a closed app lock has not ended.
 *
 * A cold-start read of a sealed token yields [StoredRefreshToken.Locked], and nothing publishes
 * `SignedOut` before the member has unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedSessionIsNotSignedOutTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var envelope: SessionEnvelope
    private lateinit var store: RefreshTokenStore
    private lateinit var session: AuthSession

    @Before
    fun setUp() {
        envelope = SessionEnvelope()
        val cipher = FakeSecretCipher()
        val file = File(temporaryFolder.newFolder(), "auth.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        store = RefreshTokenStore(dataStore, LockedSecretCipher(cipher, envelope))
        val clock = ServerClock()
        val configuration =
            OidcConfiguration(
                issuer = "https://127.0.0.1:1/realms/iri",
                clientId = "basetool-android",
                redirectUri = "https://profit-base.online/app/callback",
                postLogoutRedirectUri = "https://profit-base.online/app/logout",
            )
        session =
            AuthSession(
                tokenClient =
                    TokenClient(
                        httpClient = OkHttpClient(),
                        configuration = configuration,
                        proofFactory = DpopProofFactory(generateKeyPair(), clock),
                        serverClock = clock,
                    ),
                refreshTokenStore = store,
                cipher = cipher,
                serverClock = clock,
            )
    }

    /** Writes a token the way an armed lock leaves it, then closes the lock as a restart would. */
    private suspend fun storeSealedTokenAndLock() {
        envelope.unlocked(envelope.newSessionKey())
        store.write(TOKEN)
        envelope.close()
    }

    @Test
    fun `a request made before the unlock does not end the session`() =
        runTest {
            storeSealedTokenAndLock()

            val result = session.refreshIfNeeded()

            assertNull("nothing can be refreshed while the token is sealed", result)
            assertEquals(
                "the session must still be unread, not ended — publishing SignedOut here is what" +
                    " put a member on the login screen after a successful unlock",
                SessionState.Unknown,
                session.state.value,
            )
        }

    @Test
    fun `a rejected access token before the unlock does not end the session either`() =
        runTest {
            storeSealedTokenAndLock()

            val token = session.refreshFor(refused = "stale-access-token")

            assertNull(token)
            assertEquals(SessionState.Unknown, session.state.value)
        }

    @Test
    fun `restoring while locked decides nothing rather than deciding wrongly`() =
        runTest {
            storeSealedTokenAndLock()

            val state = session.restore()

            assertNotEquals(
                "a sealed token is a session waiting for a fingerprint, not one that ended",
                SessionState.SignedOut,
                state,
            )
            assertEquals(SessionState.Unknown, session.state.value)
        }

    @Test
    fun `the token is still there for the unlock that follows`() =
        runTest {
            storeSealedTokenAndLock()

            session.refreshIfNeeded()
            session.restore()

            assertEquals(StoredRefreshToken.Locked, store.read())
        }

    @Test
    fun `an empty store still means signed out`() =
        runTest {
            val state = session.restore()

            assertEquals(SessionState.SignedOut, state)
        }

    /**
     * Generates an in-memory P-256 pair standing in for the Keystore one.
     *
     * @return the pair
     */
    private fun generateKeyPair(): DpopKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return DpopKeyPair(pair.private as ECPrivateKey, pair.public as ECPublicKey)
    }

    private companion object {
        const val TOKEN = "refresh-token-value"
    }
}

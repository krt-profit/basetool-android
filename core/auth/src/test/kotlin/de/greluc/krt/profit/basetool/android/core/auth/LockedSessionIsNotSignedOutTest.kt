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
 * A session behind a closed app lock has not ended, and nothing may say that it has.
 *
 * Reproduced on a device (Galaxy S25 Ultra, v0.1.2): with the lock armed, killing the app from the
 * recents list and starting it again showed the lock screen and then, after a successful unlock,
 * the **login** screen — every time, from a session that had been working seconds earlier, and
 * without a single line in the log.
 *
 * The chain was: `UpdateGate` sits above the lock gate by design and fires the version check
 * through the authenticated client; its interceptor asks [AuthSession.refreshIfNeeded]; on a cold
 * start there is no in-memory token so the store is read; the blob is sealed and the envelope is
 * still closed, so the read answered `null`; `null` meant "no session", so `SignedOut` was
 * published — before the member had even been offered the fingerprint prompt. By the time the gate
 * opened, `MainActivity`'s `restore()` was guarded on `Unknown` and skipped, and the login screen
 * is what `SignedOut` renders.
 *
 * Every link held its own contract. What was missing was a way for the store to say **which** of
 * two opposite things had happened, which is now [StoredRefreshToken.Locked].
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
                // Nothing here may be reached: every test below must decide before a request is
                // made, and a socket to this address would hang rather than fail fast if one were.
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
            // This is the exact call the version check makes through the API client's interceptor,
            // on a cold start, above the lock gate.
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
            // The interceptor's other entry point, reached when the backend refuses a token.
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

            // Nothing above may have cleared it: the member authenticates and gets their session.
            assertEquals(StoredRefreshToken.Locked, store.read())
        }

    @Test
    fun `an empty store still means signed out`() =
        runTest {
            // The other half of the contract. Making "locked" distinct must not make "nothing
            // stored" ambiguous — that one really is a member who has to log in.
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

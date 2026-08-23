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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The session is where a wrong decision costs the member their login.
 *
 * Two of these tests exist because the obvious implementation gets them backwards. A refresh that
 * fails on a train must not wipe the stored token — a tunnel is not a logout — while a refusal from
 * the realm must wipe it, or the app retries a dead grant on every start-up. And a refresh response
 * that carries no new refresh token must keep the old one, because the realm does not rotate them
 * and overwriting the field with `null` throws away the only way back into the session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthSessionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var store: RefreshTokenStore
    private lateinit var cipher: FakeSecretCipher
    private lateinit var configuration: OidcConfiguration
    private lateinit var session: AuthSession

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        configuration =
            OidcConfiguration(
                issuer = server.url("/realms/iri").toString(),
                clientId = "basetool-android",
                redirectUri = "https://profit-base.online/app/callback",
                postLogoutRedirectUri = "https://profit-base.online/app/logout",
            )
        cipher = FakeSecretCipher()
        val file = File(temporaryFolder.newFolder(), "auth.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        store = RefreshTokenStore(dataStore, cipher)
        val clock = ServerClock()
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

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `restores a stored session and exposes the access token synchronously`() =
        runTest {
            // Synchronously, because the OkHttp interceptor that reads it cannot suspend (ADR-0001).
            store.write(STORED_REFRESH)
            server.enqueue(grant())

            val state = session.restore()

            assertTrue("expected SignedIn, got $state", state is SessionState.SignedIn)
            assertEquals("access-value", session.currentAccessToken())
        }

    @Test
    fun `signs out when there is nothing stored`() =
        runTest {
            assertEquals(SessionState.SignedOut, session.restore())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `an unreachable realm leaves the stored session intact`() =
        runTest {
            // A tunnel is not a logout. Wiping here would ask the member for a password they never
            // needed, and the session is very probably still valid.
            store.write(STORED_REFRESH)
            server.close()

            val state = session.restore()

            assertTrue("expected Stale, got $state", state is SessionState.Stale)
            assertEquals("the stored token must survive an offline start", STORED_REFRESH, store.read())
        }

    @Test
    fun `a refused refresh token is cleared`() =
        runTest {
            // The opposite case: the grant is gone at the realm, so keeping the blob only means
            // failing again on every start-up.
            store.write(STORED_REFRESH)
            server.enqueue(oauthError())

            val state = session.restore()

            assertEquals(SessionState.SignedOut, state)
            assertNull(store.read())
        }

    @Test
    fun `concurrent refreshes send exactly one token request`() =
        runTest {
            // Several screens loading at once would each notice the expiry. Without single-flight
            // that is one token request per screen, each with a DPoP proof for the realm to verify,
            // and all but one result discarded.
            store.write(STORED_REFRESH)
            repeat(CONCURRENT_CALLERS) { server.enqueue(grant()) }

            coroutineScope {
                (1..CONCURRENT_CALLERS).map { async { session.refreshIfNeeded() } }.awaitAll()
            }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a refused token is exchanged even when it has not expired yet`() =
        runTest {
            // The server's 401 outranks the local expiry estimate: the device clock can be wrong
            // and a token can be revoked long before it runs out. refreshIfNeeded would do nothing
            // here, which is what left the app stuck on "Signal Lost" until it was restarted.
            store.write(STORED_REFRESH)
            server.enqueue(grant())
            session.restore()
            server.enqueue(grant())

            val renewed = session.refreshFor("access-value")

            assertEquals("access-value", renewed)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a token another caller already renewed is handed back unspent`() =
        runTest {
            // Every screen hits the 401 at the same moment. Only the first may spend the refresh
            // token; the rest must be given the result rather than each starting an exchange.
            store.write(STORED_REFRESH)
            server.enqueue(grant())
            session.restore()

            val renewed = session.refreshFor("a-token-from-before-the-refresh")

            assertEquals("access-value", renewed)
            assertEquals("no second exchange may be sent", 1, server.requestCount)
        }

    @Test
    fun `a refresh without a new refresh token keeps the stored one`() =
        runTest {
            // The realm does not rotate refresh tokens, so a response may legitimately omit it.
            // Taking that as "there is none now" would discard the only way back into the session.
            store.write(STORED_REFRESH)
            server.enqueue(grant(refreshToken = null))

            session.restore()

            assertEquals(STORED_REFRESH, store.read())
        }

    @Test
    fun `completing a login establishes the session`() =
        runTest {
            val request = authorizationRequest()
            server.enqueue(grant(nonce = request.nonce))

            val result = session.completeLogin(request, code = "auth-code")

            assertTrue("expected SignedIn, got $result", result is LoginResult.SignedIn)
            assertEquals("member-1", (result as LoginResult.SignedIn).claims?.subject)
            assertEquals(STORED_REFRESH, store.read())
        }

    @Test
    fun `an ID token minted for another attempt is refused`() =
        runTest {
            // The injection the nonce exists to prevent. Nothing is stored and no session starts.
            val request = authorizationRequest()
            server.enqueue(grant(nonce = "some-other-attempt"))

            val result = session.completeLogin(request, code = "auth-code")

            assertEquals(LoginResult.NonceMismatch, result)
            assertNull(store.read())
            assertNull(session.currentAccessToken())
        }

    @Test
    fun `a refused code exchange does not start a session`() =
        runTest {
            val request = authorizationRequest()
            server.enqueue(oauthError())

            val result = session.completeLogin(request, code = "used-already")

            assertTrue("expected Failed, got $result", result is LoginResult.Failed)
            assertEquals(SessionState.SignedOut, session.state.value)
        }

    @Test
    fun `logout wipes the token and the key and returns the end-session url`() =
        runTest {
            val request = authorizationRequest()
            server.enqueue(grant(nonce = request.nonce))
            session.completeLogin(request, code = "auth-code")
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build()) // revocation

            val endSession = session.logout()

            assertNull(session.currentAccessToken())
            assertEquals(SessionState.SignedOut, session.state.value)
            assertNull(store.read())
            assertTrue("the Keystore key must be destroyed too", cipher.keyDeleted)
            assertNotNull(endSession)
            assertTrue(endSession!!.startsWith(configuration.endSessionEndpoint))
            assertTrue(endSession.contains("id_token_hint="))
        }

    @Test
    fun `logout completes even when the realm refuses the revocation`() =
        runTest {
            // What protects the device is the local wipe; revocation only shortens the window for
            // a copy that escaped. A logout must not be blockable by the network.
            val request = authorizationRequest()
            server.enqueue(grant(nonce = request.nonce))
            session.completeLogin(request, code = "auth-code")
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_REQUEST).body("{}").build())

            session.logout()

            assertNull(store.read())
            assertTrue(cipher.keyDeleted)
            assertEquals(SessionState.SignedOut, session.state.value)
        }

    /**
     * Builds an authorization request against this test's realm.
     *
     * @return a fresh attempt, carrying the nonce the grant fixtures echo
     */
    private fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequestFactory(
            configuration,
            DpopProofFactory(generateKeyPair(), ServerClock()),
        ).create()

    /**
     * Builds a token-endpoint success body.
     *
     * @param refreshToken the refresh token to return, or `null` to omit the field entirely
     * @param nonce the nonce to embed in the ID token
     * @return the mock response
     */
    private fun grant(
        refreshToken: String? = STORED_REFRESH,
        nonce: String = "any-nonce",
    ): MockResponse {
        val refreshField = refreshToken?.let { """"refresh_token": "$it",""" } ?: ""
        return MockResponse
            .Builder()
            .code(HTTP_OK)
            .setHeader("Content-Type", "application/json")
            .body(
                """
                {
                  "access_token": "access-value",
                  "token_type": "Bearer",
                  "expires_in": 300,
                  $refreshField
                  "id_token": "${idToken(nonce)}",
                  "scope": "openid profile email roles"
                }
                """.trimIndent(),
            ).build()
    }

    /**
     * Builds the realm's "this grant is gone" answer.
     *
     * @return the mock response
     */
    private fun oauthError(): MockResponse =
        MockResponse
            .Builder()
            .code(HTTP_BAD_REQUEST)
            .setHeader("Content-Type", "application/json")
            .body("""{"error":"invalid_grant","error_description":"Session not active"}""")
            .build()

    /**
     * Builds an unsigned ID token carrying the claims the session reads.
     *
     * Unsigned on purpose: the app does not verify the signature — the token arrives directly from
     * the token endpoint over TLS, which OIDC Core section 3.1.3.7 accepts — so a fixture that
     * forged one would be testing a check that does not exist.
     *
     * @param nonce the `nonce` claim to embed
     * @return a three-part compact JWT
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun idToken(nonce: String): String {
        val payload = """{"sub":"member-1","nonce":"$nonce","preferred_username":"pilot"}"""
        val encoded =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(payload.toByteArray())
        return "header.$encoded.signature"
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
        const val STORED_REFRESH = "refresh-value"
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400

        /** Enough callers that a missing lock shows up as more than one request. */
        const val CONCURRENT_CALLERS = 8
    }
}

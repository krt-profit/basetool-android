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
 * Tests the session's decisions: a transport failure keeps the stored token, a realm refusal wipes it, and a refresh
 * without a new refresh token keeps the old one.
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
            store.write(STORED_REFRESH)
            server.close()

            val state = session.restore()

            assertTrue("expected Stale, got $state", state is SessionState.Stale)
            assertEquals("the stored token must survive an offline start", STORED_REFRESH, store.readTokenOrNull())
        }

    @Test
    fun `a transient failure leaves an established session standing`() =
        runTest {
            store.write(STORED_REFRESH)
            server.enqueue(grant())
            session.restore()
            server.close()

            val renewed = session.refreshFor("access-value")

            assertNull("a failed refresh hands back no token", renewed)
            assertTrue(
                "the session must survive a transient failure, got ${session.state.value}",
                session.state.value is SessionState.SignedIn,
            )
            assertEquals(STORED_REFRESH, store.readTokenOrNull())
        }

    @Test
    fun `a second restore does not spend another refresh`() =
        runTest {
            store.write(STORED_REFRESH)
            server.enqueue(grant())

            session.restore()
            val second = session.restore()

            assertTrue("expected SignedIn, got $second", second is SessionState.SignedIn)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a refused refresh token is cleared`() =
        runTest {
            store.write(STORED_REFRESH)
            server.enqueue(oauthError())

            val state = session.restore()

            assertEquals(SessionState.SignedOut, state)
            assertNull(store.readTokenOrNull())
        }

    @Test
    fun `concurrent refreshes send exactly one token request`() =
        runTest {
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
            store.write(STORED_REFRESH)
            server.enqueue(grant(refreshToken = null))

            session.restore()

            assertEquals(STORED_REFRESH, store.readTokenOrNull())
        }

    @Test
    fun `completing a login establishes the session`() =
        runTest {
            val request = authorizationRequest()
            server.enqueue(grant(nonce = request.nonce))

            val result = session.completeLogin(request, code = "auth-code")

            assertTrue("expected SignedIn, got $result", result is LoginResult.SignedIn)
            assertEquals("member-1", (result as LoginResult.SignedIn).claims?.subject)
            assertEquals(STORED_REFRESH, store.readTokenOrNull())
        }

    @Test
    fun `an ID token minted for another attempt is refused`() =
        runTest {
            val request = authorizationRequest()
            server.enqueue(grant(nonce = "some-other-attempt"))

            val result = session.completeLogin(request, code = "auth-code")

            assertEquals(LoginResult.NonceMismatch, result)
            assertNull(store.readTokenOrNull())
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
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

            val endSession = session.logout()

            assertNull(session.currentAccessToken())
            assertEquals(SessionState.SignedOut, session.state.value)
            assertNull(store.readTokenOrNull())
            assertTrue("the Keystore key must be destroyed too", cipher.keyDeleted)
            assertNotNull(endSession)
            assertTrue(endSession!!.startsWith(configuration.endSessionEndpoint))
            assertTrue(endSession.contains("id_token_hint="))
        }

    @Test
    fun `logout completes even when the realm refuses the revocation`() =
        runTest {
            val request = authorizationRequest()
            server.enqueue(grant(nonce = request.nonce))
            session.completeLogin(request, code = "auth-code")
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_REQUEST).body("{}").build())

            session.logout()

            assertNull(store.readTokenOrNull())
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
     * Builds an unsigned ID token carrying the claims the session reads; the app does not verify ID-token signatures.
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

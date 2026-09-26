/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import com.nimbusds.jwt.SignedJWT
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant

/**
 * Tests what the app sends to the token endpoint and how it reads each answer, with `invalid_grant` and hard errors
 * asserted separately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenClientTest {
    private lateinit var server: MockWebServer
    private lateinit var configuration: OidcConfiguration
    private lateinit var serverClock: ServerClock
    private lateinit var client: TokenClient

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        configuration =
            OidcConfiguration(
                issuer = server.url("/realms/iri").toString(),
                clientId = CLIENT_ID,
                redirectUri = REDIRECT_URI,
                postLogoutRedirectUri = POST_LOGOUT_URI,
            )
        serverClock = ServerClock()
        client =
            TokenClient(
                httpClient = OkHttpClient(),
                configuration = configuration,
                proofFactory = DpopProofFactory(generateKeyPair(), serverClock),
                serverClock = serverClock,
            )
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `exchanges an authorization code for a token set`() =
        runTest {
            server.enqueue(grantResponse())

            val result = client.exchangeCode(code = "auth-code", codeVerifier = "verifier-value")

            val tokens = (result as TokenResult.Granted).tokens
            assertEquals("access-value", tokens.accessToken)
            assertEquals("refresh-value", tokens.refreshToken)
            assertEquals("id-value", tokens.idToken)
            val form = formOf(server.takeRequest())
            assertEquals("authorization_code", form["grant_type"])
            assertEquals("auth-code", form["code"])
            assertEquals("verifier-value", form["code_verifier"])
            assertEquals(REDIRECT_URI, form["redirect_uri"])
            assertEquals(CLIENT_ID, form["client_id"])
        }

    @Test
    fun `every token request carries a proof bound to the token endpoint`() =
        runTest {
            server.enqueue(grantResponse())

            client.refresh("refresh-value")

            val recorded = server.takeRequest()
            val proof = SignedJWT.parse(recorded.headers[HEADER_DPOP])
            assertEquals("POST", proof.jwtClaimsSet.getStringClaim("htm"))
            assertEquals(configuration.tokenEndpoint, proof.jwtClaimsSet.getStringClaim("htu"))
            assertEquals("refresh_token", formOf(recorded)["grant_type"])
            assertEquals("refresh-value", formOf(recorded)["refresh_token"])
        }

    @Test
    fun `invalid_grant means the session ended, not that something went wrong`() =
        runTest {
            server.enqueue(errorResponse(HTTP_BAD_REQUEST, "invalid_grant", "Session not active"))

            val result = client.refresh("refresh-value")

            assertEquals(TokenResult.SessionEnded("Session not active"), result)
        }

    @Test
    fun `any other oauth error stays a rejection`() =
        runTest {
            server.enqueue(errorResponse(HTTP_BAD_REQUEST, "unauthorized_client", "Client not allowed"))

            val result = client.refresh("refresh-value")

            assertEquals(TokenResult.Rejected("unauthorized_client", "Client not allowed"), result)
        }

    @Test
    fun `a refusal without an oauth body keeps its status`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_GATEWAY).body("<html>502</html>").build())

            val result = client.refresh("refresh-value")

            assertEquals(TokenResult.Rejected("http_502", null), result)
        }

    @Test
    fun `a bound access token is named instead of handed on`() =
        runTest {
            server.enqueue(grantResponse(tokenType = "DPoP"))

            val result = client.refresh("refresh-value")

            assertEquals(TokenResult.AccessTokenBound("DPoP"), result)
        }

    @Test
    fun `retries once with the nonce the realm demanded`() =
        runTest {
            server.enqueue(
                errorResponse(HTTP_BAD_REQUEST, "use_dpop_nonce", "Missing nonce")
                    .newBuilder()
                    .addHeader(HEADER_DPOP_NONCE, "nonce-1")
                    .build(),
            )
            server.enqueue(grantResponse())

            val result = client.refresh("refresh-value")

            assertTrue("expected the retry to succeed, got $result", result is TokenResult.Granted)
            assertEquals(2, server.requestCount)
            assertNull(nonceOf(server.takeRequest()))
            assertEquals("nonce-1", nonceOf(server.takeRequest()))
        }

    @Test
    fun `it gives up after one nonce retry`() =
        runTest {
            repeat(2) {
                server.enqueue(
                    errorResponse(HTTP_BAD_REQUEST, "use_dpop_nonce", "Missing nonce")
                        .newBuilder()
                        .addHeader(HEADER_DPOP_NONCE, "nonce-$it")
                        .build(),
                )
            }

            val result = client.refresh("refresh-value")

            assertEquals(TokenResult.Rejected("use_dpop_nonce", "Missing nonce"), result)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a 2xx that is not a grant is malformed, not an empty session`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_OK).body("<html>sign in to wifi</html>").build())

            val result = client.refresh("refresh-value")

            assertTrue("expected Malformed, got $result", result is TokenResult.Malformed)
        }

    @Test
    fun `a realm that never answers is not a refusal`() =
        runTest {
            server.close()

            val result = client.refresh("refresh-value")

            assertTrue("expected Unreachable, got $result", result is TokenResult.Unreachable)
            assertTrue((result as TokenResult.Unreachable).cause is IOException)
        }

    @Test
    fun `access token expiry is stamped in server time`() =
        runTest {
            val deviceNow = Instant.now()
            serverClock.observe(serverTime = deviceNow.plusSeconds(DRIFT_SECONDS), deviceTime = deviceNow)
            server.enqueue(grantResponse())

            val tokens = (client.refresh("refresh-value") as TokenResult.Granted).tokens

            val fromDeviceClock = Duration.between(Instant.now(), tokens.accessTokenExpiresAt).seconds
            assertEquals(
                "expiry should sit $DRIFT_SECONDS s past device time plus the token lifetime",
                (DRIFT_SECONDS + EXPIRES_IN_SECONDS).toDouble(),
                fromDeviceClock.toDouble(),
                TOLERANCE_SECONDS.toDouble(),
            )
        }

    @Test
    fun `revocation is best effort and carries no proof`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_REQUEST).body("{}").build())

            val revoked = client.revokeRefreshToken("refresh-value")

            val recorded = server.takeRequest()
            assertEquals(false, revoked)
            assertNull("revocation must not send a DPoP proof", recorded.headers[HEADER_DPOP])
            val form = formOf(recorded)
            assertEquals("refresh-value", form["token"])
            assertEquals("refresh_token", form["token_type_hint"])
            assertEquals(CLIENT_ID, form["client_id"])
        }

    @Test
    fun `the end-session url carries the hint keycloak requires`() =
        runTest {
            val url = client.endSessionUri("id-token-value")

            assertTrue(url.startsWith(configuration.endSessionEndpoint))
            assertTrue(url.contains("id_token_hint=id-token-value"))
            assertTrue(url.contains("client_id=$CLIENT_ID"))
            assertTrue(url.contains("post_logout_redirect_uri="))
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

    /**
     * Builds a token-endpoint success body.
     *
     * @param tokenType the `token_type` to answer with
     * @return the mock response
     */
    private fun grantResponse(tokenType: String = "Bearer"): MockResponse =
        MockResponse
            .Builder()
            .code(HTTP_OK)
            .setHeader("Content-Type", "application/json")
            .body(
                """
                {
                  "access_token": "access-value",
                  "token_type": "$tokenType",
                  "expires_in": $EXPIRES_IN_SECONDS,
                  "refresh_token": "refresh-value",
                  "id_token": "id-value",
                  "scope": "openid profile email roles"
                }
                """.trimIndent(),
            ).build()

    /**
     * Builds an OAuth 2.0 error body.
     *
     * @param status the HTTP status
     * @param error the `error` code
     * @param description the `error_description`
     * @return the mock response
     */
    private fun errorResponse(
        status: Int,
        error: String,
        description: String,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(status)
            .setHeader("Content-Type", "application/json")
            .body("""{"error":"$error","error_description":"$description"}""")
            .build()

    /**
     * Decodes a recorded form body.
     *
     * @param request the recorded request
     * @return its form fields
     */
    private fun formOf(request: RecordedRequest): Map<String, String> =
        request.body
            ?.utf8()
            .orEmpty()
            .split("&")
            .filter { it.contains("=") }
            .associate { pair ->
                val (name, value) = pair.split("=", limit = 2)
                URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }

    /**
     * Reads the `nonce` claim of a recorded request's DPoP proof.
     *
     * @param request the recorded request
     * @return the nonce, or `null` when the proof carried none
     */
    private fun nonceOf(request: RecordedRequest): String? =
        SignedJWT
            .parse(request.headers[HEADER_DPOP])
            .jwtClaimsSet
            .getStringClaim("nonce")

    private companion object {
        const val CLIENT_ID = "basetool-android"
        const val REDIRECT_URI = "https://profit-base.online/app/callback"
        const val POST_LOGOUT_URI = "https://profit-base.online/app/logout"

        const val HEADER_DPOP = "DPoP"
        const val HEADER_DPOP_NONCE = "DPoP-Nonce"

        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_BAD_GATEWAY = 502

        /** The realm's per-client access-token lifetime. */
        const val EXPIRES_IN_SECONDS = 300L

        /** A device drift far outside Keycloak's 15 s skew allowance. */
        const val DRIFT_SECONDS = 45L

        /** Slack for the wall-clock reads these assertions bracket. */
        const val TOLERANCE_SECONDS = 5L
    }
}

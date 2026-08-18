/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The authorization request is the half of the login the app cannot verify afterwards: if a
 * parameter is wrong the realm either refuses, or — worse — accepts a weaker flow. And the redirect
 * is the one input that arrives from outside the app, so what it may and may not cause is asserted
 * case by case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthorizationRequestTest {
    private val proofFactory = DpopProofFactory(generateKeyPair(), ServerClock())

    private val configuration =
        OidcConfiguration(
            issuer = "https://keycloak.example/realms/iri",
            clientId = CLIENT_ID,
            redirectUri = REDIRECT_URI,
            postLogoutRedirectUri = "https://profit-base.online/app/logout",
        )

    private val factory = AuthorizationRequestFactory(configuration, proofFactory)

    @Test
    fun `asks for a code with S256 and the registered redirect`() {
        val request = factory.create()

        val url = request.url.toHttpUrl()
        assertEquals(configuration.authorizationEndpoint, url.newBuilder().query(null).build().toString())
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(CLIENT_ID, url.queryParameter("client_id"))
        assertEquals(REDIRECT_URI, url.queryParameter("redirect_uri"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals(request.pkce.challenge, url.queryParameter("code_challenge"))
        assertEquals("openid profile email roles", url.queryParameter("scope"))
    }

    @Test
    fun `names the DPoP key the grant must be bound to`() {
        // dpop_jkt (RFC 9449 section 10): it tells the realm which key the eventual grant belongs
        // to before any token exists, closing the window where an intercepted code could be
        // redeemed against a different one.
        val request = factory.create()

        assertEquals(
            proofFactory.publicKeyThumbprint(),
            request.url.toHttpUrl().queryParameter("dpop_jkt"),
        )
    }

    @Test
    fun `does not ask for offline access`() {
        // An offline token would outlive the SSO session the revocation levers act on, and the
        // client is not configured for it — the request would be refused rather than downgraded.
        assertTrue(
            "offline_access must not be requested",
            !factory.create().url.contains("offline_access"),
        )
    }

    @Test
    fun `state and nonce are fresh per attempt`() {
        val first = factory.create()
        val second = factory.create()

        assertNotEquals(first.state, second.state)
        assertNotEquals(first.nonce, second.nonce)
        assertNotEquals(first.pkce.verifier, second.pkce.verifier)
    }

    @Test
    fun `accepts the code from its own redirect`() {
        val request = factory.create()

        val response = request.readRedirect("$REDIRECT_URI?code=auth-code&state=${request.state}")

        assertEquals(AuthorizationResponse.Code("auth-code"), response)
    }

    @Test
    fun `reads a custom-scheme redirect too`() {
        // The dev realm registers de.kartell.basetool:/oauth2redirect, which no HTTP URL parser
        // will touch. Parsing the redirect with one would break every login on the build the flow
        // is actually developed against.
        val request = factory.create()

        val response =
            request.readRedirect("de.kartell.basetool:/oauth2redirect?code=auth-code&state=${request.state}")

        assertEquals(AuthorizationResponse.Code("auth-code"), response)
    }

    @Test
    fun `a redirect with a foreign state cannot steer the flow`() {
        // Not even into an error screen of its choosing: the state is checked before the error and
        // the code are looked at, so a redirect the app did not start decides nothing.
        val request = factory.create()

        val stolen = request.readRedirect("$REDIRECT_URI?code=someone-elses&state=not-ours")
        val alsoStolen = request.readRedirect("$REDIRECT_URI?error=access_denied&state=not-ours")

        assertEquals(AuthorizationResponse.StateMismatch, stolen)
        assertEquals(AuthorizationResponse.StateMismatch, alsoStolen)
    }

    @Test
    fun `a denial keeps its reason`() {
        val request = factory.create()

        val response =
            request.readRedirect(
                "$REDIRECT_URI?error=access_denied&error_description=User+cancelled&state=${request.state}",
            )

        assertEquals(AuthorizationResponse.Denied("access_denied", "User cancelled"), response)
    }

    @Test
    fun `a redirect with neither a code nor an error is unusable`() {
        val request = factory.create()

        val response = request.readRedirect("$REDIRECT_URI?state=${request.state}")

        assertTrue("expected Unusable, got $response", response is AuthorizationResponse.Unusable)
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
        const val CLIENT_ID = "basetool-android"
        const val REDIRECT_URI = "https://profit-base.online/app/callback"
    }
}

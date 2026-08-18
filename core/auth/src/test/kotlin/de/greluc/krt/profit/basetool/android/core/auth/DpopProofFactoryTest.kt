/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.SignedJWT
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * The proof format Keycloak's token endpoint accepts, and the timing that decides whether a member
 * can log in at all.
 *
 * The signature is verified with Nimbus's own verifier rather than being eyeballed: a proof that
 * parses but does not verify is exactly the failure that would reach production as "login broken".
 */
class DpopProofFactoryTest {
    private val keyPair = generateKeyPair()

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
     * Parses a proof and checks its signature against the embedded public key.
     *
     * @param proof the serialised JWT
     * @return the parsed JWT, already verified
     */
    private fun parseVerified(proof: String): SignedJWT {
        val jwt = SignedJWT.parse(proof)
        val embedded = ECKey.parse(jwt.header.jwk.toJSONObject())
        assertTrue("proof does not verify against its own jwk", jwt.verify(ECDSAVerifier(embedded)))
        return jwt
    }

    @Test
    fun `binds the proof to the method and uri`() {
        val factory = DpopProofFactory(keyPair, ServerClock())

        val jwt = parseVerified(factory.createProof("post", TOKEN_URI))

        assertEquals("POST", jwt.jwtClaimsSet.getStringClaim("htm"))
        assertEquals(TOKEN_URI, jwt.jwtClaimsSet.getStringClaim("htu"))
    }

    @Test
    fun `carries the header RFC 9449 requires`() {
        val factory = DpopProofFactory(keyPair, ServerClock())

        val jwt = parseVerified(factory.createProof("POST", TOKEN_URI))

        assertEquals("dpop+jwt", jwt.header.type.type)
        assertEquals(JWSAlgorithm.ES256, jwt.header.algorithm)
        assertNull("the private half must never be serialised", jwt.header.jwk.toECKey().d)
    }

    @Test
    fun `every proof has its own jti`() {
        // Keycloak rejects a replayed jti, so a factory that reused one would make the second
        // refresh of a session fail — intermittently, and only in the field.
        val factory = DpopProofFactory(keyPair, ServerClock())

        val first = parseVerified(factory.createProof("POST", TOKEN_URI)).jwtClaimsSet.jwtid
        val second = parseVerified(factory.createProof("POST", TOKEN_URI)).jwtClaimsSet.jwtid

        assertNotEquals(first, second)
    }

    @Test
    fun `iat follows server time, not the device clock`() {
        // The whole reason ServerClock exists: Keycloak allows 10 s lifetime with 15 s skew, so a
        // device a minute off cannot log in unless the proof is stamped with server time.
        val clock = ServerClock()
        val deviceNow = Instant.now()
        clock.observe(serverTime = deviceNow.plusSeconds(DRIFT_SECONDS), deviceTime = deviceNow)
        val factory = DpopProofFactory(keyPair, clock)

        val issuedAt = parseVerified(factory.createProof("POST", TOKEN_URI)).jwtClaimsSet.issueTime

        val drift = Duration.between(Instant.now(), issuedAt.toInstant())
        assertTrue(
            "iat was $drift from device time, expected about $DRIFT_SECONDS s",
            abs(drift.seconds - DRIFT_SECONDS) <= TOLERANCE_SECONDS,
        )
    }

    private companion object {
        /** Keycloak's token endpoint on the production realm. */
        const val TOKEN_URI = "https://keycloak.profit-base.online/realms/iri/protocol/openid-connect/token"

        /** A drift far outside Keycloak's 15 s skew allowance. */
        const val DRIFT_SECONDS = 45L

        /** Slack for the two wall-clock reads this assertion brackets. */
        const val TOLERANCE_SECONDS = 5L
    }
}

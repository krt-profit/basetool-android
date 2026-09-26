/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Date
import java.util.UUID

/**
 * Builds the DPoP proof JWTs for token-endpoint requests only (RFC 9449, ADR-0131).
 *
 * A proof on an ordinary API call would make Keycloak bind the access token, which the backend
 * rejects. `iat` comes from [ServerClock], since Keycloak tolerates only seconds of skew.
 *
 * @property keyPair the per-install P-256 key; its private half signs, its public half is embedded
 *   in the header as the `jwk` the server binds the refresh token to
 * @property serverClock the corrected time source
 */
class DpopProofFactory(
    private val keyPair: DpopKeyPair,
    private val serverClock: ServerClock,
) {
    private val signer: JWSSigner = ECDSASigner(keyPair.privateKey, Curve.P_256)

    private val publicJwk: ECKey =
        ECKey
            .Builder(Curve.P_256, keyPair.publicKey)
            .build()
            .toPublicJWK()

    /**
     * Builds a proof for one request.
     *
     * @param httpMethod the request method, upper case — `htm`
     * @param httpUri the request URI **without** query or fragment, as RFC 9449 requires — `htu`
     * @param nonce the last `DPoP-Nonce` the server issued, or `null` when it has issued none
     *   (RFC 9449 §8)
     * @return the serialised proof JWT for the `DPoP` header
     */
    fun createProof(
        httpMethod: String,
        httpUri: String,
        nonce: String? = null,
    ): String {
        val issuedAt = Date.from(serverClock.now())
        val claims =
            JWTClaimsSet
                .Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_HTTP_METHOD, httpMethod.uppercase())
                .claim(CLAIM_HTTP_URI, httpUri)
                .issueTime(issuedAt)
                .apply { nonce?.let { claim(CLAIM_NONCE, it) } }
                .build()
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.ES256)
                .type(JOSEObjectType(PROOF_TYPE))
                .jwk(publicJwk)
                .build()
        return SignedJWT(header, claims).apply { sign(signer) }.serialize()
    }

    /**
     * The base64url JWK SHA-256 thumbprint of the public key (RFC 7638), sent as the authorization request's `dpop_jkt`
     * (RFC 9449 §10).
     *
     * @return the thumbprint, ready to send as a query parameter
     */
    fun publicKeyThumbprint(): String = publicJwk.computeThumbprint().toString()

    private companion object {
        /** `typ` RFC 9449 prescribes; a plain `JWT` here is rejected. */
        const val PROOF_TYPE = "dpop+jwt"

        /** The HTTP method the proof is bound to. */
        const val CLAIM_HTTP_METHOD = "htm"

        /** The target URI, query and fragment removed. */
        const val CLAIM_HTTP_URI = "htu"

        /** The server-issued nonce, echoed back when the server has issued one (RFC 9449 §8). */
        const val CLAIM_NONCE = "nonce"
    }
}

/**
 * The per-install DPoP key pair: Keystore-backed in production, an in-memory P-256 pair in tests.
 *
 * @property privateKey signs the proof; typed as [PrivateKey] because a Keystore handle never
 *   implements `ECPrivateKey`
 * @property publicKey embedded in each proof header as the `jwk` claim
 */
data class DpopKeyPair(
    val privateKey: PrivateKey,
    val publicKey: ECPublicKey,
)

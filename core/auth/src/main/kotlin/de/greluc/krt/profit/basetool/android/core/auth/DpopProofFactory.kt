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
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Date
import java.util.UUID

/**
 * Builds the DPoP proof JWTs the token endpoint expects (RFC 9449).
 *
 * **Only token requests carry a proof.** Under the refresh-only binding policy the realm uses
 * (main repo ADR-0131), a *voluntarily* sent proof makes Keycloak bind the access token too — and
 * the backend's bearer filter rejects an access token carrying `cnf.jkt`. Sending a proof on an
 * ordinary API call would therefore break the very next request. The proof belongs on
 * `/token` and nowhere else, which is why this class is used by the token client and is not an
 * interceptor.
 *
 * **`iat` comes from [ServerClock], not the device clock.** Keycloak allows a 10 s proof lifetime
 * with 15 s of skew; a phone a minute off produces proofs that are rejected, and the member sees
 * "login broken" rather than "clock wrong". The desktop extractor records clock drift as its
 * primary DPoP failure mode (main repo REQ-INGEST-012).
 *
 * @property keyPair the per-install P-256 key; its private half signs, its public half is embedded
 *   in the header as the `jwk` the server binds the refresh token to
 * @property serverClock the corrected time source
 */
class DpopProofFactory(
    private val keyPair: DpopKeyPair,
    private val serverClock: ServerClock,
) {
    private val signer: JWSSigner = ECDSASigner(keyPair.privateKey)

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
     * @return the serialised proof JWT for the `DPoP` header
     */
    fun createProof(
        httpMethod: String,
        httpUri: String,
    ): String {
        val issuedAt = Date.from(serverClock.now())
        val claims =
            JWTClaimsSet
                .Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_HTTP_METHOD, httpMethod.uppercase())
                .claim(CLAIM_HTTP_URI, httpUri)
                .issueTime(issuedAt)
                .build()
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.ES256)
                .type(JOSEObjectType(PROOF_TYPE))
                .jwk(publicJwk)
                .build()
        return SignedJWT(header, claims).apply { sign(signer) }.serialize()
    }

    private companion object {
        /** `typ` RFC 9449 prescribes; a plain `JWT` here is rejected. */
        const val PROOF_TYPE = "dpop+jwt"

        /** The HTTP method the proof is bound to. */
        const val CLAIM_HTTP_METHOD = "htm"

        /** The target URI, query and fragment removed. */
        const val CLAIM_HTTP_URI = "htu"
    }
}

/**
 * The per-install DPoP key pair.
 *
 * Production creates it in the Android Keystore (non-exportable, StrongBox where available); tests
 * generate an ordinary in-memory P-256 pair. The distinction lives at the provider that produced
 * the keys, not in this type — which is what lets the proof format be tested on a JVM while the
 * key's hardware binding is an instrumented concern.
 *
 * @property privateKey signs the proof; never leaves the process it was created in
 * @property publicKey embedded in each proof header as the `jwk` claim
 */
data class DpopKeyPair(
    val privateKey: ECPrivateKey,
    val publicKey: ECPublicKey,
)

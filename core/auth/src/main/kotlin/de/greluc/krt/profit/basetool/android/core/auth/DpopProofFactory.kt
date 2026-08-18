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
    // The (PrivateKey, Curve) constructor, not the ECPrivateKey one: a key that lives in the
    // Android Keystore implements PrivateKey and ECKey but NOT java.security.interfaces.ECPrivateKey,
    // because it cannot expose its scalar. Nimbus provides this overload for exactly that case, and
    // the curve has to be named since it can no longer be read off the key.
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
     * @param nonce the value of the last `DPoP-Nonce` the server issued, or `null` when it has
     *   issued none; RFC 9449 §8 lets a server start demanding one at any time, and a client that
     *   cannot echo it back would be locked out by a server-side setting change
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
     * The JWK SHA-256 thumbprint of the public key, base64url-encoded (RFC 7638).
     *
     * This is the `dpop_jkt` parameter of the authorization request (RFC 9449 §10): it tells the
     * realm, before any token exists, which key the eventual grant must be bound to. Under the
     * refresh-only policy it is defence in depth rather than a requirement — it closes the window
     * in which an intercepted authorization code could be redeemed against a different key.
     *
     * Computed here rather than by the caller so the key never has to leave this class.
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
 * The per-install DPoP key pair.
 *
 * Production creates it in the Android Keystore (non-exportable, StrongBox where available); tests
 * generate an ordinary in-memory P-256 pair. The distinction lives at the provider that produced
 * the keys, not in this type — which is what lets the proof format be tested on a JVM while the
 * key's hardware binding is an instrumented concern.
 *
 * @property privateKey signs the proof. Typed as [PrivateKey] rather than `ECPrivateKey` on
 *   purpose: the production key lives in the Android Keystore, which hands out a handle that
 *   implements `PrivateKey` and `ECKey` but never `ECPrivateKey` — it has no scalar to expose. A
 *   narrower type compiles, passes every JVM test with an in-memory key, and throws
 *   `ClassCastException` on the first real device
 * @property publicKey embedded in each proof header as the `jwk` claim
 */
data class DpopKeyPair(
    val privateKey: PrivateKey,
    val publicKey: ECPublicKey,
)

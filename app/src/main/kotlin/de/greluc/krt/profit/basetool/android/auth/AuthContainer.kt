/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import android.content.Context
import de.greluc.krt.profit.basetool.android.BuildConfig
import de.greluc.krt.profit.basetool.android.core.auth.AuthDataStore
import de.greluc.krt.profit.basetool.android.core.auth.AuthSession
import de.greluc.krt.profit.basetool.android.core.auth.AuthorizationRequestFactory
import de.greluc.krt.profit.basetool.android.core.auth.DpopProofFactory
import de.greluc.krt.profit.basetool.android.core.auth.KeystoreDpopKeyProvider
import de.greluc.krt.profit.basetool.android.core.auth.KeystoreSecretCipher
import de.greluc.krt.profit.basetool.android.core.auth.PendingAuthorization
import de.greluc.krt.profit.basetool.android.core.auth.RefreshTokenStore
import de.greluc.krt.profit.basetool.android.core.auth.TokenClient
import de.greluc.krt.profit.basetool.android.core.data.AccountGateRepository
import de.greluc.krt.profit.basetool.android.core.network.KrtHttpClient
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import java.util.Locale
import java.util.UUID

/**
 * The auth object graph, built once per process.
 *
 * Hilt is deliberately not here yet (ADR-0001): the graph is small enough that a hand-written
 * container is easier to read than the generated one, and every dependency below is a decision
 * worth seeing in one place rather than inferring from annotations.
 *
 * Everything is `by lazy` because the Keystore work — generating the token cipher's AES key and the
 * DPoP signing key — is not free, and a member who never opens the app should not pay for it at
 * process start.
 *
 * @property context application context; the constructor takes any and keeps the application one
 */
class AuthContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** Corrected server time, fed by every response and read when a DPoP proof is stamped. */
    val serverClock: ServerClock by lazy { ServerClock() }

    private val cipher by lazy { KeystoreSecretCipher() }

    private val dpopKeys by lazy { KeystoreDpopKeyProvider() }

    private val dataStore by lazy { AuthDataStore.create(appContext) }

    /** The encrypted refresh token at rest. */
    val refreshTokenStore by lazy { RefreshTokenStore(dataStore, cipher) }

    /** The login attempt that is currently out in the browser. */
    val pendingAuthorization by lazy { PendingAuthorization(dataStore, cipher) }

    /** Realm endpoints and client id for this flavour. */
    val configuration by lazy { AppOidc.configuration() }

    /**
     * The API client.
     *
     * Its `AccessTokenProvider` is [session], which is why this is lazy and why the two do not form
     * a cycle: the provider is read at request time, long after both objects exist.
     */
    private val apiClient by lazy {
        KrtHttpClient.create(
            serverClock = serverClock,
            accessTokenProvider = { session.currentAccessToken() },
            correlationIdFactory = { UUID.randomUUID().toString() },
            languageTagProvider = { Locale.getDefault().toLanguageTag() },
            activeOrgUnitProvider = { null },
        )
    }

    /**
     * Reads the approval gate that stands between a valid token and the app.
     *
     * Built on [apiClient] rather than on its own client: the bearer token, the correlation id and
     * the `Accept-Language` header are exactly as required here, and a second client would open a
     * second connection to the same host for one small request per minute.
     */
    val accountGate: AccountGateRepository by lazy {
        AccountGateRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    private val proofFactory by lazy { DpopProofFactory(dpopKeys.keyPair(), serverClock) }

    /** Builds authorization requests; owns the per-attempt secrets. */
    val authorizationRequests by lazy { AuthorizationRequestFactory(configuration, proofFactory) }

    private val tokenClient by lazy {
        TokenClient(
            // Derived from the API client and stripped of its interceptors: an Authorization header
            // on Keycloak's token endpoint is read as client authentication and answered with
            // invalid_client (REQ-APP-AUTH-006).
            httpClient = KrtHttpClient.createTokenClient(apiClient, serverClock),
            configuration = configuration,
            proofFactory = proofFactory,
            serverClock = serverClock,
        )
    }

    /** The session every screen observes and every API call reads its token from. */
    val session: AuthSession by lazy {
        AuthSession(
            tokenClient = tokenClient,
            refreshTokenStore = refreshTokenStore,
            cipher = cipher,
            serverClock = serverClock,
        )
    }

    /**
     * Ends a session completely, including the DPoP key the refresh token was bound to.
     *
     * [AuthSession.logout] wipes the token and the cipher key; the signing key lives outside its
     * reach, and leaving it behind would leave the binding alive.
     *
     * @return the RP-initiated logout URL to open in a browser, or `null` when there was no session
     */
    suspend fun logout(): String? {
        val endSession = session.logout()
        dpopKeys.deleteKey()
        return endSession
    }
}

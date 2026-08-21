/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import android.content.Context
import de.greluc.krt.profit.basetool.android.BuildConfig
import de.greluc.krt.profit.basetool.android.core.auth.ActiveOrgUnitStore
import de.greluc.krt.profit.basetool.android.core.auth.AppLock
import de.greluc.krt.profit.basetool.android.core.auth.AppLockKey
import de.greluc.krt.profit.basetool.android.core.auth.AppLockSetting
import de.greluc.krt.profit.basetool.android.core.auth.AuthDataStore
import de.greluc.krt.profit.basetool.android.core.auth.AuthSession
import de.greluc.krt.profit.basetool.android.core.auth.AuthorizationRequestFactory
import de.greluc.krt.profit.basetool.android.core.auth.DpopProofFactory
import de.greluc.krt.profit.basetool.android.core.auth.KeystoreAppLock
import de.greluc.krt.profit.basetool.android.core.auth.KeystoreDpopKeyProvider
import de.greluc.krt.profit.basetool.android.core.auth.KeystoreSecretCipher
import de.greluc.krt.profit.basetool.android.core.auth.LockedSecretCipher
import de.greluc.krt.profit.basetool.android.core.auth.PendingAuthorization
import de.greluc.krt.profit.basetool.android.core.auth.RefreshTokenStore
import de.greluc.krt.profit.basetool.android.core.auth.SecretCipher
import de.greluc.krt.profit.basetool.android.core.auth.SessionEnvelope
import de.greluc.krt.profit.basetool.android.core.auth.TokenClient
import de.greluc.krt.profit.basetool.android.core.data.AccountGateRepository
import de.greluc.krt.profit.basetool.android.core.data.MissionRepository
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitRepository
import de.greluc.krt.profit.basetool.android.core.data.TermsRepository
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

    /** The Keystore-backed cipher both stores below are built on. */
    private val keystoreCipher by lazy { KeystoreSecretCipher() }

    /**
     * The refresh token's cipher, with the app lock's outer layer around it.
     *
     * The order is the security property: [KeystoreSecretCipher] keeps every guarantee that makes
     * storing a refresh token defensible — non-exportable, device-bound, StrongBox where available —
     * and [LockedSecretCipher] adds a layer the member's authentication removes. Inert while the
     * lock is off, so the stored form then is byte-identical to a build without any of this.
     */
    private val tokenCipher: SecretCipher by lazy { LockedSecretCipher(keystoreCipher, envelope) }

    /** The in-memory session key and the wrap format; empty until an unlock fills it. */
    private val envelope by lazy { SessionEnvelope() }

    private val dpopKeys by lazy { KeystoreDpopKeyProvider() }

    private val dataStore by lazy { AuthDataStore.create(appContext) }

    /** The encrypted refresh token at rest, sealed by the app lock when one is armed. */
    val refreshTokenStore by lazy { RefreshTokenStore(dataStore, tokenCipher) }

    /**
     * The sealed session key, stored beside the refresh token.
     *
     * It shares the token DataStore so a logout wipe reaches it too — a device handed on with the
     * app signed out should not still ask the next person for a fingerprint.
     */
    private val appLockSetting by lazy { AppLockSetting(dataStore) }

    /**
     * The app lock.
     *
     * It takes [refreshTokenStore] because arming and disarming **rewrite the stored token**: the
     * blob's form has to match the setting, or a member who armed the lock mid-session would find
     * an unsealed blob the envelope refuses to open — or, worse, a sealed one after disarming that
     * nothing can.
     */
    val appLock: AppLock by lazy {
        KeystoreAppLock(AppLockKey(), appLockSetting, envelope, refreshTokenStore)
    }

    /**
     * Whether a lock stands, for the settings row to reflect.
     *
     * Read from the stored sentinel rather than from a separate flag, so the switch shows what
     * is actually armed instead of what somebody once asked for.
     */
    val appLockArmed get() = appLockSetting.enabled

    /**
     * The org unit every request is scoped to.
     *
     * Its own preference file rather than the token DataStore, because the request interceptor
     * reads it **synchronously** off an OkHttp thread and DataStore cannot answer that way — see
     * the store's own documentation for the two attempts that proved it. It is still session
     * state, so [logout] wipes it and the backup rules exclude it alongside the token store.
     */
    val activeOrgUnit by lazy { ActiveOrgUnitStore(appContext) }

    /**
     * The login attempt that is currently out in the browser.
     *
     * **Deliberately NOT behind the app lock's outer layer**, unlike the refresh token. The redirect
     * is handled in `onCreate`, before a single frame is composed and therefore before the lock gate
     * has had any chance to run — a sealed attempt would be unreadable at exactly that moment, and
     * `take()` discards what it cannot read, so an armed lock would silently swallow every login
     * that survived a process death. Sealing it would also buy nothing: it holds a PKCE verifier for
     * the length of one browser round trip, not a session, and it is already encrypted by the same
     * non-exportable Keystore key.
     */
    val pendingAuthorization by lazy { PendingAuthorization(dataStore, keystoreCipher) }

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
            // Read synchronously off an OkHttp dispatcher thread, which is why the store
            // mirrors its value in memory rather than being asked to suspend here.
            activeOrgUnitProvider = { activeOrgUnit.current() },
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

    /**
     * Reads the Terms of Use and records consent.
     *
     * Shares [apiClient] with the approval gate: same host, same headers, and one warm TLS
     * connection instead of two.
     */
    val terms: TermsRepository by lazy {
        TermsRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * Reads the member's org units for the switcher.
     *
     * Shares [apiClient] with the gates: same host, same mandatory headers, one warm connection.
     */
    val orgUnits: OrgUnitRepository by lazy {
        OrgUnitRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The Einsatz list.
     *
     * Shares [apiClient] with the gates and the switcher, so the org pin the switcher writes is
     * already on every request this makes. Takes [serverClock] because "hide past Einsätze" is a
     * lower bound on time, and a phone whose clock runs fast would otherwise hide the one that is
     * about to start.
     */
    val missions: MissionRepository by lazy {
        MissionRepository(
            httpClient = apiClient,
            baseUrl = BuildConfig.API_BASE_URL,
            clock = serverClock,
        )
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
            // The bare Keystore cipher: the session uses it only to destroy the key on logout, and
            // that key belongs to the inner layer. Handing it the decorated one would work but
            // would suggest the lock has something to wipe here, which it does not — the lock's own
            // key is disarmed separately.
            cipher = keystoreCipher,
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
        // The scope goes with the session: the next member on this device starts from their own
        // default rather than inside the previous one's org unit.
        activeOrgUnit.clear()
        // The lock key goes with them: a device handed on with the app signed out must not still
        // hold a key that once guarded it. disarm() also drops the in-memory session key, so the
        // outer layer cannot be removed again in this process.
        appLock.disarm()
        return endSession
    }
}

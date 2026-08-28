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
import de.greluc.krt.profit.basetool.android.core.data.AnnouncementRepository
import de.greluc.krt.profit.basetool.android.core.data.AppVersionRepository
import de.greluc.krt.profit.basetool.android.core.data.BankRepository
import de.greluc.krt.profit.basetool.android.core.data.BankStaffRepository
import de.greluc.krt.profit.basetool.android.core.data.HangarRepository
import de.greluc.krt.profit.basetool.android.core.data.IdentityRepository
import de.greluc.krt.profit.basetool.android.core.data.InventoryRepository
import de.greluc.krt.profit.basetool.android.core.data.JobOrderRepository
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncRepository
import de.greluc.krt.profit.basetool.android.core.data.MaterialBoardRepository
import de.greluc.krt.profit.basetool.android.core.data.MemberPreferencesRepository
import de.greluc.krt.profit.basetool.android.core.data.MissionRepository
import de.greluc.krt.profit.basetool.android.core.data.NotificationRepository
import de.greluc.krt.profit.basetool.android.core.data.OperationRepository
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitRepository
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintRepository
import de.greluc.krt.profit.basetool.android.core.data.PersonalInventoryRepository
import de.greluc.krt.profit.basetool.android.core.data.PromotionRepository
import de.greluc.krt.profit.basetool.android.core.data.RefineryRepository
import de.greluc.krt.profit.basetool.android.core.data.TermsRepository
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.core.network.KrtHttpClient
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import de.greluc.krt.profit.basetool.android.core.network.SystemConnectivity
import de.greluc.krt.profit.basetool.android.notifications.SystemNotifications
import de.greluc.krt.profit.basetool.android.notifications.SystemNotifier
import kotlinx.coroutines.runBlocking
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
            // Both run on an OkHttp thread that is about to wait on a socket anyway, and both go
            // to the token client, which carries none of these interceptors and therefore cannot
            // re-enter this. Without them the app dies at the realm's access-token lifespan.
            refreshIfSpent = { runBlocking { session.refreshIfNeeded() } },
            refreshAfterRejection = { refused -> runBlocking { session.refreshFor(refused) } },
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
     * already on every request this makes. No clock of its own: "Vergangene aus" is a status
     * filter, and the dashboard's seven-day window brings its own bounds.
     */
    val missions: MissionRepository by lazy {
        MissionRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The member's own stock — phase 3's first writes.
     *
     * Shares [apiClient] like every other repository: the bearer token, the correlation id and the
     * org pin are already on each request, and a second client would open a second connection to
     * the same host.
     */
    val personalInventory: PersonalInventoryRepository by lazy {
        PersonalInventoryRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The member's own blueprints — the second half of the same screen.
     *
     * Its own repository rather than a method on the inventory one: the two read different
     * endpoints and fail independently, which is exactly how the screen renders them.
     */
    val personalBlueprints: PersonalBlueprintRepository by lazy {
        PersonalBlueprintRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * Whether the device has a network at all.
     *
     * Held here rather than created per screen: the callback registration is a system call, and
     * every write screen from phase 3 on asks the same question.
     */
    val connectivity: Connectivity by lazy { SystemConnectivity(appContext) }

    /**
     * The Operationen list and detail.
     *
     * No [serverClock]: an Operation has no start time of its own, so nothing here is filtered
     * against "now" the way the Einsatz list is.
     */
    val operations: OperationRepository by lazy {
        OperationRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The caller's own backend user id.
     *
     * Its own repository rather than a field on another, because the id is not about any one
     * screen: an Operation's payout rows are the first thing keyed by it, and the Hangar and the
     * personal inventory will be the next.
     */
    val identity: IdentityRepository by lazy {
        IdentityRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The notification inbox, its unread count and its push stream.
     *
     * Shares [apiClient] like everything else, which matters more here than elsewhere: the stream
     * derives its own client from it, so the bearer token, the mandatory headers and the connection
     * pool follow the long-lived SSE connection without a second configuration to keep in sync.
     */
    val notifications: NotificationRepository by lazy {
        NotificationRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The shade, for something the running app's own stream delivered (chapter 14).
     *
     * In the container rather than built at the call site so the application context is the one
     * used — an Activity context here would keep the Activity alive behind a PendingIntent.
     */
    val systemNotifications: SystemNotifications by lazy { SystemNotifier(appContext) }

    /**
     * The member's own standing choices — payout preference and blueprint sharing.
     *
     * Both are me-scoped and optimistically locked, which is why they are a repository rather than
     * a device preference store (design ch. 13, artboard 2).
     */
    val memberPreferences: MemberPreferencesRepository by lazy {
        MemberPreferencesRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The member's own Beförderung record.
     *
     * Its own repository because it answers a question nothing else on the app asks, and both its
     * paths are me-scoped: there is no id to pass and no way to reach anybody else's record.
     */
    val promotion: PromotionRepository by lazy {
        PromotionRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The live-sync bridge: change signals in, the app's own announcements out (ADR-0143).
     *
     * One instance for the whole app rather than one per screen, and shared with [apiClient] for
     * the reason the notification stream is: the stream derives its own client from it, so the
     * bearer token, the active org unit and the correlation id follow a connection that outlives
     * any single request without a second configuration to keep in sync.
     */
    val liveSync: LiveSyncRepository by lazy {
        LiveSyncRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The org-wide announcement on the dashboard.
     *
     * Its own repository rather than a field on the mission one: the two are unrelated reads behind
     * unrelated permissions, and the dashboard is built so that one failing does not blank the
     * other.
     */
    val announcements: AnnouncementRepository by lazy {
        AnnouncementRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The member's hangar and their org unit's ship aggregate.
     *
     * Which org unit the aggregate covers follows from the `X-Active-Org-Unit-Id` header the
     * interceptor already sets, so nothing about scope is configured here.
     */
    val hangar: HangarRepository by lazy {
        HangarRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The org bank a member may see.
     *
     * The member-facing paths only — the accounts public to everyone plus those this caller holds
     * a view grant for. The staff surface is [bankStaff].
     */
    val bank: BankRepository by lazy {
        BankRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The bank's staff surface — the queue, the dashboard, the account lifecycle and the grants.
     *
     * Separate from [bank] because the paths are: these list every account in the organisation and
     * are gated on a bank role, which the server decides and the screens draw. Whether the caller
     * has one is answered by `/me/capabilities`, never worked out from a role name.
     *
     * `/api/v1/bank/admin` stays out of the app entirely — that is the admin area, which is
     * web-only by owner decision.
     */
    val bankStaff: BankStaffRepository by lazy {
        BankStaffRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The Auftrag queue and one order.
     *
     * The org scope is never sent from the client: which orders a member sees follows from their
     * memberships and the active-org-unit header this client already carries.
     */
    val orders: JobOrderRepository by lazy {
        JobOrderRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The Lager tree.
     *
     * Which org unit's Lager it is follows from the active-org-unit header this client already
     * carries, so nothing about scope is configured here.
     */
    val inventory: InventoryRepository by lazy {
        InventoryRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The served-version policy behind the forced-update gate.
     *
     * Shares [apiClient] like everything else, and works signed out too: the header
     * interceptor omits `Authorization` when there is no session, which is what makes
     * the gate answer for a build too old to log in.
     */
    val appVersion: AppVersionRepository by lazy {
        AppVersionRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The Materialbörse.
     *
     * Org-wide by construction: the board is one list for the whole organisation, so nothing about
     * scope is configured here either.
     */
    val materialBoard: MaterialBoardRepository by lazy {
        MaterialBoardRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    /**
     * The member's own Raffinerie orders.
     *
     * Only the `my-orders` surface: the controller's `/all` and `/users/{id}` reads are the
     * Logistik view, and the app stays on the member-facing one.
     */
    val refinery: RefineryRepository by lazy {
        RefineryRepository(httpClient = apiClient, baseUrl = BuildConfig.API_BASE_URL)
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

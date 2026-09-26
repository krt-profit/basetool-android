/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.CapabilitiesResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.UserDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * Provides the caller's own **backend** user id, which differs from the Keycloak `sub` and keys
 * member-owned rows such as an Operation's payout entries.
 */
interface IdentitySource {
    /**
     * Reads the caller's backend user id.
     *
     * @return the id, or a failure. A failure is not fatal to a screen: it means "which row is
     *   yours" cannot be answered, not that the screen has no content.
     */
    suspend fun myUserId(): ApiResult<String>

    /**
     * Reads who the caller is: their id, and whether they hold the Logistician grant.
     *
     * @return the caller, or a failure. A failure means a screen cannot tell which row is the
     *   caller's and must assume the narrower of the two roles, never the wider.
     */
    suspend fun me(): ApiResult<Identity>

    /**
     * Drops the cached record so the next [me] reads the server again; called when the app returns to
     * the foreground (REQ-APP-AUTH-013).
     */
    fun forget()
}

/**
 * The caller as the screens need them: the backend id and server-resolved capability flags, with
 * no name or email.
 *
 * All flags and [permissions] are hints for the UI, never a gate; the server stays the authority
 * (ADR-0011).
 *
 * @property userId the backend user id that payout and assignee rows are keyed by
 * @property logistician whether the caller reaches Logistician through the role hierarchy
 *   (Logistician, Officer and Admin alike)
 * @property missionManager whether the caller reaches Mission-Manager the same way; gates the
 *   Operation's payout confirmation
 * @property bankEmployee whether the caller may see the bank's staff surface (`REQ-APP-BANK-007`)
 * @property bankManagement whether they additionally hold Bank-Management, which decides the
 *   account lifecycle and the grants tab
 * @property admin whether the caller holds ADMIN and so sees every org unit in the org picker
 * @property permissions the backend's capability vocabulary for this caller, e.g. `HANGAR_WRITE`
 */
data class Identity(
    val userId: String,
    val logistician: Boolean,
    val missionManager: Boolean = false,
    val bankEmployee: Boolean = false,
    val bankManagement: Boolean = false,
    val permissions: Set<String> = emptySet(),
    val blueprintOverview: Boolean = false,
    val admin: Boolean = false,
)

/**
 * Reads the caller's own record and caches the resulting [Identity] for the process.
 *
 * Only the id and the capability flags are kept. The bank flags come from
 * `GET /api/v1/me/capabilities`, not from the member record.
 *
 * @property reader performs the call and classifies its failure
 */
class IdentityRepository(
    private val reader: ApiReader,
) : IdentitySource {
    private val mutex = Mutex()
    private var cached: Identity? = null

    /**
     * The identity already read, without asking the server, for callers that cannot suspend.
     *
     * @return the cached identity, or `null` until the first [me] has landed.
     */
    val known: Identity?
        get() = cached

    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads the id at most once per process; a lock keeps concurrent callers from fetching twice.
     *
     * @return the id, or the classified failure.
     */
    override suspend fun myUserId(): ApiResult<String> =
        me()
            .map { it.userId }

    override suspend fun me(): ApiResult<Identity> =
        mutex.withLock {
            cached?.let { return@withLock ApiResult.Success(it) }
            when (val result = reader.get(ME_PATH, UserDto.serializer())) {
                is ApiResult.Failure -> {
                    result
                }

                is ApiResult.Success -> {
                    val id = result.value.id
                    if (id.isNullOrBlank()) {
                        ApiResult.Failure(ApiError.NotFound())
                    } else {
                        val capabilities = readCapabilities()
                        val identity =
                            Identity(
                                userId = id,
                                logistician = capabilities?.isLogisticianOrAbove == true,
                                missionManager = capabilities?.isMissionManagerOrAbove == true,
                                admin = capabilities?.isAdmin == true,
                                bankEmployee = capabilities?.canViewBankStaff == true,
                                bankManagement = capabilities?.canManageBank == true,
                                permissions = result.value.permissions.orEmpty().toSet(),
                                blueprintOverview = capabilities?.canSeeBlueprintOverview == true,
                            )
                        cached = identity
                        ApiResult.Success(identity)
                    }
                }
            }
        }

    /**
     * Reads the caller's capability flags.
     *
     * Its failure is not the identity's failure: the member record loaded, and a screen that could
     * not learn whether it may offer the bank's staff scope simply does not offer it.
     *
     * @return the flags, or `null` when they could not be read.
     */
    private suspend fun readCapabilities(): CapabilitiesResponse? =
        when (val result = reader.get(CAPABILITIES_PATH, CapabilitiesResponse.serializer())) {
            is ApiResult.Success -> {
                result.value
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "capabilities could not be read: ${result.error}" }
                null
            }
        }

    override fun forget() {
        cached = null
    }

    private companion object {
        /** Log subsystem. No name, email or id is ever logged. */
        const val LOG_TAG = "identity"

        /**
         * The caller's own record.
         *
         * The exact path, never `/api/v1/users/{id}`: that one can name another member, and the
         * API vhost is a default-deny allow-list precisely so such a path never has to be on it.
         */
        const val ME_PATH = "/api/v1/users/me"

        /** The caller's UI capability flags, including the two the bank's scope segment reads. */
        const val CAPABILITIES_PATH = "/api/v1/me/capabilities"
    }
}

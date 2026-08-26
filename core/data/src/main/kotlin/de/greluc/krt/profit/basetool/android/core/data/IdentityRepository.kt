/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.UserDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * The caller's own **backend** user id.
 *
 * Not the same thing as the Keycloak `sub` the app already holds in its ID token. Rows that belong
 * to a member server-side — an Operation's payout entries are the first case — are keyed by the
 * backend's own `user.id`, and there is no derivation from one to the other on the device.
 *
 * Matching by name was the alternative and is wrong: the server sends `displayName` when the member
 * set one and `username` otherwise, so a name match would work for some members and silently fail
 * for exactly those who personalised their profile.
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
     * Drops the cached record so the next [me] reads the server again.
     *
     * Called when the app comes back to the foreground: a role granted while it was in the
     * background otherwise takes effect only after a sign-out, and "sign out and back in" is not an
     * instruction anybody should have to be given (REQ-APP-AUTH-013).
     */
    fun forget()
}

/**
 * The caller, as far as any screen needs to know them.
 *
 * No name and no email: those are personal details the app has no question for, and the privacy gate
 * (`ANDROID_APP_PLAN` §7) keeps them out of memory.
 *
 * The **permissions** are here on purpose, and the reasoning that once kept them out does not apply
 * to them. They arrive in this very response, the access token the app must hold carries the realm
 * roles anyway, and a capability list is a statement about the session rather than a detail about
 * the person. Dropping them only ever meant the app could not read what it already had — and an app
 * that cannot read its own permissions offers actions the server refuses (ADR-0011).
 *
 * @property userId the backend user id — the key an Operation's payout rows and an order's
 *   assignee rows are written against
 * @property logistician whether the caller holds the Logistician grant, which is what decides
 *   whether a screen offers a Logistician-only control at all
 * @property missionManager whether they hold the mission-manager grant, which decides the same
 *   for the Operation's payout confirmation
 * @property permissions the backend's own capability vocabulary for this caller — `HANGAR_WRITE`,
 *   `MISSION_READ` and the rest. A **hint, never a gate**: the server stays the authority, and a
 *   screen that skips a check because this set said so is a defect rather than an optimisation
 */
data class Identity(
    val userId: String,
    val logistician: Boolean,
    val missionManager: Boolean = false,
    val permissions: Set<String> = emptySet(),
)

/**
 * Reads the caller's own record and keeps its id for the process.
 *
 * **Cached, unlike everything else in this package.** The id is the one value here that cannot
 * change while the app runs: a different id means a different session, and a sign-out tears the
 * whole object graph down. Re-reading it on every screen would spend a round trip to learn
 * something already known.
 *
 * Only the id and the two grants are kept. The response also carries the member's email,
 * roles and rank; holding those would put personal data in memory for the lifetime of the process
 * to answer questions that need an opaque key and one boolean.
 *
 * @property reader performs the call and classifies its failure
 */
class IdentityRepository(
    private val reader: ApiReader,
) : IdentitySource {
    private val mutex = Mutex()
    private var cached: Identity? = null

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
     * Reads the id, once per process.
     *
     * The lock is what makes "once" true: two screens opening at the same moment would otherwise
     * both find the cache empty and both fetch.
     *
     * @return the id, or the classified failure.
     */
    override suspend fun myUserId(): ApiResult<String> =
        when (val result = me()) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.userId)
        }

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
                        // A record without an id is not an outage and not a refusal; it is an
                        // answer this client cannot use. Reported as NotFound so the caller takes
                        // the same "cannot tell which row is yours" path as a 404.
                        ApiResult.Failure(ApiError.NotFound())
                    } else {
                        // `isLogistician` absent is read as "not one". The grant decides whether a
                        // control is offered, and the narrower reading is the one that cannot
                        // offer an action the server refuses.
                        val identity =
                            Identity(
                                userId = id,
                                logistician = result.value.isLogistician == true,
                                missionManager = result.value.isMissionManager == true,
                                permissions = result.value.permissions.orEmpty().toSet(),
                            )
                        cached = identity
                        ApiResult.Success(identity)
                    }
                }
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
    }
}

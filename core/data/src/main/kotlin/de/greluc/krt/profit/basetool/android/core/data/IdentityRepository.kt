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
}

/**
 * Reads the caller's own record and keeps its id for the process.
 *
 * **Cached, unlike everything else in this package.** The id is the one value here that cannot
 * change while the app runs: a different id means a different session, and a sign-out tears the
 * whole object graph down. Re-reading it on every screen would spend a round trip to learn
 * something already known.
 *
 * Only the id is kept. The response also carries the member's email, roles and rank; holding those
 * would put personal data in memory for the lifetime of the process to answer a question that only
 * needs an opaque key.
 *
 * @property reader performs the call and classifies its failure
 */
class IdentityRepository(
    private val reader: ApiReader,
) : IdentitySource {
    private val mutex = Mutex()
    private var cached: String? = null

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
                        cached = id
                        ApiResult.Success(id)
                    }
                }
            }
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

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AppVersionPolicyDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import okhttp3.OkHttpClient

/**
 * Which builds the server still serves.
 *
 * @property minimumVersionCode the oldest `versionCode` still served; `0` means no floor.
 * @property latestVersionCode the newest published, or `0` when the server does not say.
 * @property releasesUrl where the member gets the new build.
 */
data class AppVersionPolicy(
    val minimumVersionCode: Int,
    val latestVersionCode: Int,
    val releasesUrl: String,
) {
    /**
     * Whether [versionCode] is still served.
     *
     * **A zero floor always passes**, which is the unconfigured server's answer and must never
     * lock anybody out. Everything else is a plain comparison — the app does not interpret the
     * number, it obeys it.
     *
     * @param versionCode this build's own `versionCode`.
     * @return `true` when the build may run.
     */
    fun allows(versionCode: Int): Boolean =
        minimumVersionCode <= 0 || versionCode >= minimumVersionCode
}

/** The served-version policy, as a seam. */
fun interface AppVersionSource {
    /**
     * Reads the policy.
     *
     * @return the policy, or the classified failure.
     */
    suspend fun versionPolicy(): ApiResult<AppVersionPolicy>
}

/**
 * Reads `GET /api/v1/app/version-policy` (server REQ-API-010).
 *
 * **The one endpoint the app calls without needing a session.** That is the point of it: when a
 * contract change breaks the login itself, the build that most needs to be told it is too old is
 * exactly the one that cannot authenticate. The shared client omits `Authorization` when there is
 * no session, so this works signed in or out with no second client.
 *
 * @property reader performs the call and classifies its failure.
 */
class AppVersionRepository(
    private val reader: ApiReader,
) : AppVersionSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client.
     * @param baseUrl the flavour's API origin.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /** {@inheritDoc} */
    override suspend fun versionPolicy(): ApiResult<AppVersionPolicy> =
        when (val result = reader.get(PATH, AppVersionPolicyDto.serializer())) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    AppVersionPolicy(
                        // A missing floor is NO floor, never a blocking one. The generator makes
                        // every field nullable, and the wrong default here would wall off every
                        // member the first time the server omitted a value.
                        minimumVersionCode = result.value.minimumVersionCode ?: 0,
                        latestVersionCode = result.value.latestVersionCode ?: 0,
                        releasesUrl =
                            result.value.releasesUrl?.takeIf { it.isNotBlank() } ?: RELEASES_FALLBACK,
                    ),
                )
            }
        }

    private companion object {
        /** Log subsystem. Nothing about a member passes through here. */
        const val LOG_TAG = "app-version"

        const val PATH = "/api/v1/app/version-policy"

        /**
         * Where to send a member when the server named no URL.
         *
         * A wall with no way off it is worse than a wrong link, and this is the release page the
         * app is distributed from anyway (plan Q1).
         */
        const val RELEASES_FALLBACK = "https://github.com/krt-profit/basetool-android/releases/latest"
    }
}

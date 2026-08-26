/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MyBlueprintSharingRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MyBlueprintSharingResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.MyPayoutPreferenceRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MyPayoutPreferenceResponse
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import okhttp3.OkHttpClient

/**
 * Where the member's share goes by default.
 *
 * A per-Einsatz choice overrides it (`REQ-APP-MISSION-014`); this is the standing answer the sign-up
 * starts from.
 */
enum class PayoutPreference {
    /** To the member's own account. */
    PAYOUT,

    /** To the org treasury. */
    DONATE,
}

/**
 * The member's standing payout choice, with the version its next write has to echo.
 *
 * @property preference the choice, or `null` when the member has never made one — distinct from
 *   „Auszahlung an mich", which is a decision.
 * @property version the value the next `PUT` must send back.
 */
data class PayoutSetting(
    val preference: PayoutPreference?,
    val version: Long,
)

/**
 * Whether the member's blueprints show up in the org's availability overview.
 *
 * @property sharing whether they do.
 * @property version the value the next `PUT` must send back.
 */
data class BlueprintSharing(
    val sharing: Boolean,
    val version: Long,
)

/**
 * The two standing choices Einstellungen offers beyond the device's own toggles.
 *
 * Both are **me-scoped by construction**: the paths end in `/users/me/…` and take no id, so there is
 * no version of this repository that could read or write somebody else's preference.
 *
 * Both are also **optimistically locked** — each read carries a version and each write echoes it —
 * which is unusual for a settings row and is the reason they live here rather than in a
 * fire-and-forget preference store. A member signed in on a phone and a browser can change the same
 * value twice, and the server is entitled to refuse the second one.
 */
interface MemberPreferencesSource {
    /**
     * Reads the standing payout choice.
     *
     * @return the choice and its version, or the classified failure.
     */
    suspend fun payoutPreference(): ApiResult<PayoutSetting>

    /**
     * Sets it.
     *
     * @param preference the new choice.
     * @param version the version the value was read at.
     * @return the saved choice and its **new** version, or the classified failure —
     *   `ApiError.OptimisticLock` when somebody else wrote first.
     */
    suspend fun setPayoutPreference(
        preference: PayoutPreference,
        version: Long,
    ): ApiResult<PayoutSetting>

    /**
     * Reads whether the member's blueprints are shared with the organisation.
     *
     * @return the flag and its version, or the classified failure.
     */
    suspend fun blueprintSharing(): ApiResult<BlueprintSharing>

    /**
     * Sets it.
     *
     * @param sharing whether to share.
     * @param version the version the value was read at.
     * @return the saved flag and its **new** version, or the classified failure.
     */
    suspend fun setBlueprintSharing(
        sharing: Boolean,
        version: Long,
    ): ApiResult<BlueprintSharing>
}

/**
 * Reads and writes the member's own standing choices.
 *
 * @property reader performs the calls and classifies their failures.
 */
class MemberPreferencesRepository(
    private val reader: ApiReader,
) : MemberPreferencesSource {
    /**
     * Convenience constructor for the app's own client.
     *
     * @param httpClient the shared client.
     * @param baseUrl the API root.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    override suspend fun payoutPreference(): ApiResult<PayoutSetting> =
        when (val result = reader.get(PAYOUT_PATH, MyPayoutPreferenceResponse.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun setPayoutPreference(
        preference: PayoutPreference,
        version: Long,
    ): ApiResult<PayoutSetting> =
        when (
            val result =
                reader.put(
                    path = PAYOUT_PATH,
                    body =
                        MyPayoutPreferenceRequest(
                            preference =
                                when (preference) {
                                    PayoutPreference.PAYOUT -> MyPayoutPreferenceRequest.Preference.PAYOUT
                                    PayoutPreference.DONATE -> MyPayoutPreferenceRequest.Preference.DONATE
                                },
                            version = version,
                        ),
                    bodySerializer = MyPayoutPreferenceRequest.serializer(),
                    deserializer = MyPayoutPreferenceResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun blueprintSharing(): ApiResult<BlueprintSharing> =
        when (val result = reader.get(SHARING_PATH, MyBlueprintSharingResponse.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun setBlueprintSharing(
        sharing: Boolean,
        version: Long,
    ): ApiResult<BlueprintSharing> =
        when (
            val result =
                reader.put(
                    path = SHARING_PATH,
                    body =
                        MyBlueprintSharingRequest(
                            shareBlueprintsGlobally = sharing,
                            version = version,
                        ),
                    bodySerializer = MyBlueprintSharingRequest.serializer(),
                    deserializer = MyBlueprintSharingResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "member-prefs"

        /** The standing payout choice. */
        const val PAYOUT_PATH = "/api/v1/users/me/payout-preference"

        /** The blueprint-sharing flag. */
        const val SHARING_PATH = "/api/v1/users/me/blueprint-sharing"
    }
}

/**
 * Maps the wire payout response.
 *
 * @return the choice and its version. A version the server omits reads as `0`, which the next write
 *   sends back and the server refuses — better than guessing a number that looks current.
 */
private fun MyPayoutPreferenceResponse.toModel() =
    PayoutSetting(
        preference =
            when (defaultPayoutPreference) {
                MyPayoutPreferenceResponse.DefaultPayoutPreference.PAYOUT -> PayoutPreference.PAYOUT
                MyPayoutPreferenceResponse.DefaultPayoutPreference.DONATE -> PayoutPreference.DONATE
                null -> null
            },
        version = version ?: 0L,
    )

/**
 * Maps the wire sharing response.
 *
 * @return the flag and its version. An absent flag reads as **not shared**: the safe reading of a
 *   value that did not arrive is that nothing is being published.
 */
private fun MyBlueprintSharingResponse.toModel() =
    BlueprintSharing(
        sharing = shareBlueprintsGlobally ?: false,
        version = version ?: 0L,
    )

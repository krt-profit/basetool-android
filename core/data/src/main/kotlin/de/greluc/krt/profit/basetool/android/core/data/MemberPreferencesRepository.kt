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
import de.greluc.krt.profit.basetool.android.core.contract.model.MyRsiHandleRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MyRsiHandleResponse
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import okhttp3.OkHttpClient

/**
 * Where the member's share goes by default.
 *
 * A per-Einsatz choice overrides it (`REQ-APP-MIS-019`); this is the standing answer the sign-up
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
 * The member's RSI handle, which a connected tool may ask about but never read.
 *
 * Server REQ-SEC-072 and REQ-XCH-031.
 *
 * @property handle the stored handle, or `null` when the member has set none — a valid state.
 * @property version the value the next `PUT` must send back.
 */
data class RsiHandle(
    val handle: String?,
    val version: Long,
)

/**
 * The caller's payout preference, blueprint sharing and RSI handle, read and written through
 * `/users/me/…`.
 *
 * All three are columns of one server row and optimistically locked: each read carries the row's
 * version and each write echoes it.
 */
interface MemberPreferencesSource {
    /**
     * Reads the standing payout choice.
     *
     * @return the choice and its version, or the classified failure.
     */
    suspend fun payoutPreference(): ApiResult<PayoutSetting>

    /**
     * Sets the caller's payout preference.
     *
     * @param preference the new choice.
     * @param version the version the value was read at.
     * @return the saved choice and its new version, or the classified failure;
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
     * Sets whether the caller shares their blueprints.
     *
     * @param sharing whether to share.
     * @param version the version the value was read at.
     * @return the saved flag and its new version, or the classified failure.
     */
    suspend fun setBlueprintSharing(
        sharing: Boolean,
        version: Long,
    ): ApiResult<BlueprintSharing>

    /**
     * Reads the member's RSI handle.
     *
     * @return the handle and the row's version, or the classified failure.
     */
    suspend fun rsiHandle(): ApiResult<RsiHandle>

    /**
     * Stores or clears the member's RSI handle.
     *
     * @param handle the new handle; blank clears it.
     * @param version the version the row was read at.
     * @return the saved handle and the row's new version, or the classified failure:
     *   `ApiError.Conflict` with code `DUPLICATE_ENTITY` when another profile carries it,
     *   `ApiError.Validation` when it is outside the handle alphabet, `ApiError.OptimisticLock` when
     *   somebody else wrote first.
     */
    suspend fun setRsiHandle(
        handle: String,
        version: Long,
    ): ApiResult<RsiHandle>
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
        reader.get(PAYOUT_PATH, MyPayoutPreferenceResponse.serializer())
            .map { it.toModel() }

    override suspend fun setPayoutPreference(
        preference: PayoutPreference,
        version: Long,
    ): ApiResult<PayoutSetting> =
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
            .map { it.toModel() }

    override suspend fun blueprintSharing(): ApiResult<BlueprintSharing> =
        reader.get(SHARING_PATH, MyBlueprintSharingResponse.serializer())
            .map { it.toModel() }

    override suspend fun setBlueprintSharing(
        sharing: Boolean,
        version: Long,
    ): ApiResult<BlueprintSharing> =
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
            .map { it.toModel() }

    override suspend fun rsiHandle(): ApiResult<RsiHandle> =
        reader.get(RSI_HANDLE_PATH, MyRsiHandleResponse.serializer())
            .map { it.toModel() }

    override suspend fun setRsiHandle(
        handle: String,
        version: Long,
    ): ApiResult<RsiHandle> =
        reader.put(
            path = RSI_HANDLE_PATH,
            body = MyRsiHandleRequest(version = version, rsiHandle = handle.trim()),
            bodySerializer = MyRsiHandleRequest.serializer(),
            deserializer = MyRsiHandleResponse.serializer(),
        )
            .map { it.toModel() }

    private companion object {
        /** Log subsystem. No member identity is written here. */
        const val LOG_TAG = "member-prefs"

        /** The standing payout choice. */
        const val PAYOUT_PATH = "/api/v1/users/me/payout-preference"

        /** The blueprint-sharing flag. */
        const val SHARING_PATH = "/api/v1/users/me/blueprint-sharing"

        /** The member's own RSI handle. */
        const val RSI_HANDLE_PATH = "/api/v1/users/me/rsi-handle"
    }
}

/**
 * Maps the wire RSI-handle response.
 *
 * @return the handle, `null` when none or blank, and the row's version.
 */
private fun MyRsiHandleResponse.toModel() =
    RsiHandle(
        handle = rsiHandle?.takeIf { it.isNotBlank() },
        version = version ?: 0L,
    )

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

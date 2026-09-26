/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.RegistrationStatusDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiErrorMapper
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.await
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The account-gate read the polling logic depends on, separate from [AccountGateRepository] so scheduling can be tested
 * without a socket.
 */
fun interface AccountGateSource {
    /**
     * Reads whether the signed-in member is cleared, and if not, what is holding them.
     *
     * @return the status, or a failure the caller can show
     */
    suspend fun registrationStatus(): ApiResult<ApprovalStatus>
}

/**
 * Reads whether a signed-in member is admitted: an approved registration, an assigned role and accepted Terms of Use.
 *
 * The backend otherwise refuses with 403 `PENDING_APPROVAL`, `NO_ROLE` or `TERMS_ACCEPTANCE_REQUIRED`
 * (REQ-SEC-017). Nothing is cached, so a stale approval can never let anybody past a closed gate.
 *
 * @property reader performs the call and classifies its failures
 */
class AccountGateRepository(
    private val reader: ApiReader,
) : AccountGateSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token through its interceptor
     * @param baseUrl the flavour's API origin, e.g. `https://api.profit-base.online`
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads the calling member's position in the approval queue.
     *
     * A `PENDING_APPROVAL` refusal is folded into [ApprovalStatus.PENDING] and a `NO_ROLE` refusal into
     * `ApprovalStatus.NO_ROLE`, instead of surfacing as failures.
     *
     * @return the status, or a failure the caller can show
     */
    override suspend fun registrationStatus(): ApiResult<ApprovalStatus> =
        when (val result = reader.get(REGISTRATION_STATUS_PATH, RegistrationStatusDto.serializer())) {
            is ApiResult.Success -> {
                ApiResult.Success(ApprovalStatus.fromWire(result.value.approvalStatus?.value))
            }

            is ApiResult.Failure -> {
                when (result.error) {
                    is ApiError.PendingApproval -> ApiResult.Success(ApprovalStatus.PENDING)
                    is ApiError.NoRole -> ApiResult.Success(ApprovalStatus.NO_ROLE)
                    else -> result
                }
            }
        }

    /**
     * Performs one authenticated GET and parses its body.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed value, or the classified failure
     */
    private companion object {
        /** Log subsystem. No claim, token or member name is ever written here. */
        const val LOG_TAG = "gate"

        /** Reachable by a caller whose only authority is `ROLE_PENDING_APPROVAL`. */
        const val REGISTRATION_STATUS_PATH = "/api/v1/users/me/registration-status"

        /** The status an unreadable body is reported under, since the response itself was fine. */
        const val HTTP_OK = 200
    }
}

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
 * The one question the gate asks, as its caller needs it.
 *
 * A separate type from [AccountGateRepository] so the polling logic can be exercised without a
 * socket: the loop's interesting properties — that it stops on approval, that it survives a lost
 * response, that a second start does not double the request rate — are about *scheduling*, and
 * asserting them through a real HTTP stack would test OkHttp instead. Opening the repository class
 * for subclassing would achieve the same thing by weakening production code, which is the trade
 * this interface exists to avoid.
 */
fun interface AccountGateSource {
    /**
     * Reads the calling member's position in the approval queue.
     *
     * @return the status, or a failure the caller can show
     */
    suspend fun registrationStatus(): ApiResult<ApprovalStatus>
}

/**
 * Reads the answers that decide whether a signed-in member reaches the app at all.
 *
 * A valid token is not admission. The backend gates every other endpoint behind an approved
 * registration and an accepted Terms-of-Use version, and answers **403 for both** with the stable
 * codes `PENDING_APPROVAL` / `TERMS_ACCEPTANCE_REQUIRED` (main repo REQ-SEC-017 / REQ-SEC-028). The
 * app therefore asks up front rather than waiting to be refused: the alternative is a first screen
 * that loads, fails, and then has to guess which of three unrelated 403s it just received.
 *
 * The endpoint is deliberately reachable while its own gate is closed — that is what makes the call
 * possible for a pending caller, and it is a property of the server the app depends on rather than
 * a happy accident.
 *
 * Nothing here is cached. The state is one enum, it is read at app start and on an explicit
 * refresh, and a stale "approved" restored from disk would be the one cached value able to let
 * somebody past a gate the server has since closed.
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
     * A `PENDING_APPROVAL` **refusal is folded into a successful [ApprovalStatus.PENDING]** rather
     * than surfaced as an error. Whether this particular endpoint is refused depends on how the
     * deployment orders its gate filters, and both outcomes mean the identical thing to the caller.
     * Treating one of them as a failure would show a connectivity screen to a member whose account
     * is simply waiting for an administrator.
     *
     * @return the status, or a failure the caller can show
     */
    override suspend fun registrationStatus(): ApiResult<ApprovalStatus> =
        when (val result = reader.get(REGISTRATION_STATUS_PATH, RegistrationStatusDto.serializer())) {
            is ApiResult.Success -> {
                ApiResult.Success(ApprovalStatus.fromWire(result.value.approvalStatus?.value))
            }

            is ApiResult.Failure -> {
                if (result.error is ApiError.PendingApproval) {
                    ApiResult.Success(ApprovalStatus.PENDING)
                } else {
                    result
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

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Reports answers that may mean this build is outdated to [listener] and passes every response on
 * unchanged (REQ-APP-API-010).
 *
 * A retired path and a missing row both answer `404 NOT_FOUND`, and the edge answers an unadmitted
 * path with a bare `404`, so every `404` on an API path is reported; the listener decides how often
 * that is worth a policy read. The policy path itself is never reported.
 *
 * @property listener receives the signals.
 */
internal class UpdateSignalInterceptor(
    private val listener: UpdateSignalListener,
) : Interceptor {
    /**
     * Proceeds with the call and inspects a failed answer's status and problem code.
     *
     * @param chain the call.
     * @return the response, untouched; its body is peeked, never consumed.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful || !reportable(response)) {
            return response
        }
        val code = problemCode(response)
        when {
            code == ProblemDetail.CODE_APP_UPDATE_REQUIRED -> {
                listener.onSignal(UpdateSignal.UPDATE_REQUIRED)
            }

            response.code == HTTP_NOT_FOUND || code == ProblemDetail.CODE_NOT_FOUND -> {
                listener.onSignal(
                    UpdateSignal.NOT_FOUND,
                )
            }
        }
        return response
    }

    /**
     * Whether the answer belongs to an API path other than the policy.
     *
     * @param response the answer.
     * @return `true` when a signal may be raised for it.
     */
    private fun reportable(response: Response): Boolean {
        val path = response.request.url.encodedPath
        return path.startsWith(API_PREFIX) && path != POLICY_PATH
    }

    /**
     * Reads the problem `code`, tolerating any body that is not a problem.
     *
     * @param response the failed answer.
     * @return the code, or `null`.
     */
    private fun problemCode(response: Response): String? =
        try {
            val body = response.peekBody(MAX_PROBLEM_BODY_BYTES).string()
            if (body.isBlank()) null else JSON.decodeFromString(ProblemDetail.serializer(), body).code
        } catch (unreadable: IOException) {
            KrtLog.d(LOG_TAG) { "problem body unreadable: " + unreadable.javaClass.simpleName }
            null
        } catch (malformed: IllegalArgumentException) {
            KrtLog.d(LOG_TAG) { "non-problem error body: " + malformed.javaClass.simpleName }
            null
        }

    private companion object {
        /** Log subsystem. */
        const val LOG_TAG = "http"

        /** Every backend path the app calls starts here. */
        const val API_PREFIX = "/api/"

        /** The version gate, whose own failure must not trigger another read of it. */
        const val POLICY_PATH = "/api/v1/app/version-policy"

        const val HTTP_NOT_FOUND = 404

        /** A problem body is a few hundred bytes; an edge error page is not worth reading whole. */
        const val MAX_PROBLEM_BODY_BYTES = 64L * 1024L

        /** Lenient, as the error mapper is: an added field must not hide the code. */
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

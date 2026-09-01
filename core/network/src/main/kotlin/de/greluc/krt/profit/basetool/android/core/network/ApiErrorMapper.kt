/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.serialization.json.Json
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Turns an unsuccessful HTTP response into the [ApiError] the UI switches on.
 *
 * **The stable `code` decides, not the status.** The backend answers 403 for three unrelated
 * situations — a pending registration, unaccepted terms, and a genuine authorisation failure — and
 * a client that branches on the status alone shows the wrong screen for two of them. The status is
 * the fallback for responses that carry no problem body at all, which is what an edge (NPM) or a
 * proxy produces.
 *
 * A malformed body never becomes a thrown parse error: an unreadable problem body is still an error
 * response, and losing the status because the JSON was odd would be the worse outcome.
 *
 * @property json lenient by default; see [DEFAULT_JSON]
 */
class ApiErrorMapper(
    private val json: Json = DEFAULT_JSON,
) {
    /**
     * Maps a non-2xx response.
     *
     * @param response the response to classify; its body is peeked, not consumed
     * @return the matching [ApiError]
     */
    fun map(response: Response): ApiError {
        val problem = parseProblem(response)
        return when (problem?.code) {
            ProblemDetail.CODE_UNAUTHENTICATED -> {
                ApiError.Unauthenticated(problem)
            }

            ProblemDetail.CODE_PENDING_APPROVAL -> {
                ApiError.PendingApproval(problem)
            }

            ProblemDetail.CODE_TERMS_ACCEPTANCE_REQUIRED -> {
                ApiError.TermsAcceptanceRequired(problem)
            }

            ProblemDetail.CODE_RATE_LIMIT_EXCEEDED -> {
                ApiError.RateLimited(retryAfter(response), problem)
            }

            ProblemDetail.CODE_OPTIMISTIC_LOCK -> {
                ApiError.OptimisticLock(problem)
            }

            ProblemDetail.CODE_SERVICE_UNAVAILABLE -> {
                ApiError.ServiceUnavailable(problem)
            }

            else -> {
                byStatus(response, problem)
            }
        }
    }

    /**
     * Classifies a response whose body carried no code the app knows.
     *
     * @param response the response to classify
     * @param problem the parsed body, if any
     * @return the matching [ApiError]
     */
    private fun byStatus(
        response: Response,
        problem: ProblemDetail?,
    ): ApiError =
        when (response.code) {
            HTTP_UNAUTHORIZED -> {
                ApiError.Unauthenticated(problem)
            }

            HTTP_FORBIDDEN -> {
                ApiError.Forbidden(problem)
            }

            HTTP_NOT_FOUND -> {
                ApiError.NotFound(problem)
            }

            HTTP_CONFLICT -> {
                // Not OptimisticLock: that is what the explicit OPTIMISTIC_LOCK code above means.
                // Every other 409 is the server refusing on a rule, and calling it a concurrent
                // edit told the member to reload over a state that reloading does not change.
                ApiError.Conflict(problem)
            }

            HTTP_UNPROCESSABLE, HTTP_BAD_REQUEST -> {
                ApiError.Validation(problem)
            }

            HTTP_TOO_MANY_REQUESTS -> {
                ApiError.RateLimited(retryAfter(response), problem)
            }

            HTTP_SERVICE_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT, HTTP_BAD_GATEWAY -> {
                ApiError.ServiceUnavailable(problem)
            }

            else -> {
                ApiError.Server(response.code, problem)
            }
        }

    /**
     * Reads `Retry-After`, which the backend sends as a whole number of seconds.
     *
     * @param response the refused response
     * @return the wait, or `null` when the header is absent or not a number
     */
    private fun retryAfter(response: Response): Duration? =
        response
            .header(HEADER_RETRY_AFTER)
            ?.trim()
            ?.toLongOrNull()
            ?.seconds

    /**
     * Parses the problem body, tolerating anything that is not one.
     *
     * Expression-shaped rather than a sequence of early returns: detekt caps a function at two, and
     * the guard chain here reads better as one pipeline anyway — unreadable, blank and unparseable
     * all mean the same thing to the caller.
     *
     * @param response the response whose body to read
     * @return the parsed body, or `null` when there is none or it is unusable
     */
    private fun parseProblem(response: Response): ProblemDetail? =
        readBody(response)
            ?.takeIf { it.isNotBlank() }
            ?.let { body ->
                try {
                    json.decodeFromString(ProblemDetail.serializer(), body)
                } catch (malformed: IllegalArgumentException) {
                    // An error response that is not problem+json — an edge 404 page, an HTML 502.
                    // The status still classifies it; only the localised prose is lost.
                    KrtLog.d(LOG_TAG) { "non-problem error body: " + malformed.javaClass.simpleName }
                    null
                }
            }

    /**
     * Reads at most [MAX_PROBLEM_BODY_BYTES] of the body without consuming it.
     *
     * @param response the response to peek at
     * @return the body text, or `null` when it could not be read
     */
    private fun readBody(response: Response): String? =
        try {
            response.peekBody(MAX_PROBLEM_BODY_BYTES).string()
        } catch (io: IOException) {
            KrtLog.d(LOG_TAG) { "problem body unreadable: " + io.javaClass.simpleName }
            null
        }

    private companion object {
        /** Log subsystem for the mapper's diagnostics. */
        const val LOG_TAG = "http"

        /**
         * Cap on the body read for classification.
         *
         * A problem body is a few hundred bytes; an edge error page can be far larger, and reading
         * it whole only to decide "not JSON" would be pointless work on a failing path.
         */
        const val MAX_PROBLEM_BODY_BYTES = 64L * 1024L

        const val HEADER_RETRY_AFTER = "Retry-After"

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE = 422
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val HTTP_GATEWAY_TIMEOUT = 504

        /**
         * Lenient on purpose: the app must survive a backend that adds a field, which the external
         * contract set (main repo REQ-API-009) explicitly permits as an additive change.
         */
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

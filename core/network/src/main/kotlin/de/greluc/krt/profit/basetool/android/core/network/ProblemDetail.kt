/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One field's validation message inside a problem body's `fieldErrors`.
 *
 * The backend sends this as an array of objects and, for older consumers, the same content again as
 * a flat `{field: message}` map under `errors`. **The array is the shape to read.** Declaring
 * `fieldErrors` as a map was not merely a lost convenience: kotlinx refuses an array where an
 * object is declared, `parseProblem` catches that as „not problem+json", and every validation
 * refusal therefore arrived with **no body at all** — no title, no detail, no correlation id.
 * Found on the device: adding a frequency failed silently, and the server had said exactly why.
 *
 * @property field which field was rejected.
 * @property message the localised reason, safe to show.
 * @property code the constraint that failed, e.g. `Digits`.
 */
@Serializable
data class ProblemFieldError(
    val field: String? = null,
    val message: String? = null,
    val code: String? = null,
)

/**
 * The backend's RFC 7807 `application/problem+json` body.
 *
 * Every field is optional because a problem body is what the server sends when something already
 * went wrong — an error path is the last place to assume a complete payload. The one field the app
 * actually branches on is [code], the backend's stable machine-readable code (main repo
 * REQ-API-004); [title] and [detail] are localised prose meant for display, never for logic.
 *
 * @property type problem type URI
 * @property title short localised summary, safe to show
 * @property status the HTTP status the server assigned
 * @property detail longer localised explanation, safe to show
 * @property instance the request path the problem refers to
 * @property code the stable code the client branches on, e.g. `PENDING_APPROVAL`
 * @property correlationId ties this response to one backend log line (REQ-OBS-002)
 * @property fieldErrors the structured per-field validation messages, in the shape the backend
 *   actually sends them: an **array** of `{field, message}`
 * @property errors the same messages in the backend's legacy map shape, kept because it is
 *   still sent and costs one line to read
 */
@Serializable
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    @SerialName("correlationId") val correlationId: String? = null,
    val fieldErrors: List<ProblemFieldError>? = null,
    val errors: Map<String, String>? = null,
) {
    /**
     * Renders only the three fields that identify a problem, never the ones that describe it.
     *
     * Roughly fifty call sites log an `ApiError`, and every `ApiError` carries one of these — so
     * the generated `toString()` was putting the server's own localised prose, the request path
     * with its ids, and every field-validation message into logcat on release builds. Some of that
     * prose names members and amounts, which the project's logging rule forbids categorically, and
     * logcat is app-private only until a bugreport or an OEM collector picks it up.
     *
     * Overriding here rather than editing fifty call sites is deliberate: the next call site gets
     * it for free, and a `\${result.error}` in a log line stays the obvious thing to write.
     * [IdTokenClaims], [PkceChallenge] and [AuthorizationRequest] suppress their own sensitive
     * halves the same way.
     *
     * What is kept is exactly what a diagnosis needs: the stable code, the status, and the
     * correlation id that ties the line to the backend's own (REQ-OBS-002).
     *
     * @return the identifying fields, without the descriptive ones.
     */
    override fun toString(): String =
        "ProblemDetail(code=$code, status=$status, correlationId=$correlationId)"

    companion object {
        /** No valid session: the caller must re-authenticate. */
        const val CODE_UNAUTHENTICATED: String = "UNAUTHENTICATED"

        /** The account exists but is not approved yet (main repo REQ-SEC-017). */
        const val CODE_PENDING_APPROVAL: String = "PENDING_APPROVAL"

        /** The Terms of Use in force have not been accepted (REQ-SEC-028). */
        const val CODE_TERMS_ACCEPTANCE_REQUIRED: String = "TERMS_ACCEPTANCE_REQUIRED"

        /** A rate budget was exhausted; `Retry-After` says when to come back. */
        const val CODE_RATE_LIMIT_EXCEEDED: String = "RATE_LIMIT_EXCEEDED"

        /** A concurrent modification lost the optimistic-lock race (409). */
        const val CODE_OPTIMISTIC_LOCK: String = "OPTIMISTIC_LOCK"

        /** The backend is reachable but a dependency is not; retryable. */
        const val CODE_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    }
}

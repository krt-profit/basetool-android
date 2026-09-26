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
 * One field's validation message inside a problem body's `fieldErrors` array.
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
 * The backend's RFC 7807 `application/problem+json` body, with every field optional.
 *
 * The app branches only on [code] (REQ-API-004); [title] and [detail] are for display.
 *
 * @property type problem type URI
 * @property title short localised summary, safe to show
 * @property status the HTTP status the server assigned
 * @property detail longer localised explanation, safe to show
 * @property instance the request path the problem refers to
 * @property code the stable code the client branches on, e.g. `PENDING_APPROVAL`
 * @property correlationId ties this response to one backend log line (REQ-OBS-002)
 * @property fieldErrors the structured per-field validation messages, as an array of
 *   `{field, message}`
 * @property errors the same messages in the backend's legacy map shape
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
     * Renders only the code, status and correlation id, never the descriptive fields, so logging a
     * problem leaks no member data.
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

        /**
         * The token is valid but carries no application role (REQ-SEC-053); unlike
         * [CODE_PENDING_APPROVAL], no approval is pending.
         */
        const val CODE_NO_ROLE: String = "NO_ROLE"

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

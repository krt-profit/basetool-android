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
 * @property fieldErrors per-field validation messages, keyed by field name
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
    val fieldErrors: Map<String, String>? = null,
) {
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

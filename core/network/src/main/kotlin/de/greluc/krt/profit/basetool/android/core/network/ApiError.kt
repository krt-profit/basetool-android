/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlin.time.Duration

/**
 * What went wrong with an API call, as a state the UI can switch on.
 *
 * The backend's problem codes are first-class app states rather than strings compared at call sites
 * (`CLAUDE.md`, "Concurrency & API contract"): a pending approval drives a whole screen, an
 * unaccepted terms version drives a gate, a 409 drives a reload-and-retry prompt. Modelling them as
 * a sealed hierarchy means a new state cannot be forgotten by a `when` that stops compiling.
 *
 * [problem] is carried on every variant so the localised [ProblemDetail.title] / [ProblemDetail.detail]
 * and the correlation id remain available for display, even where the app renders its own copy.
 */
sealed interface ApiError {
    /** The parsed problem body, when the server sent one. */
    val problem: ProblemDetail?

    /**
     * No usable session — the token was missing, expired or rejected.
     *
     * @property problem the parsed body, if any
     */
    data class Unauthenticated(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * The account is authenticated but still awaiting approval.
     *
     * @property problem the parsed body, if any
     */
    data class PendingApproval(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * The Terms of Use in force must be accepted before the API answers.
     *
     * @property problem the parsed body, if any
     */
    data class TermsAcceptanceRequired(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * Authenticated and approved, but not allowed to do this.
     *
     * @property problem the parsed body, if any
     */
    data class Forbidden(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * A rate budget was exhausted.
     *
     * @property retryAfter how long to wait before retrying, when the server said so
     * @property problem the parsed body, if any
     */
    data class RateLimited(
        val retryAfter: Duration?,
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * A concurrent edit won the race; the caller must reload and re-apply.
     *
     * @property problem the parsed body, if any
     */
    data class OptimisticLock(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * The server refused on a rule, not on a race.
     *
     * A `409` whose code is **not** `OPTIMISTIC_LOCK`: a state-machine or cross-aggregate refusal
     * such as `BANK_ACCOUNT_NOT_EMPTY`, `BANK_REQUEST_NOT_PENDING`, `BANK_NOT_REVERSIBLE`,
     * `ENTITY_IN_USE` or the generic `BUSINESS_CONFLICT`. These used to be shown as
     * [OptimisticLock], which told the member somebody else had edited the row and to reload —
     * false on both counts, and the advice loops because reloading changes nothing.
     *
     * The server's own `detail` is the only text that says what was actually refused, so a screen
     * rendering this **must** show it rather than a generic phrase.
     *
     * @property problem the parsed body, if any
     */
    data class Conflict(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * The requested resource does not exist, or is hidden from this caller.
     *
     * @property problem the parsed body, if any
     */
    data class NotFound(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * A validation failure; [ProblemDetail.fieldErrors] names the offending fields.
     *
     * @property problem the parsed body, if any
     */
    data class Validation(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * The backend answered but could not serve the request; retrying is meaningful.
     *
     * @property problem the parsed body, if any
     */
    data class ServiceUnavailable(
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * A server-side failure that is not worth retrying on its own.
     *
     * @property status the HTTP status received
     * @property problem the parsed body, if any
     */
    data class Server(
        val status: Int,
        override val problem: ProblemDetail? = null,
    ) : ApiError

    /**
     * The request never produced an HTTP response — no connectivity, DNS, TLS or timeout.
     *
     * Deliberately distinct from [ServiceUnavailable]: that one means the server spoke, this one
     * means it did not, and only the latter should read as "you are offline" in the UI.
     *
     * @property cause the underlying I/O failure
     */
    data class Network(
        val cause: Throwable,
    ) : ApiError {
        override val problem: ProblemDetail? get() = null
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlin.time.Duration

/**
 * What went wrong with an API call, as a sealed state the UI can switch on.
 *
 * [problem] is carried on every variant so the localised [ProblemDetail.title] /
 * [ProblemDetail.detail] and the correlation id remain available for display.
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
     * The account is authenticated and approved but holds no role, so every call is refused and the
     * app shows its gate rather than a per-request [Forbidden] toast.
     *
     * @property problem the parsed body, if any
     */
    data class NoRole(
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
     * A `409` refusal on a rule rather than a race: any code other than `OPTIMISTIC_LOCK`, e.g.
     * `BANK_ACCOUNT_NOT_EMPTY` or `BUSINESS_CONFLICT`.
     *
     * A screen rendering this must show the server's `detail`.
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

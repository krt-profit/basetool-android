/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * Where an account stands in the admin approval queue.
 *
 * Mirrors the backend's `RegistrationStatusDto.approvalStatus` (main repo epic #720), which the app
 * reads from `GET /api/v1/users/me/registration-status` — an endpoint a pending caller may reach,
 * whose only authority is `ROLE_PENDING_APPROVAL`.
 */
enum class ApprovalStatus {
    /** Submitted and waiting for an administrator. The app shows the approval-pending gate. */
    PENDING,

    /** Approved; the account may use the app. */
    ACTIVE,

    /** Refused. The gate stays, with different wording — this is not a retryable state. */
    REJECTED,

    /**
     * The server named a status this build does not know.
     *
     * A new value added on the server must not crash a client that has not shipped yet, and it must
     * not be silently rounded to [ACTIVE] either — an unknown status is treated as "not cleared",
     * so the safe outcome is the gate rather than the app.
     */
    UNKNOWN,

    ;

    /** Whether this status lets the member past the gate. */
    val isCleared: Boolean get() = this == ACTIVE

    companion object {
        /**
         * Maps the wire value.
         *
         * Parsed from a plain string rather than through an enum serializer on purpose:
         * kotlinx.serialization throws on an unrecognised enum constant, which would turn a server
         * adding a fourth status into a client-side crash on the login path — the worst possible
         * place for one.
         *
         * @param wire the value the server sent, or `null` when the field was absent
         * @return the matching constant, or [UNKNOWN]
         */
        fun fromWire(wire: String?): ApprovalStatus =
            entries.firstOrNull { it != UNKNOWN && it.name == wire } ?: UNKNOWN
    }
}

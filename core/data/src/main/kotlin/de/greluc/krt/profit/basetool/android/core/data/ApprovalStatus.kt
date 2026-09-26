/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * Why the account gate is holding a signed-in member, or that it is not.
 *
 * Three constants mirror `RegistrationStatusDto.approvalStatus` from
 * `GET /api/v1/users/me/registration-status`. [NO_ROLE] is derived from the backend's 403 `NO_ROLE`
 * refusal (REQ-SEC-053) and is never produced by [fromWire].
 */
enum class ApprovalStatus {
    /** Submitted and waiting for an administrator. The app shows the approval-pending gate. */
    PENDING,

    /** Approved; the account may use the app. */
    ACTIVE,

    /** Refused. The gate stays, with different wording — this is not a retryable state. */
    REJECTED,

    /**
     * Approved, but holding no role an administrator has granted.
     *
     * Folded in from the backend's 403 `NO_ROLE` refusal (REQ-SEC-053); it clears once an admin assigns
     * a role.
     */
    NO_ROLE,

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
         * Maps the wire value from a plain string, so an unknown status becomes [UNKNOWN] instead of throwing.
         *
         * @param wire the value the server sent, or `null` when the field was absent
         * @return the matching constant, or [UNKNOWN]
         */
        fun fromWire(wire: String?): ApprovalStatus = WIRE_VALUES.firstOrNull { it.name == wire } ?: UNKNOWN

        /**
         * The constants the server can actually name in `approvalStatus`.
         *
         * [UNKNOWN] is this app's own word for "not one of these", and [NO_ROLE] is derived from a
         * refusal rather than read from the field; letting either match a wire string would invent
         * a state the server never claimed.
         */
        private val WIRE_VALUES = listOf(PENDING, ACTIVE, REJECTED)
    }
}

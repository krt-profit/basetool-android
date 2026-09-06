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
 * Three of the four constants mirror the backend's `RegistrationStatusDto.approvalStatus` (main
 * repo epic #720), which the app reads from `GET /api/v1/users/me/registration-status` — an
 * endpoint a pending caller may reach, whose only authority is `ROLE_PENDING_APPROVAL`.
 *
 * [NO_ROLE] is the exception and never appears in that field. It is derived from a *refusal*: the
 * backend answers 403 `NO_ROLE` to an approved account that holds no application role (main repo
 * REQ-SEC-053). The gate is the same screen either way, so the state belongs in the same enum;
 * what it must not do is arrive through [fromWire], which is why that function names the three
 * wire constants rather than excluding [UNKNOWN] alone.
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
     * Never sent as an `approvalStatus`; folded in from the backend's 403 `NO_ROLE` refusal
     * (main repo REQ-SEC-053). Not retryable by the member — it clears when an admin assigns a
     * role — but the gate's refresh still reaches it, because the refusal disappears the moment
     * one is.
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

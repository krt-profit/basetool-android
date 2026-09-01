/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import de.greluc.krt.profit.basetool.android.core.data.Identity

/**
 * Who the caller is, for every screen that has to decide whether to offer an action.
 *
 * One holder for the whole app, not one read per screen. Before this, three of twelve ViewModels
 * fetched the identity and the Lager fetched none, which is how the Zuordnung and the bulk Umbuchen
 * shipped with no permission awareness at all (ADR-0011).
 *
 * `null` means "not read yet, or the read failed". Every caller must treat that as **unknown, not
 * as forbidden**: refusing on an outage would lock a member out of their own stock because a request
 * timed out. Unknown therefore leaves the control fully enabled and lets the server answer — the
 * behaviour the whole app had before this, kept as the honest fallback.
 */
val LocalCaller = compositionLocalOf<Identity?> { null }

/**
 * Whether the caller may write to one row — **the server's answer, carried, not re-derived**.
 *
 * This used to approximate: own row, or `Identity.logistician`. The approximation claimed it would
 * only ever be too generous — "it never hides an action the member could in fact perform" — and
 * that claim was false for the people it mattered most to. `isLogistician` on the me-response
 * reports whether a *Staffel membership row* carries the flag, and an admin holds no Staffel
 * membership by design, so the helper returned `false` and the Lager's Zuordnung and Umbuchen were
 * greyed out for the one role that may edit every row. Officers without the flag were locked out
 * the same way (REQ-SEC-047, ADR-0151).
 *
 * `InventoryItemDto.canEdit` now carries the decision the endpoint's own gate would make, computed
 * by the same `AccessGateService` — so the control and the write agree by construction rather than
 * by a client guessing the role hierarchy.
 *
 * @param canEdit the row's own flag; `null` from a server that does not send it yet.
 * @param ownerId the row's holder, or `null` when the row names none.
 * @return whether to offer the write.
 */
@Composable
fun mayEditRowOf(
    canEdit: Boolean?,
    ownerId: String?,
): Boolean {
    val caller = LocalCaller.current
    return when {
        canEdit != null -> canEdit

        // Unknown, not forbidden — an older server, or a read that failed. Falling back to "own
        // row" keeps a member working on their own stock instead of locking them out of it;
        // anything wider would be the client guessing again.
        caller == null -> true

        else -> ownerId == null || ownerId == caller.userId
    }
}

/**
 * Whether the caller reaches the Logistiker role — Logistician, Officer or Admin.
 *
 * A row lock asks "is this yours?"; this asks "do you hold the grant?" — the design draws them with
 * the same picture and different copy (design ch. 09, artboard 14), and the Zuordnung needs this one
 * even on the caller's own row (artboard 11: „Buchen: eigene Zeile → aktiv; Zuordnen: Rolle
 * Logistiker → gesperrt").
 *
 * Backed by the server's `isLogisticianOrAbove`, so an admin and an officer read as held. Under the
 * previous membership-derived flag both read as *not* held and the Zuordnung was locked for them.
 *
 * @return whether the role is reached. Unknown reads as held, for the reason in [LocalCaller].
 */
@Composable
fun isLogistician(): Boolean {
    val caller = LocalCaller.current ?: return true
    return caller.logistician
}

/**
 * Whether the caller holds a backend capability.
 *
 * @param permission one of the backend's own constants — `HANGAR_WRITE`, `MISSION_READ`, …
 * @return whether it is held. Unknown reads as held, for the reason in [LocalCaller].
 */
@Composable
fun holds(permission: String): Boolean {
    val caller = LocalCaller.current ?: return true
    return permission in caller.permissions
}

/** The backend capability names the app checks against. Mirrors `backend/support/Permissions.java`. */
object KrtPermissions {
    /** Reading the hangar. */
    const val HANGAR_READ = "HANGAR_READ"

    /** Writing to the hangar — every member holds this for their own ships. */
    const val HANGAR_WRITE = "HANGAR_WRITE"

    /** Reading Einsätze. */
    const val MISSION_READ = "MISSION_READ"

    /** Creating and editing Einsätze. */
    const val MISSION_WRITE = "MISSION_WRITE"

    /** Running an Einsatz: participants, payouts, status. */
    const val MISSION_MANAGE = "MISSION_MANAGE"
}

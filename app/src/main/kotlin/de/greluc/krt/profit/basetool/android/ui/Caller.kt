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
 * `null` means "not read yet, or the read failed", and it is a **third state, not a synonym for
 * either answer**. It used to read as *permitted*, on the grounds that refusing during an outage
 * would lock a member out of their own stock. That reasoning protected the wrong thing: it left
 * every gated control open during the window the identity is still loading — which is every app
 * start — so the app offered writes the server then refused with a `403`. ADR-0011 exists to stop
 * exactly that.
 *
 * Reading it as *forbidden* would be worse in a different way: the refusal copy names a missing
 * grant („Dafür brauchst du die Rolle Logistiker."), and saying that to somebody who holds the role
 * is a lie with a plausible face.
 *
 * So unknown is neither. It **locks the control like a refusal and says something true instead** —
 * that the permission could not be checked — which is what [Gate.unknown] renders.
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
 * @return whether the role is reached, or `null` when the identity has not been read. **Not**
 *   `true` on unknown: an offered control whose write the server then refuses is the failure
 *   ADR-0011 exists to prevent, and the owner called it out as such. The caller renders the
 *   unknown state as locked-but-honest rather than as a missing grant.
 */
@Composable
fun isLogistician(): Boolean? = LocalCaller.current?.logistician

/**
 * Whether the caller is an administrator.
 *
 * **Not a gate — a wording.** The admin area is web-only permanently, so nothing here unlocks a
 * screen on this answer. What it decides is what the org switcher's no-pin row is allowed to
 * promise: sending no `X-Active-Org-Unit-Id` gives an admin `adminAllScope` — every org unit there
 * is — and gives everyone else the union of their own reach. Labelling both „Alle Org-Einheiten"
 * told a member they were seeing everything when they were seeing their own two Staffeln (measured
 * on the test stack 2026-09-01: 884.8 SCU under that row against an admin's 1403.4).
 *
 * @return whether the caller is an admin, or `null` when the identity has not been read. Treat
 *   `null` as the narrower wording: over-promising during the load window is the same defect in
 *   miniature.
 */
@Composable
fun isAdmin(): Boolean? = LocalCaller.current?.admin

/**
 * Whether the caller holds a backend capability.
 *
 * @param permission one of the backend's own constants — `HANGAR_WRITE`, `MISSION_READ`, …
 * @return whether it is held, or `null` when the identity has not been read.
 */
@Composable
fun holds(permission: String): Boolean? = LocalCaller.current?.let { permission in it.permissions }

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

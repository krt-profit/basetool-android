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
 * Whether the caller may write to a row that belongs to somebody, in some org unit.
 *
 * Mirrors the server's own rule for stock — *own row, or edit rights on that row's org unit* — as
 * closely as the client can. `Identity.logistician` is the user-level grant, while the server checks
 * it per org unit, so a Logistician of one Staffel reads as permitted on another's row and is
 * refused when they act. That is the deliberate direction of the approximation: **it never hides an
 * action the member could in fact perform**, and the refusal it cannot predict is reported in the
 * app's own words, exactly as before.
 *
 * @param ownerId the row's holder, or `null` when the row names none.
 * @return whether to offer the write.
 */
@Composable
fun mayEditRowOf(ownerId: String?): Boolean {
    val caller = LocalCaller.current ?: return true
    return ownerId == null || ownerId == caller.userId || caller.logistician
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

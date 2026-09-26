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
 * Who the caller is, for every screen that has to decide whether to offer an action (ADR-0011).
 *
 * `null` means not read yet or the read failed, and is neither permitted nor forbidden: gated
 * controls lock and say the permission could not be checked, as [Gate.unknown] renders.
 */
val LocalCaller = compositionLocalOf<Identity?> { null }

/**
 * Whether the caller may write to one row, as the server decided it (REQ-SEC-047, ADR-0151).
 *
 * Uses `InventoryItemDto.canEdit`, computed by the endpoint's own `AccessGateService`.
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
        caller == null -> true
        else -> ownerId == null || ownerId == caller.userId
    }
}

/**
 * Whether the caller reaches the Logistiker role: Logistician, Officer or Admin.
 *
 * A role check rather than a row-ownership check; backed by the server's `isLogisticianOrAbove`.
 *
 * @return whether the role is reached, or `null` when the identity has not been read; `null` is
 *   rendered locked with an honest reason, never as permitted (ADR-0011).
 */
@Composable
fun isLogistician(): Boolean? = LocalCaller.current?.logistician

/**
 * Whether the caller is an administrator; decides wording, not access.
 *
 * Chooses what the org switcher's no-pin row promises: an admin sees every org unit, everyone else
 * the union of their own units.
 *
 * @return whether the caller is an admin, or `null` when the identity has not been read; treat
 *   `null` as the narrower wording.
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

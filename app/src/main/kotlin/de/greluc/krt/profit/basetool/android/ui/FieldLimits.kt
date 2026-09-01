/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

/**
 * The server's own `maxLength` constraints, for the fields the app writes.
 *
 * **These are the backend's numbers, not the app's taste.** Each mirrors a Jakarta `@Size(max=…)`
 * on the matching request DTO, so a value that passes here is one the server will accept on length.
 * The app previously capped exactly one field out of the 130 the backend constrains, which meant a
 * member could type a 20 000-character mission description, submit it, and be told only afterwards
 * — by a validation error — that it was too long. The web app sets `maxlength` on its inputs for
 * this reason.
 *
 * Capping is deliberately silent for short fields: a name that stops at 255 needs no counter,
 * because nobody types a 255-character name by accident. The long free-text fields that a member
 * genuinely can fill carry a visible counter at the call site instead (see the order note).
 *
 * When the backend changes one of these, this file is the single place to follow it.
 */
object FieldLimits {
    /** `PatchMissionCoreRequest.name`, `AddUnitRequest.name` — `@Size(max = 255)`. */
    const val NAME: Int = 255

    /** `PatchMissionCoreRequest.description` — `@Size(max = 20000)`. */
    const val DESCRIPTION: Int = 20000

    /** `PatchMissionCoreRequest.meetingPoint` — `@Size(max = 200)`. */
    const val MEETING_POINT: Int = 200

    /**
     * The common note ceiling: `AddUnitRequest.note`, `BankWithdrawalRequest.note` and
     * `justification`, and the job-order note — all `@Size(max = 500)`.
     */
    const val NOTE: Int = 500

    /** `InventoryItemNoteUpdateRequest.note` — `@Size(max = 1000)`, wider than the rest. */
    const val INVENTORY_NOTE: Int = 1000

    /** `PersonalBlueprintCreateRequest.note` and its update twin — `@Size(max = 2000)`. */
    const val BLUEPRINT_NOTE: Int = 2000
}

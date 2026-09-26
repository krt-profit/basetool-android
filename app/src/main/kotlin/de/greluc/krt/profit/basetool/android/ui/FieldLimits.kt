/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

/**
 * The server's `maxLength` constraints for the fields the app writes.
 *
 * Each mirrors a `@Size(max=…)` on the matching backend request DTO. Short fields are capped
 * silently; long free-text fields show a counter at the call site.
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

    /**
     * A counterparty's name when they hold no tool account.
     *
     * `@Size(max = 100)` on all three booking requests — narrower than [NAME], and deliberately
     * its own constant: typing 155 characters into a field the server cuts at 100 is a refusal the
     * form can prevent.
     */
    const val COUNTERPARTY_NAME: Int = 100

    /** `InventoryItemNoteUpdateRequest.note` — `@Size(max = 1000)`, wider than the rest. */
    const val INVENTORY_NOTE: Int = 1000

    /** `PersonalBlueprintCreateRequest.note` and its update twin — `@Size(max = 2000)`. */
    const val BLUEPRINT_NOTE: Int = 2000
}

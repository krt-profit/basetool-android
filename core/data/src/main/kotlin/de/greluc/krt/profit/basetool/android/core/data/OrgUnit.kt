/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * What kind of unit an org unit is.
 *
 * The app shows the name and pins the id; the kind exists because the four kinds are not
 * interchangeable to a member — a Staffel is where they serve, a Spezialkommando is a second
 * membership alongside it — and the switcher groups by it rather than presenting one flat list.
 */
enum class OrgUnitKind {
    /** Staffel — the member's home unit. */
    SQUADRON,

    /** Spezialkommando — a cross-Staffel unit a member can belong to in addition. */
    SPECIAL_COMMAND,

    /** Bereich — the level above the Staffeln. */
    BEREICH,

    /** Organisationsleitung. */
    ORGANISATIONSLEITUNG,

    /**
     * A kind this build does not know.
     *
     * Not a defensive flourish: the reader coerces an unrecognised enum constant to `null`
     * (`REQ-APP-API-005`), precisely so a kind added on the server does not crash a build in the
     * field. The unit is still perfectly usable — it has a name and an id — so it is offered rather
     * than hidden, and only its grouping is unknown.
     */
    UNKNOWN,
}

/**
 * One org unit the member can act in.
 *
 * @property id the id echoed back in `X-Active-Org-Unit-Id`; the one field that must be present.
 * @property name the unit's name, as the switcher and the top-bar badge show it.
 * @property shorthand the short form, e.g. `S1`; empty when the server sends none.
 * @property kind which of the four kinds this is, or [OrgUnitKind.UNKNOWN].
 * @property profitEligible whether the unit may *process* orders. Only the responsible picker on
 *   the order form cares: a Bereich or the Organisationsleitung can raise an order but never work
 *   one. `false` is the safe reading of a server that did not say, because offering an ineligible
 *   unit turns a full form into a 400 on submit.
 */
data class OrgUnit(
    val id: String,
    val name: String,
    val shorthand: String,
    val kind: OrgUnitKind,
    val profitEligible: Boolean = false,
)

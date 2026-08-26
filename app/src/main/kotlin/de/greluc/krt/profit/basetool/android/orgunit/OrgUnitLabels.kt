/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orgunit

import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind

/**
 * How one org unit reads in the switcher.
 *
 * Design ch. 02, artboard 7 draws two forms: a Staffel as „IRI — IRIDIUM" and a Spezialkommando as
 * „SK VANGUARD". So a Staffel leads with its shorthand and a unit of any other kind leads with a
 * marker for what it is — without one, „VANGUARD" the Staffel and „VANGUARD" the Spezialkommando
 * are the same word in a list whose whole job is telling them apart.
 *
 * A unit whose kind this build does not recognise falls back to its plain name rather than
 * inventing a marker for it.
 *
 * @return the label, ready to render.
 */
internal fun OrgUnit.switcherLabel(): String =
    when (kind) {
        OrgUnitKind.SQUADRON -> if (shorthand.isBlank()) name else "$shorthand — $name"
        OrgUnitKind.SPECIAL_COMMAND -> name.prefixedWith("SK")
        OrgUnitKind.BEREICH -> name.prefixedWith("Bereich")
        OrgUnitKind.ORGANISATIONSLEITUNG -> name
        OrgUnitKind.UNKNOWN -> name
    }

/**
 * Puts a kind marker in front of a name that does not already carry it.
 *
 * Units are named by hand and the organisation does name Spezialkommandos „SK Nebelkraehe", so a
 * marker added unconditionally reads „SK SK NEBELKRAEHE". Seen on the device; the switcher is one
 * of the few places where every unit's name is on screen at once, which is where a stutter shows.
 *
 * @param marker the word for this kind of unit.
 * @return the name, prefixed at most once.
 */
private fun String.prefixedWith(marker: String): String =
    if (startsWith("$marker ", ignoreCase = true)) this else "$marker $this"

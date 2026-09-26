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
 * A Staffel leads with its shorthand („IRI — IRIDIUM"); another kind leads with a kind marker
 * („SK VANGUARD"). An unrecognised kind falls back to the plain name.
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
 * Puts a kind marker in front of a name that does not already start with it.
 *
 * @param marker the word for this kind of unit.
 * @return the name, prefixed at most once.
 */
private fun String.prefixedWith(marker: String): String =
    if (startsWith("$marker ", ignoreCase = true)) this else "$marker $this"

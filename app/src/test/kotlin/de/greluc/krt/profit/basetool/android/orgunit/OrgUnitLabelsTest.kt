/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orgunit

import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the switcher names a unit — the two forms design ch. 02, artboard 7 draws, and the case the
 * artboard could not show because it depends on how a unit was named by hand.
 */
class OrgUnitLabelsTest {
    private fun unit(
        name: String,
        shorthand: String = "",
        kind: OrgUnitKind = OrgUnitKind.SQUADRON,
    ) = OrgUnit(id = "u", name = name, shorthand = shorthand, kind = kind)

    @Test
    fun `a Staffel leads with its shorthand`() {
        assertEquals("IRI — IRIDIUM", unit("IRIDIUM", shorthand = "IRI").switcherLabel())
    }

    @Test
    fun `a Staffel with no shorthand is just its name`() {
        assertEquals("IRIDIUM", unit("IRIDIUM").switcherLabel())
    }

    @Test
    fun `a Spezialkommando is marked as one`() {
        assertEquals(
            "SK VANGUARD",
            unit("VANGUARD", kind = OrgUnitKind.SPECIAL_COMMAND).switcherLabel(),
        )
    }

    /**
     * The case that only showed up on the device: the organisation names units by hand, and
     * "SK Nebelkraehe" is a name somebody actually typed. Prefixing it again read "SK SK NEBELKRAEHE".
     */
    @Test
    fun `a Spezialkommando whose name already says so is not prefixed twice`() {
        assertEquals(
            "SK NEBELKRAEHE",
            unit("SK NEBELKRAEHE", kind = OrgUnitKind.SPECIAL_COMMAND).switcherLabel(),
        )
    }

    @Test
    fun `the doubling guard ignores case`() {
        assertEquals(
            "sk Nebelkraehe",
            unit("sk Nebelkraehe", kind = OrgUnitKind.SPECIAL_COMMAND).switcherLabel(),
        )
    }

    @Test
    fun `a Bereich is marked as one`() {
        assertEquals("Bereich Profit", unit("Profit", kind = OrgUnitKind.BEREICH).switcherLabel())
    }

    @Test
    fun `the Organisationsleitung needs no marker`() {
        assertEquals(
            "ORGANISATIONSLEITUNG",
            unit("ORGANISATIONSLEITUNG", kind = OrgUnitKind.ORGANISATIONSLEITUNG).switcherLabel(),
        )
    }

    /** A kind a newer server knows and this build does not still has to render as something. */
    @Test
    fun `an unknown kind falls back to the plain name`() {
        assertEquals("SOMETHING NEW", unit("SOMETHING NEW", kind = OrgUnitKind.UNKNOWN).switcherLabel())
    }
}

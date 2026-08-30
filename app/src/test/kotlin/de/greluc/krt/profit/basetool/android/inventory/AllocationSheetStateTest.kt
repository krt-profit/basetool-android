/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic behind the Zuordnung sheet's three rest states. */
class AllocationSheetStateTest {
    @Test
    fun `the two splits are reconciled apart`() {
        // The whole point of Modell G: the same 96 SCU can be promised to an Auftrag AND to an
        // Einsatz. One shared rest would report the second promise as an overbooking.
        val state =
            sheet(
                jobOrders = listOf(row("o1", "60")),
                missions = listOf(row("m1", "96")),
            )

        assertEquals("36.25", state.jobOrderRest.stripTrailingZeros().toPlainString())
        assertEquals("0.25", state.missionRest.stripTrailingZeros().toPlainString())
        assertFalse("neither split exceeds the entry", state.overbooked)
    }

    @Test
    fun `promising more than the entry holds is refused before the server sees it`() {
        val state = sheet(jobOrders = listOf(row("o1", "97")))

        assertTrue(state.overbooked)
        assertFalse("the save is what the artboard locks", state.submittable)
    }

    @Test
    fun `a row that matches the server is not sent again`() {
        // "3" and "3.0" are the same promise. The server returns the second and the stepper writes
        // the first, so comparing the strings would re-send every untouched row on every save.
        val state =
            sheet(
                jobOrders = listOf(AllocationRow("o1", "#1", null, "3", "3.0")),
                missions = listOf(AllocationRow("m1", "Lyria", null, "4", "2")),
            )

        assertEquals(1, state.pending.size)
        assertEquals("m1", state.pending.single().second.targetId)
    }

    @Test
    fun `an untouched sheet has nothing to save`() {
        val state = sheet(jobOrders = listOf(AllocationRow("o1", "#1", null, "3", "3")))

        assertFalse(state.submittable)
    }

    @Test
    fun `a target already on the sheet is not offered again`() {
        val state =
            sheet(missions = listOf(row("m1", "1"))).copy(
                missionTargets =
                    listOf(
                        AllocationTarget("m1", "Lyria"),
                        AllocationTarget("m2", "Hurston"),
                    ),
            )

        assertEquals(listOf("m2"), state.addable(AllocationKind.MISSION).map { it.id })
    }

    @Test
    fun `a half-typed amount does not freeze the rest`() {
        // The field is mid-edit: a sum that refuses to compute would stop the figure the member is
        // watching, which is worse than treating the unparseable row as nothing yet.
        val state = sheet(jobOrders = listOf(row("o1", ""), row("o2", "10")))

        assertEquals("86.25", state.jobOrderRest.stripTrailingZeros().toPlainString())
    }

    /**
     * A sheet over a 96.25 SCU entry.
     *
     * @param jobOrders the Auftrag rows.
     * @param missions the Einsatz rows.
     * @return the state under test.
     */
    private fun sheet(
        jobOrders: List<AllocationRow> = emptyList(),
        missions: List<AllocationRow> = emptyList(),
    ) = AllocationSheetState(entry = entry(), jobOrders = jobOrders, missions = missions)

    /**
     * One editable row.
     *
     * @param id the target.
     * @param amount what the member has set.
     * @return the row, as one the server does not yet know.
     */
    private fun row(
        id: String,
        amount: String,
    ) = AllocationRow(targetId = id, label = id, subtitle = null, amount = amount, serverAmount = null)

    /**
     * The stock entry the sheet is opened over.
     *
     * @return an entry holding 96.25 SCU.
     */
    private fun entry() =
        InventoryEntry(
            id = "e1",
            materialName = "Agricium",
            materialId = "mat-1",
            unit = "SCU",
            locationName = "Everus Harbor",
            locationId = "loc-1",
            holder = "test-admin",
            holderId = "u1",
            amount = "96.25",
            quality = "720",
            personal = false,
            note = null,
            version = 0,
        )
}

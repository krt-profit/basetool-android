/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import de.greluc.krt.profit.basetool.android.core.data.RefineryGoodDraft
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What „Neuer Raffinerieauftrag" may and may not send.
 *
 * The rule with teeth is completeness. The server requires an input material and both quantities at
 * 1 or more on **every** goods line, and refuses the whole order with a `goods[0]`-shaped message
 * that names an index rather than a field. Checking it here keeps the refusal where the field is.
 */
class RefineryCreateTest {
    private companion object {
        /** The wall clock „27.08.2026" + „21:00" stands for, wherever the device is. */
        val TYPED_START: LocalDateTime = LocalDateTime.parse("2026-08-27T21:00")

        /** What the 62000 units of [good]'s input come to in SCU. */
        const val INPUT_SCU = 620.0

        /** What the 44200 units of [good]'s output come to in SCU. */
        const val OUTPUT_SCU = 442.0

        /** Compared as doubles, so the assertion needs a delta rather than an equality. */
        const val SCU_TOLERANCE = 0.0001
    }

    private fun good(
        material: String? = "m1",
        input: String = "620",
        output: String = "442",
    ) = RefineryGoodDraft(
        inputMaterialId = material,
        inputMaterialName = "Quantainium (Raw)",
        inputQuantity = input,
        outputQuantity = output,
    )

    private fun draft(vararg goods: RefineryGoodDraft) =
        RefineryOrderDraft(
            locationId = "loc1",
            locationName = "Levski",
            methodId = "met1",
            methodName = "Cormack",
            goods = goods.toList(),
        )

    @Test
    fun `a complete form may be sent`() {
        assertTrue(draft(good()).sendable)
    }

    @Test
    fun `a typed material name without a pick is not a material`() {
        // The wire wants an id. A name alone drops the line, and a form that looks filled would
        // then be refused by the server for a line the member believed they had entered.
        assertFalse(draft(good(material = null)).sendable)
    }

    @Test
    fun `a line without an output quantity is not sendable`() {
        // `@NotNull @Min(1)` on the wire. Sending 0 earns „muss größer-gleich 1 sein" against
        // `goods[0]`, which names an index, not a field.
        assertFalse(draft(good(output = "")).sendable)
        assertFalse(draft(good(output = "0")).sendable)
    }

    @Test
    fun `one incomplete line blocks the whole order, not just itself`() {
        // The call carries every line; a half-filled one refuses all of them.
        assertFalse(draft(good(), good(material = null)).sendable)
    }

    @Test
    fun `without a refinery or a method nothing is sent`() {
        assertFalse(draft(good()).copy(locationId = null).sendable)
        assertFalse(draft(good()).copy(methodId = null).sendable)
    }

    @Test
    fun `the start is read from the two fields the member fills`() {
        val at = draft(good()).copy(startedDate = "27.08.2026", startedTime = "21:00").startedAt

        // Read back in the device's own zone, not asserted as a UTC literal: the member typed a
        // wall clock, and what the instant looks like in UTC depends on where they are. A literal
        // here passes in Berlin and fails on a CI runner set to UTC, which is a test about the
        // runner rather than about the parse.
        val local = requireNotNull(at).atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(TYPED_START, local)
    }

    @Test
    fun `a half-typed date is no date, rather than a guess`() {
        assertNull(draft(good()).copy(startedDate = "27.08.", startedTime = "21:00").startedAt)
        assertNull(draft(good()).startedAt)
    }

    @Test
    fun `the quantities read back in SCU beside the units the field takes`() {
        // The wire counts units, a hundred to the SCU, and the labels say so. This is the figure
        // shown beside them -- REQ-APP-REF-004a records what confusing the two cost the last time:
        // a booking that would have written a Lager entry a hundred times the yield.
        val line = good(input = "62000", output = "44200")
        assertEquals(INPUT_SCU, requireNotNull(line.inputScu), SCU_TOLERANCE)
        assertEquals(OUTPUT_SCU, requireNotNull(line.outputScu), SCU_TOLERANCE)
    }

    @Test
    fun `a quantity that is not a number yet has no SCU reading`() {
        // Shown as an absence rather than as 0,00 SCU, which would claim a run of nothing.
        assertNull(good(output = "").outputScu)
        assertNull(good(input = "6,2").inputScu)
    }
}

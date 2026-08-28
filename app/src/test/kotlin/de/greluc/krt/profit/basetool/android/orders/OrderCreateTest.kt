/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What „Neuer Auftrag" may and may not send.
 *
 * Two rules carry the weight. **A half-filled line blocks the submit** rather than being dropped:
 * `POST /orders` takes whatever lines it is handed, so a form that quietly discarded a line the
 * member had typed a material into would raise an order missing that material and say nothing. And
 * **a typed name is not a material** — the wire wants an id, and a query left behind by an
 * abandoned pick carries none.
 */
class OrderCreateTest {
    private companion object {
        /** The one minimum quality the web offers besides „Keine". */
        const val GRADE = 650

        /** What „12,5" has to come out as. */
        const val TWELVE_AND_A_HALF = 12.5

        /** A line the server would accept. */
        val FULL =
            OrderLineDraft(
                materialId = "m1",
                materialName = "Quantainium (Raw)",
                query = "Quantainium (Raw)",
                amount = "620",
            )
    }

    private fun state(vararg lines: OrderLineDraft) =
        OrderCreateState(
            responsibleId = "ou-responsible",
            requestingId = "ou-requesting",
            handle = "Sturmkind",
            lines = if (lines.isEmpty()) listOf(OrderLineDraft()) else lines.toList(),
            loading = false,
        )

    @Test
    fun `a complete form may be sent`() {
        assertTrue(state(FULL).submittable)
    }

    @Test
    fun `a trailing empty line does not block the submit`() {
        // The form always keeps one empty line at the end. Treating it as unfinished would make
        // every order unsendable until the member removed a line they never filled in.
        assertTrue(state(FULL, OrderLineDraft()).submittable)
    }

    @Test
    fun `a material without an amount blocks the submit`() {
        assertFalse(state(FULL, FULL.copy(amount = "")).submittable)
    }

    @Test
    fun `an amount without a material blocks the submit`() {
        assertFalse(state(FULL, OrderLineDraft(amount = "5")).submittable)
    }

    @Test
    fun `a typed material name without a pick is not a material`() {
        // Only `query` is set — what a member sees after typing and not choosing. The line carries
        // no id, so it would be dropped, and the form must not look sendable because of it.
        assertFalse(state(OrderLineDraft(query = "Quant")).submittable)
    }

    @Test
    fun `a zero amount is not an amount`() {
        assertFalse(state(FULL.copy(amount = "0")).submittable)
    }

    @Test
    fun `a decimal comma is an amount`() {
        // A German keyboard produces „12,5". Every earlier form in this app read that as zero.
        val draft = state(FULL.copy(amount = "12,5")).toDraft()
        assertEquals(TWELVE_AND_A_HALF, draft?.lines?.single()?.amount)
    }

    @Test
    fun `both units and a handle are required`() {
        assertFalse(state(FULL).copy(responsibleId = null).submittable)
        assertFalse(state(FULL).copy(requestingId = null).submittable)
        assertFalse(state(FULL).copy(handle = "   ").submittable)
    }

    @Test
    fun `a blank comment is sent as nothing at all`() {
        // An empty string would be stored as an empty comment; the field is optional and „absent"
        // is what the member meant.
        assertNull(state(FULL).copy(comment = "   ").toDraft()?.comment)
    }

    @Test
    fun `only the finished lines reach the wire`() {
        val draft = state(FULL, OrderLineDraft()).toDraft()
        assertEquals(1, draft?.lines?.size)
    }

    @Test
    fun `the minimum quality travels with its line`() {
        val draft = state(FULL.copy(minQuality = GRADE)).toDraft()
        assertEquals(GRADE, draft?.lines?.single()?.minQuality)
        assertNull(state(FULL).toDraft()?.lines?.single()?.minQuality)
    }

    @Test
    fun `a form still saving may not be sent twice`() {
        assertFalse(state(FULL).copy(saving = true).submittable)
    }
}

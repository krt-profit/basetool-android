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
 * Tests what „Neuer Auftrag" may send: a half-filled line blocks the submit rather than being dropped, and a typed name
 * without a picked material carries no id.
 */
class OrderCreateTest {
    private companion object {
        /** The one minimum quality the web offers besides „Keine". */
        const val GRADE = 650

        /** What „12,5" has to come out as. */
        const val TWELVE_AND_A_HALF = 12.5

        /** How many of the item the fixture asks for. */
        const val THREE = 3

        /** An item line the server would accept. */
        val ITEM =
            OrderItemLineDraft(
                gameItemId = "gi1",
                itemName = "Medizinische Station T2",
                query = "Medizinische Station T2",
                blueprintId = "bp1",
                blueprints = listOf("bp1" to "Medizinische Station T2"),
                amount = "3",
            )

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

    private fun itemState(vararg lines: OrderItemLineDraft) =
        OrderCreateState(
            kind = OrderKind.ITEM,
            responsibleId = "ou-responsible",
            requestingId = "ou-requesting",
            handle = "Sturmkind",
            itemLines = if (lines.isEmpty()) listOf(OrderItemLineDraft()) else lines.toList(),
            loading = false,
        )

    @Test
    fun `a complete form may be sent`() {
        assertTrue(state(FULL).submittable)
    }

    @Test
    fun `a trailing empty line does not block the submit`() {
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
        assertFalse(state(OrderLineDraft(query = "Quant")).submittable)
    }

    @Test
    fun `a zero amount is not an amount`() {
        assertFalse(state(FULL.copy(amount = "0")).submittable)
    }

    @Test
    fun `a decimal comma is an amount`() {
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
        assertNull(state(FULL.copy(minQuality = null)).toDraft()?.lines?.single()?.minQuality)
    }

    @Test
    fun `a fresh line asks for the grade the web form asks for`() {
        assertEquals(DEFAULT_MIN_QUALITY, OrderLineDraft().minQuality)
        assertEquals(GRADE, DEFAULT_MIN_QUALITY)
    }

    @Test
    fun `a form still saving may not be sent twice`() {
        assertFalse(state(FULL).copy(saving = true).submittable)
    }

    @Test
    fun `a complete item form may be sent`() {
        assertTrue(itemState(ITEM).submittable)
    }

    @Test
    fun `the kind decides which lines are judged`() {
        val mixed = itemState(OrderItemLineDraft()).copy(lines = listOf(FULL))
        assertFalse(mixed.submittable)
        assertTrue(mixed.copy(kind = OrderKind.MATERIAL).submittable)
    }

    @Test
    fun `an item without a blueprint blocks the submit`() {
        assertFalse(itemState(ITEM.copy(blueprintId = null)).submittable)
    }

    @Test
    fun `an item without a count blocks the submit`() {
        assertFalse(itemState(ITEM.copy(amount = "")).submittable)
        assertFalse(itemState(ITEM.copy(amount = "0")).submittable)
    }

    @Test
    fun `a typed item name without a pick is not an item`() {
        assertFalse(itemState(OrderItemLineDraft(query = "Medizin")).submittable)
    }

    @Test
    fun `a trailing empty item line does not block the submit`() {
        assertTrue(itemState(ITEM, OrderItemLineDraft()).submittable)
    }

    @Test
    fun `only the finished item lines reach the wire`() {
        val draft = itemState(ITEM, OrderItemLineDraft()).toItemDraft()
        assertEquals(1, draft?.lines?.size)
        assertEquals(THREE, draft?.lines?.single()?.amount)
        assertEquals("bp1", draft?.lines?.single()?.blueprintId)
    }

    @Test
    fun `an item order carries the same head as a material order`() {
        val draft = itemState(ITEM).copy(comment = "  ").toItemDraft()
        assertEquals("ou-responsible", draft?.responsibleOrgUnitId)
        assertEquals("ou-requesting", draft?.requestingOrgUnitId)
        assertEquals("Sturmkind", draft?.handle)
        assertNull(draft?.comment)
    }
}

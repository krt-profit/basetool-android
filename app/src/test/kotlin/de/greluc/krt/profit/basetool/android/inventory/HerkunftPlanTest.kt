/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.InventoryAllocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deduct-from contract, as the server states it (REQ-INV-027).
 *
 * These are the rules a member would otherwise learn from a 400 or a 422 after the write. Each one
 * is asserted here so the sheet can refuse in place instead — and so that a later "simplification"
 * of the arithmetic has to break a named rule rather than a feeling.
 */
class HerkunftPlanTest {
    private companion object {
        /** What is leaving the entry in most of these cases. */
        const val DEDUCTED = 200.0

        /** Comparison tolerance — these are quantities, not exact binary fractions. */
        const val EPS = 1e-9

        /** What the rest carries when the tags leave 120 of the 200 to it. */
        const val REMAINDER = 80.0

        /** By how much the 300 assigned overshoots the 200 deducted. */
        const val OVERSHOOT = 100.0

        /** The share assigned to the first tag when the second is left at zero. */
        const val SINGLE_SHARE = 150.0
    }

    private fun tag(
        id: String,
        amount: String,
    ) = InventoryAllocation(targetId = id, label = "#A-$id", subtitle = null, amount = amount)

    private val twoTags = listOf(tag("a", "200"), tag("b", "120"))

    @Test
    fun `tags covering the whole deduction leave nothing for the rest`() {
        val d = herkunftDimension(twoTags, rest = "122", deducted = DEDUCTED, typed = mapOf("a" to "150", "b" to "50"))

        assertEquals(HerkunftStatus.COVERED, d.status)
        assertEquals(0.0, d.fromRest, EPS)
        assertTrue(d.valid)
    }

    @Test
    fun `what the tags do not cover comes from the rest`() {
        val d = herkunftDimension(twoTags, rest = "142", deducted = DEDUCTED, typed = mapOf("a" to "120"))

        assertEquals(HerkunftStatus.FROM_REST, d.status)
        assertEquals(REMAINDER, d.fromRest, EPS)
        assertTrue(d.valid)
    }

    /** The server answers 400; the sheet must not offer the save. */
    @Test
    fun `tags claiming more than is deducted are over-allocated`() {
        val d = herkunftDimension(twoTags, rest = "142", deducted = DEDUCTED, typed = mapOf("a" to "180", "b" to "120"))

        assertEquals(HerkunftStatus.OVERALLOCATED, d.status)
        assertFalse(d.valid)
        assertEquals(OVERSHOOT, d.overshoot(DEDUCTED), EPS)
    }

    /** The server answers 422 — a different refusal, and one the member fixes differently. */
    @Test
    fun `a rest too small to carry the remainder is its own refusal`() {
        val d = herkunftDimension(twoTags, rest = "10", deducted = DEDUCTED, typed = mapOf("a" to "20"))

        assertEquals(HerkunftStatus.REST_TOO_SMALL, d.status)
        assertFalse(d.valid)
    }

    /**
     * One tag and no rest is not a question.
     *
     * Every unit leaving has to come from that tag, so the field follows the deducted amount and is
     * locked. Typing anything else could only ever trip one of the two refusals.
     */
    @Test
    fun `one tag and no rest fills itself and locks`() {
        val d = herkunftDimension(listOf(tag("m", "442")), rest = "0", deducted = DEDUCTED, typed = emptyMap())

        assertEquals(HerkunftStatus.AUTOMATIC, d.status)
        assertTrue(d.locked)
        assertEquals(DEDUCTED, d.assigned, EPS)
        assertTrue(d.valid)
    }

    @Test
    fun `the locked shape ignores whatever was typed`() {
        val d = herkunftDimension(listOf(tag("m", "442")), rest = null, deducted = DEDUCTED, typed = mapOf("m" to "7"))

        assertEquals(DEDUCTED, d.assigned, EPS)
        assertEquals(listOf("m" to DEDUCTED), d.reductions(DEDUCTED, mapOf("m" to "7")))
    }

    /**
     * An untouched plan sends nothing at all.
     *
     * An empty list is the server's documented "take it from the rest first". A list of zeroes says
     * the same thing in a form that has to be parsed to mean nothing, and it would make an
     * untouched sheet look like a deliberate plan in the audit trail.
     */
    @Test
    fun `an untouched plan sends no reductions`() {
        val d = herkunftDimension(twoTags, rest = "500", deducted = DEDUCTED, typed = emptyMap())

        assertEquals(HerkunftStatus.FROM_REST, d.status)
        assertTrue(d.reductions(DEDUCTED, emptyMap()).isEmpty())
    }

    @Test
    fun `a tag left at zero is omitted rather than sent as zero`() {
        val typed = mapOf("a" to "150", "b" to "0")
        val d = herkunftDimension(twoTags, rest = "122", deducted = DEDUCTED, typed = typed)

        assertEquals(listOf("a" to SINGLE_SHARE), d.reductions(DEDUCTED, typed))
    }

    /**
     * Floating-point noise is not an over-allocation.
     *
     * `0.1 + 0.2` is not `0.3` in binary, and without SCU rounding a plan that adds up exactly
     * would show the red „ÜBERZEICHNET" chip and refuse a save the server would have accepted.
     */
    @Test
    fun `a plan that adds up exactly is not over-allocated by floating-point noise`() {
        val tags = listOf(tag("a", "1"), tag("b", "1"), tag("c", "1"))
        val d = herkunftDimension(tags, rest = "0", deducted = 0.3, typed = mapOf("a" to "0.1", "b" to "0.2"))

        assertEquals(HerkunftStatus.COVERED, d.status)
        assertTrue(d.valid)
    }

    /** A dimension the entry has no tags in contributes nothing and refuses nothing. */
    @Test
    fun `a dimension with no tags takes everything from the rest`() {
        val d = herkunftDimension(emptyList(), rest = "500", deducted = DEDUCTED, typed = emptyMap())

        assertEquals(HerkunftStatus.FROM_REST, d.status)
        assertTrue(d.valid)
        assertTrue(d.reductions(DEDUCTED, emptyMap()).isEmpty())
    }

    /**
     * An entry with no earmarks at all — which is most of them — is never refused.
     *
     * The server sends no rest for a dimension it has no split in, and reading that absence as a
     * rest of **zero** made every ordinary book-out fail the "the rest cannot carry it" check. The
     * existing booking tests caught it; this one names it so it cannot come back quietly.
     */
    @Test
    fun `no tags and no rest at all is still valid`() {
        val d = herkunftDimension(emptyList(), rest = null, deducted = DEDUCTED, typed = emptyMap())

        assertTrue("an entry with no earmarks has nothing to reconcile", d.valid)
        assertEquals(HerkunftStatus.FROM_REST, d.status)
        assertTrue(d.reductions(DEDUCTED, emptyMap()).isEmpty())
    }
}

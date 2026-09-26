/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.GameItemStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the Game-Item screen (design ch. 09, artboard 21): search and category filter the complete unpaginated read,
 * and the category chips come from the `kind` values present.
 */
class GameItemStockTest {
    private companion object {
        /** Pieces of the row the category filter keeps. */
        const val WAFFEN_AMOUNT = 12.0

        /** Pieces of the row it filters away. */
        const val OTHER_AMOUNT = 24.0

        /** Doubles compared to the piece. */
        const val TOLERANCE = 0.001
    }

    @Test
    fun theChipsComeFromTheData() {
        val state =
            GameItemStockState(
                items =
                    listOf(
                        item("a", kind = "Waffen"),
                        item("b", kind = "Medizin"),
                        item("c", kind = "Waffen"),
                        item("d", kind = null),
                    ),
            )

        assertEquals(listOf("Medizin", "Waffen"), state.kinds)
    }

    @Test
    fun theFilterIsCompleteBecauseTheReadIs() {
        val state =
            GameItemStockState(
                items = listOf(item("a", name = "Medpen"), item("b", name = "Ballistic Gatling")),
                query = "gat",
            )

        assertEquals(listOf("b"), state.visible.map { it.id })
        assertEquals(1, state.visible.size)
    }

    @Test
    fun theCategoryAndTheSearchNarrowTogether() {
        val state =
            GameItemStockState(
                items =
                    listOf(
                        item("a", name = "Medpen", kind = "Medizin"),
                        item("b", name = "Medpen Pro", kind = "Waffen"),
                    ),
                query = "med",
                kind = "Medizin",
            )

        assertEquals(listOf("a"), state.visible.map { it.id })
    }

    @Test
    fun theTotalCountsWhatIsShown() {
        val state =
            GameItemStockState(
                items =
                    listOf(
                        item("a", amount = OTHER_AMOUNT),
                        item("b", amount = WAFFEN_AMOUNT, kind = "Waffen"),
                    ),
                kind = "Waffen",
            )

        assertEquals(WAFFEN_AMOUNT, state.totalAmount, TOLERANCE)
    }

    @Test
    fun noCategoryPickedMeansEverything() {
        val state = GameItemStockState(items = listOf(item("a", kind = "Waffen")))

        assertNull(state.kind)
        assertEquals(1, state.visible.size)
    }

    /**
     * One row.
     *
     * @param id its id.
     * @param name what it is called.
     * @param kind its category.
     * @param amount how many pieces.
     * @return the row.
     */
    private fun item(
        id: String,
        name: String = "Medpen",
        kind: String? = null,
        amount: Double = 1.0,
    ) = GameItemStock(
        id = id,
        name = name,
        kind = kind,
        manufacturer = null,
        amount = amount,
        holders = 1,
        locations = listOf("ARC-L1"),
    )
}

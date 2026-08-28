/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankHolderBooking
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the holder detail renders — design chapter 12, artboard 8.
 *
 * The claim under test is the one the handoff had to correct on 27.08.2026: custody is kept at
 * **org-unit level** and is not allocated to accounts. A screen that quietly implied otherwise —
 * by printing an account beside the figure, or by leaving the sentence off — would be wrong in the
 * one way this screen can be wrong.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BankHolderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun booking(
        type: String = "HOLDER_TRANSFER",
        counterHolder: String? = "Dorn",
    ) = BankHolderBooking(
        id = "p1",
        transactionId = "t1",
        type = type,
        amount = "-250.0000",
        note = null,
        createdAt = null,
        counterAccount = null,
        counterHolder = counterHolder,
        reversed = false,
    )

    private fun render(
        bookings: List<BankHolderBooking> = listOf(booking()),
        management: Boolean = true,
        totalPages: Int = 1,
        actions: BankHolderActions = BankHolderActions({}, {}, {}),
    ) {
        compose.setContent {
            KrtTheme {
                BankHolderScreen(
                    state =
                        BankHolderState(
                            holder =
                                BankHolder(
                                    id = "h1",
                                    handle = "Rhea",
                                    active = true,
                                    totalHeld = "118600.0000",
                                    version = 1,
                                ),
                            bookings = bookings,
                            totalElements = bookings.size.toLong(),
                            totalPages = totalPages,
                            phase = BankPhase.Ready,
                        ),
                    management = management,
                    actions = actions,
                )
            }
        }
    }

    @Test
    fun `the custody figure says it is unit-level, not per account`() {
        render()

        compose
            .onNodeWithText("es gibt keine Zuordnung zu einzelnen Konten", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `a booking kind is translated, not printed as the wire enum`() {
        render()

        compose.onNodeWithText("Verwahrerwechsel", ignoreCase = true).assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("HOLDER_TRANSFER").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `without Bank-Management the transfer is drawn locked rather than hidden`() {
        var locked = 0
        val transfers = mutableListOf<Unit>()
        render(
            management = false,
            actions =
                BankHolderActions(
                    onTransfer = { transfers.add(Unit) },
                    onPage = {},
                    onLocked = { locked++ },
                ),
        )

        compose.onNodeWithText("Halter-Umbuchung", ignoreCase = true).performClick()

        assertEquals(1, locked)
        assertTrue(transfers.isEmpty())
    }

    @Test
    fun `a single page of postings carries no pager`() {
        render(totalPages = 1)

        // A pager that cannot go anywhere is furniture that suggests there is more to see.
        assertEquals(
            0,
            compose.onAllNodesWithText("Weiter", ignoreCase = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `an empty custody says so instead of rendering nothing`() {
        render(bookings = emptyList())

        compose.onNodeWithText("Keine Buchungen", ignoreCase = true).assertIsDisplayed()
    }
}

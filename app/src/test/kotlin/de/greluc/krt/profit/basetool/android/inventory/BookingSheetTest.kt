/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BookOutKind
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.TerminalOption
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the booking sheet draws.
 *
 * The two things worth pinning: a mode is only offered when the entry can be booked that way, and a
 * refused booking still shows every field the member typed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BookingSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun entry(personal: Boolean = false) =
        InventoryEntry(
            id = "e1",
            materialName = "Quantainium",
            materialId = "m1",
            unit = "SCU",
            locationName = "ARC-L1",
            locationId = "l1",
            holder = "Rhea",
            holderId = "u1",
            amount = "12,5",
            quality = "880",
            personal = personal,
            note = "Reserviert",
            version = 5L,
        )

    /**
     * Renders the sheet.
     *
     * @param state what the form holds.
     * @param modes receives every mode the segment reports.
     * @param saved records that the save action was taken.
     */
    private fun show(
        state: BookingState,
        modes: MutableList<BookingMode> = mutableListOf(),
        saved: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                BookingSheet(
                    state = state,
                    callbacks =
                        BookingCallbacks(
                            onMode = { modes.add(it) },
                            onAmount = {},
                            onQuality = {},
                            onMaterialQuery = {},
                            onMaterial = {},
                            onPlaceQuery = {},
                            onPlace = {},
                            onOutKind = {},
                            onMemberQuery = {},
                            onMember = {},
                            onTerminal = {},
                            onSellAmount = {},
                            onNote = {},
                            onSave = { saved.add(Unit) },
                            onDismiss = {},
                        ),
                )
            }
        }
    }

    @Test
    fun `booking in asks for a material, the tree's own action having named none`() {
        show(BookingState(mode = BookingMode.IN))

        compose.onNodeWithTag(BOOKING_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Material").assertIsDisplayed()
    }

    @Test
    fun `an entry is offered booking out and its note, and no rebooking`() {
        // Rebooking turns private stock into shared stock, and the Lager reads exclude private
        // stock — so no entry that could be rebooked ever reaches this sheet, and the segment half
        // would only ever return a refusal.
        show(BookingState(mode = BookingMode.OUT, entry = entry()))

        compose.onAllNodesWithText("Umbuchen", ignoreCase = true).assertCountEquals(0)
        // The sheet's title and the segment half both say it.
        compose.onAllNodesWithText("Ausbuchen", ignoreCase = true).assertCountEquals(2)
        compose.onNodeWithText("Notiz", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `picking a mode reports it`() {
        val modes = mutableListOf<BookingMode>()
        show(BookingState(mode = BookingMode.OUT, entry = entry()), modes = modes)

        compose.onNodeWithText("Notiz", ignoreCase = true).performClick()

        assertEquals(listOf(BookingMode.NOTE), modes)
    }

    @Test
    fun `the note mode shows the note and no amount`() {
        show(BookingState(mode = BookingMode.NOTE, entry = entry(), note = "Reserviert"))

        compose.onNodeWithText("Reserviert").assertIsDisplayed()
        compose.onAllNodesWithText("Menge (SCU)").assertCountEquals(0)
    }

    @Test
    fun `a sale with no terminals recorded says so`() {
        show(
            BookingState(
                mode = BookingMode.OUT,
                entry = entry(),
                outKind = BookOutKind.SELL,
            ),
        )

        compose.onNodeWithText("Für dieses Material sind keine Terminals hinterlegt.")
            .assertIsDisplayed()
    }

    @Test
    fun `a sale lists what a terminal pays`() {
        show(
            BookingState(
                mode = BookingMode.OUT,
                entry = entry(),
                outKind = BookOutKind.SELL,
                terminals = listOf(TerminalOption("t1", "Area18 TDD", "170000")),
            ),
        )

        // The price is grouped like every other figure in the app.
        compose.onNodeWithText("Area18 TDD · 170.000").assertIsDisplayed()
    }

    @Test
    fun `an incomplete form offers no save`() {
        show(BookingState(mode = BookingMode.OUT, entry = entry()))

        compose.onNodeWithTag(BOOKING_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a complete form saves`() {
        val saved = mutableListOf<Unit>()
        show(
            BookingState(
                mode = BookingMode.OUT,
                entry = entry(),
                amount = "2",
                outKind = BookOutKind.TRANSFER,
                member = MemberOption("u2", "Kell"),
            ),
            saved = saved,
        )

        compose.onNodeWithTag(BOOKING_SAVE_TAG).assertIsEnabled().performClick()

        assertEquals(1, saved.size)
    }

    @Test
    fun `offline the form says so and offers no save`() {
        // The app never queues a booking: one taken offline would land against a Lager that has
        // moved on, and the member would never see the conflict.
        show(
            BookingState(
                mode = BookingMode.OUT,
                entry = entry(),
                amount = "2",
                online = false,
            ),
        )

        compose.onNodeWithText("Kein Netz — Ändern ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(BOOKING_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a transfer that moves nothing says so instead of offering a save`() {
        show(
            BookingState(
                mode = BookingMode.OUT,
                entry = entry(),
                amount = "2",
                outKind = BookOutKind.TRANSFER,
                member = MemberOption("u1", "Rhea"),
            ),
        )

        // The sheet scrolls, so the line can sit below the fold — that it is there and that the
        // save is refused is the point.
        compose.onNodeWithText(
            "So bleibt alles, wo es ist — wähle einen anderen Nutzer oder einen anderen Ort.",
        ).assertExists()
        compose.onNodeWithTag(BOOKING_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a conflict is named and every field is still there`() {
        show(
            BookingState(
                mode = BookingMode.OUT,
                entry = entry(),
                amount = "2",
                error = ApiError.OptimisticLock(),
            ),
        )

        compose.onNodeWithText(
            "Jemand anderes hat diesen Eintrag inzwischen geändert. Deine Eingabe bleibt stehen — " +
                "lade neu und speichere erneut.",
        ).assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.exchange

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BoardEntry
import de.greluc.krt.profit.basetool.android.core.data.BoardSide
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the board renders.
 *
 * The unit assertion is the one that matters off-screen: an item counted in pieces and labelled
 * „SCU" is a quantity a member would act on in a handover the tool never sees.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class MaterialBoardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun entry(
        id: String = "o1",
        mine: Boolean = false,
        piece: Boolean = false,
        side: BoardSide = BoardSide.OFFERS,
        handles: List<String>? = null,
    ) = BoardEntry(
        id = id,
        side = side,
        materialName = if (piece) "Size 3 Shield" else "Quantainium",
        unitIsPiece = piece,
        amount = if (piece) "6" else "240.0",
        quality = if (piece) null else 3,
        ownerName = "Vex",
        ownerOrgUnits = listOf("SK VG"),
        postedAt = "2026-08-24T09:29:53.187358Z",
        remark = null,
        interestCount = 2,
        interestedHandles = handles,
        viewerInterested = false,
        mine = mine,
        version = 1,
    )

    /**
     * Renders the board.
     *
     * @param entries the rows.
     * @param side the segment.
     */
    private fun board(
        entries: List<BoardEntry>,
        side: BoardSide = BoardSide.OFFERS,
    ) {
        compose.setContent {
            KrtTheme {
                MaterialBoardScreen(
                    state =
                        MaterialBoardState(
                            side = side,
                            entries = entries,
                            phase = BoardPhase.Ready,
                        ),
                    onSideChanged = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    onSignalToggled = {},
                    onWithdraw = {},
                    onCreate = {},
                )
            }
        }
    }

    @Test
    fun `the privacy line of chapter 10 is on the screen`() {
        board(listOf(entry()))

        // Copy, not decoration: the board carries no place and no handover, and a member has to be
        // able to read that off the screen rather than infer it from an absence.
        compose.onNodeWithTag(BOARD_PRIVACY_TAG).assertIsDisplayed()
    }

    @Test
    fun `an item row counts pieces, never SCU`() {
        board(listOf(entry(piece = true)))

        // The figure and its unit are two nodes since the amount moved into the header
        // (REQ-APP-MARKET-010): the quantity is what the board is scanned for, so it carries the
        // weight and the unit stays quiet beside it.
        compose.onNodeWithText("6").assertIsDisplayed()
        compose.onNodeWithText("Stück").assertIsDisplayed()
        compose.onNodeWithText("2 Zusagen").assertIsDisplayed()
    }

    @Test
    fun `a material row shows its quality beside the amount`() {
        board(listOf(entry()))

        // "240", not "240.0": the wire carries the trailing zero and a member does not read it.
        // Found on a device.
        compose.onNodeWithText("240").assertIsDisplayed()
        compose.onNodeWithText("SCU").assertIsDisplayed()
        compose.onNodeWithText("Q 3 · 2 Zusagen").assertIsDisplayed()
    }

    @Test
    fun `a request row says the quality is a minimum`() {
        board(listOf(entry(side = BoardSide.REQUESTS)), side = BoardSide.REQUESTS)

        // „Q 3" on a request would read as an offered grade. It is the floor the requester will
        // accept, and the two are opposite claims.
        compose.onNodeWithText("240").assertIsDisplayed()
        compose.onNodeWithText("Min. Q 3 · 2 Zusagen").assertIsDisplayed()
        // The timestamp is rendered as a relative span in the member's zone, never as the wire's
        // ISO string — which is what the row printed until a device walk showed it.
        compose.onNodeWithText("Gesucht von Vex", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2026-08-24T09:29:53.187358Z", substring = true).assertDoesNotExist()
    }

    @Test
    fun `somebody else's row offers the toggle`() {
        board(listOf(entry()))

        compose.onNodeWithTag(BOARD_SIGNAL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(BOARD_WITHDRAW_TAG).assertDoesNotExist()
    }

    @Test
    fun `the caller's own row offers Zurückziehen instead`() {
        board(listOf(entry(mine = true)))

        compose.onNodeWithTag(BOARD_WITHDRAW_TAG).assertIsDisplayed()
        compose.onNodeWithTag(BOARD_SIGNAL_TAG).assertDoesNotExist()
    }

    @Test
    fun `the supporter list appears only where the server sent one`() {
        board(listOf(entry(mine = true, handles = listOf("Nova", "Ash"))))

        compose.onNodeWithText("Nova, Ash").assertIsDisplayed()
    }

    @Test
    fun `a row nobody owns shows no empty supporter heading`() {
        board(listOf(entry()))

        // The handles are null for everybody but the owner. An empty „Zusagen" heading would imply
        // nobody had answered, which is a different claim from "you may not see who did".
        compose.onNodeWithText("ZUSAGEN").assertDoesNotExist()
    }
}

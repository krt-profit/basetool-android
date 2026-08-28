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
 * The board on a tablet, where it lays its cards out in two columns.
 *
 * Two things are easy to lose in the swap from a column to a grid and neither shows up as a crash:
 * a row can go missing when the keys collide, and the footer can end up inside one column instead
 * of under both. Both are pinned here, at the width that selects the grid.
 *
 * The column count itself is not asserted — Robolectric measures, so a count would only re-state
 * `BOARD_WIDE_COLUMNS`. What is asserted is that nothing is dropped by taking that path.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w1280dp-h800dp-xhdpi")
class MaterialBoardWideTest {
    @get:Rule
    val compose = createComposeRule()

    private fun entry(
        id: String,
        name: String,
    ) = BoardEntry(
        id = id,
        side = BoardSide.OFFERS,
        materialName = name,
        unitIsPiece = false,
        amount = "240.0",
        quality = 3,
        ownerName = "Vex",
        ownerOrgUnits = listOf("SK VG"),
        postedAt = "2026-08-24T09:29:53.187358Z",
        remark = null,
        interestCount = 0,
        interestedHandles = null,
        viewerInterested = false,
        mine = false,
        version = 1,
    )

    /**
     * Renders the board at tablet width.
     *
     * @param entries the rows.
     * @param hasMore whether a further page exists.
     */
    private fun board(
        entries: List<BoardEntry>,
        hasMore: Boolean = false,
    ) {
        compose.setContent {
            KrtTheme {
                MaterialBoardScreen(
                    state =
                        MaterialBoardState(
                            side = BoardSide.OFFERS,
                            entries = entries,
                            hasMore = hasMore,
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
    fun `every card survives the grid`() {
        // An odd count on purpose: the last row of a two-column grid is half empty, which is where
        // a span or a key mistake shows first.
        board(
            listOf(
                entry("o1", "Quantainium"),
                entry("o2", "Laranite"),
                entry("o3", "Agricium"),
            ),
        )

        compose.onNodeWithText("Quantainium").assertIsDisplayed()
        compose.onNodeWithText("Laranite").assertIsDisplayed()
        compose.onNodeWithText("Agricium").assertIsDisplayed()
    }

    @Test
    fun `the end of the list is drawn under both columns, not inside one`() {
        board(listOf(entry("o1", "Quantainium"), entry("o2", "Laranite")))

        compose.onNodeWithText("ENDE DER LISTE").assertIsDisplayed()
    }

    @Test
    fun `the load-more action reaches the grid too`() {
        // It lives in the same spanning footer slot, so a grid that lost the span would lose this
        // with it and the board would simply stop at page one with nothing saying so.
        board(listOf(entry("o1", "Quantainium")), hasMore = true)

        compose.onNodeWithTag(BOARD_LIST_TAG).assertIsDisplayed()
        // ignoreCase: KrtGhostButton uppercases its label, as every button in the design
        // system does.
        compose.onNodeWithText("mehr laden", substring = true, ignoreCase = true).assertIsDisplayed()
    }
}

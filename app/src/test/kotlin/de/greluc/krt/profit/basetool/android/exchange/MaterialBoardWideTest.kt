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
 * Tests the board at tablet width, where it uses two columns: no row is dropped by colliding keys and the footer spans
 * both columns.
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
        board(listOf(entry("o1", "Quantainium")), hasMore = true)

        compose.onNodeWithTag(BOARD_LIST_TAG).assertIsDisplayed()
        compose.onNodeWithText("mehr laden", substring = true, ignoreCase = true).assertIsDisplayed()
    }
}

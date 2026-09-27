/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BulkChangeResult
import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.data.PersonalFilter
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.ui.DenialState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What „Mein Lager" renders (design ch. 19, artboards 1 to 7), in German at phone width.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class MeinLagerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun myState() =
        InventoryState(
            scope = LagerScope.MY,
            groups = listOf(QUANTAINIUM.group),
            total = 1,
            phase = InventoryPhase.Ready,
            preloaded = mapOf("m1" to QUANTAINIUM.stacks),
            opened = mapOf("m1" to StackPhase.Ready(QUANTAINIUM.stacks)),
        )

    private fun show(
        state: InventoryState,
        lager: LagerScreenActions = LagerScreenActions(scoped = true),
    ) {
        compose.setContent {
            KrtTheme {
                InventoryScreen(
                    state = state,
                    onToggleGroup = {},
                    onToggleStack = { _, _ -> },
                    onToggleBranch = { _, _ -> },
                    onBookIn = {},
                    onBookOut = {},
                    onAllocate = {},
                    selection = emptySet(),
                    onToggleSelected = {},
                    denials = DenialState(),
                    onWithStockOnlyChanged = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    lager = lager,
                )
            }
        }
    }

    private fun callbacks(
        dropped: MutableList<Unit> = mutableListOf(),
        units: MutableList<String?> = mutableListOf(),
    ) = StockMoveCallbacks(
        onAmount = {},
        onAll = {},
        onUnit = { units.add(it) },
        onMerge = {},
        onMode = {},
        onDropShared = { dropped.add(Unit) },
        onConfirm = {},
        onClose = {},
        onConflictReload = {},
    )

    @Test
    fun `the scope segment, the stock-kind chips and the count line are drawn`() {
        val scopes = mutableListOf<LagerScope>()
        val kinds = mutableListOf<PersonalFilter>()
        show(myState(), LagerScreenActions(scoped = true, onScope = { scopes.add(it) }, onPersonal = { kinds.add(it) }))

        compose.onNodeWithText("ORG-LAGER").performClick()
        compose.onNodeWithText("NUR PERSÖNLICHE").performClick()

        compose.onNodeWithText("1 MATERIAL · NUR DEINE EINTRÄGE").assertIsDisplayed()
        assertEquals(listOf(LagerScope.ORG), scopes)
        assertEquals(listOf(PersonalFilter.PERSONAL_ONLY), kinds)
    }

    @Test
    fun `a stack names place and grade and carries its unit pill or Keine Einheit, with no holder level`() {
        show(myState())

        compose.onAllNodesWithText("ARC-L1 · Q 874").assertCountEquals(2)
        compose.onNodeWithText("PRO").assertIsDisplayed()
        compose.onNodeWithText("PERSÖNLICH").assertIsDisplayed()
        compose.onNodeWithText("KEINE EINHEIT").assertIsDisplayed()
        compose.onAllNodesWithText("Unbekannt", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an empty Mein Lager offers the book-in, an emptied filter offers the reset`() {
        show(myState().copy(groups = emptyList(), preloaded = emptyMap(), opened = emptyMap()))
        compose.onNodeWithText("Du hast noch keinen Bestand.").assertIsDisplayed()
        compose.onNodeWithText("EINBUCHEN").assertIsDisplayed()
    }

    @Test
    fun `the org-unit change lists Keine Einheit first, says who can see the row and waits for a change`() {
        compose.setContent {
            KrtTheme {
                StockMoveSheet(
                    move =
                        StockMoveState(
                            kind = StockMoveKind.ORG_UNIT,
                            entry = lagerEntry("p1", personal = true, unit = "iri"),
                            units = FOUR_UNITS,
                            unitsLoaded = true,
                            unitId = "iri",
                            initialUnitId = "iri",
                            scu = true,
                        ),
                    count = 0,
                    callbacks = callbacks(),
                )
            }
        }

        compose.onNodeWithTag(STOCK_MOVE_NO_UNIT_TAG).assertIsDisplayed()
        compose.onNodeWithText("OL").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Mitglieder mit Bearbeitungsrecht in dieser Einheit können den Eintrag dann sehen und ändern. " +
                    "Ohne Einheit siehst nur du ihn.",
            ).assertIsDisplayed()
        compose.onNodeWithTag(STOCK_MOVE_CONFIRM_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a rebooking into the shared Lager has no Keine Einheit option`() {
        compose.setContent {
            KrtTheme {
                StockMoveSheet(
                    move =
                        StockMoveState(
                            kind = StockMoveKind.REBOOK,
                            entry = lagerEntry("p1", personal = true),
                            amount = "80",
                            units = FOUR_UNITS,
                            unitsLoaded = true,
                            unitId = "iri",
                        ),
                    count = 0,
                    callbacks = callbacks(),
                )
            }
        }

        compose.onNodeWithText("Ins gemeinsame Lager umbuchen").assertIsDisplayed()
        compose.onAllNodesWithText("Keine Einheit").assertCountEquals(0)
        compose.onNodeWithText(
            "Pool im gemeinsamen Lager — vorbelegt mit der Einheit des Eintrags, nie leer.",
        ).assertExists()
    }

    @Test
    fun `a selection with shared rows is refused before the picker and offers the way out`() {
        val dropped = mutableListOf<Unit>()
        compose.setContent {
            KrtTheme {
                StockMoveSheet(
                    move =
                        StockMoveState(
                            kind = StockMoveKind.ORG_UNIT,
                            ids = listOf("p1", "s1", "s2"),
                            sharedInSelection = 2,
                        ),
                    count = 3,
                    callbacks = callbacks(dropped = dropped),
                )
            }
        }

        compose.onNodeWithText("Nicht möglich", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Keine Einheit").assertCountEquals(0)
        compose.onNodeWithTag(STOCK_MOVE_DROP_SHARED_TAG).performClick()
        assertEquals(1, dropped.size)
    }

    @Test
    fun `the selection's result says how many changed and how many already had the unit`() {
        compose.setContent {
            KrtTheme {
                StockMoveSheet(
                    move =
                        StockMoveState(
                            kind = StockMoveKind.ORG_UNIT,
                            ids = listOf("p1", "p2"),
                            changed = BulkChangeResult(2, 0),
                        ),
                    count = 2,
                    callbacks = callbacks(),
                )
            }
        }

        compose.onNodeWithText("Einheit bei 2 Einträgen geändert, 0 hatten sie bereits.").assertIsDisplayed()
        compose.onNodeWithTag(STOCK_MOVE_DONE_TAG).assertIsDisplayed()
    }

    @Test
    fun `the selection bar carries Ausbuchen, Umbuchen and the menu with Einheit aendern`() {
        val unit = mutableListOf<Unit>()
        compose.setContent {
            KrtTheme {
                MyLagerSelectionBar(
                    state = myState().copy(selection = setOf("p1")),
                    onCheckout = {},
                    onRebook = {},
                    onOrgUnit = { unit.add(Unit) },
                )
            }
        }

        compose.onNodeWithText("AUSBUCHEN").assertIsDisplayed()
        compose.onNodeWithTag(MY_LAGER_BAR_REBOOK_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MY_LAGER_BAR_MORE_TAG).performClick()
        compose.onNodeWithTag(MY_LAGER_BAR_ORG_UNIT_TAG).performClick()
        assertTrue(unit.isNotEmpty())
    }
}

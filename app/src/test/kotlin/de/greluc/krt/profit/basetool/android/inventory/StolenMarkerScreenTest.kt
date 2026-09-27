/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.data.StolenFilter
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.LocalCaller
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The „gestohlen" marker on screen (design ch. 19, artboards 3, 8 and 9; REQ-INV-053): shown
 * wherever stock appears whatever the server's switch, and its actions only while the server offers
 * them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class StolenMarkerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val stolenStack = lagerStack(personal = true, stolen = true)
    private val stolenEntry = lagerEntry("p1", personal = true).copy(stolen = true)

    private fun state(scope: LagerScope) =
        InventoryState(
            scope = scope,
            groups = listOf(QUANTAINIUM.group),
            total = 1,
            phase = InventoryPhase.Ready,
            opened = mapOf("m1" to StackPhase.Ready(listOf(stolenStack))),
            openedStacks = mapOf(stackKey("m1", stolenStack) to EntriesPhase.Ready(listOf(stolenEntry))),
        )

    private fun show(
        state: InventoryState,
        marking: Boolean,
        lager: LagerScreenActions = LagerScreenActions(scoped = true),
    ) {
        compose.setContent {
            Caller(marking) {
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

    @Composable
    private fun Caller(
        marking: Boolean,
        content: @Composable () -> Unit,
    ) {
        KrtTheme {
            CompositionLocalProvider(
                LocalCaller provides Identity(userId = "u1", logistician = true, markStolen = marking),
                content = content,
            )
        }
    }

    @Test
    fun `the stack and the entry both carry the chip in Mein Lager, even while marking is off`() {
        show(state(LagerScope.MY), marking = false)

        compose.onAllNodesWithTag(STOLEN_CHIP_TAG, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `the Org-Lager shows the chip too, and its rows get a menu only while marking is on`() {
        show(state(LagerScope.ORG), marking = false)
        compose.onAllNodesWithTag(STOLEN_CHIP_TAG, useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithTag(LAGER_ENTRY_MORE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a stolen row's menu offers to remove the marker`() {
        val marked = mutableListOf<Boolean>()
        show(
            state(LagerScope.ORG),
            marking = true,
            lager = LagerScreenActions(scoped = true, onMarkStolen = { _, stolen -> marked.add(stolen) }),
        )

        compose.onNodeWithTag(LAGER_ENTRY_MORE_TAG, useUnmergedTree = true).performClick()
        compose.onNodeWithText("GESTOHLEN“ ENTFERNEN", substring = true).performClick()

        assertEquals(listOf(false), marked)
    }

    @Test
    fun `the three-state filter excludes itself and resets on a second tap`() {
        val chosen = mutableListOf<StolenFilter>()
        show(
            state(
                LagerScope.MY,
            ).copy(filter = de.greluc.krt.profit.basetool.android.core.data.LagerFilter(stolen = StolenFilter.ONLY)),
            marking = false,
            lager = LagerScreenActions(scoped = true, onStolen = { chosen.add(it) }),
        )

        compose.onNodeWithTag(LAGER_STOLEN_ONLY_TAG).performClick()
        compose.onNodeWithTag(LAGER_STOLEN_WITHOUT_TAG).performClick()

        assertEquals(listOf(StolenFilter.ALL, StolenFilter.WITHOUT), chosen)
    }

    @Test
    fun `a partly marked row says what stays regular`() {
        compose.setContent {
            Caller(marking = true) {
                StockMoveSheet(
                    move =
                        StockMoveState(
                            kind = StockMoveKind.STOLEN,
                            entry = lagerEntry("p1", personal = true),
                            amount = "30",
                            stolen = true,
                            unitsLoaded = true,
                        ),
                    count = 0,
                    callbacks =
                        StockMoveCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("ALS GESTOHLEN MARKIEREN").assertIsDisplayed()
        compose.onNodeWithText(
            "Teilmenge: der markierte Teil wird ein eigener Eintrag. 50 SCU bleiben regulär.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the book-in offers Gestohlen only while the server does`() {
        compose.setContent {
            Caller(marking = false) {
                BookingSheet(state = BookingState(), callbacks = noBookingCallbacks())
            }
        }
        compose.onAllNodesWithTag(BOOKING_STOLEN_TAG).assertCountEquals(0)
    }

    @Test
    fun `the book-in's Gestohlen is there while the server offers it`() {
        compose.setContent {
            Caller(marking = true) {
                BookingSheet(state = BookingState(), callbacks = noBookingCallbacks())
            }
        }
        compose.onNodeWithTag(BOOKING_STOLEN_TAG).assertExists()
    }

    private fun noBookingCallbacks() =
        BookingCallbacks(
            onMode = {},
            onKind = {},
            onSplitAmount = { _, _, _ -> },
            onSplitStep = { _, _, _ -> },
            onSplitPicking = {},
            onSplitAdd = { _, _ -> },
            onSplitRemove = { _, _ -> },
            onGameItemQuery = {},
            onGameItem = {},
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
            onJobOrderShare = { _, _ -> },
            onMissionShare = { _, _ -> },
            onOrgUnit = {},
            onMergeStock = {},
            onPersonal = {},
            onSellAmount = {},
            onNote = {},
            onSave = {},
            onDismiss = {},
            onConflictReload = {},
        )
}

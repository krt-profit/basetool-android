/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * What the Hangar renders.
 *
 * The empty states carry the weight here: "you own no ship", "the org unit has none" and "your
 * filter matches none" are three different facts, and one message for all three would tell a member
 * something untrue about their own fleet.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class HangarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun ship(
        id: String,
        name: String? = "Meridian",
        fitted: Boolean = true,
        insurance: String? = "LTI",
    ) = Ship(
        id = id,
        name = name,
        typeName = "Carrack",
        manufacturerName = "Anvil Aerospace",
        insurance = insurance,
        locationName = "ARC-L1",
        fitted = fitted,
    )

    /**
     * Renders the Hangar.
     *
     * @param state what to draw.
     */
    private fun show(
        state: HangarState,
        created: MutableList<Unit> = mutableListOf(),
        edited: MutableList<Ship> = mutableListOf(),
        deleted: MutableList<Ship> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                HangarScreen(
                    state = state,
                    onSegmentSelected = {},
                    onSearchChanged = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    onCreate = { created.add(Unit) },
                    onEdit = { edited.add(it) },
                    onDelete = { deleted.add(it) },
                )
            }
        }
    }

    @Test
    fun `a ship card leads with its type and carries the member's own name`() {
        show(HangarState(ships = listOf(ship("s1")), shipsTotal = 1, phase = HangarPhase.Ready))

        compose.onNodeWithText("Carrack", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Meridian", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Anvil Aerospace").assertIsDisplayed()
        compose.onNodeWithTag(HANGAR_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `a ship without insurance says so rather than showing an empty chip`() {
        show(
            HangarState(
                ships = listOf(ship("s1", name = null, fitted = false, insurance = null)),
                shipsTotal = 1,
                phase = HangarPhase.Ready,
            ),
        )

        compose.onNodeWithText("Keine Versicherung", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Nicht fitted", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the org half shows the server's counts per type`() {
        show(
            HangarState(
                segment = HangarSegment.ORG,
                types = listOf(ShipTypeSummary("Carrack", "Anvil Aerospace", COUNT, FITTED)),
                typesTotal = 1,
                phase = HangarPhase.Ready,
            ),
        )

        compose.onNodeWithText("Carrack").assertIsDisplayed()
        compose.onNodeWithText("3 Schiffe · 2 fitted").assertIsDisplayed()
    }

    @Test
    fun `an empty own hangar invites the first ship`() {
        // It used to send the member to the web app. That stopped being true the moment the app
        // could add a ship itself, and an empty state that lies is worse than none.
        show(HangarState(phase = HangarPhase.Ready))

        compose.onNodeWithText("Kein Schiff im Hangar").assertIsDisplayed()
        compose.onNodeWithText("Leg dein erstes Schiff an — Typ, Versicherung und wo es steht.")
            .assertIsDisplayed()
    }

    @Test
    fun `an empty org half is a different sentence`() {
        show(HangarState(segment = HangarSegment.ORG, phase = HangarPhase.Ready))

        compose.onNodeWithText("Keine Schiffe").assertIsDisplayed()
    }

    @Test
    fun `a filtered miss is a third sentence`() {
        show(HangarState(searchText = "zzz", phase = HangarPhase.Ready))

        compose.onNodeWithText("Nichts gefunden").assertIsDisplayed()
    }

    @Test
    fun `a failure offers a retry`() {
        show(HangarState(phase = HangarPhase.Failed(ApiError.Network(IOException("offline")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the segment and the filter field are both there`() {
        show(HangarState(phase = HangarPhase.Ready))

        compose.onNodeWithTag(HANGAR_SEGMENT_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HANGAR_SEARCH_TAG).assertIsDisplayed()
    }

    private companion object {
        /** Ships of one type in the aggregate. */
        const val COUNT = 3L

        /** How many of them are fitted. */
        const val FITTED = 2L
    }

    @Test
    fun `the add action is offered on the member's own half`() {
        show(HangarState(segment = HangarSegment.MINE, phase = HangarPhase.Ready))

        compose.onNodeWithTag(HANGAR_ADD_TAG).assertIsDisplayed()
    }

    @Test
    fun `the add action is absent on the org aggregate`() {
        // It is a count per hull, and the ships behind it belong to other members. One setContent
        // per test: the rule refuses a second.
        show(HangarState(segment = HangarSegment.ORG, phase = HangarPhase.Ready))

        compose.onAllNodesWithTag(HANGAR_ADD_TAG).assertCountEquals(0)
    }

    @Test
    fun `offline, the write actions are shown and disabled`() {
        show(
            HangarState(
                segment = HangarSegment.MINE,
                ships = listOf(ship("s1")),
                phase = HangarPhase.Ready,
                online = false,
            ),
        )

        compose.onNodeWithText("Kein Netz — Ändern ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(HANGAR_ADD_TAG).assertIsNotEnabled()
    }

    @Test
    fun `tapping a ship opens it`() {
        val edited = mutableListOf<Ship>()
        show(
            HangarState(
                segment = HangarSegment.MINE,
                ships = listOf(ship("s1")),
                phase = HangarPhase.Ready,
            ),
            edited = edited,
        )

        compose.onNodeWithText("Carrack „Meridian\"").performClick()

        assertEquals("s1", edited.single().id)
    }

    @Test
    fun `an insurance the server would refuse cannot be saved`() {
        // LTI or 0..120 months, nothing else. The editor offers exactly those two shapes.
        compose.setContent {
            KrtTheme {
                ShipEditorSheet(
                    editor =
                        ShipEditor.Open(
                            hull = ShipTypeOption("t1", "Carrack", "Anvil Aerospace"),
                            insuranceLti = false,
                            insuranceMonths = "121",
                        ),
                    hulls = emptyList(),
                    places = emptyList(),
                    onName = {},
                    onHullQuery = {},
                    onHull = {},
                    onLti = {},
                    onMonths = {},
                    onPlace = {},
                    onFitted = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(SHIP_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the editor cannot be saved without a hull`() {
        compose.setContent {
            KrtTheme {
                ShipEditorSheet(
                    editor = ShipEditor.Open(),
                    hulls = emptyList(),
                    places = emptyList(),
                    onName = {},
                    onHullQuery = {},
                    onHull = {},
                    onLti = {},
                    onMonths = {},
                    onPlace = {},
                    onFitted = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(SHIP_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the delete confirmation names the ship`() {
        compose.setContent {
            KrtTheme {
                ShipDeleteModal(ship = ship("s1"), deleting = false, onConfirm = {}, onDismiss = {})
            }
        }

        compose.onNodeWithText("„Meridian“ wird aus deinem Hangar gelöscht.").assertIsDisplayed()
    }
}

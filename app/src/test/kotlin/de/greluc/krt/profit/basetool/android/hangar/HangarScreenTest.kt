/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
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
    private fun show(state: HangarState) {
        compose.setContent {
            KrtTheme {
                HangarScreen(
                    state = state,
                    onSegmentSelected = {},
                    onSearchChanged = {},
                    onRefresh = {},
                    onLoadMore = {},
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
    fun `an empty own hangar says where ships are added`() {
        show(HangarState(phase = HangarPhase.Ready))

        compose.onNodeWithText("Kein Schiff im Hangar").assertIsDisplayed()
        compose.onNodeWithText("Schiffe hinzufügen geht derzeit über die Weboberfläche.").assertIsDisplayed()
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
}

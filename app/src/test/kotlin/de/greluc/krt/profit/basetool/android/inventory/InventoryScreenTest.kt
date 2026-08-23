/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * What the Lager tree renders.
 *
 * The two states nobody looks at while developing: a group that failed to open, and a group with a
 * material the server sent without an id — which cannot be opened at all and must not pretend
 * otherwise.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class InventoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun group(
        id: String? = "m1",
        name: String = "Quantainium",
        amount: String? = "1250.5",
    ) = InventoryGroup(
        materialId = id,
        name = name,
        unit = "SCU",
        amount = amount,
        quality = "880",
        maxQuality = "940",
    )

    private fun stack() =
        InventoryStack(
            holder = "Rhea",
            location = "ARC-L1",
            personal = false,
            amount = "1000",
            quality = "880",
            entryCount = 1,
        )

    /**
     * Renders the tree.
     *
     * @param state what to draw.
     * @param toggled receives the material id of a tapped group.
     */
    private fun show(
        state: InventoryState,
        toggled: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                InventoryScreen(
                    state = state,
                    onToggleGroup = { toggled.add(it) },
                    onWithStockOnlyChanged = {},
                    onRefresh = {},
                    onLoadMore = {},
                )
            }
        }
    }

    @Test
    fun `a group states its material, its amount and its unit`() {
        show(InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready))

        compose.onNodeWithText("Quantainium").assertIsDisplayed()
        compose.onNodeWithText("1.250,5").assertIsDisplayed()
        compose.onNodeWithText("SCU").assertIsDisplayed()
        compose.onNodeWithTag(INVENTORY_TREE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a closed group shows none of its holdings`() {
        show(InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready))

        compose.onAllNodesWithText("Rhea", substring = true).assertCountEquals(0)
    }

    @Test
    fun `tapping a group reports its material id`() {
        val toggled = mutableListOf<String>()
        show(
            InventoryState(groups = listOf(group()), total = 1, phase = InventoryPhase.Ready),
            toggled = toggled,
        )

        compose.onNodeWithText("Quantainium").performClick()

        assertEquals(listOf("m1"), toggled)
    }

    @Test
    fun `a group the server sent without an id offers no tap`() {
        // It still holds something and is therefore shown, but it cannot be asked for — and a tap
        // that does nothing is how a member concludes the app is broken.
        val toggled = mutableListOf<String>()
        show(
            InventoryState(
                groups = listOf(group(id = null, name = "Namenlos")),
                total = 1,
                phase = InventoryPhase.Ready,
            ),
            toggled = toggled,
        )

        compose.onNodeWithText("Namenlos").performClick()

        assertEquals(emptyList<String>(), toggled)
    }

    @Test
    fun `an opened group shows its holdings`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Ready(listOf(stack()))),
            ),
        )

        compose.onNodeWithText("Rhea · ARC-L1").assertIsDisplayed()
        compose.onNodeWithText("1 Eintrag").assertIsDisplayed()
    }

    @Test
    fun `a group that failed to open stays open and says so`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Failed),
            ),
        )

        compose.onNodeWithText("Die Bestände dieser Gruppe konnten nicht geladen werden.")
            .assertIsDisplayed()
    }

    @Test
    fun `an emptied group says so rather than looking unopened`() {
        show(
            InventoryState(
                groups = listOf(group()),
                total = 1,
                phase = InventoryPhase.Ready,
                opened = mapOf("m1" to StackPhase.Ready(emptyList())),
            ),
        )

        compose.onNodeWithText("In dieser Gruppe liegt nichts mehr.").assertIsDisplayed()
    }

    @Test
    fun `an empty Lager and a filtered-out page are different sentences`() {
        show(InventoryState(phase = InventoryPhase.Ready))
        compose.onNodeWithText("Leeres Lager").assertIsDisplayed()
    }

    @Test
    fun `a failed tree offers a retry`() {
        show(InventoryState(phase = InventoryPhase.Failed(ApiError.Network(IOException("x")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.CraftabilityMaterial
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the Blueprints tab renders.
 *
 * The chip carries the screen: it is the difference between a list of things a member owns and a
 * list they can act on. Its most important state is the one where it says nothing at all — while
 * the craftability read has not answered, or after it failed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class PersonalBlueprintsScreenTest {
    private companion object {
        /** One build needs this much of the limiting material, and this much is reachable. */
        const val REQUIRED_SCU = 10.0
        const val AVAILABLE_SCU = 4.0
        const val MISSING_SCU = 6.0

        /** A second material that is not short, so the count cannot pass by accident. */
        const val SPARE_REQUIRED_SCU = 2.0
        const val SPARE_AVAILABLE_SCU = 9.0

        /** Nothing is missing once refining counts. */
        const val NONE_MISSING = 0.0

        /** How many builds refining makes possible. */
        const val WITH_REFINERY = 2

        /** The row's optimistic lock. */
        const val VERSION = 3L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun entry(
        id: String = "b1",
        name: String = "F7A Hornet",
        note: String? = "vom Event",
        removable: Boolean = true,
    ) = OwnedBlueprint(
        id = id,
        productKey = "anvil.hornet",
        productName = name,
        note = note,
        acquiredAt = null,
        removable = removable,
        version = VERSION,
    )

    private fun craftability(
        craftable: Int = 0,
        withRefinery: Int = WITH_REFINERY,
        resolved: Boolean = true,
    ) = Craftability(
        blueprintId = "b1",
        recipeResolved = resolved,
        craftable = craftable,
        craftableWithRefinery = withRefinery,
        limitingMaterial = "Quantainium",
        limitingMaterialWithRefinery = null,
        materials =
            listOf(
                CraftabilityMaterial(
                    "Quantainium",
                    REQUIRED_SCU,
                    AVAILABLE_SCU,
                    MISSING_SCU,
                    NONE_MISSING,
                ),
                CraftabilityMaterial(
                    "Agricium",
                    SPARE_REQUIRED_SCU,
                    SPARE_AVAILABLE_SCU,
                    NONE_MISSING,
                    NONE_MISSING,
                ),
            ),
    )

    private fun show(
        state: BlueprintsState,
        edited: MutableList<OwnedBlueprint> = mutableListOf(),
        deleted: MutableList<OwnedBlueprint> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                PersonalBlueprintsScreen(
                    state = state,
                    onQueryChanged = {},
                    onRefineryChanged = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    onAdd = {},
                    onEdit = { edited.add(it) },
                    onDelete = { deleted.add(it) },
                    onSelect = {},
                    bulk =
                        BlueprintBulkActions(
                            onStartSelection = {},
                            onToggleSelected = {},
                            onSelectAll = {},
                            onCancelSelection = {},
                            onAskDelete = {},
                            onDismissDelete = {},
                            onConfirmDelete = {},
                            onImportOpen = {},
                            onImportFile = { _, _ -> },
                            onImportApply = {},
                            onImportDismiss = {},
                        ),
                )
            }
        }
    }

    @Test
    fun `a row names the product and its note`() {
        show(
            BlueprintsState(items = listOf(entry()), total = 1, phase = BlueprintsPhase.Ready),
        )

        compose.onNodeWithText("F7A Hornet").assertIsDisplayed()
        compose.onNodeWithText("vom Event").assertIsDisplayed()
        compose.onNodeWithTag(BLUEPRINTS_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the chip says how many materials are short`() {
        show(
            BlueprintsState(
                items = listOf(entry()),
                craftability = mapOf("b1" to craftability()),
                total = 1,
                phase = BlueprintsPhase.Ready,
            ),
        )

        compose.onNodeWithText("1 MATERIAL FEHLT").assertIsDisplayed()
    }

    @Test
    fun `with refining allowed for, the same row is buildable`() {
        show(
            BlueprintsState(
                items = listOf(entry()),
                craftability = mapOf("b1" to craftability()),
                total = 1,
                withRefinery = true,
                phase = BlueprintsPhase.Ready,
            ),
        )

        compose.onNodeWithText("BAUBAR").assertIsDisplayed()
    }

    @Test
    fun `no craftability means no chip, not a claim`() {
        // The read has not answered, or it failed. Saying "nicht baubar" would be a statement
        // about the member's stock made out of an outage.
        show(
            BlueprintsState(items = listOf(entry()), total = 1, phase = BlueprintsPhase.Ready),
        )

        compose.onAllNodesWithText("BAUBAR").assertCountEquals(0)
        compose.onAllNodesWithText("1 MATERIAL FEHLT").assertCountEquals(0)
    }

    @Test
    fun `an unresolved recipe says so rather than pretending to know`() {
        show(
            BlueprintsState(
                items = listOf(entry()),
                craftability = mapOf("b1" to craftability(resolved = false)),
                total = 1,
                phase = BlueprintsPhase.Ready,
            ),
        )

        compose.onNodeWithText("REZEPT UNBEKANNT").assertIsDisplayed()
    }

    @Test
    fun `an entry the server will not release is offered no remove action`() {
        // Showing it would produce a button that answers 409 — a rule the member cannot see,
        // rendered as a failure.
        show(
            BlueprintsState(
                items = listOf(entry(removable = false)),
                total = 1,
                phase = BlueprintsPhase.Ready,
            ),
        )

        compose.onAllNodesWithText("ENTFERNEN").assertCountEquals(0)
    }

    @Test
    fun `a removable entry is`() {
        val deleted = mutableListOf<OwnedBlueprint>()
        show(
            BlueprintsState(items = listOf(entry()), total = 1, phase = BlueprintsPhase.Ready),
            deleted = deleted,
        )

        compose.onNodeWithText("ENTFERNEN").performClick()

        assertEquals("b1", deleted.single().id)
    }

    @Test
    fun `offline, the add action is shown and disabled`() {
        show(
            BlueprintsState(items = listOf(entry()), total = 1, phase = BlueprintsPhase.Ready, online = false),
        )

        compose.onNodeWithText("Schreiben ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(BLUEPRINTS_ADD_TAG).assertIsNotEnabled()
    }

    @Test
    fun `an empty list says what belongs here`() {
        show(BlueprintsState(phase = BlueprintsPhase.Ready))

        compose.onNodeWithText("Keine Blueprints").assertIsDisplayed()
    }

    @Test
    fun `the add sheet does not offer a product the member already owns`() {
        val owned = BlueprintProduct("anvil.hornet", "F7A Hornet", "Anvil", owned = true)
        compose.setContent {
            KrtTheme {
                BlueprintAddSheet(
                    editor = BlueprintEditor.Adding(query = "hornet", results = listOf(owned)),
                    onQuery = {},
                    onChosen = {},
                    onNote = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        // Not in the list at all (design ch. 17 artboard 5), and the notice line says why — so a
        // missing hit does not read as a broken search.
        compose.onNodeWithText("F7A Hornet", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Bereits vorhandene", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(BLUEPRINTS_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a product not yet owned can be picked`() {
        val free = BlueprintProduct("anvil.hornet", "F7A Hornet", "Anvil", owned = false)
        val picked = mutableListOf<BlueprintProduct>()
        compose.setContent {
            KrtTheme {
                BlueprintAddSheet(
                    editor = BlueprintEditor.Adding(query = "hornet", results = listOf(free)),
                    onQuery = {},
                    onChosen = { picked.add(it) },
                    onNote = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("F7A Hornet · Anvil").performClick()

        assertTrue(picked.isNotEmpty())
    }

    @Test
    fun `the removal confirmation names the blueprint`() {
        compose.setContent {
            KrtTheme {
                BlueprintRemoveModal(
                    entry = entry(),
                    deleting = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("„F7A Hornet“ wird aus deiner Liste entfernt.").assertIsDisplayed()
    }
}

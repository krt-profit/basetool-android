/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocationKind
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What "Mein Inventar" renders, and what it refuses to.
 *
 * The assertion that carries the phase: offline, the write actions are visibly there and visibly
 * disabled. Hiding them would leave a member wondering where the button went; enabling them would
 * promise a save the app has decided not to make.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class PersonalInventoryScreenTest {
    private companion object {
        /** The one UEX id every fixture uses. */
        const val UEX_ID = 4711

        /** Enough result rows to push the action row past a phone's bottom edge. */
        const val RESULT_COUNT = 12
    }

    @get:Rule
    val compose = createComposeRule()

    private fun item(
        id: String = "p1",
        name: String = "Medpens",
        note: String? = "Notfallkiste",
        locationName: String? = "Lorville",
    ) = PersonalItem(
        id = id,
        name = name,
        note = note,
        quantity = 12,
        locationUexId = UEX_ID,
        locationKind = PersonalLocationKind.CITY,
        locationName = locationName,
        version = 7L,
    )

    private fun show(
        state: PersonalInventoryState,
        edited: MutableList<PersonalItem> = mutableListOf(),
        deleted: MutableList<PersonalItem> = mutableListOf(),
        created: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                PersonalInventoryScreen(
                    state = state,
                    onQueryChanged = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onCreate = { created.add(Unit) },
                    onEdit = { edited.add(it) },
                    onDelete = { deleted.add(it) },
                )
            }
        }
    }

    @Test
    fun `a row shows its name, its place and its count`() {
        show(
            PersonalInventoryState(
                items = listOf(item()),
                total = 1,
                phase = PersonalInventoryPhase.Ready,
            ),
        )

        compose.onNodeWithText("Medpens").assertIsDisplayed()
        compose.onNodeWithText("Lorville · Notfallkiste").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithTag(PERSONAL_INVENTORY_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `a place the server could not resolve reads as a dash, not as a gap`() {
        show(
            PersonalInventoryState(
                items = listOf(item(locationName = null, note = null)),
                total = 1,
                phase = PersonalInventoryPhase.Ready,
            ),
        )

        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun `offline, the write actions are shown and disabled`() {
        show(
            PersonalInventoryState(
                items = listOf(item()),
                total = 1,
                phase = PersonalInventoryPhase.Ready,
                online = false,
            ),
        )

        compose.onNodeWithText("Kein Netz — Ändern ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(PERSONAL_INVENTORY_CREATE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `online, the create action can be taken`() {
        val created = mutableListOf<Unit>()
        show(
            PersonalInventoryState(phase = PersonalInventoryPhase.Ready),
            created = created,
        )

        compose.onNodeWithTag(PERSONAL_INVENTORY_CREATE_TAG).assertIsEnabled().performClick()

        assertEquals(1, created.size)
    }

    @Test
    fun `an empty list says what belongs here, and says it is private`() {
        show(PersonalInventoryState(phase = PersonalInventoryPhase.Ready))

        compose.onNodeWithText("Nichts erfasst").assertIsDisplayed()
        compose.onNodeWithText("Hier steht, was du selbst gelagert hast. Nur für dich sichtbar.")
            .assertIsDisplayed()
    }

    @Test
    fun `a search that matched nothing says so, rather than claiming the list is empty`() {
        show(PersonalInventoryState(query = "xyz", phase = PersonalInventoryPhase.Ready))

        compose.onNodeWithText("Für diese Suche gibt es keinen Eintrag.").assertIsDisplayed()
    }

    @Test
    fun `a failed read offers a retry`() {
        show(PersonalInventoryState(phase = PersonalInventoryPhase.Failed(ApiError.Forbidden())))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `tapping a row opens that entry`() {
        val edited = mutableListOf<PersonalItem>()
        show(
            PersonalInventoryState(
                items = listOf(item(), item(id = "p2", name = "Ersatzteile")),
                total = 2,
                phase = PersonalInventoryPhase.Ready,
            ),
            edited = edited,
        )

        compose.onNodeWithText("Ersatzteile").performClick()

        assertEquals("p2", edited.single().id)
    }

    @Test
    fun `the editor names the entry being changed and offers what the API carries`() {
        compose.setContent {
            KrtTheme {
                PersonalInventoryEditor(
                    editor = EditorState.Open(editing = item(), name = "Medpens", quantity = "12"),
                    locations = LocationSearch(),
                    onName = {},
                    onQuantity = {},
                    onStep = {},
                    onNote = {},
                    onLocationQuery = {},
                    onLocationChosen = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Bezeichnung").assertIsDisplayed()
        compose.onNodeWithText("Menge").assertIsDisplayed()
        compose.onNodeWithText("Ort").assertIsDisplayed()
        compose.onNodeWithText("Nur für dich sichtbar.").assertIsDisplayed()
    }

    @Test
    fun `an editor missing its place cannot be saved`() {
        compose.setContent {
            KrtTheme {
                PersonalInventoryEditor(
                    editor = EditorState.Open(name = "Medpens", quantity = "1"),
                    locations = LocationSearch(),
                    onName = {},
                    onQuantity = {},
                    onStep = {},
                    onNote = {},
                    onLocationQuery = {},
                    onLocationChosen = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(PERSONAL_INVENTORY_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the editor scrolls, so its actions cannot be pushed off the screen`() {
        // Found on a device: with a place chosen and the keyboard up, the action row sat past the
        // bottom edge and the sheet could not be submitted at all. A fixed-height sheet is only
        // ever as tall as the shortest phone it runs on.
        compose.setContent {
            KrtTheme {
                PersonalInventoryEditor(
                    editor =
                        EditorState.Open(
                            name = "Medpens",
                            quantity = "4",
                            location =
                                PersonalLocation(
                                    UEX_ID,
                                    PersonalLocationKind.CITY,
                                    "Lorville",
                                    "Stanton",
                                    "Hurston",
                                ),
                            note = "Notfallkiste",
                        ),
                    locations =
                        LocationSearch(
                            query = "lor",
                            results =
                                List(RESULT_COUNT) {
                                    PersonalLocation(
                                        it,
                                        PersonalLocationKind.CITY,
                                        "Ort $it",
                                        "Stanton",
                                        null,
                                    )
                                },
                        ),
                    onName = {},
                    onQuantity = {},
                    onStep = {},
                    onNote = {},
                    onLocationQuery = {},
                    onLocationChosen = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PERSONAL_INVENTORY_SAVE_TAG))
        compose.onNodeWithTag(PERSONAL_INVENTORY_SAVE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a conflict is worded as one, and the typing is still there`() {
        compose.setContent {
            KrtTheme {
                PersonalInventoryEditor(
                    editor =
                        EditorState.Open(
                            editing = item(),
                            name = "Medpens, neu benannt",
                            quantity = "12",
                            location =
                                PersonalLocation(
                                    UEX_ID,
                                    PersonalLocationKind.CITY,
                                    "Lorville",
                                    null,
                                    null,
                                ),
                            error = ApiError.OptimisticLock(),
                        ),
                    locations = LocationSearch(),
                    onName = {},
                    onQuantity = {},
                    onStep = {},
                    onNote = {},
                    onLocationQuery = {},
                    onLocationChosen = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Medpens, neu benannt").assertIsDisplayed()
        compose.onAllNodesWithText(
            "Jemand anderes hat diesen Eintrag inzwischen geändert. Deine Eingabe bleibt stehen — " +
                "lade neu und speichere erneut.",
        ).assertCountEquals(1)
    }

    @Test
    fun `a capped place search says the list was cut`() {
        compose.setContent {
            KrtTheme {
                PersonalInventoryEditor(
                    editor = EditorState.Open(),
                    locations =
                        LocationSearch(
                            query = "lorville",
                            results =
                                listOf(
                                    PersonalLocation(
                                        UEX_ID,
                                        PersonalLocationKind.CITY,
                                        "Lorville",
                                        "Stanton",
                                        "Hurston",
                                    ),
                                ),
                            capped = true,
                        ),
                    onName = {},
                    onQuantity = {},
                    onStep = {},
                    onNote = {},
                    onLocationQuery = {},
                    onLocationChosen = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Lorville · Hurston · Stanton").assertIsDisplayed()
        compose.onNodeWithText("Nur der erste Treffer. Suche genauer, wenn dein Ort fehlt.")
            .assertIsDisplayed()
    }

    @Test
    fun `the delete confirmation names the entry`() {
        // "Are you sure?" without the name is a question the member cannot answer.
        compose.setContent {
            KrtTheme {
                PersonalInventoryDeleteModal(
                    item = item(),
                    deleting = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("„Medpens“ wird gelöscht. Das lässt sich nicht rückgängig machen.")
            .assertIsDisplayed()
    }
}

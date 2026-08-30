/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.MissionManager
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Verwaltung tab's „Personen" section — design ch. 06 artboard 12.
 *
 * The artboard corrected an earlier build, and these tests pin the correction: **every row shows
 * its current value first**. Three ghost buttons with noun labels said neither who the
 * Einsatzleitung was nor that pressing one would be a change, and a manager could be added and
 * never seen — which is why nobody could remove one either.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class MissionPeopleSectionTest {
    @get:Rule
    val compose = createComposeRule()

    /** The lead's name stands in the row, not behind it. */
    @Test
    fun thePartyLeadRowNamesTheLead() {
        show(partyLead = "Rhea")

        compose.onNodeWithText("Rhea").assertIsDisplayed()
        // The action is a button whose label the design system uppercases, so it is reached by its
        // handle rather than by the string the resource carries.
        compose.onAllNodesWithTag(MISSION_PARTY_LEAD_TAG).onFirst().assertIsDisplayed()
    }

    /** With nobody leading, the row says so rather than looking empty. */
    @Test
    fun anUnsetLeadSaysSo() {
        show(partyLead = null)

        compose.onNodeWithText("nicht gesetzt").assertIsDisplayed()
    }

    /** The managers are chips, so they can be seen — and therefore removed. */
    @Test
    fun theManagersAreListed() {
        show(managers = listOf(manager("u1", "Dorn"), manager("u2", "Vex")))

        compose.onNodeWithText("Dorn").assertIsDisplayed()
        compose.onNodeWithText("Vex").assertIsDisplayed()
    }

    /** Without any, the row says that too. */
    @Test
    fun noManagersSaysSo() {
        show(managers = emptyList())

        compose.onNodeWithText("Noch keine Manager.").assertIsDisplayed()
    }

    /** A chip's ✕ reports the manager it belongs to — the entry point that did not exist before. */
    @Test
    fun removingAManagerReportsWhichOne() {
        val removed = mutableListOf<MissionManager>()
        show(managers = listOf(manager("u1", "Dorn")), onRemove = { removed.add(it) })

        compose.onAllNodesWithTag(MISSION_MANAGER_REMOVE_TAG).onFirst().performClick()

        assertEquals(listOf("u1"), removed.map { it.userId })
    }

    /**
     * One manager.
     *
     * @param id their user id.
     * @param name what to show.
     * @return the manager.
     */
    private fun manager(
        id: String,
        name: String,
    ) = MissionManager(userId = id, name = name)

    /**
     * Renders the section.
     *
     * @param partyLead who leads, or `null`.
     * @param managers who manages.
     * @param onRemove what a chip's ✕ reports.
     */
    private fun show(
        partyLead: String? = "Rhea",
        managers: List<MissionManager> = emptyList(),
        onRemove: (MissionManager) -> Unit = {},
    ) {
        compose.setContent {
            KrtTheme {
                MemberSection(
                    members =
                        MissionMemberActions(
                            canManage = true,
                            enabled = true,
                            state = MissionMemberPickerState(),
                            denials = rememberDenialState(),
                            onOpen = {},
                            onQuery = {},
                            onPick = {},
                            onDismiss = {},
                            partyLeadName = partyLead,
                            managers = managers,
                            onRemoveManager = onRemove,
                        ),
                )
            }
        }
    }
}

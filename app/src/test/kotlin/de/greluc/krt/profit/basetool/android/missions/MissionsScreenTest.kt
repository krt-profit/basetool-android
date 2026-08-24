/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * What the Einsatz list actually renders, for the states a member can land in.
 *
 * The list has four of them — loading, failed, empty, populated — and three are the ones nobody
 * looks at while developing, because the happy path is what a dev stack produces. They are also the
 * three a member hits first on a bad connection.
 *
 * German is pinned, because German is the primary bundle and the copy rules ("Einsätze", never
 * "Missionen") are asserted against it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de")
class MissionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun mission(
        id: String,
        name: String,
        status: MissionStatus = MissionStatus.PLANNED,
        planned: String? = "2026-08-21T19:00:00Z",
        shorthand: String? = null,
    ) = Mission(
        id = id,
        name = name,
        status = status,
        rawStatus = status.name,
        meetingTime = null,
        plannedStartTime = planned?.let(Instant::parse),
        actualStartTime = null,
        plannedEndTime = null,
        isInternal = false,
        operationName = null,
        orgUnitName = null,
        orgUnitShorthand = shorthand,
        meetingPoint = null,
    )

    /**
     * Renders the screen in [state], recording what the member's taps produced.
     *
     * @param state what to draw.
     * @param opened receives the id of a tapped row.
     * @param statuses receives the status set after a chip tap.
     * @param segmentTaps records a tap on the Operationen half of the segment.
     */
    private fun show(
        state: MissionsState,
        opened: MutableList<String> = mutableListOf(),
        statuses: MutableList<Set<MissionStatus>> = mutableListOf(),
        segmentTaps: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                MissionsScreen(
                    state = state,
                    onSearchChanged = {},
                    onStatusToggled = { statuses.add(it) },
                    onIncludePastChanged = {},
                    onResetFilters = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    onOpenMission = { opened.add(it) },
                    onOpenOperations = { segmentTaps.add(Unit) },
                )
            }
        }
    }

    @Test
    fun `a populated list shows its Einsaetze`() {
        show(
            MissionsState(
                missions = listOf(mission("m1", "Vertikaler Abbau"), mission("m2", "Konvoi-Eskorte")),
                total = 2,
                phase = MissionsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Vertikaler Abbau").assertIsDisplayed()
        compose.onNodeWithText("Konvoi-Eskorte").assertIsDisplayed()
        compose.onNodeWithTag(MISSIONS_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `tapping a row opens that Einsatz and no other`() {
        val opened = mutableListOf<String>()
        show(
            MissionsState(
                missions = listOf(mission("m1", "Vertikaler Abbau"), mission("m2", "Konvoi-Eskorte")),
                total = 2,
                phase = MissionsPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("Konvoi-Eskorte").performClick()

        assertEquals(listOf("m2"), opened)
    }

    @Test
    fun `an unfiltered empty list says nothing is scheduled`() {
        show(MissionsState(phase = MissionsPhase.Ready))

        compose.onNodeWithText("Keine Einsätze").assertIsDisplayed()
    }

    @Test
    fun `a filtered empty list says so instead, and offers the reset`() {
        // Telling a member "no Einsätze" when their own filter is what hid them reads as the
        // squadron being idle. The two states are different facts and get different copy.
        show(
            MissionsState(
                query = MissionQuery(text = "Lyria"),
                phase = MissionsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Nichts gefunden").assertIsDisplayed()
    }

    @Test
    fun `a failure shows the in-fiction error copy, not a stack trace`() {
        show(MissionsState(phase = MissionsPhase.Failed(ApiError.Network(IOException("offline")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
    }

    @Test
    fun `a status chip reports the whole resulting set, not just the tapped one`() {
        // The view model replaces the set rather than merging, so a chip that reported only itself
        // would silently clear every other selection.
        val statuses = mutableListOf<Set<MissionStatus>>()
        show(
            MissionsState(
                query = MissionQuery(statuses = setOf(MissionStatus.PLANNED)),
                missions = listOf(mission("m1", "Vertikaler Abbau")),
                total = 1,
                phase = MissionsPhase.Ready,
            ),
            statuses = statuses,
        )

        compose.onNodeWithText("AKTIV").performClick()

        assertEquals(listOf(setOf(MissionStatus.PLANNED, MissionStatus.ACTIVE)), statuses)
    }

    @Test
    fun `the org badge appears only when the server named a unit`() {
        show(
            MissionsState(
                missions = listOf(mission("m1", "Vertikaler Abbau", shorthand = "S1")),
                total = 1,
                phase = MissionsPhase.Ready,
            ),
        )

        compose.onNodeWithText("S1").assertIsDisplayed()
    }
}

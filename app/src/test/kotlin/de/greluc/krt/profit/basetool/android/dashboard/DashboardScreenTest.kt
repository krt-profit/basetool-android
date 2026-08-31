/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Announcement
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * What the dashboard renders.
 *
 * The states worth pinning are the quiet ones: nothing announced, nothing scheduled, nothing
 * unread. All three are ordinary and all three must read as facts rather than as failures.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class DashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun mission(
        id: String,
        name: String = "Vertikaler Abbau",
    ) = Mission(
        id = id,
        name = name,
        status = MissionStatus.PLANNED,
        rawStatus = "PLANNED",
        meetingTime = null,
        plannedStartTime = null,
        actualStartTime = null,
        plannedEndTime = null,
        isInternal = false,
        operationName = null,
        orgUnitName = null,
        orgUnitShorthand = null,
        meetingPoint = null,
    )

    private fun notification(id: String) =
        Notification(
            id = id,
            type = "JOB_ORDER_CREATED",
            params = mapOf("displayId" to "1042", "orgUnit" to "Staffel 1"),
            entityType = "JOB_ORDER",
            entityId = "j1",
            read = false,
            createdAt = Instant.parse("2026-08-22T10:00:00Z"),
        )

    /**
     * Renders the dashboard.
     *
     * @param state the fetched parts.
     * @param opened receives the id of a tapped Einsatz.
     * @param taps records taps on the two band links, by label.
     */
    private fun show(
        state: DashboardState,
        opened: MutableList<String> = mutableListOf(),
        taps: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                DashboardScreen(
                    state = state,
                    memberName = "GrafRotz",
                    orgUnitName = "Bereich Profit",
                    onMarkAnnouncementRead = { taps.add("announcement-read") },
                    onRefresh = {},
                    onOpenMission = { opened.add(it) },
                    onOpenMissions = { taps.add("missions") },
                    onQuickAction = { taps.add("quick:" + it.name) },
                    onOpenInbox = { taps.add("inbox") },
                )
            }
        }
    }

    @Test
    fun `the greeting names the member and their org unit, uppercase`() {
        show(DashboardState(phase = DashboardPhase.Ready))

        // Uppercase, because artboard 1 draws it that way and the assertion is the only thing
        // standing between the design and a sentence-case greeting that nobody would call a bug.
        compose.onNodeWithText("WILLKOMMEN, GRAFROTZ").assertIsDisplayed()
        compose.onNodeWithText("Bereich Profit", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an announcement is shown, and nothing is shown when there is none`() {
        show(
            DashboardState(
                announcement = Announcement("a-1", "Flottenweite Wartung am Dienstag", null),
                phase = DashboardPhase.Ready,
            ),
        )

        compose.onNodeWithText("Flottenweite Wartung am Dienstag").assertIsDisplayed()
    }

    @Test
    fun `no announcement means no band at all, not an empty one`() {
        // A 204 is an ordinary answer. An "Information" heading over nothing would read as a
        // notice that failed to load.
        show(DashboardState(phase = DashboardPhase.Ready))

        compose.onAllNodesWithText("Information", ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `the Einsatz band lists what is coming and opens a row`() {
        val opened = mutableListOf<String>()
        show(
            DashboardState(
                missions = listOf(mission("m1"), mission("m2", "Konvoi-Eskorte")),
                phase = DashboardPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("Einsätze der nächsten 7 Tage", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Konvoi-Eskorte").performClick()

        assertEquals(listOf("m2"), opened)
    }

    @Test
    fun `an empty band says nothing is scheduled`() {
        show(DashboardState(phase = DashboardPhase.Ready))

        compose.onNodeWithText("In den nächsten 7 Tagen ist nichts geplant.").assertIsDisplayed()
    }

    @Test
    fun `a failed band says so instead of claiming the week is empty`() {
        // "Nothing is scheduled" and "the app could not ask" are different facts, and the second
        // told as the first would have a member skip an Einsatz.
        show(DashboardState(phase = DashboardPhase.Failed))

        compose.onNodeWithText("Die Einsätze konnten nicht geladen werden.").assertIsDisplayed()
    }

    /**
     * The dashboard says nothing about unread notifications — withdrawn 2026-08-31.
     *
     * It previewed the three newest and stated „Nichts Ungelesenes." when there were none. The bell
     * in the top bar carries the same count on every screen, so this was a second place saying what
     * one place already says. Pinned as an absence, because a preview is exactly the kind of band
     * that gets added back by someone reading the artboard rather than this note.
     */
    @Test
    fun `the dashboard no longer previews unread notifications`() {
        show(DashboardState(phase = DashboardPhase.Ready))

        compose.onAllNodesWithText("Nichts Ungelesenes.").assertCountEquals(0)
        compose.onAllNodesWithText("UNGELESEN", ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Alle ansehen", ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `the Einsatz band's link leads somewhere`() {
        val taps = mutableListOf<String>()
        show(
            DashboardState(missions = listOf(mission("m1")), phase = DashboardPhase.Ready),
            taps = taps,
        )

        compose.onNodeWithText("Alle Einsätze", ignoreCase = true).performClick()

        assertEquals(listOf("missions"), taps)
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.MissionCrewMember
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinanceEntry
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionFrequency
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionObjective
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.data.MissionStep
import de.greluc.krt.profit.basetool.android.core.data.MissionUnit
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * The Einsatz detail screen's **manager** half.
 *
 * Split from `MissionDetailScreenTest` when that class passed detekt's LargeClass threshold. The
 * line is not arbitrary: everything here needs the server's `canEdit` to be true, and everything
 * there is what a plain member sees.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class MissionManagerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { MissionScreenRobot(compose) }

    /**
     * The Funktions-Select, reached through the row's ⋮ sheet, renders for a manager with live chips.
     *
     * The test hands in the catalogue, which is loaded only for someone who may assign.
     */
    @Test
    fun `a manager can assign a Funktion from the roster`() {
        val taps = mutableListOf<String>()
        robot.show(
            robot.ready(
                detail = robot.detail(participants = listOf(robot.rosterRow())),
                tab = MissionTab.PARTICIPANTS,
            ),
            rosterTaps = taps,
            canManage = true,
            jobTypes = listOf(MissionJobType("j2", "Turret")),
        )

        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktion und Anteil", ignoreCase = true).performClick()
        compose.onNodeWithText("Turret", ignoreCase = true).performClick()

        assertEquals(listOf("function:p2:j2"), taps)
    }

    /** And the drawn per-row check-in reaches the manager's action, not the caller's own. */
    @Test
    fun `a manager checks another member in from their row`() {
        val taps = mutableListOf<String>()
        robot.show(
            robot.ready(
                detail = robot.detail(participants = listOf(robot.rosterRow())),
                tab = MissionTab.PARTICIPANTS,
            ),
            rosterTaps = taps,
            canManage = true,
        )

        compose.onNodeWithContentDescription("Einchecken", ignoreCase = true).performClick()

        assertEquals(listOf("check-in:p2"), taps)
    }

    /**
     * Without the Missions-Manager role the Funktions-Select still takes the tap and answers with a toast naming the
     * role instead of a write.
     */
    @Test
    fun `a member without the role is refused in place rather than shown nothing`() {
        val taps = mutableListOf<String>()
        robot.show(
            robot.ready(
                detail = robot.detail(participants = listOf(robot.rosterRow())),
                tab = MissionTab.PARTICIPANTS,
            ),
            rosterTaps = taps,
            canManage = false,
        )

        compose.onNodeWithContentDescription("Einchecken", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Einchecken", ignoreCase = true).performClick()

        assertTrue("a locked control must not write", taps.isEmpty())
        compose.onNodeWithText("Missions-Manager", substring = true).assertIsDisplayed()
    }

    /** „Wunsch: …" is drawn beside the assignment, and only when it says something new. */
    @Test
    fun `the roster shows what a member asked to fly`() {
        robot.show(
            robot.ready(
                detail = robot.detail(participants = listOf(robot.rosterRow())),
                tab = MissionTab.PARTICIPANTS,
            ),
        )

        compose.onNodeWithText("Wunsch: Pilot", substring = true).assertIsDisplayed()
    }
}

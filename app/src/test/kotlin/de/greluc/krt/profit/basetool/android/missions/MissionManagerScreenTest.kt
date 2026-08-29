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
     * The Funktions-Select renders for a manager and its chips are live.
     *
     * The catalogue is only loaded for someone who may assign, so an empty one is what a plain
     * member gets — which is why this test hands one in rather than relying on the screen to ask.
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

        compose.onNodeWithText("Einchecken", ignoreCase = true).performClick()

        assertEquals(listOf("check-in:p2"), taps)
    }

    /**
     * „Ohne Missions-Manager-Rolle rendert das Funktions-Select gesperrt — antippbar, der Toast
     * nennt die Rolle." So the tap must still land, and it must produce the refusal rather than a
     * write. `enabled = false` was the rejected alternative: a control that cannot be tapped cannot
     * say why it is dim.
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

        compose.onNodeWithText("Einchecken", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Einchecken", ignoreCase = true).performClick()

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

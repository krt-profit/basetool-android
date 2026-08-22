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
import de.greluc.krt.profit.basetool.android.core.data.MissionCrewMember
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionFrequency
import de.greluc.krt.profit.basetool.android.core.data.MissionObjective
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.data.MissionStep
import de.greluc.krt.profit.basetool.android.core.data.MissionUnit
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * What the seven tabs actually render, and how the screen words the answers it did not want.
 *
 * The failure states carry most of the weight. "Refused", "gone" and "broken" are three different
 * facts, and the one generic message that covers all three tells a member to try again on an
 * Einsatz they will never be allowed to see.
 *
 * German is pinned: it is the primary bundle and the copy rules are asserted against it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de")
class MissionDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun detail(
        description: String? = "Quantainium-Abbau an der Lyria-Südwand.",
        participants: List<MissionParticipant> = emptyList(),
        units: List<MissionUnit> = emptyList(),
        steps: List<MissionStep> = emptyList(),
        objectives: List<MissionObjective> = emptyList(),
        frequencies: List<MissionFrequency> = emptyList(),
    ) = MissionDetail(
        id = "m1",
        name = "Vertikaler Abbau",
        description = description,
        status = MissionStatus.PLANNED,
        rawStatus = "PLANNED",
        meetingTime = null,
        plannedStartTime = null,
        actualStartTime = null,
        plannedEndTime = null,
        isInternal = false,
        meetingPoint = "ARC-L1",
        operationName = null,
        orgUnitName = null,
        orgUnitShorthand = "S1",
        partyLeadName = "Rhea",
        registeredParticipants = 14,
        checkedInParticipants = 9,
        participants = participants,
        units = units,
        steps = steps,
        objectives = objectives,
        frequencies = frequencies,
    )

    /**
     * Renders the screen, recording tab changes.
     *
     * @param state what to draw.
     * @param tabs receives every tab the member picked.
     */
    private fun show(
        state: MissionDetailState,
        tabs: MutableList<MissionTab> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                MissionDetailScreen(
                    state = state,
                    onTabSelected = { tabs.add(it) },
                    onRefresh = {},
                    onRetryFinances = {},
                )
            }
        }
    }

    private fun ready(
        detail: MissionDetail = detail(),
        tab: MissionTab = MissionTab.OVERVIEW,
        finances: MissionFinancesPhase = MissionFinancesPhase.Idle,
    ) = MissionDetailState(
        missionId = "m1",
        detail = detail,
        phase = MissionDetailPhase.Ready,
        tab = tab,
        finances = finances,
    )

    @Test
    fun `the head names the Einsatz and states its sign-ups`() {
        show(ready())

        compose.onNodeWithText("Vertikaler Abbau").assertIsDisplayed()
        compose.onNodeWithText("14 angemeldet, davon 9 eingecheckt").assertIsDisplayed()
        compose.onNodeWithText("S1").assertIsDisplayed()
        compose.onNodeWithTag(MISSION_DETAIL_TABS_TAG).assertIsDisplayed()
    }

    @Test
    fun `a tap on a tab reports which one`() {
        val tabs = mutableListOf<MissionTab>()
        show(ready(), tabs = tabs)

        compose.onNodeWithText("TEILNEHMER").performClick()

        assertEquals(listOf(MissionTab.PARTICIPANTS), tabs)
    }

    @Test
    fun `a redacted Einsatz says the description is members-only rather than showing a blank`() {
        // An outsider read carries no description (main repo ADR-0034). A blank section reads as
        // an Einsatz nobody bothered to describe, which is a different and wrong statement.
        show(ready(detail = detail(description = null)))

        compose.onNodeWithText("Die Beschreibung ist nur für Mitglieder sichtbar.").assertIsDisplayed()
    }

    @Test
    fun `the roster marks who has checked in`() {
        show(
            ready(
                detail =
                    detail(
                        participants =
                            listOf(
                                // Deliberately not "Rhea": she is the party lead in the head, so
                                // the name would match two nodes and the assertion would say
                                // nothing about the roster.
                                MissionParticipant("p1", "Kestrel", "Pilot", checkedIn = true, comment = null),
                                MissionParticipant("p2", "Dorn", null, checkedIn = false, comment = null),
                            ),
                    ),
                tab = MissionTab.PARTICIPANTS,
            ),
        )

        compose.onNodeWithText("Kestrel").assertIsDisplayed()
        compose.onNodeWithText("EINGECHECKT").assertIsDisplayed()
        compose.onNodeWithText("NICHT EINGECHECKT").assertIsDisplayed()
    }

    @Test
    fun `a unit shows its ship, its HVU mark and its crew`() {
        show(
            ready(
                detail =
                    detail(
                        units =
                            listOf(
                                MissionUnit(
                                    id = "u1",
                                    name = "Einheit Alpha",
                                    shipName = "Carrack Meridian",
                                    highValue = true,
                                    responsibleName = "Rhea",
                                    crew = listOf(MissionCrewMember("c1", "Dorn", listOf("Turret"))),
                                ),
                            ),
                    ),
                tab = MissionTab.UNITS,
            ),
        )

        compose.onNodeWithText("Einheit Alpha").assertIsDisplayed()
        compose.onNodeWithText("Carrack Meridian").assertIsDisplayed()
        compose.onNodeWithText("HVU").assertIsDisplayed()
        compose.onNodeWithText("Dorn — Turret").assertIsDisplayed()
    }

    @Test
    fun `an empty tab says so instead of showing nothing at all`() {
        // A blank tab is indistinguishable from a rendering fault; a sentence is not.
        show(ready(tab = MissionTab.STEPS))

        compose.onNodeWithText("Kein Ablauf hinterlegt.").assertIsDisplayed()
    }

    @Test
    fun `an objective kind this build does not know is shown verbatim`() {
        show(
            ready(
                detail = detail(objectives = listOf(MissionObjective("o1", "500 SCU", "STRETCH_GOAL"))),
                tab = MissionTab.OBJECTIVES,
            ),
        )

        compose.onNodeWithText("STRETCH_GOAL").assertIsDisplayed()
    }

    @Test
    fun `the Finanzen totals band renders its three sums`() {
        show(
            ready(
                tab = MissionTab.FINANCES,
                finances =
                    MissionFinancesPhase.Ready(
                        MissionFinances(
                            total = "74700.0000",
                            incomeSum = "86400.0000",
                            incomeCount = 3,
                            expenseSum = "11700.0000",
                            expenseCount = 2,
                            entries = emptyList(),
                            totalEntries = 0,
                        ),
                    ),
            ),
        )

        // Grouped and signed, not the raw `86400.0000` the wire carries. The first version showed
        // exactly that, and a device run is what caught it.
        compose.onNodeWithText("+86.400").assertIsDisplayed()
        compose.onNodeWithText("−11.700").assertIsDisplayed()
        compose.onNodeWithText("74.700").assertIsDisplayed()
    }

    @Test
    fun `a refused Finanzen tab says so in its own words, and offers no retry`() {
        // Retrying a permission the member does not have is advice that cannot help.
        show(ready(tab = MissionTab.FINANCES, finances = MissionFinancesPhase.Failed(ApiError.Forbidden())))

        compose.onNodeWithText("Die Finanzen dieses Einsatzes sind für dich nicht einsehbar.").assertIsDisplayed()
    }

    @Test
    fun `a refused Einsatz reads Access Denied, not Signal Lost`() {
        show(
            MissionDetailState(
                missionId = "m1",
                phase = MissionDetailPhase.Failed(ApiError.Forbidden()),
            ),
        )

        compose.onNodeWithText("Access Denied").assertIsDisplayed()
    }

    @Test
    fun `a missing Einsatz reads Signal Lost`() {
        show(
            MissionDetailState(
                missionId = "m1",
                phase = MissionDetailPhase.Failed(ApiError.NotFound()),
            ),
        )

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
    }

    @Test
    fun `any other failure reads System Malfunction`() {
        show(
            MissionDetailState(
                missionId = "m1",
                phase = MissionDetailPhase.Failed(ApiError.Network(IOException("offline"))),
            ),
        )

        compose.onNodeWithText("System Malfunction").assertIsDisplayed()
    }
}

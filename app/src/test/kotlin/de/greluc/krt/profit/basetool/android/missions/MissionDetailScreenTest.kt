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
 * What the seven tabs actually render, and how the screen words the answers it did not want.
 *
 * The failure states carry most of the weight. "Refused", "gone" and "broken" are three different
 * facts, and the one generic message that covers all three tells a member to try again on an
 * Einsatz they will never be allowed to see.
 *
 * German is pinned: it is the primary bundle and the copy rules are asserted against it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class MissionDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { MissionScreenRobot(compose) }

    /**
     * The Verwaltung tab is **drawn for everybody** and locked for a non-manager.
     *
     * This asserted the opposite until 2026-08-29. The app hid the tab, on the argument that a
     * member who does not run this Einsatz is not one grant away from running it; the designer
     * rejected that (ch. 06 artboard 6) and the rule stands as it always did — this organisation
     * grants roles by hand, and **a function nobody sees is never requested**.
     */
    @Test
    fun `the Verwaltung tab is drawn for a manager`() {
        robot.show(robot.ready(detail = robot.detail(canManage = true)))

        compose.onNodeWithText("Verwaltung", ignoreCase = true).assertExists()
    }

    @Test
    fun `a member who may not manage still sees the Verwaltung tab`() {
        robot.show(robot.ready(detail = robot.detail(canManage = false)))

        compose.onNodeWithText("Verwaltung", ignoreCase = true).assertExists()
    }

    /**
     * And tapping it does **not** open it: it raises the role toast and the active tab stays.
     *
     * The gate lives on the tab row rather than only inside the tab, because a tab that opened and
     * then refused every control inside it would be a worse answer than a tab that says why up
     * front.
     */
    @Test
    fun `tapping the locked Verwaltung tab refuses instead of opening it`() {
        val tabs = mutableListOf<MissionTab>()
        robot.show(robot.ready(detail = robot.detail(canManage = false)), tabs = tabs)

        compose.onNodeWithText("Verwaltung", ignoreCase = true).performClick()

        assertTrue("the tab must not open", tabs.isEmpty())
        compose.onNodeWithText("Missions-Manager", substring = true).assertExists()
    }

    /**
     * The tab indices are into the VISIBLE list. Handing the tab row an enum ordinal while it draws
     * a shorter list selects the wrong tab for every non-manager — tapping „Finanzen" would report
     * whatever sits one place further along.
     */
    @Test
    fun `a non-manager's tab taps still name the tab they tapped`() {
        val tabs = mutableListOf<MissionTab>()
        robot.show(robot.ready(detail = robot.detail(canManage = false)), tabs = tabs)

        compose.onNodeWithText("Finanzen", ignoreCase = true).performClick()

        assertEquals(listOf(MissionTab.FINANCES), tabs)
    }

    /** And the Verwaltung tab draws the form rather than a sheet over the screen. */
    @Test
    fun `the Verwaltung tab draws the three sections`() {
        robot.show(
            robot.ready(
                detail = robot.detail(canManage = true),
                tab = MissionTab.ADMIN,
                adminForm = MissionAdminForm(name = "Vertikaler Abbau"),
            ),
        )

        compose.onNodeWithTag(MISSION_ADMIN_SHEET_TAG).assertExists()
    }

    @Test
    fun `the head names the Einsatz and states its sign-ups`() {
        robot.show(robot.ready())

        // The name, its status and the org badge live in the TOP BAR now (design ch. 06
        // artboard 2), which this harness does not render — the screen publishes them through
        // ProvideScreenTopBar. What the screen itself draws is the facts bar, the attendance
        // block and the tabs, and those are what this asserts.
        compose.onNodeWithText("14").assertIsDisplayed()
        compose.onNodeWithText("ANGEMELDET").assertIsDisplayed()
        compose.onNodeWithText("davon 9 eingecheckt").assertIsDisplayed()
        // "ARC-L1" is in the facts bar AND in the briefing card, which is the point of both —
        // take the first rather than asserting a uniqueness the design does not have.
        compose.onAllNodesWithText("ARC-L1", substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithTag(MISSION_DETAIL_TABS_TAG).assertIsDisplayed()
    }

    @Test
    fun `a tap on a tab reports which one`() {
        val tabs = mutableListOf<MissionTab>()
        robot.show(robot.ready(), tabs = tabs)

        // The tab now carries its count (design ch. 06), so the label is a prefix rather than the
        // whole node text.
        compose.onNodeWithText("TEILNEHMER", substring = true).performClick()

        assertEquals(listOf(MissionTab.PARTICIPANTS), tabs)
    }

    @Test
    fun `a redacted Einsatz says the description is members-only rather than showing a blank`() {
        // An outsider read carries no description (main repo ADR-0034). A blank section reads as
        // an Einsatz nobody bothered to describe, which is a different and wrong statement.
        robot.show(robot.ready(detail = robot.detail(description = null)))

        // Below the attendance block and the briefing card now, so it may sit off-screen in the
        // test's viewport: assert it EXISTS rather than that it happens to be visible.
        // The Übersicht is a LazyColumn and the description now sits under the attendance block
        // and the briefing card, so it is not composed until it is scrolled to. Scrolling is what
        // a member does; asserting without it would only be testing the viewport height.
        compose
            .onNodeWithTag(MISSION_DETAIL_CONTENT_TAG)
            .performScrollToNode(hasText("Die Beschreibung ist nur für Mitglieder sichtbar."))
        compose.onNodeWithText("Die Beschreibung ist nur für Mitglieder sichtbar.").assertIsDisplayed()
    }

    @Test
    fun `the roster marks who has checked in`() {
        robot.show(
            robot.ready(
                detail =
                    robot.detail(
                        participants =
                            listOf(
                                // Deliberately not "Rhea": she is the party lead in the head, so
                                // the name would match two nodes and the assertion would say
                                // nothing about the roster.
                                MissionParticipant(
                                    "p1",
                                    "u1",
                                    "Kestrel",
                                    "Pilot",
                                    checkedIn = true,
                                    comment = null,
                                    donating = null,
                                ),
                                MissionParticipant(
                                    "p2",
                                    "u2",
                                    "Dorn",
                                    null,
                                    checkedIn = false,
                                    comment = null,
                                    donating = null,
                                ),
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
        robot.show(
            robot.ready(
                detail =
                    robot.detail(
                        units =
                            listOf(
                                MissionUnit(
                                    id = "u1",
                                    name = "Einheit Alpha",
                                    shipName = "Carrack Meridian",
                                    highValue = true,
                                    responsibleName = "Rhea",
                                    crew =
                                        listOf(
                                            MissionCrewMember(
                                                id = "c1",
                                                name = "Dorn",
                                                roles = listOf("Turret"),
                                                roleIds = listOf("j-turret"),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                tab = MissionTab.UNITS,
            ),
        )

        compose.onNodeWithText("Einheit Alpha").assertIsDisplayed()
        compose.onNodeWithText("Carrack Meridian").assertIsDisplayed()
        compose.onNodeWithText("HVU").assertIsDisplayed()
        // The sign-up band above the tabs costs a row of height, so the crew line can sit below
        // the fold on a compact screen. That it is drawn is the assertion.
        //
        // The name and the roles are two nodes now, not one joined string: the roles became the
        // Funktions-Auswahl, so for a manager the chips ARE the reading of them. Without a CREW
        // catalogue in hand — which is what a plain member has — the sentence stands in for them.
        compose.onNodeWithText("Dorn").assertExists()
        compose.onNodeWithText("Keine CREW-Funktionen hinterlegt.").assertExists()
    }

    @Test
    fun `an empty tab says so instead of showing nothing at all`() {
        // A blank tab is indistinguishable from a rendering fault; a sentence is not.
        robot.show(robot.ready(tab = MissionTab.STEPS))

        compose.onNodeWithText("Kein Ablauf hinterlegt.").assertIsDisplayed()
    }

    @Test
    fun `an objective kind this build does not know is shown verbatim`() {
        robot.show(
            robot.ready(
                detail = robot.detail(objectives = listOf(MissionObjective("o1", "500 SCU", "STRETCH_GOAL"))),
                tab = MissionTab.OBJECTIVES,
            ),
        )

        compose.onNodeWithText("STRETCH_GOAL").assertIsDisplayed()
    }

    @Test
    fun `the Finanzen totals band renders its three sums`() {
        robot.show(
            robot.ready(
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
        robot.show(robot.ready(tab = MissionTab.FINANCES, finances = MissionFinancesPhase.Failed(ApiError.Forbidden())))

        compose.onNodeWithText("Die Finanzen dieses Einsatzes sind für dich nicht einsehbar.").assertIsDisplayed()
    }

    @Test
    fun `a refused Einsatz reads Access Denied, not Signal Lost`() {
        robot.show(
            MissionDetailState(
                missionId = "m1",
                phase = MissionDetailPhase.Failed(ApiError.Forbidden()),
            ),
        )

        compose.onNodeWithText("Access Denied").assertIsDisplayed()
    }

    @Test
    fun `a missing Einsatz reads Signal Lost`() {
        robot.show(
            MissionDetailState(
                missionId = "m1",
                phase = MissionDetailPhase.Failed(ApiError.NotFound()),
            ),
        )

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
    }

    @Test
    fun `any other failure reads System Malfunction`() {
        robot.show(
            MissionDetailState(
                missionId = "m1",
                phase = MissionDetailPhase.Failed(ApiError.Network(IOException("offline"))),
            ),
        )

        compose.onNodeWithText("System Malfunction").assertIsDisplayed()
    }

    @Test
    fun `an Einsatz the caller is not on offers to sign up, and nothing else`() {
        // Check-in and the payout preference act on a row. Offering them before there is one
        // would be offering a 404.
        val signed = mutableListOf<Unit>()
        robot.show(readyForMe(), signUps = signed)

        compose.onNodeWithTag(MISSION_SIGN_UP_TAG).assertIsEnabled().performClick()
        compose.onAllNodesWithTag(MISSION_CHECK_IN_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(MISSION_PAYOUT_TAG).assertCountEquals(0)

        assertEquals(1, signed.size)
    }

    @Test
    fun `a signed-up caller is offered the withdrawal, the check-in and the preference`() {
        val checked = mutableListOf<Unit>()
        val paid = mutableListOf<Unit>()
        robot.show(readyForMe(mine()), checkIns = checked, payouts = paid)

        compose.onNodeWithText("Abmelden", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag(MISSION_CHECK_IN_TAG).performClick()
        // The tag now sits on the radio PAIR, so the click goes to the option the caller is not in
        // — choosing the state they already hold reports nothing, which is the point of a radio.
        compose.onNodeWithText("Org-Kasse", ignoreCase = true).performClick()

        assertEquals(1, checked.size)
        assertEquals(1, paid.size)
    }

    @Test
    fun `a checked-in caller is offered the way back out`() {
        robot.show(readyForMe(mine(checkedIn = true)))

        compose.onNodeWithText("Auschecken", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a donating caller is offered the payout instead`() {
        robot.show(readyForMe(mine(donating = true)))

        // Both standing states are on screen as radios (ch. 02 §6), and the one the caller is in is
        // the one that reads as chosen — a toggle labelled with the other state left that ambiguous.
        compose.onNodeWithText("Org-Kasse", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Auszahlung", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a refusal on this Einsatz is said in the app's own words`() {
        robot.show(readyForMe(mine()).copy(error = ApiError.Forbidden()))

        compose.onNodeWithText("Für diesen Einsatz fehlt dir die Berechtigung.").assertIsDisplayed()
    }

    @Test
    fun `offline the Einsatz says so and offers no write`() {
        robot.show(readyForMe().copy(online = false))

        compose.onNodeWithText("Kein Netz — Ändern ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(MISSION_SIGN_UP_TAG).assertIsNotEnabled()
    }

    /**
     * The caller's own sign-up.
     *
     * @param checkedIn whether it is checked in.
     * @param donating whether the share is donated.
     * @return the row.
     */
    private fun mine(
        checkedIn: Boolean = false,
        donating: Boolean? = null,
    ) = MissionParticipant(
        id = "p1",
        userId = "u1",
        name = "Rhea",
        role = null,
        checkedIn = checkedIn,
        comment = null,
        donating = donating,
    )

    /**
     * A loaded Einsatz with the caller known.
     *
     * @param roster who is signed up.
     * @return the state.
     */
    private fun readyForMe(vararg roster: MissionParticipant) =
        MissionDetailState(
            missionId = "m1",
            detail = robot.detail(participants = roster.toList()),
            phase = MissionDetailPhase.Ready,
            me = Identity("u1", logistician = false),
        )

    @Test
    fun `an Einsatz that has not started offers no check-in, and says why`() {
        robot.show(
            MissionDetailState(
                missionId = "m1",
                detail = robot.detail(started = false, participants = listOf(mine())),
                phase = MissionDetailPhase.Ready,
                me = Identity("u1", logistician = false),
            ),
        )

        compose.onAllNodesWithTag(MISSION_CHECK_IN_TAG).assertCountEquals(0)
        compose.onNodeWithText("Einchecken geht, sobald der Einsatz gestartet ist.")
            .assertIsDisplayed()
    }

    @Test
    fun `the Finanzen tab offers a booking once the caller has signed up`() {
        val actions = mutableListOf<String>()
        robot.show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(robot.finances()),
            ),
            bookings = actions,
        )

        compose.onNodeWithTag(MISSION_FINANCE_ADD_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf("add"), actions)
    }

    @Test
    fun `without a sign-up the tab says why it cannot book`() {
        robot.show(
            readyForMe().copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(robot.finances()),
            ),
        )

        compose.onAllNodesWithTag(MISSION_FINANCE_ADD_TAG).assertCountEquals(0)
        compose.onNodeWithText("Buchen geht, sobald du für den Einsatz angemeldet bist.")
            .assertExists()
    }

    @Test
    fun `the caller's own booking offers a change and a delete`() {
        robot.show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(robot.finances(robot.entry(participantId = "p1"))),
            ),
        )

        // The rows sit below the fold of a lazy list, so they are not composed until it is
        // scrolled to them.
        compose.onNodeWithTag(MISSION_DETAIL_CONTENT_TAG)
            .performScrollToNode(hasTestTag(MISSION_FINANCE_EDIT_TAG))
        compose.onNodeWithTag(MISSION_FINANCE_EDIT_TAG).assertIsEnabled()
        compose.onNodeWithTag(MISSION_FINANCE_DELETE_TAG).assertIsEnabled()
    }

    @Test
    fun `somebody else's booking offers neither`() {
        // The server refuses an edit by anyone but the owner or an admin. Offering it anyway is
        // offering a refusal.
        robot.show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(robot.finances(robot.entry(participantId = "p9"))),
            ),
        )

        compose.onNodeWithTag(MISSION_DETAIL_CONTENT_TAG).performScrollToNode(hasText("Erlös"))
        compose.onNodeWithText("Erlös").assertIsDisplayed()
        compose.onAllNodesWithTag(MISSION_FINANCE_EDIT_TAG).assertCountEquals(0)
    }

    @Test
    fun `the booking form opens on what the entry holds`() {
        robot.show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(robot.finances()),
                entryDraft = FinanceEntryDraft(entryId = "e1", income = false, amount = "2500"),
            ),
        )

        compose.onNodeWithTag(MISSION_FINANCE_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("2500").performScrollTo().assertIsDisplayed()
    }
}

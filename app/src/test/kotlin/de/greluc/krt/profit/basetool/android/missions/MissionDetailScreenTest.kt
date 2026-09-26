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
import androidx.compose.ui.test.hasAnyAncestor
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
 * Tests what the seven tabs render and how the screen words a refused, a gone and a broken load differently.
 *
 * German is pinned as the primary bundle the copy rules are asserted against.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class MissionDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { MissionScreenRobot(compose) }

    /**
     * The Verwaltung tab is drawn for everybody and locked for a non-manager (design ch. 06, artboard 6).
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

        compose.onNodeWithText("14").assertIsDisplayed()
        compose.onNodeWithText("ANGEMELDET").assertIsDisplayed()
        compose.onNodeWithText("davon 9 eingecheckt").assertIsDisplayed()
        compose.onAllNodesWithText("ARC-L1", substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithTag(MISSION_DETAIL_TABS_TAG).assertIsDisplayed()
    }

    @Test
    fun `a tap on a tab reports which one`() {
        val tabs = mutableListOf<MissionTab>()
        robot.show(robot.ready(), tabs = tabs)

        compose.onNodeWithText("TEILNEHMER", substring = true).performClick()

        assertEquals(listOf(MissionTab.PARTICIPANTS), tabs)
    }

    @Test
    fun `a redacted Einsatz says the description is members-only rather than showing a blank`() {
        robot.show(robot.ready(detail = robot.detail(description = null)))

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
        compose.onNodeWithContentDescription("Eingecheckt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Nicht eingecheckt").assertIsDisplayed()
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

        compose.onNodeWithText("EINHEIT ALPHA").assertIsDisplayed()
        compose.onNodeWithText("Carrack Meridian").assertIsDisplayed()
        compose.onNodeWithText("HVU").assertIsDisplayed()
        compose.onNodeWithText("Dorn").assertExists()
        compose.onNodeWithText("Turret", ignoreCase = true).assertExists()
        compose.onAllNodesWithText("Keine CREW-Funktionen hinterlegt.").assertCountEquals(0)
    }

    @Test
    fun `an empty tab says so instead of showing nothing at all`() {
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

        compose.onNodeWithText("+86.400").assertIsDisplayed()
        compose.onNodeWithText("−11.700").assertIsDisplayed()
        compose.onNodeWithText("74.700").assertIsDisplayed()
    }

    @Test
    fun `a refused Finanzen tab says so in its own words, and offers no retry`() {
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
        val signed = mutableListOf<Unit>()
        robot.show(readyForMe(), signUps = signed)

        compose.onNodeWithTag(MISSION_SIGN_UP_TAG).assertIsEnabled().performClick()
        compose.onAllNodesWithTag(MISSION_CHECK_IN_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(MISSION_PAYOUT_TAG).assertCountEquals(0)

        assertEquals(1, signed.size)
    }

    @Test
    fun `a signed-up caller is offered the withdrawal and the check-in`() {
        val checked = mutableListOf<Unit>()
        robot.show(readyForMe(mine()), checkIns = checked)

        compose.onNodeWithText("Abmelden", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag(MISSION_CHECK_IN_TAG).performClick()

        assertEquals(1, checked.size)
    }

    @Test
    fun `a checked-in caller is offered the way back out`() {
        robot.show(readyForMe(mine(checkedIn = true)))

        compose.onNodeWithText("Auschecken", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a donating caller changes their payout in the sheet behind their row's menu`() {
        val paid = mutableListOf<Unit>()
        robot.show(readyForMe(mine(donating = true)).copy(tab = MissionTab.PARTICIPANTS), payouts = paid)

        compose.onAllNodesWithTag(MISSION_PAYOUT_TAG).assertCountEquals(0)
        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktion und Anteil", ignoreCase = true).performClick()

        compose.onNodeWithTag(MISSION_PAYOUT_TAG).assertIsDisplayed()
        compose.onNode(inPayoutSheet("Org-Kasse")).assertIsDisplayed()
        compose.onNode(inPayoutSheet("Auszahlung")).performClick()

        assertEquals(1, paid.size)
    }

    @Test
    fun `a refusal on this Einsatz is said in the app's own words`() {
        robot.show(readyForMe(mine()).copy(error = ApiError.Forbidden()))

        compose.onNodeWithText("Für diesen Einsatz fehlt dir die Berechtigung.").assertIsDisplayed()
    }

    @Test
    fun `offline the Einsatz says so and offers no write`() {
        robot.show(readyForMe().copy(online = false))

        compose.onNodeWithText("Schreiben ist gesperrt, bis die Verbindung zurück ist.")
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
    @Test
    fun `a roster row shows the Funktion that was chosen, not the catalogue it came from`() {
        robot.show(
            readyForMe(assigned()).copy(tab = MissionTab.PARTICIPANTS),
            canManage = true,
            jobTypes = catalogue(),
        )

        compose.onNodeWithText("Turret", ignoreCase = true).assertIsDisplayed()
        compose.onAllNodesWithText("Pilot", ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Cargo", ignoreCase = true).assertCountEquals(0)

        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktion und Anteil", ignoreCase = true).performClick()

        compose.onNodeWithText("Pilot", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Cargo", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the caller changes the Funktion they wish for from their own row's sheet`() {
        val taps = mutableListOf<String>()
        robot.show(
            readyForMe(mine()).copy(tab = MissionTab.PARTICIPANTS),
            rosterTaps = taps,
            jobTypes = catalogue(),
        )

        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktion und Anteil", ignoreCase = true).performClick()
        compose.onAllNodesWithText("Pilot", ignoreCase = true)[0].performClick()

        assertEquals(listOf("wish:j1"), taps)
    }

    @Test
    fun `an Einheit's crew row shows the Funktionen held, and the catalogue in its sheet`() {
        robot.show(
            readyForMe().copy(detail = robot.detail(units = listOf(alpha())), tab = MissionTab.UNITS),
            canManage = true,
            crewJobTypes = catalogue(),
        )

        compose.onNodeWithText("Turret", ignoreCase = true).assertIsDisplayed()
        compose.onAllNodesWithText("Pilot", ignoreCase = true).assertCountEquals(0)

        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktionen an Bord (Crew)", ignoreCase = true).performClick()

        compose.onNodeWithTag(MISSION_CREW_ROLE_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Pilot", ignoreCase = true).assertIsDisplayed()
    }

    /**
     * The roster sheet's last section can be scrolled to; `performScrollTo` fails without a scrollable ancestor.
     */
    @Test
    fun `the roster sheet's last section can be scrolled to`() {
        robot.show(
            readyForMe(mine()).copy(tab = MissionTab.PARTICIPANTS),
            canManage = true,
            jobTypes = longCatalogue(),
        )

        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktion und Anteil", ignoreCase = true).performClick()

        compose.onAllNodesWithText("Marine 4", ignoreCase = true)[1].performScrollTo().assertIsDisplayed()
    }

    /**
     * The crew row's sheet can likewise be scrolled to its catalogue.
     */
    @Test
    fun `the crew sheet's catalogue can be scrolled to`() {
        robot.show(
            readyForMe().copy(detail = robot.detail(units = listOf(alpha())), tab = MissionTab.UNITS),
            canManage = true,
            crewJobTypes = longCatalogue(),
        )

        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Funktionen an Bord (Crew)", ignoreCase = true).performClick()

        compose.onNodeWithText("Marine 4", ignoreCase = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * A catalogue the size a real organisation has — the device report came from one with two
     * dozen Funktionen, and three would never overflow anything.
     *
     * @return twenty-four Funktionen.
     */
    private fun longCatalogue() =
        listOf("Pilot", "Turret", "Cargo", "Scout", "Medic", "Marine")
            .flatMap { name -> (1..CATALOGUE_VARIANTS).map { "$name $it" } }
            .mapIndexed { index, name -> MissionJobType("j$index", name) }

    /**
     * One of the payout sheet's radios, told apart from the row's read chip behind it.
     *
     * @param label the radio's German label.
     * @return a matcher for that radio and nothing else.
     */
    private fun inPayoutSheet(label: String) =
        hasText(label, ignoreCase = true) and hasAnyAncestor(hasTestTag(MISSION_PAYOUT_TAG))

    @Test
    fun `a roster row of a deleted account is named, not left blank`() {
        robot.show(
            readyForMe(
                MissionParticipant(
                    id = "p9",
                    userId = null,
                    name = "",
                    role = null,
                    checkedIn = false,
                    comment = null,
                    donating = null,
                ),
            ).copy(tab = MissionTab.PARTICIPANTS),
        )

        compose.onNodeWithText("Gelöschter Nutzer").assertIsDisplayed()
    }

    /**
     * The catalogue both pickers draw from.
     *
     * @return three Funktionen, of which a fixture row holds exactly one.
     */
    private fun catalogue() =
        listOf(
            MissionJobType("j1", "Pilot"),
            MissionJobType("j2", "Turret"),
            MissionJobType("j3", "Cargo"),
        )

    /**
     * Somebody else's row, with a Funktion already assigned to it.
     *
     * @return the participant.
     */
    private fun assigned() =
        MissionParticipant(
            id = "p2",
            userId = "u2",
            name = "Dorn",
            role = "Turret",
            checkedIn = false,
            comment = null,
            donating = false,
            plannedJobTypeId = "j2",
        )

    /**
     * An Einheit with one crew slot holding one Funktion.
     *
     * @return the unit.
     */
    private fun alpha() =
        MissionUnit(
            id = "u1",
            name = "Einheit Alpha",
            shipName = "Carrack Meridian",
            highValue = false,
            responsibleName = "Rhea",
            crew =
                listOf(
                    MissionCrewMember(
                        id = "c1",
                        name = "Dorn",
                        roles = listOf("Turret"),
                        roleIds = listOf("j2"),
                    ),
                ),
        )

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

        compose.onNodeWithTag(MISSION_DETAIL_CONTENT_TAG)
            .performScrollToNode(hasTestTag(MISSION_FINANCE_EDIT_TAG))
        compose.onNodeWithTag(MISSION_FINANCE_EDIT_TAG).assertIsEnabled()
        compose.onNodeWithTag(MISSION_FINANCE_DELETE_TAG).assertIsEnabled()
    }

    @Test
    fun `somebody else's booking offers neither`() {
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

    private companion object {
        /**
         * How many numbered variants each base Funktion gets in [longCatalogue].
         *
         * Six bases times this is twenty-four, which is the size the device report came from and
         * comfortably more than one phone screen of chips.
         */
        const val CATALOGUE_VARIANTS = 4
    }
}

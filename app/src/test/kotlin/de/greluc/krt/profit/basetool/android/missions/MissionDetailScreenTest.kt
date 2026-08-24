/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
@Config(sdk = [34], qualifiers = "de")
class MissionDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun detail(
        description: String? = "Quantainium-Abbau an der Lyria-Südwand.",
        started: Boolean = true,
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
        actualStartTime = if (started) Instant.parse("2026-08-23T12:00:00Z") else null,
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
     * @param signUps receives the sign-up action.
     * @param checkIns receives the check-in action.
     * @param payouts receives the payout-preference action.
     * @param bookings receives every money action, by name.
     */
    private fun show(
        state: MissionDetailState,
        tabs: MutableList<MissionTab> = mutableListOf(),
        signUps: MutableList<Unit> = mutableListOf(),
        checkIns: MutableList<Unit> = mutableListOf(),
        payouts: MutableList<Unit> = mutableListOf(),
        bookings: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                MissionDetailScreen(
                    state = state,
                    onTabSelected = { tabs.add(it) },
                    onRefresh = {},
                    onRetryNow = {},
                    onRetryFinances = {},
                    actions =
                        MissionSignUpActions(
                            onToggleSignUp = { signUps.add(Unit) },
                            onToggleCheckIn = { checkIns.add(Unit) },
                            onTogglePayoutPreference = { payouts.add(Unit) },
                        ),
                    finances =
                        MissionFinanceActions(
                            onAdd = { bookings.add("add") },
                            onEdit = { bookings.add("edit") },
                            onDelete = { bookings.add("delete") },
                            onIncome = {},
                            onAmount = {},
                            onNote = {},
                            onSave = { bookings.add("save") },
                            onDismiss = {},
                        ),
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
        // The sign-up band above the tabs costs a row of height, so the crew line can sit below
        // the fold on a compact screen. That it is drawn is the assertion.
        compose.onNodeWithText("Dorn — Turret").assertExists()
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

    @Test
    fun `an Einsatz the caller is not on offers to sign up, and nothing else`() {
        // Check-in and the payout preference act on a row. Offering them before there is one
        // would be offering a 404.
        val signed = mutableListOf<Unit>()
        show(readyForMe(), signUps = signed)

        compose.onNodeWithTag(MISSION_SIGN_UP_TAG).assertIsEnabled().performClick()
        compose.onAllNodesWithTag(MISSION_CHECK_IN_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(MISSION_PAYOUT_TAG).assertCountEquals(0)

        assertEquals(1, signed.size)
    }

    @Test
    fun `a signed-up caller is offered the withdrawal, the check-in and the preference`() {
        val checked = mutableListOf<Unit>()
        val paid = mutableListOf<Unit>()
        show(readyForMe(mine()), checkIns = checked, payouts = paid)

        compose.onNodeWithText("Abmelden", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag(MISSION_CHECK_IN_TAG).performClick()
        compose.onNodeWithTag(MISSION_PAYOUT_TAG).performClick()

        assertEquals(1, checked.size)
        assertEquals(1, paid.size)
    }

    @Test
    fun `a checked-in caller is offered the way back out`() {
        show(readyForMe(mine(checkedIn = true)))

        compose.onNodeWithText("Auschecken", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a donating caller is offered the payout instead`() {
        show(readyForMe(mine(donating = true)))

        compose.onNodeWithText("Auszahlen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a refusal on this Einsatz is said in the app's own words`() {
        show(readyForMe(mine()).copy(error = ApiError.Forbidden()))

        compose.onNodeWithText("Für diesen Einsatz fehlt dir die Berechtigung.").assertIsDisplayed()
    }

    @Test
    fun `offline the Einsatz says so and offers no write`() {
        show(readyForMe().copy(online = false))

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
            detail = detail(participants = roster.toList()),
            phase = MissionDetailPhase.Ready,
            me = Identity("u1", logistician = false),
        )

    @Test
    fun `an Einsatz that has not started offers no check-in, and says why`() {
        show(
            MissionDetailState(
                missionId = "m1",
                detail = detail(started = false, participants = listOf(mine())),
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
        show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(finances()),
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
        show(
            readyForMe().copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(finances()),
            ),
        )

        compose.onAllNodesWithTag(MISSION_FINANCE_ADD_TAG).assertCountEquals(0)
        compose.onNodeWithText("Buchen geht, sobald du für den Einsatz angemeldet bist.")
            .assertExists()
    }

    @Test
    fun `the caller's own booking offers a change and a delete`() {
        show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(finances(entry(participantId = "p1"))),
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
        show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(finances(entry(participantId = "p9"))),
            ),
        )

        compose.onNodeWithTag(MISSION_DETAIL_CONTENT_TAG).performScrollToNode(hasText("Erlös"))
        compose.onNodeWithText("Erlös").assertIsDisplayed()
        compose.onAllNodesWithTag(MISSION_FINANCE_EDIT_TAG).assertCountEquals(0)
    }

    @Test
    fun `the booking form opens on what the entry holds`() {
        show(
            readyForMe(mine()).copy(
                tab = MissionTab.FINANCES,
                finances = MissionFinancesPhase.Ready(finances()),
                entryDraft = FinanceEntryDraft(entryId = "e1", income = false, amount = "2500"),
            ),
        )

        compose.onNodeWithTag(MISSION_FINANCE_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("2500").assertIsDisplayed()
    }

    /**
     * The Finanzen tab's contents.
     *
     * @param entries the bookings.
     * @return the totals and the list.
     */
    private fun finances(vararg entries: MissionFinanceEntry) =
        MissionFinances(
            total = "74700",
            incomeSum = "86400",
            incomeCount = 3,
            expenseSum = "11700",
            expenseCount = 2,
            entries = entries.toList(),
            totalEntries = entries.size.toLong(),
        )

    /**
     * One booking.
     *
     * @param id the entry's id.
     * @param participantId whose sign-up it hangs off.
     * @return the entry.
     */
    private fun entry(
        id: String = "e1",
        participantId: String? = "p1",
    ) = MissionFinanceEntry(
        id = id,
        income = true,
        amount = "12000",
        note = "Erlös",
        participantName = "Rhea",
        participantId = participantId,
        version = 4L,
    )
}

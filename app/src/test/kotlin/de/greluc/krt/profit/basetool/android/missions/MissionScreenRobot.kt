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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
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
 * The Einsatz detail screen's test harness, shared by its two test classes.
 *
 * A file of its own rather than a base class: `MissionDetailScreenTest` covers the reading half and
 * the caller's own sign-up, `MissionManagerScreenTest` covers the manager half, and both drive the
 * same screen with the same fixtures. Two copies would drift the moment one of them gained a field.
 *
 * @property compose the rule that owns the composition.
 */
internal class MissionScreenRobot(
    private val compose: ComposeContentTestRule,
) {
    fun detail(
        description: String? = "Quantainium-Abbau an der Lyria-Südwand.",
        started: Boolean = true,
        participants: List<MissionParticipant> = emptyList(),
        units: List<MissionUnit> = emptyList(),
        steps: List<MissionStep> = emptyList(),
        objectives: List<MissionObjective> = emptyList(),
        frequencies: List<MissionFrequency> = emptyList(),
        canManage: Boolean = false,
    ) = MissionDetail(
        id = "m1",
        name = "Vertikaler Abbau",
        description = description,
        status = MissionStatus.PLANNED,
        rawStatus = "PLANNED",
        meetingTime = null,
        plannedStartTime = null,
        actualStartTime = if (started) Instant.parse("2026-08-23T12:00:00Z") else null,
        actualEndTime = null,
        plannedEndTime = null,
        isInternal = false,
        meetingPoint = "ARC-L1",
        operationId = null,
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
        canManage = canManage,
    )

    /**
     * The structure actions, recording every tap by name.
     *
     * Its own function so [show] stays under the length the gate allows — eleven fields inline made
     * the harness longer than the tests it serves.
     *
     * @param canManage whether the controls are unlocked.
     * @param taps where a tap is recorded.
     * @return the actions.
     */
    @Composable
    fun structureActions(
        canManage: Boolean,
        taps: MutableList<String>,
        crewJobTypes: List<MissionJobType> = emptyList(),
    ) = MissionStructureActions(
        canManage = canManage,
        enabled = true,
        draft = MissionStructureDraft(),
        denials = rememberDenialState(),
        onChange = {},
        onAddUnit = { taps.add("add-unit") },
        onRemoveUnit = { taps.add("remove-unit:$it") },
        onAddFrequency = { taps.add("add-freq") },
        onRemoveFrequency = { taps.add("remove-freq:$it") },
        onRemoveCrew = { unit, crew -> taps.add("remove-crew:$unit:$crew") },
        onEditUnit = { taps.add("edit-unit:${'$'}{it.id}") },
        onSaveUnit = { unit, version -> taps.add("save-unit:$unit:$version") },
        onAddCrew = { unit, participant -> taps.add("add-crew:$unit:$participant") },
        onOpenCrewPicker = { taps.add("open-crew-picker:${'$'}{it.id}") },
        onDismissCrewPicker = { taps.add("dismiss-crew-picker") },
        onSetCrewRoles = { unit, crew, ids, version ->
            taps.add("crew-roles:$unit:$crew:${'$'}{ids.sorted().joinToString(" + ")}:$version")
        },
        crewJobTypes = crewJobTypes,
    )

    /**
     * The Ablauf and Ziele actions, recording every tap.
     *
     * @param canManage the server's verdict.
     * @param taps receives each action by name.
     * @return the record the screen takes.
     */
    @Composable
    fun timelineActions(
        canManage: Boolean,
        taps: MutableList<String>,
    ) = MissionTimelineActions(
        canManage = canManage,
        enabled = true,
        draft = MissionTimelineDraft(),
        denials = rememberDenialState(),
        onChange = {},
        onCompose = { taps.add("compose:$it") },
        onSaveStep = { taps.add("save-step") },
        onEditStep = { taps.add("edit-step:${'$'}{it.id}") },
        onToggleStep = { id, done -> taps.add("toggle-step:$id:$done") },
        onRemoveStep = { taps.add("remove-step:$it") },
        onMoveStep = { id, up -> taps.add("move-step:$id:$up") },
        onDuplicateStep = { taps.add("duplicate-step") },
        onSaveObjective = { taps.add("save-objective") },
        onEditObjective = { taps.add("edit-objective:${'$'}{it.id}") },
        onRemoveObjective = { taps.add("remove-objective:$it") },
        onMoveObjective = { id, up -> taps.add("move-objective:$id:$up") },
        onDuplicateObjective = { taps.add("duplicate-objective") },
        onCancel = {},
    )

    /**
     * The member picker's actions, recording every tap.
     *
     * @param canManage the server's verdict.
     * @param taps receives each action by name.
     * @param picker the picker's state, so a test can open it.
     * @return the record the screen takes.
     */
    @Composable
    fun memberActions(
        canManage: Boolean,
        taps: MutableList<String>,
        picker: MissionMemberPickerState,
    ) = MissionMemberActions(
        canManage = canManage,
        enabled = true,
        state = picker,
        denials = rememberDenialState(),
        onOpen = { taps.add("open-picker:$it") },
        onQuery = { taps.add("query:$it") },
        onPick = { taps.add("pick:${'$'}{it.id}") },
        onDismiss = { taps.add("dismiss-picker") },
    )

    /**
     * The caller's own sign-up actions, recording every tap.
     *
     * Extracted from [show] so the harness stays under the length the gate allows — the same
     * reason `structureActions` was extracted before it.
     *
     * @param signUps receives the sign-up action.
     * @param checkIns receives the check-in action.
     * @param payouts receives the payout-preference action.
     * @return the record the screen takes.
     */
    private fun signUpActions(
        signUps: MutableList<Unit>,
        checkIns: MutableList<Unit>,
        payouts: MutableList<Unit>,
    ) = MissionSignUpActions(
        onToggleSignUp = { signUps.add(Unit) },
        onToggleCheckIn = { checkIns.add(Unit) },
        onTogglePayoutPreference = { payouts.add(Unit) },
        onJoinPayout = {},
        onDesiredFunction = {},
        onJoinConfirmed = {},
        onJoinDismissed = {},
    )

    /**
     * The money actions, recording every tap by name.
     *
     * @param bookings receives each money action.
     * @return the record the screen takes.
     */
    private fun financeActions(bookings: MutableList<String>) =
        MissionFinanceActions(
            onAdd = { bookings.add("add") },
            onEdit = { bookings.add("edit") },
            onDelete = { bookings.add("delete") },
            onIncome = {},
            onAmount = {},
            onNote = {},
            onSave = { bookings.add("save") },
            onDismiss = {},
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
    fun show(
        state: MissionDetailState,
        tabs: MutableList<MissionTab> = mutableListOf(),
        signUps: MutableList<Unit> = mutableListOf(),
        checkIns: MutableList<Unit> = mutableListOf(),
        payouts: MutableList<Unit> = mutableListOf(),
        bookings: MutableList<String> = mutableListOf(),
        rosterTaps: MutableList<String> = mutableListOf(),
        canManage: Boolean = false,
        jobTypes: List<MissionJobType> = emptyList(),
        crewJobTypes: List<MissionJobType> = emptyList(),
        picker: MissionMemberPickerState = MissionMemberPickerState(),
    ) {
        compose.setContent {
            KrtTheme {
                val roster =
                    MissionRosterActions(
                        canManage = canManage,
                        enabled = true,
                        checkInPossible = true,
                        jobTypes = jobTypes,
                        denials = rememberDenialState(),
                        onCheckIn = { rosterTaps.add("check-in:$it") },
                        onPayout = { rosterTaps.add("payout:$it") },
                        onFunction = { id, job -> rosterTaps.add("function:$id:${job.id}") },
                    )
                MissionDetailScreen(
                    state = state,
                    onTabSelected = { tabs.add(it) },
                    onRefresh = {},
                    onRetryNow = {},
                    onRetryFinances = {},
                    actions = signUpActions(signUps, checkIns, payouts),
                    roster = roster,
                    structure = structureActions(canManage, rosterTaps, crewJobTypes),
                    timeline = timelineActions(canManage, rosterTaps),
                    members = memberActions(canManage, rosterTaps, picker),
                    admin =
                        MissionAdminActions(
                            onChange = { _, _ -> },
                            onToggle = {},
                            onSave = {},
                            onAskLifecycle = {},
                            onConfirmLifecycle = {},
                            onDismissLifecycle = {},
                            onCorrectStart = {},
                            onCancelCorrectStart = {},
                            onKeepMine = {},
                            onReload = {},
                        ),
                    finances = financeActions(bookings),
                )
            }
        }
    }

    fun ready(
        detail: MissionDetail = detail(),
        tab: MissionTab = MissionTab.OVERVIEW,
        finances: MissionFinancesPhase = MissionFinancesPhase.Idle,
        adminForm: MissionAdminForm? = null,
    ) = MissionDetailState(
        missionId = "m1",
        detail = detail,
        phase = MissionDetailPhase.Ready,
        tab = tab,
        finances = finances,
        adminForm = adminForm,
    )

    /**
     * The Finanzen tab's contents.
     *
     * @param entries the bookings.
     * @return the totals and the list.
     */
    fun finances(vararg entries: MissionFinanceEntry) =
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
    fun entry(
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

    /**
     * Somebody other than the caller, with a wish and no assignment.
     *
     * @return the row.
     */
    fun rosterRow() =
        MissionParticipant(
            id = "p2",
            userId = "u2",
            name = "Dorn",
            role = null,
            checkedIn = false,
            comment = null,
            donating = null,
            desiredJobTypeId = "j1",
            desiredJobName = "Pilot",
            plannedJobTypeId = null,
            version = 3L,
        )
}

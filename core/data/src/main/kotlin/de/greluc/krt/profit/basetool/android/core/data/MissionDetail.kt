/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import java.time.Instant

/**
 * One participant of an Einsatz: a member, or an external person recorded by the leadership.
 *
 * @property id the participant row's id
 * @property userId which member this row belongs to, or `null` for an external row; the only way to
 *   tell whether a row is the caller's
 * @property name the member's effective name, else the external person's name, else empty when the
 *   server redacted it (REQ-SEC-007)
 * @property role the planned job, falling back to the desired one when nothing is assigned yet
 * @property orgUnitNames which Staffeln or Spezialkommandos they belong to, shorthand first; empty
 *   for a redacted read
 * @property checkedIn whether they have checked in, derived from [startTime]
 * @property comment their free-text note; absent for an outsider read (ADR-0034)
 * @property donating whether their share is donated rather than paid out, or `null` when the
 *   server stated no preference
 * @property desiredJobTypeId the job they asked for, echoed by [MissionSource.setPlannedFunction]
 * @property desiredJobName the same job's display name, or `null`
 * @property plannedJobTypeId the job actually assigned, or `null` while nobody has assigned one
 * @property version the row's optimistic-lock version, required by every write against it
 * @property startTime the check-in time, verbatim as the server sent it; echoed by a manager's
 *   write, since omitting it checks the member out
 * @property endTime when they checked out, echoed back the same way
 */
data class MissionParticipant(
    val id: String,
    val userId: String?,
    val name: String,
    val role: String?,
    val orgUnitNames: List<String> = emptyList(),
    val checkedIn: Boolean,
    val comment: String?,
    val donating: Boolean?,
    val desiredJobTypeId: String? = null,
    val desiredJobName: String? = null,
    val plannedJobTypeId: String? = null,
    val version: Long = 0L,
    val startTime: String? = null,
    val endTime: String? = null,
)

/**
 * One crew slot inside a unit.
 *
 * @property id the crew row's id
 * @property name the assigned participant's name
 * @property roles the jobs they hold in this unit, in server order
 * @property roleIds the same jobs as ids, sent in full by a role write
 * @property version the row's own optimistic lock, echoed by a role write
 */
data class MissionCrewMember(
    val id: String,
    val name: String,
    val roles: List<String>,
    val roleIds: List<String> = emptyList(),
    val version: Long = 0L,
)

/**
 * A ship or squad the Einsatz is organised into ("Einheit Alpha").
 *
 * @property id the unit's id
 * @property name the unit's name
 * @property shipName the ship or ship type it flies, or `null`
 * @property highValue whether it is flagged HVU
 * @property responsibleName who leads it, or `null`
 * @property fields what it carries beyond its name and its mark, by id; echoed by every write
 *   because `PUT /units/{id}` replaces what it is not sent
 * @property crew who is aboard, in server order
 * @property version the unit's own optimistic lock, echoed by a rename or an HVU toggle
 */
data class MissionUnit(
    val id: String,
    val name: String,
    val shipName: String?,
    val highValue: Boolean,
    val responsibleName: String?,
    val crew: List<MissionCrewMember>,
    val version: Long = 0L,
    val fields: MissionUnitFields = MissionUnitFields(),
)

/**
 * What an Einsatz-Einheit carries beyond its name and its HVU mark.
 *
 * @property shipTypeId which class of ship, or `null`.
 * @property shipId which ship of it, or `null`; limited to ships a registered participant owns or
 *   one already pinned to a unit of the mission.
 * @property frequency the comms frequency, or `null`.
 * @property responsibleUserId who answers for the unit, or `null`.
 * @property note the free line, or `null`.
 */
data class MissionUnitFields(
    val shipTypeId: String? = null,
    val shipId: String? = null,
    val frequency: Double? = null,
    val responsibleUserId: String? = null,
    val note: String? = null,
)

/**
 * One line of the Ablauf checklist.
 *
 * @property id the step's id
 * @property title what happens
 * @property meta the time-and-place line beneath it, or `null`
 * @property done whether it is ticked off
 */
data class MissionStep(
    val id: String,
    val title: String,
    val meta: String?,
    val done: Boolean,
)

/**
 * One Ziel of the Einsatz.
 *
 * @property id the objective's id
 * @property title what is to be achieved
 * @property kind the server's classification, verbatim and uninterpreted
 */
data class MissionObjective(
    val id: String,
    val title: String,
    val kind: String?,
)

/**
 * One radio frequency the Einsatz uses.
 *
 * @property id the frequency's id
 * @property type what it is for ("Einsatz-1", "Notruf"), or `null`
 * @property value the frequency itself, which the design makes tap-to-copy
 */
data class MissionFrequency(
    val id: String,
    val type: String?,
    val value: String,
)

/**
 * One member who manages this Einsatz.
 *
 * @property userId who — what a removal addresses.
 * @property name what to show for them, the server's effective name.
 */
data class MissionManager(
    val userId: String,
    val name: String,
)

/**
 * An Einsatz in full: everything the seven detail tabs draw.
 *
 * An outsider read is redacted (ADR-0034): no [description], no owner, no participant comments;
 * those fields are legitimately absent.
 *
 * @property id the Einsatz's id
 * @property name its title
 * @property description the long free-text briefing; `null` for an outsider read
 * @property status where it stands
 * @property rawStatus the untranslated server value, for [MissionStatus.UNKNOWN]
 * @property meetingTime the Teamspeak gathering time, or `null`
 * @property plannedStartTime the scheduled server-join time, or `null`
 * @property actualStartTime when it actually began, or `null`
 * @property actualEndTime when it actually ended, or `null` while it runs; echoed by the schedule
 *   write, which replaces the section
 * @property plannedEndTime the scheduled end, or `null`
 * @property isInternal squadron-internal; an outsider never receives one at all
 * @property meetingPoint the in-fiction gathering location, or `null`
 * @property operationId the umbrella Operation by id, or `null`; echoed by the Kern write, which
 *   replaces the section
 * @property operationName the umbrella Operation, or `null`
 * @property orgUnitName the owning unit's name, or `null`
 * @property orgUnitShorthand the owning unit's short form, which the badge draws
 * @property partyLeadName who leads it, member or guest, or `null`
 * @property managers who manages it besides the lead
 * @property canManageManagers whether the caller may add or remove a manager, as the server
 *   answered it
 * @property registeredParticipants how many signed up, as the server counts them
 * @property checkedInParticipants how many of those have checked in
 * @property participants the roster, in server order
 * @property units the Einheiten, in server order
 * @property steps the Ablauf, in server order
 * @property objectives the Ziele, in server order
 * @property frequencies the radio plan, in server order
 * @property coreVersion the Kern section's own optimistic-lock counter
 * @property scheduleVersion the Zeitplan section's counter
 * @property flagsVersion the flags section's counter
 * @property calendarLink the external calendar entry, or `null`; neither shown nor edited, only
 *   echoed by a Kern write
 * @property canManage whether the caller may act on other members' rows, the server's own `canEdit`
 *   (ADR-0011); `false` when the server omits it
 */
data class MissionDetail(
    val id: String,
    val name: String,
    val description: String?,
    val status: MissionStatus,
    val rawStatus: String?,
    val meetingTime: Instant?,
    val plannedStartTime: Instant?,
    val actualStartTime: Instant?,
    val actualEndTime: Instant?,
    val plannedEndTime: Instant?,
    val isInternal: Boolean,
    val meetingPoint: String?,
    val operationId: String?,
    val operationName: String?,
    val orgUnitName: String?,
    val orgUnitShorthand: String?,
    val partyLeadName: String?,
    val managers: List<MissionManager> = emptyList(),
    val canManageManagers: Boolean = false,
    val registeredParticipants: Int,
    val checkedInParticipants: Int,
    val participants: List<MissionParticipant>,
    val units: List<MissionUnit>,
    val steps: List<MissionStep>,
    val objectives: List<MissionObjective>,
    val frequencies: List<MissionFrequency>,
    val calendarLink: String? = null,
    val canManage: Boolean = false,
    val coreVersion: Long = 0L,
    val scheduleVersion: Long = 0L,
    val flagsVersion: Long = 0L,
    val partyLeadVersion: Long = 0L,
    val stepsVersion: Long = 0L,
    val objectivesVersion: Long = 0L,
)

/**
 * One booked income or expense.
 *
 * @property id the entry's id
 * @property income `true` for an income, `false` for an expense
 * @property amount the magnitude, always positive; the sign lives in [income]
 * @property note what it was for, or `null`
 * @property participantName who booked it, or `null`
 * @property participantId whose sign-up it hangs off, or `null`; the app may only edit its own
 * @property version the entry's optimistic lock, echoed by an edit
 */
data class MissionFinanceEntry(
    val id: String,
    val income: Boolean,
    val amount: String,
    val note: String?,
    val participantId: String?,
    val version: Long?,
    val participantName: String?,
)

/**
 * The Finanzen tab: the totals band plus the entries behind it.
 *
 * Amounts are strings exactly as the server rendered them and are never recomputed.
 *
 * @property total the net, income minus expense
 * @property incomeSum everything booked as income
 * @property incomeCount how many income entries there are
 * @property expenseSum everything booked as expense
 * @property expenseCount how many expense entries there are
 * @property entries the first page of entries, in server order
 * @property totalEntries how many entries exist in total
 */
data class MissionFinances(
    val total: String?,
    val incomeSum: String?,
    val incomeCount: Long,
    val expenseSum: String?,
    val expenseCount: Long,
    val entries: List<MissionFinanceEntry>,
    val totalEntries: Long,
)

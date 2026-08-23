/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import java.time.Instant

/**
 * One member (or guest) signed up for an Einsatz.
 *
 * @property id the participant row's id
 * @property userId which member this row belongs to, or `null` for a guest sign-up and for an
 *   outsider read the server redacted. It is the only thing that says whether a row is the
 *   caller's — a name cannot decide it, since the server sends `displayName` when a member set one
 *   and `username` otherwise
 * @property name what to show: the member's effective name, else the guest's name, else empty —
 *   the server redacts identity for outsiders, so an anonymous read can legitimately yield neither
 * @property role the planned job, falling back to the desired one when nothing is assigned yet
 * @property checkedIn whether they have checked in, which the design draws as the "davon N
 *   eingecheckt" count and a per-row mark
 * @property comment their free-text note; **absent for an outsider read** (ADR-0034 strips it)
 * @property donating whether their share is donated rather than paid out, or `null` when the
 *   server stated no preference
 */
data class MissionParticipant(
    val id: String,
    val userId: String?,
    val name: String,
    val role: String?,
    val checkedIn: Boolean,
    val comment: String?,
    val donating: Boolean?,
)

/**
 * One crew slot inside a unit.
 *
 * @property id the crew row's id
 * @property name the assigned participant's name
 * @property roles the jobs they hold in this unit, in server order
 */
data class MissionCrewMember(
    val id: String,
    val name: String,
    val roles: List<String>,
)

/**
 * A ship or squad the Einsatz is organised into ("Einheit Alpha").
 *
 * @property id the unit's id
 * @property name the unit's name
 * @property shipName the ship or ship type it flies, or `null`
 * @property highValue whether it is flagged HVU, which the design marks with its own chip
 * @property responsibleName who leads it, or `null`
 * @property crew who is aboard, in server order
 */
data class MissionUnit(
    val id: String,
    val name: String,
    val shipName: String?,
    val highValue: Boolean,
    val responsibleName: String?,
    val crew: List<MissionCrewMember>,
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
 * @property kind the server's classification, verbatim — this build does not interpret it, and
 *   showing an unrecognised kind beats hiding a goal
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
 * An Einsatz in full — everything the seven detail tabs draw.
 *
 * **An outsider read is a smaller object, not a failed one.** The backend redacts for anonymous and
 * role-less callers (ADR-0034): no [description], no owner, participants without their comment. The
 * app must therefore treat every one of those as legitimately absent rather than as a parse
 * failure — which is why they are nullable here and why nothing downstream may assume otherwise.
 *
 * @property id the Einsatz's id
 * @property name its title
 * @property description the long free-text briefing; `null` for an outsider read
 * @property status where it stands
 * @property rawStatus the untranslated server value, for [MissionStatus.UNKNOWN]
 * @property meetingTime the Teamspeak gathering time, or `null`
 * @property plannedStartTime the scheduled server-join time, or `null`
 * @property actualStartTime when it actually began, or `null`
 * @property plannedEndTime the scheduled end, or `null`
 * @property isInternal squadron-internal; an outsider never receives one at all
 * @property meetingPoint the in-fiction gathering location, or `null`
 * @property operationName the umbrella Operation, or `null`
 * @property orgUnitName the owning unit's name, or `null`
 * @property orgUnitShorthand the owning unit's short form, which the badge draws
 * @property partyLeadName who leads it, member or guest, or `null`
 * @property registeredParticipants how many signed up, as the server counts them
 * @property checkedInParticipants how many of those have checked in
 * @property participants the roster, in server order
 * @property units the Einheiten, in server order
 * @property steps the Ablauf, in server order
 * @property objectives the Ziele, in server order
 * @property frequencies the radio plan, in server order
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
    val plannedEndTime: Instant?,
    val isInternal: Boolean,
    val meetingPoint: String?,
    val operationName: String?,
    val orgUnitName: String?,
    val orgUnitShorthand: String?,
    val partyLeadName: String?,
    val registeredParticipants: Int,
    val checkedInParticipants: Int,
    val participants: List<MissionParticipant>,
    val units: List<MissionUnit>,
    val steps: List<MissionStep>,
    val objectives: List<MissionObjective>,
    val frequencies: List<MissionFrequency>,
)

/**
 * One booked income or expense.
 *
 * @property id the entry's id
 * @property income `true` for an income, `false` for an expense. Stored as a flag rather than the
 *   server's string so the sign cannot be derived twice, in two places, from two spellings.
 * @property amount the magnitude, always positive; the sign lives in [income]
 * @property note what it was for, or `null`
 * @property participantName who booked it, or `null`
 * @property participantId whose sign-up it hangs off, or `null` — the app may only edit its own,
 *   and a name cannot decide whose that is
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
 * Amounts are carried as **strings exactly as the server rendered them**. They are aUEC sums that
 * are only ever displayed, never recomputed on the device, and parsing a decimal into a `Double` to
 * print it again is how a total gains a rounding error it did not have on the server.
 *
 * @property total the net, income minus expense
 * @property incomeSum everything booked as income
 * @property incomeCount how many income entries there are
 * @property expenseSum everything booked as expense
 * @property expenseCount how many expense entries there are
 * @property entries the first page of entries, in server order
 * @property totalEntries how many entries exist in total, which the tab states so a partial view
 *   can never look complete
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

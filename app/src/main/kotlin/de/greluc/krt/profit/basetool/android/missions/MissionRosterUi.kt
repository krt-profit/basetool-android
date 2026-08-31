/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChoiceChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusDot
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Teilnehmer: the roster with its check-in marks, and — for a manager — the per-row actions the
 * design draws ("Manager sehen die Check-In-Aktion je Zeile; Mitglieder nur den eigenen Status",
 * chapter 06, artboard 2).
 *
 * @param detail the Einsatz.
 * @param mine the caller's own row, drawn in the brand colour so they can find themselves in a
 *   roster of thirty.
 * @param roster what a manager may do to a row, and what to say when they may not.
 */
internal fun LazyListScope.participantsTab(
    detail: MissionDetail,
    mine: MissionParticipant?,
    roster: MissionRosterActions,
) {
    if (detail.participants.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_participants) }
        return
    }
    // Checked in first, then by name — and the chip beside the counts says so. The artboard draws
    // both („14 Teilnehmer · 9 eingecheckt" · „Sortiert: Check-In"), and the order is the point: on
    // the evening of an Einsatz the question is who is already there, not who signed up first.
    val ordered =
        detail.participants.sortedWith(
            compareByDescending<MissionParticipant> { it.checkedIn }.thenBy { it.name.lowercase() },
        )
    item { RosterSummary(detail = detail) }
    items(ordered, key = { it.id }) { participant ->
        ParticipantRow(participant = participant, isMine = participant.id == mine?.id, roster = roster)
    }
    item {
        Text(
            text = stringResource(R.string.mission_detail_roster_note),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The roster's own header line: how many, how many are in, and how the list is ordered.
 *
 * @param detail the Einsatz.
 */
@Composable
private fun RosterSummary(detail: MissionDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Two counts, two plural rules: German and English both inflect „Teilnehmer" and
            // „eingecheckt" independently, so the line is composed from two plurals rather than
            // from one string with two placeholders in it.
            text =
                pluralStringResource(
                    R.plurals.mission_roster_count,
                    detail.registeredParticipants,
                    detail.registeredParticipants,
                ) + MISSION_DOT +
                    pluralStringResource(
                        R.plurals.mission_roster_checked_in,
                        detail.checkedInParticipants,
                        detail.checkedInParticipants,
                    ).krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
            modifier = Modifier.weight(1f),
        )
        KrtChip(text = stringResource(R.string.mission_detail_roster_sorted))
    }
}

/**
 * One roster row: who, whether they are in, what they fly, and what they asked to fly.
 *
 * @param participant the row.
 * @param isMine whether it is the caller's own.
 * @param roster the manager's actions and their gate.
 */
@Composable
private fun ParticipantRow(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
) {
    // A bordered card, not loose text on the page: artboard 06-2 draws each member as a record with
    // its own frame, which is what lets a roster of thirty be scanned rather than read.
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .padding(horizontal = KrtSpacing.s14, vertical = KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = ROSTER_ROW_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The dot replaces the „NICHT EINGECHECKT" chip. Two words per row, thirty rows, and
            // the one fact they carry is binary — the design spends 8 dp on it instead of 90.
            KrtStatusDot(
                on = participant.checkedIn,
                stateLabel =
                    stringResource(
                        if (participant.checkedIn) {
                            R.string.mission_detail_checked_in
                        } else {
                            R.string.mission_detail_not_checked_in
                        },
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isMine) MaterialTheme.colorScheme.primary else KrtPalette.White,
                )
                // Which Staffel they come from — not their Funktion, which the chips below already
                // are. The row used to repeat the assigned job here and say nothing about where a
                // name belongs, on a screen whose whole subject is who is coming.
                participant.orgUnitNames.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it.joinToString(MISSION_DOT),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
                // The wish is drawn beside the assignment („Wunsch: {{ p.jobWish }}") and is the
                // whole reason a manager can assign anything sensibly. Shown only when it differs
                // from what is assigned — repeating the same word twice tells nobody anything —
                // and in the warning tint, because a divergence is what the manager must act on.
                participant.desiredJobName
                    ?.takeIf { it != participant.role }
                    ?.let {
                        Text(
                            text = stringResource(R.string.mission_detail_wish, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = KrtPalette.Warning,
                        )
                    }
            }
            // The payout as a **read** chip: it states the member's standing choice. Design ch. 18
            // §3 (E6) keeps the read chip and the choice chip apart on purpose, so this one never
            // becomes the control — switching it is the ghost button below.
            participant.donating?.let { donating ->
                KrtChip(
                    text =
                        stringResource(
                            if (donating) {
                                R.string.mission_detail_payout_org
                            } else {
                                R.string.mission_detail_payout_self
                            },
                        ),
                    tone = if (donating) KrtChipTone.Primary else KrtChipTone.Muted,
                )
            }
            ParticipantCheckIn(participant, roster)
        }
        ParticipantManagerActions(participant, roster)
    }
}

/**
 * The row's check-in, as the 44 dp icon button the artboard draws.
 *
 * A labelled button per row cost about a third of the row's width for a word every row repeats;
 * the `.btn-icon` contract exists for exactly this case, and carries the name in the content
 * description and the tooltip instead.
 *
 * @param participant the row.
 * @param roster the actions and the gate.
 */
@Composable
private fun ParticipantCheckIn(
    participant: MissionParticipant,
    roster: MissionRosterActions,
) {
    val gate =
        Gate(
            allowed = roster.canManage,
            reason = stringResource(R.string.gate_role_mission_manager),
            detail = stringResource(R.string.gate_role_mission_manager_detail),
        )
    val (dim, click) = rememberGated(gate, { roster.onCheckIn(participant.id) }, roster.denials)
    KrtIconButton(
        iconRes =
            when {
                !gate.allowed -> DesignR.drawable.ic_krt_lock
                participant.checkedIn -> DesignR.drawable.ic_krt_logout
                else -> DesignR.drawable.ic_krt_login
            },
        label =
            stringResource(
                if (participant.checkedIn) {
                    R.string.mission_detail_check_out_row
                } else {
                    R.string.mission_detail_check_in_row
                },
            ),
        onClick = click,
        modifier = dim.alpha(if (roster.enabled) 1f else DISABLED_WRITE_ALPHA),
        // The server refuses a check-in before the Einsatz has actually started, so the control is
        // inactive before then — validation, which dims but never locks.
        enabled = roster.enabled && roster.checkInPossible,
    )
}

/**
 * The row's manager controls: check the member in or out, switch their payout, assign their job.
 *
 * All three render for **everyone** and are locked for a caller who may not manage, per the design
 * ("Ohne Missions-Manager-Rolle rendert das Funktions-Select gesperrt — antippbar, der Toast nennt
 * die Rolle"). Hiding them was the rejected alternative: this organisation grants roles by hand,
 * and a control nobody can see is one nobody asks to be given.
 *
 * @param participant the row.
 * @param roster the actions and the gate.
 */
@Composable
private fun ParticipantManagerActions(
    participant: MissionParticipant,
    roster: MissionRosterActions,
) {
    val gate =
        Gate(
            allowed = roster.canManage,
            reason = stringResource(R.string.gate_role_mission_manager),
            detail = stringResource(R.string.gate_role_mission_manager_detail),
        )
    val (payoutDim, payoutClick) =
        rememberGated(gate, { roster.onPayout(participant.id) }, roster.denials)
    // Switching somebody else's payout is the one manager action artboard 06-2 does not place: the
    // row draws the preference as a read chip and stops there. It stays a ghost button under the
    // row rather than being dropped — the write exists and the design nowhere strikes it — and it
    // is on the gap list for a ruling.
    KrtGhostButton(
        text = stringResource(R.string.mission_detail_payout_row),
        onClick = payoutClick,
        iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
        modifier = payoutDim.alpha(if (roster.enabled) 1f else DISABLED_WRITE_ALPHA),
        enabled = roster.enabled,
    )
    ParticipantFunctionSelect(participant, gate, roster)
}

/**
 * „Funktion an Bord": the chips a manager assigns from.
 *
 * The catalogue is only read for a caller who may assign, so for everyone else this draws the
 * assignment as a single locked chip rather than an empty row — a locked control with nothing in it
 * would say less than the plain text above it already does.
 *
 * @param participant the row.
 * @param gate whether the caller may assign, and why not.
 * @param roster the actions and the catalogue.
 */
@Composable
private fun ParticipantFunctionSelect(
    participant: MissionParticipant,
    gate: Gate,
    roster: MissionRosterActions,
) {
    if (roster.jobTypes.isEmpty()) {
        return
    }
    Text(
        text = stringResource(R.string.mission_detail_function_label),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
    // The same control as the sign-up sheet's, for the same reason it is a FlowRow there: five
    // Funktionen do not fit one phone line, and a horizontal scroller would hide the ones past the
    // edge behind a gesture nothing announces.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        roster.jobTypes.forEach { jobType ->
            val (dim, click) =
                rememberGated(gate, { roster.onFunction(participant.id, jobType) }, roster.denials)
            // A choice, not a filter: design ch. 18 §3 (E6) keeps the two chips deliberately
            // different, and this one IS the value rather than a way of narrowing a list.
            KrtChoiceChip(
                text = jobType.name,
                selected = participant.plannedJobTypeId == jobType.id,
                onClick = click,
                modifier = dim.alpha(if (roster.enabled) 1f else DISABLED_WRITE_ALPHA),
                // Never `enabled = false`: a chip that cannot be tapped cannot say why it is dim,
                // which is the whole point of the locked pattern (ADR-0011, artboard 14). Offline
                // is the one case that does disable it — there the answer is the connection, not a
                // grant, and the toast would name the wrong thing.
                enabled = roster.enabled,
            )
        }
    }
}

/**
 * The roster row's identity line — 56 dp in artboard 06-2, above the control floor because the
 * line carries a name, a second line and a button group.
 */
private val ROSTER_ROW_HEIGHT = 56.dp

/** The separator the design uses between two facts on one line. */
private const val MISSION_DOT = " · "

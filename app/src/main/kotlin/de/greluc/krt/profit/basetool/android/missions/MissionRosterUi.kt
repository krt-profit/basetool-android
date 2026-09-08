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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusDot
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.participantLabel
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
 * @param own what the caller may do to their OWN row, from its sheet.
 */
internal fun LazyListScope.participantsTab(
    detail: MissionDetail,
    mine: MissionParticipant?,
    roster: MissionRosterActions,
    own: MissionOwnRoleActions,
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
        val isMine = participant.id == mine?.id
        ParticipantRow(participant = participant, isMine = isMine, roster = roster, own = own)
    }
    // No footnote. Artboard 06-2 ends the tab with a grey paragraph, but it is a **handoff
    // annotation** rather than copy — its second sentence points at „Muster Kap. 09" — and the app
    // does not put chapter references in front of members.
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
 * @param own what the caller may do to their own row, for the sheet behind the row's ⋮.
 */
@Composable
private fun ParticipantRow(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
    own: MissionOwnRoleActions,
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
                    text = participantLabel(participant.name),
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
            // becomes the control — the control is in the row's sheet.
            //
            // On the caller's own row too, now. It was suppressed there while the radio pair was
            // drawn directly beneath it, which would have stated the same value twice a finger
            // apart; with that pair moved into the sheet, the chip is the only thing saying it.
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
            ParticipantOverflow(participant, isMine, roster, own)
        }
        ParticipantAssignedFunction(participant)
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
 * The row's ⋮ — one entry, and it opens the row's sheet.
 *
 * **It used to carry the payout toggle directly, and the assignment chips were drawn under every
 * row** (owner decision, 2026-09-07). The chips were the whole catalogue on every one of fourteen
 * rows, so the roster read as a wall of chips in which the chosen one was indistinguishable at a
 * glance. Anteil, Wunsch and Funktion are one subject and now share one surface: [MissionRoleSheet].
 *
 * The entry itself is never locked and never gated, because opening a sheet is not a write. The
 * lock did not disappear with it — each of the sheet's three sections carries its own, which is
 * what lets one sheet serve a manager, a member on their own row, and a member on somebody
 * else's (ADR-0011: the control is drawn and locked, not hidden).
 *
 * @param participant the row.
 * @param isMine whether it is the caller's own.
 * @param roster the manager's actions, the catalogue and the denial sink.
 * @param own what the caller may do to their own row.
 */
@Composable
private fun ParticipantOverflow(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
    own: MissionOwnRoleActions,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    KrtOverflowMenu(
        contentDescription = stringResource(R.string.mission_detail_participant_actions),
        expanded = menuOpen,
        onExpandedChange = { menuOpen = it },
        items =
            listOf(
                KrtMenuItem(
                    label = stringResource(R.string.mission_role_sheet_title),
                    iconRes = DesignR.drawable.ic_krt_user,
                ) {
                    menuOpen = false
                    sheetOpen = true
                },
            ),
    )
    if (sheetOpen) {
        MissionRoleSheet(
            participant = participant,
            isMine = isMine,
            roster = roster,
            own = own,
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * „Funktion an Bord": what this member was actually assigned, and nothing else.
 *
 * A read chip, not the picker it replaced. The picker drew every Funktion the organisation has
 * defined on every row of the roster; what a reader of the roster wants from a row is the one that
 * was chosen. Assigning is still done here — through the row's ⋮, in [MissionRoleSheet], where the
 * catalogue is the subject rather than the noise around it.
 *
 * Nothing is drawn when nobody has been assigned yet: an empty label on fourteen rows says less
 * than the absence of a chip does, and the ⋮ that would set one is on every row regardless.
 *
 * @param participant the row.
 */
@Composable
private fun ParticipantAssignedFunction(participant: MissionParticipant) {
    val assigned = participant.role?.takeIf { it.isNotBlank() } ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.mission_detail_function_label),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        KrtChip(text = assigned, tone = KrtChipTone.Primary)
    }
}

/**
 * The roster row's identity line — 56 dp in artboard 06-2, above the control floor because the
 * line carries a name, a second line and a button group.
 */
private val ROSTER_ROW_HEIGHT = 56.dp

/** The separator the design uses between two facts on one line. */
private const val MISSION_DOT = " · "

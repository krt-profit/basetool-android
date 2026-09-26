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
    val ordered =
        detail.participants.sortedWith(
            compareByDescending<MissionParticipant> { it.checkedIn }.thenBy { it.name.lowercase() },
        )
    item { RosterSummary(detail = detail) }
    items(ordered, key = { it.id }) { participant ->
        val isMine = participant.id == mine?.id
        ParticipantRow(participant = participant, isMine = isMine, roster = roster, own = own)
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
                participant.orgUnitNames.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it.joinToString(MISSION_DOT),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
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
 * The row's check-in as a 44 dp icon button, named by its content description and tooltip.
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
        enabled = roster.enabled && roster.checkInPossible,
    )
}

/**
 * The row's ⋮ — one entry that opens [MissionRoleSheet].
 *
 * The entry is never locked, since opening a sheet is not a write; each section of the sheet
 * carries its own lock (ADR-0011).
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
 * „Funktion an Bord": a read chip for the Funktion this member was assigned, or nothing when none
 * is; assigning happens in [MissionRoleSheet].
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

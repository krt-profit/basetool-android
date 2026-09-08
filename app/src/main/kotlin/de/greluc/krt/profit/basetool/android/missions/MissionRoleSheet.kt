/*
 * Profit Basetool - Android app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChoiceChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.participantLabel
import de.greluc.krt.profit.basetool.android.ui.rememberGated

/** Test handle for the roster row's Funktionen-und-Anteil sheet. */
internal const val MISSION_ROLE_SHEET_TAG = "mission-role-sheet"

/**
 * What the caller may do to their OWN row from the sheet.
 *
 * Separate from [MissionRosterActions] because these two are not the manager's to make: the payout
 * and the wish belong to the member whose row it is, and the manager's own payout entry writes a
 * different call.
 *
 * @property onPayout switch the caller's own share between paid out and donated.
 * @property onDesired the caller asked for a different job, or tapped the one they had to clear it.
 */
internal data class MissionOwnRoleActions(
    val onPayout: () -> Unit,
    val onDesired: (MissionJobType) -> Unit,
)

/**
 * Anteil, Wunschfunktion and the assigned Funktion — one sheet, opened from the row's ⋮.
 *
 * **Why a sheet at all (owner decision, 2026-09-07).** All three were drawn INLINE in the roster
 * row, and the two job sections drew the whole catalogue as choice chips: fifteen members each
 * showing every Funktion the organisation has defined, so the roster read as a wall of chips in
 * which the one that was actually chosen was indistinguishable at a glance. The row now shows only
 * what is set; the catalogue is here, one tap away, where a picker belongs.
 *
 * **Every section is drawn, and a section the caller may not use is locked rather than absent**
 * (ADR-0011). The three do not share an owner:
 *
 *  - **Anteil** — the member's own choice on their own row; on somebody else's it is the manager's
 *    entry, which is the one thing this ⋮ already carried before.
 *  - **Wunsch** — the member's own, and nobody else's. On a foreign row it is drawn as the plain
 *    value with a line saying whose choice it is. Not a locked control: a lock offers a role that
 *    would unlock it, and no role lets one member wish on another's behalf, so the toast would name
 *    the wrong thing.
 *  - **Funktion an Bord** — the Einsatzleitung's, locked with the role for everyone else.
 *
 * @param participant the row the sheet was opened on.
 * @param isMine whether that row is the caller's own.
 * @param roster the manager's actions, the catalogue and the denial sink.
 * @param own what the caller may do to their own row.
 * @param onDismiss close it.
 */
@Composable
internal fun MissionRoleSheet(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
    own: MissionOwnRoleActions,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.mission_role_sheet_title),
        modifier = Modifier.testTag(MISSION_ROLE_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            Text(
                text = participantLabel(participant.name),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            PayoutSection(participant = participant, isMine = isMine, roster = roster, own = own)
            WishSection(participant = participant, isMine = isMine, roster = roster, own = own)
            AssignedSection(participant = participant, roster = roster)
        }
    }
}

/**
 * Where this member's share goes.
 *
 * @param participant the row.
 * @param isMine whether it is the caller's own.
 * @param roster the manager's actions and gate.
 * @param own what the caller may do to their own row.
 */
@Composable
private fun PayoutSection(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
    own: MissionOwnRoleActions,
) {
    SectionLabel(stringResource(R.string.mission_detail_payout_label))
    val gate = missionManagerGate(roster.canManage)
    // On the caller's own row the choice is theirs and needs no grant; on anybody else's it is the
    // manager's, and the tap is routed through the gate so a refusal explains itself.
    val (dim, click) = rememberGated(gate, { roster.onPayout(participant.id) }, roster.denials)
    val donating = participant.donating == true
    Column(
        modifier = Modifier.fillMaxWidth().testTag(MISSION_PAYOUT_TAG),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        KrtRadioRow(
            selected = !donating,
            onSelect = { if (donating) (if (isMine) own.onPayout() else click()) },
            label = stringResource(R.string.mission_detail_payout_self),
            enabled = roster.enabled,
            modifier = if (isMine) Modifier else dim,
        )
        KrtRadioRow(
            selected = donating,
            onSelect = { if (!donating) (if (isMine) own.onPayout() else click()) },
            label = stringResource(R.string.mission_detail_payout_org),
            enabled = roster.enabled,
            modifier = if (isMine) Modifier else dim,
        )
    }
}

/**
 * The job this member asked for.
 *
 * @param participant the row.
 * @param isMine whether it is the caller's own.
 * @param roster the catalogue.
 * @param own what the caller may do to their own row.
 */
@Composable
private fun WishSection(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
    own: MissionOwnRoleActions,
) {
    if (roster.jobTypes.isEmpty()) {
        return
    }
    SectionLabel(stringResource(R.string.mission_join_function))
    if (!isMine) {
        // The value, and whose it is. See the class comment: a lock here would offer a role that
        // does not exist.
        KrtChip(
            text =
                roster.jobTypes.firstOrNull { it.id == participant.desiredJobTypeId }?.name
                    ?: stringResource(R.string.mission_role_sheet_no_wish),
            tone = KrtChipTone.Muted,
        )
        Text(
            text = stringResource(R.string.mission_role_sheet_wish_is_theirs),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        roster.jobTypes.forEach { jobType ->
            KrtChoiceChip(
                text = jobType.name,
                selected = participant.desiredJobTypeId == jobType.id,
                onClick = { own.onDesired(jobType) },
                enabled = roster.enabled,
            )
        }
    }
    Text(
        text = stringResource(R.string.mission_join_function_hint),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/**
 * The job the Einsatzleitung has assigned.
 *
 * @param participant the row.
 * @param roster the manager's actions, the catalogue and the gate.
 */
@Composable
private fun AssignedSection(
    participant: MissionParticipant,
    roster: MissionRosterActions,
) {
    if (roster.jobTypes.isEmpty()) {
        return
    }
    SectionLabel(stringResource(R.string.mission_detail_function_label))
    val gate = missionManagerGate(roster.canManage)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        roster.jobTypes.forEach { jobType ->
            val (dim, click) =
                rememberGated(gate, { roster.onFunction(participant.id, jobType) }, roster.denials)
            KrtChoiceChip(
                text = jobType.name,
                selected = participant.plannedJobTypeId == jobType.id,
                onClick = click,
                modifier = dim,
                // Never `enabled = false` for a missing grant: a chip that cannot be tapped cannot
                // say why it is dim (ADR-0011). Offline is the one case that does disable it.
                enabled = roster.enabled,
            )
        }
    }
}

/**
 * One section's uppercase label.
 *
 * @param text the label.
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.krtUppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = KrtPalette.TextMuted,
    )
}

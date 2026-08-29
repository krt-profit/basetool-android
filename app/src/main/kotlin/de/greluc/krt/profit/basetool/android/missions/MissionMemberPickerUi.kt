/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the member picker's sheet. */
const val MISSION_MEMBER_PICKER_TAG: String = "mission-member-picker"

/** Test handle for the „Einsatzleitung setzen" entry point. */
const val MISSION_PARTY_LEAD_TAG: String = "mission-party-lead"

/** Test handle for the „Manager hinzufügen" entry point. */
const val MISSION_MANAGER_ADD_TAG: String = "mission-manager-add"

/** Test handle for the „Teilnehmer hinzufügen" entry point. */
const val MISSION_PARTICIPANT_ADD_TAG: String = "mission-participant-add"

/**
 * What the member picker reports back.
 *
 * @property canManage whether the caller may write at all; the server's own verdict.
 * @property enabled whether a write may run right now.
 * @property state the picker as it stands.
 * @property denials where a refused tap is announced.
 * @property onOpen open the picker for one of the three writes.
 * @property onQuery the search text changed.
 * @property onPick a member was chosen.
 * @property onDismiss the picker was closed without choosing.
 */
data class MissionMemberActions(
    val canManage: Boolean,
    val enabled: Boolean,
    val state: MissionMemberPickerState,
    val denials: DenialState,
    val onOpen: (MissionMemberTarget) -> Unit,
    val onQuery: (String) -> Unit,
    val onPick: (MemberOption) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The three member-shaped writes, as three entry points into one picker.
 *
 * > **Round 10's § 10e is answered here**: chapter 12's remote combobox *is* the right control for
 * > naming a member — type, the list narrows, a muted notice states what the cap hid. What round 11
 * > still asks for is where these three rows sit on the Verwaltung tab; the section below is
 * > composed from drawn parts and its **composition is unratified**.
 *
 * @param members the actions and the picker's state.
 */
@Composable
fun MemberSection(members: MissionMemberActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        MemberEntry(
            label = stringResource(R.string.mission_member_party_lead),
            target = MissionMemberTarget.PARTY_LEAD,
            tag = MISSION_PARTY_LEAD_TAG,
            members = members,
        )
        MemberEntry(
            label = stringResource(R.string.mission_member_manager),
            target = MissionMemberTarget.MANAGER,
            tag = MISSION_MANAGER_ADD_TAG,
            members = members,
        )
        MemberEntry(
            label = stringResource(R.string.mission_member_participant),
            target = MissionMemberTarget.PARTICIPANT,
            tag = MISSION_PARTICIPANT_ADD_TAG,
            members = members,
        )
    }
}

/**
 * One entry point into the picker, locked for a caller who may not manage.
 *
 * @param label what it says.
 * @param target what the pick is for.
 * @param tag the test handle.
 * @param members the actions, for the gate and the refusal slot.
 */
@Composable
private fun MemberEntry(
    label: String,
    target: MissionMemberTarget,
    tag: String,
    members: MissionMemberActions,
) {
    val gate = missionMemberGate(members.canManage)
    val (dim, click) = rememberGated(gate, { members.onOpen(target) }, members.denials)
    KrtGhostButton(
        text = label,
        onClick = click,
        iconRes = if (gate.allowed) DesignR.drawable.ic_krt_user_plus else DesignR.drawable.ic_krt_lock,
        modifier = dim.fillMaxWidth().testTag(tag),
        enabled = members.enabled,
    )
}

/**
 * The picker itself: a sheet holding chapter 12's combobox.
 *
 * A sheet rather than an inline field, and for a reason the three entry points make plain: one
 * combobox serves three writes, so it has to say which one it is serving. A sheet titled
 * „Einsatzleitung setzen" does; a bare field under three buttons would not.
 *
 * @param members the actions and the picker's state.
 */
@Composable
fun MemberPickerSheet(members: MissionMemberActions) {
    val target = members.state.target ?: return
    // The combobox is stateless and the caller owns expansion. Held here rather than in the view
    // model: the list is open for as long as the sheet is, and nothing outside it cares.
    var expanded by remember(target) { mutableStateOf(true) }
    KrtBottomSheet(
        onDismiss = members.onDismiss,
        title = stringResource(target.titleRes()),
        modifier = Modifier.testTag(MISSION_MEMBER_PICKER_TAG),
    ) {
        KrtCombobox(
            query = members.state.query,
            onQueryChange = {
                expanded = true
                members.onQuery(it)
            },
            options = members.state.options.map { KrtOption(value = it.id, label = it.name) },
            onSelect = { option ->
                members.state.options.firstOrNull { it.id == option.value }?.let(members.onPick)
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.mission_member_search),
            placeholder = stringResource(R.string.mission_member_search_hint),
            // The cap is stated, never silent: the server is asked for at most MEMBER_PICKER_CAP
            // rows, so a full list is a list that may be hiding somebody. Typing more narrows it.
            notice =
                if (members.state.searching) {
                    stringResource(R.string.mission_member_searching)
                } else {
                    pluralStringResource(
                        R.plurals.mission_member_notice,
                        members.state.options.size,
                        members.state.options.size,
                        MEMBER_PICKER_CAP,
                    )
                },
        )
    }
}

/**
 * What the picker's sheet is titled, which is what says which write it is serving.
 *
 * @return the string resource.
 */
private fun MissionMemberTarget.titleRes(): Int =
    when (this) {
        MissionMemberTarget.PARTY_LEAD -> R.string.mission_member_party_lead
        MissionMemberTarget.MANAGER -> R.string.mission_member_manager
        MissionMemberTarget.PARTICIPANT -> R.string.mission_member_participant
    }

/**
 * The gate the three entry points share.
 *
 * @param canManage the server's verdict.
 * @return the gate, with the role it names.
 */
@Composable
private fun missionMemberGate(canManage: Boolean): Gate =
    Gate(
        allowed = canManage,
        reason = stringResource(R.string.gate_role_mission_manager),
        detail = stringResource(R.string.gate_role_mission_manager_detail),
    )

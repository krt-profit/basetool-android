/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionCrewMember
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionUnit
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtAssocAdd
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChoiceChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSheetOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the „+ Person zuweisen" surface. */
const val MISSION_CREW_ADD_TAG: String = "mission-crew-add"

/** Test handle for the roster picker it opens. */
const val MISSION_CREW_PICKER_TAG: String = "mission-crew-picker"

/** Test handle for one Einheit's rename action. */
const val MISSION_UNIT_EDIT_TAG: String = "mission-unit-edit"

/** Test handle for the Einheiten tab's add action. */
const val MISSION_UNIT_ADD_TAG: String = "mission-unit-add"

/** Test handle for the Frequenzen tab's add action. */
const val MISSION_FREQ_ADD_TAG: String = "mission-freq-add"

/**
 * What a manager can do to the Einsatz's structure.
 *
 * @property canManage whether the caller may edit it at all.
 * @property enabled whether a write may run right now.
 * @property draft what is being composed.
 * @property denials where a refused tap is announced.
 * @property onChange a field changed.
 * @property onAddUnit add the Einheit that is typed.
 * @property onRemoveUnit remove an Einheit by id.
 * @property onAddFrequency add the frequency that is typed.
 * @property onRemoveFrequency remove a frequency by id.
 * @property onConfirmRemoveManager the manager removal was accepted.
 * @property onDismissRemoveManager it was dismissed.
 * @property onRemoveCrew take somebody off an Einheit — `(unitId, crewId)`.
 */
data class MissionStructureActions(
    val canManage: Boolean,
    val enabled: Boolean,
    val draft: MissionStructureDraft,
    val denials: DenialState,
    val onChange: ((MissionStructureDraft) -> MissionStructureDraft) -> Unit,
    val onAddUnit: () -> Unit,
    val onRemoveUnit: (String) -> Unit,
    val onAddFrequency: () -> Unit,
    val onRemoveFrequency: (String) -> Unit,
    val onConfirmRemoveManager: () -> Unit = {},
    val onDismissRemoveManager: () -> Unit = {},
    val onRemoveCrew: (String, String) -> Unit,
    val onEditUnit: (MissionUnit) -> Unit,
    val onSaveUnit: (String, Long) -> Unit,
    val onSetCrewRoles: (String, String, Set<String>, Long) -> Unit,
    val onAddCrew: (String, String) -> Unit,
    val onOpenCrewPicker: (MissionUnit) -> Unit,
    val onDismissCrewPicker: () -> Unit,
    val crewJobTypes: List<MissionJobType>,
)

/**
 * The Einheiten tab's composer: name it, mark it, add it.
 *
 * **No artboard.** Artboard 2 annotates „+ Person zuweisen" and „Einheiten sind offen (keine
 * Slot-Grenze)" on a unit it does not draw being created. Composed from drawn parts; round 10 asks
 * for the drawing.
 *
 * @param structure the actions and what is typed.
 */
@Composable
fun UnitComposer(structure: MissionStructureActions) {
    val gate = missionManagerGate(structure.canManage)
    val editing = structure.draft.editingUnitId
    // One set of fields appends and renames — `editingUnitId` decides which, and the button says
    // so. The save carries the unit's own version, because a rename is a replace and a stale
    // counter would overwrite a concurrent one silently.
    val save: () -> Unit = {
        if (editing == null) {
            structure.onAddUnit()
        } else {
            structure.onSaveUnit(editing, structure.draft.editingUnitVersion)
        }
    }
    val (dim, click) = rememberGated(gate, save, structure.denials)
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtTextField(
            value = structure.draft.unitName,
            onValueChange = { v -> structure.onChange { it.copy(unitName = v) } },
            label = stringResource(R.string.mission_struct_unit_name),
            enabled = structure.enabled && gate.allowed,
        )
        // A yes/no is not one-of-N, so it is a square checkbox. The round radio is the design
        // system's only circular element and stays reserved for a real choice — the payout
        // preference (ch. 06 artboards 3 and 10).
        KrtCheckboxRow(
            checked = structure.draft.unitHighValue,
            onCheckedChange = { v -> structure.onChange { it.copy(unitHighValue = v) } },
            label = stringResource(R.string.mission_struct_hvu),
            enabled = structure.enabled && gate.allowed,
        )
        KrtGhostButton(
            text =
                stringResource(
                    if (editing == null) {
                        R.string.mission_struct_add_unit
                    } else {
                        R.string.mission_unit_rename_save
                    },
                ),
            onClick = click,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = dim.fillMaxWidth().testTag(MISSION_UNIT_ADD_TAG),
            enabled = structure.enabled,
        )
        if (editing != null) {
            KrtGhostButton(
                text = stringResource(R.string.mission_timeline_cancel),
                onClick = { structure.onChange { MissionStructureDraft() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = structure.enabled,
            )
        }
    }
}

/**
 * One Einheit's own manager actions: rename it, or drop it.
 *
 * @param unit the Einheit.
 * @param structure the actions, for the gate and the refusal slot.
 */
@Composable
fun UnitRowActions(
    unit: MissionUnit,
    structure: MissionStructureActions,
) {
    val gate = missionManagerGate(structure.canManage)
    val (renameDim, rename) = rememberGated(gate, { structure.onEditUnit(unit) }, structure.denials)
    // Stacked, not side by side: „EINHEIT ENTFERNEN" is seventeen characters and will not share a
    // 411 dp row with anything without wrapping — the same measured collapse as the sign-up bar's.
    KrtGhostButton(
        text = stringResource(R.string.mission_unit_rename),
        onClick = rename,
        iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
        modifier = renameDim.fillMaxWidth().testTag(MISSION_UNIT_EDIT_TAG),
        enabled = structure.enabled,
    )
    StructureRemove(
        label = stringResource(R.string.mission_struct_remove_unit),
        structure = structure,
        onRemove = { structure.onRemoveUnit(unit.id) },
    )
}

/**
 * Putting somebody aboard an Einheit — „+ Person zuweisen", which artboard 2 annotates.
 *
 * The candidates come from the **roster**, not from a server search: crew is drawn from the people
 * already signed up to this Einsatz, and they are already in hand. Anyone aboard this unit is
 * dropped from the row, so the list is what can still be done rather than what exists.
 *
 * > Artboard 2 annotates „+ Person zuweisen — antippen oder halten & ziehen" on a unit it does not
 * > draw. The tap half is built; the drag half is a gesture and round 11 asks for it.
 *
 * @param unit the Einheit.
 * @param roster everybody signed up to the Einsatz.
 * @param structure the actions, for the gate and the refusal slot.
 */
@Composable
fun CrewAdd(
    unit: MissionUnit,
    roster: List<MissionParticipant>,
    structure: MissionStructureActions,
) {
    val gate = missionManagerGate(structure.canManage)
    val aboard = unit.crew.map { it.name }.toSet()
    val candidates = roster.filterNot { aboard.contains(it.name) }
    if (candidates.isEmpty()) {
        return
    }
    val (dim, click) = rememberGated(gate, { structure.onOpenCrewPicker(unit) }, structure.denials)
    KrtAssocAdd(
        text = stringResource(R.string.mission_crew_assign),
        onClick = click,
        modifier = dim.testTag(MISSION_CREW_ADD_TAG),
        enabled = structure.enabled,
        locked = !gate.allowed,
    )
}

/**
 * The roster picker „+ Person zuweisen" opens.
 *
 * Candidates come from the **roster**, not from a server search: crew is drawn from the people
 * already signed up to this Einsatz, and they are already in hand. Anyone aboard this unit is
 * dropped, so the list is what can still be done rather than what exists.
 *
 * @param unit which Einheit the picker is filling.
 * @param roster everybody signed up to the Einsatz.
 * @param structure the actions.
 */
@Composable
fun CrewPickerSheet(
    unit: MissionUnit,
    roster: List<MissionParticipant>,
    structure: MissionStructureActions,
) {
    val aboard = unit.crew.map { it.name }.toSet()
    val candidates = roster.filterNot { aboard.contains(it.name) }
    KrtBottomSheet(
        onDismiss = structure.onDismissCrewPicker,
        title = stringResource(R.string.mission_crew_assign_title, unit.name),
        modifier = Modifier.testTag(MISSION_CREW_PICKER_TAG),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            candidates.forEach { participant ->
                KrtSheetOption(
                    text = participant.name,
                    selected = false,
                    onClick = { structure.onAddCrew(unit.id, participant.id) },
                )
            }
        }
    }
}

/**
 * One crew slot's Funktionen an Bord: the CREW catalogue as toggling chips.
 *
 * > **The second catalogue.** These are `CREW` job types — Pilot, Turret, Cargo, Scan, Medic — and
 * > they share their names with the `MISSION` ones a participant's Funktion comes from. Assigning
 * > from the wrong list produces a `400` that only appears on save.
 *
 * The write is a **replace**: tapping a chip sends the whole set with that one added or removed.
 *
 * Three states, ratified by design ch. 18 §3 (E7): chosen is filled orange with black text,
 * available is a hairline, and one already held by somebody else in the same Einheit is dimmed
 * **with their name behind it** — which is what turns „dim" into a reason. It is not locked: two
 * people may legitimately share a role, and the name is there so the second one is a decision
 * rather than an accident.
 *
 * @param unitId which Einheit.
 * @param member the crew slot.
 * @param crew every slot of that Einheit, so a role taken elsewhere can name its holder.
 * @param structure the actions, the catalogue, and the refusal slot.
 */
@Composable
fun CrewRoleSelect(
    unitId: String,
    member: MissionCrewMember,
    crew: List<MissionCrewMember>,
    structure: MissionStructureActions,
) {
    val gate = missionManagerGate(structure.canManage)
    if (structure.crewJobTypes.isEmpty()) {
        // An organisation that has defined no CREW types gets a sentence rather than an empty row.
        // The Funktionen are admin-maintained Stammdaten with no seed, so this is an ordinary state
        // and not a failure.
        Text(
            text = stringResource(R.string.mission_crew_roles_empty),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Text(
            text = stringResource(R.string.mission_crew_roles),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            structure.crewJobTypes.forEach { job ->
                val held = member.roleIds.contains(job.id)
                val next = if (held) member.roleIds - job.id else member.roleIds + job.id
                val takenBy =
                    crew.firstOrNull { it.id != member.id && job.id in it.roleIds }?.name
                val (dim, click) =
                    rememberGated(
                        gate,
                        { structure.onSetCrewRoles(unitId, member.id, next.toSet(), member.version) },
                        structure.denials,
                    )
                KrtChoiceChip(
                    text = job.name,
                    selected = held,
                    onClick = click,
                    modifier = dim.alpha(if (takenBy == null || held) 1f else TAKEN_ROLE_ALPHA),
                    enabled = structure.enabled,
                    suffix = takenBy.takeIf { !held },
                )
            }
        }
    }
}

/** A role somebody else already holds is dimmed to this, with their name behind it (E7). */
private const val TAKEN_ROLE_ALPHA = 0.55f

/**
 * The Frequenzen tab's composer.
 *
 * @param structure the actions and what is typed.
 */
@Composable
fun FrequencyComposer(structure: MissionStructureActions) {
    val gate = missionManagerGate(structure.canManage)
    val (dim, click) = rememberGated(gate, structure.onAddFrequency, structure.denials)
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtTextField(
            value = structure.draft.freqName,
            onValueChange = { v -> structure.onChange { it.copy(freqName = v) } },
            label = stringResource(R.string.mission_struct_freq_name),
            enabled = structure.enabled && gate.allowed,
        )
        KrtTextField(
            value = structure.draft.freqValue,
            onValueChange = { v -> structure.onChange { it.copy(freqValue = v) } },
            label = stringResource(R.string.mission_struct_freq_value),
            enabled = structure.enabled && gate.allowed,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        KrtGhostButton(
            text = stringResource(R.string.mission_struct_add_freq),
            onClick = click,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = dim.testTag(MISSION_FREQ_ADD_TAG),
            enabled = structure.enabled,
        )
    }
}

/**
 * A per-row remove, locked for a caller who may not manage.
 *
 * @param label what it says.
 * @param structure the actions, for the gate and the refusal slot.
 * @param onRemove what to do when it is allowed.
 */
@Composable
fun StructureRemove(
    label: String,
    structure: MissionStructureActions,
    onRemove: () -> Unit,
) {
    val gate = missionManagerGate(structure.canManage)
    val (dim, click) = rememberGated(gate, onRemove, structure.denials)
    KrtGhostButton(
        text = label,
        onClick = click,
        iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
        modifier = dim,
        enabled = structure.enabled,
    )
}

/**
 * The gate every structure control shares.
 *
 * @param canManage the server's verdict.
 * @return the gate, with the role it names.
 */
@Composable
private fun missionManagerGate(canManage: Boolean): Gate =
    Gate(
        allowed = canManage,
        reason = stringResource(R.string.gate_role_mission_manager),
        detail = stringResource(R.string.gate_role_mission_manager_detail),
    )

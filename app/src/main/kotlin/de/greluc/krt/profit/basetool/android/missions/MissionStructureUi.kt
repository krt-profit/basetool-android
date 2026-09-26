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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionCrewMember
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionUnit
import de.greluc.krt.profit.basetool.android.core.data.krtToDoubleOrNull
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtAssocAdd
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChoiceChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSheetOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusDot
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.FieldLimits
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.participantLabel
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
 * @property shipOptions the ships one of this Einsatz's units may be crewed with.
 * @property memberOptions who may be made responsible for a unit.
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
    val shipOptions: List<Pair<String, String>> = emptyList(),
    val memberOptions: List<Pair<String, String>> = emptyList(),
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
 * „+ Einheit" — the dashed action at the foot of the Einheiten list that opens the compose sheet
 * (artboard 06-14).
 *
 * @param structure the actions, for the gate and the refusal slot.
 */
@Composable
fun UnitAdd(structure: MissionStructureActions) {
    val gate = missionManagerGate(structure.canManage)
    val (dim, click) =
        rememberGated(
            gate,
            { structure.onChange { it.copy(composingUnit = true, unitName = "", unitHighValue = false) } },
            structure.denials,
        )
    KrtAssocAdd(
        text = stringResource(R.string.mission_struct_add_unit_short),
        onClick = click,
        modifier = dim.fillMaxWidth().testTag(MISSION_UNIT_ADD_TAG),
        enabled = structure.enabled,
        locked = !gate.allowed,
    )
}

/** Test handle for the „Einheit hinzufügen" sheet. */
const val MISSION_UNIT_COMPOSE_TAG: String = "mission-unit-compose"

/**
 * Composing an Einheit in a sheet: name it, mark it HVU, add it.
 *
 * @param structure the actions and what is typed.
 */
@Composable
fun UnitComposeSheet(structure: MissionStructureActions) {
    if (!structure.draft.composingUnit) {
        return
    }
    KrtBottomSheet(
        onDismiss = { structure.onChange { MissionStructureDraft() } },
        modifier = Modifier.testTag(MISSION_UNIT_COMPOSE_TAG),
        title = stringResource(R.string.mission_struct_add_unit),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            StructureError(structure)
            KrtTextField(
                value = structure.draft.unitName,
                onValueChange = { v -> structure.onChange { it.copy(unitName = v.take(FieldLimits.NAME)) } },
                label = stringResource(R.string.mission_struct_unit_name),
                enabled = structure.enabled,
            )
            KrtCheckboxRow(
                checked = structure.draft.unitHighValue,
                onCheckedChange = { v -> structure.onChange { it.copy(unitHighValue = v) } },
                label = stringResource(R.string.mission_struct_hvu),
                enabled = structure.enabled,
            )
            UnitFields(structure = structure)
            KrtCtaButton(
                text = stringResource(R.string.mission_struct_add_unit),
                onClick = structure.onAddUnit,
                modifier = Modifier.fillMaxWidth(),
                enabled = structure.enabled && structure.draft.unitName.isNotBlank(),
            )
        }
    }
}

/**
 * What an Einheit carries beyond its name and its mark: ship, frequency, responsible member, note.
 *
 * The ship list is the mission's own (`/unit-ship-options`): ships owned by registered participants
 * plus those already pinned to a unit.
 *
 * @param structure the tab's state and actions.
 */
@Composable
private fun UnitFields(structure: MissionStructureActions) {
    var shipOpen by remember { mutableStateOf(false) }
    var responsibleOpen by remember { mutableStateOf(false) }
    val fields = structure.draft.unitFields
    val noShip = stringResource(R.string.mission_struct_unit_ship_none)
    KrtSelectField(
        value = structure.shipOptions.firstOrNull { it.first == fields.shipId }?.second ?: noShip,
        options =
            listOf(KrtOption(value = "", label = noShip)) +
                structure.shipOptions.map { KrtOption(value = it.first, label = it.second) },
        onSelect = { option ->
            shipOpen = false
            structure.onChange {
                it.copy(unitFields = it.unitFields.copy(shipId = option.value.ifBlank { null }))
            }
        },
        expanded = shipOpen,
        onExpandedChange = { shipOpen = it },
        label = stringResource(R.string.mission_struct_unit_ship),
        selectedValue = fields.shipId.orEmpty(),
        enabled = structure.enabled,
    )
    val noResponsible = stringResource(R.string.mission_struct_unit_responsible_none)
    KrtSelectField(
        value =
            structure.memberOptions.firstOrNull { it.first == fields.responsibleUserId }?.second
                ?: noResponsible,
        options =
            listOf(KrtOption(value = "", label = noResponsible)) +
                structure.memberOptions.map { KrtOption(value = it.first, label = it.second) },
        onSelect = { option ->
            responsibleOpen = false
            structure.onChange {
                it.copy(
                    unitFields =
                        it.unitFields.copy(responsibleUserId = option.value.ifBlank { null }),
                )
            }
        },
        expanded = responsibleOpen,
        onExpandedChange = { responsibleOpen = it },
        label = stringResource(R.string.mission_struct_unit_responsible),
        selectedValue = fields.responsibleUserId.orEmpty(),
        enabled = structure.enabled,
    )
    KrtTextField(
        value = fields.frequency?.let { krtPlainFrequency(it) }.orEmpty(),
        onValueChange = { v ->
            structure.onChange {
                it.copy(unitFields = it.unitFields.copy(frequency = v.krtToDoubleOrNull()))
            }
        },
        label = stringResource(R.string.mission_struct_unit_frequency),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = structure.enabled,
    )
    KrtTextField(
        value = fields.note.orEmpty(),
        onValueChange = { v ->
            structure.onChange {
                val capped = v.take(FieldLimits.NOTE)
                it.copy(unitFields = it.unitFields.copy(note = capped.takeIf { t -> t.isNotBlank() }))
            }
        },
        label = stringResource(R.string.mission_struct_unit_note),
        enabled = structure.enabled,
    )
}

/**
 * A frequency as the field spells it.
 *
 * @param value the figure.
 * @return it without a trailing `.0`, which is what a whole frequency would otherwise show.
 */
private fun krtPlainFrequency(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/**
 * One Einheit's header band (artboard 06-14): glyph, name, ship, crew count, and the two manager
 * actions as icon buttons above a 2 dp orange rule.
 *
 * @param unit the Einheit.
 * @param structure the actions, for the gate and the refusal slot.
 */
@Composable
fun UnitHeader(
    unit: MissionUnit,
    structure: MissionStructureActions,
) {
    val gate = missionManagerGate(structure.canManage)
    val (renameDim, rename) = rememberGated(gate, { structure.onEditUnit(unit) }, structure.denials)
    val (removeDim, remove) =
        rememberGated(gate, { structure.onRemoveUnit(unit.id) }, structure.denials)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .padding(start = KrtSpacing.s14, end = KrtSpacing.s4, top = KrtSpacing.s4, bottom = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIcon(
            id = DesignR.drawable.ic_krt_ship,
            contentDescription = null,
            size = UNIT_GLYPH,
            tint = KrtPalette.Orange,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = unit.name.krtUppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = KrtPalette.White,
            )
            unit.shipName?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
            }
        }
        if (unit.highValue) {
            KrtChip(text = stringResource(R.string.mission_detail_unit_hvu), tone = KrtChipTone.Warning)
        }
        KrtChip(text = unit.crew.size.toString())
        KrtIconButton(
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_edit else DesignR.drawable.ic_krt_lock,
            label = stringResource(R.string.mission_unit_rename),
            onClick = rename,
            modifier = renameDim.testTag(MISSION_UNIT_EDIT_TAG),
            enabled = structure.enabled,
        )
        KrtIconButton(
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_trash else DesignR.drawable.ic_krt_lock,
            label = stringResource(R.string.mission_struct_remove_unit),
            onClick = remove,
            modifier = removeDim,
            enabled = structure.enabled,
        )
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(KrtSpacing.headingRule)
                .background(KrtPalette.Orange),
    )
}

/** The Einheit's leading glyph in its header band — 18 px in artboard 06-14. */
private val UNIT_GLYPH = 18.dp

/**
 * One crew slot of an Einheit (artboard 06-14), framed inside the unit's card, with a trailing
 * icon button that takes the member off the Einheit.
 *
 * @param unit the Einheit the slot belongs to.
 * @param member the slot.
 * @param roster the Einsatz's roster, the source of the person's check-in mark and Staffel.
 * @param structure the actions, for the gate and the refusal slot.
 */
@Composable
fun CrewRow(
    unit: MissionUnit,
    member: MissionCrewMember,
    roster: List<MissionParticipant>,
    structure: MissionStructureActions,
) {
    val gate = missionManagerGate(structure.canManage)
    val (dim, click) =
        rememberGated(gate, { structure.onRemoveCrew(unit.id, member.id) }, structure.denials)
    val person = roster.firstOrNull { it.name == member.name }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtStatusDot(
                on = person?.checkedIn == true,
                stateLabel =
                    stringResource(
                        if (person?.checkedIn == true) {
                            R.string.mission_detail_checked_in
                        } else {
                            R.string.mission_detail_not_checked_in
                        },
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participantLabel(member.name),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = KrtPalette.White,
                )
                person?.orgUnitNames?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it.joinToString(CREW_DOT),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
            KrtIconButton(
                iconRes = if (gate.allowed) DesignR.drawable.ic_krt_logout else DesignR.drawable.ic_krt_lock,
                label = stringResource(R.string.mission_struct_remove_crew),
                onClick = click,
                modifier = dim,
                enabled = structure.enabled,
            )
            KrtOverflowMenu(
                contentDescription = stringResource(R.string.mission_detail_participant_actions),
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                items =
                    listOf(
                        KrtMenuItem(
                            label = stringResource(R.string.mission_crew_roles),
                            iconRes = DesignR.drawable.ic_krt_user,
                        ) {
                            menuOpen = false
                            sheetOpen = true
                        },
                    ),
            )
        }
        CrewRolesRead(member)
    }
    if (sheetOpen) {
        KrtBottomSheet(
            onDismiss = { sheetOpen = false },
            title = stringResource(R.string.mission_crew_roles),
            modifier = Modifier.testTag(MISSION_CREW_ROLE_SHEET_TAG),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(KrtSpacing.s16),
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
                CrewRoleSelect(
                    unitId = unit.id,
                    member = member,
                    crew = unit.crew,
                    structure = structure,
                )
            }
        }
    }
}

/** Test handle for the crew row's Funktionen sheet. */
const val MISSION_CREW_ROLE_SHEET_TAG: String = "mission-crew-role-sheet"

/**
 * The Funktionen this crew slot holds, as read chips; nothing when it holds none.
 *
 * Assigning happens in the row's ⋮.
 *
 * @param member the crew slot.
 */
@Composable
private fun CrewRolesRead(member: MissionCrewMember) {
    if (member.roles.isEmpty()) {
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        member.roles.forEach { role -> KrtChip(text = role, tone = KrtChipTone.Primary) }
    }
}

/** The separator between two facts on a crew row's second line. */
private const val CREW_DOT = " · "

/** Test handle for the rename sheet. */
const val MISSION_UNIT_RENAME_TAG: String = "mission-unit-rename"

/**
 * Renaming an Einheit in a one-field sheet (design ch. 18 §3, E7).
 *
 * „Speichern" stays dimmed until the name differs; the HVU mark is echoed unchanged.
 *
 * @param structure the actions and what is typed.
 */
@Composable
fun UnitRenameSheet(structure: MissionStructureActions) {
    val editing = structure.draft.editingUnitId ?: return
    val typed = structure.draft.unitName
    val unchanged = typed.trim() == structure.draft.editingUnitOriginalName.trim()
    KrtBottomSheet(
        onDismiss = { structure.onChange { MissionStructureDraft() } },
        modifier = Modifier.testTag(MISSION_UNIT_RENAME_TAG),
        title = stringResource(R.string.mission_unit_rename),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            StructureError(structure)
            KrtTextField(
                value = typed,
                onValueChange = { v -> structure.onChange { it.copy(unitName = v.take(FieldLimits.NAME)) } },
                label = stringResource(R.string.mission_struct_unit_name),
                enabled = structure.enabled,
            )
            KrtCtaButton(
                text = stringResource(R.string.mission_unit_rename_save),
                onClick = { structure.onSaveUnit(editing, structure.draft.editingUnitVersion) },
                modifier = Modifier.fillMaxWidth(),
                enabled = structure.enabled && !unchanged && typed.isNotBlank(),
            )
        }
    }
}

/**
 * „+ Person zuweisen" — putting somebody aboard an Einheit by tap.
 *
 * Candidates come from the Einsatz roster, minus anyone already aboard this unit.
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
 * The roster picker „+ Person zuweisen" opens, listing signed-up members not already aboard this
 * unit.
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
 * One crew slot's Funktionen an Bord: the `CREW` catalogue as toggling chips, drawn in the row's
 * sheet.
 *
 * Each tap sends the whole set (a replace). Chosen chips are filled orange, available ones a
 * hairline, and one held by somebody else in the same Einheit is dimmed with that holder's name
 * but stays selectable (design ch. 18 §3, E7).
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
        Text(
            text = stringResource(R.string.mission_crew_roles_empty),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        Text(
            text = stringResource(R.string.mission_crew_roles).krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
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
fun FrequencyAdd(structure: MissionStructureActions) {
    val gate = missionManagerGate(structure.canManage)
    val (dim, click) =
        rememberGated(
            gate,
            { structure.onChange { it.copy(composingFrequency = true, freqName = "", freqValue = "") } },
            structure.denials,
        )
    KrtAssocAdd(
        text = stringResource(R.string.mission_struct_add_freq_short),
        onClick = click,
        modifier = dim.fillMaxWidth().testTag(MISSION_FREQ_ADD_TAG),
        enabled = structure.enabled,
        locked = !gate.allowed,
    )
}

/** Test handle for the „Frequenz hinzufügen" sheet. */
const val MISSION_FREQ_COMPOSE_TAG: String = "mission-freq-compose"

/**
 * Composing a frequency in a sheet with two fields (design ch. 18 §3, E7).
 *
 * @param structure the actions and what is typed.
 */
@Composable
fun FrequencyComposeSheet(structure: MissionStructureActions) {
    if (!structure.draft.composingFrequency) {
        return
    }
    KrtBottomSheet(
        onDismiss = { structure.onChange { MissionStructureDraft() } },
        modifier = Modifier.testTag(MISSION_FREQ_COMPOSE_TAG),
        title = stringResource(R.string.mission_struct_add_freq),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            StructureError(structure)
            KrtTextField(
                value = structure.draft.freqName,
                onValueChange = { v -> structure.onChange { it.copy(freqName = v) } },
                label = stringResource(R.string.mission_struct_freq_name),
                enabled = structure.enabled,
            )
            KrtTextField(
                value = structure.draft.freqValue,
                onValueChange = { v -> structure.onChange { it.copy(freqValue = v) } },
                label = stringResource(R.string.mission_struct_freq_value),
                enabled = structure.enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            KrtHint(explanation = stringResource(R.string.mission_struct_freq_helper))
            KrtCtaButton(
                text = stringResource(R.string.mission_struct_add_freq),
                onClick = structure.onAddFrequency,
                modifier = Modifier.fillMaxWidth(),
                enabled = structure.enabled && structure.draft.freqValue.krtIsFrequency(),
            )
        }
    }
}

/**
 * Shows the refusal of a failed Einheit, crew or frequency write.
 *
 * @param structure the actions, for the draft that carries the refusal.
 */
@Composable
fun StructureError(structure: MissionStructureActions) {
    structure.draft.error?.let { SignUpError(error = it) }
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
internal fun missionManagerGate(canManage: Boolean): Gate =
    Gate(
        allowed = canManage,
        reason = stringResource(R.string.gate_role_mission_manager),
        detail = stringResource(R.string.gate_role_mission_manager_detail),
    )

/**
 * Whether this is a frequency the server accepts: at most three integer digits and two decimals.
 *
 * @receiver what was typed; a comma counts as a decimal point.
 * @return whether the server would accept it.
 */
private fun String.krtIsFrequency(): Boolean {
    val typed = trim().replace(',', '.')
    return typed.isNotBlank() && FREQUENCY_SHAPE.matches(typed)
}

/** Three digits before the point, at most two after — the server's own `@Digits(3, 2)`. */
private val FREQUENCY_SHAPE = Regex("""^\d{1,3}(\.\d{1,2})?$""")

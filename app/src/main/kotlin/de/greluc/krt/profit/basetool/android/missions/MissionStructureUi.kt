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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtAssocAdd
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChoiceChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSheetOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusDot
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
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
 * „+ Einheit" — the dashed action at the foot of the Einheiten list.
 *
 * Artboard 06-14 draws composing as **an action, not a form**: the tab used to open on a name
 * field, an HVU checkbox and a button, so the first thing a member met on a reading surface was
 * three controls for a write most of them may not make. The sheet it opens carries the same two
 * fields; it is simply not standing there when nobody asked for it.
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
        // „+ EINHEIT", not „+ EINHEIT HINZUFÜGEN": the plus already says „hinzufügen", and the
        // artboard writes the noun alone beside it.
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
 * Composing an Einheit: name it, mark it, add it — in a sheet.
 *
 * The same two fields the tab used to carry permanently. E7's reasoning for the rename applies
 * unchanged to the creation: an editor standing open under a list competes with that list for the
 * same surface, and has nothing to cancel.
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
                onValueChange = { v -> structure.onChange { it.copy(unitName = v) } },
                label = stringResource(R.string.mission_struct_unit_name),
                enabled = structure.enabled,
            )
            // A yes/no is not one-of-N, so it is a square checkbox. The round radio is the design
            // system's only circular element and stays reserved for a real choice — the payout
            // preference (ch. 06 artboards 3 and 10).
            KrtCheckboxRow(
                checked = structure.draft.unitHighValue,
                onCheckedChange = { v -> structure.onChange { it.copy(unitHighValue = v) } },
                label = stringResource(R.string.mission_struct_hvu),
                enabled = structure.enabled,
            )
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
 * One Einheit's header band — artboard 06-14's `card--flush` head.
 *
 * The unit's glyph, its name, what it flies, how many are aboard, and its two manager actions as
 * icon buttons above a 2 dp orange rule. The actions used to be two full-width labelled buttons
 * under the crew, which put „EINHEIT ENTFERNEN" — the destructive one — at the bottom of a list
 * of people and about 100 dp from the unit it belonged to.
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
 * One crew slot of an Einheit — artboard 06-14's inner row.
 *
 * Its own frame inside the unit's card, because a crew slot is a record: who, whether they are
 * there, and which Funktionen they hold. Taking somebody off the Einheit is the `[→` icon button
 * at the trailing edge, not a labelled button under the chips — the row repeats, and so did the
 * word.
 *
 * @param unit the Einheit the slot belongs to.
 * @param member the slot.
 * @param roster the Einsatz's roster, which is where the check-in mark and the Staffel come from:
 *   the crew row carries neither, and both are facts about the **person**.
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
    // Matched by name, which is what the wire gives: `MissionCrewMemberDto` carries the assigned
    // person's display name and no participant id. `CrewAdd` already excludes candidates the same
    // way, so the two agree — and a duplicate name would at worst borrow the wrong check-in mark,
    // never write anything.
    val person = roster.firstOrNull { it.name == member.name }
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
                    text = member.name,
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
                // „Off board", not „delete": the person stays on the Einsatz, they just leave this
                // Einheit. The artboard's glyph says exactly that.
                iconRes = if (gate.allowed) DesignR.drawable.ic_krt_logout else DesignR.drawable.ic_krt_lock,
                label = stringResource(R.string.mission_struct_remove_crew),
                onClick = click,
                modifier = dim,
                enabled = structure.enabled,
            )
        }
        CrewRoleSelect(
            unitId = unit.id,
            member = member,
            crew = unit.crew,
            structure = structure,
        )
    }
}

/** The separator between two facts on a crew row's second line. */
private const val CREW_DOT = " · "

/** Test handle for the rename sheet. */
const val MISSION_UNIT_RENAME_TAG: String = "mission-unit-rename"

/**
 * Renaming an Einheit — design ch. 18 §3 (E7).
 *
 * **A sheet with one field**, the current name filled in, and „Speichern" dimmed until it actually
 * differs. Not an inline field in the row's header: a header that turns into an input has no way to
 * be cancelled, and the member is left editing something they only meant to read.
 *
 * The HVU mark is **not** in here even though the same call carries it. One field is the point, so
 * the write echoes the mark back as it stands; changing it stays where it is set.
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
                onValueChange = { v -> structure.onChange { it.copy(unitName = v) } },
                label = stringResource(R.string.mission_struct_unit_name),
                enabled = structure.enabled,
            )
            KrtCtaButton(
                text = stringResource(R.string.mission_unit_rename_save),
                onClick = { structure.onSaveUnit(editing, structure.draft.editingUnitVersion) },
                modifier = Modifier.fillMaxWidth(),
                // Dimmed until the name differs: a save that writes the value it already holds
                // costs a round trip and a version bump for nothing, and reads as if it failed.
                enabled = structure.enabled && !unchanged && typed.isNotBlank(),
            )
        }
    }
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
 * Composing a frequency — two fields, in a sheet.
 *
 * The Frequenzen tab is a **reading** surface: on the evening of an Einsatz it is opened to copy a
 * number, and it used to open on two empty inputs above the numbers. Same move as the Einheit's
 * composer, and the same reason (design ch. 18 §3, E7).
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
            KrtCtaButton(
                text = stringResource(R.string.mission_struct_add_freq),
                onClick = structure.onAddFrequency,
                modifier = Modifier.fillMaxWidth(),
                enabled = structure.enabled && structure.draft.freqValue.isNotBlank(),
            )
        }
    }
}

/**
 * Where a structure write's refusal is said out loud.
 *
 * `MissionStructureDraft.error` was set on every failed Einheit, crew and frequency write and
 * **rendered nowhere**: a 403, a 409 or a dropped connection left the sheet standing open with the
 * button still lit, which reads as a tap that did not register. Found on the device — adding a
 * frequency did nothing, twice, with nothing on screen and nothing in the log.
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
private fun missionManagerGate(canManage: Boolean): Gate =
    Gate(
        allowed = canManage,
        reason = stringResource(R.string.gate_role_mission_manager),
        detail = stringResource(R.string.gate_role_mission_manager_detail),
    )

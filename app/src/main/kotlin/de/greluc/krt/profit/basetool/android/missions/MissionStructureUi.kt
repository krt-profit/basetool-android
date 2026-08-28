/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

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
    val onRemoveCrew: (String, String) -> Unit,
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
    val (dim, click) = rememberGated(gate, structure.onAddUnit, structure.denials)
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtTextField(
            value = structure.draft.unitName,
            onValueChange = { v -> structure.onChange { it.copy(unitName = v) } },
            label = stringResource(R.string.mission_struct_unit_name),
            enabled = structure.enabled && gate.allowed,
        )
        KrtRadioRow(
            selected = structure.draft.unitHighValue,
            onSelect = { structure.onChange { it.copy(unitHighValue = !it.unitHighValue) } },
            label = stringResource(R.string.mission_struct_hvu),
            enabled = structure.enabled && gate.allowed,
        )
        KrtGhostButton(
            text = stringResource(R.string.mission_struct_add_unit),
            onClick = click,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = dim.testTag(MISSION_UNIT_ADD_TAG),
            enabled = structure.enabled,
        )
    }
}

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

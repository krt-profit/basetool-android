/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionObjectiveKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Ablauf composer's save. */
const val MISSION_STEP_SAVE_TAG: String = "mission-step-save"

/** Test handle for the Ziele composer's save. */
const val MISSION_OBJECTIVE_SAVE_TAG: String = "mission-objective-save"

/**
 * What a manager may do to the Ablauf and the Ziele, and what to say when they may not.
 *
 * One record for both tabs: they share a draft, a gate and a refusal slot, and splitting them would
 * only mean threading two nearly identical objects through the same screen.
 *
 * > **The write half of these two tabs has no artboard.** Chapter 06 draws both as reading
 * > surfaces — the numbered checklist with its current-phase mark, and the Ziele with their kind.
 * > The editors below are composed from drawn parts and their composition is **unratified**; round
 * > 11 asks for the drawing.
 *
 * @property canManage whether the caller may write at all; the server's own verdict.
 * @property enabled whether a write may run right now — online, and nothing already in flight.
 * @property draft what is typed.
 * @property denials where a refused tap is announced.
 * @property onChange a field changed.
 * @property onSaveStep append or rewrite the composed step.
 * @property onEditStep load a step into the editor.
 * @property onToggleStep tick a step off, or back on.
 * @property onRemoveStep drop a step.
 * @property onMoveStep move a step one place; `true` is towards the start.
 * @property onSaveObjective append or rewrite the composed Ziel.
 * @property onEditObjective load a Ziel into the editor.
 * @property onRemoveObjective drop a Ziel.
 * @property onMoveObjective move a Ziel one place; `true` is towards the start.
 * @property onCancel abandon whichever editor is open.
 */
data class MissionTimelineActions(
    val canManage: Boolean,
    val enabled: Boolean,
    val draft: MissionTimelineDraft,
    val denials: DenialState,
    val onChange: ((MissionTimelineDraft) -> MissionTimelineDraft) -> Unit,
    val onSaveStep: () -> Unit,
    val onEditStep: (MissionStepEdit) -> Unit,
    val onToggleStep: (String, Boolean) -> Unit,
    val onRemoveStep: (String) -> Unit,
    val onMoveStep: (String, Boolean) -> Unit,
    val onSaveObjective: () -> Unit,
    val onEditObjective: (MissionObjectiveEdit) -> Unit,
    val onRemoveObjective: (String) -> Unit,
    val onMoveObjective: (String, Boolean) -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The Ablauf tab's editor: a title, the line beneath it, and one save.
 *
 * The same control appends and rewrites — `editingStepId` decides which, and the button says so.
 * Two separate editors would be two places to keep the same three fields.
 *
 * @param timeline the actions and what is typed.
 */
@Composable
fun StepComposer(timeline: MissionTimelineActions) {
    val gate = missionTimelineGate(timeline.canManage)
    val (dim, click) = rememberGated(gate, timeline.onSaveStep, timeline.denials)
    val editing = timeline.draft.editingStepId != null
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtSectionTitle(
            text =
                stringResource(
                    if (editing) R.string.mission_step_editing else R.string.mission_step_new,
                ),
        )
        KrtTextField(
            value = timeline.draft.stepTitle,
            onValueChange = { v -> timeline.onChange { it.copy(stepTitle = v) } },
            label = stringResource(R.string.mission_step_title),
            enabled = timeline.enabled && gate.allowed,
        )
        KrtTextField(
            value = timeline.draft.stepMeta,
            onValueChange = { v -> timeline.onChange { it.copy(stepMeta = v) } },
            label = stringResource(R.string.mission_step_meta),
            enabled = timeline.enabled && gate.allowed,
        )
        KrtGhostButton(
            text =
                stringResource(
                    if (editing) R.string.mission_step_save_edit else R.string.mission_step_add,
                ),
            onClick = click,
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_check else DesignR.drawable.ic_krt_lock,
            modifier = dim.fillMaxWidth().testTag(MISSION_STEP_SAVE_TAG),
            enabled = timeline.enabled,
        )
        if (editing) {
            KrtGhostButton(
                text = stringResource(R.string.mission_timeline_cancel),
                onClick = timeline.onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = timeline.enabled,
            )
        }
    }
}

/**
 * The Ziele tab's editor: a title, the three kinds, and one save.
 *
 * @param timeline the actions and what is typed.
 */
@Composable
fun ObjectiveComposer(timeline: MissionTimelineActions) {
    val gate = missionTimelineGate(timeline.canManage)
    val (dim, click) = rememberGated(gate, timeline.onSaveObjective, timeline.denials)
    val editing = timeline.draft.editingObjectiveId != null
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtSectionTitle(
            text =
                stringResource(
                    if (editing) R.string.mission_objective_editing else R.string.mission_objective_new,
                ),
        )
        KrtTextField(
            value = timeline.draft.objectiveTitle,
            onValueChange = { v -> timeline.onChange { it.copy(objectiveTitle = v) } },
            label = stringResource(R.string.mission_objective_title),
            enabled = timeline.enabled && gate.allowed,
        )
        // Chips rather than free text: the server's enum has exactly three values, and a field that
        // accepts a fourth would only ever produce a 400 the member cannot act on.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            MissionObjectiveKind.entries.forEach { kind ->
                KrtFilterChip(
                    text = stringResource(kind.labelRes()),
                    selected = timeline.draft.objectiveKind == kind,
                    onClick = { timeline.onChange { it.copy(objectiveKind = kind) } },
                    enabled = timeline.enabled && gate.allowed,
                )
            }
        }
        KrtGhostButton(
            text =
                stringResource(
                    if (editing) R.string.mission_objective_save_edit else R.string.mission_objective_add,
                ),
            onClick = click,
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_check else DesignR.drawable.ic_krt_lock,
            modifier = dim.fillMaxWidth().testTag(MISSION_OBJECTIVE_SAVE_TAG),
            enabled = timeline.enabled,
        )
        if (editing) {
            KrtGhostButton(
                text = stringResource(R.string.mission_timeline_cancel),
                onClick = timeline.onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = timeline.enabled,
            )
        }
    }
}

/**
 * Moving one row of an ordered list, up or down.
 *
 * Two buttons rather than a drag: the reorder endpoint wants the whole id list in its new order,
 * and a move produces that just as well without inventing a gesture no artboard draws. Round 11
 * asks whether it should become a drag.
 *
 * @param onMove the move; `true` is towards the start.
 * @param gate whether the caller may reorder at all.
 * @param denials where a refused tap is announced.
 * @param enabled whether a write may run right now.
 */
@Composable
private fun RowScope.MoveActions(
    onMove: (Boolean) -> Unit,
    gate: Gate,
    denials: DenialState,
    enabled: Boolean,
) {
    val (upDim, up) = rememberGated(gate, { onMove(true) }, denials)
    val (downDim, down) = rememberGated(gate, { onMove(false) }, denials)
    KrtGhostButton(
        text = stringResource(R.string.mission_timeline_up),
        onClick = up,
        iconRes = if (gate.allowed) DesignR.drawable.ic_krt_chevron_up else DesignR.drawable.ic_krt_lock,
        modifier = upDim.weight(1f),
        enabled = enabled,
    )
    KrtGhostButton(
        text = stringResource(R.string.mission_timeline_down),
        onClick = down,
        iconRes = if (gate.allowed) DesignR.drawable.ic_krt_chevron_down else DesignR.drawable.ic_krt_lock,
        modifier = downDim.weight(1f),
        enabled = enabled,
    )
}

/**
 * The per-row actions of one Ablauf step: tick, rewrite, drop, and move.
 *
 * **At most two buttons to a row.** Three German labels across a phone's width put „ABHAKEN" on two
 * lines on a 411 dp device, measured — the same collapse the sign-up bar produced with three items
 * in a weightless row, and the same fix. Ticking gets a row of its own because on a checklist it is
 * the action the row exists for; „ZURÜCKSETZEN" is twelve characters and would not fit beside
 * anything else in any case.
 *
 * @param step the row.
 * @param done whether it is ticked off, which decides what the first button says.
 * @param timeline the actions, for the gate and the refusal slot.
 */
@Composable
fun StepRowActions(
    step: MissionStepEdit,
    done: Boolean,
    timeline: MissionTimelineActions,
) {
    val gate = missionTimelineGate(timeline.canManage)
    val (tickDim, tick) = rememberGated(gate, { timeline.onToggleStep(step.id, !done) }, timeline.denials)
    KrtGhostButton(
        text =
            stringResource(
                if (done) R.string.mission_step_untick else R.string.mission_step_tick,
            ),
        onClick = tick,
        iconRes = if (gate.allowed) DesignR.drawable.ic_krt_check else DesignR.drawable.ic_krt_lock,
        modifier = tickDim.fillMaxWidth(),
        enabled = timeline.enabled,
    )
    EditAndRemove(
        onEdit = { timeline.onEditStep(step) },
        onRemove = { timeline.onRemoveStep(step.id) },
        gate = gate,
        timeline = timeline,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoveActions(
            onMove = { up -> timeline.onMoveStep(step.id, up) },
            gate = gate,
            denials = timeline.denials,
            enabled = timeline.enabled,
        )
    }
}

/**
 * The pair every ordered row carries: rewrite it, or drop it.
 *
 * @param onEdit load the row into the editor.
 * @param onRemove drop the row.
 * @param gate whether the caller may write at all.
 * @param timeline the actions, for the refusal slot and the in-flight state.
 */
@Composable
private fun EditAndRemove(
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    gate: Gate,
    timeline: MissionTimelineActions,
) {
    val (editDim, edit) = rememberGated(gate, onEdit, timeline.denials)
    val (dropDim, drop) = rememberGated(gate, onRemove, timeline.denials)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtGhostButton(
            text = stringResource(R.string.mission_timeline_edit),
            onClick = edit,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = editDim.weight(1f),
            enabled = timeline.enabled,
        )
        KrtGhostButton(
            text = stringResource(R.string.mission_timeline_remove),
            onClick = drop,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = dropDim.weight(1f),
            enabled = timeline.enabled,
        )
    }
}

/**
 * The per-row actions of one Ziel: rewrite, drop and move.
 *
 * @param objective the row.
 * @param timeline the actions, for the gate and the refusal slot.
 */
@Composable
fun ObjectiveRowActions(
    objective: MissionObjectiveEdit,
    timeline: MissionTimelineActions,
) {
    val gate = missionTimelineGate(timeline.canManage)
    EditAndRemove(
        onEdit = { timeline.onEditObjective(objective) },
        onRemove = { timeline.onRemoveObjective(objective.id) },
        gate = gate,
        timeline = timeline,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoveActions(
            onMove = { up -> timeline.onMoveObjective(objective.id, up) },
            gate = gate,
            denials = timeline.denials,
            enabled = timeline.enabled,
        )
    }
}

/**
 * What a Ziel's kind is called on screen.
 *
 * @return the string resource.
 */
fun MissionObjectiveKind.labelRes(): Int =
    when (this) {
        MissionObjectiveKind.PRIMARY -> R.string.mission_objective_kind_primary
        MissionObjectiveKind.SECONDARY -> R.string.mission_objective_kind_secondary
        MissionObjectiveKind.NON_GOAL -> R.string.mission_objective_kind_non_goal
    }

/**
 * What the server's raw kind string reads as on screen.
 *
 * A kind this build knows gets its own label; anything else is returned unchanged, because a goal
 * marked with an unfamiliar word is better than one marked with nothing.
 *
 * @return the label, or the raw value.
 */
@Composable
fun String.kindLabel(): String =
    MissionObjectiveKind.entries.firstOrNull { it.wire == this }
        ?.let { stringResource(it.labelRes()) }
        ?: this

/**
 * Resolves the server's raw kind string back to the enum a write sends.
 *
 * An unrecognised kind falls back to `PRIMARY` for the **editor only** — the read side keeps
 * showing the raw string, because hiding a goal is worse than showing an unfamiliar label.
 *
 * @return the kind the editor opens with.
 */
fun String?.toObjectiveKind(): MissionObjectiveKind =
    MissionObjectiveKind.entries.firstOrNull { it.wire == this } ?: MissionObjectiveKind.PRIMARY

/**
 * The gate every Ablauf and Ziele control shares.
 *
 * @param canManage the server's verdict.
 * @return the gate, with the role it names.
 */
@Composable
private fun missionTimelineGate(canManage: Boolean): Gate =
    Gate(
        allowed = canManage,
        reason = stringResource(R.string.gate_role_mission_manager),
        detail = stringResource(R.string.gate_role_mission_manager_detail),
    )

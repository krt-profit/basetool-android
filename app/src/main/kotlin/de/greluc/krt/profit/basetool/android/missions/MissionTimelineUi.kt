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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionObjectiveKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Ablauf editor's save. */
const val MISSION_STEP_SAVE_TAG: String = "mission-step-save"

/** Test handle for the Ziele editor's save. */
const val MISSION_OBJECTIVE_SAVE_TAG: String = "mission-objective-save"

/** Test handle for the „+ Schritt" action that opens the editor. */
const val MISSION_STEP_ADD_TAG: String = "mission-step-add"

/** Test handle for the „+ Ziel" action that opens the editor. */
const val MISSION_OBJECTIVE_ADD_TAG: String = "mission-objective-add"

/** Test handle for the reorder mode toggle — the click fallback for the drag handle. */
const val MISSION_SORT_TAG: String = "mission-timeline-sort"

/**
 * What a manager may do to the Ablauf and the Ziele, and what to say when they may not.
 *
 * One record for both tabs: they share a draft, a gate and a refusal slot, and splitting them would
 * only mean threading two nearly identical objects through the same screen.
 *
 * **Composition ratified 2026-08-29** (design ch. 06 artboard 13): three 44 dp icon buttons per
 * row — tick · edit · remove — instead of the three rows of German-labelled buttons this shipped
 * with; and the editor as a **sheet**, because an open form under a list somebody is sorting
 * competes with it for the same surface.
 *
 * @property canManage whether the caller may write at all; the server's own verdict.
 * @property enabled whether a write may run right now — online, and nothing already in flight.
 * @property draft what is typed.
 * @property sorting whether the reorder mode is on — the click fallback for the drag handle.
 * @property denials where a refused tap is announced.
 * @property onChange a field changed.
 * @property onCompose open the editor for a new row.
 * @property onSaveStep append or rewrite the composed step.
 * @property onEditStep load a step into the editor.
 * @property onToggleStep tick a step off, or back on.
 * @property onRemoveStep drop a step.
 * @property onMoveStep move a step one place; `true` is towards the start.
 * @property onSaveObjective append or rewrite the composed Ziel.
 * @property onEditObjective load a Ziel into the editor.
 * @property onRemoveObjective drop a Ziel.
 * @property onMoveObjective move a Ziel one place; `true` is towards the start.
 * @property onToggleSorting turn the reorder mode on or off.
 * @property onCancel close the editor without saving.
 */
data class MissionTimelineActions(
    val canManage: Boolean,
    val enabled: Boolean,
    val draft: MissionTimelineDraft,
    val sorting: Boolean,
    val denials: DenialState,
    val onChange: ((MissionTimelineDraft) -> MissionTimelineDraft) -> Unit,
    val onCompose: (Boolean) -> Unit,
    val onSaveStep: () -> Unit,
    val onEditStep: (MissionStepEdit) -> Unit,
    val onToggleStep: (String, Boolean) -> Unit,
    val onRemoveStep: (String) -> Unit,
    val onMoveStep: (String, Boolean) -> Unit,
    val onSaveObjective: () -> Unit,
    val onEditObjective: (MissionObjectiveEdit) -> Unit,
    val onRemoveObjective: (String) -> Unit,
    val onMoveObjective: (String, Boolean) -> Unit,
    val onToggleSorting: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The two actions above an ordered list: add a row, and turn reordering on.
 *
 * Design ch. 06 artboard 13 puts „Sortieren" and „+ Schritt" at the foot of the list rather than an
 * always-open form above it. The sort toggle is the **click fallback** the design system requires
 * beside any drag: „Reihenfolge ändern: Griff halten und ziehen." is the gesture, and this is the
 * way that works without one.
 *
 * @param addLabelRes what the add action says.
 * @param addTag its test handle.
 * @param timeline the actions, for the gate and the refusal slot.
 */
@Composable
fun TimelineListActions(
    addLabelRes: Int,
    addTag: String,
    timeline: MissionTimelineActions,
) {
    val gate = missionTimelineGate(timeline.canManage)
    val (sortDim, sort) = rememberGated(gate, timeline.onToggleSorting, timeline.denials)
    val (addDim, add) = rememberGated(gate, { timeline.onCompose(true) }, timeline.denials)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtGhostButton(
            text = stringResource(R.string.mission_timeline_sort),
            onClick = sort,
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_grip else DesignR.drawable.ic_krt_lock,
            modifier = sortDim.weight(1f).testTag(MISSION_SORT_TAG),
            enabled = timeline.enabled,
        )
        KrtGhostButton(
            text = stringResource(addLabelRes),
            onClick = add,
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_plus else DesignR.drawable.ic_krt_lock,
            modifier = addDim.weight(1f).testTag(addTag),
            enabled = timeline.enabled,
        )
    }
    if (timeline.sorting) {
        Text(
            text = stringResource(R.string.mission_timeline_sort_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The per-row actions of one Ablauf step: tick, rewrite, remove — three icon buttons, one row.
 *
 * Design ch. 06 artboard 13. This shipped as three stacked rows of German-labelled buttons —
 * about 150 dp of chrome per checklist line — because three labels do not fit a 411 dp row. The
 * design system's answer to exactly that case is the icon button (`.btn-icon`), and it carries its
 * own contract: 44 dp, **always** a content description and a tooltip, and only for a repeated row
 * action whose meaning is universal.
 *
 * Ticking is the action the row exists for, so it comes first; once ticked it becomes the undo.
 *
 * @param step the row.
 * @param done whether it is ticked off.
 * @param timeline the actions, for the gate and the refusal slot.
 */
@Composable
fun StepRowActions(
    step: MissionStepEdit,
    done: Boolean,
    timeline: MissionTimelineActions,
) {
    RowActions(
        timeline = timeline,
        firstIcon = if (done) DesignR.drawable.ic_krt_reset else DesignR.drawable.ic_krt_check,
        firstLabelRes = if (done) R.string.mission_step_untick else R.string.mission_step_tick,
        onFirst = { timeline.onToggleStep(step.id, !done) },
        onEdit = { timeline.onEditStep(step) },
        onRemove = { timeline.onRemoveStep(step.id) },
        onMove = { up -> timeline.onMoveStep(step.id, up) },
    )
}

/**
 * The per-row actions of one Ziel: rewrite and remove.
 *
 * A Ziel has nothing to tick, so the first slot is absent rather than filled with something else.
 *
 * @param objective the row.
 * @param timeline the actions, for the gate and the refusal slot.
 */
@Composable
fun ObjectiveRowActions(
    objective: MissionObjectiveEdit,
    timeline: MissionTimelineActions,
) {
    RowActions(
        timeline = timeline,
        firstIcon = null,
        firstLabelRes = null,
        onFirst = {},
        onEdit = { timeline.onEditObjective(objective) },
        onRemove = { timeline.onRemoveObjective(objective.id) },
        onMove = { up -> timeline.onMoveObjective(objective.id, up) },
    )
}

/**
 * The shared icon-button row behind both list kinds.
 *
 * The move pair appears **only** in reorder mode: the design draws the order as a drag handle with
 * a click fallback, and two permanent arrows on every row is the chrome this whole correction was
 * about.
 *
 * @param timeline the actions, for the gate and the refusal slot.
 * @param firstIcon the row's own primary action, or `null` when it has none.
 * @param firstLabelRes that action's label — mandatory whenever [firstIcon] is present, because an
 *   icon button without a name is unusable to a screen reader and unlabelled on long press.
 * @param onFirst runs it.
 * @param onEdit loads the row into the editor.
 * @param onRemove drops the row.
 * @param onMove moves it one place; `true` is towards the start.
 */
@Composable
private fun RowActions(
    timeline: MissionTimelineActions,
    firstIcon: Int?,
    firstLabelRes: Int?,
    onFirst: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    val gate = missionTimelineGate(timeline.canManage)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (firstIcon != null && firstLabelRes != null) {
            GatedIcon(firstIcon, firstLabelRes, gate, timeline, onFirst)
        }
        GatedIcon(DesignR.drawable.ic_krt_edit, R.string.mission_timeline_edit, gate, timeline, onEdit)
        GatedIcon(DesignR.drawable.ic_krt_trash, R.string.mission_timeline_remove, gate, timeline, onRemove)
        if (timeline.sorting) {
            GatedIcon(
                DesignR.drawable.ic_krt_chevron_up,
                R.string.mission_timeline_up,
                gate,
                timeline,
            ) { onMove(true) }
            GatedIcon(
                DesignR.drawable.ic_krt_chevron_down,
                R.string.mission_timeline_down,
                gate,
                timeline,
            ) { onMove(false) }
        }
    }
}

/**
 * One icon button, dimmed and locked when the caller may not use it.
 *
 * @param iconRes the glyph.
 * @param labelRes its name — the content description and the tooltip, both mandatory.
 * @param gate whether the caller may act.
 * @param timeline the actions, for the refusal slot and the in-flight state.
 * @param onClick what it does when allowed.
 */
@Composable
private fun GatedIcon(
    iconRes: Int,
    labelRes: Int,
    gate: Gate,
    timeline: MissionTimelineActions,
    onClick: () -> Unit,
) {
    val (dim, click) = rememberGated(gate, onClick, timeline.denials)
    KrtIconButton(
        iconRes = if (gate.allowed) iconRes else DesignR.drawable.ic_krt_lock,
        label = stringResource(labelRes),
        onClick = click,
        modifier = dim,
        enabled = timeline.enabled,
    )
}

/**
 * The Ablauf editor, as a sheet.
 *
 * Design ch. 06 artboard 13: „Anlegen/Bearbeiten NICHT als Dauer-Formular unter der Liste, sondern
 * als Sheet" — an open editor under a list somebody is sorting competes with it for the same
 * surface. The same control appends and rewrites; `editingStepId` decides which, and the title
 * says so.
 *
 * @param timeline the actions and what is typed.
 */
@Composable
fun StepEditorSheet(timeline: MissionTimelineActions) {
    val editing = timeline.draft.editingStepId != null
    KrtBottomSheet(
        onDismiss = timeline.onCancel,
        title =
            stringResource(if (editing) R.string.mission_step_editing else R.string.mission_step_new),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtTextField(
                value = timeline.draft.stepTitle,
                onValueChange = { v -> timeline.onChange { it.copy(stepTitle = v) } },
                label = stringResource(R.string.mission_step_title),
                enabled = timeline.enabled,
            )
            KrtTextField(
                value = timeline.draft.stepMeta,
                onValueChange = { v -> timeline.onChange { it.copy(stepMeta = v) } },
                label = stringResource(R.string.mission_step_meta),
                enabled = timeline.enabled,
            )
            KrtCtaButton(
                text =
                    stringResource(
                        if (editing) R.string.mission_step_save_edit else R.string.mission_step_add,
                    ),
                onClick = timeline.onSaveStep,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(MISSION_STEP_SAVE_TAG),
                enabled = timeline.enabled,
            )
            timeline.draft.error?.let { SignUpError(error = it) }
        }
    }
}

/**
 * The Ziele editor, as a sheet — the same shape, plus the three kinds.
 *
 * @param timeline the actions and what is typed.
 */
@Composable
fun ObjectiveEditorSheet(timeline: MissionTimelineActions) {
    val editing = timeline.draft.editingObjectiveId != null
    KrtBottomSheet(
        onDismiss = timeline.onCancel,
        title =
            stringResource(
                if (editing) R.string.mission_objective_editing else R.string.mission_objective_new,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtTextField(
                value = timeline.draft.objectiveTitle,
                onValueChange = { v -> timeline.onChange { it.copy(objectiveTitle = v) } },
                label = stringResource(R.string.mission_objective_title),
                enabled = timeline.enabled,
            )
            // Chips rather than free text: the server's enum has exactly three values, and a field
            // that accepts a fourth would only ever produce a 400 the member cannot act on. German
            // labels, never the wire constant (artboard 13).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                MissionObjectiveKind.entries.forEach { kind ->
                    KrtFilterChip(
                        text = stringResource(kind.labelRes()),
                        selected = timeline.draft.objectiveKind == kind,
                        onClick = { timeline.onChange { it.copy(objectiveKind = kind) } },
                        enabled = timeline.enabled,
                    )
                }
            }
            KrtCtaButton(
                text =
                    stringResource(
                        if (editing) {
                            R.string.mission_objective_save_edit
                        } else {
                            R.string.mission_objective_add
                        },
                    ),
                onClick = timeline.onSaveObjective,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(MISSION_OBJECTIVE_SAVE_TAG),
                enabled = timeline.enabled,
            )
            timeline.draft.error?.let { SignUpError(error = it) }
        }
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
 * showing its own label or the raw string, because hiding a goal is worse than showing an
 * unfamiliar one.
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

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionObjectiveKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
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
 * **Composition ratified 2026-08-29** (design ch. 06 artboard 13) and **again on 2026-08-30**
 * (ch. 18 §3, E5/E8). The row shipped with three rows of German-labelled buttons, then with three
 * icon buttons and a separate reorder *mode*. Five actions do not fit a 411 dp row, so it now
 * carries the two move buttons **visibly** — dimmed at the first and last row, which is validation
 * and not a lock — and a `⋮` for the rest: Bearbeiten · Duplizieren · Löschen.
 *
 * The reorder mode is gone with it. Drag and drop in a scrolling list without a grip is unreliable
 * on a phone, and the system asks for a tap alternative beside every drag anyway — so the pair of
 * arrows **is** the mechanism rather than a fallback behind a toggle. Swipe stays out: the inbox
 * has it bound to delete, and a second vocabulary for the same gesture is worse than none.
 *
 * @property canManage whether the caller may write at all; the server's own verdict.
 * @property enabled whether a write may run right now — online, and nothing already in flight.
 * @property draft what is typed.
 * @property denials where a refused tap is announced.
 * @property onChange a field changed.
 * @property onCompose open the editor for a new row.
 * @property onSaveStep append or rewrite the composed step.
 * @property onEditStep load a step into the editor.
 * @property onToggleStep tick a step off, or back on.
 * @property onRemoveStep drop a step.
 * @property onMoveStep move a step one place; `true` is towards the start.
 * @property onDuplicateStep append a copy of a step, which is what „Duplizieren" writes.
 * @property onSaveObjective append or rewrite the composed Ziel.
 * @property onEditObjective load a Ziel into the editor.
 * @property onRemoveObjective drop a Ziel.
 * @property onMoveObjective move a Ziel one place; `true` is towards the start.
 * @property onDuplicateObjective append a copy of a Ziel.
 * @property onCancel close the editor without saving.
 */
data class MissionTimelineActions(
    val canManage: Boolean,
    val enabled: Boolean,
    val draft: MissionTimelineDraft,
    val denials: DenialState,
    val onChange: ((MissionTimelineDraft) -> MissionTimelineDraft) -> Unit,
    val onCompose: (Boolean) -> Unit,
    val onSaveStep: () -> Unit,
    val onEditStep: (MissionStepEdit) -> Unit,
    val onToggleStep: (String, Boolean) -> Unit,
    val onRemoveStep: (String) -> Unit,
    val onMoveStep: (String, Boolean) -> Unit,
    val onDuplicateStep: (MissionStepEdit) -> Unit,
    val onSaveObjective: () -> Unit,
    val onEditObjective: (MissionObjectiveEdit) -> Unit,
    val onRemoveObjective: (String) -> Unit,
    val onMoveObjective: (String, Boolean) -> Unit,
    val onDuplicateObjective: (MissionObjectiveEdit) -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The one action at the foot of an ordered list: add a row.
 *
 * „Sortieren" used to sit beside it as the click fallback for a drag handle. Design ch. 18 §3 (E8)
 * ratified the two per-row arrows as the mechanism instead, so there is no mode left to toggle and
 * no hint to explain one.
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
    val (addDim, add) = rememberGated(gate, { timeline.onCompose(true) }, timeline.denials)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtGhostButton(
            text = stringResource(addLabelRes),
            onClick = add,
            iconRes = if (gate.allowed) DesignR.drawable.ic_krt_plus else DesignR.drawable.ic_krt_lock,
            modifier = addDim.fillMaxWidth().testTag(addTag),
            enabled = timeline.enabled,
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
    position: RowPosition,
) {
    RowActions(
        timeline = timeline,
        position = position,
        firstIcon = if (done) DesignR.drawable.ic_krt_reset else DesignR.drawable.ic_krt_check,
        firstLabelRes = if (done) R.string.mission_step_untick else R.string.mission_step_tick,
        onFirst = { timeline.onToggleStep(step.id, !done) },
        onEdit = { timeline.onEditStep(step) },
        onDuplicate = { timeline.onDuplicateStep(step) },
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
    position: RowPosition,
) {
    RowActions(
        timeline = timeline,
        position = position,
        firstIcon = null,
        firstLabelRes = null,
        onFirst = {},
        onEdit = { timeline.onEditObjective(objective) },
        onDuplicate = { timeline.onDuplicateObjective(objective) },
        onRemove = { timeline.onRemoveObjective(objective.id) },
        onMove = { up -> timeline.onMoveObjective(objective.id, up) },
    )
}

/**
 * Where a row sits in its list, which is all the move buttons need to know.
 *
 * @property first whether it is the first row, so „nach oben" has nowhere to go.
 * @property last whether it is the last.
 */
data class RowPosition(
    val first: Boolean,
    val last: Boolean,
)

/**
 * The shared action row behind both list kinds — design ch. 18 §3 (E5/E8).
 *
 * Two visible move buttons and one `⋮`. The arrows are **dimmed at the ends of the list**, which is
 * validation rather than a lock: nothing is being refused, there is simply nowhere to move to, so
 * they carry no lock glyph and raise no refusal.
 *
 * The group is **as wide as its buttons** and nothing more. Artboard 13 draws it at the trailing
 * edge of the row the actions belong to — „die Zeile bleibt EINE Zeile hoch" — so it must not claim
 * a width of its own and push itself onto a line below the title.
 *
 * @param timeline the actions, for the gate and the refusal slot.
 * @param position where the row sits, which decides whether each arrow has anywhere to go.
 * @param firstIcon the row's own primary action, or `null` when it has none.
 * @param firstLabelRes that action's label — mandatory whenever [firstIcon] is present, because an
 *   icon button without a name is unusable to a screen reader and unlabelled on long press.
 * @param onFirst runs it.
 * @param onEdit loads the row into the editor.
 * @param onDuplicate appends a copy of it.
 * @param onRemove drops the row.
 * @param onMove moves it one place; `true` is towards the start.
 */
@Composable
private fun RowActions(
    timeline: MissionTimelineActions,
    position: RowPosition,
    firstIcon: Int?,
    firstLabelRes: Int?,
    onFirst: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    val gate = missionTimelineGate(timeline.canManage)
    var open by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (firstIcon != null && firstLabelRes != null) {
            GatedIcon(firstIcon, firstLabelRes, gate, timeline, onFirst)
        }
        MoveIcon(
            iconRes = DesignR.drawable.ic_krt_chevron_up,
            labelRes = R.string.mission_timeline_up,
            gate = gate,
            timeline = timeline,
            possible = !position.first,
        ) { onMove(true) }
        MoveIcon(
            iconRes = DesignR.drawable.ic_krt_chevron_down,
            labelRes = R.string.mission_timeline_down,
            gate = gate,
            timeline = timeline,
            possible = !position.last,
        ) { onMove(false) }
        KrtOverflowMenu(
            contentDescription = stringResource(R.string.mission_timeline_more),
            expanded = open,
            onExpandedChange = { open = it },
            items =
                listOf(
                    menuItem(R.string.mission_timeline_edit, DesignR.drawable.ic_krt_edit, gate) {
                        open = false
                        onEdit()
                    },
                    menuItem(R.string.mission_timeline_duplicate, DesignR.drawable.ic_krt_plus, gate) {
                        open = false
                        onDuplicate()
                    },
                    menuItem(
                        R.string.mission_timeline_remove,
                        DesignR.drawable.ic_krt_trash,
                        gate,
                        danger = true,
                    ) {
                        open = false
                        onRemove()
                    },
                ),
        )
    }
}

/**
 * One entry of the row's overflow, locked rather than hidden when the caller may not use it.
 *
 * @param labelRes what it says.
 * @param iconRes its glyph.
 * @param gate whether the caller may act.
 * @param danger whether it is the destructive one.
 * @param onClick what it does.
 * @return the menu entry.
 */
@Composable
private fun menuItem(
    labelRes: Int,
    iconRes: Int,
    gate: Gate,
    danger: Boolean = false,
    onClick: () -> Unit,
): KrtMenuItem =
    KrtMenuItem(
        label = stringResource(labelRes),
        iconRes = iconRes,
        danger = danger,
        reason = gate.reason.takeIf { !gate.allowed },
        locked = !gate.allowed,
        onClick = onClick,
    )

/**
 * One move button: narrower than a square icon button, and dimmed where it has nowhere to go.
 *
 * A row at the end of its list is not being **refused** anything, so the arrow neither wears the
 * lock nor raises a refusal — it is simply inactive. That is the difference between validation and
 * a permission, and drawing them the same way is what makes a lock stop meaning anything.
 *
 * @param iconRes the chevron.
 * @param labelRes its name.
 * @param gate whether the caller may reorder at all.
 * @param timeline the actions, for the refusal slot and the in-flight state.
 * @param possible whether there is anywhere to move to.
 * @param onClick moves it.
 */
@Composable
private fun MoveIcon(
    iconRes: Int,
    labelRes: Int,
    gate: Gate,
    timeline: MissionTimelineActions,
    possible: Boolean,
    onClick: () -> Unit,
) {
    val (dim, click) = rememberGated(gate, onClick, timeline.denials)
    KrtIconButton(
        iconRes = if (gate.allowed) iconRes else DesignR.drawable.ic_krt_lock,
        label = stringResource(labelRes),
        onClick = click,
        modifier = dim,
        enabled = timeline.enabled && possible,
        width = MOVE_BUTTON_WIDTH,
        height = KrtSpacing.touchTarget,
    )
}

/** The reorder pair is 40 dp wide against the 44 dp floor, so the pair plus a `⋮` fits a phone row. */
private val MOVE_BUTTON_WIDTH = 40.dp

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
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
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
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            KrtTextField(
                value = timeline.draft.objectiveTitle,
                onValueChange = { v -> timeline.onChange { it.copy(objectiveTitle = v) } },
                label = stringResource(R.string.mission_objective_title),
                enabled = timeline.enabled,
            )
            // Chips rather than free text: the server's enum has exactly three values, and a field
            // that accepts a fourth would only ever produce a 400 the member cannot act on. German
            // labels, never the wire constant (artboard 13).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
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
 * The hue a Ziel's kind chip carries.
 *
 * Artboard 06-2 draws a Primärziel in the brand tone and everything else muted, which is the whole
 * point of classifying them: on a list of five goals the two that decide the Einsatz have to be
 * findable without reading the chips. A kind this build does not know stays muted rather than
 * borrowing the weight of a primary goal.
 *
 * @return the chip tone.
 */
fun String.kindTone(): KrtChipTone =
    if (this == MissionObjectiveKind.PRIMARY.wire) KrtChipTone.Primary else KrtChipTone.Muted

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

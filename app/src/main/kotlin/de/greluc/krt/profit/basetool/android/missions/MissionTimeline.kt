/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionObjectiveKind
import de.greluc.krt.profit.basetool.android.core.data.MissionTimelineSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Log tag for the Ablauf and the Ziele. */
private const val LOG_TAG = "MissionTimeline"

/**
 * What a manager is composing on the Ablauf or the Ziele tab; one draft serves both.
 *
 * @property stepTitle the new or edited step's title, as typed.
 * @property stepMeta its time-and-place line, as typed.
 * @property editingStepId the step being rewritten, or `null` while composing a new one.
 * @property objectiveTitle the new or edited Ziel, as typed.
 * @property objectiveKind what the Ziel is for.
 * @property editingObjectiveId the Ziel being rewritten, or `null` while composing a new one.
 * @property composing which editor sheet is open — `true` for the Ablauf, `false` for the Ziele,
 *   `null` for neither.
 * @property sorting whether the reorder mode is on.
 * @property busy whether a write is running.
 * @property error the last refusal.
 */
data class MissionTimelineDraft(
    val stepTitle: String = "",
    val stepMeta: String = "",
    val editingStepId: String? = null,
    val objectiveTitle: String = "",
    val objectiveKind: MissionObjectiveKind = MissionObjectiveKind.PRIMARY,
    val editingObjectiveId: String? = null,
    val composing: Boolean? = null,
    val sorting: Boolean = false,
    val busy: Boolean = false,
    val error: ApiError? = null,
)

/**
 * The Einsatz's Ablauf and its Ziele, as a manager writes them.
 *
 * @property missionId the Einsatz.
 * @property source where the writes go.
 * @property scope the view model's scope.
 * @property read what is typed, and the Einsatz as last read, which carries the two section
 *   counters these writes echo.
 * @property write reports the draft back, together with the Einsatz a successful write answers
 *   with.
 */
class MissionTimeline(
    private val missionId: String,
    private val source: MissionTimelineSource,
    private val scope: CoroutineScope,
    private val read: () -> Pair<MissionTimelineDraft, MissionDetail?>,
    private val write: (MissionTimelineDraft, MissionDetail?) -> Unit,
) {
    /**
     * Records a change in the draft.
     *
     * @param change what the field did to it.
     */
    fun change(change: (MissionTimelineDraft) -> MissionTimelineDraft) {
        val (draft, _) = read()
        write(change(draft), null)
    }

    /**
     * Opens the editor sheet for a **new** row.
     *
     * @param step `true` for an Ablauf step, `false` for a Ziel.
     */
    fun compose(step: Boolean) {
        val (draft, _) = read()
        write(
            draft.copy(
                composing = step,
                stepTitle = "",
                stepMeta = "",
                editingStepId = null,
                objectiveTitle = "",
                editingObjectiveId = null,
                error = null,
            ),
            null,
        )
    }

    /**
     * Loads one step into the editor sheet so it can be rewritten.
     *
     * @param step which one.
     */
    fun editStep(step: MissionStepEdit) {
        val (draft, _) = read()
        write(
            draft.copy(
                composing = true,
                stepTitle = step.title,
                stepMeta = step.meta.orEmpty(),
                editingStepId = step.id,
                error = null,
            ),
            null,
        )
    }

    /** Closes the editor sheet, keeping the Einsatz and the reorder mode untouched. */
    fun cancel() {
        val (draft, _) = read()
        write(
            draft.copy(
                composing = null,
                stepTitle = "",
                stepMeta = "",
                editingStepId = null,
                objectiveTitle = "",
                editingObjectiveId = null,
                error = null,
            ),
            null,
        )
    }

    /** Saves the composed step — appending a new one, or rewriting the one being edited. */
    fun saveStep() {
        val (draft, detail) = read()
        val title = draft.stepTitle.trim()
        if (title.isEmpty() || detail == null) {
            return
        }
        val meta = draft.stepMeta.trim().takeIf { it.isNotEmpty() }
        val editing = draft.editingStepId
        run(draft) {
            if (editing == null) {
                source.addStep(missionId, detail, title, meta)
            } else {
                source.updateStep(missionId, detail, editing, title, meta)
            }
        }
    }

    /**
     * Ticks a step off, or back on.
     *
     * @param stepId which step.
     * @param done the state it is to be in.
     */
    fun toggleStep(
        stepId: String,
        done: Boolean,
    ) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        run(draft) { source.toggleStep(missionId, detail, stepId, done) }
    }

    /**
     * Removes one step.
     *
     * @param stepId which one.
     */
    fun removeStep(stepId: String) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        run(draft) { source.removeStep(missionId, detail, stepId) }
    }

    /**
     * Appends a copy of one step with the same title and meta — „Duplizieren" (design ch. 18 §3, E5).
     *
     * The copy lands at the end of the list.
     *
     * @param step the row to copy.
     */
    fun duplicateStep(step: MissionStepEdit) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        run(draft) { source.addStep(missionId, detail, step.title, step.meta) }
    }

    /**
     * Moves one step one place up or down.
     *
     * Sends the whole id list from the Einsatz as last read; a stale counter makes the server answer
     * `409`.
     *
     * @param stepId which step.
     * @param up `true` to move it towards the start.
     */
    fun moveStep(
        stepId: String,
        up: Boolean,
    ) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        val order = detail.steps.map { it.id }.moved(stepId, up) ?: return
        run(draft) { source.reorderSteps(missionId, detail, order) }
    }

    /**
     * Appends a copy of one Ziel, the same way [duplicateStep] copies a step.
     *
     * @param objective the row to copy.
     */
    fun duplicateObjective(objective: MissionObjectiveEdit) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        run(draft) { source.addObjective(missionId, detail, objective.title, objective.kind) }
    }

    /**
     * Moves one Ziel one place up or down, under the same rule as [moveStep].
     *
     * @param objectiveId which Ziel.
     * @param up `true` to move it towards the start.
     */
    fun moveObjective(
        objectiveId: String,
        up: Boolean,
    ) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        val order = detail.objectives.map { it.id }.moved(objectiveId, up) ?: return
        run(draft) { source.reorderObjectives(missionId, detail, order) }
    }

    /**
     * Loads one Ziel into the editor sheet so it can be rewritten.
     *
     * @param objective which one.
     */
    fun editObjective(objective: MissionObjectiveEdit) {
        val (draft, _) = read()
        write(
            draft.copy(
                composing = false,
                objectiveTitle = objective.title,
                objectiveKind = objective.kind,
                editingObjectiveId = objective.id,
                error = null,
            ),
            null,
        )
    }

    /** Saves the composed Ziel — appending a new one, or rewriting the one being edited. */
    fun saveObjective() {
        val (draft, detail) = read()
        val title = draft.objectiveTitle.trim()
        if (title.isEmpty() || detail == null) {
            return
        }
        val editing = draft.editingObjectiveId
        run(draft) {
            if (editing == null) {
                source.addObjective(missionId, detail, title, draft.objectiveKind)
            } else {
                source.updateObjective(missionId, detail, editing, title, draft.objectiveKind)
            }
        }
    }

    /**
     * Removes one Ziel.
     *
     * @param objectiveId which one.
     */
    fun removeObjective(objectiveId: String) {
        val (draft, detail) = read()
        if (detail == null) {
            return
        }
        run(draft) { source.removeObjective(missionId, detail, objectiveId) }
    }

    /**
     * Runs a write and reports its answer.
     *
     * @param draft what is typed, so the busy flag has something to sit on.
     * @param request the write.
     */
    private fun run(
        draft: MissionTimelineDraft,
        request: suspend () -> ApiResult<MissionDetail>,
    ) {
        write(draft.copy(busy = true, error = null), null)
        scope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    write(MissionTimelineDraft(sorting = draft.sorting), result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the timeline write failed: ${result.error}" }
                    write(draft.copy(busy = false, error = result.error), null)
                }
            }
        }
    }
}

/**
 * The same list with one id moved a single place.
 *
 * @param id which row to move.
 * @param up `true` towards the start.
 * @return the new order, or `null` when the row is unknown or already at that end, in which case
 *   nothing is written.
 */
private fun List<String>.moved(
    id: String,
    up: Boolean,
): List<String>? {
    val from = indexOf(id)
    val to = if (up) from - 1 else from + 1
    return if (from < 0 || to !in indices) {
        null
    } else {
        toMutableList().apply { add(to, removeAt(from)) }
    }
}

/**
 * The parts of a step an editor loads.
 *
 * @property id which step.
 * @property title what happens.
 * @property meta the line beneath it, or `null`.
 */
data class MissionStepEdit(
    val id: String,
    val title: String,
    val meta: String?,
)

/**
 * The parts of a Ziel an editor loads.
 *
 * @property id which Ziel.
 * @property title what is to be achieved.
 * @property kind its classification, already resolved to the enum the write sends.
 */
data class MissionObjectiveEdit(
    val id: String,
    val title: String,
    val kind: MissionObjectiveKind,
)

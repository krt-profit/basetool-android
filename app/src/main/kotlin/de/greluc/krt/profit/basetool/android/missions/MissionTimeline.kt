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
 * What a manager is composing on the Ablauf or the Ziele tab.
 *
 * One draft for both, because only one editor can be open at a time — the tabs are exclusive, and a
 * second draft would only be a second thing to keep in sync.
 *
 * @property stepTitle the new or edited step's title, as typed.
 * @property stepMeta its time-and-place line, as typed.
 * @property editingStepId the step being rewritten, or `null` while composing a new one.
 * @property objectiveTitle the new or edited Ziel, as typed.
 * @property objectiveKind what the Ziel is for.
 * @property editingObjectiveId the Ziel being rewritten, or `null` while composing a new one.
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
    val busy: Boolean = false,
    val error: ApiError? = null,
)

/**
 * The Einsatz's Ablauf and its Ziele, as a manager writes them.
 *
 * > **These surfaces have no artboard for their WRITE half.** Chapter 06 draws both tabs as reading
 * > surfaces — the numbered checklist with its current-phase mark, and the Ziele with their kind.
 * > The editors are composed from the design system's own drawn parts and their composition is
 * > **unratified**; round 11 asks for the drawing.
 *
 * @property missionId the Einsatz.
 * @property source where the writes go.
 * @property scope the view model's scope.
 * @property read what is typed, and the Einsatz as last read — which carries the two section
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
     * Loads one step into the editor so it can be rewritten.
     *
     * @param step which one.
     */
    fun editStep(step: MissionStepEdit) {
        val (draft, _) = read()
        write(
            draft.copy(
                stepTitle = step.title,
                stepMeta = step.meta.orEmpty(),
                editingStepId = step.id,
                error = null,
            ),
            null,
        )
    }

    /** Abandons whichever editor is open, keeping the Einsatz untouched. */
    fun cancel() {
        val (draft, _) = read()
        write(
            draft.copy(
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
     * Moves one step one place up or down.
     *
     * > **Buttons, not a drag.** The reorder endpoint wants the whole id list in its new order, and
     * > a two-button move produces that list exactly as reliably as a gesture does — without
     * > inventing a drag interaction no artboard has drawn. Round 11 asks whether it should become
     * > one; until then this is a marked, working stand-in rather than a guess at a drawing.
     *
     * The list is taken from the Einsatz as last read, so a step somebody else added meanwhile is
     * carried along rather than dropped — and if it was added after this read, the counter is stale
     * and the server refuses with a `409` instead of losing it.
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
     * Loads one Ziel into the editor so it can be rewritten.
     *
     * @param objective which one.
     */
    fun editObjective(objective: MissionObjectiveEdit) {
        val (draft, _) = read()
        write(
            draft.copy(
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
                    // The editor clears on success and only on success: a refusal that emptied it
                    // would make the member type it all again to find out what was wrong.
                    write(MissionTimelineDraft(), result.value)
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
 * @return the new order, or `null` when the row is unknown or already at that end — in which case
 *   nothing is written, so a tap at the edge is a no-op rather than a request the server refuses.
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
 * A record rather than the domain model: [editStep] needs three fields and nothing else, and taking
 * the whole `MissionStep` would tie the holder to the read model's shape.
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

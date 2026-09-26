/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Log tag for the roster's own lines. */
private const val LOG_TAG = "MissionRoster"

/**
 * What a manager may do to somebody else's row on the Teilnehmer tab.
 *
 * It holds no state beyond the catalogue; rows and their versions are read through [rowToManage].
 *
 * @property missionId the Einsatz whose roster this is.
 * @property source where the writes go.
 * @property scope the view model's scope; the catalogue read is cancelled with the screen.
 * @property rowToManage the row a manager action may address, or `null` when it may not run.
 * @property write runs one row write and folds the answer back into the screen's state.
 */
class MissionRoster(
    private val missionId: String,
    private val source: MissionSource,
    private val scope: CoroutineScope,
    private val rowToManage: (String) -> MissionParticipant?,
    private val write: (suspend () -> ApiResult<MissionParticipant>) -> Unit,
) {
    /**
     * Reads the Funktionen catalogue the roster offers, once and only for a caller who may assign.
     *
     * @param canManage whether the caller may assign at all.
     * @param known what has already been read; a non-empty list means there is nothing to do.
     * @param onLoaded called with the catalogue once it arrives.
     */
    fun loadJobTypes(
        canManage: Boolean,
        known: List<MissionJobType>,
        onLoaded: (List<MissionJobType>) -> Unit,
    ) {
        if (!canManage || known.isNotEmpty()) {
            return
        }
        scope.launch {
            when (val result = source.jobTypes()) {
                is ApiResult.Success -> {
                    onLoaded(result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the Funktionen catalogue could not be read: ${result.error}" }
                }
            }
        }
    }

    /**
     * Checks another member in or out — the per-row action the design draws for managers
     * („Manager sehen die Check-In-Aktion je Zeile; Mitglieder nur den eigenen Status",
     * chapter 06, artboard 2).
     *
     * @param participantId whose row to change.
     * @param checkInPossible whether the Einsatz has actually started; the server refuses a
     *   check-in before then, so the write is not attempted either.
     */
    fun checkIn(
        participantId: String,
        checkInPossible: Boolean,
    ) {
        val row = rowToManage(participantId) ?: return
        if (!checkInPossible) {
            return
        }
        write { source.setCheckedIn(missionId, row.id, checkedIn = !row.checkedIn) }
    }

    /**
     * Switches another member's share between paid out and donated — the drawn "manager payout
     * toggles" (chapter 06).
     *
     * @param participantId whose row to change.
     */
    fun payout(participantId: String) {
        val row = rowToManage(participantId) ?: return
        write { source.setDonating(missionId, row.id, donating = row.donating != true) }
    }

    /**
     * Assigns the job a member flies — the drawn „Funktion an Bord" select.
     *
     * Tapping the job already assigned clears it, which is how the same control behaves on the
     * sign-up sheet.
     *
     * @param participantId whose row to change.
     * @param jobType the job to assign.
     */
    fun assign(
        participantId: String,
        jobType: MissionJobType,
    ) {
        val row = rowToManage(participantId) ?: return
        val next = if (row.plannedJobTypeId == jobType.id) null else jobType.id
        write { source.setPlannedFunction(missionId, row, next) }
    }

    /**
     * Changes the job the caller asked for after signing up.
     *
     * Takes the caller's own row directly, since [rowToManage] does not vouch for it. Tapping the job
     * already wished for clears it.
     *
     * @param participant the caller's own row, as last read.
     * @param jobType the job they would like.
     */
    fun wish(
        participant: MissionParticipant,
        jobType: MissionJobType,
    ) {
        val next = jobType.id.takeIf { participant.desiredJobTypeId != jobType.id }
        write { source.setDesiredFunction(missionId, participant, next) }
    }
}

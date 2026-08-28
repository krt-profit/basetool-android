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
 * A holder beside [MissionDetailViewModel] rather than three more methods on it, following the
 * `MaterialPaneLoader` precedent: the detail screen drives seven tabs and the class had reached the
 * point where every new capability made it harder to see which handler belonged to which tab. The
 * manager's roster is one capability, gated as a whole, so it lives as one thing.
 *
 * It owns no state of its own beyond the catalogue — the roster rows and the version they carry
 * live in the screen's state, and this reads them through [rowToManage] rather than keeping a
 * second copy that could disagree with what is on screen.
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
     * Reads the Funktionen catalogue the roster's select offers.
     *
     * Only for a caller who may actually assign one, and only once. A member who cannot manage sees
     * the select **locked** — which needs the row's own function and wish, both already in hand,
     * and not the catalogue. Reading it for them would be a request for a list they can look at and
     * never use, on the tab most members open every time.
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
                    // Assigning is optional, so a catalogue that will not load must not break the
                    // roster: the select simply does not appear, and every other row action works.
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
}

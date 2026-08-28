/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MissionAdminSource
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionStructureSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Log tag for the structure's own lines. */
private const val LOG_TAG = "MissionStructure"

/**
 * What a manager is composing right now, on the Einheiten or Frequenzen tab.
 *
 * @property unitName the new Einheit's name, as typed.
 * @property unitHighValue whether it is to be flagged HVU.
 * @property freqName the new frequency's label, as typed.
 * @property freqValue the frequency itself, as typed.
 * @property busy whether a write is running.
 * @property error the last refusal.
 */
data class MissionStructureDraft(
    val unitName: String = "",
    val unitHighValue: Boolean = false,
    val freqName: String = "",
    val freqValue: String = "",
    val busy: Boolean = false,
    val error: ApiError? = null,
)

/**
 * The Einsatz's structure: its Einheiten, who is aboard them, its radio plan, its leadership.
 *
 * > **These surfaces have no artboard.** Chapter 06 draws the Einheiten and Frequenzen tabs as
 * > *reading* surfaces — „+ Person zuweisen" is annotated on artboard 2 but the Einheit that holds
 * > it is not drawn, and nothing draws adding a frequency, a manager or a party lead. This is
 * > composed from the design system's own drawn parts and its **composition is unratified**; round
 * > 10 asks for the drawing.
 *
 * @property missionId the Einsatz.
 * @property structure where the Einheit, crew and frequency writes go.
 * @property admin where the leadership writes go — a different seam, because they edit the
 *   Einsatz's own record rather than what it is made of.
 * @property scope the view model's scope.
 * @property read what is typed, and the Einsatz as last read.
 * @property write reports the draft back, together with the Einsatz a successful write answers
 *   with. **Every** structure write answers with the whole Einsatz — the `/slim` variants answer
 *   with the narrow object instead, which is why these use the plain endpoints: the screen swaps one
 *   object rather than writing and then re-reading.
 */
class MissionStructure(
    private val missionId: String,
    private val structure: MissionStructureSource,
    private val admin: MissionAdminSource,
    private val scope: CoroutineScope,
    private val read: () -> Pair<MissionStructureDraft, MissionDetail?>,
    private val write: (MissionStructureDraft, MissionDetail?) -> Unit,
) {
    /**
     * Records a change in the draft.
     *
     * @param change what the field did to it.
     */
    fun change(change: (MissionStructureDraft) -> MissionStructureDraft) {
        val (draft, _) = read()
        write(change(draft), null)
    }

    /** Adds the Einheit that is typed, and clears the field. */
    fun addUnit() {
        val (draft, _) = read()
        val name = draft.unitName.trim()
        if (name.isEmpty()) {
            return
        }
        run(draft) { structure.addUnit(missionId, name, draft.unitHighValue) }
    }

    /**
     * Removes an Einheit, and with it every crew slot aboard.
     *
     * @param unitId which one.
     */
    fun removeUnit(unitId: String) {
        val (draft, _) = read()
        run(draft) { structure.removeUnit(missionId, unitId) }
    }

    /**
     * Puts a participant aboard an Einheit.
     *
     * @param unitId which unit.
     * @param participantId which roster row — a participant, not a user.
     */
    fun addCrew(
        unitId: String,
        participantId: String,
    ) {
        val (draft, _) = read()
        // No roles: the CREW catalogue is a second, differently-archetyped list and no drawn control
        // picks from it yet. The server accepts an empty set, so somebody can be put aboard now and
        // given their roles when round 10 says what that control looks like.
        run(draft) { structure.addCrew(missionId, unitId, participantId, emptySet()) }
    }

    /**
     * Takes somebody off an Einheit.
     *
     * @param unitId which unit.
     * @param crewId which slot.
     */
    fun removeCrew(
        unitId: String,
        crewId: String,
    ) {
        val (draft, _) = read()
        run(draft) { structure.removeCrew(missionId, unitId, crewId) }
    }

    /** Adds the frequency that is typed, as a custom channel, and clears the fields. */
    fun addFrequency() {
        val (draft, detail) = read()
        val name = draft.freqName.trim()
        val value = draft.freqValue.trim()
        if (name.isEmpty() || value.isEmpty() || detail == null) {
            return
        }
        run(draft) { structure.addCustomFrequency(missionId, detail, name, value) }
    }

    /**
     * Removes one frequency.
     *
     * @param frequencyId which one.
     */
    fun removeFrequency(frequencyId: String) {
        val (draft, _) = read()
        run(draft) { structure.removeFrequency(missionId, frequencyId) }
    }

    /**
     * Sets who leads the Einsatz.
     *
     * @param userId the member.
     * @param version the party-lead section's counter as last read.
     */
    fun setPartyLead(
        userId: String,
        version: Long,
    ) {
        val (draft, _) = read()
        run(draft) { admin.setPartyLead(missionId, userId, guestName = null, version = version) }
    }

    /**
     * Grants somebody the right to manage this Einsatz.
     *
     * @param userId who.
     */
    fun addManager(userId: String) {
        val (draft, _) = read()
        run(draft) { admin.addManager(missionId, userId) }
    }

    /**
     * Takes that right away again.
     *
     * @param userId who.
     */
    fun removeManager(userId: String) {
        val (draft, _) = read()
        run(draft) { admin.removeManager(missionId, userId) }
    }

    /**
     * Puts a member on the roster who has not signed themselves up.
     *
     * @param userId who.
     */
    fun addParticipant(userId: String) {
        val (draft, _) = read()
        run(draft) { admin.addParticipant(missionId, userId) }
    }

    /**
     * Runs a write that answers with the whole Einsatz.
     *
     * @param draft what is typed, so the busy flag has something to sit on.
     * @param request the write.
     */
    private fun run(
        draft: MissionStructureDraft,
        request: suspend () -> ApiResult<MissionDetail>,
    ) {
        write(draft.copy(busy = true, error = null), null)
        scope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    // The fields clear on success and only on success: a refusal that emptied them
                    // would make the member type it all again to find out what was wrong.
                    write(MissionStructureDraft(), result.value)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the structure write failed: ${result.error}" }
                    write(draft.copy(busy = false, error = result.error), null)
                }
            }
        }
    }
}

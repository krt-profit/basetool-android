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
import de.greluc.krt.profit.basetool.android.core.data.MissionManager
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
 * @property unitName the new Einheit's name, as typed — or the edited one's, while
 *   [editingUnitId] names a unit.
 * @property unitHighValue whether it is to be flagged HVU.
 * @property composingUnit whether the „Einheit hinzufügen" sheet is open. Design ch. 18 §3 (E7)
 *   keeps composing out of the list itself, and artboard 06-14 replaced the permanent form above
 *   the Einheiten with a dashed „+ Einheit" at their foot.
 * @property editingUnitId the Einheit being renamed, or `null` while composing a new one. One set
 *   of fields serves both, because only one Einheit can be edited at a time and a second pair would
 *   only be a second thing to keep in sync.
 * @property editingUnitVersion that Einheit's optimistic lock as last read, echoed by the rename.
 * @property editingUnitOriginalName the name the rename sheet opened on, so „Speichern" can stay
 *   dimmed until the typed value actually differs from it (design ch. 18 §3, E7).
 * @property editingUnitHighValue that Einheit's HVU mark as it stands, echoed by the rename — the
 *   sheet carries one field, and the call carries both.
 * @property crewRolesFor the crew slot whose Funktionen are open for editing, as
 *   `unitId to crewId`, or `null`.
 * @property crewPickerUnitId the Einheit whose roster picker is open, or `null`. „+ Person
 *   zuweisen" is one surface that opens a picker (design ch. 06 artboard 14), not a chip
 *   field over the whole roster — that grows with the roster and is four rows high at
 *   fourteen names on a 412 dp phone.
 * @property freqName the new frequency's label, as typed.
 * @property freqValue the frequency itself, as typed.
 * @property composingFrequency whether the „Frequenz hinzufügen" sheet is open — same move as
 *   [composingUnit], and for the same reason: the tab is read to copy a number.
 * @property busy whether a write is running.
 * @property error the last refusal.
 */
data class MissionStructureDraft(
    val unitName: String = "",
    val unitHighValue: Boolean = false,
    val composingUnit: Boolean = false,
    val editingUnitId: String? = null,
    val editingUnitVersion: Long = 0L,
    val editingUnitOriginalName: String = "",
    val editingUnitHighValue: Boolean = false,
    val crewRolesFor: Pair<String, String>? = null,
    val crewPickerUnitId: String? = null,
    val freqName: String = "",
    val freqValue: String = "",
    val composingFrequency: Boolean = false,
    val busy: Boolean = false,
    val error: ApiError? = null,
    val removingManager: MissionManager? = null,
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
     * Renames an Einheit, or flips its HVU mark.
     *
     * Both in one write because the endpoint is a **replace**: `UpdateUnitRequest` carries the
     * name and the flag together, so sending one without the other would clear whichever was
     * omitted.
     *
     * @param unitId which one.
     * @param name what it is now called.
     * @param highValue whether it is flagged HVU.
     * @param version the unit's own optimistic lock, as last read.
     */
    fun updateUnit(
        unitId: String,
        name: String,
        highValue: Boolean,
        version: Long,
    ) {
        val (draft, _) = read()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return
        }
        run(draft) { structure.updateUnit(missionId, unitId, trimmed, highValue, version) }
    }

    /**
     * Sets which Funktionen somebody holds aboard an Einheit.
     *
     * Also a **replace**: the whole set goes over the wire, so a caller that drops one id has
     * revoked exactly that role and kept the rest.
     *
     * @param unitId which Einheit.
     * @param crewId which slot.
     * @param jobTypeIds the roles they are to hold, whole.
     * @param version the crew row's own optimistic lock, as last read.
     */
    fun setCrewRoles(
        unitId: String,
        crewId: String,
        jobTypeIds: Set<String>,
        version: Long,
    ) {
        val (draft, _) = read()
        run(draft) { structure.setCrewRoles(missionId, unitId, crewId, jobTypeIds, version) }
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
     * Opens the roster picker for one Einheit.
     *
     * @param unitId which Einheit it will fill.
     */
    fun openCrewPicker(unitId: String) {
        val (draft, _) = read()
        write(draft.copy(crewPickerUnitId = unitId), null)
    }

    /** Closes it without assigning anybody. */
    fun dismissCrewPicker() {
        val (draft, _) = read()
        write(draft.copy(crewPickerUnitId = null), null)
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
        // No roles at assignment: the CREW catalogue is a second, differently-archetyped list, and
        // the drawn flow puts somebody aboard first and gives them their Funktionen from the crew
        // row's own toggle chips afterwards (ch. 06 artboard 14). The server accepts an empty set.
        //
        // The picker closes with the write: it is a pick, not a multi-select.
        run(draft.copy(crewPickerUnitId = null)) {
            structure.addCrew(missionId, unitId, participantId, emptySet())
        }
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
     * Asks before taking a manager off.
     *
     * Removing one withdraws a right and therefore confirms; changing the Einsatzleitung does not,
     * because it is replaced rather than taken away (design ch. 06 artboard 12).
     *
     * @param manager who.
     */
    fun askRemoveManager(manager: MissionManager) {
        val (draft, _) = read()
        write(draft.copy(removingManager = manager), null)
    }

    /** Abandons that. */
    fun dismissRemoveManager() {
        val (draft, _) = read()
        write(draft.copy(removingManager = null), null)
    }

    /** Takes off the manager the member confirmed. */
    fun confirmRemoveManager() {
        val (draft, _) = read()
        val manager = draft.removingManager ?: return
        write(draft.copy(removingManager = null), null)
        removeManager(manager.userId)
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

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtDecimal
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.AddCrewRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddCustomFrequencyRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddParticipantByIdRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddUnitRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.JoinMissionRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryUpdateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceTotalsDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFrequencyDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionParticipantDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionUnitDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationReferenceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseJobTypeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMissionFinanceEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMissionListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PatchMissionCoreRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PatchMissionFlagsRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PatchMissionScheduleRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.SetPartyLeadRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.ShipDto
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateCrewRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateParticipantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdatePayoutPreferenceRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateUnitRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UserReferenceDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.time.Instant

/**
 * What the member has narrowed the Einsatz list to, compared by value to decide whether a re-fetch
 * is needed.
 *
 * @property text the free-text name fragment, blank when the member has not searched
 * @property statuses the statuses the member ticked; empty means "decide from [includePast]"
 * @property includePast whether `COMPLETED` and `CANCELLED` are asked for; only effective while no
 *   status is ticked
 * @property from lower bound on the planned start, or `null`
 * @property until upper bound on the planned start, or `null`
 */
data class MissionQuery(
    val text: String = "",
    val statuses: Set<MissionStatus> = emptySet(),
    val includePast: Boolean = false,
    val from: Instant? = null,
    val until: Instant? = null,
) {
    /** Whether the member has narrowed anything, which is what decides if "zurücksetzen" is offered. */
    val isNarrowed: Boolean
        get() = this != NONE

    companion object {
        /** The unnarrowed default: upcoming Einsätze, every status, no text. */
        val NONE = MissionQuery()
    }
}

/**
 * One Funktion (API: job type) a member can ask to fill on board.
 *
 * @property id what the sign-up sends as `desiredJobTypeId`.
 * @property name what the member reads.
 */
data class MissionJobType(
    val id: String,
    val name: String,
)

/**
 * The Einsatz's books: the bookings a member makes against their own sign-up.
 *
 * Guarded separately from the Einsatz itself (`isMemberOrAbove` + `canSeeMission`).
 */
interface MissionFinanceSource {
    /**
     * Books an income or an expense against an Einsatz.
     *
     * @param missionId the Einsatz.
     * @param participantId whose booking it is — the caller's own sign-up.
     * @param income whether it is money in rather than money out.
     * @param amount the magnitude, always positive; the sign lives in [income].
     * @param note what it was for, or `null`.
     * @return success, or the classified failure.
     */
    suspend fun addFinanceEntry(
        missionId: String,
        participantId: String,
        income: Boolean,
        amount: String,
        note: String?,
    ): ApiResult<Unit>

    /**
     * Rewrites one booking.
     *
     * @param entryId the entry.
     * @param income whether it is money in rather than money out.
     * @param amount the magnitude.
     * @param note what it was for, or `null`.
     * @param version the entry's version, echoed from the read.
     * @return success, or the classified failure.
     */
    suspend fun updateFinanceEntry(
        entryId: String,
        income: Boolean,
        amount: String,
        note: String?,
        version: Long?,
    ): ApiResult<Unit>

    /**
     * Removes one booking.
     *
     * @param entryId the entry.
     * @return success, or the classified failure.
     */
    suspend fun deleteFinanceEntry(entryId: String): ApiResult<Unit>
}

/**
 * The manager-only writes to the Einsatz's own record, each against its own section version
 * counter.
 */
interface MissionAdminSource {
    /**
     * The Operations the Kern section may attach this Einsatz to.
     *
     * @return what the picker may offer, id to name; empty on a failure.
     */
    suspend fun operationOptions(): List<Pair<String, String>>

    /**
     * The ships one of this Einsatz's units may be crewed with: every ship a registered participant
     * owns plus every ship already pinned to one of its units, regardless of org unit.
     *
     * @param missionId the Einsatz.
     * @return the ships, id to a readable label; empty on a failure.
     */
    suspend fun unitShipOptions(missionId: String): List<Pair<String, String>>

    /**
     * Rewrites the Kern section: title, briefing, meeting point, calendar link, status, Operation.
     *
     * The write replaces the whole section: every field left `null` is cleared, except `status`, where
     * `null` leaves it untouched. Setting `ACTIVE` also stamps `actualStartTime` and bumps the schedule
     * counter, so take the counters from the returned detail.
     *
     * @param missionId the Einsatz.
     * @param name the title; the server requires one.
     * @param description the briefing, or `null` to clear it.
     * @param meetingPoint the gathering place, or `null` to clear it.
     * @param calendarLink the external calendar entry, or `null` to clear it.
     * @param status the new lifecycle status, or `null` to leave it untouched.
     * @param operationId which Operation the Einsatz belongs to, or `null` for none; the only way an
     *   Einsatz joins an Operation.
     * @param version the Kern section's counter as last read.
     * @return the Einsatz as it now stands, or the classified failure; `409` when the counter is stale.
     */
    suspend fun patchCore(
        missionId: String,
        name: String,
        description: String?,
        meetingPoint: String?,
        calendarLink: String?,
        status: String?,
        operationId: String?,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Rewrites the Zeitplan section.
     *
     * Check-ins are refused until `actualStartTime` is set.
     *
     * @param missionId the Einsatz.
     * @param meetingTime Teamspeak gathering, or `null`.
     * @param plannedStartTime the scheduled server join, or `null`.
     * @param plannedEndTime the scheduled end, or `null`.
     * @param actualStartTime when it actually began, or `null`.
     * @param actualEndTime when it actually ended, or `null`; the only way an Einsatz ends, and setting
     *   it closes every participant's open end-time.
     * @param version the Zeitplan section's counter as last read.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun patchSchedule(
        missionId: String,
        meetingTime: String?,
        plannedStartTime: String?,
        plannedEndTime: String?,
        actualStartTime: String?,
        actualEndTime: String?,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Switches the Einsatz between internal and open; an internal Einsatz is invisible to guests and
     * anonymous visitors.
     *
     * @param missionId the Einsatz.
     * @param internal whether it is squadron-internal.
     * @param version the flags section's counter as last read.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun patchFlags(
        missionId: String,
        internal: Boolean,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Sets who leads the Einsatz.
     *
     * @param missionId the Einsatz.
     * @param userId the member, or `null` together with a [guestName].
     * @param guestName a guest's name when no member leads.
     * @param version the party-lead section's own counter.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun setPartyLead(
        missionId: String,
        userId: String?,
        guestName: String?,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Grants somebody the right to manage this Einsatz; gated on the narrower `canManageManagers`.
     *
     * @param missionId the Einsatz.
     * @param userId who.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail>

    /**
     * Takes that right away again.
     *
     * @param missionId the Einsatz.
     * @param userId who.
     * @return success, or the classified failure.
     */
    suspend fun removeManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail>

    /**
     * Puts a member on the roster who has not signed themselves up.
     *
     * @param missionId the Einsatz.
     * @param userId who.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addParticipant(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail>
}

/**
 * The Einsatz's **structure**: its Einheiten, who is aboard them, and its radio plan.
 */
interface MissionStructureSource {
    /**
     * Adds a frequency the catalogue does not hold, invented for this Einsatz.
     *
     * The `/slim` endpoint answers with the frequency list only, which is spliced onto [current].
     *
     * @param missionId the Einsatz.
     * @param current the Einsatz as last read, for everything the slim answer does not carry.
     * @param name what to call it.
     * @param value the frequency itself.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addCustomFrequency(
        missionId: String,
        current: MissionDetail,
        name: String,
        value: String,
    ): ApiResult<MissionDetail>

    /**
     * Removes one frequency.
     *
     * @param missionId the Einsatz.
     * @param frequencyId which one.
     * @return success, or the classified failure.
     */
    suspend fun removeFrequency(
        missionId: String,
        frequencyId: String,
    ): ApiResult<MissionDetail>

    /**
     * Adds an Einheit.
     *
     * @param missionId the Einsatz.
     * @param name what to call it.
     * @param highValue whether it is flagged HVU.
     * @param fields what it carries beyond those two.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addUnit(
        missionId: String,
        name: String,
        highValue: Boolean,
        fields: MissionUnitFields = MissionUnitFields(),
    ): ApiResult<MissionDetail>

    /**
     * Renames an Einheit or changes its HVU mark.
     *
     * @param missionId the Einsatz.
     * @param unitId which unit.
     * @param name its name.
     * @param highValue whether it is flagged HVU.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun updateUnit(
        missionId: String,
        unitId: String,
        name: String,
        highValue: Boolean,
        version: Long,
        fields: MissionUnitFields = MissionUnitFields(),
    ): ApiResult<MissionDetail>

    /**
     * Replaces the set of Funktionen somebody holds aboard an Einheit, from the **CREW** catalogue
     * ([MissionPeopleSource.crewJobTypes]).
     *
     * @param missionId the Einsatz.
     * @param unitId which Einheit.
     * @param crewId which slot aboard it.
     * @param jobTypeIds the roles they are to hold, whole.
     * @param version the crew row's own optimistic lock, as last read.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun setCrewRoles(
        missionId: String,
        unitId: String,
        crewId: String,
        jobTypeIds: Set<String>,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Removes an Einheit, and with it every crew slot on board.
     *
     * @param missionId the Einsatz.
     * @param unitId which unit.
     * @return success, or the classified failure.
     */
    suspend fun removeUnit(
        missionId: String,
        unitId: String,
    ): ApiResult<MissionDetail>

    /**
     * Puts a participant aboard an Einheit („+ Person zuweisen").
     *
     * @param missionId the Einsatz.
     * @param unitId which unit.
     * @param participantId who goes aboard; a roster row, not a user.
     * @param jobTypeIds the roles they hold there, from the **CREW** catalogue.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addCrew(
        missionId: String,
        unitId: String,
        participantId: String,
        jobTypeIds: Set<String>,
    ): ApiResult<MissionDetail>

    /**
     * Takes somebody off an Einheit.
     *
     * @param missionId the Einsatz.
     * @param unitId which unit.
     * @param crewId which slot.
     * @return success, or the classified failure.
     */
    suspend fun removeCrew(
        missionId: String,
        unitId: String,
        crewId: String,
    ): ApiResult<MissionDetail>
}

/**
 * The Einsatz list, as a seam.
 *
 * Separate from its HTTP implementation so the list screen's rules — debouncing, paging, what an
 * empty result means versus a failed one — can be exercised without a socket.
 */
interface MissionSource : MissionFinanceSource {
    /**
     * Reads one page of Einsätze matching [query].
     *
     * @param query what the member narrowed to.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun search(
        query: MissionQuery,
        page: Int = 0,
        pageSize: Int = MissionRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<MissionPage>

    /**
     * Reads one Einsatz in full.
     *
     * @param id the Einsatz's id.
     * @return everything the detail tabs draw, or a failure the caller can show; `Forbidden` and
     *   `NotFound` are ordinary answers.
     */
    suspend fun detail(id: String): ApiResult<MissionDetail>

    /**
     * Reads an Einsatz's money.
     *
     * @param missionId the Einsatz's id.
     * @return the totals band and the first page of entries, or a failure. Unlike [detail] this
     *   requires membership: an anonymous caller gets `Unauthenticated`, a guest `Forbidden`.
     */
    suspend fun finances(missionId: String): ApiResult<MissionFinances>

    /**
     * The functions a member can ask to fill on board.
     *
     * Read when the sign-up sheet opens rather than with the mission: a member who never signs up
     * should not pay for a catalogue they will not see (design ch. 06, artboard 3).
     *
     * @return the active functions, or the classified failure.
     */
    suspend fun jobTypes(): ApiResult<List<MissionJobType>>

    /**
     * Signs the caller up through `join`, which derives the member from the token (ADR-0154).
     *
     * @param missionId the mission.
     * @param desiredJobTypeId the function they would like, or `null` for no preference.
     * @param donate whether their share goes to the org treasury instead of to them.
     * @return the mission as it now stands, or the classified failure.
     */
    suspend fun join(
        missionId: String,
        desiredJobTypeId: String?,
        donate: Boolean,
    ): ApiResult<MissionDetail>

    /**
     * Withdraws one sign-up.
     *
     * @param missionId the Einsatz.
     * @param participantId the row to remove; the server refuses anyone else's unless the caller
     *   manages the Einsatz.
     * @return success, or the classified failure.
     */
    suspend fun leave(
        missionId: String,
        participantId: String,
    ): ApiResult<Unit>

    /**
     * Stamps a check-in or a check-out on one row.
     *
     * @param missionId the Einsatz.
     * @param participantId the row.
     * @param checkedIn whether they should end up checked in.
     * @return the row as it now stands, or the classified failure.
     */
    suspend fun setCheckedIn(
        missionId: String,
        participantId: String,
        checkedIn: Boolean,
    ): ApiResult<MissionParticipant>

    /**
     * Sets what happens to the row's share of the payout.
     *
     * @param missionId the Einsatz.
     * @param participantId the row.
     * @param donating whether the share is donated rather than paid out.
     * @return the row as it now stands, or the classified failure.
     */
    suspend fun setDonating(
        missionId: String,
        participantId: String,
        donating: Boolean,
    ): ApiResult<MissionParticipant>

    /**
     * Assigns the job a participant flies („Funktion an Bord"); mission-management only.
     *
     * `PUT …/participants/{id}` replaces the row, so every field not being changed is echoed from
     * [participant].
     *
     * @param missionId the Einsatz.
     * @param participant the row as last read; supplies the version and the fields left alone.
     * @param jobTypeId the job to assign, or `null` to clear the assignment.
     * @return the row as it now stands, or the classified failure; `409` when the version is stale.
     */
    suspend fun setPlannedFunction(
        missionId: String,
        participant: MissionParticipant,
        jobTypeId: String?,
    ): ApiResult<MissionParticipant>

    /**
     * Changes the job a member asked for after signing up.
     *
     * The `PUT` replaces the row, so every field not being changed is echoed from [participant].
     *
     * @param missionId the Einsatz.
     * @param participant the row as last read; supplies the version and the fields left alone.
     * @param jobTypeId the job they would like, or `null` for no preference.
     * @return the row as it now stands, or the classified failure; `409` when the version is stale.
     */
    suspend fun setDesiredFunction(
        missionId: String,
        participant: MissionParticipant,
        jobTypeId: String?,
    ): ApiResult<MissionParticipant>
}

/**
 * Reads and writes Einsätze through the backend, filtering the list server-side via
 * `/missions/search`.
 *
 * The org scope is not sent; it follows from memberships and the `X-Active-Org-Unit-Id` header.
 * Nothing is cached.
 *
 * @property reader performs the calls and classifies their failures
 */
class MissionRepository(
    private val reader: ApiReader,
) : MissionSource,
    MissionAdminSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads one page of Einsätze.
     *
     * "Vergangene aus" is a status filter (`PLANNED` + `ACTIVE`), applied only while no status is
     * ticked. Rows without an id are dropped without lowering the server's total.
     *
     * @param query what the member narrowed to.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun search(
        query: MissionQuery,
        page: Int,
        pageSize: Int,
    ): ApiResult<MissionPage> {
        val params =
            buildList {
                query.text.trim().takeIf { it.isNotEmpty() }?.let { add(QUERY_PARAM to it) }
                val ticked = query.statuses.filter { it != MissionStatus.UNKNOWN }
                val asked =
                    when {
                        ticked.isNotEmpty() -> ticked
                        query.includePast -> emptyList()
                        else -> UPCOMING_STATUSES
                    }
                asked.forEach { add(STATUS_PARAM to it.name) }
                query.from?.let { add(START_PARAM to it.toString()) }
                query.until?.let { add(END_PARAM to it.toString()) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
                add(SORT_PARAM to if (query.includePast) PAST_SORT else DEFAULT_SORT)
            }

        return reader.get(SEARCH_PATH, params, PageResponseMissionListDto.serializer())
            .map { it.toModel(page) }
    }

    /**
     * Reads one Einsatz in full with a single `GET /missions/{id}`, which carries every tab's data.
     *
     * @param id the Einsatz's id.
     * @return the Einsatz, or the classified failure.
     */
    override suspend fun detail(id: String): ApiResult<MissionDetail> =
        reader.get(missionPath(id), MissionDto.serializer())
            .map { it.toModel(id) }

    /**
     * Reads an Einsatz's money from two calls that succeed or fail together.
     *
     * @param missionId the Einsatz's id.
     * @return the Finanzen tab's contents, or the first failure.
     */
    override suspend fun finances(missionId: String): ApiResult<MissionFinances> =
        when (val summary = reader.get(financeSummaryPath(missionId), MissionFinanceTotalsDto.serializer())) {
            is ApiResult.Failure -> summary
            is ApiResult.Success -> financesWith(missionId, summary.value)
        }

    override suspend fun jobTypes(): ApiResult<List<MissionJobType>> =
        when (
            val result =
                reader.get(JOB_TYPES_PATH, PageResponseJobTypeDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.content
                        .orEmpty()
                        .filter { it.active != false }
                        .mapNotNull { dto -> dto.id?.let { MissionJobType(id = it, name = dto.name) } },
                )
            }
        }

    override suspend fun join(
        missionId: String,
        desiredJobTypeId: String?,
        donate: Boolean,
    ): ApiResult<MissionDetail> =
        reader.post(
            path = "${missionPath(missionId)}/join",
            body =
                JoinMissionRequest(
                    desiredJobTypeId = desiredJobTypeId,
                    payoutPreference =
                        if (donate) {
                            JoinMissionRequest.PayoutPreference.DONATE
                        } else {
                            JoinMissionRequest.PayoutPreference.PAYOUT
                        },
                ),
            bodySerializer = JoinMissionRequest.serializer(),
            deserializer = MissionDto.serializer(),
        )
            .map { it.toModel(missionId) }

    override suspend fun leave(
        missionId: String,
        participantId: String,
    ): ApiResult<Unit> = reader.delete(participantPath(missionId, participantId))

    override suspend fun setCheckedIn(
        missionId: String,
        participantId: String,
        checkedIn: Boolean,
    ): ApiResult<MissionParticipant> =
        oneRow(
            reader.post(
                participantPath(missionId, participantId, if (checkedIn) "check-in" else "check-out"),
                MissionParticipantDto.serializer(),
            ),
        )

    override suspend fun setDonating(
        missionId: String,
        participantId: String,
        donating: Boolean,
    ): ApiResult<MissionParticipant> =
        oneRow(
            reader.put(
                participantPath(missionId, participantId, "payout-preference"),
                UpdatePayoutPreferenceRequest(
                    preference =
                        if (donating) {
                            UpdatePayoutPreferenceRequest.Preference.DONATE
                        } else {
                            UpdatePayoutPreferenceRequest.Preference.PAYOUT
                        },
                ),
                UpdatePayoutPreferenceRequest.serializer(),
                MissionParticipantDto.serializer(),
            ),
        )

    override suspend fun operationOptions(): List<Pair<String, String>> =
        when (
            val result =
                reader.get(
                    OPERATIONS_LOOKUP_PATH,
                    emptyList(),
                    ListSerializer(OperationReferenceDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                emptyList()
            }

            is ApiResult.Success -> {
                result.value.mapNotNull { row ->
                    row.id?.let { id -> id to (row.name?.takeIf { it.isNotBlank() } ?: id) }
                }
            }
        }

    override suspend fun unitShipOptions(missionId: String): List<Pair<String, String>> =
        when (
            val result =
                reader.get(
                    "${missionPath(missionId)}/unit-ship-options",
                    emptyList(),
                    ListSerializer(ShipDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                emptyList()
            }

            is ApiResult.Success -> {
                result.value.mapNotNull { ship ->
                    ship.id?.let { id ->
                        val label =
                            listOfNotNull(
                                ship.name?.takeIf { it.isNotBlank() },
                                ship.shipType?.name?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ").ifBlank { id }
                        id to label
                    }
                }
            }
        }

    override suspend fun patchCore(
        missionId: String,
        name: String,
        description: String?,
        meetingPoint: String?,
        calendarLink: String?,
        status: String?,
        operationId: String?,
        version: Long,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.send(
                "${missionPath(missionId)}/core",
                PATCH,
                PatchMissionCoreRequest(
                    name = name,
                    version = version,
                    description = description,
                    meetingPoint = meetingPoint,
                    calendarLink = calendarLink,
                    status = status,
                    operationId = operationId,
                ),
                PatchMissionCoreRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun patchSchedule(
        missionId: String,
        meetingTime: String?,
        plannedStartTime: String?,
        plannedEndTime: String?,
        actualStartTime: String?,
        actualEndTime: String?,
        version: Long,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.send(
                "${missionPath(missionId)}/schedule",
                PATCH,
                PatchMissionScheduleRequest(
                    version = version,
                    meetingTime = meetingTime,
                    plannedStartTime = plannedStartTime,
                    plannedEndTime = plannedEndTime,
                    actualStartTime = actualStartTime,
                    actualEndTime = actualEndTime,
                ),
                PatchMissionScheduleRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun patchFlags(
        missionId: String,
        internal: Boolean,
        version: Long,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.send(
                "${missionPath(missionId)}/flags",
                PATCH,
                PatchMissionFlagsRequest(isInternal = internal, version = version),
                PatchMissionFlagsRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun setPartyLead(
        missionId: String,
        userId: String?,
        guestName: String?,
        version: Long,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.put(
                "${missionPath(missionId)}/party-lead",
                SetPartyLeadRequest(version = version, userId = userId, guestName = guestName),
                SetPartyLeadRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun addManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> =
        rereadMission(reader, missionId, reader.postAccepted("${missionPath(missionId)}/managers/$userId/slim"))

    override suspend fun removeManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> =
        rereadMission(reader, missionId, reader.delete("${missionPath(missionId)}/managers/$userId/slim"))

    /**
     * Lets a manager put a registered member on the roster by user id via
     * `POST …/participants/by-id/slim` (REQ-MISSION-020).
     *
     * The answer is the participant list, so the Einsatz is re-read afterwards.
     */
    override suspend fun addParticipant(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> =
        rereadMission(
            reader,
            missionId,
            reader.post(
                "${missionPath(missionId)}/participants/by-id/slim",
                AddParticipantByIdRequest(userId = userId),
                AddParticipantByIdRequest.serializer(),
                ListSerializer(MissionParticipantDto.serializer()),
            ),
        )

    override suspend fun setPlannedFunction(
        missionId: String,
        participant: MissionParticipant,
        jobTypeId: String?,
    ): ApiResult<MissionParticipant> =
        oneRow(
            reader.put(
                participantPath(missionId, participant.id, null),
                UpdateParticipantRequest(
                    version = participant.version,
                    plannedMissionJobTypeId = jobTypeId,
                    desiredMissionJobTypeId = participant.desiredJobTypeId,
                    comment = participant.comment,
                    startTime = participant.startTime,
                    endTime = participant.endTime,
                    payoutPreference =
                        when (participant.donating) {
                            true -> UpdateParticipantRequest.PayoutPreference.DONATE
                            false -> UpdateParticipantRequest.PayoutPreference.PAYOUT
                            null -> null
                        },
                ),
                UpdateParticipantRequest.serializer(),
                MissionParticipantDto.serializer(),
            ),
        )

    override suspend fun setDesiredFunction(
        missionId: String,
        participant: MissionParticipant,
        jobTypeId: String?,
    ): ApiResult<MissionParticipant> =
        oneRow(
            reader.put(
                participantPath(missionId, participant.id, null),
                UpdateParticipantRequest(
                    version = participant.version,
                    desiredMissionJobTypeId = jobTypeId,
                    plannedMissionJobTypeId = participant.plannedJobTypeId,
                    comment = participant.comment,
                    startTime = participant.startTime,
                    endTime = participant.endTime,
                    payoutPreference =
                        when (participant.donating) {
                            true -> UpdateParticipantRequest.PayoutPreference.DONATE
                            false -> UpdateParticipantRequest.PayoutPreference.PAYOUT
                            null -> null
                        },
                ),
                UpdateParticipantRequest.serializer(),
                MissionParticipantDto.serializer(),
            ),
        )

    override suspend fun addFinanceEntry(
        missionId: String,
        participantId: String,
        income: Boolean,
        amount: String,
        note: String?,
    ): ApiResult<Unit> =
        discarding(
            reader.post(
                FINANCE_ENTRIES_PATH,
                MissionFinanceEntryCreateDto(
                    missionId = missionId,
                    participantId = participantId,
                    type =
                        if (income) {
                            MissionFinanceEntryCreateDto.Type.INCOME
                        } else {
                            MissionFinanceEntryCreateDto.Type.EXPENSE
                        },
                    amount = KrtDecimal(amount.toBigDecimalOrNull() ?: BigDecimal.ZERO),
                    note = note,
                ),
                MissionFinanceEntryCreateDto.serializer(),
                MissionFinanceEntryDto.serializer(),
            ),
        )

    override suspend fun updateFinanceEntry(
        entryId: String,
        income: Boolean,
        amount: String,
        note: String?,
        version: Long?,
    ): ApiResult<Unit> =
        discarding(
            reader.put(
                "$FINANCE_ENTRIES_PATH/$entryId",
                MissionFinanceEntryUpdateDto(
                    type =
                        if (income) {
                            MissionFinanceEntryUpdateDto.Type.INCOME
                        } else {
                            MissionFinanceEntryUpdateDto.Type.EXPENSE
                        },
                    amount = KrtDecimal(amount.toBigDecimalOrNull() ?: BigDecimal.ZERO),
                    version = version ?: 0L,
                    note = note,
                ),
                MissionFinanceEntryUpdateDto.serializer(),
                MissionFinanceEntryDto.serializer(),
            ),
        )

    override suspend fun deleteFinanceEntry(entryId: String): ApiResult<Unit> =
        reader.delete("$FINANCE_ENTRIES_PATH/$entryId")

    /**
     * Keeps a finance write's outcome and discards its body; the tab is re-read afterwards.
     *
     * @param result what the write returned.
     * @return success or the failure, without the body.
     */
    private fun discarding(result: ApiResult<MissionFinanceEntryDto>): ApiResult<Unit> =
        result
            .map { }

    /**
     * Turns a slim write's answer, the participant alone, into the row.
     *
     * @param result what the write returned.
     * @return the row, or the failure, including an answer with no id.
     */
    private fun oneRow(result: ApiResult<MissionParticipantDto>): ApiResult<MissionParticipant> =
        when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.NotFound())
            }
        }

    /**
     * Fetches the entries and folds them together with the already-read summary.
     *
     * @param missionId the Einsatz's id.
     * @param summary the totals already read.
     * @return the tab's contents, or the entries' failure.
     */
    private suspend fun financesWith(
        missionId: String,
        summary: MissionFinanceTotalsDto,
    ): ApiResult<MissionFinances> =
        when (
            val entries =
                reader.get(
                    financeEntriesPath(missionId),
                    listOf(PAGE_PARAM to "0", SIZE_PARAM to FINANCE_PAGE_SIZE.toString()),
                    PageResponseMissionFinanceEntryDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                entries
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    MissionFinances(
                        total = summary.total?.toString(),
                        incomeSum = summary.incomeSum?.toString(),
                        incomeCount = summary.incomeCount ?: 0L,
                        expenseSum = summary.expenseSum?.toString(),
                        expenseCount = summary.expenseCount ?: 0L,
                        entries = entries.value.content.orEmpty().mapNotNull { it.toModel() },
                        totalEntries = entries.value.totalElements ?: 0L,
                    ),
                )
            }
        }

    companion object {
        /**
         * Rows per page.
         *
         * Sized for a phone: enough that the first screenful never needs a second round trip, small
         * enough that a slow connection shows something quickly.
         */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /**
         * The server's sort for the default, forward-looking list.
         *
         * `plannedStartTime` is on the backend's sort whitelist; a field that is not would be
         * answered with a 400, so this is not a free-form string. Ascending is right while the list
         * shows what is coming: the next mission belongs at the top.
         */
        const val DEFAULT_SORT: String = "plannedStartTime,asc"

        /**
         * The sort once past missions are included: most recent planned start first.
         */
        const val PAST_SORT: String = "plannedStartTime,desc"

        /** Log subsystem. Search terms are member input and never reach the log. */
        private const val LOG_TAG = "missions"

        /**
         * Entries fetched for the Finanzen tab.
         *
         * The tab shows a list under a totals band, not a paginated ledger; the band's counts come
         * from the summary and are correct regardless, so the list states how many of the total it
         * is showing rather than pretending to be all of them.
         */
        const val FINANCE_PAGE_SIZE: Int = 50

        /** The verb the three section edits use; [ApiReader] has no dedicated `patch`. */
        private const val PATCH = "PATCH"

        private const val SEARCH_PATH = "/api/v1/missions/search"

        /** The Operations the Kern section may attach an Einsatz to. */
        private const val OPERATIONS_LOOKUP_PATH = "/api/v1/operations/lookup"

        /**
         * The participant Funktionen, restricted to `archetype=MISSION`; the server refuses a `CREW` type
         * on a participant.
         */
        private const val JOB_TYPES_PATH = "/api/v1/job-types?archetype=MISSION&page=0&size=200"

        /**
         * Where a booking is written.
         *
         * NOT under `/missions`: the write paths of the money live at the API root, which is why
         * they are their own family on the vhost rather than an exception to the read-only guard
         * on the Einsatz prefix.
         */
        private const val FINANCE_ENTRIES_PATH = "/api/v1/finance-entries"

        /**
         * One participant row's slim path.
         *
         * @param missionId the Einsatz's id.
         * @param participantId the row's id.
         * @param action the sub-resource, or `null` for the row itself.
         * @return the path.
         */
        private fun participantPath(
            missionId: String,
            participantId: String,
            action: String? = null,
        ): String {
            val row = "${missionPath(missionId)}/participants/$participantId"
            return if (action == null) "$row/slim" else "$row/$action/slim"
        }

        /**
         * The finance-entries path for one Einsatz.
         *
         * @param id the Einsatz's id.
         * @return the path.
         */
        private fun financeEntriesPath(id: String) = "/api/v1/missions/$id/finance-entries"

        /**
         * The finance-summary path for one Einsatz.
         *
         * @param id the Einsatz's id.
         * @return the path.
         */
        private fun financeSummaryPath(id: String) = "/api/v1/missions/$id/finance-entries/summary"

        private const val QUERY_PARAM = "query"

        /**
         * What "Vergangene aus" asks for.
         *
         * `ACTIVE` is in it because a running Einsatz is not a past one, however long ago it
         * gathered.
         */
        private val UPCOMING_STATUSES = listOf(MissionStatus.PLANNED, MissionStatus.ACTIVE)

        private const val STATUS_PARAM = "status"
        private const val START_PARAM = "start"
        private const val END_PARAM = "end"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val SORT_PARAM = "sort"
    }
}

/**
 * Maps one page of wire rows onto the model.
 *
 * @param requestedPage the page index that was asked for, used when the server omits its own.
 * @return the page, with unopenable rows removed.
 */
private fun PageResponseMissionListDto.toModel(requestedPage: Int): MissionPage =
    MissionPage(
        rows = content.orEmpty().mapNotNull { it.toModel() },
        page = page ?: requestedPage,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one wire row onto the model.
 *
 * @return the Einsatz, or `null` when it carries no id and therefore cannot be opened.
 */
private fun MissionListDto.toModel(): Mission? {
    val missionId = id ?: return null
    return Mission(
        id = missionId,
        name = name?.takeIf { it.isNotBlank() } ?: missionId,
        status = MissionStatus.from(status),
        rawStatus = status,
        meetingTime = meetingTime?.toInstantOrNull(),
        plannedStartTime = plannedStartTime?.toInstantOrNull(),
        actualStartTime = actualStartTime?.toInstantOrNull(),
        plannedEndTime = plannedEndTime?.toInstantOrNull(),
        isInternal = isInternal ?: false,
        operationName = operation?.name,
        orgUnitName = owningSquadron?.name,
        orgUnitShorthand = owningSquadron?.shorthand,
        meetingPoint = meetingPoint,
        description = description?.takeIf { it.isNotBlank() },
        registeredCount = registeredCount?.toInt(),
    )
}

/**
 * Parses an ISO-8601 instant from the wire, so a bad timestamp costs only that value.
 *
 * @return the instant, or `null` when the value is not parseable.
 */
private fun String.toInstantOrNull(): Instant? =
    runCatching { Instant.parse(this) }.getOrNull()

/**
 * The Einsatz's radio plan, dropping frequencies without an id.
 *
 * @receiver what the server sent.
 * @return the frequencies, in the server's order.
 */
private fun MissionDto.frequencyModels(): List<MissionFrequency> =
    frequencies.orEmpty().mapNotNull { frequency -> frequency.model() }

/**
 * One frequency as the screen reads it: `name` is the label and `value` the number.
 *
 * A custom frequency labels itself with its own `name`; a preset one takes the type's name.
 *
 * @receiver the wire row.
 * @return the model, or `null` for a row the server sent without an id.
 */
private fun MissionFrequencyDto.model(): MissionFrequency? =
    id?.let {
        MissionFrequency(
            id = it,
            type = frequencyType?.name ?: name,
            value = value?.toString().orEmpty(),
        )
    }

/**
 * One manager as the Verwaltung tab holds them.
 *
 * @receiver what the server sent.
 * @return the manager, or `null` when it carries no id, which a removal needs.
 */
private fun UserReferenceDto.toManager(): MissionManager? =
    id?.let {
        MissionManager(
            userId = it,
            name = effectiveName ?: displayName ?: username.orEmpty(),
        )
    }

/**
 * Maps the full wire DTO onto the detail model.
 *
 * @param requestedId the id that was asked for, used when the server omits its own.
 * @return the Einsatz.
 */
private fun MissionDto.toModel(requestedId: String): MissionDetail {
    val missionId = id ?: requestedId
    return MissionDetail(
        id = missionId,
        name = name.takeIf { it.isNotBlank() } ?: missionId,
        description = description?.takeIf { it.isNotBlank() },
        status = MissionStatus.from(status),
        rawStatus = status,
        meetingTime = meetingTime?.toInstantOrNull(),
        plannedStartTime = plannedStartTime?.toInstantOrNull(),
        actualStartTime = actualStartTime?.toInstantOrNull(),
        actualEndTime = actualEndTime?.toInstantOrNull(),
        plannedEndTime = plannedEndTime?.toInstantOrNull(),
        isInternal = isInternal ?: false,
        meetingPoint = meetingPoint?.takeIf { it.isNotBlank() },
        calendarLink = calendarLink?.takeIf { it.isNotBlank() },
        operationId = operation?.id,
        operationName = operation?.name,
        orgUnitName = owningSquadron?.name,
        orgUnitShorthand = owningSquadron?.shorthand,
        partyLeadName = partyLeadUser?.effectiveName ?: partyLeadUser?.displayName ?: partyLeadGuestName,
        managers = managers.orEmpty().mapNotNull { it.toManager() },
        canManageManagers = canManageManagers == true,
        registeredParticipants = registeredParticipants ?: 0,
        checkedInParticipants = checkedInParticipants ?: 0,
        participants = participants.orEmpty().mapNotNull { it.toModel() },
        units = assignedUnits.orEmpty().mapNotNull { it.toModel() },
        steps =
            steps.orEmpty().mapNotNull { step ->
                val stepId = step.id ?: return@mapNotNull null
                MissionStep(
                    id = stepId,
                    title = step.title.orEmpty(),
                    meta = step.meta?.takeIf { it.isNotBlank() },
                    done = step.done ?: false,
                )
            },
        objectives =
            objectives.orEmpty().mapNotNull { objective ->
                val objectiveId = objective.id ?: return@mapNotNull null
                MissionObjective(
                    id = objectiveId,
                    title = objective.title.orEmpty(),
                    kind = objective.kind?.value,
                )
            },
        frequencies = frequencyModels(),
        canManage = canEdit ?: false,
        coreVersion = sectionVersion(coreVersion),
        scheduleVersion = sectionVersion(scheduleVersion),
        flagsVersion = sectionVersion(flagsVersion),
        partyLeadVersion = sectionVersion(partyLeadVersion),
        stepsVersion = sectionVersion(stepsVersion),
        objectivesVersion = sectionVersion(objectivesVersion),
    )
}

/**
 * The Einsatz's structure writes over HTTP, beside [MissionRepository] on the same [ApiReader].
 *
 * @property reader the HTTP seam.
 */
class MissionStructureRepository(
    private val reader: ApiReader,
) : MissionStructureSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client, so the bearer, the correlation id and the org pin are
     *   already on every request.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "MissionStructure"),
    )

    override suspend fun addCustomFrequency(
        missionId: String,
        current: MissionDetail,
        name: String,
        value: String,
    ): ApiResult<MissionDetail> =
        when (
            val result =
                reader.post(
                    "${missionPath(missionId)}/frequencies/custom/slim",
                    AddCustomFrequencyRequest(name = name, value = KrtDecimal(BigDecimal(value))),
                    AddCustomFrequencyRequest.serializer(),
                    ListSerializer(MissionFrequencyDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    current.copy(
                        frequencies = result.value.mapNotNull { it.model() },
                    ),
                )
            }
        }

    override suspend fun removeFrequency(
        missionId: String,
        frequencyId: String,
    ): ApiResult<MissionDetail> =
        rereadMission(reader, missionId, reader.delete("${missionPath(missionId)}/frequencies/$frequencyId/slim"))

    override suspend fun addUnit(
        missionId: String,
        name: String,
        highValue: Boolean,
        fields: MissionUnitFields,
    ): ApiResult<MissionDetail> =
        rereadMission(
            reader,
            missionId,
            reader.postAccepted(
                "${missionPath(missionId)}/units/slim",
                AddUnitRequest(
                    name = name,
                    highValueUnit = highValue,
                    shipTypeId = fields.shipTypeId,
                    shipId = fields.shipId,
                    frequency = fields.frequency,
                    responsibleUserId = fields.responsibleUserId,
                    note = fields.note,
                ),
                AddUnitRequest.serializer(),
            ),
        )

    override suspend fun updateUnit(
        missionId: String,
        unitId: String,
        name: String,
        highValue: Boolean,
        version: Long,
        fields: MissionUnitFields,
    ): ApiResult<MissionDetail> =
        rereadMission(
            reader,
            missionId,
            reader.putAccepted(
                "${missionPath(missionId)}/units/$unitId/slim",
                UpdateUnitRequest(
                    name = name,
                    highValueUnit = highValue,
                    version = version,
                    shipTypeId = fields.shipTypeId,
                    shipId = fields.shipId,
                    frequency = fields.frequency,
                    responsibleUserId = fields.responsibleUserId,
                    note = fields.note,
                ),
                UpdateUnitRequest.serializer(),
            ),
        )

    override suspend fun setCrewRoles(
        missionId: String,
        unitId: String,
        crewId: String,
        jobTypeIds: Set<String>,
        version: Long,
    ): ApiResult<MissionDetail> =
        rereadMission(
            reader,
            missionId,
            reader.putAccepted(
                "${missionPath(missionId)}/units/$unitId/crew/$crewId/slim",
                UpdateCrewRequest(jobTypeIds = jobTypeIds, version = version),
                UpdateCrewRequest.serializer(),
            ),
        )

    override suspend fun removeUnit(
        missionId: String,
        unitId: String,
    ): ApiResult<MissionDetail> =
        rereadMission(reader, missionId, reader.delete("${missionPath(missionId)}/units/$unitId/slim"))

    override suspend fun addCrew(
        missionId: String,
        unitId: String,
        participantId: String,
        jobTypeIds: Set<String>,
    ): ApiResult<MissionDetail> =
        rereadMission(
            reader,
            missionId,
            reader.postAccepted(
                "${missionPath(missionId)}/units/$unitId/crew/slim",
                AddCrewRequest(participantId = participantId, jobTypeIds = jobTypeIds),
                AddCrewRequest.serializer(),
            ),
        )

    override suspend fun removeCrew(
        missionId: String,
        unitId: String,
        crewId: String,
    ): ApiResult<MissionDetail> =
        rereadMission(reader, missionId, reader.delete("${missionPath(missionId)}/units/$unitId/crew/$crewId/slim"))
}

/**
 * Folds a write's whole-Einsatz answer back into the model, with fresh counters for every section.
 *
 * @param missionId the Einsatz, for the id fallback.
 * @param result what the write answered.
 * @return the Einsatz, or the failure unchanged.
 */
private fun oneMission(
    missionId: String,
    result: ApiResult<MissionDto>,
): ApiResult<MissionDetail> =
    result
        .map { it.toModel(missionId) }

/**
 * Re-reads the Einsatz after a `/slim` write that answered with only the part it touched.
 *
 * @param reader the API seam to re-read through.
 * @param missionId the Einsatz.
 * @param written what the write answered; only whether it succeeded matters here.
 * @return the Einsatz as it now stands, or the write's failure unchanged.
 */
private suspend fun rereadMission(
    reader: ApiReader,
    missionId: String,
    written: ApiResult<*>,
): ApiResult<MissionDetail> =
    when (written) {
        is ApiResult.Failure -> {
            written
        }

        is ApiResult.Success -> {
            oneMission(missionId, reader.get(missionPath(missionId), MissionDto.serializer()))
        }
    }

/**
 * The path of one Einsatz.
 *
 * @param missionId which one.
 * @return the API path.
 */
internal fun missionPath(missionId: String): String = "/api/v1/missions/$missionId"

/**
 * A section's optimistic-lock counter as the client should hold it.
 *
 * An absent counter becomes `0`, which the server never issues, so a write carrying it is refused
 * with a `409`.
 *
 * @param raw what the wire carried, or `null`.
 * @return the counter to echo on the next write against that section.
 */
private fun sectionVersion(raw: Long?): Long = raw ?: 0L

/**
 * Maps one participant row.
 *
 * @return the participant, or `null` without an id — a row that cannot be keyed cannot be listed
 *   stably.
 */
private fun MissionParticipantDto.toModel(): MissionParticipant? {
    val participantId = id ?: return null
    return MissionParticipant(
        id = participantId,
        userId = user?.id,
        name = user?.effectiveName ?: user?.displayName ?: guestName.orEmpty(),
        role = plannedMissionJobType?.name ?: desiredMissionJobType?.name,
        orgUnitNames = orgUnits.orEmpty().mapNotNull { it.shorthand ?: it.name },
        checkedIn = startTime != null,
        startTime = startTime,
        endTime = endTime,
        comment = comment?.takeIf { it.isNotBlank() },
        donating = payoutPreference?.let { it == MissionParticipantDto.PayoutPreference.DONATE },
        desiredJobTypeId = desiredMissionJobType?.id,
        desiredJobName = desiredMissionJobType?.name,
        plannedJobTypeId = plannedMissionJobType?.id,
        version = version ?: 0L,
    )
}

/**
 * Maps one unit and its crew.
 *
 * @return the unit, or `null` without an id.
 */
private fun MissionUnitDto.toModel(): MissionUnit? {
    val unitId = id ?: return null
    return MissionUnit(
        id = unitId,
        name = name.orEmpty(),
        shipName = ship?.name ?: shipType?.name,
        highValue = highValueUnit ?: false,
        fields =
            MissionUnitFields(
                shipTypeId = shipType?.id,
                shipId = ship?.id,
                frequency = frequency,
                responsibleUserId = responsibleUser?.id,
                note = note,
            ),
        responsibleName = responsibleUser?.effectiveName ?: responsibleUser?.displayName,
        crew =
            crew.orEmpty().mapNotNull { member ->
                val crewId = member.id ?: return@mapNotNull null
                MissionCrewMember(
                    id = crewId,
                    name = member.participantName.orEmpty(),
                    roles = member.jobTypes.orEmpty().map { it.name },
                    roleIds = member.jobTypes.orEmpty().mapNotNull { it.id },
                    version = member.version ?: 0L,
                )
            },
        version = version ?: 0L,
    )
}

/**
 * Maps one finance entry.
 *
 * @return the entry, or `null` without an id.
 */
private fun MissionFinanceEntryDto.toModel(): MissionFinanceEntry? {
    val entryId = id ?: return null
    return MissionFinanceEntry(
        id = entryId,
        income = type?.value.equals("INCOME", ignoreCase = true),
        amount = amount?.toString().orEmpty(),
        note = note?.takeIf { it.isNotBlank() },
        participantName = participant?.user?.effectiveName ?: participant?.guestName,
        participantId = participant?.id,
        version = version,
    )
}

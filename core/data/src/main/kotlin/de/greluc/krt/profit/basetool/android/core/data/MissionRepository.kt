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
import de.greluc.krt.profit.basetool.android.core.contract.model.AddFrequencyRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddParticipantPublicRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddParticipantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.AddUnitRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryCreateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryUpdateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceTotalsDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFrequencyDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionParticipantDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionUnitDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseJobTypeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMissionFinanceEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMissionListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PatchMissionCoreRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PatchMissionFlagsRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PatchMissionScheduleRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.SetPartyLeadRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateCrewRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateParticipantRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdatePayoutPreferenceRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UpdateUnitRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UserReferenceDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.time.Instant

/**
 * What the member has narrowed the Einsatz list to.
 *
 * A value type rather than a bag of parameters so the screen can hold one object, compare two for
 * equality (which is what decides whether a re-fetch is even needed) and reset to [NONE] in one
 * assignment.
 *
 * @property text the free-text name fragment, blank when the member has not searched
 * @property statuses the statuses the member ticked; empty means "decide from [includePast]"
 * @property includePast whether Einsätze that are over belong in the list — that is, whether
 *   `COMPLETED` and `CANCELLED` are asked for. It only has an effect while no status is ticked, in
 *   which case the ticked ones are the filter (the web app behaves the same way)
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
 * One function a member can ask to fill on board.
 *
 * The organisation calls these Funktionen and the API calls them job types; the app follows the
 * organisation, because that is the word on the artboard and in the room.
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
 * Split from [MissionSource] rather than sitting inside it because the money is a **separately
 * guarded** surface — a member may read an Einsatz and still be refused its finances
 * (`isMemberOrAbove` + `canSeeMission`) — and because the interface had grown past what one
 * abstraction should carry. The same implementation serves both; the split is about what a caller
 * has to depend on, not about where the code lives.
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
 * Editing the Einsatz itself — the Verwaltung half of the detail screen.
 *
 * Its own seam rather than three more methods on [MissionSource]: these are the writes only a
 * manager may make, and they are the ones that carry the **section version counters**. Keeping them
 * together is what makes it obvious that the three sections are independent, which is the whole
 * point of there being three counters instead of one.
 */
interface MissionAdminSource {
    /**
     * Rewrites the Kern section: title, briefing, meeting point, calendar link, status.
     *
     * > **This replaces the whole section, it does not merge into it.** The server assigns every
     * > one of these fields unconditionally, so anything left out is set to `null` — which is how
     * > the app used to clear a mission's `calendarLink` on every rename, having never mapped the
     * > field at all. Pass the value as it stands unless you mean to change it.
     *
     * `status` is the exception and the only sparse field: `null` leaves the status alone. Setting
     * it to `ACTIVE` also stamps `actualStartTime` server-side, in the same transaction, and bumps
     * the **schedule** counter with it — which is what makes „Starten" one call rather than two,
     * and why the caller must take the returned detail's counters rather than its own.
     *
     * @param missionId the Einsatz.
     * @param name the title; the server requires one.
     * @param description the briefing, or `null` to clear it.
     * @param meetingPoint the gathering place, or `null` to clear it.
     * @param calendarLink the external calendar entry, or `null` to clear it.
     * @param status the new lifecycle status, or `null` to leave it untouched.
     * @param version the **Kern** section's counter as last read.
     * @return the Einsatz as it now stands, or the classified failure — `409` when the counter is
     *   stale, which is a concurrent edit of *this* section and nothing else.
     */
    suspend fun patchCore(
        missionId: String,
        name: String,
        description: String?,
        meetingPoint: String?,
        calendarLink: String?,
        status: String?,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Rewrites the Zeitplan section.
     *
     * `actualStartTime` is the one that matters operationally: the server refuses every check-in
     * until it is set, so this is what opens the Einsatz for its participants.
     *
     * @param missionId the Einsatz.
     * @param meetingTime Teamspeak gathering, or `null`.
     * @param plannedStartTime the scheduled server join, or `null`.
     * @param plannedEndTime the scheduled end, or `null`.
     * @param actualStartTime when it actually began, or `null`.
     * @param version the **Zeitplan** section's counter as last read.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun patchSchedule(
        missionId: String,
        meetingTime: String?,
        plannedStartTime: String?,
        plannedEndTime: String?,
        actualStartTime: String?,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Switches the Einsatz between internal and open.
     *
     * Not cosmetic: an internal Einsatz is invisible to guests and to anonymous visitors, so this
     * is the control that decides who can find it at all.
     *
     * @param missionId the Einsatz.
     * @param internal whether it is squadron-internal.
     * @param version the **flags** section's counter as last read.
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
     * @param version the **party-lead** section's counter, which is its own again.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun setPartyLead(
        missionId: String,
        userId: String?,
        guestName: String?,
        version: Long,
    ): ApiResult<MissionDetail>

    /**
     * Grants somebody the right to manage this Einsatz.
     *
     * Gated on `canManageManagers`, which is a **narrower** right than managing the Einsatz — being
     * able to run it does not imply being able to hand that out.
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
 *
 * Split from [MissionAdminSource] because the two answer different questions — that one edits the
 * Einsatz's own record, this one edits what it is made of — and because one seam carrying both had
 * grown past what a single abstraction should ask a caller to depend on.
 */
interface MissionStructureSource {
    /**
     * Adds a frequency from the catalogue.
     *
     * @param missionId the Einsatz.
     * @param frequencyTypeId which frequency.
     * @param value the frequency itself. The type names the channel and this is the number on it —
     *   both are required, because the catalogue holds the purpose and not the setting.
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addFrequency(
        missionId: String,
        frequencyTypeId: String,
        value: String,
    ): ApiResult<MissionDetail>

    /**
     * Adds a frequency the catalogue does not hold — a channel invented for this Einsatz.
     *
     * A custom frequency exists **only** as a `/slim` endpoint, which answers with the new frequency
     * list rather than the whole Einsatz — `POST …/frequencies/custom` is a `405` whose
     * `supportedMethods` is `[DELETE]`, found on a device. So this takes the Einsatz as last read
     * and splices the answer onto it, which keeps the caller's contract the same as every other
     * structure write.
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
     * @return the Einsatz as it now stands, or the classified failure.
     */
    suspend fun addUnit(
        missionId: String,
        name: String,
        highValue: Boolean,
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
    ): ApiResult<MissionDetail>

    /**
     * Sets which Funktionen somebody holds aboard an Einheit.
     *
     * A **replace**: the request carries the whole set, so a caller that sends one id has assigned
     * exactly that one and revoked the rest. Roles come from the **CREW** catalogue, which shares
     * its names with the MISSION one — see [MissionPeopleSource.crewJobTypes].
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
     * Puts a participant aboard an Einheit — the artboard's „+ Person zuweisen".
     *
     * @param missionId the Einsatz.
     * @param unitId which unit.
     * @param participantId who goes aboard; a roster row, not a user.
     * @param jobTypeIds the roles they hold there. These are **CREW** job types, not the MISSION
     *   ones a participant's own Funktion uses — the two catalogues share their names.
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
     * @return everything the detail tabs draw, or a failure the caller can show. `NotFound` and
     *   `Forbidden` are ordinary answers here: the backend refuses an outsider an internal or
     *   terminal Einsatz with 403, and a stale link is a 404.
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
     * Signs the caller up, with what they asked for.
     *
     * Sent through `participants/add` rather than `join`, because `join` takes no body and the
     * sheet has two answers to carry: the payout preference and the desired function. Both
     * endpoints are guarded by `canSeeMission`, so this is the same permission through a door that
     * fits — verified against the running stack rather than inferred.
     *
     * @param missionId the mission.
     * @param userId the caller's own id; this endpoint can name anybody, and the app names only the
     *   member using it.
     * @param desiredJobTypeId the function they would like, or `null` for no preference.
     * @param donate whether their share goes to the org treasury instead of to them.
     * @return the mission as it now stands, or the classified failure.
     */
    suspend fun join(
        missionId: String,
        userId: String,
        desiredJobTypeId: String?,
        donate: Boolean,
    ): ApiResult<MissionDetail>

    /**
     * Withdraws one sign-up.
     *
     * @param missionId the Einsatz.
     * @param participantId the row to remove — the caller's own; the server refuses anyone else's
     *   unless the caller manages the Einsatz.
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
     * Assigns the job a participant flies — the design's „Funktion an Bord" select (chapter 06,
     * artboard 2). Mission-management only; the server refuses it for anyone else.
     *
     * **It sends the row whole, and it has to.** `PUT …/participants/{id}` is a replace, not a
     * patch: the server clears `desiredMissionJobType`, `plannedMissionJobType` and `comment` when
     * the request omits them, and only `payoutPreference` survives a null. So a call that carried
     * nothing but the new function would silently wipe the member's own stated wish and their note
     * — a data loss with no error and no visible cause, discoverable only by the member who typed
     * the note. [participant] is therefore the row as last read, and everything not being changed
     * is echoed back from it.
     *
     * @param missionId the Einsatz.
     * @param participant the row as last read; supplies the version and the fields left alone.
     * @param jobTypeId the job to assign, or `null` to clear the assignment.
     * @return the row as it now stands, or the classified failure — `409` when the version is
     *   stale, which is the case a concurrent manager edit produces.
     */
    suspend fun setPlannedFunction(
        missionId: String,
        participant: MissionParticipant,
        jobTypeId: String?,
    ): ApiResult<MissionParticipant>
}

/**
 * Reads Einsätze from the backend.
 *
 * **`/missions/search`, not `/missions`.** The plain list takes only paging, so every filter the
 * design puts in the chip row — text, status, date range, "Vergangene aus" — would have to be
 * applied on the device, over a page the server already truncated. One endpoint that filters
 * server-side is both correct and the only version that can say how many rows the filter matched.
 *
 * **The org scope is not sent here and must not be.** Which units a member sees is decided
 * server-side from their memberships and the `X-Active-Org-Unit-Id` header that
 * `MandatoryHeadersInterceptor` puts on every request. A client-side unit filter would be a second,
 * weaker copy of a rule that already exists.
 *
 * Nothing is cached. An Einsatz list is the kind of data whose staleness a member notices
 * immediately — someone signs up, a start time moves — and the screen offers pull-to-refresh
 * precisely because the answer is expected to change.
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
     * "Vergangene aus" is a **status** filter, not a time one, exactly as the web app has it: it
     * asks for `PLANNED` + `ACTIVE` and leaves out what is over. Expressed as a lower bound on the
     * start instead — which is what this did until a device walk-through caught it — it also hides
     * every *running* Einsatz, whose gathering time is by definition in the past. That is the row a
     * member most needs, and the design's own "seit 15:57" wording for it could never appear.
     *
     * A ticked status wins: the member has then said which ones they want, and subtracting from
     * that would answer "show me the finished ones" with an empty list.
     *
     * A row without an id is dropped: it cannot be opened, so offering it would produce a tap that
     * does nothing. The drop is counted into neither total, because the server's total is what the
     * screen states and quietly lowering it would hide the fault.
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
                add(SORT_PARAM to DEFAULT_SORT)
            }

        return when (val result = reader.get(SEARCH_PATH, params, PageResponseMissionListDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    /**
     * Reads one Einsatz in full.
     *
     * One call, not seven: `GET /missions/{id}` already carries the participants, units, steps,
     * objectives and frequencies, so a tab switch costs nothing and the seven tabs cannot disagree
     * with each other about the same Einsatz.
     *
     * @param id the Einsatz's id.
     * @return the Einsatz, or the classified failure.
     */
    override suspend fun detail(id: String): ApiResult<MissionDetail> =
        when (val result = reader.get(missionPath(id), MissionDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(id))
        }

    /**
     * Reads an Einsatz's money.
     *
     * **Two calls, and they succeed or fail together.** The totals band and the entries are one
     * tab; showing a total over an empty list, or a list under a blank total, would read as data
     * rather than as the partial answer it is. Both are guarded identically server-side
     * (`isMemberOrAbove` + `canSeeMission`), so in practice they never disagree.
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
        userId: String,
        desiredJobTypeId: String?,
        donate: Boolean,
    ): ApiResult<MissionDetail> =
        when (
            val result =
                reader.post(
                    path = "${missionPath(missionId)}/participants/add",
                    body =
                        AddParticipantPublicRequest(
                            userId = userId,
                            desiredJobTypeId = desiredJobTypeId,
                            payoutPreference =
                                if (donate) {
                                    AddParticipantPublicRequest.PayoutPreference.DONATE
                                } else {
                                    AddParticipantPublicRequest.PayoutPreference.PAYOUT
                                },
                        ),
                    bodySerializer = AddParticipantPublicRequest.serializer(),
                    deserializer = MissionDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(missionId))
        }

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

    override suspend fun patchCore(
        missionId: String,
        name: String,
        description: String?,
        meetingPoint: String?,
        calendarLink: String?,
        status: String?,
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
        oneMission(
            missionId,
            reader.post("${missionPath(missionId)}/managers/$userId", MissionDto.serializer()),
        )

    override suspend fun removeManager(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> =
        oneMission(missionId, reader.delete("${missionPath(missionId)}/managers/$userId", MissionDto.serializer()))

    override suspend fun addParticipant(
        missionId: String,
        userId: String,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.post(
                "${missionPath(missionId)}/participants",
                AddParticipantRequest(userId = userId),
                AddParticipantRequest.serializer(),
                MissionDto.serializer(),
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
                    // Everything below is echoed, not chosen. `PUT …/participants/{id}` replaces
                    // the row: the server clears desiredMissionJobType and comment on a null, and
                    // assigns startTime/endTime UNCONDITIONALLY — so an omitted startTime checks
                    // the member out. Only payoutPreference survives a null, and it is echoed too
                    // rather than relying on that asymmetry.
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
     * Keeps the outcome of a write and throws its body away.
     *
     * The three finance writes answer with the entry, and the tab is re-read afterwards anyway:
     * the totals above the list move with every one of them, and patching a row would leave a sum
     * that disagrees with the rows under it.
     *
     * @param result what the write returned.
     * @return success or the failure, without the body.
     */
    private fun discarding(result: ApiResult<MissionFinanceEntryDto>): ApiResult<Unit> =
        when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    /**
     * Turns a slim write's answer into the row.
     *
     * The slim endpoints answer with the participant alone rather than the whole Einsatz, which is
     * the point of them: a check-in changes one timestamp and the detail is large.
     *
     * @param result what the write returned.
     * @return the row, or the failure — including an answer with no id, which no row can be built
     *   from.
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
     * Split out so [finances] reads as the one decision it makes -- either read fails, the tab
     * fails -- rather than as a chain of early returns.
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
         * The server's sort, named explicitly rather than left to the default.
         *
         * `plannedStartTime` is on the backend's sort whitelist; a field that is not would be
         * answered with a 400, so this is not a free-form string.
         */
        const val DEFAULT_SORT: String = "plannedStartTime,asc"

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

        /**
         * The Funktionen a **participant** can be asked for or assigned — read when the sign-up
         * sheet opens, and by a manager on the Teilnehmer tab.
         *
         * `archetype=MISSION` is load-bearing, not tidiness. The catalogue holds two kinds and the
         * backend refuses the wrong one outright — "Planned JobType Pilot is not of archetype
         * MISSION", a 400 found on a device. `CREW` types (Pilot, Turret, Cargo, Scan, Medic) are
         * the roles inside an Einheit and are assigned through the unit's crew, not through the
         * participant. The two share names, which is exactly why an unfiltered read looks right on
         * screen and fails on write.
         *
         * The web asks the same way, through two separate cached catalogues (`JOB_TYPES_MISSION` /
         * `JOB_TYPES_CREW`).
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
         * The slim pair throughout: the legacy full-DTO endpoints are `@ApiDeprecation`-marked
         * with a sunset, and they answer with the whole Einsatz for a change to one row.
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
        missions = content.orEmpty().mapNotNull { it.toModel() },
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
        // A nameless Einsatz would render as a blank row. The design has no placeholder for one, and
        // an empty row is indistinguishable from a rendering bug, so the id stands in — meaningless
        // to a member, but at least something to point at when reporting it.
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
        // MissionListDto carries no participant count, so the dashboard band's "{n} angemeldet"
        // (design ch. 05) has nothing behind it on this endpoint. Left null rather than faked, and
        // recorded as a contract gap in docs/DESIGN_PARITY_AUDIT.md.
        registeredCount = null,
    )
}

/**
 * Parses an ISO-8601 instant from the wire.
 *
 * Returns `null` rather than throwing: one unparseable timestamp must cost that row its time
 * label, not the whole list its page. The contract says UTC ISO-8601, so this is a guard against a
 * server change, not an expected branch.
 *
 * @return the instant, or `null` when the value is not parseable.
 */
private fun String.toInstantOrNull(): Instant? =
    runCatching { Instant.parse(this) }.getOrNull()

/**
 * The Einsatz's radio plan.
 *
 * Lifted out of [toModel] rather than nested in it: the mapper had grown past detekt's length
 * limit, and a frequency without an id is a row nothing can address, so it is dropped here.
 *
 * @receiver what the server sent.
 * @return the frequencies, in the server's order.
 */
private fun MissionDto.frequencyModels(): List<MissionFrequency> =
    frequencies.orEmpty().mapNotNull { frequency ->
        frequency.id?.let {
            MissionFrequency(
                id = it,
                type = frequency.frequencyType?.name,
                value = frequency.name.orEmpty(),
            )
        }
    }

/**
 * One manager as the Verwaltung tab holds them.
 *
 * A row without an id is dropped: the id is what a removal addresses, so a chip without one is a
 * name nobody can act on.
 *
 * @receiver what the server sent.
 * @return the manager, or `null` when it carries no id.
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
 * @param requestedId the id that was asked for, used when the server omits its own. A detail read
 *   is addressed by id, so the answer is about that Einsatz whether or not it repeats it — which
 *   is why this cannot fail for want of one, and why there is no error branch for it.
 * @return the Einsatz.
 */
private fun MissionDto.toModel(requestedId: String): MissionDetail {
    val missionId = id ?: requestedId
    return MissionDetail(
        id = missionId,
        name = name.takeIf { it.isNotBlank() } ?: missionId,
        // Absent for an outsider read (ADR-0034) rather than missing — the screen omits the
        // section instead of showing an empty one.
        description = description?.takeIf { it.isNotBlank() },
        status = MissionStatus.from(status),
        rawStatus = status,
        meetingTime = meetingTime?.toInstantOrNull(),
        plannedStartTime = plannedStartTime?.toInstantOrNull(),
        actualStartTime = actualStartTime?.toInstantOrNull(),
        plannedEndTime = plannedEndTime?.toInstantOrNull(),
        isInternal = isInternal ?: false,
        meetingPoint = meetingPoint?.takeIf { it.isNotBlank() },
        calendarLink = calendarLink?.takeIf { it.isNotBlank() },
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
        // The server's own verdict, not a role check repeated here. An absent field means "no",
        // so a server that predates the flag locks the manager actions instead of offering writes
        // it would refuse.
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
 * The Einsatz's structure, over HTTP.
 *
 * A second class rather than more methods on [MissionRepository], which had reached the point where
 * one type carried the list, the detail, the books, the roster, the Einsatz's own record **and**
 * everything it is made of. They share the same [ApiReader] and the same base URL; what differs is
 * what a caller has to depend on.
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

    override suspend fun addFrequency(
        missionId: String,
        frequencyTypeId: String,
        value: String,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.post(
                "${missionPath(missionId)}/frequencies",
                AddFrequencyRequest(frequencyTypeId = frequencyTypeId, value = KrtDecimal(BigDecimal(value))),
                AddFrequencyRequest.serializer(),
                MissionDto.serializer(),
            ),
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
                        frequencies =
                            result.value.mapNotNull { dto ->
                                dto.id?.let {
                                    MissionFrequency(
                                        id = it,
                                        type = dto.frequencyType?.name,
                                        value = dto.name.orEmpty(),
                                    )
                                }
                            },
                    ),
                )
            }
        }

    override suspend fun removeFrequency(
        missionId: String,
        frequencyId: String,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.delete("${missionPath(missionId)}/frequencies/$frequencyId", MissionDto.serializer()),
        )

    override suspend fun addUnit(
        missionId: String,
        name: String,
        highValue: Boolean,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.post(
                "${missionPath(missionId)}/units",
                AddUnitRequest(name = name, highValueUnit = highValue),
                AddUnitRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun updateUnit(
        missionId: String,
        unitId: String,
        name: String,
        highValue: Boolean,
        version: Long,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.put(
                "${missionPath(missionId)}/units/$unitId",
                // The version is echoed, not omitted. `UpdateUnitRequest` makes it nullable and the
                // server treats an absent one as "do not check" — which turns a concurrent rename
                // into a silent overwrite instead of the 409 the counter exists to raise.
                UpdateUnitRequest(name = name, highValueUnit = highValue, version = version),
                UpdateUnitRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun setCrewRoles(
        missionId: String,
        unitId: String,
        crewId: String,
        jobTypeIds: Set<String>,
        version: Long,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.put(
                "${missionPath(missionId)}/units/$unitId/crew/$crewId",
                UpdateCrewRequest(jobTypeIds = jobTypeIds, version = version),
                UpdateCrewRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun removeUnit(
        missionId: String,
        unitId: String,
    ): ApiResult<MissionDetail> =
        oneMission(missionId, reader.delete("${missionPath(missionId)}/units/$unitId", MissionDto.serializer()))

    override suspend fun addCrew(
        missionId: String,
        unitId: String,
        participantId: String,
        jobTypeIds: Set<String>,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.post(
                "${missionPath(missionId)}/units/$unitId/crew",
                AddCrewRequest(participantId = participantId, jobTypeIds = jobTypeIds),
                AddCrewRequest.serializer(),
                MissionDto.serializer(),
            ),
        )

    override suspend fun removeCrew(
        missionId: String,
        unitId: String,
        crewId: String,
    ): ApiResult<MissionDetail> =
        oneMission(
            missionId,
            reader.delete("${missionPath(missionId)}/units/$unitId/crew/$crewId", MissionDto.serializer()),
        )
}

/**
 * Folds a write's answer back into the model.
 *
 * The Einsatz's writes answer with the **whole** Einsatz, so a screen swaps one object rather than
 * re-reading — which also means every other section's counter arrives fresh, and a manager can make
 * two edits in a row without a 409 from a version they never saw.
 *
 * At file scope because both [MissionRepository] and [MissionStructureRepository] fold the same
 * answer; two copies would be two places for the id fallback to drift.
 *
 * @param missionId the Einsatz, for the id fallback.
 * @param result what the write answered.
 * @return the Einsatz, or the failure unchanged.
 */
private fun oneMission(
    missionId: String,
    result: ApiResult<MissionDto>,
): ApiResult<MissionDetail> =
    when (result) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> ApiResult.Success(result.value.toModel(missionId))
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
 * An absent counter becomes `0`, which is deliberately **not** a value the server ever issues: a
 * write carrying it is refused with a `409` rather than silently overwriting whatever is there. The
 * three sections each have their own, so a stale one only ever collides with an edit of that same
 * section.
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
        // The server redacts identity for an outsider, so all three can legitimately be absent.
        name = user?.effectiveName ?: user?.displayName ?: guestName.orEmpty(),
        role = plannedMissionJobType?.name ?: desiredMissionJobType?.name,
        // A start time is what a check-in writes; there is no separate flag on the wire.
        checkedIn = startTime != null,
        startTime = startTime,
        endTime = endTime,
        comment = comment?.takeIf { it.isNotBlank() },
        // Absent means the server stated no preference, which is a different thing from "pays
        // out": the screen shows nothing rather than claiming one of the two.
        donating = payoutPreference?.let { it == MissionParticipantDto.PayoutPreference.DONATE },
        // Both job types are carried separately even though `role` collapses them for display. A
        // manager's write has to send the row whole (see setPlannedFunction), so the one it is not
        // changing must survive the round trip.
        desiredJobTypeId = desiredMissionJobType?.id,
        desiredJobName = desiredMissionJobType?.name,
        plannedJobTypeId = plannedMissionJobType?.id,
        // A row with no version cannot be written to. 0 is not a valid server version, so the
        // write fails loudly with a 409 rather than silently overwriting a concurrent edit.
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
        // The sign is read once, here, from the server's own classification. Deriving it a second
        // time from the amount's sign would let the two disagree.
        income = type?.value.equals("INCOME", ignoreCase = true),
        amount = amount?.toString().orEmpty(),
        note = note?.takeIf { it.isNotBlank() },
        participantName = participant?.user?.effectiveName ?: participant?.guestName,
        participantId = participant?.id,
        version = version,
    )
}

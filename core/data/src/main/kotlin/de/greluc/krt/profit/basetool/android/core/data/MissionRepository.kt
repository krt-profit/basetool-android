/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionFinanceTotalsDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionListDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionParticipantDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MissionUnitDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMissionFinanceEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMissionListDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * What the member has narrowed the Einsatz list to.
 *
 * A value type rather than a bag of parameters so the screen can hold one object, compare two for
 * equality (which is what decides whether a re-fetch is even needed) and reset to [NONE] in one
 * assignment.
 *
 * @property text the free-text name fragment, blank when the member has not searched
 * @property statuses the statuses to include; empty means "whatever the server offers", which for a
 *   member is all of them and for a role-less outsider is `PLANNED` + `ACTIVE`
 * @property includePast whether Einsätze that already started are shown
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
 * The Einsatz list, as a seam.
 *
 * Separate from its HTTP implementation so the list screen's rules — debouncing, paging, what an
 * empty result means versus a failed one — can be exercised without a socket.
 */
interface MissionSource {
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
 * @property clock the server-corrected clock, used only to turn "Vergangene aus" into a lower bound
 */
class MissionRepository(
    private val reader: ApiReader,
    private val clock: ServerClock,
) : MissionSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     * @param clock the server-corrected clock
     */
    constructor(httpClient: OkHttpClient, baseUrl: String, clock: ServerClock) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
        clock,
    )

    /**
     * Reads one page of Einsätze.
     *
     * "Vergangene aus" is expressed as a lower bound of **now on the server's clock**, not the
     * device's. A phone whose clock is a few minutes fast would otherwise hide an Einsatz that is
     * about to start — the one case where the member most needs to see it.
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
                query.statuses.filter { it != MissionStatus.UNKNOWN }
                    .forEach { add(STATUS_PARAM to it.name) }
                val lowerBound = query.from ?: clock.now().takeUnless { query.includePast }
                lowerBound?.let { add(START_PARAM to it.toString()) }
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

        private const val SEARCH_PATH = "/api/v1/missions/search"

        /**
         * The detail path for one Einsatz.
         *
         * @param id the Einsatz's id.
         * @return the path.
         */
        private fun missionPath(id: String) = "/api/v1/missions/$id"

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
        operationName = operation?.name,
        orgUnitName = owningSquadron?.name,
        orgUnitShorthand = owningSquadron?.shorthand,
        partyLeadName = partyLeadUser?.effectiveName ?: partyLeadUser?.displayName ?: partyLeadGuestName,
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
        frequencies =
            frequencies.orEmpty().mapNotNull { frequency ->
                val frequencyId = frequency.id ?: return@mapNotNull null
                MissionFrequency(
                    id = frequencyId,
                    type = frequency.frequencyType?.name,
                    value = frequency.name.orEmpty(),
                )
            },
    )
}

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
        // The server redacts identity for an outsider, so all three can legitimately be absent.
        name = user?.effectiveName ?: user?.displayName ?: guestName.orEmpty(),
        role = plannedMissionJobType?.name ?: desiredMissionJobType?.name,
        // A start time is what a check-in writes; there is no separate flag on the wire.
        checkedIn = startTime != null,
        comment = comment?.takeIf { it.isNotBlank() },
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
                )
            },
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
    )
}

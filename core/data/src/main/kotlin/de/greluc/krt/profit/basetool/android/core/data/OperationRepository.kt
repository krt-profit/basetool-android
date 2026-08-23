/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationFinanceSummaryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationPayoutDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationPayoutStatusDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationPayoutStatusUpdateDto
import de.greluc.krt.profit.basetool.android.core.contract.model.OperationPayoutSummaryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseOperationDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * What the member has narrowed the Operationen list to.
 *
 * Deliberately **not** the same type as [MissionQuery] even though the chip row above both lists is
 * the same control. An Operation has no start time of its own — the server filters on its earliest
 * and latest linked Einsatz — and "Vergangene aus" therefore has no meaning here: the list's second
 * group *is* the finished ones. Sharing one type would have carried a flag that silently does
 * nothing on half the screen.
 *
 * @property text the free-text name fragment, blank when the member has not searched
 * @property statuses the statuses to include; empty means every status the caller may see
 * @property from lower bound, matched against the earliest linked Einsatz's planned start
 * @property until upper bound, matched against the latest linked Einsatz's planned end
 */
data class OperationQuery(
    val text: String = "",
    val statuses: Set<OperationStatus> = emptySet(),
    val from: Instant? = null,
    val until: Instant? = null,
) {
    /** Whether the member has narrowed anything, which decides if "zurücksetzen" is offered. */
    val isNarrowed: Boolean
        get() = this != NONE

    companion object {
        /** The unnarrowed default. */
        val NONE = OperationQuery()
    }
}

/**
 * Everything the Operation detail draws, read in one go.
 *
 * **The three reads succeed or fail together, and that is the difference from the Einsatz detail.**
 * There, the Finanzen tab is guarded by a *second* permission (`isMemberOrAbove` on top of
 * `canSeeMission`), so it has its own load state and its own refusal. Here all three endpoints
 * carry the identical `isAuthenticated() and canSeeOperation(#id)` gate: a member who may open the
 * Operation may read all of it, so splitting the states would model a case the server cannot
 * produce — and the head itself needs the payouts, because the participant count and the per-head
 * share come from there.
 *
 * @property detail the head
 * @property rollup the Finanz-Rollup and the per-Einsatz results
 * @property payouts the payout rows
 */
data class OperationOverview(
    val detail: OperationDetail,
    val rollup: OperationRollup,
    val payouts: OperationPayouts,
)

/**
 * The Operationen reads, as a seam.
 *
 * Separate from the HTTP implementation so the screens' rules can be exercised without a socket.
 */
interface OperationSource {
    /**
     * Reads one page of Operationen matching [query].
     *
     * @param query what the member narrowed to.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun search(
        query: OperationQuery,
        page: Int = 0,
        pageSize: Int = OperationRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<OperationPage>

    /**
     * Reads one Operation in full.
     *
     * @param id the Operation's id.
     * @return the head, the roll-up and the payouts, or the first failure. `Forbidden` is the
     *   ordinary answer for an Operation outside the caller's scope, `NotFound` for a stale link.
     */
    suspend fun overview(id: String): ApiResult<OperationOverview>

    /**
     * Confirms that one participant's share has been paid out, or takes that back.
     *
     * @param operationId the Operation.
     * @param participantKey the payout row's key, unique within the Operation.
     * @param paidOut whether it should end up confirmed.
     * @return success, or the classified failure. `403` is ordinary rather than exceptional:
     *   confirming needs the mission-manager grant, and **taking a confirmation back** needs an
     *   officer or an admin on top — a distinction the app cannot make for itself.
     */
    suspend fun setPaidOut(
        operationId: String,
        participantKey: String,
        paidOut: Boolean,
    ): ApiResult<Unit>
}

/**
 * Reads Operationen from the backend.
 *
 * `/operations/search` rather than the plain `/operations`, for the reason [MissionRepository]
 * gives: the plain list takes only paging, so every filter in the chip row would have to be applied
 * to a page the server had already truncated.
 *
 * Nothing is cached. A payout that was marked as paid while the member had the screen open is
 * exactly the change they came to see.
 *
 * @property reader performs the calls and classifies their failures
 */
class OperationRepository(
    private val reader: ApiReader,
) : OperationSource {
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
     * Reads one page of Operationen.
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
        query: OperationQuery,
        page: Int,
        pageSize: Int,
    ): ApiResult<OperationPage> {
        val params =
            buildList {
                query.text.trim().takeIf { it.isNotEmpty() }?.let { add(QUERY_PARAM to it) }
                query.statuses.filter { it != OperationStatus.UNKNOWN }
                    .forEach { add(STATUS_PARAM to it.name) }
                query.from?.let { add(START_PARAM to it.toString()) }
                query.until?.let { add(END_PARAM to it.toString()) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
                add(SORT_PARAM to DEFAULT_SORT)
            }

        return when (val result = reader.get(SEARCH_PATH, params, PageResponseOperationDto.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    /**
     * Reads one Operation's head, roll-up and payouts.
     *
     * @param id the Operation's id.
     * @return the overview, or the first failure.
     */
    override suspend fun overview(id: String): ApiResult<OperationOverview> =
        when (val head = reader.get(operationPath(id), OperationDto.serializer())) {
            is ApiResult.Failure -> head
            is ApiResult.Success -> overviewWith(id, head.value.toModel(id))
        }

    /**
     * Fetches the roll-up and then the payouts, folding both onto the already-read head.
     *
     * Split out so [overview] reads as the one decision it makes rather than as a chain of early
     * returns.
     *
     * @param id the Operation's id.
     * @param detail the head already read.
     * @return the overview, or the first failure of the two remaining reads.
     */
    private suspend fun overviewWith(
        id: String,
        detail: OperationDetail,
    ): ApiResult<OperationOverview> =
        when (val rollup = reader.get(financeSummaryPath(id), OperationFinanceSummaryDto.serializer())) {
            is ApiResult.Failure -> rollup
            is ApiResult.Success -> payoutsWith(id, detail, rollup.value.toModel())
        }

    override suspend fun setPaidOut(
        operationId: String,
        participantKey: String,
        paidOut: Boolean,
    ): ApiResult<Unit> =
        when (
            val result =
                reader.put(
                    "${payoutsPath(operationId)}/paid-out",
                    OperationPayoutStatusUpdateDto(
                        participantKey = participantKey,
                        paidOut = paidOut,
                    ),
                    OperationPayoutStatusUpdateDto.serializer(),
                    OperationPayoutStatusDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    /**
     * Fetches the payouts and completes the overview.
     *
     * @param id the Operation's id.
     * @param detail the head already read.
     * @param rollup the roll-up already read.
     * @return the overview, or the payouts' failure.
     */
    private suspend fun payoutsWith(
        id: String,
        detail: OperationDetail,
        rollup: OperationRollup,
    ): ApiResult<OperationOverview> =
        when (val payouts = reader.get(payoutsPath(id), OperationPayoutSummaryDto.serializer())) {
            is ApiResult.Failure -> payouts
            is ApiResult.Success -> ApiResult.Success(OperationOverview(detail, rollup, payouts.value.toModel()))
        }

    companion object {
        /** Rows per page, sized like the Einsatz list for the same reason. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /**
         * The server's sort, named explicitly rather than left to the default.
         *
         * `name` is on the backend's sort whitelist. The list groups running before finished, so
         * within a group an alphabetical order is what a member can scan; the server's own default
         * is creation date, which puts rows in an order nothing on the screen explains.
         */
        const val DEFAULT_SORT: String = "name,asc"

        /** Log subsystem. Search terms are member input and never reach the log. */
        private const val LOG_TAG = "operations"

        private const val SEARCH_PATH = "/api/v1/operations/search"
        private const val QUERY_PARAM = "query"
        private const val STATUS_PARAM = "status"
        private const val START_PARAM = "start"
        private const val END_PARAM = "end"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val SORT_PARAM = "sort"

        /**
         * The detail path for one Operation.
         *
         * @param id the Operation's id.
         * @return the path.
         */
        private fun operationPath(id: String) = "/api/v1/operations/$id"

        /**
         * The finance roll-up path for one Operation.
         *
         * @param id the Operation's id.
         * @return the path.
         */
        private fun financeSummaryPath(id: String) = "/api/v1/operations/$id/finance-summary"

        /**
         * The payouts path for one Operation.
         *
         * @param id the Operation's id.
         * @return the path.
         */
        private fun payoutsPath(id: String) = "/api/v1/operations/$id/payouts"
    }
}

/**
 * Maps a page of wire rows onto the model.
 *
 * @param page the page index that was requested, used because the envelope's own is optional.
 * @return the page, without rows the server sent without an id.
 */
private fun PageResponseOperationDto.toModel(page: Int): OperationPage =
    OperationPage(
        operations = content.orEmpty().mapNotNull { it.toRow() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one wire row onto the model.
 *
 * @return the row, or `null` when it has no id and therefore cannot be opened.
 */
private fun OperationDto.toRow(): Operation? {
    val rowId = id ?: return null
    return Operation(
        id = rowId,
        name = name.orEmpty(),
        status = OperationStatus.from(status?.value),
        rawStatus = status?.value,
        description = description,
    )
}

/**
 * Maps the wire head onto the model.
 *
 * @param requestedId the id that was asked for, used because the payload's own is optional.
 * @return the head.
 */
private fun OperationDto.toModel(requestedId: String): OperationDetail =
    OperationDetail(
        id = id ?: requestedId,
        name = name.orEmpty(),
        status = OperationStatus.from(status?.value),
        rawStatus = status?.value,
        description = description,
        payoutPreliminary = payoutPreliminary,
    )

/**
 * Maps the wire roll-up onto the model.
 *
 * A missing `truncated` is read as `false`: the field is a warning, and inventing one where the
 * server sent none would put a caveat on a complete list.
 *
 * @return the roll-up.
 */
private fun OperationFinanceSummaryDto.toModel(): OperationRollup =
    OperationRollup(
        total = totalSum?.toString(),
        truncated = truncated == true,
        missions =
            missions.orEmpty().map {
                OperationMissionResult(
                    missionId = it.missionId,
                    missionName = it.missionName.orEmpty(),
                    total = it.totalSum?.toString().orEmpty(),
                )
            },
    )

/**
 * Maps the wire payout summary onto the model.
 *
 * @return the payouts, in the server's order — it sorts by name, and re-sorting on the device would
 *   make two members looking at the same screen see different lists.
 */
private fun OperationPayoutSummaryDto.toModel(): OperationPayouts =
    OperationPayouts(
        totalDonations = totalDonations?.toString(),
        rows = payouts.orEmpty().map { it.toModel() },
    )

/**
 * Maps one wire payout row onto the model.
 *
 * @return the row.
 */
private fun OperationPayoutDto.toModel(): OperationPayout =
    OperationPayout(
        participantId = participantId,
        participantName = participantName.orEmpty(),
        donating = payoutPreference == OperationPayoutDto.PayoutPreference.DONATE,
        share = shareAmount?.toString(),
        donated = donatedAmount?.toString(),
        payout = payoutAmount?.toString(),
        paidOut = paidOut == true,
    )

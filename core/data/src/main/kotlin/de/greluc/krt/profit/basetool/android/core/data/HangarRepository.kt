/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseShipDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseSquadronShipOverviewDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ShipDto
import de.greluc.krt.profit.basetool.android.core.contract.model.SquadronShipOverviewDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import okhttp3.OkHttpClient

/**
 * One ship in the member's hangar.
 *
 * The nested `shipType`, `manufacturer` and `location` of the wire model are flattened to the names
 * the card shows. Their ids and descriptions are not carried: a read-only card cannot use them, and
 * a model that mirrors the wire hides what the screen actually needs.
 *
 * @property id the ship's id
 * @property name the member's own name for it, or `null` when they gave none
 * @property typeName the ship type, e.g. "Carrack"
 * @property manufacturerName the maker, e.g. "Anvil Aerospace", or `null` when the type carries none
 * @property insurance the insurance as the server words it, e.g. "LTI", or `null`
 * @property locationName where it is parked, or `null`
 * @property fitted whether it is equipped and ready for an Einsatz
 */
data class Ship(
    val id: String,
    val name: String?,
    val typeName: String,
    val manufacturerName: String?,
    val insurance: String?,
    val locationName: String?,
    val fitted: Boolean,
)

/**
 * One ship type in the org-unit aggregate.
 *
 * @property typeName the ship type
 * @property manufacturerName the maker, or `null`
 * @property count how many the org unit has
 * @property fittedCount how many of them are fitted
 */
data class ShipTypeSummary(
    val typeName: String,
    val manufacturerName: String?,
    val count: Long,
    val fittedCount: Long,
)

/**
 * One page of ships.
 *
 * @property ships the rows on this page
 * @property page the zero-based page index
 * @property totalPages how many pages exist
 * @property totalElements how many ships the filter matches in total
 */
data class ShipPage(
    val ships: List<Ship>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * One page of the org-unit aggregate.
 *
 * @property types the rows on this page
 * @property page the zero-based page index
 * @property totalPages how many pages exist
 * @property totalElements how many ship types the filter matches in total
 */
data class ShipTypePage(
    val types: List<ShipTypeSummary>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * The hangar reads, as a seam.
 */
interface HangarSource {
    /**
     * Reads one page of the caller's own ships.
     *
     * @param search a ship-type fragment, blank for no filter.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun myShips(
        search: String = "",
        page: Int = 0,
        pageSize: Int = HangarRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<ShipPage>

    /**
     * Reads one page of the active org unit's aggregate, one row per ship type.
     *
     * @param search a ship-type fragment, blank for no filter.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or a failure the caller can show.
     */
    suspend fun orgOverview(
        search: String = "",
        page: Int = 0,
        pageSize: Int = HangarRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<ShipTypePage>
}

/**
 * Reads the hangar from the backend.
 *
 * **`/my-ships`, never `/ships`.** The latter reads every member's ships and is gated on a
 * permission most members do not have; the app's screen is the member's own hangar plus the
 * aggregate their org unit already publishes. Which org unit that is follows from the
 * `X-Active-Org-Unit-Id` header the interceptor sets, not from anything sent here.
 *
 * @property reader performs the calls and classifies their failures
 */
class HangarRepository(
    private val reader: ApiReader,
) : HangarSource {
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
     * Reads the caller's ships.
     *
     * @param search a ship-type fragment.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun myShips(
        search: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<ShipPage> =
        when (
            val result =
                reader.get(
                    MY_SHIPS_PATH,
                    params(search, page, pageSize),
                    PageResponseShipDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }

    /**
     * Reads the org unit's aggregate.
     *
     * @param search a ship-type fragment.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    override suspend fun orgOverview(
        search: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<ShipTypePage> =
        when (
            val result =
                reader.get(
                    OVERVIEW_PATH,
                    params(search, page, pageSize),
                    PageResponseSquadronShipOverviewDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }

    /**
     * Builds the query both reads take.
     *
     * @param search a ship-type fragment; a blank one is left off the wire entirely rather than
     *   sent as an empty filter.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the parameters, raw and unencoded — `HttpUrl` encodes them exactly once.
     */
    private fun params(
        search: String,
        page: Int,
        pageSize: Int,
    ): List<Pair<String, String>> =
        buildList {
            search.trim().takeIf { it.isNotEmpty() }?.let { add(SEARCH_PARAM to it) }
            add(PAGE_PARAM to page.toString())
            add(SIZE_PARAM to pageSize.toString())
        }

    companion object {
        /** Rows per page, sized for a phone like the other lists. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /** Log subsystem. A ship's name is member input and never reaches the log. */
        private const val LOG_TAG = "hangar"

        private const val MY_SHIPS_PATH = "/api/v1/hangar/my-ships"
        private const val OVERVIEW_PATH = "/api/v1/hangar/squadron-overview"
        private const val SEARCH_PARAM = "search"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
    }
}

/**
 * Maps a page of ships onto the model.
 *
 * @param page the page index that was requested, used because the envelope's own is optional.
 * @return the page, without rows the server sent without an id.
 */
private fun PageResponseShipDto.toModel(page: Int): ShipPage =
    ShipPage(
        ships = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one ship onto the model.
 *
 * @return the ship, or `null` when it has no id.
 */
private fun ShipDto.toModel(): Ship? {
    val rowId = id ?: return null
    return Ship(
        id = rowId,
        name = name?.trim()?.takeIf { it.isNotEmpty() },
        typeName = shipType?.name.orEmpty(),
        manufacturerName = shipType?.manufacturer?.name,
        insurance = insurance?.trim()?.takeIf { it.isNotEmpty() },
        locationName = location?.name,
        fitted = fitted == true,
    )
}

/**
 * Maps a page of the aggregate onto the model.
 *
 * @param page the page index that was requested.
 * @return the page.
 */
private fun PageResponseSquadronShipOverviewDto.toModel(page: Int): ShipTypePage =
    ShipTypePage(
        types = content.orEmpty().map { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one aggregate row onto the model.
 *
 * A row with no ship type is kept with an empty name rather than dropped: it still carries a count
 * the org unit owns, and dropping it would quietly lower what the screen adds up to.
 *
 * @return the row.
 */
private fun SquadronShipOverviewDto.toModel(): ShipTypeSummary =
    ShipTypeSummary(
        typeName = shipType?.name.orEmpty(),
        manufacturerName = shipType?.manufacturer?.name,
        count = count ?: 0L,
        fittedCount = fittedCount ?: 0L,
    )

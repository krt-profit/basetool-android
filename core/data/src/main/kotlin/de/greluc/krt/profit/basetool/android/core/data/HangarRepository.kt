/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.LocationDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseShipDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseShipTypeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseSquadronShipOverviewDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ShipDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ShipRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ShipTypeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.SquadronShipOverviewDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
 * @property typeId the ship type's id — carried since phase 3 because an edit has to send it back
 * @property locationId the place's id, or `null`; likewise
 * @property version the optimistic lock, echoed on the next save
 */
data class Ship(
    val id: String,
    val name: String?,
    val typeName: String,
    val manufacturerName: String?,
    val insurance: String?,
    val locationName: String?,
    val fitted: Boolean,
    val typeId: String? = null,
    val locationId: String? = null,
    val version: Long? = null,
)

/**
 * A hull the member can pick when adding a ship.
 *
 * @property id what a write sends
 * @property name the hull
 * @property manufacturerName the maker, or `null` — what tells two similar hulls apart
 */
data class ShipTypeOption(
    val id: String,
    val name: String,
    val manufacturerName: String?,
)

/**
 * A place a ship can be parked.
 *
 * @property id what a write sends
 * @property name the place
 */
data class HomeLocation(
    val id: String,
    val name: String,
)

/**
 * What a ship save carries.
 *
 * @property name the member's own name for it, or `null`
 * @property typeId the hull
 * @property insurance `LTI`, or a whole number of months as text — the server accepts nothing else
 * @property locationId where it is parked, or `null`
 * @property fitted whether it is ready
 */
data class ShipDraft(
    val name: String?,
    val typeId: String,
    val insurance: String,
    val locationId: String?,
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

    /**
     * Adds a ship to the member's own hangar.
     *
     * @param draft what the member entered.
     * @return the saved ship, or the classified failure.
     */
    suspend fun create(draft: ShipDraft): ApiResult<Ship>

    /**
     * Changes one of the member's own ships.
     *
     * @param id the ship.
     * @param version the version the row was read at.
     * @param draft what the member entered.
     * @return the saved ship, or [de.greluc.krt.profit.basetool.android.core.network.ApiError
     *   .OptimisticLock] when somebody else saved first.
     */
    suspend fun update(
        id: String,
        version: Long?,
        draft: ShipDraft,
    ): ApiResult<Ship>

    /**
     * Removes one of the member's own ships.
     *
     * @param id the ship.
     * @return success, or the classified failure.
     */
    suspend fun delete(id: String): ApiResult<Unit>

    /**
     * Reads the hull catalogue for the editor's picker.
     *
     * @param query a name fragment, or blank for the first page.
     * @return the hulls, capped by the page size.
     */
    suspend fun shipTypes(query: String): ApiResult<List<ShipTypeOption>>

    /**
     * Reads the places a ship can be parked.
     *
     * @return the places; the server returns the whole list, which is short.
     */
    suspend fun homeLocations(): ApiResult<List<HomeLocation>>

    /**
     * Imports ships from a CCU-Game Fleetview export.
     *
     * @param fileName what the export was called, sent with the part so the server's log says
     *   where the rows came from.
     * @param bytes the export's content, whether picked as a file or pasted into the box.
     * @return what the server made of it, or the classified failure.
     */
    suspend fun importFleetview(
        fileName: String,
        bytes: ByteArray,
    ): ApiResult<FleetImportResult>

    /**
     * Deletes every ship the caller owns.
     *
     * @return success, or the classified failure.
     */
    suspend fun clearHangar(): ApiResult<Unit>

    /**
     * Moves the caller's whole fleet to one home location.
     *
     * @param locationId the location every ship is to be based at.
     * @return success, or the classified failure.
     */
    suspend fun setHomeLocationForAll(locationId: String): ApiResult<Unit>
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

    override suspend fun create(draft: ShipDraft): ApiResult<Ship> =
        save(SHIPS_PATH, draft.toRequest(version = null), post = true)

    override suspend fun update(
        id: String,
        version: Long?,
        draft: ShipDraft,
    ): ApiResult<Ship> = save("$SHIPS_PATH/$id", draft.toRequest(version), post = false)

    override suspend fun delete(id: String): ApiResult<Unit> = reader.delete("$SHIPS_PATH/$id")

    override suspend fun importFleetview(
        fileName: String,
        bytes: ByteArray,
    ): ApiResult<FleetImportResult> =
        when (
            val result =
                reader.postFile(
                    path = FLEETVIEW_IMPORT_PATH,
                    partName = "file",
                    fileName = fileName,
                    bytes = bytes,
                    mediaType = JSON_MEDIA_TYPE,
                    deserializer = FleetImportResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun clearHangar(): ApiResult<Unit> = reader.delete(SHIPS_PATH)

    override suspend fun setHomeLocationForAll(locationId: String): ApiResult<Unit> =
        reader.postAccepted(
            path = BULK_HOME_LOCATION_PATH,
            body = SetHomeLocationRequest(locationId = locationId),
            bodySerializer = SetHomeLocationRequest.serializer(),
        )

    override suspend fun shipTypes(query: String): ApiResult<List<ShipTypeOption>> {
        // The catalogue endpoint has no search parameter of its own — the app asks for one page and
        // narrows it here. That is honest only because the page is the whole visible catalogue at
        // this size; when it stops being, the screen has to say so rather than filter silently.
        val params =
            listOf(PAGE_PARAM to "0", SIZE_PARAM to SHIP_TYPE_PAGE_SIZE.toString(), SORT_PARAM to TYPE_SORT)
        return when (
            val result = reader.get(SHIP_TYPES_PATH, params, PageResponseShipTypeDto.serializer())
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                val term = query.trim()
                val all = result.value.content.orEmpty().mapNotNull { it.toOption() }
                ApiResult.Success(
                    if (term.isEmpty()) all else all.filter { it.matches(term) },
                )
            }
        }
    }

    override suspend fun homeLocations(): ApiResult<List<HomeLocation>> =
        when (
            val result =
                reader.get(HOME_LOCATIONS_PATH, ListSerializer(LocationDto.serializer()))
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.mapNotNull { it.toModel() })
        }

    /**
     * Sends one ship payload.
     *
     * @param path where to send it.
     * @param body the payload.
     * @param post whether this is a create; an update is a `PUT`.
     * @return the saved ship, or the classified failure.
     */
    private suspend fun save(
        path: String,
        body: ShipRequestDto,
        post: Boolean,
    ): ApiResult<Ship> {
        val result =
            if (post) {
                reader.post(path, body, ShipRequestDto.serializer(), ShipDto.serializer())
            } else {
                reader.put(path, body, ShipRequestDto.serializer(), ShipDto.serializer())
            }
        return when (result) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                result.value.toModel()?.let { ApiResult.Success(it) }
                    // A saved ship with no id is a server that answered something this client
                    // cannot key a list by. Reported as a broken contract, not silently dropped.
                    ?: ApiResult.Failure(ApiError.Server(status = HTTP_OK_STATUS, problem = null))
            }
        }
    }

    companion object {
        /** Rows per page, sized for a phone like the other lists. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /** Log subsystem. A ship's name is member input and never reaches the log. */
        private const val LOG_TAG = "hangar"

        private const val MY_SHIPS_PATH = "/api/v1/hangar/my-ships"
        private const val SHIPS_PATH = "/api/v1/hangar/ships"
        private const val SHIP_TYPES_PATH = "/api/v1/ship-types"
        private const val FLEETVIEW_IMPORT_PATH = "/api/v1/hangar/import/fleetview"
        private const val BULK_HOME_LOCATION_PATH = "/api/v1/hangar/ships/home-location"
        private const val JSON_MEDIA_TYPE = "application/json"
        private const val HOME_LOCATIONS_PATH = "/api/v1/locations/home-locations"
        private const val SORT_PARAM = "sort"
        private const val TYPE_SORT = "name,asc"

        /** How much of the hull catalogue the picker reads in one go. */
        private const val SHIP_TYPE_PAGE_SIZE = 500

        /** The status a successful-but-unusable answer is reported under. */
        private const val HTTP_OK_STATUS = 200
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
        typeId = shipType?.id,
        locationId = location?.id,
        version = version,
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

/**
 * Builds the wire payload.
 *
 * @param version the version read from the server, or `null` on a create.
 * @return the request.
 */
private fun ShipDraft.toRequest(version: Long?): ShipRequestDto =
    ShipRequestDto(
        insurance = insurance,
        shipTypeId = typeId,
        name = name,
        locationId = locationId,
        fitted = fitted,
        version = version,
    )

/**
 * Maps one hull onto the picker's model.
 *
 * @return the option, or `null` without an id — a hull that cannot be sent is not worth offering.
 */
private fun ShipTypeDto.toOption(): ShipTypeOption? {
    val typeId = id ?: return null
    return ShipTypeOption(
        id = typeId,
        name = name.orEmpty(),
        manufacturerName = manufacturer?.name,
    )
}

/**
 * Whether a hull matches what the member typed.
 *
 * Matched on the hull and its maker together, because "Anvil" is how somebody looks for a Carrack
 * they cannot spell.
 *
 * @param term what they typed.
 * @return whether to offer it.
 */
private fun ShipTypeOption.matches(term: String): Boolean =
    name.contains(term, ignoreCase = true) || manufacturerName?.contains(term, ignoreCase = true) == true

/**
 * Maps one place onto the picker's model.
 *
 * @return the place, or `null` without an id.
 */
private fun LocationDto.toModel(): HomeLocation? {
    val placeId = id ?: return null
    return HomeLocation(id = placeId, name = name.orEmpty())
}

/**
 * What the server made of a Fleetview export.
 *
 * The three counts are reported separately rather than summed because they mean different things
 * to the member: imported rows are new ships, duplicates were already in the hangar and are not a
 * fault, and skipped rows are hulls the catalogue does not know — the only group that needs them
 * to do anything. The two lists name the rows behind the last two counts, which is what turns
 * "3 nicht erkannt" into something actionable.
 *
 * @property imported how many ships were created.
 * @property skipped how many rows the server could not match to a hull.
 * @property duplicates how many rows the hangar already held.
 * @property skippedShips the names behind [skipped].
 * @property duplicateShips the names behind [duplicates].
 */
data class FleetImportResult(
    val imported: Int,
    val skipped: Int,
    val duplicates: Int,
    val skippedShips: List<String>,
    val duplicateShips: List<String>,
)

/**
 * The wire shape of a Fleetview import answer.
 *
 * @property importedCount how many ships were created.
 * @property skippedCount how many rows were not recognised.
 * @property duplicateCount how many rows were already present.
 * @property skippedShips the names behind [skippedCount].
 * @property duplicateShips the names behind [duplicateCount].
 */
@Serializable
internal data class FleetImportResponse(
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val duplicateCount: Int = 0,
    val skippedShips: List<String> = emptyList(),
    val duplicateShips: List<String> = emptyList(),
) {
    /**
     * Maps the wire shape onto the model.
     *
     * @return the result as the screen reads it.
     */
    fun toModel(): FleetImportResult =
        FleetImportResult(
            imported = importedCount,
            skipped = skippedCount,
            duplicates = duplicateCount,
            skippedShips = skippedShips,
            duplicateShips = duplicateShips,
        )
}

/**
 * The wire shape of the bulk home-location write.
 *
 * @property locationId the location every ship is to be based at.
 */
@Serializable
internal data class SetHomeLocationRequest(
    val locationId: String,
)

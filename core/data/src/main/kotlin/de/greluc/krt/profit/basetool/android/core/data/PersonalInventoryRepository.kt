/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponsePersonalInventoryItemResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalInventoryItemCreateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalInventoryItemResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalInventoryItemUpdateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.UexLocationDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/** Where a personal item is kept. */
enum class PersonalLocationKind {
    /** A landing zone on a planet or moon. */
    CITY,

    /** An orbital station. */
    SPACE_STATION,

    /** A kind this build does not know. */
    UNKNOWN,

    ;

    companion object {
        /**
         * Maps a server value onto the enum.
         *
         * @param raw the wire value, or `null`.
         * @return the matching constant, or [UNKNOWN] — never an exception: a place the server
         *   learns about later must not make a member's own row unreadable.
         */
        fun from(raw: String?): PersonalLocationKind =
            entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

/**
 * One place an item can be kept, as the picker offers it.
 *
 * @property uexId the UEX identifier, which is what a write sends
 * @property kind city or station — the other half of what a write sends
 * @property name the place itself
 * @property system the star system, shown so two places of the same name can be told apart
 * @property parent the body or station the place belongs to, or `null`
 */
data class PersonalLocation(
    val uexId: Int,
    val kind: PersonalLocationKind,
    val name: String,
    val system: String?,
    val parent: String?,
)

/**
 * One entry in the member's own stock.
 *
 * @property id the server id
 * @property name what the member called it — free text, not a material from the catalogue
 * @property note their own note, or `null`
 * @property quantity how many
 * @property locationUexId the place's UEX id, echoed unchanged on an edit
 * @property locationKind city or station
 * @property locationName the place's name as the server resolved it; `null` when it could not
 * @property version the optimistic lock, echoed on the next save
 */
data class PersonalItem(
    val id: String,
    val name: String,
    val note: String?,
    val quantity: Int,
    val locationUexId: Int?,
    val locationKind: PersonalLocationKind,
    val locationName: String?,
    val version: Long?,
)

/**
 * One page of the member's stock.
 *
 * @property items the rows on this page
 * @property page the zero-based page index
 * @property totalElements how many rows exist in total
 * @property totalPages how many pages exist
 */
data class PersonalItemPage(
    val items: List<PersonalItem>,
    val page: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * What a save carries.
 *
 * A value type rather than five parameters, because the editor holds exactly this and the 409
 * dialog has to hand the same thing back unchanged when the member retries.
 *
 * @property name the item's name
 * @property quantity how many
 * @property locationUexId the chosen place
 * @property locationKind the chosen place's kind
 * @property note the optional note
 */
data class PersonalItemDraft(
    val name: String,
    val quantity: Int,
    val locationUexId: Int,
    val locationKind: PersonalLocationKind,
    val note: String?,
)

/**
 * The member's own stock, as a seam.
 *
 * Separate from its HTTP implementation so the editor's rules — what a 409 does, what an empty
 * search means — can be exercised without a socket.
 */
interface PersonalInventorySource {
    /**
     * Reads one page.
     *
     * @param query a name fragment, or blank for everything.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    suspend fun page(
        query: String = "",
        page: Int = 0,
        pageSize: Int = PersonalInventoryRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<PersonalItemPage>

    /**
     * Creates an entry.
     *
     * @param draft what the member typed.
     * @return the saved row — including the `version` an edit will have to echo.
     */
    suspend fun create(draft: PersonalItemDraft): ApiResult<PersonalItem>

    /**
     * Replaces an entry.
     *
     * @param id the row to replace.
     * @param version the version the member's copy was read at.
     * @param draft what the member typed.
     * @return the saved row, or [de.greluc.krt.profit.basetool.android.core.network.ApiError
     *   .OptimisticLock] when somebody else saved first.
     */
    suspend fun update(
        id: String,
        version: Long,
        draft: PersonalItemDraft,
    ): ApiResult<PersonalItem>

    /**
     * Deletes an entry.
     *
     * @param id the row to delete.
     * @return success, or the classified failure.
     */
    suspend fun delete(id: String): ApiResult<Unit>

    /**
     * Searches places for the editor's picker.
     *
     * @param query what the member typed.
     * @return the matches, capped by the server.
     */
    suspend fun locations(query: String): ApiResult<List<PersonalLocation>>
}

/**
 * Reads and writes the member's own stock.
 *
 * **Everything here is me-scoped by the server.** No path in this family names a user, so the
 * repository never sends an id of one — which is what makes this the safest place to build the
 * app's first writes (owner decision, 2026-08-23: phase 3 in ascending order of risk).
 *
 * Nothing is cached. A member edits this list from the web app too, and a stale row would be saved
 * with a stale `version` — the conflict the optimistic lock exists to catch, manufactured by the
 * client instead of by two people.
 *
 * @property reader performs the calls and classifies their failures
 */
class PersonalInventoryRepository(
    private val reader: ApiReader,
) : PersonalInventorySource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    override suspend fun page(
        query: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<PersonalItemPage> {
        val params =
            buildList {
                query.trim().takeIf { it.isNotEmpty() }?.let { add(QUERY_PARAM to it) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
            }
        return when (
            val result =
                reader.get(PATH, params, PageResponsePersonalInventoryItemResponse.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    override suspend fun create(draft: PersonalItemDraft): ApiResult<PersonalItem> =
        when (
            val result =
                reader.post(
                    PATH,
                    draft.toCreateRequest(),
                    PersonalInventoryItemCreateRequest.serializer(),
                    PersonalInventoryItemResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun update(
        id: String,
        version: Long,
        draft: PersonalItemDraft,
    ): ApiResult<PersonalItem> =
        when (
            val result =
                reader.put(
                    itemPath(id),
                    draft.toUpdateRequest(version),
                    PersonalInventoryItemUpdateRequest.serializer(),
                    PersonalInventoryItemResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun delete(id: String): ApiResult<Unit> = reader.delete(itemPath(id))

    override suspend fun locations(query: String): ApiResult<List<PersonalLocation>> {
        val params = listOf(QUERY_PARAM to query.trim(), LIMIT_PARAM to LOCATION_LIMIT.toString())
        return when (
            val result =
                reader.get(LOCATIONS_PATH, params, ListSerializer(UexLocationDto.serializer()))
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.map { it.toModel() })
        }
    }

    /**
     * Builds one item's path.
     *
     * @param id the row's id.
     * @return the path.
     */
    private fun itemPath(id: String): String = "$PATH/$id"

    companion object {
        /** Rows per page. */
        const val DEFAULT_PAGE_SIZE: Int = 30

        /**
         * How many places the picker asks for.
         *
         * Named here because the number is a cap the member has to be told about: the search says
         * so when it comes back full, rather than pretending the rest do not exist (ADR-0104).
         */
        const val LOCATION_LIMIT: Int = 25

        private const val LOG_TAG = "personal-inventory"
        private const val PATH = "/api/v1/personal-inventory"
        private const val LOCATIONS_PATH = "/api/v1/uex/locations/search"
        private const val QUERY_PARAM = "q"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val LIMIT_PARAM = "limit"
    }
}

/**
 * Maps a page of rows.
 *
 * A row without an id is dropped: it cannot be opened, edited or deleted, so offering it would
 * produce a tap that does nothing. The server's own total is kept, because quietly lowering it
 * would hide the fault.
 *
 * @param page the requested index, which the envelope does not always echo.
 * @return the page.
 */
private fun PageResponsePersonalInventoryItemResponse.toModel(page: Int): PersonalItemPage =
    PersonalItemPage(
        items = content.orEmpty().filter { !it.id.isNullOrBlank() }.map { it.toModel() },
        page = this.page ?: page,
        totalElements = totalElements ?: 0L,
        totalPages = totalPages ?: 0,
    )

/**
 * Maps one row.
 *
 * @return the model.
 */
private fun PersonalInventoryItemResponse.toModel(): PersonalItem =
    PersonalItem(
        id = id.orEmpty(),
        name = name.orEmpty(),
        note = note?.takeIf { it.isNotBlank() },
        quantity = quantity ?: 0,
        locationUexId = locationUexId,
        locationKind = PersonalLocationKind.from(locationType?.value),
        locationName = locationName?.takeIf { it.isNotBlank() },
        version = version,
    )

/**
 * Maps one place.
 *
 * @return the model.
 */
private fun UexLocationDto.toModel(): PersonalLocation =
    PersonalLocation(
        uexId = uexId ?: 0,
        kind = PersonalLocationKind.from(type?.value),
        name = name.orEmpty(),
        system = starSystemName?.takeIf { it.isNotBlank() },
        parent = parentName?.takeIf { it.isNotBlank() },
    )

/**
 * Narrows the app's kind onto the generated create enum.
 *
 * [PersonalLocationKind.UNKNOWN] cannot be saved — it only ever comes from a server value this
 * build does not know, and the editor never offers it — so it falls back to the city, which is
 * unreachable in practice and keeps the mapping total.
 *
 * @return the generated constant.
 */
private fun PersonalLocationKind.toCreateType(): PersonalInventoryItemCreateRequest.LocationType =
    when (this) {
        PersonalLocationKind.SPACE_STATION -> PersonalInventoryItemCreateRequest.LocationType.SPACE_STATION
        else -> PersonalInventoryItemCreateRequest.LocationType.CITY
    }

/**
 * Narrows the app's kind onto the generated update enum.
 *
 * @return the generated constant.
 */
private fun PersonalLocationKind.toUpdateType(): PersonalInventoryItemUpdateRequest.LocationType =
    when (this) {
        PersonalLocationKind.SPACE_STATION -> PersonalInventoryItemUpdateRequest.LocationType.SPACE_STATION
        else -> PersonalInventoryItemUpdateRequest.LocationType.CITY
    }

/**
 * Builds the create payload.
 *
 * @return the request.
 */
private fun PersonalItemDraft.toCreateRequest(): PersonalInventoryItemCreateRequest =
    PersonalInventoryItemCreateRequest(
        name = name,
        quantity = quantity,
        locationUexId = locationUexId,
        locationType = locationKind.toCreateType(),
        note = note,
    )

/**
 * Builds the update payload.
 *
 * @param version the version read from the server, echoed unchanged.
 * @return the request.
 */
private fun PersonalItemDraft.toUpdateRequest(version: Long): PersonalInventoryItemUpdateRequest =
    PersonalInventoryItemUpdateRequest(
        name = name,
        quantity = quantity,
        locationUexId = locationUexId,
        locationType = locationKind.toUpdateType(),
        version = version,
        note = note,
    )

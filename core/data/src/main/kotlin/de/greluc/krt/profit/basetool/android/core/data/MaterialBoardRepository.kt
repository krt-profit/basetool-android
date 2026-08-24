/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialExchangeOfferDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialExchangeReleasableItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialExchangeReleaseRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialRequestCreateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialRequestDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialExchangeOfferDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialRequestDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/** Which half of the board a row belongs to. */
enum class BoardSide {
    /** Somebody has material and is offering it. */
    OFFERS,

    /** Somebody needs material and is asking for it. */
    REQUESTS,
}

/**
 * One row of the board, offer or request.
 *
 * The two server DTOs differ in three fields and agree on everything the screen draws, so they map
 * onto one model. Keeping them apart would duplicate the row composable, the interest toggle and
 * the withdraw action for a difference the member never sees.
 *
 * @property id the row's id.
 * @property side which half it belongs to.
 * @property materialName what is being offered or wanted.
 * @property unitIsPiece whether the amount counts pieces rather than SCU. **Never hardcode SCU** —
 *   an item counted in pieces and labelled „SCU" is a quantity a member would act on.
 * @property amount the offered or requested amount, as the server rendered it.
 * @property quality the offered quality, or the minimum wanted; `null` when none is stated.
 * @property ownerName who posted it.
 * @property ownerOrgUnits their affiliation badges, in the server's order.
 * @property postedAt when, as the server rendered it.
 * @property remark their note, or `null`.
 * @property interestCount how many members have said they can help.
 * @property interestedHandles who they are — **owner-only**, and `null` for everybody else. The
 *   server decides this (REQ-MARKET-006); the app never derives it.
 * @property viewerInterested whether the caller is one of them.
 * @property mine whether the caller posted it.
 * @property version the optimistic lock, echoed on an edit.
 */
data class BoardEntry(
    val id: String,
    val side: BoardSide,
    val materialName: String,
    val unitIsPiece: Boolean,
    val amount: String,
    val quality: Int?,
    val ownerName: String,
    val ownerOrgUnits: List<String>,
    val postedAt: String?,
    val remark: String?,
    val interestCount: Int,
    val interestedHandles: List<String>?,
    val viewerInterested: Boolean,
    val mine: Boolean,
    val version: Long?,
) {
    /**
     * Whether the caller may toggle „Ich kann liefern" on this row.
     *
     * Never on their own: the server refuses it, and offering the control would be an invitation to
     * a `400`. Their own rows get Bearbeiten and Zurückziehen instead.
     */
    val canSignal: Boolean get() = !mine
}

/**
 * One page of one half of the board.
 *
 * @property entries the rows on this page.
 * @property page the zero-based page index.
 * @property totalPages how many pages exist.
 * @property totalElements how many rows the filter matches.
 */
data class BoardPage(
    val entries: List<BoardEntry>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * One of the caller's own Lager entries that could be offered.
 *
 * @property inventoryItemId the entry to release; the offer addresses it by id.
 * @property materialName what it is.
 * @property unitIsPiece whether it counts pieces rather than SCU.
 * @property amount how much is in stock, as the server rendered it.
 * @property quality its quality, or `null`.
 * @property locationName where it is — shown so a member can tell two stacks apart, and **not**
 *   sent to the board: chapter 10 is explicit that place stays off-tool.
 * @property alreadyReleased whether an offer for it already exists.
 */
data class ReleasableStock(
    val inventoryItemId: String,
    val materialName: String,
    val unitIsPiece: Boolean,
    val amount: String,
    val quality: Int?,
    val locationName: String,
    val alreadyReleased: Boolean,
)

/** The Materialbörse reads and writes the app offers, as a seam. */
interface MaterialBoardSource {
    /**
     * Reads one page of one half of the board.
     *
     * @param side which half.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    suspend fun board(
        side: BoardSide,
        page: Int = 0,
        pageSize: Int = MaterialBoardRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<BoardPage>

    /**
     * Says the caller can help, or takes it back.
     *
     * Answers with the **updated row** rather than with nothing, so the screen can replace the one
     * it has instead of re-reading the page: the count, the caller's own flag and the version all
     * move together, and a re-read would also lose the member's scroll position.
     *
     * @param entry the row.
     * @param interested whether the caller can help.
     * @return the row as it now stands, or the classified failure.
     */
    suspend fun setInterest(
        entry: BoardEntry,
        interested: Boolean,
    ): ApiResult<BoardEntry>

    /**
     * Withdraws one of the caller's own rows.
     *
     * @param entry the row.
     * @return the row as it now stands — deactivated — or the classified failure.
     */
    suspend fun withdraw(entry: BoardEntry): ApiResult<BoardEntry>

    /**
     * Reads the caller's own stock that could be offered.
     *
     * @return the entries, or the classified failure.
     */
    suspend fun releasableStock(): ApiResult<List<ReleasableStock>>

    /**
     * Offers one of the caller's own Lager entries.
     *
     * @param inventoryItemId which entry.
     * @param amount how much of it, as typed.
     * @param remark an optional note.
     * @return success, or the classified failure.
     */
    suspend fun createOffer(
        inventoryItemId: String,
        amount: Double,
        remark: String?,
    ): ApiResult<Unit>

    /**
     * Posts a request for a material.
     *
     * @param materialId which material.
     * @param amount how much is wanted.
     * @param minQuality the minimum quality, or `null`.
     * @param remark an optional note.
     * @return success, or the classified failure.
     */
    suspend fun createRequest(
        materialId: String,
        amount: Double,
        minQuality: Int?,
        remark: String?,
    ): ApiResult<Unit>
}

/**
 * The Materialbörse (REQ-APP-MARKET-001…008).
 *
 * **A board that vermittelt interest and nothing else.** Handover and place stay off-tool by
 * design (chapter 10 §3), so nothing here sends or shows a location for a board row — the only
 * place name in this file belongs to the caller's *own* stock, on the sheet where they pick which
 * stack to offer.
 *
 * **Item offers and item requests are read but not created.** `POST /item-offers` and
 * `/material-requests/item` address an item by a `productKey` from the P4K catalogue, which the app
 * has no picker for; both halves render item rows that the web created. Creating one is a phase-5
 * question together with the catalogue browse it needs.
 *
 * @property reader performs the calls and classifies their failures.
 */
class MaterialBoardRepository(
    private val reader: ApiReader,
) : MaterialBoardSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers.
     * @param baseUrl the flavour's API origin.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /** {@inheritDoc} */
    override suspend fun board(
        side: BoardSide,
        page: Int,
        pageSize: Int,
    ): ApiResult<BoardPage> {
        val params = listOf(PAGE_PARAM to page.toString(), SIZE_PARAM to pageSize.toString())
        return when (side) {
            BoardSide.OFFERS -> {
                when (
                    val result =
                        reader.get(
                            OFFERS_PATH,
                            params,
                            PageResponseMaterialExchangeOfferDto.serializer(),
                        )
                ) {
                    is ApiResult.Failure -> result
                    is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
                }
            }

            BoardSide.REQUESTS -> {
                when (
                    val result =
                        reader.get(
                            REQUESTS_PATH,
                            params,
                            PageResponseMaterialRequestDto.serializer(),
                        )
                ) {
                    is ApiResult.Failure -> result
                    is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
                }
            }
        }
    }

    /** {@inheritDoc} */
    override suspend fun setInterest(
        entry: BoardEntry,
        interested: Boolean,
    ): ApiResult<BoardEntry> {
        val path = interestPath(entry)
        return when (entry.side) {
            BoardSide.OFFERS -> {
                val result =
                    if (interested) {
                        reader.post(path, MaterialExchangeOfferDto.serializer())
                    } else {
                        reader.delete(path, MaterialExchangeOfferDto.serializer())
                    }
                result.mapOffer(entry)
            }

            BoardSide.REQUESTS -> {
                val result =
                    if (interested) {
                        reader.post(path, MaterialRequestDto.serializer())
                    } else {
                        reader.delete(path, MaterialRequestDto.serializer())
                    }
                result.mapRequest(entry)
            }
        }
    }

    /** {@inheritDoc} */
    override suspend fun withdraw(entry: BoardEntry): ApiResult<BoardEntry> =
        when (entry.side) {
            BoardSide.OFFERS -> {
                reader
                    .post(deactivatePath(entry), MaterialExchangeOfferDto.serializer())
                    .mapOffer(entry)
            }

            BoardSide.REQUESTS -> {
                reader
                    .post(deactivatePath(entry), MaterialRequestDto.serializer())
                    .mapRequest(entry)
            }
        }

    /** {@inheritDoc} */
    override suspend fun releasableStock(): ApiResult<List<ReleasableStock>> =
        when (
            val result =
                reader.get(
                    RELEASABLE_PATH,
                    ListSerializer(MaterialExchangeReleasableItemDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.mapNotNull { it.toModel() })
        }

    /** {@inheritDoc} */
    override suspend fun createOffer(
        inventoryItemId: String,
        amount: Double,
        remark: String?,
    ): ApiResult<Unit> =
        reader.postAccepted(
            OFFERS_PATH,
            MaterialExchangeReleaseRequest(
                inventoryItemId = inventoryItemId,
                offeredAmount = amount,
                remark = remark?.takeIf { it.isNotBlank() },
            ),
            MaterialExchangeReleaseRequest.serializer(),
        )

    /** {@inheritDoc} */
    override suspend fun createRequest(
        materialId: String,
        amount: Double,
        minQuality: Int?,
        remark: String?,
    ): ApiResult<Unit> =
        reader.postAccepted(
            REQUESTS_PATH,
            MaterialRequestCreateRequest(
                materialId = materialId,
                requestedAmount = amount,
                minQuality = minQuality,
                remark = remark?.takeIf { it.isNotBlank() },
            ),
            MaterialRequestCreateRequest.serializer(),
        )

    companion object {
        /** One screenful and then some. */
        const val DEFAULT_PAGE_SIZE: Int = 25

        /** Log subsystem. A remark is member input and never reaches the log. */
        private const val LOG_TAG = "materialboard"

        private const val OFFERS_PATH = "/api/v1/material-exchange/offers"
        private const val REQUESTS_PATH = "/api/v1/material-requests"
        private const val RELEASABLE_PATH = "/api/v1/material-exchange/releasable-items"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"

        /**
         * The interest path of one row.
         *
         * The two halves are different families, so this is where the side stops mattering to the
         * rest of the file.
         *
         * @param entry the row.
         * @return the path.
         */
        private fun interestPath(entry: BoardEntry): String =
            when (entry.side) {
                BoardSide.OFFERS -> "$OFFERS_PATH/${entry.id}/interest"
                BoardSide.REQUESTS -> "$REQUESTS_PATH/${entry.id}/interest"
            }

        /**
         * The withdraw path of one row.
         *
         * @param entry the row.
         * @return the path.
         */
        private fun deactivatePath(entry: BoardEntry): String =
            when (entry.side) {
                BoardSide.OFFERS -> "$OFFERS_PATH/${entry.id}/deactivate"
                BoardSide.REQUESTS -> "$REQUESTS_PATH/${entry.id}/deactivate"
            }
    }
}

/**
 * Maps a write's answer back onto the row it was made on.
 *
 * The row's own id is the fallback, because a response that omits it is still an answer about the
 * row the caller addressed — losing it would drop a successful write on the floor.
 *
 * @param entry the row the write was made on.
 * @return the updated row, or the failure unchanged.
 */
private fun ApiResult<MaterialExchangeOfferDto>.mapOffer(entry: BoardEntry): ApiResult<BoardEntry> =
    when (this) {
        is ApiResult.Failure -> {
            this
        }

        is ApiResult.Success -> {
            ApiResult.Success(value.toModel() ?: entry.copy(version = value.version))
        }
    }

/**
 * The same, for the request half.
 *
 * @param entry the row the write was made on.
 * @return the updated row, or the failure unchanged.
 */
private fun ApiResult<MaterialRequestDto>.mapRequest(entry: BoardEntry): ApiResult<BoardEntry> =
    when (this) {
        is ApiResult.Failure -> {
            this
        }

        is ApiResult.Success -> {
            ApiResult.Success(value.toModel() ?: entry.copy(version = value.version))
        }
    }

/**
 * Maps one page of offers.
 *
 * @param page the index that was asked for, used when the server omits its own.
 * @return the page.
 */
private fun PageResponseMaterialExchangeOfferDto.toModel(page: Int): BoardPage =
    BoardPage(
        entries = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one page of requests.
 *
 * @param page the index that was asked for.
 * @return the page.
 */
private fun PageResponseMaterialRequestDto.toModel(page: Int): BoardPage =
    BoardPage(
        entries = content.orEmpty().mapNotNull { it.toModel() },
        page = this.page ?: page,
        totalPages = totalPages ?: 0,
        totalElements = totalElements ?: 0L,
    )

/**
 * Maps one offer.
 *
 * An item offer names its material in `itemName` and its amount in `itemQuantity`, because it is
 * not a material at all — reading only the material fields would render every item row blank.
 *
 * @return the row, or `null` when it has no id.
 */
private fun MaterialExchangeOfferDto.toModel(): BoardEntry? {
    val entryId = id ?: return null
    val item = kind?.value == "ITEM"
    return BoardEntry(
        id = entryId,
        side = BoardSide.OFFERS,
        materialName = displayName(item, itemName, material?.name),
        // An item is counted in pieces by definition; a material says so itself.
        unitIsPiece = item || material?.quantityType?.value == "PIECE",
        amount = if (item) itemQuantity?.toString().orEmpty() else amount?.toString().orEmpty(),
        quality = quality,
        ownerName = owner?.effectiveName?.takeIf { it.isNotBlank() }.orEmpty(),
        ownerOrgUnits = ownerOrgUnits.orEmpty().mapNotNull { it.shorthand ?: it.name },
        postedAt = releasedAt?.takeIf { it.isNotBlank() },
        remark = remark?.takeIf { it.isNotBlank() },
        interestCount = interestCount ?: 0,
        interestedHandles = interestedHandles,
        viewerInterested = viewerInterested == true,
        mine = mine == true,
        version = version,
    )
}

/**
 * Maps one request.
 *
 * @return the row, or `null` when it has no id.
 */
private fun MaterialRequestDto.toModel(): BoardEntry? {
    val entryId = id ?: return null
    val item = kind?.value == "ITEM"
    return BoardEntry(
        id = entryId,
        side = BoardSide.REQUESTS,
        materialName = displayName(item, itemName, material?.name),
        unitIsPiece = item || material?.quantityType?.value == "PIECE",
        amount =
            if (item) {
                itemQuantity?.toString().orEmpty()
            } else {
                requestedAmount?.toString().orEmpty()
            },
        quality = minQuality,
        ownerName = owner?.effectiveName?.takeIf { it.isNotBlank() }.orEmpty(),
        ownerOrgUnits = ownerOrgUnits.orEmpty().mapNotNull { it.shorthand ?: it.name },
        postedAt = postedAt?.takeIf { it.isNotBlank() },
        remark = remark?.takeIf { it.isNotBlank() },
        interestCount = interestCount ?: 0,
        interestedHandles = interestedHandles,
        viewerInterested = viewerInterested == true,
        mine = mine == true,
        version = version,
    )
}

/**
 * Picks the name to show for a row.
 *
 * @param item whether the row is an item rather than a material.
 * @param itemName the item's name.
 * @param materialName the material's name.
 * @return the name, or an empty string when the server named neither.
 */
private fun displayName(
    item: Boolean,
    itemName: String?,
    materialName: String?,
): String =
    if (item) {
        itemName?.takeIf { it.isNotBlank() }.orEmpty()
    } else {
        materialName?.takeIf { it.isNotBlank() }.orEmpty()
    }

/**
 * Maps one releasable Lager entry.
 *
 * @return the entry, or `null` when it carries no id to release.
 */
private fun MaterialExchangeReleasableItemDto.toModel(): ReleasableStock? {
    val id = inventoryItemId ?: return null
    return ReleasableStock(
        inventoryItemId = id,
        materialName = materialName?.takeIf { it.isNotBlank() }.orEmpty(),
        unitIsPiece = quantityType?.value == "PIECE",
        amount = amount?.toString().orEmpty(),
        quality = quality,
        locationName = locationName?.takeIf { it.isNotBlank() }.orEmpty(),
        alreadyReleased = alreadyReleased == true,
    )
}

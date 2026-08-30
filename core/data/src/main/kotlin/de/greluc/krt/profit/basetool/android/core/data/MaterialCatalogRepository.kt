/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialMatrixItemDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialPriceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialPriceOverviewDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseShipTypeDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseTerminalDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ProfitCalculationDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal

/** How many rows one page of the catalogue asks for. */
private const val PAGE_SIZE = 500

/** A page-walk that has read this many pages has met something it was not built for. */
private const val MAX_PAGES = 40

/**
 * How many matrix cells one page carries.
 *
 * Larger than the catalogue's page because a cell is small and the matrix is the one read here that
 * runs into the thousands: fewer, fatter pages mean fewer round trips on a phone connection.
 */
private const val MATRIX_PAGE_SIZE = 1000

/** Where the material catalogue's category fallback comes from — the web's own „Unsortiert". */
const val MATERIAL_CATEGORY_UNSORTED: String = "Unsortiert"

/**
 * One material in the trade list, with the best price on each side.
 *
 * > **No type and no unit.** The artboard's subtitle reads „Veredelt · SCU", and
 * > `MaterialPriceOverviewDto` carries neither `type` nor `quantityType` — the category is what the
 * > projection has, and it is what the web groups by. Reading `/materials/search` alongside just to
 * > fill a subtitle would double the traffic of the whole list. On the design gap list.
 *
 * @property id the material — the detail is addressed by it.
 * @property name what it is called.
 * @property category which family it belongs to, or `null`; the screen falls back to „Unsortiert".
 * @property minPriceBuy the cheapest terminal sells it for this, or `null` where none does. Kept
 *   as the server's own decimal rather than a `Double`: the screen renders it verbatim and the two
 *   filters only ever compare it, so nothing here has to round.
 * @property maxPriceSell the dearest terminal pays this, or `null`.
 * @property illegal whether it is contraband, which the web badges.
 */
data class MaterialPriceRow(
    val id: String,
    val name: String,
    val category: String?,
    val minPriceBuy: BigDecimal?,
    val maxPriceSell: BigDecimal?,
    val illegal: Boolean,
)

/**
 * One material, as its own page needs it.
 *
 * Unlike the list row this **does** carry the type and the unit: `/materials/{id}` answers with the
 * full `MaterialDto`.
 *
 * @property id the material.
 * @property name what it is called.
 * @property type `RAW` or `REFINED` as the server names it, or `null`.
 * @property unit `SCU` or `PIECE`, or `null`.
 * @property category which family, or `null`.
 * @property illegal whether it is contraband.
 */
data class MaterialSummary(
    val id: String,
    val name: String,
    val type: String?,
    val unit: String?,
    val category: String?,
    val illegal: Boolean,
)

/**
 * What one terminal pays and charges for one material.
 *
 * @property id the price row.
 * @property terminal where it trades.
 * @property priceBuy what the terminal charges, or `null` when it does not sell.
 * @property priceSell what it pays, or `null` when it does not buy.
 */
data class MaterialTerminalPrice(
    val id: String,
    val terminal: String,
    val priceBuy: BigDecimal?,
    val priceSell: BigDecimal?,
)

/**
 * The trade reference: the material catalogue with its prices.
 *
 * Read-only throughout. Prices come from UEX and are written by the sync, never by a member.
 */
interface MaterialCatalogSource {
    /**
     * The whole price list, in one answer.
     *
     * **Page-walked rather than paged on screen**, which is the same choice the web makes
     * (`size=10000`). The two price filters the design draws — „Min. Einkaufspreis" and „Max.
     * Verkaufspreis" — are not query parameters on this endpoint, so filtering them over a
     * partially loaded list would quietly answer from a fraction of the catalogue and look like a
     * complete answer (ADR-0104). Roughly two hundred rows is a cheap thing to hold and an
     * expensive thing to get wrong.
     *
     * @return every material with its two best prices, or the classified failure.
     */
    suspend fun priceOverview(): ApiResult<List<MaterialPriceRow>>

    /**
     * One material's own record.
     *
     * @param materialId which one.
     * @return it, or the classified failure.
     */
    suspend fun material(materialId: String): ApiResult<MaterialSummary>

    /**
     * Every terminal price for one material.
     *
     * Page-walked for the reason the list is: the detail's terminal filter is a local one, and a
     * filter over half the terminals would be a wrong answer rather than a short one.
     *
     * @param materialId which material.
     * @return its price rows, or the classified failure.
     */
    suspend fun prices(materialId: String): ApiResult<List<MaterialTerminalPrice>>
}

/**
 * One cell of the Material × Terminal matrix: what one terminal does with one material.
 *
 * @property materialId which material — the row.
 * @property materialName what it is called.
 * @property terminalId which terminal — the column.
 * @property terminalName what it is called.
 * @property starSystem which system it is in, or `null`; the system filter is built from these.
 * @property priceBuy what the terminal charges, or `null`.
 * @property priceSell what it pays, or `null`.
 */
data class MaterialMatrixCell(
    val materialId: String,
    val materialName: String,
    val terminalId: String,
    val terminalName: String,
    val starSystem: String?,
    val priceBuy: BigDecimal?,
    val priceSell: BigDecimal?,
)

/**
 * One page of the matrix.
 *
 * Delivered a page at a time rather than page-walked inside the repository, because the design
 * draws it arriving: „Nachladen zeilenweise … die Ladezeile bleibt unten stehen und wird nie durch
 * einen Vollbild-Spinner ersetzt" (ch. 16 artboard 3).
 *
 * @property cells the rows on this page.
 * @property page the zero-based index this page had.
 * @property totalPages how many exist.
 * @property totalElements how many cells the whole matrix holds.
 */
data class MaterialMatrixPage(
    val cells: List<MaterialMatrixCell>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page follows this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * One material's profit for one ship, as the server computed it.
 *
 * **Every figure here is the server's.** The app renders them and computes none: a margin is money
 * advice, and an app that derived one would be stating a number nobody could reconcile with the
 * web.
 *
 * @property materialName which material.
 * @property minBuy the cheapest purchase, or `null`.
 * @property maxSell the dearest sale, or `null`.
 * @property profitPerScu what one SCU makes, or `null`.
 * @property fullLoadCost what filling the ship costs, or `null`.
 * @property maxProfitFullLoad what a full load makes, or `null`.
 * @property marginPercent the margin, or `null`.
 */
data class ProfitRow(
    val materialName: String,
    val minBuy: BigDecimal?,
    val maxSell: BigDecimal?,
    val profitPerScu: BigDecimal?,
    val fullLoadCost: BigDecimal?,
    val maxProfitFullLoad: BigDecimal?,
    val marginPercent: BigDecimal?,
)

/**
 * The market surfaces behind „Handel"'s overflow: the price matrix and the profit calculation.
 *
 * Its own seam beside [MaterialCatalogSource] because the two screens read four endpoints the list
 * never touches, and a test for the list should not have to fake them.
 */
interface MaterialMarketSource {
    /**
     * One page of the Material × Terminal matrix.
     *
     * @param page the zero-based index.
     * @return the page, or the classified failure.
     */
    suspend fun matrixPage(page: Int): ApiResult<MaterialMatrixPage>

    /**
     * The ships a profit calculation can be run for.
     *
     * Page-walked, and filtered to those that carry something: the calculation prices a **full
     * load**, so a ship with no hold has no answer.
     *
     * @return the ships, or the classified failure.
     */
    suspend fun shipTypes(): ApiResult<List<ShipTypeOption>>

    /**
     * Every star system that has a terminal in it.
     *
     * Derived from the terminal catalogue, which is where the web takes it from too — there is no
     * star-system endpoint, and the systems that matter are the ones something trades in.
     *
     * @return the system names, sorted, or the classified failure.
     */
    suspend fun starSystems(): ApiResult<List<String>>

    /**
     * Runs the profit calculation.
     *
     * @param shipId the ship whose hold is being filled.
     * @param starSystemNames the systems to stay inside, or empty for all of them.
     * @return one row per material, or the classified failure.
     */
    suspend fun profit(
        shipId: String,
        starSystemNames: List<String>,
    ): ApiResult<List<ProfitRow>>
}

/** The trade reference, over HTTP. */
class MaterialCatalogRepository(
    private val reader: ApiReader,
) : MaterialCatalogSource,
    MaterialMarketSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client, so the bearer and the correlation id are already set.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "MaterialCatalog"),
    )

    override suspend fun priceOverview(): ApiResult<List<MaterialPriceRow>> {
        val rows = mutableListOf<MaterialPriceRow>()
        var page = 0
        while (page < MAX_PAGES) {
            val result =
                reader.get(
                    "/api/v1/materials/prices-overview",
                    listOf("page" to page.toString(), "size" to PAGE_SIZE.toString(), "sort" to "name,asc"),
                    PageResponseMaterialPriceOverviewDto.serializer(),
                )
            val answer = (result as? ApiResult.Success)?.value ?: return result as ApiResult.Failure
            answer.content.orEmpty().forEach { dto ->
                val id = dto.id ?: return@forEach
                rows.add(
                    MaterialPriceRow(
                        id = id,
                        name = dto.name.orEmpty(),
                        category = dto.category?.name?.takeIf { it.isNotBlank() },
                        minPriceBuy = dto.minPriceBuy?.value,
                        maxPriceSell = dto.maxPriceSell?.value,
                        illegal = dto.isIllegal == true,
                    ),
                )
            }
            page += 1
            if (page >= (answer.totalPages ?: 1)) {
                break
            }
        }
        return ApiResult.Success(rows)
    }

    override suspend fun material(materialId: String): ApiResult<MaterialSummary> =
        when (val result = reader.get("/api/v1/materials/$materialId", MaterialDto.serializer())) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    MaterialSummary(
                        id = result.value.id ?: materialId,
                        name = result.value.name.orEmpty(),
                        type = result.value.type,
                        unit = result.value.quantityType,
                        category = result.value.category?.name?.takeIf { it.isNotBlank() },
                        illegal = result.value.isIllegal == true,
                    ),
                )
            }
        }

    override suspend fun prices(materialId: String): ApiResult<List<MaterialTerminalPrice>> {
        val rows = mutableListOf<MaterialTerminalPrice>()
        var page = 0
        while (page < MAX_PAGES) {
            val result =
                reader.get(
                    "/api/v1/materials/$materialId/prices",
                    listOf(
                        "page" to page.toString(),
                        "size" to PAGE_SIZE.toString(),
                        "sort" to "terminal.name,asc",
                    ),
                    PageResponseMaterialPriceDto.serializer(),
                )
            val answer = (result as? ApiResult.Success)?.value ?: return result as ApiResult.Failure
            answer.content.orEmpty().forEach { dto ->
                val id = dto.id ?: return@forEach
                rows.add(
                    MaterialTerminalPrice(
                        id = id,
                        terminal = dto.terminalName.orEmpty(),
                        priceBuy = dto.priceBuy?.value,
                        priceSell = dto.priceSell?.value,
                    ),
                )
            }
            page += 1
            if (page >= (answer.totalPages ?: 1)) {
                break
            }
        }
        return ApiResult.Success(rows)
    }

    override suspend fun matrixPage(page: Int): ApiResult<MaterialMatrixPage> =
        when (
            val result =
                reader.get(
                    "/api/v1/materials/matrix",
                    listOf(
                        "page" to page.toString(),
                        "size" to MATRIX_PAGE_SIZE.toString(),
                        "sort" to "material.name,asc",
                    ),
                    PageResponseMaterialMatrixItemDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    MaterialMatrixPage(
                        cells =
                            result.value.content.orEmpty().mapNotNull { dto ->
                                val materialId = dto.materialId ?: return@mapNotNull null
                                val terminalId = dto.terminalId ?: return@mapNotNull null
                                MaterialMatrixCell(
                                    materialId = materialId,
                                    materialName = dto.materialName.orEmpty(),
                                    terminalId = terminalId,
                                    // The nickname is what the web's own column header shows where
                                    // there is one — „ARC-L1" rather than the full station name.
                                    terminalName =
                                        dto.terminalNickname?.takeIf { it.isNotBlank() } ?: dto.terminalName.orEmpty(),
                                    starSystem = dto.starSystemName?.takeIf { it.isNotBlank() },
                                    priceBuy = dto.priceBuy?.value,
                                    priceSell = dto.priceSell?.value,
                                )
                            },
                        page = result.value.page ?: page,
                        totalPages = result.value.totalPages ?: 1,
                        totalElements = result.value.totalElements ?: 0L,
                    ),
                )
            }
        }

    override suspend fun shipTypes(): ApiResult<List<ShipTypeOption>> {
        val ships = mutableListOf<ShipTypeOption>()
        var page = 0
        while (page < MAX_PAGES) {
            val result =
                reader.get(
                    "/api/v1/ship-types",
                    listOf("page" to page.toString(), "size" to PAGE_SIZE.toString(), "sort" to "name,asc"),
                    PageResponseShipTypeDto.serializer(),
                )
            val answer = (result as? ApiResult.Success)?.value ?: return result as ApiResult.Failure
            answer.content.orEmpty().forEach { dto ->
                val id = dto.id ?: return@forEach
                val scu = dto.scu ?: return@forEach
                // A full load of nothing is not a calculation, which is why the web filters the
                // same way rather than offering the choice and answering with zeroes.
                if (scu > 0) {
                    ships.add(
                        ShipTypeOption(
                            id = id,
                            name = dto.name.orEmpty(),
                            manufacturerName = dto.manufacturer?.name,
                            scu = scu,
                        ),
                    )
                }
            }
            page += 1
            if (page >= (answer.totalPages ?: 1)) {
                break
            }
        }
        return ApiResult.Success(ships)
    }

    override suspend fun starSystems(): ApiResult<List<String>> {
        val systems = sortedSetOf<String>()
        var page = 0
        while (page < MAX_PAGES) {
            val result =
                reader.get(
                    "/api/v1/terminals",
                    listOf("page" to page.toString(), "size" to PAGE_SIZE.toString(), "sort" to "name,asc"),
                    PageResponseTerminalDto.serializer(),
                )
            val answer = (result as? ApiResult.Success)?.value ?: return result as ApiResult.Failure
            answer.content.orEmpty().forEach { dto ->
                dto.starSystemName?.takeIf { it.isNotBlank() }?.let { systems.add(it) }
            }
            page += 1
            if (page >= (answer.totalPages ?: 1)) {
                break
            }
        }
        return ApiResult.Success(systems.toList())
    }

    override suspend fun profit(
        shipId: String,
        starSystemNames: List<String>,
    ): ApiResult<List<ProfitRow>> =
        when (
            val result =
                reader.get(
                    "/api/v1/materials/profit-calculation",
                    // A repeated parameter, one per system — an absent list means „every system",
                    // which is what the server treats null as.
                    listOf("shipId" to shipId) + starSystemNames.map { "starSystemNames" to it },
                    ListSerializer(ProfitCalculationDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.map { dto ->
                        ProfitRow(
                            materialName = dto.materialName.orEmpty(),
                            minBuy = dto.minBuyPrice?.value,
                            maxSell = dto.maxSellPrice?.value,
                            profitPerScu = dto.profitPerScu?.value,
                            fullLoadCost = dto.fullLoadCost?.value,
                            maxProfitFullLoad = dto.maxProfitFullLoad?.value,
                            marginPercent = dto.marginPercent?.value,
                        )
                    },
                )
            }
        }
}

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

/** The page-walk's upper bound on pages read before it gives up. */
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
 * One material in the trade list, with the best price on each side; carries no type or unit.
 *
 * @property id the material, which addresses the detail.
 * @property name what it is called.
 * @property category which family it belongs to, or `null`; the screen falls back to „Unsortiert".
 * @property minPriceBuy the cheapest terminal sells it for this, or `null` where none does; the
 *   server's own decimal, never rounded.
 * @property maxPriceSell the dearest terminal pays this, or `null`.
 * @property illegal whether it is contraband.
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
 * One material as its own page needs it, including type and unit from `/materials/{id}`.
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
     * Reads the whole price list by walking every page, so the local price filters apply to the
     * complete catalogue (ADR-0104).
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
     * Reads every terminal price for one material by walking every page, so the local terminal filter
     * applies to all of them.
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
 * One page of the Material × Terminal matrix, delivered a page at a time.
 *
 * [Page.rows] holds the rows on this page.
 */
typealias MaterialMatrixPage = Page<MaterialMatrixCell>

/**
 * One material's profit for one ship; every figure is the server's and none is computed here.
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
                        rows =
                            result.value.content.orEmpty().mapNotNull { dto ->
                                val materialId = dto.materialId ?: return@mapNotNull null
                                val terminalId = dto.terminalId ?: return@mapNotNull null
                                MaterialMatrixCell(
                                    materialId = materialId,
                                    materialName = dto.materialName.orEmpty(),
                                    terminalId = terminalId,
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

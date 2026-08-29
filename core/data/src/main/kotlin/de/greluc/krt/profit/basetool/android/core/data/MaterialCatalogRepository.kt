/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialPriceDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseMaterialPriceOverviewDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import okhttp3.OkHttpClient
import java.math.BigDecimal

/** How many rows one page of the catalogue asks for. */
private const val PAGE_SIZE = 500

/** A page-walk that has read this many pages has met something it was not built for. */
private const val MAX_PAGES = 40

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

/** The trade reference, over HTTP. */
class MaterialCatalogRepository(
    private val reader: ApiReader,
) : MaterialCatalogSource {
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
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDemandGroupDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDemandOrderShareDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDemandOverviewDto
import de.greluc.krt.profit.basetool.android.core.contract.model.MaterialDemandRowDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import okhttp3.OkHttpClient

/**
 * One order's share of a material's demand.
 *
 * @property jobOrderId which order, for the jump into it.
 * @property displayId its number as an integer; the screen prefixes a bare `#`.
 * @property status where that order stands.
 * @property required how much this order asks for.
 * @property booked how much has already been handed over to it.
 * @property claimed how much is promised but not yet handed over.
 */
data class MaterialDemandShare(
    val jobOrderId: String,
    val displayId: String,
    val status: JobOrderStatus,
    val required: Double,
    val booked: Double,
    val claimed: Double,
)

/**
 * What one material still needs, summed over every open order that asks for it.
 *
 * @property materialId the material.
 * @property materialName what it is called.
 * @property unit what it is counted in: SCU for a raw material, pieces for an item.
 * @property qualityRequirement `GOOD` when only good ore counts, or `null` for any.
 * @property required the sum asked for.
 * @property booked the sum already handed over.
 * @property claimed the sum promised and not yet handed over.
 * @property outstanding what is still open, as the server computed it.
 * @property orders the orders that ask for it.
 */
data class MaterialDemandRow(
    val materialId: String,
    val materialName: String,
    val unit: String,
    val qualityRequirement: String?,
    val required: Double,
    val booked: Double,
    val claimed: Double,
    val outstanding: Double,
    val orders: List<MaterialDemandShare>,
) {
    /**
     * How much of what was asked for is covered by booked plus claimed, between 0 and 1; 1 when
     * nothing was asked for.
     */
    val coverage: Float
        get() = if (required <= 0.0) 1f else ((booked + claimed) / required).coerceIn(0.0, 1.0).toFloat()

    /** Whether anything is still open — the „Ungedeckt" filter is exactly this. */
    val uncovered: Boolean get() = outstanding > 0.0
}

/**
 * The demand of one org unit, which is how the server groups it.
 *
 * @property orgUnitId which unit, or `null` for the orders that belong to none.
 * @property orgUnitName its name.
 * @property orgUnitShorthand its short form for the badge.
 * @property rows what that unit still needs.
 */
data class MaterialDemandGroup(
    val orgUnitId: String?,
    val orgUnitName: String?,
    val orgUnitShorthand: String?,
    val rows: List<MaterialDemandRow>,
)

/** Reads the cross-order material demand. */
interface MaterialDemandSource {
    /**
     * Reads what every open order together still needs, per material, in one unpaged call.
     *
     * @return the demand grouped by org unit, or the classified failure.
     */
    suspend fun demand(): ApiResult<List<MaterialDemandGroup>>
}

/**
 * Reads the cross-order material demand from `GET /api/v1/orders/material-demand`.
 *
 * @property reader the API seam.
 */
class MaterialDemandRepository(
    private val reader: ApiReader,
) : MaterialDemandSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers.
     * @param baseUrl the flavour's API origin.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    override suspend fun demand(): ApiResult<List<MaterialDemandGroup>> =
        reader.get(DEMAND_PATH, MaterialDemandOverviewDto.serializer())
            .map { loaded -> loaded.groups.orEmpty().map { it.toModel() } }

    private companion object {
        const val DEMAND_PATH = "/api/v1/orders/material-demand"
    }
}

/** Log subsystem of this repository's calls. */
private const val LOG_TAG = "material-demand"

/**
 * One org unit's block.
 *
 * @return the group.
 */
private fun MaterialDemandGroupDto.toModel(): MaterialDemandGroup =
    MaterialDemandGroup(
        orgUnitId = orgUnit?.id,
        orgUnitName = orgUnit?.name,
        orgUnitShorthand = orgUnit?.shorthand,
        rows = materials.orEmpty().mapNotNull { it.toModel() },
    )

/**
 * One material's line.
 *
 * A row without a material is dropped rather than rendered as a blank line: it cannot be tapped,
 * cannot be named, and would read as a loading failure.
 *
 * @return the row, or `null` when the material is missing.
 */
private fun MaterialDemandRowDto.toModel(): MaterialDemandRow? {
    val id = material?.id ?: return null
    return MaterialDemandRow(
        materialId = id,
        materialName = material?.name.orEmpty().ifEmpty { id },
        unit = material?.quantityType.orEmpty(),
        qualityRequirement = qualityRequirement?.value?.takeIf { it != "NONE" },
        required = requiredAmount ?: 0.0,
        booked = bookedAmount ?: 0.0,
        claimed = claimedAmount ?: 0.0,
        outstanding = outstandingAmount ?: 0.0,
        orders = orders.orEmpty().mapNotNull { it.toModel() },
    )
}

/**
 * One order's share.
 *
 * @return the share, or `null` when it carries no order id to jump to.
 */
private fun MaterialDemandOrderShareDto.toModel(): MaterialDemandShare? {
    val id = jobOrderId ?: return null
    return MaterialDemandShare(
        jobOrderId = id,
        displayId = displayId?.toString().orEmpty(),
        status = JobOrderStatus.from(status?.value),
        required = requiredAmount ?: 0.0,
        booked = bookedAmount ?: 0.0,
        claimed = claimedAmount ?: 0.0,
    )
}

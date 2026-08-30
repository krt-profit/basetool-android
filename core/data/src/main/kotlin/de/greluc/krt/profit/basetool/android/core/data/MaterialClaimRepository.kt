/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.ClaimBucketDto
import de.greluc.krt.profit.basetool.android.core.contract.model.ClaimDto
import de.greluc.krt.profit.basetool.android.core.contract.model.CreateClaimDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.math.BigDecimal

/**
 * The quality a claimed bucket is for.
 *
 * A bucket is keyed on **(material, quality)**, not on material alone: the same material at „gut"
 * and at „egal" are two separate demands on the order, and a claim names which of them it covers.
 */
enum class ClaimQuality {
    /** The order asked for good-quality material. */
    GOOD,

    /** The order did not care. */
    NONE,
    ;

    companion object {
        /**
         * Maps the server's own value onto the enum.
         *
         * @param raw the wire value, possibly `null`.
         * @return the matching constant, [NONE] for anything else — the server's own default when
         *   an order line names no requirement.
         */
        fun from(raw: String?): ClaimQuality = entries.firstOrNull { it.name == raw?.trim() } ?: NONE
    }
}

/**
 * One Staffel's pledge on one bucket.
 *
 * > **A claim belongs to a unit, not to a member.** The wire keys it on `(bucket, claimingOrgUnit)`
 * > and a repeat post updates rather than duplicates. The member who lodged it is recorded, but the
 * > pledge is the Staffel's.
 *
 * @property id the claim — what a withdrawal addresses.
 * @property orgUnitId which Staffel pledged.
 * @property orgUnitName how it reads.
 * @property amount how much it pledged.
 */
data class MaterialClaim(
    val id: String,
    val orgUnitId: String?,
    val orgUnitName: String,
    val amount: BigDecimal?,
)

/**
 * One material demand of an order, and who has signed up for it.
 *
 * @property materialId which material.
 * @property materialName what it is called.
 * @property unit `SCU` or `PIECE`, or `null`.
 * @property quality which of the material's two buckets this is.
 * @property required how much the order needs.
 * @property claimed how much has been pledged in total.
 * @property open what is left — the server's own figure, so nobody has to subtract on screen.
 * @property claims the individual pledges.
 */
data class ClaimBucket(
    val materialId: String,
    val materialName: String,
    val unit: String?,
    val quality: ClaimQuality,
    val required: BigDecimal?,
    val claimed: BigDecimal?,
    val open: BigDecimal?,
    val claims: List<MaterialClaim>,
) {
    /**
     * The pledge of one Staffel on this bucket, or `null` when it has none.
     *
     * The write is an upsert keyed on the unit, so „setzen" and „ändern" are the same call and the
     * form has to know which of the two it is doing.
     *
     * @param orgUnitId the Staffel.
     * @return its claim.
     */
    fun claimOf(orgUnitId: String?): MaterialClaim? =
        orgUnitId?.let { id -> claims.firstOrNull { it.orgUnitId == id } }
}

/**
 * Zusagen — a Staffel signing up to deliver part of a Spezialkommando order.
 *
 * > **A claim is an intention, never a booking.** Delivery happens through the Übergabe. This is
 * > why the server's `openRemaining` is `required − claimed` and measures **promises**, and why the
 * > handover's „already delivered" must never be derived from it.
 */
interface MaterialClaimSource {
    /**
     * Reads the order's buckets with their pledges.
     *
     * Visible to anyone who may see the order, which is wider than who may pledge.
     *
     * @param orderId the Auftrag.
     * @return one entry per required material bucket, or the classified failure.
     */
    suspend fun buckets(orderId: String): ApiResult<List<ClaimBucket>>

    /**
     * Signs a Staffel up, or changes what it already pledged.
     *
     * One call for both: the server upserts on `(bucket, claimingOrgUnit)`.
     *
     * > **Overclaim is refused.** The sum across Staffeln may not exceed what the bucket needs
     * > (REQ-ORDERS-024); the server takes a row lock on the order and answers **400** for anything
     * > over. Design ch. 10 artboard 13 says the opposite („Überzusage ist erlaubt") and is wrong —
     * > flagged rather than coded around.
     *
     * @param orderId the Auftrag.
     * @param materialId which material.
     * @param quality which of its buckets.
     * @param orgUnitId the pledging Staffel. Must be a **squadron** and profit-eligible; a
     *   Spezialkommando places orders and never claims against one.
     * @param amount how much it pledges.
     * @return nothing on success, or the classified failure.
     */
    suspend fun upsert(
        orderId: String,
        materialId: String,
        quality: ClaimQuality,
        orgUnitId: String,
        amount: Double,
    ): ApiResult<Unit>

    /**
     * Takes a pledge back.
     *
     * No confirmation anywhere in this flow: a claim books nothing, so withdrawing one is free.
     *
     * @param orderId the Auftrag.
     * @param claimId the pledge.
     * @return nothing on success, or the classified failure.
     */
    suspend fun withdraw(
        orderId: String,
        claimId: String,
    ): ApiResult<Unit>
}

/** The Zusagen, over HTTP. */
class MaterialClaimRepository(
    private val reader: ApiReader,
) : MaterialClaimSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the shared client.
     * @param baseUrl where the API lives.
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = "MaterialClaim"),
    )

    override suspend fun buckets(orderId: String): ApiResult<List<ClaimBucket>> =
        when (
            val result =
                reader.get("/api/v1/orders/$orderId/claims", ListSerializer(ClaimBucketDto.serializer()))
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.mapNotNull { dto -> dto.krtToModel() })
            }
        }

    override suspend fun upsert(
        orderId: String,
        materialId: String,
        quality: ClaimQuality,
        orgUnitId: String,
        amount: Double,
    ): ApiResult<Unit> =
        reader.postAccepted(
            "/api/v1/orders/$orderId/claims",
            CreateClaimDto(
                materialId = materialId,
                qualityRequirement = CreateClaimDto.QualityRequirement.valueOf(quality.name),
                claimingOrgUnitId = orgUnitId,
                amount = amount,
            ),
            CreateClaimDto.serializer(),
        )

    override suspend fun withdraw(
        orderId: String,
        claimId: String,
    ): ApiResult<Unit> = reader.delete("/api/v1/orders/$orderId/claims/$claimId")
}

/**
 * Maps one bucket onto the model, or drops it when it cannot be addressed.
 *
 * @receiver the server's bucket.
 * @return it, or `null` without a material id — the write is keyed on it.
 */
private fun ClaimBucketDto.krtToModel(): ClaimBucket? {
    val id = material?.id ?: return null
    return ClaimBucket(
        materialId = id,
        materialName = material?.name.orEmpty(),
        unit = material?.quantityType,
        quality = ClaimQuality.from(qualityRequirement?.value),
        required = requiredAmount?.toBigDecimal(),
        claimed = claimedAmount?.toBigDecimal(),
        open = openRemaining?.toBigDecimal(),
        claims = claims.orEmpty().mapNotNull { it.krtToModel() },
    )
}

/**
 * Maps one pledge onto the model.
 *
 * @receiver the server's claim.
 * @return it, or `null` without an id — a withdrawal addresses it by id.
 */
private fun ClaimDto.krtToModel(): MaterialClaim? {
    val claimId = id ?: return null
    return MaterialClaim(
        id = claimId,
        orgUnitId = claimingOrgUnit?.id,
        orgUnitName = claimingOrgUnit?.shorthand?.takeIf { it.isNotBlank() } ?: claimingOrgUnit?.name.orEmpty(),
        amount = amount?.toBigDecimal(),
    )
}

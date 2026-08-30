/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.ClaimBucket
import de.greluc.krt.profit.basetool.android.core.data.ClaimQuality
import de.greluc.krt.profit.basetool.android.core.data.MaterialClaimSource
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
import de.greluc.krt.profit.basetool.android.core.data.krtToDoubleOrNull
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Log tag for the Zusagen. */
private const val LOG_TAG = "OrderClaims"

/**
 * One pledge being set, changed or withdrawn.
 *
 * @property materialId which material.
 * @property materialName what it is called.
 * @property quality which of the material's two buckets.
 * @property unit `SCU` or `PIECE`, or `null`.
 * @property required what the bucket needs.
 * @property open what is still unpledged, as the server computed it.
 * @property orgUnitId the pledging Staffel.
 * @property orgUnitName how it reads.
 * @property claimId the existing pledge this replaces, or `null` for a first one — which is what
 *   decides whether „Zurückziehen" is offered at all.
 * @property amount how much, as typed.
 * @property saving whether a write is in flight.
 * @property error the last refusal.
 */
data class ClaimDraft(
    val materialId: String,
    val materialName: String,
    val quality: ClaimQuality,
    val unit: String?,
    val required: BigDecimal?,
    val open: BigDecimal?,
    val orgUnitId: String,
    val orgUnitName: String,
    val claimId: String?,
    val amount: String = "",
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    /** How much is being pledged, or `null` when nothing usable is typed. */
    val value: Double?
        get() = amount.krtToDoubleOrNull()?.takeIf { it > 0.0 }

    /** Whether the form may be sent. */
    val submittable: Boolean
        get() = !saving && value != null
}

/**
 * Everything the Zusagen tab draws.
 *
 * @property buckets the order's material demands with their pledges.
 * @property units the Staffeln the caller may pledge for — their own memberships, filtered to the
 *   profit-eligible squadrons, which is exactly the server's own guard.
 * @property loading whether the first read is still running.
 * @property error what stopped it, or `null`.
 * @property draft the sheet, or `null` when it is shut.
 */
data class ClaimsState(
    val buckets: List<ClaimBucket> = emptyList(),
    val units: List<OrgUnit> = emptyList(),
    val loading: Boolean = true,
    val error: ApiError? = null,
    val draft: ClaimDraft? = null,
) {
    /**
     * The Staffel a pledge is made for.
     *
     * The first profit-eligible squadron the caller belongs to. With more than one the sheet lets
     * them switch; with none they may not pledge at all, which is the server's rule and not this
     * screen's invention.
     */
    val defaultUnit: OrgUnit?
        get() = units.firstOrNull()
}

/**
 * Zusagen — a Staffel signing up to deliver part of a Spezialkommando order.
 *
 * > **A claim is an intention, not a booking.** Nothing moves in the Lager; delivery is the
 * > Übergabe. That is also why withdrawing one needs no confirmation.
 *
 * > **Overclaim is refused by the server** (REQ-ORDERS-024), which design ch. 10 artboard 13 states
 * > the opposite of. The sheet says what is still open and the refusal is rendered plainly; the
 * > artboard's claim is on the design gap list rather than coded around.
 *
 * @property source where the buckets and the two writes go.
 * @property orgUnits where the caller's own Staffeln come from.
 * @property scope the view model's scope.
 * @property read the state as it stands.
 * @property write reports it back.
 */
class OrderClaims(
    private val source: MaterialClaimSource,
    private val orgUnits: OrgUnitSource,
    private val scope: CoroutineScope,
    private val read: () -> ClaimsState,
    private val write: (ClaimsState) -> Unit,
) {
    /**
     * Reads the order's buckets and the Staffeln the caller may pledge for.
     *
     * @param orderId the Auftrag.
     */
    fun load(orderId: String) {
        write(read().copy(loading = true, error = null))
        scope.launch {
            when (val result = source.buckets(orderId)) {
                is ApiResult.Success -> {
                    write(read().copy(buckets = result.value, loading = false))
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the claims could not be read: ${result.error}" }
                    write(read().copy(loading = false, error = result.error))
                }
            }
        }
        scope.launch {
            val result = orgUnits.memberships()
            if (result is ApiResult.Success) {
                // Exactly the server's guard: a Spezialkommando places orders and never claims
                // against one, and a squadron an admin has not marked profit-eligible is outside
                // the order workflow. Offering either would turn a filled sheet into a 400.
                write(
                    read().copy(
                        units = result.value.filter { it.kind == OrgUnitKind.SQUADRON && it.profitEligible },
                    ),
                )
            }
        }
    }

    /**
     * Opens the sheet for one bucket.
     *
     * @param bucket which demand.
     * @param unit the Staffel to pledge for.
     */
    fun open(
        bucket: ClaimBucket,
        unit: OrgUnit,
    ) {
        val existing = bucket.claimOf(unit.id)
        write(
            read().copy(
                draft =
                    ClaimDraft(
                        materialId = bucket.materialId,
                        materialName = bucket.materialName,
                        quality = bucket.quality,
                        unit = bucket.unit,
                        required = bucket.required,
                        open = bucket.open,
                        orgUnitId = unit.id,
                        orgUnitName = unit.shorthand.takeIf { it.isNotBlank() } ?: unit.name,
                        claimId = existing?.id,
                        // Setting and changing are the same sheet, so an existing pledge arrives
                        // filled in rather than making somebody retype what they already promised.
                        amount = existing?.amount?.toPlainString().orEmpty(),
                    ),
            ),
        )
    }

    /** Closes the sheet. */
    fun dismiss() {
        write(read().copy(draft = null))
    }

    /**
     * Records a change in the open sheet.
     *
     * @param change what the field did to it.
     */
    fun change(change: (ClaimDraft) -> ClaimDraft) {
        val draft = read().draft ?: return
        write(read().copy(draft = change(draft)))
    }

    /**
     * Sends the pledge.
     *
     * @param orderId the Auftrag.
     */
    fun submit(orderId: String) {
        val draft = read().draft?.takeIf { it.submittable } ?: return
        val amount = draft.value ?: return
        change { it.copy(saving = true, error = null) }
        scope.launch {
            val result =
                source.upsert(
                    orderId = orderId,
                    materialId = draft.materialId,
                    quality = draft.quality,
                    orgUnitId = draft.orgUnitId,
                    amount = amount,
                )
            finish(orderId, result)
        }
    }

    /**
     * Takes the open sheet's pledge back.
     *
     * No confirmation: a claim books nothing, so this is reversible by simply pledging again.
     *
     * @param orderId the Auftrag.
     */
    fun withdraw(orderId: String) {
        val claimId = read().draft?.claimId ?: return
        change { it.copy(saving = true, error = null) }
        scope.launch { finish(orderId, source.withdraw(orderId, claimId)) }
    }

    /**
     * Closes the sheet on success and re-reads the buckets, or keeps it with the refusal.
     *
     * The buckets are re-read rather than patched: a pledge moves the bucket's claimed and open
     * figures, and both are the server's own arithmetic.
     *
     * @param orderId the Auftrag.
     * @param result what the write returned.
     */
    private fun finish(
        orderId: String,
        result: ApiResult<Unit>,
    ) {
        when (result) {
            is ApiResult.Success -> {
                write(read().copy(draft = null))
                load(orderId)
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the claim was refused: ${result.error}" }
                change { it.copy(saving = false, error = result.error) }
            }
        }
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.InventoryAllocation
import kotlin.math.abs

/**
 * The shape of one earmark dimension's sourcing for a deducted quantity.
 *
 * Stock is tagged independently by Auftrag and by Einsatz, so a deduction is sourced once per
 * dimension. The client mirrors the server's rules: per dimension the assigned sum must not exceed
 * the deducted amount and each assignment must fit its slice (both 400), the rest absorbs what the
 * tags do not cover (422 otherwise), and an empty plan means "take it from the rest first".
 */
enum class HerkunftStatus {
    /** The tags cover the whole deduction; nothing comes from the rest. */
    COVERED,

    /** Part of the deduction comes from the not-yet-assigned rest, and the rest can carry it. */
    FROM_REST,

    /** The tags claim more than is being deducted — the server refuses this with 400. */
    OVERALLOCATED,

    /** More is left for the rest than the rest holds — the server refuses this with 422. */
    REST_TOO_SMALL,

    /**
     * One tag, no rest, nothing to decide.
     *
     * Every unit leaving has to come out of that one tag, so the field is filled from the deducted
     * amount and locked instead of being demanded from the member. Typing anything else could only
     * ever trip one of the two refusals above.
     */
    AUTOMATIC,
}

/**
 * One dimension of the plan, reduced to what the sheet draws and the save needs.
 *
 * @property tags the earmarks this dimension holds, in the order they are shown.
 * @property assigned what the member has assigned to tags, in total.
 * @property fromRest what is left for the not-yet-assigned rest to carry; never negative.
 * @property free how much that rest actually holds.
 * @property status which of the five shapes this dimension is in.
 * @property locked whether the single field is filled from the amount and not editable.
 */
data class HerkunftDimension(
    val tags: List<InventoryAllocation>,
    val assigned: Double,
    val fromRest: Double,
    val free: Double,
    val status: HerkunftStatus,
    val locked: Boolean,
) {
    /** Whether the server would accept this dimension as it stands. */
    val valid: Boolean
        get() = status != HerkunftStatus.OVERALLOCATED && status != HerkunftStatus.REST_TOO_SMALL
}

/**
 * Rounds a quantity to the three decimals an SCU amount is expressed in.
 *
 * The server compares with an epsilon for exactly this reason: `0.1 + 0.2` is not `0.3` in binary
 * floating point, and a plan that is off by 5e-17 must not read as over-assigned.
 *
 * @return the rounded value.
 */
private fun Double.scu(): Double = Math.round(this * SCU_SCALE) / SCU_SCALE

/** Three decimals, matching the server's own rounding before its epsilon comparison. */
private const val SCU_SCALE = 1000.0

/** What counts as zero after SCU rounding; mirrors the server's `REDUCTION_EPSILON`. */
private const val EPSILON = 1e-6

/**
 * Reads one dimension's shape from what the member has typed.
 *
 * @param tags the earmarks the entry holds in this dimension.
 * @param rest what the server says is not yet assigned in this dimension.
 * @param deducted how much is leaving the entry in total.
 * @param typed what the member put in each tag's field, keyed by target id.
 * @return the dimension, including the status the chip shows.
 */
fun herkunftDimension(
    tags: List<InventoryAllocation>,
    rest: String?,
    deducted: Double,
    typed: Map<String, String>,
): HerkunftDimension {
    val free = (rest?.toDoubleOrNull() ?: 0.0).scu()

    if (tags.isEmpty()) {
        return HerkunftDimension(
            tags = emptyList(),
            assigned = 0.0,
            fromRest = maxOf(deducted.scu(), 0.0),
            free = maxOf(deducted.scu(), 0.0),
            status = HerkunftStatus.FROM_REST,
            locked = false,
        )
    }

    val automatic = tags.size == 1 && free <= EPSILON

    val assigned =
        if (automatic) {
            deducted.scu()
        } else {
            tags.sumOf { typed[it.targetId]?.toDoubleOrNull() ?: 0.0 }.scu()
        }
    val fromRest = (deducted - assigned).scu()

    val status =
        when {
            automatic -> HerkunftStatus.AUTOMATIC
            fromRest < -EPSILON -> HerkunftStatus.OVERALLOCATED
            abs(fromRest) <= EPSILON -> HerkunftStatus.COVERED
            fromRest > free + EPSILON -> HerkunftStatus.REST_TOO_SMALL
            else -> HerkunftStatus.FROM_REST
        }

    return HerkunftDimension(
        tags = tags,
        assigned = assigned,
        fromRest = maxOf(fromRest, 0.0),
        free = free,
        status = status,
        locked = automatic,
    )
}

/**
 * What this dimension contributes to the write.
 *
 * Tags left at zero are omitted, so an all-zero plan becomes the empty list the server reads as
 * "take it from the rest first".
 *
 * @param deducted how much is leaving the entry, for the locked single-tag shape.
 * @param typed what the member put in each field.
 * @return the reductions to send, or an empty list for the default.
 */
fun HerkunftDimension.reductions(
    deducted: Double,
    typed: Map<String, String>,
): List<Pair<String, Double>> =
    if (locked) {
        tags.map { it.targetId to deducted.scu() }
    } else {
        tags.mapNotNull { tag ->
            val value = (typed[tag.targetId]?.toDoubleOrNull() ?: 0.0).scu()
            if (value > EPSILON) tag.targetId to value else null
        }
    }

/**
 * By how much the tags overshoot the deducted amount.
 *
 * @param deducted how much is leaving the entry.
 * @return the overshoot, or zero when there is none.
 */
fun HerkunftDimension.overshoot(deducted: Double): Double =
    maxOf((assigned - deducted).scu(), 0.0)

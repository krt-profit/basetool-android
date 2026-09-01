/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.InventoryAllocation
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import java.math.BigDecimal

/**
 * One line of the Zuordnung sheet, as the member is currently editing it.
 *
 * @property targetId the Auftrag or Einsatz.
 * @property label what it is called.
 * @property subtitle its second line, or `null`.
 * @property amount what the member has set it to, as typed.
 * @property serverAmount what the server currently holds, so the sheet knows what actually has to
 *   be written — and whether the target needs a `POST` or a `PATCH`.
 */
data class AllocationRow(
    val targetId: String,
    val label: String,
    val subtitle: String?,
    val amount: String,
    val serverAmount: String?,
) {
    /** Whether the server already carries an allocation for this target. */
    val existsOnServer: Boolean get() = serverAmount != null

    /** Whether this row is a change the save has to send. */
    val dirty: Boolean get() = amount.normalisedAmount() != (serverAmount ?: "0").normalisedAmount()
}

/**
 * The open Zuordnung sheet.
 *
 * The two splits are held apart all the way through, because the server reconciles them apart: the
 * same 642 SCU can be promised to an Auftrag and to an Einsatz, and one shared rest would be wrong
 * in both directions.
 *
 * @property entry the stock entry being split.
 * @property jobOrders the Auftrag rows.
 * @property missions the Einsatz rows.
 * @property orderTargets what "+ Auftrag zuordnen" can offer.
 * @property missionTargets what "+ Einsatz zuordnen" can offer.
 * @property picking which add-picker is open, or `null`.
 * @property saving whether the write sequence is running.
 * @property error the last refusal.
 * @property partial how many rows had already been written when one failed — the sheet says so
 *   rather than implying nothing happened.
 */
data class AllocationSheetState(
    val entry: InventoryEntry,
    val jobOrders: List<AllocationRow> = emptyList(),
    val missions: List<AllocationRow> = emptyList(),
    val orderTargets: List<AllocationTarget> = emptyList(),
    val missionTargets: List<AllocationTarget> = emptyList(),
    val picking: AllocationKind? = null,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val partial: Int = 0,
) {
    /** How much the entry holds in total. */
    private val total: BigDecimal get() = entry.amount?.toBigDecimalOrNull() ?: BigDecimal.ZERO

    /** What is left of the entry after the Auftrag split, as the member has it right now. */
    val jobOrderRest: BigDecimal get() = total - jobOrders.krtSum()

    /** The same for the Einsatz split. */
    val missionRest: BigDecimal get() = total - missions.krtSum()

    /** Whether either split promises more than the entry holds. */
    val overbooked: Boolean get() = jobOrderRest.signum() < 0 || missionRest.signum() < 0

    /** The rows whose amount differs from what the server holds. */
    val pending: List<Pair<AllocationKind, AllocationRow>>
        get() =
            jobOrders.filter { it.dirty }.map { AllocationKind.JOB_ORDER to it } +
                missions.filter { it.dirty }.map { AllocationKind.MISSION to it }

    /** Whether saving would send anything, and whether it is allowed to. */
    val submittable: Boolean get() = !saving && !overbooked && pending.isNotEmpty()

    /**
     * The rows of one split.
     *
     * @param kind which split.
     * @return its rows.
     */
    fun rows(kind: AllocationKind): List<AllocationRow> =
        if (kind == AllocationKind.JOB_ORDER) jobOrders else missions

    /**
     * What is left of one split.
     *
     * @param kind which split.
     * @return the rest.
     */
    fun rest(kind: AllocationKind): BigDecimal =
        if (kind == AllocationKind.JOB_ORDER) jobOrderRest else missionRest

    /**
     * The targets one split may still add.
     *
     * @param kind which split.
     * @return the targets not already on the sheet.
     */
    fun addable(kind: AllocationKind): List<AllocationTarget> {
        val taken = rows(kind).map { it.targetId }.toSet()
        val all = if (kind == AllocationKind.JOB_ORDER) orderTargets else missionTargets
        return all.filterNot { it.id in taken }
    }
}

/**
 * Sums what a split currently promises.
 *
 * @return the total of the rows' amounts, treating an unparseable one as zero — the field is
 *   mid-edit, and a sum that refuses to compute would freeze the rest figure the member is watching.
 */
internal fun List<AllocationRow>.krtSum(): BigDecimal =
    fold(BigDecimal.ZERO) { acc, row -> acc + (row.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }

/**
 * Compares two amounts by value rather than by spelling.
 *
 * `3` and `3.0` are the same promise; the server returns the second and the stepper produces the
 * first, so a string comparison would mark every untouched row dirty and re-send it.
 *
 * @return a canonical form for comparison.
 */
private fun String.normalisedAmount(): String =
    (trim().toBigDecimalOrNull() ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString()

/**
 * Builds the sheet's rows from what the server sent.
 *
 * @param allocations the entry's current allocations for one split.
 * @return one row per allocation, each remembering the server's figure so the save knows what
 *   changed.
 */
fun List<InventoryAllocation>.toRows(): List<AllocationRow> =
    map { allocation ->
        AllocationRow(
            targetId = allocation.targetId,
            label = allocation.label,
            subtitle = allocation.subtitle,
            amount = allocation.amount,
            serverAmount = allocation.amount,
        )
    }

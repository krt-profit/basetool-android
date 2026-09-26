/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * The book-in form's earmarks: where the amount being booked in is promised to go.
 *
 * Every operation is a transform on the shared [BookingState]; it holds no state of its own.
 *
 * @property source where the targets are read from.
 * @property scope the view model's scope, so a target read dies with the screen.
 * @property update applies a transform to the open form, or does nothing when it is closed.
 */
class BookingSplitHolder(
    private val source: InventorySource,
    private val scope: CoroutineScope,
    private val update: ((BookingState) -> BookingState) -> Unit,
) {
    /**
     * Reads the Auftrag and Einsatz targets once when the form opens.
     *
     * Filtering against the picked material happens on the device. A failure leaves the lists empty
     * without a banner.
     */
    fun load() {
        scope.launch {
            val orders = source.orderTargets()
            val missions = source.missionTargets()
            update {
                it.copy(
                    orderTargets = (orders as? ApiResult.Success)?.value.orEmpty(),
                    missionTargets = (missions as? ApiResult.Success)?.value.orEmpty(),
                )
            }
        }
    }

    /**
     * Opens or closes one of the two „+ zuordnen" pickers.
     *
     * @param kind which split, or `null` to close.
     */
    fun picking(kind: AllocationKind?) = update { it.copy(picking = kind) }

    /**
     * Adds an earmark row for the picked target, starting at the remaining rest.
     *
     * @param kind which split.
     * @param target what to earmark for.
     */
    fun add(
        kind: AllocationKind,
        target: AllocationTarget,
    ) = update { current ->
        val row =
            AllocationRow(
                targetId = target.id,
                label = target.label,
                subtitle = target.subtitle,
                amount = current.rest(kind).takeIf { it.signum() > 0 }.krtPlain(),
                serverAmount = null,
            )
        current.withSplit(kind, current.split(kind) + row).copy(picking = null, error = null)
    }

    /**
     * Sets one earmark's amount.
     *
     * @param kind which split.
     * @param targetId which row.
     * @param amount what was typed.
     */
    fun amount(
        kind: AllocationKind,
        targetId: String,
        amount: String,
    ) = update { current -> current.mapRow(kind, targetId) { it.copy(amount = amount) } }

    /**
     * Steps one earmark by whole units, clamped at zero.
     *
     * @param kind which split.
     * @param targetId which row.
     * @param by how many units, negative to step down.
     */
    fun step(
        kind: AllocationKind,
        targetId: String,
        by: Int,
    ) = update { current ->
        current.mapRow(kind, targetId) { row ->
            val stepped = (row.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) + BigDecimal(by)
            row.copy(amount = stepped.takeIf { it.signum() > 0 }.krtPlain())
        }
    }

    /**
     * Drops one earmark.
     *
     * @param kind which split.
     * @param targetId which row.
     */
    fun remove(
        kind: AllocationKind,
        targetId: String,
    ) = update { current ->
        current.withSplit(kind, current.split(kind).filterNot { it.targetId == targetId })
            .copy(error = null)
    }
}

/**
 * The same form with one earmark row transformed.
 *
 * @param kind which split.
 * @param targetId which row.
 * @param transform what to do to it.
 * @return the updated form, with the last refusal cleared.
 */
private fun BookingState.mapRow(
    kind: AllocationKind,
    targetId: String,
    transform: (AllocationRow) -> AllocationRow,
): BookingState =
    withSplit(
        kind,
        split(kind).map { if (it.targetId == targetId) transform(it) else it },
    ).copy(error = null)

/**
 * A figure as the amount field spells it.
 *
 * @return the plain string, or empty for `null` — an empty field reads as „not set yet", where a
 *   `0` reads as a promise of nothing.
 */
private fun BigDecimal?.krtPlain(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()

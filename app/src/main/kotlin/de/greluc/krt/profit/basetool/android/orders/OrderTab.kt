/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.annotation.StringRes
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.JobOrder

/**
 * The tabs of an Auftrag detail.
 *
 * There is no Materialbedarf tab (the cross-order `MaterialDemandScreen` covers it) and no Verlauf
 * tab (the API has no per-order activity trail).
 *
 * @property labelRes the tab's name.
 */
enum class OrderTab(
    @param:StringRes val labelRes: Int,
) {
    /** What was ordered, how much has arrived, and the requester's note. */
    POSITIONS(R.string.order_tab_positions),

    /** Who has taken the order on, with their own notes. */
    ASSIGNEES(R.string.order_tab_assignees),

    /** What has physically changed hands. */
    HANDOVERS(R.string.order_tab_handovers),

    /**
     * Which Staffel has signed up to deliver what.
     *
     * **Only on a Spezialkommando order.** The server refuses a claim on anything else, so this tab
     * is not among the ones a Staffel's own order offers — see [OrderDetailState.tabs].
     */
    CLAIMS(R.string.order_tab_claims),

    ;

    /**
     * How many rows this tab holds for one order.
     *
     * @param order the order.
     * @return the row count; the tab row prints it beside the label.
     */
    fun countIn(order: JobOrder): Int =
        when (this) {
            POSITIONS -> order.materials.size + order.items.size
            ASSIGNEES -> order.assignees.size
            HANDOVERS -> order.handovers.size + order.itemHandovers.size
            CLAIMS -> 0
        }
}

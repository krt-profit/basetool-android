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
 * The pages of an Auftrag, following design chapter 10 artboard 2.
 *
 * Pages rather than one long screen: an order carries its positions, who is on it and what has
 * changed hands, and on a phone those stacked into a column a member had to scroll past to reach
 * the part they came for.
 *
 * **Two of the chapter's four tabs are missing, deliberately rather than by oversight.**
 * *Materialbedarf* is the queue-wide fold of the same materials; the API sends it
 * (`JobOrderDto.aggregatedMaterials`) but the app's model does not map it yet. *Verlauf* is an
 * append-only activity trail the API does not expose at all. A tab that can only ever be empty is
 * worse than no tab — it promises content and then blames the member's filter for its absence — so
 * neither is drawn until it can be filled.
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
            // An order is one kind or the other, so the sum is the count of whichever it
            // carries. An item order used to read "0" here while its items sat unread.
            POSITIONS -> order.materials.size + order.items.size

            ASSIGNEES -> order.assignees.size

            HANDOVERS -> order.handovers.size + order.itemHandovers.size

            // The pledges are their own read, not part of the order aggregate, so this tab carries
            // no count rather than a stale one.
            CLAIMS -> 0
        }
}

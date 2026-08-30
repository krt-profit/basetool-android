/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemStock

/**
 * What an item line on the Positionen tab needs.
 *
 * One value rather than four parameters: the tab already takes the order, the gate and the refusal
 * slot, and four more would put it past what the codebase allows for one signature.
 *
 * @property onProduce open „Herstellung erfassen" for one line.
 * @property onHandOver open „Übergabe erfassen" for one line.
 * @property tree the two-level sub-assembly tree, empty for a material order.
 * @property itemStock the earmarked stock per item, for the availability chip.
 */
data class ItemLineBindings(
    val onProduce: (JobOrderItem) -> Unit,
    val onHandOver: (JobOrderItem) -> Unit,
    val tree: List<ItemBranch> = emptyList(),
    val itemStock: Map<String, JobOrderItemStock> = emptyMap(),
)

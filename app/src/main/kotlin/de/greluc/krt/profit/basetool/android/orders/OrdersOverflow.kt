/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The order list's overflow menu, holding the single „Materialbedarf" entry.
 *
 * Not gated: the Materialbedarf screen only reads the orders this list already shows.
 *
 * @param onOpenDemand open the Materialbedarf.
 */
@Composable
internal fun OrdersOverflow(onOpenDemand: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.orders_demand_title)
    ProvideScreenTopBar(
        actions = {
            KrtOverflowMenu(
                contentDescription = label,
                expanded = open,
                onExpandedChange = { open = it },
                items =
                    listOf(
                        KrtMenuItem(label = label, iconRes = DesignR.drawable.ic_krt_crate) {
                            open = false
                            onOpenDemand()
                        },
                    ),
            )
        },
    )
}

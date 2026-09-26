/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The in-fiction 404 for a `basetool://` link this build does not know, reached through the catch-all deep link on
 * `KrtDestination.NotFound`.
 *
 * @param onBackToBase invoked by the single action; goes to Übersicht and drops this screen from the
 *   back stack.
 * @param modifier layout modifier.
 */
@Composable
fun RouteNotFoundScreen(
    onBackToBase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_warning,
            title = stringResource(R.string.route_not_found_title),
            message = stringResource(R.string.route_not_found_message),
            actionText = stringResource(R.string.route_not_found_action),
            onAction = onBackToBase,
        )
    }
}

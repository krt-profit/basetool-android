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
 * Where a link this build does not know ends up: the in-fiction 404 of design chapter 14.
 *
 * Reached through the catch-all deep link on `KrtDestination.NotFound`, so the only way here is a
 * `basetool://…` address nothing in the graph declares — a notification from a newer server, a
 * hand-typed link, a web link into an area this build predates.
 *
 * Distinct from [PlaceholderScreen] on purpose. That one says "this area has no screen yet", which
 * is true and is not a failure; this one says the address itself goes nowhere. Chapter 14 reserves
 * the in-fiction wording for real failures precisely so it keeps meaning something.
 *
 * The title stays English in both locales — it is product canon, like „Access Denied" and
 * „System Malfunction" — over one plain German line, which is the shape every other error state in
 * this app already uses.
 *
 * @param onBackToBase invoked by the single action; goes to Übersicht and drops this screen from
 *   the back stack, because there is nothing to come back to.
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

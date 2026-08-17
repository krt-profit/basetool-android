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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.KrtDestination

/**
 * Stands in for a destination whose screen has not been built yet.
 *
 * Deliberately an ordinary empty state rather than the in-fiction error copy: nothing is broken
 * here, the area simply has no screen yet. The in-fiction wording is reserved for real failures
 * (403, 404, 500) so it keeps its meaning.
 *
 * Every one of these disappears as its chapter of the design specification is implemented.
 *
 * @param destination the area the user navigated to.
 * @param modifier layout modifier.
 */
@Composable
fun PlaceholderScreen(
    destination: KrtDestination,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KrtEmptyState(
            iconRes = destination.iconRes,
            title = destination.title,
            message = "Dieser Bereich wird gerade gebaut.",
        )
    }
}

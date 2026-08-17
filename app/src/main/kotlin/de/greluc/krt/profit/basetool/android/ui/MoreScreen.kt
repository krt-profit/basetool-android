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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtListRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.KrtDestination
import de.greluc.krt.profit.basetool.android.navigation.MORE_DESTINATIONS

/**
 * The "Mehr" overflow list.
 *
 * A phone bottom bar holds five destinations; everything else lives here as a plain list of dense
 * rows. The list is identical on tablets even though the rail already exposes three of these
 * entries, so a user who learned where something lives keeps finding it in the same place.
 *
 * @param onOpen invoked with the chosen destination.
 * @param modifier layout modifier.
 */
@Composable
fun MoreScreen(
    onOpen: (KrtDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = KrtSpacing.md),
    ) {
        KrtSectionTitle(
            text = "Sekundärziele",
            modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        )
        MORE_DESTINATIONS.forEach { destination ->
            KrtListRow(
                title = destination.title,
                leadingIcon = destination.iconRes,
                onClick = { onOpen(destination) },
            )
        }
    }
}

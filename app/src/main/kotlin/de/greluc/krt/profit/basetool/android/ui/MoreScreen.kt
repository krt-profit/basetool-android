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
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
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
 * It is a **list and nothing else**. The app lock and sign-out lived here while Einstellungen did
 * not exist yet; both have moved to that screen, where the design puts them and where sign-out is
 * no longer one mis-tap away from the row above it.
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
            text = stringResource(R.string.more_section_secondary),
            modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        )
        MORE_DESTINATIONS.forEach { destination ->
            KrtListRow(
                title = stringResource(destination.titleRes),
                leadingIcon = destination.iconRes,
                onClick = { onOpen(destination) },
            )
        }
    }
}

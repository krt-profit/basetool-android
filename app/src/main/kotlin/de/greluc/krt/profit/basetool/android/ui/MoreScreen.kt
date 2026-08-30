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
import de.greluc.krt.profit.basetool.android.ui.DenialToast
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import de.greluc.krt.profit.basetool.android.ui.rememberGated

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
    blueprintOverview: Boolean = true,
) {
    val denials = rememberDenialState()
    val locked = stringResource(R.string.blueprint_overview_locked)
    val detail = stringResource(R.string.blueprint_overview_locked_detail)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberRootScrollState())
                .padding(vertical = KrtSpacing.s12),
    ) {
        KrtSectionTitle(
            text = stringResource(R.string.more_section_secondary),
            modifier = Modifier.padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
        )
        MORE_DESTINATIONS.forEach { destination ->
            // Every entry is drawn; whether the caller may open it is the server's answer, and an
            // entry that is simply absent teaches nobody what to ask for (app ADR-0011). The
            // blueprint overview is the one entry here with a role behind it.
            val gate =
                Gate(
                    allowed = destination != KrtDestination.BlueprintOverview || blueprintOverview,
                    reason = locked,
                    detail = detail,
                )
            val (dim, click) = rememberGated(gate, { onOpen(destination) }, denials)
            KrtListRow(
                title = stringResource(destination.titleRes),
                leadingIcon = destination.iconRes,
                onClick = click,
                modifier = dim,
                showChevron = gate.allowed,
            )
        }
    }
    DenialToast(denials)
}

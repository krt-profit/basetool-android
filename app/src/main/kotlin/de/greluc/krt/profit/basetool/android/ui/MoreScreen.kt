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
 * The „Mehr" overflow list of destinations beyond the bottom bar.
 *
 * Identical on phones and tablets.
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

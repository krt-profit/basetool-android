/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Shows the booking form whenever one is open.
 *
 * Hosted beside the Lager route rather than inside it, because the form is opened from two places —
 * the screen's own action and an entry row — and a sheet owned by one of them would close when the
 * other recomposed.
 *
 * @param viewModel drives the form.
 */
@Composable
fun BookingHost(viewModel: BookingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { open ->
        BookingSheet(
            state = open,
            callbacks =
                BookingCallbacks(
                    onMode = viewModel::onModeChanged,
                    onAmount = viewModel::onAmountChanged,
                    onQuality = viewModel::onQualityChanged,
                    onMaterialQuery = viewModel::onMaterialQueryChanged,
                    onMaterial = viewModel::onMaterialChosen,
                    onPlaceQuery = viewModel::onPlaceQueryChanged,
                    onPlace = viewModel::onPlaceChosen,
                    onOutKind = viewModel::onOutKindChanged,
                    onMemberQuery = viewModel::onMemberQueryChanged,
                    onMember = viewModel::onMemberChosen,
                    onTerminal = viewModel::onTerminalChosen,
                    onSellAmount = viewModel::onSellAmountChanged,
                    onNote = viewModel::onNoteChanged,
                    onSave = viewModel::onSave,
                    onDismiss = viewModel::onDismissed,
                ),
        )
    }
}

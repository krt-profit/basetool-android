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
 * Hosted beside the Lager route because the form is opened both from the screen's action and from an
 * entry row.
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
                    onKind = viewModel::onKindChanged,
                    onSplitAmount = viewModel.splits::amount,
                    onSplitStep = viewModel.splits::step,
                    onSplitPicking = viewModel.splits::picking,
                    onSplitAdd = viewModel.splits::add,
                    onSplitRemove = viewModel.splits::remove,
                    onGameItemQuery = viewModel::onGameItemQueryChanged,
                    onGameItem = viewModel::onGameItemChosen,
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
                    onJobOrderShare = viewModel::onJobOrderShare,
                    onMissionShare = viewModel::onMissionShare,
                    onOrgUnit = viewModel::onOrgUnitChosen,
                    onMergeStock = viewModel::onMergeStockChanged,
                    onSellAmount = viewModel::onSellAmountChanged,
                    onNote = viewModel::onNoteChanged,
                    onSave = viewModel::onSave,
                    onDismiss = viewModel::onDismissed,
                    onConflictReload = viewModel::onConflictReload,
                ),
        )
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/**
 * The conflict dialog shown when a save is refused with a 409 (REQ-APP-UI-008).
 *
 * Says the save did not happen and the input stays in the form; it names no other user and writes
 * nothing to the clipboard. The primary action reloads, it does not re-send.
 *
 * @param onReload the primary action: discard this attempt and read the record again.
 * @param onDismiss the secondary action: keep the form and its input, and close the dialog.
 */
@Composable
fun ConflictModal(
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.conflict_title),
        confirmText = stringResource(R.string.conflict_reload),
        cancelText = stringResource(R.string.conflict_cancel),
        onConfirm = onReload,
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.conflict_body),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
}

/**
 * Shows [ConflictModal] for a refused save, once per refusal.
 *
 * Dismissal is tracked by instance identity, since two refusals can be equal as data.
 *
 * @param error the write's last outcome, or `null`.
 * @param onReload the primary action: read the record again.
 */
@Composable
fun ConflictOn(
    error: ApiError?,
    onReload: () -> Unit,
) {
    var seen by remember { mutableStateOf<ApiError?>(null) }
    if (shouldRaiseConflict(error = error, seen = seen)) {
        ConflictModal(onReload = onReload, onDismiss = { seen = error })
    }
}

/**
 * Whether a refusal still needs to be shown.
 *
 * @param error the write's last outcome.
 * @param seen the refusal the member has already dismissed, if any.
 * @return `true` for a conflict the member has not dismissed yet.
 */
internal fun shouldRaiseConflict(
    error: ApiError?,
    seen: ApiError?,
): Boolean = error is ApiError.OptimisticLock && seen !== error

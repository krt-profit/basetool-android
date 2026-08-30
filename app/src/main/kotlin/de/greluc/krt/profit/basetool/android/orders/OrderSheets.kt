/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * Where the order should stand.
 *
 * The current status is shown as chosen rather than left out: a picker that hides where the order
 * is now reads as if it had no status at all.
 *
 * @param current where it stands.
 * @param state the screen, for the save gate.
 * @param actions what it reports back.
 */
@Composable
internal fun StatusSheet(
    current: JobOrderStatus,
    state: OrderDetailState,
    actions: OrderDetailActions,
) {
    KrtBottomSheet(
        onDismiss = actions.onDismissStatusPicker,
        modifier = Modifier.testTag(ORDER_STATUS_SHEET_TAG),
        title = stringResource(R.string.order_detail_change_status),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            state.order?.let { order ->
                Text(
                    text =
                        stringResource(
                            R.string.order_detail_status_scope,
                            order.displayId,
                            stringResource(current.labelRes()),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            // UNKNOWN is absent on purpose: it carries a status this build has never seen, and
            // asking the server to move an order into one is not a request that means anything.
            STATUS_CHOICES.forEach { status ->
                StatusOption(
                    status = status,
                    current = current,
                    chosen = state.statusChoice,
                    enabled = state.writable,
                    onSelect = { actions.onStatusSelected(status) },
                )
            }
            Text(
                text = stringResource(R.string.order_detail_status_role_note),
                style = MaterialTheme.typography.labelSmall,
                color = KrtPalette.TextMuted,
            )
            KrtCtaButton(
                text = stringResource(R.string.order_detail_status_apply),
                onClick = actions.onApplyStatus,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.writable && state.statusChoice != null && !state.saving,
            )
        }
    }
    val choice = state.statusChoice
    if (state.statusConfirmOpen && choice != null) {
        KrtModal(
            // One title for both terminal moves asked "Auftrag abschließen?" while rejecting one.
            // They are equally final and they do not mean the same thing — the modal has to name
            // the move it is about to make, or the confirmation confirms the wrong thing.
            title =
                stringResource(
                    if (choice == JobOrderStatus.REJECTED) {
                        R.string.order_detail_status_confirm_reject_title
                    } else {
                        R.string.order_detail_status_confirm_title
                    },
                ),
            confirmText = stringResource(R.string.order_detail_status_apply),
            onConfirm = actions.onApplyStatus,
            onDismiss = actions.onDismissStatusConfirm,
            // Artboard 9 distinguishes the two terminal ends: finishing an order is orange, and
            // refusing one is red. They are equally final, but they do not mean the same thing.
            tone =
                if (choice == JobOrderStatus.REJECTED) KrtModalTone.Danger else KrtModalTone.Standard,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.order_detail_status_confirm_body,
                        stringResource(choice.labelRes()),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
        }
    }
}

/**
 * One row of the status picker.
 *
 * Design ch. 10 artboard 8: a colour square for the status, its name, what choosing it commits the
 * member to, and - on the one the order is already in - an inert "Aktuell" chip instead of a
 * choice. Showing the current status rather than hiding it is orientation before brevity.
 *
 * **What this row cannot do yet:** the chapter wants the offered set to come from the API
 * (`transitions[]`, with a reason on the ones the caller's role forbids). `JobOrderDto` carries no
 * such field, so every non-current status is offered and the server refuses what it must. Guessing
 * the rules here would put a second, drifting copy of the permission model in the client.
 *
 * @param status the row's status.
 * @param current where the order stands.
 * @param chosen what the member has picked so far.
 * @param enabled whether writes are possible at all.
 * @param onSelect picks this row.
 */
@Composable
private fun StatusOption(
    status: JobOrderStatus,
    current: JobOrderStatus,
    chosen: JobOrderStatus?,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val isCurrent = status == current
    val isChosen = status == chosen
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && !isCurrent, onClick = onSelect)
                .background(if (isChosen) KrtPalette.SurfaceInput else Color.Transparent)
                .padding(KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(STATUS_SWATCH).background(status.swatch()))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(status.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) KrtPalette.TextMuted else KrtPalette.White,
            )
            // Only for a move that is on offer. The current status carries no consequence,
            // because choosing it is not a thing the sheet lets anyone do.
            status.consequenceRes()?.takeIf { !isCurrent }?.let { note ->
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.labelSmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        if (isCurrent) {
            KrtChip(
                text = stringResource(R.string.order_detail_status_current),
                tone = KrtChipTone.Warning,
            )
        } else if (isChosen) {
            KrtIcon(
                id = DesignR.drawable.ic_krt_check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The colour the design gives each status.
 *
 * @return the swatch colour for the square that leads the row.
 */
private fun JobOrderStatus.swatch(): Color =
    when (this) {
        JobOrderStatus.OPEN -> KrtPalette.Info
        JobOrderStatus.IN_PROGRESS -> KrtPalette.Warning
        JobOrderStatus.COMPLETED -> KrtPalette.SuccessText
        JobOrderStatus.REJECTED -> KrtPalette.DangerText
        JobOrderStatus.UNKNOWN -> KrtPalette.Gray3
    }

/**
 * What choosing this status commits the member to.
 *
 * @return a line for the statuses that carry a consequence worth stating before the tap, `null` for
 *   the ones that do not. Only the terminal pair and the reopening move do: nothing else changes
 *   what anyone else can still do with the order.
 */
private fun JobOrderStatus.consequenceRes(): Int? =
    when (this) {
        JobOrderStatus.OPEN -> {
            R.string.order_detail_status_note_open
        }

        JobOrderStatus.COMPLETED, JobOrderStatus.REJECTED -> {
            R.string.order_detail_status_note_terminal
        }

        else -> {
            null
        }
    }

/** Edge of the status colour square, as design ch. 10 artboard 8 draws it. */
private val STATUS_SWATCH = 10.dp

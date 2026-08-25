/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeBand
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAssignee
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadgeKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import java.time.Instant
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/*
 * The two write surfaces an order's detail opens, split out of OrdersScreen.kt when that file
 * crossed the thirty-function ceiling. They belong together: both are bottom sheets over the same
 * order, both are drawn by design ch. 10 (artboards 5–9), and both were added in the same pass.
 */

/**
 * The caller's own note on this order.
 *
 * @param draft what the editor holds.
 * @param state the screen, for the save gate and the last refusal.
 * @param actions what it reports back.
 */
@Composable
internal fun NoteSheet(
    draft: String,
    state: OrderDetailState,
    actions: OrderDetailActions,
) {
    KrtBottomSheet(
        onDismiss = actions.onDismissNote,
        modifier = Modifier.testTag(ORDER_NOTE_SHEET_TAG),
        title = stringResource(R.string.order_detail_note),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            // Whose note this is, on the sheet itself: the API only ever lets a member write their
            // own, and the sheet is reached from a list of everybody's (design ch. 10 artboard 5).
            state.order?.let { order ->
                Text(
                    text = stringResource(R.string.order_detail_note_scope, order.displayId),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            state.rejectedNote?.let { refused -> NoteConflict(refused = refused, actions = actions) }
            Text(
                text = stringResource(R.string.order_detail_note_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = draft,
                onValueChange = { typed -> actions.onNoteChanged(typed.take(NOTE_MAX_LENGTH)) },
                label = stringResource(R.string.order_detail_note),
                enabled = !state.saving,
            )
            Text(
                text = stringResource(R.string.order_detail_note_counter, draft.length, NOTE_MAX_LENGTH),
                style = MaterialTheme.typography.labelSmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.align(Alignment.End),
            )
            // The conflict is drawn above as its own block, so it does not also arrive as a bare
            // error line saying the same thing twice.
            state.error?.takeIf { state.rejectedNote == null }?.let { error -> WriteError(error = error) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = actions.onDismissNote,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = actions.onSaveNote,
                    modifier = Modifier.testTag(ORDER_NOTE_SAVE_TAG),
                    enabled = state.writable,
                )
            }
        }
    }
}

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
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
    if (state.statusConfirmOpen) {
        val choice = state.statusChoice
        if (choice != null) {
            KrtModal(
                title = stringResource(R.string.order_detail_status_confirm_title),
                confirmText = stringResource(choice.labelRes()),
                onConfirm = actions.onApplyStatus,
                onDismiss = actions.onDismissStatusConfirm,
                tone = KrtModalTone.Danger,
            ) {
                Text(
                    text = stringResource(R.string.order_detail_status_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                )
            }
        }
    }
}

/**
 * One row of the status picker.
 *
 * Design ch. 10 artboard 8: a colour square for the status, its name, what choosing it means, and
 * — on the one the order is in — an „Aktuell" chip instead of a selection box. Choosing is not
 * applying; the sheet's button does that.
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
                .background(if (isChosen || isCurrent) KrtPalette.SurfaceInput else Color.Transparent)
                .padding(KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(STATUS_SWATCH)
                    .background(status.swatch()),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(status.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) KrtPalette.TextMuted else KrtPalette.White,
            )
            status.consequenceRes()?.let { note ->
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.labelSmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        if (isCurrent) {
            KrtChip(text = stringResource(R.string.order_detail_status_current), tone = KrtChipTone.Warning)
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
 * What a lost optimistic-lock race looks like on the note sheet.
 *
 * Design ch. 10 artboard 7. The field above has already been reset to what the server holds; this
 * shows the text that was refused and offers to put it back, because the alternative — dropping it
 * — loses a paragraph the member wrote to a colleague who happened to save first.
 *
 * @param refused the text the server would not take.
 * @param actions what the sheet reports back.
 */
@Composable
private fun NoteConflict(
    refused: String,
    actions: OrderDetailActions,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Inset) {
        Text(
            text = stringResource(R.string.order_detail_note_conflict_title),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.Warning,
        )
        Text(
            text = stringResource(R.string.order_detail_note_conflict_rejected),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(top = KrtSpacing.xs),
        )
        Text(
            text = refused,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.White,
        )
        KrtGhostButton(
            text = stringResource(R.string.order_detail_note_conflict_reapply),
            onClick = actions.onReapplyRejectedNote,
            modifier = Modifier.padding(top = KrtSpacing.xs),
        )
    }
}

/**
 * The colour the design gives each status.
 *
 * @return the swatch colour for the square that leads the row.
 */
@Composable
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
 * @return a line for the statuses that carry a consequence worth stating before the tap, `null`
 *   for the ones that do not. Only the terminal pair does: nothing in this app moves an order back
 *   out of them.
 */
private fun JobOrderStatus.consequenceRes(): Int? =
    when (this) {
        JobOrderStatus.OPEN -> R.string.order_detail_status_note_open
        JobOrderStatus.COMPLETED, JobOrderStatus.REJECTED -> R.string.order_detail_status_note_terminal
        else -> null
    }

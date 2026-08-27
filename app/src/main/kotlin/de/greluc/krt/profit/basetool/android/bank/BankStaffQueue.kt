/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSuccessButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The staff queue, for the tests that read it. */
const val BANK_STAFF_QUEUE_TAG: String = "bank-staff-queue"

/** The confirmation sheet artboard 5 does not draw but the server requires. */
const val BANK_CONFIRM_SHEET_TAG: String = "bank-confirm-sheet"

/**
 * What the staff queue reports back.
 *
 * @property onConfirm the employee wants to book a request.
 * @property onReject they want to refuse it.
 */
data class BankStaffQueueActions(
    val onConfirm: (BankBookingRequest) -> Unit,
    val onReject: (BankBookingRequest) -> Unit,
)

/**
 * The Verwaltung scope's Anträge tab — design chapter 12, artboard 5.
 *
 * The same card the member surface uses, with the bank employee's two decisions in place of the
 * holder's one. Here `BESTÄTIGEN` and `ABLEHNEN` are exactly right, unlike on artboard 1: this is
 * the surface that has `POST …/confirm` and `POST …/reject` behind it.
 *
 * @param state what the scope holds.
 * @param onRefresh a pull-to-refresh.
 * @param actions what a row reports back.
 * @param modifier layout modifier.
 */
@Composable
fun BankStaffQueue(
    state: BankStaffState,
    onRefresh: () -> Unit,
    actions: BankStaffQueueActions,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.queue.isEmpty()) {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_clipboard_check,
                title = stringResource(R.string.bank_staff_queue_empty_title),
                message = stringResource(R.string.bank_staff_queue_empty_message),
                modifier = Modifier.padding(KrtSpacing.lg),
            )
            return@PullToRefreshBox
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(BANK_STAFF_QUEUE_TAG),
            contentPadding = PaddingValues(KrtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            items(state.queue, key = { it.id }) { request ->
                BankRequestCard(request = request) {
                    StaffRequestActions(
                        request = request,
                        busy = state.busyId == request.id,
                        actions = actions,
                    )
                }
            }
            if (state.countsPartial) {
                // Stated, not tucked into a tooltip: a queue that ends early has to say so where
                // it ends (ADR-0104).
                item(key = "partial") {
                    Text(
                        text = stringResource(R.string.bank_staff_queue_partial),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.Warning,
                        modifier = Modifier.padding(KrtSpacing.sm),
                    )
                }
            }
        }
    }
}

/**
 * The bank employee's two decisions.
 *
 * @param request the request.
 * @param busy whether a decision on it is in flight.
 * @param actions what the row reports back.
 */
@Composable
private fun RowScope.StaffRequestActions(
    request: BankBookingRequest,
    busy: Boolean,
    actions: BankStaffQueueActions,
) {
    if (request.status != BankRequestStatus.PENDING) {
        return
    }
    KrtQuietDangerButton(
        text = stringResource(R.string.bank_staff_reject),
        onClick = { actions.onReject(request) },
        modifier = Modifier.weight(1f),
        enabled = !busy,
        iconRes = DesignR.drawable.ic_krt_close,
    )
    KrtSuccessButton(
        text = stringResource(R.string.bank_staff_confirm),
        onClick = { actions.onConfirm(request) },
        modifier = Modifier.weight(1f),
        enabled = !busy,
        iconRes = DesignR.drawable.ic_krt_check,
    )
}

/**
 * Books a request — the sheet artboard 5 draws as a bare button.
 *
 * **It cannot be a button.** `ConfirmBankBookingRequest.holderId` is required by the server: a
 * booked deposit or withdrawal records which Verwahrer received or paid the money out
 * (REQ-BANK-040/-044). An over-limit request is additionally refused with
 * `BANK_OWNER_APPROVAL_REQUIRED` unless the employee attests that the responsible holder approved
 * (REQ-BANK-041). The web frontend has a modal for exactly this; the drawing has neither control,
 * and the gap went back to the design side rather than being coded around.
 *
 * @param state what the sheet holds.
 * @param holders the holders it may name.
 * @param actions what the sheet reports back.
 */
@Composable
fun BankConfirmSheet(
    state: BankConfirmState,
    holders: List<BankHolderOption>,
    actions: BankConfirmSheetActions,
) {
    var holderOpen by remember { mutableStateOf(false) }
    var targetOpen by remember { mutableStateOf(false) }
    val options = holders.map { KrtOption(value = it.id, label = it.label) }
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.bank_staff_confirm_title),
        modifier = Modifier.testTag(BANK_CONFIRM_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.bank_staff_confirm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtSelectField(
                value = options.firstOrNull { it.value == state.holderId }?.label.orEmpty(),
                options = options,
                onSelect = { actions.onHolder(it.value) },
                expanded = holderOpen,
                onExpandedChange = { holderOpen = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_staff_confirm_holder),
                selectedValue = state.holderId,
            )
            if (state.request.kind == BankRequestKind.TRANSFER) {
                KrtSelectField(
                    value =
                        options.firstOrNull { it.value == state.destinationHolderId }
                            ?.label
                            .orEmpty(),
                    options = options,
                    onSelect = { actions.onDestinationHolder(it.value) },
                    expanded = targetOpen,
                    onExpandedChange = { targetOpen = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.bank_staff_confirm_target_holder),
                    selectedValue = state.destinationHolderId,
                )
            }
            if (state.request.requiresOwnerApproval) {
                ApprovalAttestation(state = state, onAttest = actions.onAttest)
            }
            KrtTextField(
                value = state.staffNote,
                onValueChange = actions.onStaffNote,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_staff_confirm_note),
                placeholder = stringResource(R.string.bank_staff_confirm_note_placeholder),
            )
            state.error?.let { error ->
                Text(
                    text = bankRequestErrorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            ) {
                KrtGhostButton(
                    text = stringResource(R.string.bank_request_cancel),
                    onClick = actions.onDismiss,
                    modifier = Modifier.weight(1f),
                )
                KrtCtaButton(
                    text = stringResource(R.string.bank_staff_confirm),
                    onClick = actions.onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = state.submittable && !state.saving,
                    iconRes = DesignR.drawable.ic_krt_check,
                )
            }
        }
    }
}

/**
 * The attestation an over-limit request cannot be booked without.
 *
 * It says what the server already knows — whether the responsible holder granted their approval —
 * so the employee is ticking a box they can check rather than one they must take on trust.
 *
 * @param state what the sheet holds.
 * @param onAttest reports the tick.
 */
@Composable
private fun ApprovalAttestation(
    state: BankConfirmState,
    onAttest: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtCheckboxRow(
            label = stringResource(R.string.bank_staff_confirm_attest),
            checked = state.approvalAttested,
            onCheckedChange = onAttest,
        )
        Text(
            text =
                if (state.request.ownerApprovalGranted) {
                    state.request.ownerApprovalBy?.let {
                        stringResource(R.string.bank_request_approved_by, it)
                    } ?: stringResource(R.string.bank_request_approved)
                } else {
                    stringResource(R.string.bank_staff_confirm_attest_outstanding)
                },
            style = MaterialTheme.typography.bodySmall,
            color =
                if (state.request.ownerApprovalGranted) {
                    KrtPalette.SuccessText
                } else {
                    KrtPalette.Warning
                },
        )
    }
}

/**
 * A holder as the confirmation picker offers them.
 *
 * @property id the holder.
 * @property label how they read in the picker.
 */
data class BankHolderOption(
    val id: String,
    val label: String,
)

/**
 * What the confirmation sheet reports back.
 *
 * @property onHolder a holder was picked.
 * @property onDestinationHolder a transfer's receiving holder was picked.
 * @property onAttest the approval attestation was ticked.
 * @property onStaffNote the employee's note was typed.
 * @property onSubmit the booking was sent.
 * @property onDismiss the sheet was closed.
 */
data class BankConfirmSheetActions(
    val onHolder: (String) -> Unit,
    val onDestinationHolder: (String) -> Unit,
    val onAttest: (Boolean) -> Unit,
    val onStaffNote: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

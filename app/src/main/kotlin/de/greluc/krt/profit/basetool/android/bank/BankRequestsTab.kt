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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankRequestApprover
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSuccessButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The Anträge list, for the tests that read it. */
const val BANK_REQUESTS_TAG: String = "bank-requests"

/**
 * What a request row reports back.
 *
 * @property onGrant the holder granted the owner approval.
 * @property onRevoke the holder took it back.
 * @property onEdit the requester wants to correct their own request.
 * @property onWithdraw the requester withdrew it.
 */
data class BankRequestRowActions(
    val onGrant: (BankBookingRequest) -> Unit,
    val onRevoke: (BankBookingRequest) -> Unit,
    val onEdit: (BankBookingRequest) -> Unit,
    val onWithdraw: (BankBookingRequest) -> Unit,
)

/**
 * The Anträge tab: the caller's own requests and those awaiting their approval, in one list.
 *
 * @param state what the tab holds.
 * @param onRefresh a pull-to-refresh.
 * @param actions what a row reports back.
 * @param modifier layout modifier.
 */
@Composable
fun BankRequestsTab(
    state: BankRequestsState,
    onRefresh: () -> Unit,
    actions: BankRequestRowActions,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.rows.isEmpty()) {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_bank,
                title = stringResource(R.string.bank_requests_empty_title),
                message = stringResource(R.string.bank_requests_empty_message),
                modifier = Modifier.padding(KrtSpacing.s16),
            )
            return@PullToRefreshBox
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(BANK_REQUESTS_TAG),
            contentPadding = PaddingValues(KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            items(state.rows, key = { it.request.id }) { row ->
                BankRequestCard(request = row.request) {
                    MemberRequestActions(
                        row = row,
                        busy = state.busyId == row.request.id,
                        online = state.online,
                        actions = actions,
                    )
                }
            }
        }
    }
}

/**
 * One request card, shared by the member and staff surfaces.
 *
 * The facts are fixed; [actions] is the slot for what the surface lets the caller do.
 *
 * @param request the request.
 * @param actions what this surface offers on it; empty for a decided one.
 */
@Composable
internal fun BankRequestCard(
    request: BankBookingRequest,
    actions: @Composable RowScope.() -> Unit,
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            RequestHeader(request = request)
            request.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium, color = KrtPalette.Gray1)
            }
            Text(
                text =
                    stringResource(
                        R.string.bank_request_meta,
                        request.requester.orEmpty(),
                        request.accountName.orEmpty(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            request.rejectReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                content = actions,
            )
        }
    }
}

/**
 * The amount, the movement and where the request stands.
 *
 * @param request the request.
 */
@Composable
private fun RequestHeader(request: BankBookingRequest) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = request.signedAmount(),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = request.amountTone(),
                ),
        )
        request.kind?.let { kind ->
            KrtChip(text = stringResource(kind.labelRes()), tone = KrtChipTone.Muted)
        }
        Spacer(modifier = Modifier.weight(1f))
        val trailing =
            request.approvalChip()
                ?: (stringResource(request.statusLabelRes()) to request.statusTone())
        KrtChip(text = trailing.first, tone = trailing.second)
    }
}

/**
 * The member's actions on one request: grant or revoke the owner approval, or edit and withdraw
 * their own.
 *
 * There is no reject; refusing a request is a bank employee's act.
 *
 * @param row the request plus who the caller is to it.
 * @param busy whether a write against it is in flight.
 * @param online whether any write may be sent.
 * @param actions what the row reports back.
 */
@Composable
private fun RowScope.MemberRequestActions(
    row: BankRequestRow,
    busy: Boolean,
    online: Boolean,
    actions: BankRequestRowActions,
) {
    val request = row.request
    if (request.status != BankRequestStatus.PENDING) {
        return
    }
    val enabled = online && !busy
    run {
        when {
            row.actionable && request.requiresOwnerApproval && !request.ownerApprovalGranted -> {
                KrtSuccessButton(
                    text = stringResource(R.string.bank_request_grant),
                    onClick = { actions.onGrant(request) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    iconRes = DesignR.drawable.ic_krt_check,
                )
            }

            row.actionable && request.ownerApprovalGranted -> {
                KrtGhostButton(
                    text = stringResource(R.string.bank_request_revoke),
                    onClick = { actions.onRevoke(request) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    iconRes = DesignR.drawable.ic_krt_reset,
                )
            }

            row.mine -> {
                if (!request.ownerApprovalGranted) {
                    KrtGhostButton(
                        text = stringResource(R.string.bank_request_edit),
                        onClick = { actions.onEdit(request) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        iconRes = DesignR.drawable.ic_krt_edit,
                    )
                }
                KrtQuietDangerButton(
                    text = stringResource(R.string.bank_request_withdraw),
                    onClick = { actions.onWithdraw(request) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    iconRes = DesignR.drawable.ic_krt_close,
                )
            }
        }
    }
}

/**
 * The amount with the sign the movement gives it.
 *
 * @return the grouped, signed amount.
 */
@Composable
internal fun BankBookingRequest.signedAmount(): String {
    val shown = formatAmount(amount.orEmpty())
    return when (kind) {
        BankRequestKind.DEPOSIT -> "+$shown"
        BankRequestKind.WITHDRAWAL -> "−$shown"
        else -> shown
    }
}

/**
 * The tint the amount is stated in.
 *
 * @return green in, red out, neutral for a transfer between two org-unit accounts.
 */
internal fun BankBookingRequest.amountTone(): Color =
    when (kind) {
        BankRequestKind.DEPOSIT -> KrtPalette.SuccessText
        BankRequestKind.WITHDRAWAL -> KrtPalette.DangerText
        else -> KrtPalette.Gray1
    }

/**
 * Which word names this request's state.
 *
 * @return the string resource.
 */
internal fun BankBookingRequest.statusLabelRes(): Int =
    when (status) {
        BankRequestStatus.CONFIRMED -> R.string.bank_request_status_confirmed
        BankRequestStatus.REJECTED -> R.string.bank_request_status_rejected
        BankRequestStatus.CANCELLED -> R.string.bank_request_status_cancelled
        else -> R.string.bank_request_status_pending
    }

/**
 * The tone that state is stated in.
 *
 * @return the chip tone.
 */
internal fun BankBookingRequest.statusTone(): KrtChipTone =
    when (status) {
        BankRequestStatus.CONFIRMED -> KrtChipTone.Success
        BankRequestStatus.REJECTED -> KrtChipTone.Danger
        BankRequestStatus.CANCELLED -> KrtChipTone.Muted
        else -> KrtChipTone.Data
    }

/**
 * The approval chip naming the approver class the request waits on (REQ-BANK-047).
 *
 * A request needs at most one owner approval, so there is no counter. A decided request shows its
 * plain status instead.
 *
 * @return the chip's label and tone, or `null` when the plain status should be shown instead.
 */
@Composable
private fun BankBookingRequest.approvalChip(): Pair<String, KrtChipTone>? {
    if (!requiresOwnerApproval || status != BankRequestStatus.PENDING) {
        return null
    }
    return if (ownerApprovalGranted) {
        val label =
            ownerApprovalBy?.let { stringResource(R.string.bank_request_approved_by, it) }
                ?: stringResource(R.string.bank_request_approved)
        label to KrtChipTone.Success
    } else {
        stringResource(requiredApprover.awaitingLabelRes()) to KrtChipTone.Warning
    }
}

/**
 * Which class the request is waiting on.
 *
 * A `null` approver on a flagged request is a band this build does not know; it falls back to the
 * unqualified wording rather than naming the wrong office.
 *
 * @return the string resource.
 */
private fun BankRequestApprover?.awaitingLabelRes(): Int =
    when (this) {
        BankRequestApprover.RESPONSIBLE_HOLDER -> R.string.bank_request_awaiting_holder
        BankRequestApprover.BANK_MANAGEMENT -> R.string.bank_request_awaiting_bank_management
        BankRequestApprover.ORGANISATIONSLEITUNG -> R.string.bank_request_awaiting_ol
        null -> R.string.bank_request_awaiting_approval
    }

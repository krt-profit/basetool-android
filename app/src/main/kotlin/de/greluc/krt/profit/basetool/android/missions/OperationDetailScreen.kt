/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.OperationDetail
import de.greluc.krt.profit.basetool.android.core.data.OperationMissionResult
import de.greluc.krt.profit.basetool.android.core.data.OperationOverview
import de.greluc.krt.profit.basetool.android.core.data.OperationPayout
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.participantLabel
import de.greluc.krt.profit.basetool.android.ui.writeFailureText
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the scrolling content of the Operation detail. */
const val OPERATION_DETAIL_CONTENT_TAG: String = "operation-detail-content"

/** Test handle for the „⋮" that opens „Operation bearbeiten". */
const val OPERATION_EDIT_MENU_TAG: String = "operation-edit-menu"

/** Test handle for a payout row's confirmation. */
const val OPERATION_PAID_OUT_TAG: String = "operation-paid-out"

/**
 * One Operation in full (design spec ch. 06 §5), as one scrolling page: what it earned, which
 * Einsätze earned it, and who gets what.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onOpenMission an Einsatz row was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationDetailScreen(
    state: OperationDetailState,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onOpenMission: (String) -> Unit,
    onTogglePaidOut: (OperationPayout) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val overview = state.overview
    val phase = state.phase
    Column(modifier = modifier.fillMaxSize()) {
        when {
            overview != null -> {
                OperationDetailHead(
                    detail = overview.detail,
                    overview = overview,
                    onEdit = onEdit,
                )
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OperationDetailBody(
                        overview = overview,
                        myPayout = state.myPayout,
                        identityKnown = state.myUserId != null,
                        onOpenMission = onOpenMission,
                        state = state,
                        onTogglePaidOut = onTogglePaidOut,
                    )
                }
            }

            phase is OperationDetailPhase.Failed -> {
                val retryIn = state.retryIn
                if (retryIn != null) {
                    KrtRetryCountdown(
                        secondsLeft = retryIn,
                        title = stringResource(R.string.retry_busy_title),
                        message = stringResource(R.string.retry_busy_message, retryIn),
                        retryLabel = stringResource(R.string.retry_now),
                        onRetry = onRetryNow,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                    )
                } else {
                    OperationDetailFailure(error = phase.error)
                }
            }

            else -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.operation_detail_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The head: title, status and the two counts the design puts under them.
 *
 * @param detail the Operation.
 * @param overview everything loaded with it, which is where the counts come from.
 * @param onEdit opens the edit form, or `null` where the screen cannot navigate.
 */
@Composable
private fun OperationDetailHead(
    detail: OperationDetail,
    overview: OperationOverview,
    onEdit: (() -> Unit)?,
) {
    val facts =
        pluralStringResource(
            R.plurals.operation_detail_missions,
            overview.rollup.missions.size,
            overview.rollup.missions.size,
        ) + " · " +
            pluralStringResource(
                R.plurals.operation_detail_participants,
                overview.payouts.participants,
                overview.payouts.participants,
            )
    val editLabel = stringResource(R.string.operation_form_edit_title)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    ProvideScreenTopBar(
        title = detail.name,
        actions =
            onEdit?.let { edit ->
                {
                    KrtOverflowMenu(
                        contentDescription = editLabel,
                        expanded = menuOpen,
                        onExpandedChange = { menuOpen = it },
                        modifier = Modifier.testTag(OPERATION_EDIT_MENU_TAG),
                        items =
                            listOf(
                                KrtMenuItem(
                                    label = editLabel,
                                    iconRes = DesignR.drawable.ic_krt_edit,
                                    onClick = {
                                        menuOpen = false
                                        edit()
                                    },
                                ),
                            ),
                    )
                }
            },
        subtitle = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtStatusBadge(text = detail.statusLabel(), tone = detail.status.tone())
                Text(
                    text = facts,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        },
    )
    if (detail.payoutPreliminary == true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            Text(
                text = stringResource(R.string.operation_detail_preliminary),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.Warning,
            )
            KrtHairlineRule()
        }
    }
}

/**
 * The three sections beneath the head.
 *
 * @param overview everything loaded.
 * @param myPayout the caller's own payout row, when it could be identified.
 * @param identityKnown whether the caller's user id is known at all, which is what tells "you did
 *   not take part" apart from "we could not find out".
 * @param onOpenMission an Einsatz row was tapped.
 * @param state the screen, for the mission-manager grant and whether a write may be sent.
 * @param onTogglePaidOut a payout row's confirmation was taken.
 */
@Composable
private fun OperationDetailBody(
    overview: OperationOverview,
    myPayout: OperationPayout?,
    identityKnown: Boolean,
    onOpenMission: (String) -> Unit,
    state: OperationDetailState,
    onTogglePaidOut: (OperationPayout) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(OPERATION_DETAIL_CONTENT_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        if (!state.online) {
            item(key = "offline") { OfflineBand() }
        }
        state.error?.let { error ->
            item(key = "write-error") {
                KrtFieldError(
                    text =
                        error.writeFailureText(
                            if (error is ApiError.Forbidden) {
                                R.string.operation_detail_payout_not_allowed
                            } else {
                                R.string.write_failed
                            },
                        ),
                )
            }
        }
        item(key = "my-share") {
            MyShareBand(payout = myPayout, identityKnown = identityKnown)
        }
        item(key = "missions-title") {
            KrtSectionTitle(
                text = stringResource(R.string.operation_detail_missions_title),
                modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
            )
        }
        if (overview.rollup.missions.isEmpty()) {
            item(key = "missions-empty") {
                EmptyLine(text = stringResource(R.string.operation_detail_missions_empty))
            }
        } else {
            items(overview.rollup.missions, key = { it.missionId ?: it.missionName }) { result ->
                MissionResultRow(result = result, onOpenMission = onOpenMission)
            }
        }
        if (overview.rollup.truncated) {
            item(key = "missions-truncated") {
                Text(
                    text = stringResource(R.string.operation_detail_missions_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Warning,
                    modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
                )
            }
        }
        item(key = "rollup-title") {
            KrtSectionTitle(
                text = stringResource(R.string.operation_detail_rollup),
                modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
            )
        }
        item(key = "rollup") {
            RollupBlock(overview = overview)
        }
        item(key = "payouts-title") {
            KrtSectionTitle(
                text = stringResource(R.string.operation_detail_payouts_title),
                modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
            )
        }
        if (overview.payouts.rows.isEmpty()) {
            item(key = "payouts-empty") {
                EmptyLine(text = stringResource(R.string.operation_detail_payouts_empty))
            }
        } else {
            items(overview.payouts.rows, key = { it.participantId ?: it.participantName }) { row ->
                PayoutRow(row = row, state = state, onTogglePaidOut = onTogglePaidOut)
            }
        }
    }
}

/**
 * "Dein Anteil" — the amount the caller actually receives, or a sentence when there is nothing to
 * show.
 *
 * A donating member's amount is zero and the label says where the share went.
 *
 * @param payout the caller's row, or `null`.
 * @param identityKnown whether the caller's id could be read.
 */
@Composable
private fun MyShareBand(
    payout: OperationPayout?,
    identityKnown: Boolean,
) {
    Box(modifier = Modifier.padding(KrtSpacing.s12)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier =
                    Modifier
                        .width(SHARE_RAIL)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
            )
            KrtHudBox(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
                    ) {
                        Text(
                            text = stringResource(R.string.operation_detail_my_share).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = KrtPalette.White,
                        )
                        MyShareSubline(payout = payout, identityKnown = identityKnown)
                    }
                    if (payout != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatAmount(payout.payout.orEmpty()),
                                style = MaterialTheme.typography.headlineSmall,
                                color = KrtPalette.White,
                            )
                            Text(
                                text = stringResource(R.string.bank_total_unit),
                                style = MaterialTheme.typography.labelMedium,
                                color = KrtPalette.TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The line under "DEIN ANTEIL": the state of the member's own payout.
 *
 * Only a known identity with no matching row may claim the member took no part.
 *
 * @param payout the member's own row, if it could be found.
 * @param identityKnown whether the caller's own id was read at all.
 */
@Composable
private fun MyShareSubline(
    payout: OperationPayout?,
    identityKnown: Boolean,
) {
    val text =
        when {
            payout == null && identityKnown -> stringResource(R.string.operation_detail_my_share_unknown)
            payout == null -> stringResource(R.string.operation_detail_my_share_pending)
            payout.donating -> stringResource(R.string.operation_detail_my_share_donated)
            payout.paidOut -> stringResource(R.string.operation_detail_my_share_paid)
            else -> stringResource(R.string.operation_detail_my_share_open)
        }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
}

/**
 * The Finanz-Rollup: net, donations and the per-head share, without an income/expense split.
 *
 * @param overview everything loaded.
 */
@Composable
private fun RollupBlock(overview: OperationOverview) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12)) {
        KrtKeyValueRow(
            label = stringResource(R.string.operation_detail_rollup_net),
            value = formatAmount(overview.rollup.total.orEmpty()),
        )
        KrtKeyValueRow(
            label = stringResource(R.string.operation_detail_rollup_donations),
            value = formatAmount(overview.payouts.totalDonations.orEmpty()),
        )
        overview.payouts.shareRange?.let { (lowest, highest) ->
            KrtKeyValueRow(
                label =
                    pluralStringResource(
                        R.plurals.operation_detail_rollup_share,
                        overview.payouts.participants,
                        overview.payouts.participants,
                    ),
                value =
                    if (lowest == highest) {
                        formatAmount(lowest)
                    } else {
                        stringResource(
                            R.string.operation_detail_rollup_share_range,
                            formatAmount(lowest),
                            formatAmount(highest),
                        )
                    },
            )
        }
    }
}

/**
 * One Einsatz's contribution.
 *
 * @param result the per-Einsatz result.
 * @param onOpenMission opens it, when the server named an id.
 */
@Composable
private fun MissionResultRow(
    result: OperationMissionResult,
    onOpenMission: (String) -> Unit,
) {
    val id = result.missionId
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (id != null) Modifier.clickable { onOpenMission(id) } else Modifier)
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = result.missionName,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = signedAmount(result.total),
            style = MaterialTheme.typography.bodyMedium,
            color = amountTone(result.total),
        )
        if (id != null) {
            KrtIcon(
                id = DesignR.drawable.ic_krt_chevron_right,
                contentDescription = null,
                size = CHEVRON_SIZE,
                tint = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * An amount with its sign in front of it.
 *
 * @param raw the amount as the server rendered it.
 * @return the formatted amount, prefixed with `+` when it is positive.
 */
@Composable
private fun signedAmount(raw: String): String {
    val formatted = formatAmount(raw)
    val positive = raw.trim().toBigDecimalOrNull()?.signum() == 1
    return if (positive) stringResource(R.string.operation_detail_amount_positive, formatted) else formatted
}

/**
 * The colour an operation result is stated in.
 *
 * @param raw the amount as the server rendered it.
 * @return green for a gain, red for a loss, plain white when it is zero or unparseable - a
 *   tint on a number nobody could read would be an assertion the app cannot back.
 */
private fun amountTone(raw: String): androidx.compose.ui.graphics.Color =
    when (raw.trim().toBigDecimalOrNull()?.signum()) {
        1 -> KrtPalette.SuccessText
        -1 -> KrtPalette.DangerText
        else -> KrtPalette.White
    }

/**
 * One participant's payout row.
 *
 * The confirmation is offered to a mission manager in both directions; a refused rescind is named
 * when it happens. A row without a participant key is shown without the action.
 *
 * @param row the payout.
 * @param state the screen, for the grant and whether a write may be sent.
 * @param onTogglePaidOut the confirmation was taken.
 */
@Composable
private fun PayoutRow(
    row: OperationPayout,
    state: OperationDetailState,
    onTogglePaidOut: (OperationPayout) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        ) {
            Text(
                text = participantLabel(row.participantName),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
            Text(
                text =
                    stringResource(
                        R.string.operation_detail_payout_line,
                        formatAmount(row.earnedShare.orEmpty()) + row.percentSuffix(),
                        stringResource(
                            if (row.donating) {
                                R.string.operation_detail_payout_route_org
                            } else {
                                R.string.operation_detail_payout_route_member
                            },
                        ),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            PayoutComposition(row = row)
        }
        KrtChip(text = row.payoutLabel(), tone = row.payoutTone())
        if (state.missionManager && row.participantId != null) {
            PayoutCheckbox(row = row, state = state, onTogglePaidOut = onTogglePaidOut)
        }
    }
}

/**
 * „ (12,5 %)" after the share, when the server sent the proportion.
 *
 * @return the suffix, or an empty string.
 */
@Composable
private fun OperationPayout.percentSuffix(): String {
    val pct = participationPercentage ?: return ""
    return " (" + stringResource(R.string.operation_detail_payout_percent, formatPercent(pct)) + ")"
}

/**
 * Renders a participation percentage without a trailing `,0`.
 *
 * @param value the percentage as sent.
 * @return the rendered number, decimal comma, at most one decimal place.
 */
private fun formatPercent(value: Double): String {
    val rounded = kotlin.math.round(value * PERCENT_ROUNDING) / PERCENT_ROUNDING
    val text =
        if (rounded == kotlin.math.floor(rounded)) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    return text.replace('.', ',')
}

/** One decimal place, which is the precision the web app shows a participation share at. */
private const val PERCENT_ROUNDING = 10.0

/**
 * What the transferred figure is made of — participation percentage, reimbursed outlay, deducted
 * in-game fee — and who closed it.
 *
 * Each optional part is rendered only when present.
 *
 * @param row the payout row.
 */
@Composable
private fun PayoutComposition(row: OperationPayout) {
    val parts =
        buildList {
            row.personalExpenses
                ?.takeIf { it.isNotBlank() && it.toBigDecimalOrNull()?.signum() != 0 }
                ?.let { add(stringResource(R.string.operation_detail_payout_expenses, formatAmount(it))) }
            row.transferFee
                ?.takeIf { it.isNotBlank() && it.toBigDecimalOrNull()?.signum() != 0 }
                ?.let { add(stringResource(R.string.operation_detail_payout_fee, formatAmount(it))) }
        }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(PAYOUT_PART_SEPARATOR),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
    val paidLine = row.paidOutLine()
    if (paidLine != null) {
        Text(
            text = paidLine,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * „Ausgezahlt am … von …", once a manager has closed the row; the name is never logged.
 *
 * @return the line, or `null` while the payout is still open or the server named no time.
 */
@Composable
private fun OperationPayout.paidOutLine(): String? {
    val stamp =
        paidOutAt
            ?.takeIf { paidOut }
            ?.toKrtDateTime()
            ?.takeIf { (date, _) -> date.isNotEmpty() }
            ?.let { (date, time) -> "$date $time" }
    val by = paidOutByName?.takeIf { it.isNotBlank() }
    return when {
        stamp == null -> null
        by != null -> stringResource(R.string.operation_detail_payout_paid_by, stamp, by)
        else -> stringResource(R.string.operation_detail_payout_paid_at, stamp)
    }
}

/** Between two parts of the payout composition, the design's separator between two facts. */
private const val PAYOUT_PART_SEPARATOR = " · "

/**
 * The manager's confirm box on a payout row: marking is one tap, unmarking goes through a modal
 * that names what it undoes.
 *
 * @param row the payout row.
 * @param state the screen, for the write gate.
 * @param onTogglePaidOut invoked once the direction is settled.
 */
@Composable
private fun PayoutCheckbox(
    row: OperationPayout,
    state: OperationDetailState,
    onTogglePaidOut: (OperationPayout) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val writable = state.online && !state.saving
    Box(
        modifier =
            Modifier
                .size(KrtSpacing.touchTarget)
                .testTag(OPERATION_PAID_OUT_TAG)
                .alpha(if (writable) 1f else DISABLED_WRITE_ALPHA)
                .toggleable(
                    value = row.paidOut,
                    enabled = writable,
                    role = Role.Checkbox,
                    onValueChange = { if (row.paidOut) confirming = true else onTogglePaidOut(row) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PAYOUT_BOX)
                    .background(if (row.paidOut) MaterialTheme.colorScheme.primary else KrtPalette.SurfaceInput)
                    .border(
                        KrtSpacing.hairline,
                        if (row.paidOut) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (row.paidOut) {
                KrtIcon(
                    id = DesignR.drawable.ic_krt_check,
                    contentDescription = stringResource(R.string.operation_detail_payout_undo),
                    size = PAYOUT_CHECK,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
    if (confirming) {
        KrtModal(
            title = stringResource(R.string.operation_detail_payout_undo_title),
            confirmText = stringResource(R.string.operation_detail_payout_undo),
            onConfirm = {
                confirming = false
                onTogglePaidOut(row)
            },
            onDismiss = { confirming = false },
            tone = KrtModalTone.Danger,
            cancelText = stringResource(R.string.personal_inventory_cancel),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.operation_detail_payout_undo_body,
                        participantLabel(row.participantName),
                        row.payout.orEmpty(),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
        }
    }
}

/**
 * The chip text for a payout row.
 *
 * @return "Verzicht" for a donating member — which is the fact that explains the amount — else
 *   whether it has been paid.
 */
@Composable
private fun OperationPayout.payoutLabel(): String =
    when {
        donating -> stringResource(R.string.operation_detail_payout_donated)
        paidOut -> stringResource(R.string.operation_detail_payout_paid)
        else -> stringResource(R.string.operation_detail_payout_open)
    }

/**
 * The chip tone for a payout row.
 *
 * @return success once paid, muted for a donation, neutral while open.
 */
private fun OperationPayout.payoutTone(): KrtChipTone =
    when {
        donating -> KrtChipTone.Muted
        paidOut -> KrtChipTone.Success
        else -> KrtChipTone.Info
    }

/**
 * A muted line standing in for an empty section.
 *
 * @param text what to say.
 */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
    )
}

/**
 * The badge text for this Operation.
 *
 * @return the translated status, or the raw server value when this build does not know it.
 */
@Composable
private fun OperationDetail.statusLabel(): String =
    if (status == OperationStatus.UNKNOWN) {
        rawStatus.orEmpty()
    } else {
        stringResource(status.labelRes())
    }

/**
 * The whole-screen failure, worded differently for a refusal and an outage.
 *
 * @param error what went wrong.
 */
@Composable
private fun OperationDetailFailure(error: ApiError) {
    val (titleRes, messageRes) =
        when (error) {
            is ApiError.Forbidden -> {
                R.string.operation_detail_error_forbidden_title to
                    R.string.operation_detail_error_forbidden_message
            }

            is ApiError.NotFound -> {
                R.string.operation_detail_error_missing_title to
                    R.string.operation_detail_error_missing_message
            }

            else -> {
                R.string.operation_detail_error_title to R.string.operation_detail_error_message
            }
        }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_clipboard_check,
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
    )
}

/**
 * The Operation detail, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param onOpenMission an Einsatz row was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun OperationDetailRoute(
    viewModel: OperationDetailViewModel,
    onOpenMission: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OperationDetailScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onOpenMission = onOpenMission,
        onTogglePaidOut = viewModel::onTogglePaidOut,
        modifier = modifier,
        onEdit = onEdit,
    )
}

/** Width of the orange rail beside "Dein Anteil". */
private val SHARE_RAIL = 4.dp

/** Edge length of the payout confirm box. */
private val PAYOUT_BOX = 24.dp

/** Size of the check inside it. */
private val PAYOUT_CHECK = 16.dp

/** Size of the chevron on a result row. */
private val CHEVRON_SIZE = 16.dp

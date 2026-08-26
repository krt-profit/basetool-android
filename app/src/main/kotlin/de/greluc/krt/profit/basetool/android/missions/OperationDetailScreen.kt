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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the scrolling content of the Operation detail. */
const val OPERATION_DETAIL_CONTENT_TAG: String = "operation-detail-content"

/** Test handle for a payout row's confirmation. */
const val OPERATION_PAID_OUT_TAG: String = "operation-paid-out"

/**
 * One Operation in full (design spec ch. 06 §5), read-only.
 *
 * **One scrolling page, not tabs.** The Einsatz detail has seven tabs because it carries seven
 * unrelated collections; an Operation carries three short sections that a member reads together —
 * what it earned, which Einsätze earned it, and who gets what. Tabs would hide two thirds of a
 * screenful behind a control.
 *
 * The manager payout toggles of the design mock are mutations and belong to Phase 3. This screen
 * shows the payout **state** and no action.
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
) {
    val overview = state.overview
    // Bound so the smart cast survives the branch; `state.phase` is a property read.
    val phase = state.phase
    Column(modifier = modifier.fillMaxSize()) {
        when {
            overview != null -> {
                OperationDetailHead(detail = overview.detail, overview = overview)
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
                // A busy server gets the countdown of chapter 14; anything else gets the ordinary
                // failure state, because a countdown in front of a 403 promises a retry that will
                // answer exactly the same.
                val retryIn = state.retryIn
                if (retryIn != null) {
                    KrtRetryCountdown(
                        secondsLeft = retryIn,
                        title = stringResource(R.string.retry_busy_title),
                        message = stringResource(R.string.retry_busy_message, retryIn),
                        retryLabel = stringResource(R.string.retry_now),
                        onRetry = onRetryNow,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
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
 */
@Composable
private fun OperationDetailHead(
    detail: OperationDetail,
    overview: OperationOverview,
) {
    // Artboard 06.5 puts the Operation's own name in the TOP BAR with its status and counts under
    // it, the way every other detail in this app now reads. The bar used to say "OPERATION" - the
    // category, which the member picked two taps ago and already knows.
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
    ProvideScreenTopBar(
        title = detail.name,
        subtitle = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
    // Shown only when the server said so. `null` means the flag was not computed, and a warning
    // invented from an absent field would put a caveat on a figure that may well be final.
    if (detail.payoutPreliminary == true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(OPERATION_DETAIL_CONTENT_TAG)) {
        if (!state.online) {
            item(key = "offline") { OfflineBand() }
        }
        state.error?.let { error ->
            item(key = "write-error") {
                KrtFieldError(
                    text =
                        stringResource(
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
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
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
        // ADR-0104 in the main repo: a capped list says so. The net figure above is computed over
        // every Einsatz regardless, so the note has to draw that distinction rather than imply the
        // total is short too.
        if (overview.rollup.truncated) {
            item(key = "missions-truncated") {
                Text(
                    text = stringResource(R.string.operation_detail_missions_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Warning,
                    modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                )
            }
        }
        // The rollup follows the Einsätze it is a rollup OF (artboard 06.5). Above them it was a
        // total with nothing yet to total.
        item(key = "rollup-title") {
            KrtSectionTitle(
                text = stringResource(R.string.operation_detail_rollup),
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
            )
        }
        item(key = "rollup") {
            RollupBlock(overview = overview)
        }
        item(key = "payouts-title") {
            KrtSectionTitle(
                text = stringResource(R.string.operation_detail_payouts_title),
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
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
 * "Dein Anteil", or an honest sentence when there is nothing to point at.
 *
 * A donating member's share is zero by construction — it went to the org treasury — so the amount
 * shown is what they actually receive, and the label says where the rest went. Showing the share
 * would read as money they are owed.
 *
 * @param payout the caller's row, or `null`.
 * @param identityKnown whether the caller's id could be read.
 */
@Composable
private fun MyShareBand(
    payout: OperationPayout?,
    identityKnown: Boolean,
) {
    // The band the artboard leads with: a HUD box behind an orange rail, saying in one line what
    // the member is owed and whether it has been paid. It was a muted caption and a plain number,
    // which on a screen of other people's money did not read as the member's own row.
    Box(modifier = Modifier.padding(KrtSpacing.md)) {
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
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
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
 * The line under "DEIN ANTEIL" - what state the member's own payout is in.
 *
 * Split out because the three cases it distinguishes are not the same kind of statement: two are
 * facts about the payout, and the third is an admission that the identity read failed. Only the
 * identity case may claim the member took no part; a failed request saying so would be a statement
 * about them made out of an outage.
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
 * The Finanz-Rollup: net, donations and the per-head share.
 *
 * **No income/expense split.** The design mock shows one; the server's roll-up has no such field,
 * and deriving it would mean fetching every entry of every Einsatz and adding money up on the
 * device. The web page shows the same net-plus-donations pair.
 *
 * @param overview everything loaded.
 */
@Composable
private fun RollupBlock(overview: OperationOverview) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md)) {
        KrtKeyValueRow(
            label = stringResource(R.string.operation_detail_rollup_net),
            value = formatAmount(overview.rollup.total.orEmpty()),
        )
        KrtKeyValueRow(
            label = stringResource(R.string.operation_detail_rollup_donations),
            value = formatAmount(overview.payouts.totalDonations.orEmpty()),
        )
        // The server's own per-row figures, not the net divided by the head count: the split is
        // weighted by how long each member actually took part. When the rows disagree the two ends
        // are named, because one number would be contradicted by the payout list below it.
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
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = result.missionName,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            modifier = Modifier.weight(1f),
        )
        // Signed and tinted, as the artboard has it: on a list of results the direction is the
        // fact, and a column of unsigned figures makes a losing Einsatz look like a winning one.
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
 * The server sends a plain decimal; a positive result carries no `+`, and on a list where the next
 * row may be negative that absence is easy to read as "no change" rather than "gain".
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
 * The confirmation is offered to a mission manager alone. Whether they may take one **back** needs
 * an officer or an admin on top, which `/users/me` does not answer — so both directions are
 * offered and a refusal on the second is named rather than predicted.
 *
 * A row the server sent without a participant key cannot be addressed and is shown without the
 * action rather than with one that would 400.
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        ) {
            Text(
                text = row.participantName,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
            // "Anteil 4.150 · Auszahlung" (artboard 06.5): the row's own amount and where it goes.
            // A bare name beside a chip left the figure to be inferred from the column heading.
            Text(
                text =
                    stringResource(
                        R.string.operation_detail_payout_line,
                        formatAmount(row.earnedShare.orEmpty()),
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
        }
        KrtChip(text = row.payoutLabel(), tone = row.payoutTone())
        if (state.missionManager && row.participantId != null) {
            PayoutCheckbox(row = row, state = state, onTogglePaidOut = onTogglePaidOut)
        }
    }
}

/**
 * The manager's confirm box on a payout row.
 *
 * Asymmetric on purpose, and the design chapter is explicit about it: marking a payout is one tap,
 * taking the mark back goes through a modal that names what it undoes. The two directions are not
 * equally recoverable - the member has been told they were paid.
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
                text = stringResource(R.string.operation_detail_payout_undo_body, row.participantName),
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
 * @return success once paid, muted for a donation, neutral while open. "Open" is not a problem and
 *   must not be drawn as one.
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
        modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
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
 * The whole-screen failure, worded by cause.
 *
 * A refusal and an outage are different facts: one says the Operation is not the member's to see,
 * the other says the app could not ask. One message for both would leave a member retrying
 * something that will never succeed.
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
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OperationDetailScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onOpenMission = onOpenMission,
        onTogglePaidOut = viewModel::onTogglePaidOut,
        modifier = modifier,
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

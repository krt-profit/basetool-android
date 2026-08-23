/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import java.time.Instant
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Konten list. */
const val BANK_ACCOUNTS_TAG: String = "bank-accounts"

/** Test handle for one account's screen. */
const val BANK_ACCOUNT_TAG: String = "bank-account"

/** Height of the drawn balance line. */
private val SPARKLINE_HEIGHT = 32.dp

/** Stroke width of the drawn balance line, in device pixels. */
private const val SPARKLINE_STROKE = 3f

/**
 * The Konten list (design spec ch. 12 §1), read-only.
 *
 * **The Anträge tab is absent.** Approving and rejecting a booking request are mutations behind a
 * staged approval ladder (Phase 3), and a tab that only listed them while the actions lived
 * elsewhere would invite a member to try.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onOpenAccount an account card was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountsScreen(
    state: BankAccountsState,
    onRefresh: () -> Unit,
    onOpenAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        is BankPhase.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.bank_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is BankPhase.Failed -> {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_bank,
                title = stringResource(R.string.bank_error_title),
                message = stringResource(R.string.bank_error_message),
                actionText = stringResource(R.string.missions_retry),
                onAction = onRefresh,
                modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
            )
        }

        is BankPhase.Ready -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                if (state.accounts.isEmpty()) {
                    KrtRefreshableFill {
                        KrtEmptyState(
                            iconRes = DesignR.drawable.ic_krt_bank,
                            title = stringResource(R.string.bank_accounts_empty_title),
                            message = stringResource(R.string.bank_accounts_empty_message),
                            modifier = Modifier.padding(KrtSpacing.lg),
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag(BANK_ACCOUNTS_TAG)) {
                        items(state.accounts, key = { it.id }) { account ->
                            AccountCard(account = account, onClick = { onOpenAccount(account.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One account card.
 *
 * @param account the account.
 * @param onClick opens it.
 */
@Composable
private fun AccountCard(
    account: BankAccountSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatAmount(account.balance.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
            )
        }
        account.delta30d?.let { delta ->
            Text(
                text = stringResource(R.string.bank_delta_30d, formatAmount(delta)),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        Sparkline(points = account.sparkline)
        KrtHairlineRule()
    }
}

/**
 * The balance line, drawn rather than charted.
 *
 * The design says so explicitly (REQ-BANK-016 in the main repo): the server sends the points and
 * the client draws a polyline. No chart framework enters this app for one line on a card — it would
 * be a dependency, a theme to fight and, under this repo's privacy gate, a decision.
 *
 * A line needs two points; fewer draws nothing rather than a dot pretending to be a trend. A flat
 * series draws a straight line through the middle instead of dividing by a zero span.
 *
 * @param points the balance points, oldest first.
 */
@Composable
private fun Sparkline(points: List<Double>) {
    if (points.size < MIN_POINTS) {
        return
    }
    val description = stringResource(R.string.bank_sparkline_description)
    val line = MaterialTheme.colorScheme.primary
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SPARKLINE_HEIGHT)
                .semantics { contentDescription = description },
    ) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0.0 }
        val stepX = size.width / (points.size - 1)
        val offsets =
            points.mapIndexed { index, value ->
                val ratio = span?.let { (value - min) / it } ?: HALF
                Offset(index * stepX, (size.height * (1.0 - ratio)).toFloat())
            }
        offsets.zipWithNext { from, to ->
            drawLine(color = line, start = from, end = to, strokeWidth = SPARKLINE_STROKE, cap = StrokeCap.Round)
        }
    }
}

/** Fewer points than this is not a line. */
private const val MIN_POINTS = 2

/** Where a flat series is drawn. */
private const val HALF = 0.5

/**
 * One account with its ledger (design spec ch. 12 §2), read-only.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onLoadMore the "Ältere laden" control was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountScreen(
    state: BankAccountState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.account
    val phase = state.phase
    when {
        account != null -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag(BANK_ACCOUNT_TAG)) {
                    item(key = "head") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                        ) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = KrtPalette.White,
                            )
                            Text(
                                text = stringResource(R.string.bank_balance),
                                style = MaterialTheme.typography.labelMedium,
                                color = KrtPalette.TextMuted,
                            )
                            Text(
                                text = formatAmount(account.balance.orEmpty()),
                                style = MaterialTheme.typography.titleLarge,
                                color = KrtPalette.White,
                            )
                            account.delta30d?.let { delta ->
                                Text(
                                    text = stringResource(R.string.bank_delta_30d, formatAmount(delta)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KrtPalette.TextMuted,
                                )
                            }
                        }
                    }
                    item(key = "ledger-title") {
                        KrtSectionTitle(
                            text = stringResource(R.string.bank_transactions),
                            modifier =
                                Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                        )
                    }
                    if (state.bookings.isEmpty()) {
                        item(key = "ledger-empty") {
                            Text(
                                text = stringResource(R.string.bank_transactions_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = KrtPalette.TextMuted,
                                modifier =
                                    Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                            )
                        }
                    } else {
                        items(state.bookings, key = { it.id }) { booking ->
                            BookingRow(booking = booking)
                        }
                        item(key = "ledger-footer") {
                            if (state.hasMore) {
                                KrtLoadMore(
                                    text =
                                        pluralStringResource(
                                            R.plurals.bank_transactions_count,
                                            state.bookingTotal.toInt(),
                                            state.bookings.size,
                                            state.bookingTotal,
                                        ),
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore,
                                    modifier = Modifier.padding(KrtSpacing.md),
                                )
                            } else {
                                KrtEndOfList(
                                    text = stringResource(R.string.bank_transactions_end),
                                    modifier = Modifier.padding(KrtSpacing.md),
                                )
                            }
                        }
                    }
                }
            }
        }

        phase is BankPhase.Failed -> {
            BankAccountFailure(error = phase.error, modifier = modifier)
        }

        else -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.bank_title),
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * One ledger line.
 *
 * The amount takes its sign from the booking **kind**, never from the digits: the ledger stores
 * every amount as a positive magnitude, so reading a sign off the number would show every
 * withdrawal as a deposit. A kind this build does not know renders without a sign and in the
 * neutral colour rather than guessing.
 *
 * @param booking the line.
 */
@Composable
private fun BookingRow(booking: BankBooking) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = booking.note ?: booking.typeLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = booking.subline(),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = booking.signedAmount(),
            style = MaterialTheme.typography.bodyMedium,
            color = booking.amountColor(),
        )
    }
}

/**
 * The booking's amount with the sign its kind gives it.
 *
 * @return e.g. `+12.400`, `−3.200`, or the bare figure for a kind this build does not classify.
 */
private fun BankBooking.signedAmount(): String {
    val figure = formatAmount(amount.orEmpty())
    if (figure.isEmpty()) {
        return ""
    }
    return when (incoming) {
        true -> "+$figure"
        false -> "−$figure"
        null -> figure
    }
}

/**
 * The colour the amount is drawn in.
 *
 * @return green for money in, red for money out, plain white for a kind this build does not know —
 *   colouring an unclassified line would state a direction nobody checked.
 */
@Composable
private fun BankBooking.amountColor(): Color =
    when (incoming) {
        // The *Text tints, not the fills: Success/Danger are container colours and fail contrast
        // as text on the dark ground. The design calls these "Text-Tints" for that reason.
        true -> KrtPalette.SuccessText

        false -> KrtPalette.DangerText

        null -> KrtPalette.White
    }

/**
 * The line's second row: who moved it and when.
 *
 * @return the holder and the relative time, whichever of them the server sent.
 */
@Composable
private fun BankBooking.subline(): String {
    LocalConfiguration.current
    val time = createdAt?.relativeToNow()
    return listOfNotNull(holder?.takeIf { it.isNotBlank() }, time).joinToString(" · ")
}

/**
 * How long ago an instant is, in the platform's words.
 *
 * @return the localised relative span.
 */
private fun Instant.relativeToNow(): String =
    DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

/**
 * The translated name of a booking kind.
 *
 * @return the wording, or the raw server value for a kind this build has never seen — an
 *   untranslated word beats an empty line.
 */
@Composable
private fun BankBooking.typeLabel(): String =
    when (type) {
        "DEPOSIT" -> stringResource(R.string.bank_type_deposit)
        "WITHDRAWAL" -> stringResource(R.string.bank_type_withdrawal)
        "TRANSFER" -> stringResource(R.string.bank_type_transfer)
        "HOLDER_TRANSFER" -> stringResource(R.string.bank_type_holder_transfer)
        "REVERSAL" -> stringResource(R.string.bank_type_reversal)
        "WIPE_RESET" -> stringResource(R.string.bank_type_wipe_reset)
        else -> type
    }

/**
 * The whole-screen failure, worded by cause.
 *
 * @param error what went wrong.
 * @param modifier layout modifier.
 */
@Composable
private fun BankAccountFailure(
    error: ApiError,
    modifier: Modifier = Modifier,
) {
    val (titleRes, messageRes) =
        when (error) {
            is ApiError.Forbidden -> {
                R.string.bank_account_error_forbidden_title to
                    R.string.bank_account_error_forbidden_message
            }

            is ApiError.NotFound -> {
                R.string.bank_account_error_missing_title to
                    R.string.bank_account_error_missing_message
            }

            else -> {
                R.string.bank_account_error_title to R.string.bank_account_error_message
            }
        }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_bank,
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
    )
}

/**
 * The Konten list, bound to its view model.
 *
 * @param viewModel drives the list.
 * @param onOpenAccount a card was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun BankAccountsRoute(
    viewModel: BankViewModel,
    onOpenAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BankAccountsScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onOpenAccount = onOpenAccount,
        modifier = modifier,
    )
}

/**
 * One account, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun BankAccountRoute(
    viewModel: BankAccountViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BankAccountScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        modifier = modifier,
    )
}

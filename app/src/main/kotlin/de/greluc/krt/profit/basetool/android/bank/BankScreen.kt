/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.FileHandoff
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.common.formatSignedAmount
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSettings
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
import de.greluc.krt.profit.basetool.android.core.data.BankLimitTarget
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKpiCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTabs
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTotalTile
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.LocalCaller
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Konten list. */
const val BANK_ACCOUNTS_TAG: String = "bank-accounts"

/** Test handle for the account-settings action. */
const val BANK_SETTINGS_TAG: String = "bank-settings"

/** Test handle for the settings sheet. */
const val BANK_SETTINGS_SHEET_TAG: String = "bank-settings-sheet"

/** Test handle for the target's save action. */
const val BANK_TARGET_SAVE_TAG: String = "bank-target-save"

/** Test handle for the all-members switch. */
const val BANK_ALL_MEMBERS_TAG: String = "bank-all-members"

/** Test handle for one role-bucket chip. */
const val BANK_ROLE_TAG: String = "bank-role"

/** Test handle for one account's screen. */
const val BANK_ACCOUNT_TAG: String = "bank-account"

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
    onRetryNow: () -> Unit,
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
            // A busy server gets the countdown of chapter 14; anything else gets the ordinary
            // empty state, because a countdown in front of a 403 promises a retry that will
            // answer exactly the same.
            val retryIn = state.retryIn
            if (retryIn != null) {
                KrtRetryCountdown(
                    secondsLeft = retryIn,
                    title = stringResource(R.string.retry_busy_title),
                    message = stringResource(R.string.retry_busy_message, retryIn),
                    retryLabel = stringResource(R.string.retry_now),
                    onRetry = onRetryNow,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
                return
            }
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(BANK_ACCOUNTS_TAG),
                        contentPadding = PaddingValues(KrtSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    ) {
                        item(key = "total") {
                            TotalCard(accounts = state.accounts)
                        }
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
 * The colour a 30-day change is stated in.
 *
 * Green up, red down, muted when it did not move — the direction is the fact, and a member reading
 * a column of deltas should not have to parse a sign to see it. Both tints are the -text variants,
 * which are the ones that hold contrast on black.
 *
 * @param delta the change as the server sent it.
 * @return the tint for that reading.
 */
internal fun deltaTone(delta: String): androidx.compose.ui.graphics.Color {
    val value = delta.trim().toBigDecimalOrNull() ?: return KrtPalette.TextMuted
    return when {
        value.signum() > 0 -> KrtPalette.SuccessText
        value.signum() < 0 -> KrtPalette.DangerText
        else -> KrtPalette.TextMuted
    }
}

/**
 * What the visible accounts add up to.
 *
 * Design ch. 12 artboard 1 leads the list with it, and the reason is scope: a member with a
 * view-grant on three accounts is being told how much the org holds *that they can see*, which is
 * not the same as what the org holds. Summing the rows on screen keeps the two identical by
 * construction — a server-side grand total would silently include accounts the caller is not shown.
 *
 * Accounts whose balance the server withheld are skipped rather than counted as zero: a redacted
 * balance is unknown, and unknown is not nothing.
 *
 * @param accounts the accounts on screen.
 */
@Composable
private fun TotalCard(accounts: List<BankAccountSummary>) {
    val total = accounts.mapNotNull { it.balance?.trim()?.toBigDecimalOrNull() }
    if (total.isEmpty()) {
        return
    }
    val sum = total.reduce { a, b -> a + b }
    // `KrtTotalTile` IS this tile — the orange leading bar that marks a figure as the sum of the
    // screen, the muted label, the bright value. It was hand-built here before anyone noticed the
    // component existed, and the copy lost two things the original has: the label uppercased, and
    // the value in tabular figures so the digits hold their column while the total changes.
    KrtTotalTile(
        label = stringResource(R.string.bank_total),
        value = formatAmount(sum.toPlainString()),
        unit = stringResource(R.string.bank_total_unit),
        modifier = Modifier.fillMaxWidth(),
    )
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
    // KrtKpiCard *is* this card: design ch. 12 draws the account as a `kpi-card` — name above, the
    // balance large beneath it, and the 30-day delta beside a sparkline on one row. It was built
    // here as a bare Column with a hairline underneath, which loses the border, puts the balance on
    // the name's line and leaves the delta grey when its sign is the point of it.
    KrtKpiCard(
        title = account.name,
        value = formatAmount(account.balance.orEmpty()),
        modifier = Modifier.fillMaxWidth(),
        delta =
            account.delta30d?.let {
                stringResource(
                    R.string.bank_delta_30d,
                    formatSignedAmount(it.trimStart('+', '-', MINUS_CHAR), it.isPositiveDelta()),
                )
            },
        deltaPositive = account.delta30d.isPositiveDelta(),
        sparkline = account.sparkline.takeIf { it.isNotEmpty() }?.map(Double::toFloat),
        sparklineDescription = stringResource(R.string.bank_sparkline_description),
        onClick = onClick,
    )
}

/** The typographic minus the shared formatter emits, which a raw server value may carry. */
private const val MINUS_CHAR = '\u2212'

/**
 * Whether a formatted delta reads as an increase.
 *
 * The server sends it already formatted and already signed, so the sign is read off the string
 * rather than re-derived — which also keeps the minus sign the server chose, typographic or not.
 *
 * @return `false` only for an explicitly negative figure; an absent or unsigned one is not drawn as
 *   a loss.
 */
internal fun String?.isPositiveDelta(): Boolean {
    val first = this?.trimStart()?.firstOrNull() ?: return true
    return first !in MINUS_SIGNS
}

/**
 * One account with its ledger (design spec ch. 12 §2), read-only.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onLoadMore the "Ältere laden" control was tapped.
 * @param modifier layout modifier.
 * @param onReverse a ledger row's Storno was asked for.
 * @param onStatement the account statement was asked for.
 * @param onThreeMonthReport the quarter report was asked for.
 * @param onReportHandled a fetched report has been handed on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountScreen(
    state: BankAccountState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    actions: BankSettingsActions,
    modifier: Modifier = Modifier,
    onReverse: (BankBooking) -> Unit = {},
    onStatement: () -> Unit = {},
    onThreeMonthReport: () -> Unit = {},
    onReportHandled: () -> Unit = {},
    limitActions: BankLimitActions? = null,
) {
    // The Storno is a BANK_EMPLOYEE act, and the server decides that — the app only draws what
    // `/me/capabilities` answered.
    val staff = LocalCaller.current?.bankEmployee == true

    // Which originals already carry a counter-booking. The wire says only which transaction a
    // Storno negates, so this is read off the rows themselves — right for everything on screen, and
    // an older page's reversal simply leaves the action offered until the server refuses it, which
    // it then says plainly.
    val reversedIds = state.bookings.mapNotNull { it.reversesTransactionId }.toSet()

    // The file is handed on the moment it arrives, then cleared: a report kept in state is a report
    // re-offered on the next recomposition.
    val context = LocalContext.current
    LaunchedEffect(state.report) {
        state.report?.let { file ->
            FileHandoff.shareIntent(context, file)?.let { context.startActivity(it) }
            onReportHandled()
        }
    }
    val account = state.account
    val phase = state.phase
    AccountSettingsOverlay(
        state = state,
        actions = actions,
        onRefresh = onRefresh,
        limitActions = limitActions,
    )
    when {
        account != null -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(BANK_ACCOUNT_TAG),
                    contentPadding = PaddingValues(horizontal = contentGutter()),
                ) {
                    if (!state.online) {
                        item(key = "offline") { OfflineBand() }
                    }
                    item(key = "head") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                        ) {
                            // The account's name and its org sit in the TOP BAR (design ch. 12
                            // artboard 2), the rule every detail in this app now follows.
                            // The artboard also puts the owning unit under the name; the detail
                            // DTO does not carry it (BankAccountDetail has no orgUnitName, only
                            // the summary does), so it is left off rather than guessed from the
                            // list the member may not have come through.
                            ProvideScreenTopBar(title = account.name)
                            // The balance is a HUD box with its sparkline, not three stacked
                            // Texts: the artboard gives the one number a member came for the
                            // heaviest treatment on the screen, and puts the 30-day shape under it
                            // so "is this going up" is answered without reading the ledger.
                            KrtHudBox(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.bank_balance).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KrtPalette.TextMuted,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Text(
                                        text = formatAmount(account.balance.orEmpty()),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = KrtPalette.White,
                                    )
                                    Text(
                                        text = stringResource(R.string.bank_total_unit),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = KrtPalette.TextMuted,
                                        modifier = Modifier.padding(bottom = KrtSpacing.xs),
                                    )
                                }
                                account.delta30d?.let { delta ->
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.bank_delta_30d,
                                                formatSignedAmount(
                                                    delta.trimStart('+', '-', MINUS_CHAR),
                                                    delta.isPositiveDelta(),
                                                ),
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = deltaTone(delta),
                                    )
                                }
                                // The artboard draws a large sparkline here. BankAccountDetail
                                // carries no series — only the list summary does — so it is a
                                // mapping gap rather than a layout one, and inventing a shape from
                                // one number would be a chart of nothing.
                            }
                            // Only for the member responsible for this account, and only because
                            // the server said so in the settings answer: the app works out no role
                            // of its own here.
                            state.settings?.takeIf { it.canSetTarget || it.canConfigureVisibility }
                                ?.let {
                                    KrtGhostButton(
                                        text = stringResource(R.string.bank_settings),
                                        onClick = actions.onOpen,
                                        modifier =
                                            Modifier
                                                .testTag(BANK_SETTINGS_TAG)
                                                .alpha(if (state.writable) 1f else DISABLED_WRITE_ALPHA),
                                        enabled = state.writable,
                                    )
                                }
                        }
                    }
                    if (staff) {
                        item(key = "reports") {
                            ReportActions(
                                busy = state.downloading,
                                onStatement = onStatement,
                                onThreeMonthReport = onThreeMonthReport,
                            )
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
                            BookingRow(
                                booking = booking,
                                staff = staff,
                                reversed = booking.transactionId in reversedIds,
                                onReverse = onReverse,
                            )
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
 * The row's direction, as the artboard's arrow.
 *
 * Exactly the classification `REQ-APP-BANK-003` already uses for the sign, so the glyph and the
 * figure can never disagree: money in points down and reads success, money out points up and reads
 * danger, and every other kind — a transfer, a reversal, one this build has never seen — gets the
 * neutral swap rather than a direction nobody checked.
 *
 * @param incoming `true` for a deposit, `false` for a withdrawal, `null` for anything else.
 */
@Composable
private fun BookingDirection(incoming: Boolean?) {
    KrtIcon(
        id =
            when (incoming) {
                true -> DesignR.drawable.ic_krt_bank_in
                false -> DesignR.drawable.ic_krt_bank_out
                null -> DesignR.drawable.ic_krt_swap
            },
        contentDescription = null,
        tint =
            when (incoming) {
                true -> KrtPalette.SuccessText
                false -> KrtPalette.DangerText
                null -> KrtPalette.TextMuted
            },
    )
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
private fun BookingRow(
    booking: BankBooking,
    staff: Boolean = false,
    reversed: Boolean = false,
    onReverse: (BankBooking) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The direction as a glyph, which artboard 2 draws on every row. The sign and the tint
        // already say it, but both live at the far right; the ledger is read down the left edge,
        // and „was this money in or out" should not need a saccade to the amount column.
        BookingDirection(booking.incoming)
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
            if (reversed) {
                // Saying it on the row is what makes the missing action legible: without it, a
                // member sees a Storno offered on one line and absent on the next for no reason.
                Text(
                    text = stringResource(R.string.bank_booking_reversed),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
        }
        Text(
            text = booking.signedAmount(),
            style = MaterialTheme.typography.bodyMedium,
            color = booking.amountColor(),
        )
        // A Storno is offered on an original that still stands and that the reversal can name:
        // not on a counter-booking, not on one already reversed, and not on a row whose transaction
        // the wire did not carry.
        val reversible = !reversed && !booking.isReversal && booking.transactionId != null
        if (staff && reversible) {
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_reset,
                label = stringResource(R.string.bank_booking_reverse),
                onClick = { onReverse(booking) },
            )
        }
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
    val booked = createdAt
    val time = if (booked == null) null else booked.relativeToNow()
    return listOfNotNull(holder?.takeIf { it.isNotBlank() }, time).joinToString(" · ")
}

/**
 * The translated name of a booking kind.
 *
 * @return the wording, or the raw server value for a kind this build has never seen — an
 *   untranslated word beats an empty line.
 */
@Composable
private fun BankBooking.typeLabel(): String = bankTypeLabel(type)

/**
 * The translated name of a booking kind.
 *
 * Shared with the holder detail, which shows the same kinds against custody rather than against an
 * account — the wording is the same fact either way.
 *
 * @param type the server's value.
 * @return the wording, or the raw server value for a kind this build has never seen — an
 *   untranslated word beats an empty line.
 */
@Composable
internal fun bankTypeLabel(type: String?): String =
    when (type) {
        null -> ""
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
 * @param onOpenHolder a holder row in the Konten tab was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun BankAccountsRoute(
    viewModel: BankViewModel,
    requestsViewModel: BankRequestsViewModel,
    staffViewModel: BankStaffViewModel,
    lifecycleViewModel: BankLifecycleViewModel,
    onOpenAccount: (String) -> Unit,
    onOpenHolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requests by requestsViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab) {
        if (tab == 1) {
            requestsViewModel.loadOnce()
        }
    }
    val caller = LocalCaller.current
    val staffAllowed = caller?.bankEmployee == true
    var scope by rememberSaveable { mutableIntStateOf(0) }
    var lockToast by remember { mutableStateOf(false) }
    LaunchedEffect(scope) {
        if (scope == STAFF_SCOPE) {
            staffViewModel.loadOnce()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.bank_scope_member),
                    stringResource(R.string.bank_scope_staff),
                ),
            selectedIndex = scope,
            onSelect = { chosen ->
                // A locked segment still reports the tap; that is what lets the screen say why it
                // is locked instead of silently doing nothing.
                if (chosen == STAFF_SCOPE && !staffAllowed) {
                    lockToast = true
                } else {
                    scope = chosen
                }
            },
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
            stretch = true,
            lockedIndices = if (staffAllowed) emptySet() else setOf(STAFF_SCOPE),
        )
        if (scope == STAFF_SCOPE) {
            BankStaffScope(
                viewModel = staffViewModel,
                lifecycleViewModel = lifecycleViewModel,
                onOpenAccount = onOpenAccount,
                onOpenHolder = onOpenHolder,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        KrtPageTabs(
            tabs =
                listOf(
                    KrtPageTab(label = stringResource(R.string.bank_tab_accounts)),
                    KrtPageTab(
                        label = stringResource(R.string.bank_tab_requests),
                        count = requests.pendingCount.takeIf { it > 0 },
                    ),
                ),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (tab == 0) {
                BankAccountsScreen(
                    state = state,
                    onRefresh = viewModel::onRefresh,
                    onRetryNow = viewModel::onRetry,
                    onOpenAccount = onOpenAccount,
                )
            } else {
                BankRequestsTab(
                    state = requests,
                    onRefresh = requestsViewModel::onRefresh,
                    actions =
                        BankRequestRowActions(
                            onGrant = { requestsViewModel.onSetApproval(it, granted = true) },
                            onRevoke = { requestsViewModel.onSetApproval(it, granted = false) },
                            onEdit = requestsViewModel::onEdit,
                            onWithdraw = requestsViewModel::onWithdraw,
                        ),
                )
            }
        }
        KrtBottomCtaBar(
            // Only where the rail replaces the bottom navigation: on a phone the nav bar sits
            // below this and has already taken the inset, so taking it again would lift the CTA
            // off its own bar.
            modifier =
                if (isWideWindow()) {
                    Modifier.padding(bottom = LocalKrtBottomBarInset.current)
                } else {
                    Modifier
                },
        ) {
            KrtCtaButton(
                text = stringResource(R.string.bank_request_action),
                onClick = {
                    // Raising a request needs the accounts, and on the Konten tab they are already
                    // here — but a member who opens the sheet without ever visiting Anträge would
                    // otherwise get an empty picker.
                    requestsViewModel.loadOnce()
                    requestsViewModel.onCompose()
                },
                modifier = Modifier.weight(1f),
                enabled = requests.online,
                iconRes = DesignR.drawable.ic_krt_plus,
            )
        }
    }
    if (lockToast) {
        // The lock has to explain itself, or it is just a control that does nothing. Dismisses on
        // its own action, because there is nothing to do about it but read it.
        KrtToast(
            title = stringResource(R.string.bank_scope_staff),
            message = stringResource(R.string.bank_scope_locked),
            actionLabel = stringResource(R.string.action_ok),
            onAction = { lockToast = false },
        )
    }
    requests.draft?.let { draft ->
        BankRequestSheet(
            state = draft,
            accounts = requests.accounts,
            targets = requests.targets.map { BankTransferTargetOption(it.id, it.label) },
            online = requests.online,
            actions =
                BankRequestSheetActions(
                    onKind = { kind ->
                        requestsViewModel.onDraftChanged { it.copy(kind = kind, targetAccountId = null) }
                    },
                    onAccount = { id -> requestsViewModel.onDraftChanged { it.copy(accountId = id) } },
                    onTarget = { id -> requestsViewModel.onDraftChanged { it.copy(targetAccountId = id) } },
                    onAmount = { value -> requestsViewModel.onDraftChanged { it.copy(amount = value) } },
                    onNote = { value -> requestsViewModel.onDraftChanged { it.copy(note = value) } },
                    onSubmit = requestsViewModel::onSubmit,
                    onDismiss = requestsViewModel::onDismissSheet,
                ),
        )
    }
}

/** Test handle for the Verwaltung's „Direktbuchung" entry. */
const val BANK_DIRECT_OPEN_TAG: String = "bank-direct-open"

/**
 * The Verwaltung scope's content.
 *
 * **Only the Übersicht for now.** Artboard 4 draws four tabs — ÜBERSICHT · ANTRÄGE · KONTEN ·
 * GRANTS — and the other three arrive with the screens behind them. A tab bar whose tabs lead
 * nowhere would be worse than no tab bar, so it appears with the second tab rather than ahead of
 * it.
 *
 * @param viewModel drives the dashboard.
 * @param onOpenAccount a row was tapped.
 * @param onOpenHolder a holder row in the Konten tab was tapped.
 * @param modifier layout modifier.
 */
@Composable
private fun BankStaffScope(
    viewModel: BankStaffViewModel,
    lifecycleViewModel: BankLifecycleViewModel,
    onOpenAccount: (String) -> Unit,
    onOpenHolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle by lifecycleViewModel.state.collectAsStateWithLifecycle()
    var staffTab by rememberSaveable { mutableIntStateOf(0) }
    var managementToast by remember { mutableStateOf(false) }
    LaunchedEffect(staffTab) {
        if (staffTab == LIFECYCLE_TAB || staffTab == GRANTS_TAB) {
            lifecycleViewModel.loadOnce()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        KrtPageTabs(
            tabs =
                listOf(
                    KrtPageTab(label = stringResource(R.string.bank_staff_tab_overview)),
                    KrtPageTab(
                        label = stringResource(R.string.bank_staff_tab_requests),
                        count = state.openRequestTotal.takeIf { it > 0 },
                    ),
                    KrtPageTab(label = stringResource(R.string.bank_staff_tab_lifecycle)),
                    KrtPageTab(label = stringResource(R.string.bank_staff_tab_grants)),
                ),
            selectedIndex = staffTab,
            onSelect = { chosen ->
                // Artboard 4 draws GRANTS locked for an employee without Bank-Management —
                // tappable, never hidden, answering with the role it needs.
                if (chosen == GRANTS_TAB && !state.management) {
                    managementToast = true
                } else {
                    staffTab = chosen
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.weight(1f)) {
            StaffScopeContent(
                state = state,
                lifecycle = lifecycle,
                viewModel = viewModel,
                lifecycleViewModel = lifecycleViewModel,
                tab = staffTab,
                onOpenAccount = onOpenAccount,
                onOpenHolder = onOpenHolder,
                onLocked = { managementToast = true },
            )
        }
        if (staffTab == LIFECYCLE_TAB && phaseIsReady(state)) {
            KrtBottomCtaBar(
                modifier =
                    if (isWideWindow()) {
                        Modifier.padding(bottom = LocalKrtBottomBarInset.current)
                    } else {
                        Modifier
                    },
            ) {
                // Artboard 9: the direct booking is offered here and nowhere in the member view,
                // and without Bank-Management it is locked at the entry rather than at the CTA.
                KrtGhostButton(
                    text = stringResource(R.string.bank_direct_title),
                    onClick = {
                        if (state.management) {
                            viewModel.directBooking.open()
                        } else {
                            managementToast = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag(BANK_DIRECT_OPEN_TAG),
                    iconRes =
                        if (state.management) {
                            DesignR.drawable.ic_krt_swap
                        } else {
                            DesignR.drawable.ic_krt_lock
                        },
                )
                KrtCtaButton(
                    text = stringResource(R.string.bank_lifecycle_create),
                    onClick = {
                        if (state.management) {
                            lifecycleViewModel.onPrompt(BankLifecyclePrompt.Create(""))
                        } else {
                            managementToast = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    iconRes =
                        if (state.management) {
                            DesignR.drawable.ic_krt_plus
                        } else {
                            DesignR.drawable.ic_krt_lock
                        },
                )
            }
        }
    }
    state.direct?.let { direct ->
        BankDirectBookingSheet(
            state = direct,
            accounts = state.rows.map { it.account },
            holders = state.holders,
            onEdit = viewModel.directBooking::edit,
            onConfirm = viewModel.directBooking::confirm,
            onDismiss = viewModel.directBooking::close,
        )
    }
    StaffScopeDialogs(state = state, viewModel = viewModel)
    BankLifecycleDialogs(state = lifecycle, viewModel = lifecycleViewModel)
    if (managementToast) {
        KrtToast(
            title = stringResource(R.string.bank_staff_tab_lifecycle),
            message = stringResource(R.string.bank_staff_grants_locked),
            actionLabel = stringResource(R.string.action_ok),
            onAction = { managementToast = false },
        )
    }
}

/**
 * Whether the scope's read has resolved.
 *
 * @param state the scope.
 * @return whether its content is on screen.
 */
private fun phaseIsReady(state: BankStaffState): Boolean = state.phase is BankPhase.Ready

/** Which of the Verwaltung tabs the lifecycle sits on. */
private const val LIFECYCLE_TAB = 2

/** And the grants matrix. */
private const val GRANTS_TAB = 3

/**
 * What the selected staff tab shows, once the read has resolved.
 *
 * @param state what the scope holds.
 * @param lifecycle what the Konten tab holds.
 * @param viewModel drives the dashboard and the queue.
 * @param lifecycleViewModel drives the Konten tab.
 * @param tab which tab is selected.
 * @param onOpenAccount a row was tapped.
 * @param onOpenHolder a holder row in the Konten tab was tapped.
 * @param onLocked a locked lifecycle action was tapped.
 */
@Composable
private fun StaffScopeContent(
    state: BankStaffState,
    lifecycle: BankLifecycleState,
    viewModel: BankStaffViewModel,
    lifecycleViewModel: BankLifecycleViewModel,
    tab: Int,
    onOpenAccount: (String) -> Unit,
    onOpenHolder: (String) -> Unit,
    onLocked: () -> Unit,
) {
    val modifier = Modifier.fillMaxSize()
    when (val phase = state.phase) {
        is BankPhase.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.bank_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is BankPhase.Failed -> {
            // A caller without the role is the ordinary answer here, not a fault: the segment is
            // offered to everyone by design, so the refusal has to read as an explanation.
            val forbidden = phase.error is ApiError.Forbidden
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_lock,
                title =
                    stringResource(
                        if (forbidden) {
                            R.string.bank_staff_error_forbidden_title
                        } else {
                            R.string.bank_error_title
                        },
                    ),
                message =
                    stringResource(
                        if (forbidden) {
                            R.string.bank_staff_error_forbidden_message
                        } else {
                            R.string.bank_error_message
                        },
                    ),
                actionText = stringResource(R.string.missions_retry).takeIf { !forbidden },
                onAction = viewModel::onRefresh,
                modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
            )
        }

        is BankPhase.Ready -> {
            if (tab == GRANTS_TAB) {
                BankGrantsTab(
                    state = lifecycle,
                    accounts = lifecycle.accounts,
                    management = state.management,
                    actions =
                        BankGrantsActions(
                            onSelectAccount = lifecycleViewModel::onSelectGrantAccount,
                            onSetGrant = lifecycleViewModel::onSetGrant,
                            onRevoke = lifecycleViewModel::onPrompt,
                            onLocked = onLocked,
                            onAdd = lifecycleViewModel::onAddGrant,
                        ),
                    modifier = modifier,
                )
            } else if (tab == LIFECYCLE_TAB) {
                BankLifecycleTab(
                    state = lifecycle,
                    management = state.management,
                    onRefresh = lifecycleViewModel::onRefresh,
                    actions =
                        BankLifecycleActions(
                            onExpand = lifecycleViewModel::onExpand,
                            onPrompt = lifecycleViewModel::onPrompt,
                            onOpenHolder = { onOpenHolder(it.id) },
                            onAddHolder = lifecycleViewModel::onAddHolder,
                            onLocked = onLocked,
                        ),
                    modifier = modifier,
                )
            } else if (tab == 0) {
                BankStaffOverview(
                    state = state,
                    onRefresh = viewModel::onRefresh,
                    onOpenAccount = onOpenAccount,
                    modifier = modifier,
                )
            } else {
                BankStaffQueue(
                    state = state,
                    onRefresh = viewModel::onRefresh,
                    actions =
                        BankStaffQueueActions(
                            onConfirm = viewModel::onConfirmOpen,
                            onReject = viewModel::onRejectOpen,
                        ),
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * The scope's two decision dialogs.
 *
 * Both sit outside the tab content on purpose: a decision taken from the queue must survive the
 * list reloading underneath it.
 *
 * @param state what the scope holds.
 * @param viewModel drives it.
 */
@Composable
private fun StaffScopeDialogs(
    state: BankStaffState,
    viewModel: BankStaffViewModel,
) {
    state.confirming?.let { confirming ->
        BankConfirmSheet(
            state = confirming,
            holders =
                state.holders.map {
                    BankHolderOption(id = it.id, label = it.handle.ifBlank { it.id })
                },
            actions =
                BankConfirmSheetActions(
                    onHolder = { id -> viewModel.onConfirmChanged { it.copy(holderId = id) } },
                    onDestinationHolder = { id ->
                        viewModel.onConfirmChanged { it.copy(destinationHolderId = id) }
                    },
                    onAttest = { on -> viewModel.onConfirmChanged { it.copy(approvalAttested = on) } },
                    onStaffNote = { note -> viewModel.onConfirmChanged { it.copy(staffNote = note) } },
                    onSubmit = viewModel::onConfirmSubmit,
                    onDismiss = viewModel::onConfirmDismiss,
                ),
        )
    }
    state.rejecting?.let { rejecting ->
        KrtModal(
            title = stringResource(R.string.bank_staff_reject_title),
            confirmText = stringResource(R.string.bank_staff_reject),
            onConfirm = viewModel::onRejectSubmit,
            onDismiss = viewModel::onRejectDismiss,
            tone = KrtModalTone.Danger,
        ) {
            Text(
                text = stringResource(R.string.bank_staff_reject_message),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
            KrtTextField(
                value = rejecting.reason,
                onValueChange = viewModel::onRejectReason,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_staff_reject_reason),
                placeholder = stringResource(R.string.bank_staff_reject_reason_placeholder),
                isError = rejecting.error != null,
                errorText = rejecting.error?.let { bankRequestErrorMessage(it) },
            )
        }
    }
}

/** Which segment the staff scope sits on. */
private const val STAFF_SCOPE = 1

/**
 * The Konten tab's confirmations.
 *
 * None of them is destructive — closing is reversible and a deactivated holder keeps their
 * holdings withdrawable — so none carries a type-to-confirm hurdle. Every wording is the web
 * frontend's own, so the same act reads the same on both surfaces.
 *
 * @param state what the tab holds.
 * @param viewModel drives it.
 */
@Composable
private fun BankLifecycleDialogs(
    state: BankLifecycleState,
    viewModel: BankLifecycleViewModel,
) {
    state.holderDraft?.let { draft ->
        BankHolderRegisterSheet(
            draft = draft,
            saving = state.saving,
            error = state.error,
            actions =
                BankHolderRegisterActions(
                    onQuery = viewModel::onHolderQuery,
                    onSelect = viewModel::onHolderSelected,
                    onConfirm = viewModel::onRegisterHolder,
                    onDismiss = viewModel::onDismissHolderDraft,
                ),
        )
    }
    state.granteeDraft?.let { draft ->
        BankGrantSheet(
            draft = draft,
            accountName =
                state.accounts.firstOrNull { it.id == state.grantAccountId }?.name.orEmpty(),
            saving = state.saving,
            error = state.error,
            actions =
                BankGrantSheetActions(
                    onQuery = viewModel::onGranteeQuery,
                    onSelect = viewModel::onGranteeSelected,
                    onDraftChanged = viewModel::onGrantDraftChanged,
                    onCreate = viewModel::onCreateGrant,
                    onDismiss = viewModel::onDismissGrantDraft,
                ),
        )
    }
    val prompt = state.prompt ?: return
    val naming = prompt is BankLifecyclePrompt.Rename || prompt is BankLifecyclePrompt.Create
    KrtModal(
        title = stringResource(prompt.titleRes()),
        confirmText = stringResource(prompt.confirmRes()),
        onConfirm = viewModel::onConfirmPrompt,
        onDismiss = viewModel::onDismissPrompt,
        tone =
            if (prompt is BankLifecyclePrompt.Close || prompt is BankLifecyclePrompt.RevokeGrant) {
                KrtModalTone.Danger
            } else {
                KrtModalTone.Standard
            },
    ) {
        prompt.bodyRes()?.let { body ->
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
        }
        if (naming) {
            KrtTextField(
                value = prompt.name(),
                onValueChange = viewModel::onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_lifecycle_name),
                isError = state.error != null,
                errorText = state.error?.let { bankRequestErrorMessage(it) },
            )
        } else {
            state.error?.let { error ->
                Text(
                    text = bankRequestErrorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
        }
    }
}

/**
 * The heading each confirmation carries.
 *
 * @return the string resource.
 */
private fun BankLifecyclePrompt.titleRes(): Int =
    when (this) {
        is BankLifecyclePrompt.Close -> {
            R.string.bank_lifecycle_close_title
        }

        is BankLifecyclePrompt.Reopen -> {
            R.string.bank_lifecycle_reopen_title
        }

        is BankLifecyclePrompt.Rename -> {
            R.string.bank_lifecycle_rename_title
        }

        is BankLifecyclePrompt.Create -> {
            R.string.bank_lifecycle_create_title
        }

        is BankLifecyclePrompt.HolderActivation -> {
            if (active) {
                R.string.bank_lifecycle_holder_reactivate_title
            } else {
                R.string.bank_lifecycle_holder_deactivate_title
            }
        }

        is BankLifecyclePrompt.RevokeGrant -> {
            R.string.bank_grants_revoke_title
        }
    }

/**
 * What the confirming button says.
 *
 * @return the string resource.
 */
private fun BankLifecyclePrompt.confirmRes(): Int =
    when (this) {
        is BankLifecyclePrompt.Close -> {
            R.string.bank_lifecycle_close
        }

        is BankLifecyclePrompt.Reopen -> {
            R.string.bank_lifecycle_reopen
        }

        is BankLifecyclePrompt.Rename -> {
            R.string.bank_lifecycle_rename
        }

        is BankLifecyclePrompt.Create -> {
            R.string.bank_lifecycle_create
        }

        is BankLifecyclePrompt.HolderActivation -> {
            if (active) {
                R.string.bank_lifecycle_holder_reactivate
            } else {
                R.string.bank_lifecycle_holder_deactivate
            }
        }

        is BankLifecyclePrompt.RevokeGrant -> {
            R.string.bank_grants_revoke
        }
    }

/**
 * The consequence it states, or `null` when the field alone says enough.
 *
 * @return the string resource, or `null`.
 */
private fun BankLifecyclePrompt.bodyRes(): Int? =
    when (this) {
        is BankLifecyclePrompt.Close -> {
            R.string.bank_lifecycle_close_text
        }

        is BankLifecyclePrompt.Reopen -> {
            R.string.bank_lifecycle_reopen_text
        }

        is BankLifecyclePrompt.Rename -> {
            null
        }

        is BankLifecyclePrompt.Create -> {
            null
        }

        is BankLifecyclePrompt.HolderActivation -> {
            if (active) {
                R.string.bank_lifecycle_holder_reactivate_text
            } else {
                R.string.bank_lifecycle_holder_deactivate_text
            }
        }

        is BankLifecyclePrompt.RevokeGrant -> {
            if (sightSurvives) {
                R.string.bank_grants_revoke_text_cartel
            } else {
                R.string.bank_grants_revoke_text
            }
        }
    }

/**
 * The name a naming prompt currently carries.
 *
 * @return the name, or empty for a prompt that names nothing.
 */
private fun BankLifecyclePrompt.name(): String =
    when (this) {
        is BankLifecyclePrompt.Rename -> name
        is BankLifecyclePrompt.Create -> name
        else -> ""
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
    state.reversal?.let { booking ->
        KrtModal(
            title = stringResource(R.string.bank_booking_reverse_title),
            confirmText = stringResource(R.string.bank_booking_reverse),
            onConfirm = viewModel::onConfirmReversal,
            onDismiss = viewModel::onDismissReversal,
            tone = KrtModalTone.Danger,
        ) {
            Text(
                text = stringResource(R.string.bank_booking_reverse_text),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
            KrtTextField(
                value = state.reversalNote,
                onValueChange = viewModel::onReversalNote,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_booking_reverse_note),
            )
            state.error?.let { error ->
                Text(
                    text = bankConflictMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
        }
    }
    BankAccountScreen(
        state = state,
        onReverse = viewModel::onReverse,
        onStatement = viewModel::onStatement,
        onThreeMonthReport = viewModel::onThreeMonthReport,
        onReportHandled = viewModel::onReportHandled,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        actions =
            BankSettingsActions(
                onOpen = viewModel::onOpenSettings,
                onDismiss = viewModel::onDismissSettings,
                onTargetChanged = viewModel::onTargetChanged,
                onSaveTarget = viewModel::onSaveTarget,
                onToggleRole = viewModel::onToggleRole,
                onToggleAllMembers = viewModel::onToggleAllMembers,
                onEditLimit = viewModel.limits::edit,
                onRemoveLimit = viewModel.limits::remove,
            ),
        limitActions =
            BankLimitActions(
                onAmount = viewModel.limits::onAmount,
                onConfirm = viewModel.limits::confirm,
                onConfirmRemoval = viewModel.limits::confirmRemoval,
                onDismiss = viewModel.limits::close,
            ),
        modifier = modifier,
    )
}

/**
 * What the account settings report back.
 *
 * @property onOpen the settings were opened.
 * @property onDismiss they were closed.
 * @property onTargetChanged the target changed.
 * @property onSaveTarget the target was saved.
 * @property onToggleRole a role bucket was granted or revoked.
 * @property onToggleAllMembers the all-members switch was flipped.
 */
data class BankSettingsActions(
    val onOpen: () -> Unit,
    val onDismiss: () -> Unit,
    val onTargetChanged: (String) -> Unit,
    val onSaveTarget: () -> Unit,
    val onToggleRole: (String) -> Unit,
    val onToggleAllMembers: () -> Unit,
    val onEditLimit: (BankLimitTarget, String, String?) -> Unit = { _, _, _ -> },
    val onRemoveLimit: (BankLimitTarget, String, String?) -> Unit = { _, _, _ -> },
)

/**
 * What the account's responsible holder may change about it.
 *
 * Every control here is drawn from a flag the **server** sent: `canSetTarget` and
 * `canConfigureVisibility` are per-account facts, and the app works out no role of its own. An
 * account type that does not support visibility at all says so rather than showing an empty
 * section — "cannot be configured" and "you may not configure it" are different sentences.
 *
 * @param settings what the account says.
 * @param state the screen, for the save gate and the last refusal.
 * @param actions what the sheet reports back.
 */
@Composable
private fun BankSettingsSheet(
    settings: BankAccountSettings,
    state: BankAccountState,
    actions: BankSettingsActions,
) {
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        modifier = Modifier.testTag(BANK_SETTINGS_SHEET_TAG),
        title = stringResource(R.string.bank_settings),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            if (settings.canSetTarget) {
                KrtTextField(
                    value = state.targetDraft.orEmpty(),
                    onValueChange = actions.onTargetChanged,
                    label = stringResource(R.string.bank_settings_target),
                    enabled = !state.saving,
                )
                Text(
                    text = stringResource(R.string.bank_settings_target_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = actions.onSaveTarget,
                    modifier = Modifier.testTag(BANK_TARGET_SAVE_TAG),
                    enabled = state.writable,
                )
            }
            if (settings.canConfigureVisibility) {
                BankVisibilitySection(settings = settings, state = state, actions = actions)
            }
            // Design ch. 12 artboard 10 makes this a fifth tab of the Verwaltung; it cannot be one,
            // because every limit endpoint addresses ONE account and the current values ride on
            // that account's settings. It lives beside the visibility grants instead — same scope,
            // same owner, same read. Recorded as a deviation.
            BankApprovalLimitsSection(
                limits = settings.approvalLimits,
                busy = state.busyLimit,
                onEdit = actions.onEditLimit,
                onRemove = actions.onRemoveLimit,
            )
            state.error?.let { error ->
                KrtFieldError(
                    text =
                        stringResource(
                            if (error is ApiError.OptimisticLock) {
                                R.string.conflict_inline
                            } else {
                                R.string.write_failed
                            },
                        ),
                )
            }
            KrtGhostButton(
                text = stringResource(R.string.personal_inventory_cancel),
                onClick = actions.onDismiss,
                enabled = !state.saving,
            )
        }
    }
}

/**
 * Who may see the account.
 *
 * @param settings what it says.
 * @param state the screen.
 * @param actions what the section reports back.
 */
@Composable
private fun BankVisibilitySection(
    settings: BankAccountSettings,
    state: BankAccountState,
    actions: BankSettingsActions,
) {
    KrtSectionTitle(text = stringResource(R.string.bank_settings_visibility))
    if (!settings.visibilityConfigurable) {
        Text(
            text = stringResource(R.string.bank_settings_visibility_fixed),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    if (settings.allMembersSupported) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtToggle(
                checked = settings.allMembersGranted,
                onCheckedChange = { actions.onToggleAllMembers() },
                enabled = state.writable,
                modifier = Modifier.testTag(BANK_ALL_MEMBERS_TAG),
            )
            Text(
                text = stringResource(R.string.bank_settings_all_members),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
        }
    }
    settings.availableRoleCodes.forEach { code ->
        KrtFilterChip(
            text = code,
            selected = code in settings.grantedRoleCodes,
            onClick = { actions.onToggleRole(code) },
            modifier = Modifier.testTag(BANK_ROLE_TAG),
        )
    }
}

/** Signs the server may put in front of a negative delta: hyphen-minus and U+2212. */
private const val MINUS_SIGNS = "-−"

/**
 * „Kontoauszug" and „3-Monats-Bericht", the staff account detail's two reports.
 *
 * Its own composable so the screen stays under detekt's complexity limit, and because the pair is
 * one idea: both fetch a file and hand it to a share sheet, and neither is offered to a member.
 *
 * @param busy whether a report is already being fetched.
 * @param onStatement the statement was asked for.
 * @param onThreeMonthReport the quarter report was asked for.
 */
@Composable
private fun ReportActions(
    busy: Boolean,
    onStatement: () -> Unit,
    onThreeMonthReport: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtOutlineButton(
            text = stringResource(R.string.bank_report_statement),
            onClick = onStatement,
            modifier = Modifier.weight(1f),
            enabled = !busy,
            iconRes = DesignR.drawable.ic_krt_pdf,
        )
        KrtOutlineButton(
            text = stringResource(R.string.bank_report_quarter),
            onClick = onThreeMonthReport,
            modifier = Modifier.weight(1f),
            enabled = !busy,
            iconRes = DesignR.drawable.ic_krt_download,
        )
    }
}

/**
 * The settings sheet and the conflict dialog that shares its screen.
 *
 * Extracted so the account screen stays under detekt's complexity limit; the two belong together
 * anyway, since the dialog exists to resolve a refusal the sheet caused.
 *
 * @param state what the screen holds.
 * @param actions what the sheet reports back.
 * @param onRefresh re-reads the account after a conflict.
 */
@Composable
private fun AccountSettingsOverlay(
    state: BankAccountState,
    actions: BankSettingsActions,
    onRefresh: () -> Unit,
    limitActions: BankLimitActions? = null,
) {
    // Over the settings sheet, and outside its own open-check: opening a limit from one of its
    // rows must not close what it was opened from.
    limitActions?.let { ApprovalLimitOverlays(state = state, actions = it) }
    if (!state.settingsOpen) {
        return
    }
    state.settings?.let { settings ->
        // Design ch. 14's conflict dialog, at the host: „Neu laden" closes the sheet and makes the
        // account re-read rather than re-sending a value against a newer version.
        ConflictOn(
            error = state.error,
            onReload = {
                actions.onDismiss()
                onRefresh()
            },
        )
        BankSettingsSheet(settings = settings, state = state, actions = actions)
    }
}

/**
 * The two Freigabe-Limit sheets, at the host.
 *
 * Outside [AccountSettingsOverlay] because they sit **over** the settings sheet: opening one from
 * a row must not close what it was opened from.
 *
 * @param state the screen.
 * @param actions setting and removing one limit.
 */
@Composable
private fun ApprovalLimitOverlays(
    state: BankAccountState,
    actions: BankLimitActions,
) {
    state.limitDraft?.let { draft ->
        BankLimitSheet(
            draft = draft,
            saving = state.saving,
            onAmount = actions.onAmount,
            onConfirm = actions.onConfirm,
            onDismiss = actions.onDismiss,
        )
    }
    state.limitRemoval?.let { draft ->
        BankLimitRemoveModal(
            draft = draft,
            saving = state.saving,
            onConfirm = actions.onConfirmRemoval,
            onDismiss = actions.onDismiss,
        )
    }
}

/**
 * What the two Freigabe-Limit sheets report back.
 *
 * @property onAmount the field changed.
 * @property onConfirm „Setzen".
 * @property onConfirmRemoval „Entfernen".
 * @property onDismiss either sheet was closed.
 */
data class BankLimitActions(
    val onAmount: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onConfirmRemoval: () -> Unit,
    val onDismiss: () -> Unit,
)

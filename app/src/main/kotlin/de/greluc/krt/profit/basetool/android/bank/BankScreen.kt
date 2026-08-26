/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import android.text.format.DateUtils
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSettings
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKpiCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import java.time.Instant
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
private fun deltaTone(delta: String): androidx.compose.ui.graphics.Color {
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
    // The artboard's total card carries an orange rail on its LEFT edge; KrtCardVariant.Accent
    // puts its bar across the top, which is a different mark for a different purpose. Composed
    // here rather than by widening the shared component, because nothing else asks for this.
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier =
                    Modifier
                        .width(KrtSpacing.xs)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.padding(KrtSpacing.lg)) {
                Text(
                    text = stringResource(R.string.bank_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = formatAmount(sum.toPlainString()),
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
    // KrtKpiCard *is* this card: design ch. 12 draws the account as a `kpi-card` — name above, the
    // balance large beneath it, and the 30-day delta beside a sparkline on one row. It was built
    // here as a bare Column with a hairline underneath, which loses the border, puts the balance on
    // the name's line and leaves the delta grey when its sign is the point of it.
    KrtKpiCard(
        title = account.name,
        value = formatAmount(account.balance.orEmpty()),
        modifier = Modifier.fillMaxWidth(),
        delta = account.delta30d?.let { stringResource(R.string.bank_delta_30d, formatAmount(it)) },
        deltaPositive = account.delta30d.isPositiveDelta(),
        sparkline = account.sparkline.takeIf { it.isNotEmpty() }?.map(Double::toFloat),
        sparklineDescription = stringResource(R.string.bank_sparkline_description),
        onClick = onClick,
    )
}

/**
 * Whether a formatted delta reads as an increase.
 *
 * The server sends it already formatted and already signed, so the sign is read off the string
 * rather than re-derived — which also keeps the minus sign the server chose, typographic or not.
 *
 * @return `false` only for an explicitly negative figure; an absent or unsigned one is not drawn as
 *   a loss.
 */
private fun String?.isPositiveDelta(): Boolean {
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountScreen(
    state: BankAccountState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    actions: BankSettingsActions,
    modifier: Modifier = Modifier,
) {
    val account = state.account
    val phase = state.phase
    if (state.settingsOpen) {
        state.settings?.let { settings ->
            BankSettingsSheet(settings = settings, state = state, actions = actions)
        }
    }
    when {
        account != null -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag(BANK_ACCOUNT_TAG)) {
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
                                        text = stringResource(R.string.bank_delta_30d, formatAmount(delta)),
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
        onRetryNow = viewModel::onRetry,
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
        actions =
            BankSettingsActions(
                onOpen = viewModel::onOpenSettings,
                onDismiss = viewModel::onDismissSettings,
                onTargetChanged = viewModel::onTargetChanged,
                onSaveTarget = viewModel::onSaveTarget,
                onToggleRole = viewModel::onToggleRole,
                onToggleAllMembers = viewModel::onToggleAllMembers,
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
            state.error?.let { error ->
                KrtFieldError(
                    text =
                        stringResource(
                            if (error is ApiError.OptimisticLock) {
                                R.string.conflict_body
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

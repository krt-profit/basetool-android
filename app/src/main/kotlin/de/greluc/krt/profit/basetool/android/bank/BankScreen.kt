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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
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

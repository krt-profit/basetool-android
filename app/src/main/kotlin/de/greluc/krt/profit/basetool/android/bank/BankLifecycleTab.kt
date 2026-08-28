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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankManagedAccount
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The lifecycle list, for the tests that read it. */
const val BANK_LIFECYCLE_TAG: String = "bank-lifecycle"

/** How far a closed account recedes; a data difference, not a rights lock. */
private const val CLOSED_ALPHA = 0.55f

/**
 * What the Konten tab reports back.
 *
 * @property onExpand a row was opened or closed.
 * @property onPrompt a lifecycle decision was reached for.
 * @property onOpenHolder a holder row was tapped.
 * @property onAddHolder a new holder is to be registered.
 * @property onLocked a locked action was tapped by someone without Bank-Management. The control
 *   answers rather than doing nothing, because a lock that says nothing is indistinguishable from
 *   a broken button.
 */
data class BankLifecycleActions(
    val onExpand: (String?) -> Unit,
    val onPrompt: (BankLifecyclePrompt) -> Unit,
    val onOpenHolder: (BankHolder) -> Unit,
    val onAddHolder: () -> Unit,
    val onLocked: () -> Unit,
)

/**
 * The Verwaltung scope's Konten tab — design chapter 12, artboard 6.
 *
 * Reads are a bank employee's; every action here is **Bank-Management's**. Whether the caller has
 * that comes from the server rather than from a role the app worked out — and without it the
 * actions are drawn **locked**, not hidden. A member who cannot see a control cannot learn that the
 * surface exists or which role opens it; the chapter-09 pattern is a padlock that answers when
 * tapped.
 *
 * @param state what the tab holds.
 * @param management whether the caller may change anything.
 * @param onRefresh a pull-to-refresh.
 * @param actions what a row reports back.
 * @param modifier layout modifier.
 */
@Composable
fun BankLifecycleTab(
    state: BankLifecycleState,
    management: Boolean,
    onRefresh: () -> Unit,
    actions: BankLifecycleActions,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.accounts.isEmpty() && state.holders.isEmpty()) {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_bank,
                title = stringResource(R.string.bank_lifecycle_empty_title),
                message = stringResource(R.string.bank_lifecycle_empty_message),
                modifier = Modifier.padding(KrtSpacing.lg),
            )
            return@PullToRefreshBox
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(BANK_LIFECYCLE_TAG),
            contentPadding = PaddingValues(KrtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            items(state.accounts, key = { it.id }) { account ->
                LifecycleAccountRow(
                    account = account,
                    expanded = state.expandedId == account.id,
                    management = management,
                    actions = actions,
                )
            }
            item(key = "holders-header") { HolderSectionHeader() }
            item(key = "holders-add") {
                KrtOutlineButton(
                    text = stringResource(R.string.bank_lifecycle_holder_register),
                    onClick = { if (management) actions.onAddHolder() else actions.onLocked() },
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = if (management) null else DesignR.drawable.ic_krt_lock,
                )
            }
            items(state.holders, key = { "holder-${it.id}" }) { holder ->
                HolderRow(
                    holder = holder,
                    management = management,
                    actions = actions,
                )
            }
            if (state.holders.isEmpty()) {
                item(key = "holders-empty") {
                    Text(
                        text = stringResource(R.string.bank_lifecycle_holders_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                        modifier = Modifier.padding(KrtSpacing.sm),
                    )
                }
            }
        }
    }
}

/**
 * One account, with its lifecycle actions once the row is opened.
 *
 * @param account the account.
 * @param expanded whether its actions are showing.
 * @param management whether the caller may change anything.
 * @param actions what the row reports back.
 */
@Composable
private fun LifecycleAccountRow(
    account: BankManagedAccount,
    expanded: Boolean,
    management: Boolean,
    actions: BankLifecycleActions,
) {
    val closed = account.status == BankAccountStatus.CLOSED
    KrtCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { actions.onExpand(account.id) },
    ) {
        Column(
            modifier = Modifier.alpha(if (closed) CLOSED_ALPHA else 1f),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (closed) KrtPalette.TextMuted else KrtPalette.White,
                )
                KrtChip(
                    text =
                        stringResource(
                            if (closed) {
                                R.string.bank_lifecycle_status_closed
                            } else {
                                R.string.bank_lifecycle_status_active
                            },
                        ),
                    tone = if (closed) KrtChipTone.Muted else KrtChipTone.Success,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatAmount(account.balance.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = KrtPalette.White,
                )
                KrtIcon(
                    id =
                        if (expanded) {
                            DesignR.drawable.ic_krt_chevron_up
                        } else {
                            DesignR.drawable.ic_krt_chevron_down
                        },
                    contentDescription = null,
                    tint = KrtPalette.TextMuted,
                )
            }
            if (expanded) {
                AccountActions(
                    account = account,
                    closed = closed,
                    management = management,
                    actions = actions,
                )
            }
        }
    }
}

/**
 * What Bank-Management may do to one account.
 *
 * **Closing needs a zero balance**, which the server enforces and the row states in advance rather
 * than letting the button find out. Closing is reversible, so neither it nor its counterpart asks
 * the member to type anything.
 *
 * @param account the account.
 * @param closed whether it is closed.
 * @param management whether the caller may actually do any of it.
 * @param actions what the row reports back.
 */
@Composable
private fun AccountActions(
    account: BankManagedAccount,
    closed: Boolean,
    management: Boolean,
    actions: BankLifecycleActions,
) {
    val settled = (account.balance?.toDoubleOrNull() ?: 0.0) == 0.0
    // Drawn for everyone, locked for those without the role. A lock a member can see and tap is
    // what tells them the surface exists and which role opens it; hiding it tells them nothing.
    val lockIcon = DesignR.drawable.ic_krt_lock.takeIf { !management }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtGhostButton(
                text = stringResource(R.string.bank_lifecycle_rename),
                onClick = {
                    if (management) {
                        actions.onPrompt(BankLifecyclePrompt.Rename(account, account.name))
                    } else {
                        actions.onLocked()
                    }
                },
                modifier = Modifier.weight(1f),
                iconRes = lockIcon ?: DesignR.drawable.ic_krt_edit,
            )
            if (closed) {
                KrtOutlineButton(
                    text = stringResource(R.string.bank_lifecycle_reopen),
                    onClick = {
                        if (management) {
                            actions.onPrompt(BankLifecyclePrompt.Reopen(account))
                        } else {
                            actions.onLocked()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    iconRes = lockIcon ?: DesignR.drawable.ic_krt_reset,
                )
            } else {
                KrtQuietDangerButton(
                    text = stringResource(R.string.bank_lifecycle_close),
                    onClick = {
                        if (management) {
                            actions.onPrompt(BankLifecyclePrompt.Close(account))
                        } else {
                            actions.onLocked()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    // A non-zero balance is the server's own refusal and applies to everyone; the
                    // lock is about the role and stays tappable so it can explain itself.
                    enabled = settled || !management,
                    iconRes = lockIcon ?: DesignR.drawable.ic_krt_lock,
                )
            }
        }
        if (!closed && !settled && management) {
            Text(
                text = stringResource(R.string.bank_lifecycle_close_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/** The holder register's heading, and the fact that holders are not tied to accounts. */
@Composable
private fun HolderSectionHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.bank_lifecycle_holders),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = KrtPalette.White,
        )
        // Verwahrung is kept at unit level; a holder belongs to no single account, and the design
        // marks that so nobody reads the section as "the holders of the account above".
        KrtChip(
            text = stringResource(R.string.bank_lifecycle_holders_unbound),
            tone = KrtChipTone.Muted,
        )
    }
}

/**
 * One holder.
 *
 * @param holder the holder.
 * @param management whether the caller may change their activation.
 * @param actions what the row reports back.
 */
@Composable
private fun HolderRow(
    holder: BankHolder,
    management: Boolean,
    actions: BankLifecycleActions,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = { actions.onOpenHolder(holder) }) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth().alpha(if (holder.active) 1f else CLOSED_ALPHA),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = holder.handle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = KrtPalette.White,
                )
                if (!holder.active) {
                    KrtChip(
                        text = stringResource(R.string.bank_lifecycle_holder_inactive),
                        tone = KrtChipTone.Muted,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatAmount(holder.totalHeld.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Gray1,
                )
                KrtIcon(
                    id = DesignR.drawable.ic_krt_chevron_right,
                    contentDescription = null,
                    tint = KrtPalette.TextMuted,
                )
            }
            KrtGhostButton(
                text =
                    stringResource(
                        if (holder.active) {
                            R.string.bank_lifecycle_holder_deactivate
                        } else {
                            R.string.bank_lifecycle_holder_reactivate
                        },
                    ),
                onClick = {
                    if (management) {
                        actions.onPrompt(
                            BankLifecyclePrompt.HolderActivation(holder, !holder.active),
                        )
                    } else {
                        actions.onLocked()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                iconRes = DesignR.drawable.ic_krt_lock.takeIf { !management },
            )
        }
    }
}

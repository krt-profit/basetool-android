/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BankGrant
import de.greluc.krt.profit.basetool.android.core.data.BankManagedAccount
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The grants matrix, for the tests that read it. */
const val BANK_GRANTS_TAG: String = "bank-grants"

/** The KRT account, which every member sees by rule rather than by grant (REQ-BANK-037). */
private const val CARTEL_TYPE = "CARTEL"

/**
 * What the Grants tab reports back.
 *
 * @property onSelectAccount a different account's matrix was asked for.
 * @property onSetGrant a member's capabilities changed.
 * @property onRevoke a member's standing is to be removed entirely — a confirmation, because it
 *   is what takes their sight of the account away.
 * @property onAdd a new entry is to be made on the shown account.
 * @property onLocked a locked control was tapped by someone without Bank-Management.
 */
data class BankGrantsActions(
    val onSelectAccount: (String) -> Unit,
    val onSetGrant: (BankGrant) -> Unit,
    val onRevoke: (BankLifecyclePrompt.RevokeGrant) -> Unit,
    val onLocked: () -> Unit,
    val onAdd: () -> Unit,
)

/**
 * The Verwaltung scope's Grants tab — design chapter 12, artboard 7.
 *
 * **The matrix has three columns, not the drawn two.** `REQ-BANK-009` gives a grant three
 * independent flags — deposit, withdrawal, transfer — and no approval flag at all: who may approve
 * a request is decided by `requiredApprover`, per request, not per member. The artboard's „SEHEN"
 * column is right in substance, though: **the row's existence is the view grant**, so a member with
 * all three flags off may see the account and book nothing, and taking sight away means removing
 * the row.
 *
 * A per-member card rather than the drawn table: three capability columns plus a name do not fit a
 * phone's width the way two short ones did.
 *
 * One account escapes the sight rule: `CARTEL` is seen by every KRT member by rule (REQ-BANK-037),
 * so there the entry only ever carried booking rights — the note and the removal modal say so
 * rather than promising a sight they cannot take away.
 *
 * @param state what the tab holds.
 * @param accounts the accounts whose matrices can be shown.
 * @param management whether the caller may change anything.
 * @param actions what the tab reports back.
 * @param modifier layout modifier.
 */
@Composable
fun BankGrantsTab(
    state: BankLifecycleState,
    accounts: List<BankManagedAccount>,
    management: Boolean,
    actions: BankGrantsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (accounts.isEmpty()) {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_users,
                title = stringResource(R.string.bank_grants_no_accounts_title),
                message = stringResource(R.string.bank_grants_no_accounts_message),
                modifier = Modifier.padding(KrtSpacing.lg),
            )
            return@Column
        }
        // A chip per account, as artboard 7 draws it. Scrollable, because a unit with many
        // accounts would otherwise push the last one off the edge.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(KrtSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            accounts.forEach { account ->
                KrtFilterChip(
                    text = account.name,
                    selected = account.id == state.grantAccountId,
                    onClick = { actions.onSelectAccount(account.id) },
                )
            }
        }
        val sightSurvives =
            accounts.firstOrNull { it.id == state.grantAccountId }?.type == CARTEL_TYPE
        if (state.grantAccountId == null) {
            Text(
                text = stringResource(R.string.bank_grants_pick_account),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.padding(horizontal = KrtSpacing.md),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(BANK_GRANTS_TAG),
            contentPadding = PaddingValues(KrtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            state.error?.let { error ->
                item(key = "error") {
                    // A refused flag change used to snap the checkbox back and say nothing, which
                    // reads as "the app is broken" rather than "the server said no".
                    Text(
                        text = bankRequestErrorMessage(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.DangerText,
                    )
                }
            }
            item(key = "note") {
                Text(
                    text =
                        stringResource(
                            if (sightSurvives) {
                                R.string.bank_grants_view_note_cartel
                            } else {
                                R.string.bank_grants_view_note
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            item(key = "add") {
                KrtOutlineButton(
                    text = stringResource(R.string.bank_grants_add),
                    onClick = { if (management) actions.onAdd() else actions.onLocked() },
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = if (management) null else DesignR.drawable.ic_krt_lock,
                )
            }
            items(state.grants, key = { "${it.userId}-${it.accountId}" }) { grant ->
                GrantCard(
                    grant = grant,
                    management = management,
                    sightSurvives = sightSurvives,
                    actions = actions,
                )
            }
            if (state.grants.isEmpty() && !state.grantsLoading) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.bank_grants_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * One member's standing on the shown account.
 *
 * @param grant what the matrix says today.
 * @param management whether the caller may change it.
 * @param sightSurvives whether removing the entry leaves the member seeing the account anyway.
 * @param actions what the card reports back.
 */
@Composable
private fun GrantCard(
    grant: BankGrant,
    management: Boolean,
    sightSurvives: Boolean,
    actions: BankGrantsActions,
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Text(
                text = grant.handle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
            )
            GrantFlag(
                label = stringResource(R.string.bank_grants_deposit),
                checked = grant.canDeposit,
                management = management,
                actions = actions,
            ) { grant.copy(canDeposit = it) }
            GrantFlag(
                label = stringResource(R.string.bank_grants_withdraw),
                checked = grant.canWithdraw,
                management = management,
                actions = actions,
            ) { grant.copy(canWithdraw = it) }
            GrantFlag(
                label = stringResource(R.string.bank_grants_transfer),
                checked = grant.canTransfer,
                management = management,
                actions = actions,
            ) { grant.copy(canTransfer = it) }
            Row(modifier = Modifier.fillMaxWidth()) {
                KrtQuietDangerButton(
                    text = stringResource(R.string.bank_grants_revoke),
                    onClick = {
                        if (management) {
                            actions.onRevoke(
                                BankLifecyclePrompt.RevokeGrant(grant, sightSurvives),
                            )
                        } else {
                            actions.onLocked()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    iconRes =
                        if (management) {
                            DesignR.drawable.ic_krt_trash
                        } else {
                            DesignR.drawable.ic_krt_lock
                        },
                )
            }
        }
    }
}

/**
 * One capability of one grant.
 *
 * @param label what it lets the member do.
 * @param checked whether they may.
 * @param management whether the caller may change it.
 * @param actions what the row reports back.
 * @param toggled the grant as it would be with this flag flipped.
 */
@Composable
private fun GrantFlag(
    label: String,
    checked: Boolean,
    management: Boolean,
    actions: BankGrantsActions,
    toggled: (Boolean) -> BankGrant,
) {
    KrtCheckboxRow(
        label = label,
        checked = checked,
        onCheckedChange = { on ->
            if (management) actions.onSetGrant(toggled(on)) else actions.onLocked()
        },
    )
}

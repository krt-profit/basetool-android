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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.common.formatSignedAmount
import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTotalTile
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The staff dashboard's account list, for the tests that read it. */
const val BANK_STAFF_OVERVIEW_TAG: String = "bank-staff-overview"

/** The line under the KPI that counts accounts and open requests. */
const val BANK_STAFF_COUNTS_TAG: String = "bank-staff-counts"

/** How far a closed account recedes — a data difference, not a rights lock, so it dims rather than locks. */
private const val CLOSED_ROW_ALPHA = 0.55f

/**
 * The Verwaltung scope's Übersicht tab — design chapter 12, artboard 4.
 *
 * **The delta to the member list is the point of this screen.** It carries every account of the
 * unit, including ones the caller holds no view grant on and ones that are closed. Both are marked,
 * because "I can see this because I run the bank" and "I can see this because someone showed me"
 * are different facts about the same balance.
 *
 * @param state what the tab holds.
 * @param onRefresh a pull-to-refresh.
 * @param onOpenAccount a row was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun BankStaffOverview(
    state: BankStaffState,
    onRefresh: () -> Unit,
    onOpenAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.rows.isEmpty()) {
            // Two different emptinesses. Management sees every account there is, so an empty list
            // means the organisation runs none. An employee sees only what they hold a grant for,
            // so an empty list usually means they hold none — and telling them the bank has no
            // accounts would be false, in the one place they cannot check.
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_bank,
                title = stringResource(R.string.bank_staff_empty_title),
                message =
                    stringResource(
                        if (state.management) {
                            R.string.bank_staff_empty_message
                        } else {
                            R.string.bank_staff_empty_message_no_grant
                        },
                    ),
                modifier = Modifier.padding(KrtSpacing.s16),
            )
            return@PullToRefreshBox
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(BANK_STAFF_OVERVIEW_TAG),
            contentPadding = PaddingValues(KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            // The aggregate strip is management's alone (REQ-BANK-010); the server sends none
            // to anyone else, and inventing zeroes for it would assert an empty bank.
            state.totals?.let { totals ->
                item(key = "kpi") { StaffKpiBand(state = state, totals = totals) }
            }
            items(state.rows, key = { it.account.id }) { row ->
                StaffAccountRow(
                    row = row,
                    management = state.management,
                    onClick = { onOpenAccount(row.account.id) },
                )
            }
        }
    }
}

/**
 * The KPI band and the line that counts what it is made of.
 *
 * @param state what the tab holds.
 * @param totals the strip's figures; the caller has already established there are some.
 */
@Composable
private fun StaffKpiBand(
    state: BankStaffState,
    totals: BankStaffTotals,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        // The same tile the member list uses, which is the component the design intends for "this
        // figure is the sum of the screen" — orange leading bar, muted uppercase label, tabular
        // value.
        KrtTotalTile(
            label = stringResource(R.string.bank_staff_kpi_label_plain),
            value = formatAmount(totals.totalBalance.orEmpty()),
            unit = stringResource(R.string.bank_total_unit),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = state.countsLine(totals),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(horizontal = KrtSpacing.s4).testTag(BANK_STAFF_COUNTS_TAG),
        )
    }
}

/**
 * The sentence under the KPI: how many accounts, how many closed, how many requests are open.
 *
 * When the queue was too long to walk to the end, the request count is prefixed rather than shown
 * bare — a floor stated as a floor, not a total that happens to be wrong (ADR-0104).
 *
 * @param totals the strip's figures.
 * @return the assembled line.
 */
@Composable
private fun BankStaffState.countsLine(totals: BankStaffTotals): String {
    val separator = stringResource(R.string.bank_staff_counts_separator)
    val accounts = (totals.activeAccounts + totals.closedAccounts).toInt()
    val parts =
        buildList {
            add(pluralStringResource(R.plurals.bank_staff_accounts_count, accounts, accounts))
            if (totals.closedAccounts > 0) {
                val closed = totals.closedAccounts.toInt()
                add(pluralStringResource(R.plurals.bank_staff_counts_closed, closed, closed))
            }
            val requests =
                pluralStringResource(
                    R.plurals.bank_staff_counts_requests,
                    openRequestTotal,
                    openRequestTotal,
                )
            add(
                if (countsPartial) {
                    stringResource(R.string.bank_staff_counts_partial) + " " + requests
                } else {
                    requests
                },
            )
        }
    return parts.joinToString(separator)
}

/**
 * One account of the unit.
 *
 * @param row the account plus what the dashboard could not say about it.
 * @param management whether the caller sees beyond their own grants, which decides whether the
 *   "reached only through the office" mark can mean anything.
 * @param onClick the row was tapped.
 */
@Composable
private fun StaffAccountRow(
    row: BankStaffRow,
    management: Boolean,
    onClick: () -> Unit,
) {
    val closed = row.account.status == BankAccountStatus.CLOSED
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (closed) CLOSED_ROW_ALPHA else 1f),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
            ) {
                Text(
                    text = row.account.name,
                    style =
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = KrtPalette.White,
                )
                StaffAccountChips(row = row, closed = closed, management = management)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAmount(row.account.balance.orEmpty()),
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = KrtPalette.White,
                )
                row.account.delta30d?.let { delta ->
                    Text(
                        text =
                            stringResource(
                                R.string.bank_delta_30d,
                                formatSignedAmount(
                                    delta.trimStart('+', '-', '\u2212'),
                                    delta.isPositiveDelta(),
                                ),
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = deltaTone(delta),
                    )
                }
            }
            KrtIcon(
                id = DesignR.drawable.ic_krt_chevron_right,
                contentDescription = null,
                tint = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * What is worth saying about an account beyond its name.
 *
 * At most three chips, and each answers a different question: is everyone allowed to see it, does
 * it have work waiting, and am I seeing it only because of my office.
 *
 * @param row the account plus its annotations.
 * @param closed whether it is closed, which is stated instead of the rest.
 * @param management whether the caller sees beyond their own grants.
 */
@Composable
private fun StaffAccountChips(
    row: BankStaffRow,
    closed: Boolean,
    management: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        if (closed) {
            KrtChip(text = stringResource(R.string.bank_staff_chip_closed), tone = KrtChipTone.Muted)
            return@Row
        }
        if (row.account.type == CARTEL_TYPE) {
            KrtChip(text = stringResource(R.string.bank_staff_chip_public), tone = KrtChipTone.Muted)
        }
        if (row.openRequests > 0) {
            KrtChip(
                text =
                    pluralStringResource(
                        R.plurals.bank_staff_chip_requests,
                        row.openRequests,
                        row.openRequests,
                    ),
                tone = KrtChipTone.Warning,
            )
        }
        // Only management sees accounts beyond their own grants, so only there can a row be
        // one the caller reaches purely through their office. An employee's list is already
        // grant-shaped, and marking every row would say nothing.
        if (management && !row.viewable) {
            KrtChip(
                text = stringResource(R.string.bank_staff_chip_no_grant),
                tone = KrtChipTone.Data,
            )
        }
        Spacer(modifier = Modifier.weight(1f, fill = false))
    }
}

/** The account type the whole organisation may see (REQ-BANK-037). */
private const val CARTEL_TYPE = "CARTEL"

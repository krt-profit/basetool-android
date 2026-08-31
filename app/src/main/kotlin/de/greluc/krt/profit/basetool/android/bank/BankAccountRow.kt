/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.common.formatSignedAmount
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDataValue
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/**
 * The account as one **row**, which is what a tablet's list column gets.
 *
 * Name left, balance and delta right, and nothing else: the sparkline and the ledger are the
 * detail pane's, one column over. Chapter 02 §5's rule — „the tablet keeps tables, the phone
 * collapses to rows" — was extended to list rows in round 14 (S31), and this is the case it was
 * extended for.
 *
 * @param account the account.
 * @param onClick opens it in the pane.
 */
@Composable
internal fun AccountRow(
    account: BankAccountSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
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
        Column(horizontalAlignment = Alignment.End) {
            KrtDataValue(
                text = formatAmount(account.balance.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
            )
            account.delta30d?.let { delta ->
                Text(
                    text =
                        stringResource(
                            R.string.bank_delta_30d,
                            formatSignedAmount(delta.trimStart('+', '-', MINUS_CHAR), delta.isPositiveDelta()),
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = deltaTone(delta),
                )
            }
        }
    }
    KrtHairlineRule()
}

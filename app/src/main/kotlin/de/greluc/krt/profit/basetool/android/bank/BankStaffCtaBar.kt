/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Verwaltung's „Direktbuchung" entry. */
const val BANK_DIRECT_OPEN_TAG: String = "bank-direct-open"

/** Test handle for „Konto anlegen", the one CTA beside it that Bank-Management really does gate. */
const val BANK_CREATE_ACCOUNT_TAG: String = "bank-create-account"

/**
 * The Konten tab's two calls to action: direct booking and „Konto anlegen".
 *
 * Direct booking needs only `BANK_EMPLOYEE`, which this scope already requires; the per-account check
 * surfaces as a 403 in the sheet. „Konto anlegen" is locked without Bank-Management.
 *
 * @param management whether the server grants this caller Bank-Management.
 * @param onDirectBooking open the booking sheet.
 * @param onCreateAccount open the account-creation prompt; only reached with [management].
 * @param onLocked the caller tapped a control their role does not carry.
 * @param modifier layout modifier.
 */
@Composable
internal fun BankStaffCtaBar(
    management: Boolean,
    onDirectBooking: () -> Unit,
    onCreateAccount: () -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KrtBottomCtaBar(
        modifier =
            if (isWideWindow()) {
                modifier.padding(bottom = LocalKrtBottomBarInset.current)
            } else {
                modifier
            },
    ) {
        KrtGhostButton(
            text = stringResource(R.string.bank_direct_title),
            onClick = onDirectBooking,
            modifier = Modifier.weight(1f).testTag(BANK_DIRECT_OPEN_TAG),
            iconRes = DesignR.drawable.ic_krt_swap,
        )
        KrtCtaButton(
            text = stringResource(R.string.bank_lifecycle_create),
            onClick = { if (management) onCreateAccount() else onLocked() },
            modifier = Modifier.weight(1f).testTag(BANK_CREATE_ACCOUNT_TAG),
            iconRes =
                if (management) {
                    DesignR.drawable.ic_krt_plus
                } else {
                    DesignR.drawable.ic_krt_lock
                },
        )
    }
}

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
 * The Konten tab's two calls to action, and the asymmetry between them.
 *
 * Artboard 9 puts the direct booking here and nowhere in the member view. It carries **no** role
 * gate of its own: all four endpoints behind the sheet ask for `hasRole('BANK_EMPLOYEE')` and
 * nothing more (`BankBookingController`), and this whole scope is already unreachable without it —
 * the scope segment tests `bankEmployee`, which the server resolves through the hierarchy, so a
 * Bankleitung reaches it too.
 *
 * > Until 2026-09-03 this button was locked behind Bank-Management, which is the client inventing
 * > a stricter rule than the endpoint it calls: a plain Bankmitarbeiter could book directly in the
 * > web and was refused in the app. Artboard 9's state list asks for that lock („403 (Rolle
 * > Bank-Management fehlt)"); the endpoint is the authority and the artboard is wrong here.
 *
 * The per-account half of the server's rule (`canDeposit(#accountId, …)`) is decided on the write
 * and surfaces as a 403 the sheet shows. It cannot be pre-empted from here, because it is a fact
 * about the account picked inside.
 *
 * **„Konto anlegen" keeps its lock**, and that is the point of drawing them side by side: creating
 * an account is a management act and its endpoint gates on it, so the lock there states a real
 * rule rather than a copied one.
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

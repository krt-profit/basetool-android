/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BankBooking

/**
 * „Gebühr 2.250" — what a transfer itself cost, when it cost anything.
 *
 * The server sends `transferFee` on the ledger row and the app was dropping it, which left the
 * amount shown on the row and the amount that actually left the account differing by an
 * unexplained gap. The write side already states the fee before a transfer is sent; this is the
 * same fact afterwards.
 *
 * A zero fee draws nothing: most bookings carry none, and „Gebühr 0" on every one of them would be
 * noise that buries the rows where a fee was really charged.
 *
 * It lives in its own file rather than beside the ledger row because `BankScreen.kt` sits on
 * detekt's 30-functions-per-file ceiling, and pushing it over would have been a lint failure
 * rather than a design decision.
 *
 * @receiver the ledger row.
 * @return the line, or `null` when no fee was charged.
 */
@Composable
fun BankBooking.feeLine(): String? {
    val fee =
        transferFee
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it.toBigDecimalOrNull()?.signum() != 0 }
    return fee?.let { stringResource(R.string.bank_booking_fee, formatAmount(it)) }
}

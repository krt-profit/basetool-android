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
 * „Gebühr 2.250": the transfer fee a ledger row was charged, from its `transferFee`.
 *
 * A zero fee draws nothing.
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

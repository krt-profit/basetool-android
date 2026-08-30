/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.DirectBookingKind
import de.greluc.krt.profit.basetool.android.core.data.parseTypedDecimal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import java.math.BigDecimal

/** Test handle for the sheet. */
const val BANK_DIRECT_SHEET_TAG: String = "bank-direct-sheet"

/** Test handle for its CTA. */
const val BANK_DIRECT_CONFIRM_TAG: String = "bank-direct-confirm"

/** The three modes, in the order the segment draws them. */
private val MODES = listOf(DirectBookingKind.DEPOSIT, DirectBookingKind.WITHDRAWAL, DirectBookingKind.TRANSFER)

/**
 * „Direktbuchung" — design ch. 12 artboard 9.
 *
 * **One sheet, three modes**, not the web's three forms: they differ in one field each, and a
 * member picking between three screens would have to know which one they wanted before seeing any
 * of them.
 *
 * The **holder is required in all three**, which is the server's rule too — custody is kept per org
 * unit, so a balance without a holder is money nobody is accountable for. „Stand nach Buchung" and
 * the „no second approval" warning both stand **above** the CTA, because they are what distinguish
 * this from a request and a member has to read them before typing, not after.
 *
 * @param state what the sheet holds.
 * @param accounts the unit's accounts, for the two pickers.
 * @param holders the unit's holders.
 * @param onEdit a field changed.
 * @param onConfirm the CTA.
 * @param onDismiss the sheet was closed.
 */
@Composable
@Suppress("LongParameterList")
fun BankDirectBookingSheet(
    state: DirectBookingState,
    accounts: List<BankStaffAccount>,
    holders: List<BankHolder>,
    onEdit: ((DirectBookingState) -> DirectBookingState) -> Unit,
    onConfirm: (BigDecimal?) -> Unit,
    onDismiss: () -> Unit,
) {
    val source = accounts.firstOrNull { it.id == state.accountId }
    val balance = remember(source?.balance) { parseTypedDecimal(source?.balance) }
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.bank_direct_title),
        modifier = Modifier.testTag(BANK_DIRECT_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            KrtSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.bank_direct_deposit),
                        stringResource(R.string.bank_direct_withdrawal),
                        stringResource(R.string.bank_direct_transfer),
                    ),
                selectedIndex = MODES.indexOf(state.kind).coerceAtLeast(0),
                onSelect = { picked -> onEdit { it.copy(kind = MODES[picked]) } },
                modifier = Modifier.fillMaxWidth(),
            )
            AccountPicker(
                label = stringResource(R.string.bank_direct_account),
                accounts = accounts,
                selected = state.accountId,
                onSelect = { id -> onEdit { it.copy(accountId = id) } },
            )
            KrtTextField(
                value = state.amount,
                onValueChange = { value -> onEdit { it.copy(amount = value) } },
                label = stringResource(R.string.bank_direct_amount),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            // Validation-dimmed rather than locked, and said at the field: nothing here is
            // forbidden, the figure is simply larger than the account holds.
            if (state.kind == DirectBookingKind.WITHDRAWAL &&
                balance != null &&
                (state.figure ?: BigDecimal.ZERO) > balance
            ) {
                KrtFieldError(text = stringResource(R.string.bank_direct_over_balance))
            }
            HolderPicker(
                label = stringResource(R.string.bank_direct_holder),
                holders = holders,
                selected = state.holderId,
                onSelect = { id -> onEdit { it.copy(holderId = id) } },
            )
            KrtHint(explanation = stringResource(R.string.bank_direct_holder_hint))
            if (state.kind == DirectBookingKind.TRANSFER) {
                AccountPicker(
                    label = stringResource(R.string.bank_direct_target_account),
                    accounts = accounts.filterNot { it.id == state.accountId },
                    selected = state.destinationAccountId,
                    onSelect = { id -> onEdit { it.copy(destinationAccountId = id) } },
                )
                HolderPicker(
                    label = stringResource(R.string.bank_direct_target_holder),
                    holders = holders,
                    selected = state.destinationHolderId,
                    onSelect = { id -> onEdit { it.copy(destinationHolderId = id) } },
                )
            }
            KrtTextField(
                value = state.note,
                onValueChange = { value -> onEdit { it.copy(note = value) } },
                label = stringResource(R.string.bank_direct_note),
                modifier = Modifier.fillMaxWidth(),
            )
            KrtHint(explanation = stringResource(R.string.bank_direct_no_approval))
            state.preview(balance)?.let { after ->
                Text(
                    text = stringResource(R.string.bank_direct_preview, after.toPlainString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                )
            }
            state.error?.let { KrtFieldError(text = stringResource(R.string.bank_direct_failed)) }
            KrtCtaButton(
                text = stringResource(state.kind.ctaRes()),
                onClick = { onConfirm(balance) },
                enabled = state.submittable(balance),
                modifier = Modifier.fillMaxWidth().testTag(BANK_DIRECT_CONFIRM_TAG),
            )
            KrtGhostButton(
                text = stringResource(R.string.personal_inventory_cancel),
                onClick = onDismiss,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * What the CTA is called in this mode.
 *
 * @receiver the mode.
 * @return its label.
 */
private fun DirectBookingKind.ctaRes(): Int =
    when (this) {
        DirectBookingKind.DEPOSIT -> R.string.bank_direct_book_deposit
        DirectBookingKind.WITHDRAWAL -> R.string.bank_direct_book_withdrawal
        DirectBookingKind.TRANSFER -> R.string.bank_direct_book_transfer
    }

/**
 * One of the unit's accounts.
 *
 * @param label what the field is.
 * @param accounts what may be picked.
 * @param selected which is picked.
 * @param onSelect one was picked.
 */
@Composable
private fun AccountPicker(
    label: String,
    accounts: List<BankStaffAccount>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    KrtSelectField(
        value = accounts.firstOrNull { it.id == selected }?.name.orEmpty(),
        options = accounts.map { KrtOption(it.id, it.name) },
        onSelect = {
            open = false
            onSelect(it.value)
        },
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth(),
        label = label,
        selectedValue = selected,
    )
}

/**
 * One of the unit's holders.
 *
 * @param label what the field is.
 * @param holders who may be picked.
 * @param selected who is picked.
 * @param onSelect one was picked.
 */
@Composable
private fun HolderPicker(
    label: String,
    holders: List<BankHolder>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Only active holders: an inactive one is somebody who no longer keeps custody, and offering
    // them would set up a refusal the member cannot read off the list.
    val choices = holders.filter { it.active }
    KrtSelectField(
        value = choices.firstOrNull { it.id == selected }?.handle.orEmpty(),
        options = choices.map { KrtOption(it.id, it.handle) },
        onSelect = {
            open = false
            onSelect(it.value)
        },
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth(),
        label = label,
        selectedValue = selected,
    )
}

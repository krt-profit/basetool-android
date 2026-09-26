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
import de.greluc.krt.profit.basetool.android.core.data.BankGrantee
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.DirectBookingKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.parseTypedDecimal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
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
import de.greluc.krt.profit.basetool.android.ui.FieldLimits
import java.math.BigDecimal

/** Test handle for the sheet. */
const val BANK_DIRECT_SHEET_TAG: String = "bank-direct-sheet"

/** Test handle for its CTA. */
const val BANK_DIRECT_CONFIRM_TAG: String = "bank-direct-confirm"

/** The three modes, in the order the segment draws them. */
private val MODES = listOf(DirectBookingKind.DEPOSIT, DirectBookingKind.WITHDRAWAL, DirectBookingKind.TRANSFER)

/**
 * Shows what the in-game transfer fee does to this booking before it is made.
 *
 * By default the fee is on top: the recipient receives the typed amount and the account is debited
 * `amount + fee` (ADR-0052, REQ-BANK-033). Shown only where a fee applies, and marked as guidance,
 * since the server computes the binding fee.
 *
 * @param state the form.
 * @param onEdit how the toggle reports back.
 */
@Composable
private fun FeeBlock(
    state: DirectBookingState,
    onEdit: ((DirectBookingState) -> DirectBookingState) -> Unit,
) {
    val fee = state.fee ?: return
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        KrtCheckboxRow(
            checked = state.feeInclusive,
            onCheckedChange = { value -> onEdit { it.copy(feeInclusive = value) } },
            label = stringResource(R.string.bank_direct_fee_inclusive),
            enabled = !state.saving,
        )
        Text(
            text = stringResource(R.string.bank_direct_fee, fee.toPlainString()),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        state.debited?.let { gross ->
            Text(
                text = stringResource(R.string.bank_direct_fee_debited, gross.toPlainString()),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
        }
        state.arrives?.let { net ->
            Text(
                text = stringResource(R.string.bank_direct_fee_arrives, net.toPlainString()),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
        }
        KrtHint(explanation = stringResource(R.string.bank_direct_fee_hint))
    }
}

/**
 * Who the money came from or went to (REQ-BANK-044); not shown on a transfer.
 *
 * A counterparty is either a registered member picked from `/users/search-bank` or a typed external
 * name; only the one the toggle selects is sent. The org unit is chosen independently.
 *
 * @param state the form.
 * @param options what the picker currently offers.
 * @param query what has been typed into the picker.
 * @param orgUnits every active unit of either kind.
 * @param onQuery the picker's text changed.
 * @param onEdit how the controls report back.
 */
@Composable
@Suppress("LongParameterList")
private fun CounterpartyBlock(
    state: DirectBookingState,
    options: List<BankGrantee>,
    query: String,
    orgUnits: List<OrgUnit>,
    onQuery: (String) -> Unit,
    onEdit: ((DirectBookingState) -> DirectBookingState) -> Unit,
) {
    if (!state.counterpartyApplies) {
        return
    }
    var expanded by remember { mutableStateOf(false) }
    var unitOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        Text(
            text =
                stringResource(
                    if (state.kind == DirectBookingKind.DEPOSIT) {
                        R.string.bank_direct_counterparty_depositor
                    } else {
                        R.string.bank_direct_counterparty_recipient
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        KrtCheckboxRow(
            checked = state.counterpartyExternal,
            onCheckedChange = { value ->
                onEdit {
                    it.copy(
                        counterpartyExternal = value,
                        counterpartyUserId = null,
                        counterpartyExternalName = "",
                    )
                }
            },
            label = stringResource(R.string.bank_direct_counterparty_external),
            enabled = !state.saving,
        )
        if (state.counterpartyExternal) {
            KrtTextField(
                value = state.counterpartyExternalName,
                onValueChange = { value ->
                    onEdit { it.copy(counterpartyExternalName = value.take(FieldLimits.COUNTERPARTY_NAME)) }
                },
                label = stringResource(R.string.bank_direct_counterparty_name),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            KrtCombobox(
                query = query,
                onQueryChange = {
                    expanded = true
                    onQuery(it)
                },
                options = options.map { KrtOption(value = it.id, label = it.handle) },
                onSelect = { option ->
                    expanded = false
                    onQuery(option.label)
                    onEdit { it.copy(counterpartyUserId = option.value) }
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_direct_counterparty_person),
                placeholder = stringResource(R.string.bank_direct_counterparty_placeholder),
                selectedValue = state.counterpartyUserId,
                enabled = !state.saving,
            )
        }
        KrtSelectField(
            value = orgUnits.firstOrNull { it.id == state.counterpartyOrgUnitId }?.name.orEmpty(),
            options = orgUnits.map { KrtOption(value = it.id, label = it.name) },
            onSelect = { option ->
                unitOpen = false
                onEdit { it.copy(counterpartyOrgUnitId = option.value) }
            },
            expanded = unitOpen,
            onExpandedChange = { unitOpen = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.bank_direct_counterparty_unit),
            selectedValue = state.counterpartyOrgUnitId,
            enabled = !state.saving,
        )
        KrtHint(explanation = stringResource(R.string.bank_direct_counterparty_hint))
    }
}

/**
 * Spreads a deposit across the squadron accounts.
 *
 * Deposit only. The toggle and the percentage are set and cleared together, since the server refuses
 * one without the other. The preview rounds half-up on the share and takes the remainder by
 * subtraction, matching the web.
 *
 * @param state the form.
 * @param onEdit how the controls report back.
 */
@Composable
private fun SplitBlock(
    state: DirectBookingState,
    onEdit: ((DirectBookingState) -> DirectBookingState) -> Unit,
) {
    if (!state.splitApplies) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
        KrtCheckboxRow(
            checked = state.splitEnabled,
            onCheckedChange = { value ->
                onEdit { it.copy(splitEnabled = value, splitPercent = if (value) it.splitPercent else "") }
            },
            label = stringResource(R.string.bank_direct_split),
            enabled = !state.saving,
        )
        if (state.splitEnabled) {
            KrtTextField(
                value = state.splitPercent,
                onValueChange = { value -> onEdit { it.copy(splitPercent = value) } },
                label = stringResource(R.string.bank_direct_split_percent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.splitPercent.isNotBlank() && !state.splitValid) {
                KrtFieldError(text = stringResource(R.string.bank_direct_split_range))
            }
            state.splitPreview?.let { (share, rest) ->
                Text(
                    text =
                        stringResource(
                            R.string.bank_direct_split_preview,
                            share.toPlainString(),
                            rest.toPlainString(),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                )
            }
            KrtHint(explanation = stringResource(R.string.bank_direct_split_hint))
        }
    }
}

/**
 * „Direktbuchung": one sheet for deposit, withdrawal and transfer.
 *
 * A holder is required in every mode. „Stand nach Buchung" and the „no second approval" warning
 * stand above the confirm button.
 *
 * @param state what the sheet holds.
 * @param accounts the unit's accounts, for the two pickers.
 * @param holders the unit's holders.
 * @param onEdit a field changed.
 * @param onConfirm the CTA.
 * @param onDismiss the sheet was closed.
 * @param counterpartyOptions what the counterparty picker offers.
 * @param counterpartyQuery what has been typed into it.
 * @param orgUnitOptions every active org unit of either kind.
 * @param onCounterpartyQuery the counterparty picker's text changed.
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
    counterpartyOptions: List<BankGrantee> = emptyList(),
    counterpartyQuery: String = "",
    orgUnitOptions: List<OrgUnit> = emptyList(),
    onCounterpartyQuery: (String) -> Unit = {},
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
                stretch = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AccountPicker(
                label = stringResource(R.string.bank_direct_account),
                accounts = accounts,
                selected = state.accountId,
                onSelect = { id ->
                    val picked = accounts.firstOrNull { account -> account.id == id }
                    onEdit { it.copy(accountId = id, accountType = picked?.type) }
                },
            )
            KrtTextField(
                value = state.amount,
                onValueChange = { value -> onEdit { it.copy(amount = value) } },
                label = stringResource(R.string.bank_direct_amount),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
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
            CounterpartyBlock(
                state = state,
                options = counterpartyOptions,
                query = counterpartyQuery,
                orgUnits = orgUnitOptions,
                onQuery = onCounterpartyQuery,
                onEdit = onEdit,
            )
            SplitBlock(state = state, onEdit = onEdit)
            if (state.kind != DirectBookingKind.DEPOSIT) {
                KrtTextField(
                    value = state.justification,
                    onValueChange = { value ->
                        onEdit { it.copy(justification = value.take(FieldLimits.NOTE)) }
                    },
                    label = stringResource(R.string.bank_direct_justification),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.justificationRequired && state.justification.isBlank()) {
                    KrtFieldError(text = stringResource(R.string.bank_direct_justification_required))
                }
                KrtHint(explanation = stringResource(R.string.bank_direct_justification_hint))
            }
            KrtTextField(
                value = state.note,
                onValueChange = { value -> onEdit { it.copy(note = value.take(FieldLimits.NOTE)) } },
                label = stringResource(R.string.bank_direct_note),
                modifier = Modifier.fillMaxWidth(),
            )
            KrtTextField(
                value = state.staffNote,
                onValueChange = { value ->
                    onEdit { it.copy(staffNote = value.take(FieldLimits.NOTE)) }
                },
                label = stringResource(R.string.bank_direct_staff_note),
                modifier = Modifier.fillMaxWidth(),
            )
            KrtHint(explanation = stringResource(R.string.bank_direct_staff_note_hint))
            KrtHint(explanation = stringResource(R.string.bank_direct_no_approval))
            FeeBlock(state = state, onEdit = onEdit)
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

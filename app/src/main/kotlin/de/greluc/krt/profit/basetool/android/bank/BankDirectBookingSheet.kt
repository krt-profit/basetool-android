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
 * What the in-game transfer fee does to this booking, before it is made.
 *
 * The default is **on top**: the figure the member typed is what the recipient must receive, and
 * the account is debited `amount + fee` (ADR-0052, REQ-BANK-033). The app used to send neither the
 * flag nor any word about the fee, so a member typing 100 000 watched more than 100 000 leave the
 * account with nothing on screen having said so — and „Stand nach Buchung" beneath it showed the
 * balance the account would have had **without** the fee.
 *
 * Shown only where a fee applies. Guidance only: the authoritative fee is computed server-side at
 * booking time, and the block says so rather than implying this figure is binding.
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
 * Who the money came from, or who it went to (REQ-BANK-044).
 *
 * Not on a transfer: `BankTransferRequest` carries no counterparty at all, because both sides of a
 * transfer are accounts of the same unit and are already named by the two account fields.
 *
 * **Two identities, one of them.** A counterparty either holds a tool account — and is then picked
 * from `/users/search-bank`, the same list the web's picker uses — or does not, and is then typed
 * as a name. The toggle chooses; sending both would leave the server to guess which the member
 * meant, so the view model sends exactly the one the toggle points at.
 *
 * The **unit** is independent of that choice: a registered member can be acting for a unit, and an
 * external party can belong to one.
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
            // Switching identity clears the other one rather than leaving it in the state: a name
            // typed under one mode and an id picked under the other are two answers to a question
            // that takes one.
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
 * Spreading a deposit across the squadron accounts.
 *
 * Deposit only, and the two halves are one control: `BankDepositRequest` carries an
 * `@AssertTrue` refusing a split without a percentage and a percentage without a split. That rule
 * is `@Schema(hidden = true)`, so it reaches no generated client and no contract test — which is
 * exactly why the toggle and the field are drawn and cleared together here rather than left to
 * agree by habit.
 *
 * The preview rounds the way the web rounds: half-up on the share, remainder by subtraction, so
 * the two figures always add back to the deposit and the two clients cannot show different totals
 * for the same booking.
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
            // Clearing the percentage with the toggle is the rule, not tidiness: a percentage left
            // behind on an unticked toggle is exactly what the server refuses.
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
                // Stretched: a fixed 52 dp segment is narrower than any of these labels, and
                // the control is one row high, so they wrapped instead of fitting.
                stretch = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AccountPicker(
                label = stringResource(R.string.bank_direct_account),
                accounts = accounts,
                selected = state.accountId,
                // The kind travels with the id, because the justification rule turns on it and the
                // state is where that rule is enforced. Re-deriving it at submit time would put
                // the rule in a second place, which is how the two drift apart.
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
                // Said at the field rather than only on the CTA: the requirement belongs to the
                // ACCOUNT, so a member who has typed everything else needs to know why this one
                // is asking and the next one did not.
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

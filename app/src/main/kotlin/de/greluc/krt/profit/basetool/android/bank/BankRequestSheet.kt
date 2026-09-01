/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The sheet, for the tests that drive it. */
const val BANK_REQUEST_SHEET_TAG: String = "bank-request-sheet"

/** The approval line under the amount, which is the artboard's live explanation of the threshold. */
const val BANK_REQUEST_LIMIT_TAG: String = "bank-request-limit"

/** The amount field: 56 dp tall and 22 sp, a step above every other field on the sheet. */
private val AMOUNT_FIELD_HEIGHT = 56.dp

/** The amount's own type size, measured off artboard 3. */
private val AMOUNT_TEXT_SIZE = 22.sp

/**
 * How the footer splits, measured off artboard 3 (154 dp beside 200 dp).
 *
 * An even split wrapped „ANTRAG EINREICHEN" onto two lines on a 411 dp phone — found on a
 * device, not in a preview.
 */
private const val CANCEL_WEIGHT = 0.44f

/** The confirming half of that split. */
private const val SUBMIT_WEIGHT = 0.56f

/**
 * What the request sheet reports back.
 *
 * @property onKind a different movement was chosen.
 * @property onAccount the source (or, for a deposit, the destination) account was chosen.
 * @property onTarget a transfer's destination was chosen.
 * @property onAmount the amount was typed.
 * @property onNote the purpose was typed.
 * @property onSubmit the request was sent.
 * @property onDismiss the sheet was closed.
 */
data class BankRequestSheetActions(
    val onKind: (BankRequestKind) -> Unit,
    val onAccount: (String) -> Unit,
    val onTarget: (String) -> Unit,
    val onAmount: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The colour an amount is stated in, by what the movement does to the account.
 *
 * A transfer stays neutral on purpose: at the level the requester is looking at, the money leaves
 * one org-unit account and lands in another, so painting it red would claim a loss the
 * organisation does not make.
 *
 * @param kind the movement.
 * @return the tint for that reading.
 */
private fun amountTone(kind: BankRequestKind): Color =
    when (kind) {
        BankRequestKind.DEPOSIT -> KrtPalette.SuccessText
        BankRequestKind.WITHDRAWAL -> KrtPalette.DangerText
        BankRequestKind.TRANSFER -> KrtPalette.Gray1
    }

/**
 * The approval line under the amount, or `null` when there is nothing true to say.
 *
 * Three rules decide this, and all three come from the server rather than from a constant:
 *
 * - **A deposit is never approval-limited** (REQ-BANK-042), so it gets no line at all. The web
 *   frontend says the same in its own words: „Einzahlungen sind für jedes aktive Konto möglich –
 *   ohne Freigabe-Limit."
 * - **A caller the account exempts** (`approvalExempt`) gets no line either — a threshold that
 *   will never apply to them is noise.
 * - Otherwise the line states the account's own `applicableLimit`, and flips from *what will
 *   happen* to *what is now the case* as the typed amount crosses it.
 *
 * There is **no approval count** in any of this. The API models one approval, granted by one
 * class of approver (`requiredApprover`); for the KRT account that class escalates with the
 * amount (REQ-BANK-047), but the number of approvals never does.
 *
 * @param kind the movement being requested.
 * @param amount the amount as typed.
 * @param account the account the money moves on, or `null` before one is picked.
 * @return the sentence to show, or `null`.
 */
@Composable
private fun approvalLine(
    kind: BankRequestKind,
    amount: String,
    account: BankAccountSummary?,
): String? {
    val limit =
        account
            ?.takeIf { kind != BankRequestKind.DEPOSIT && !it.approvalExempt }
            ?.approvalLimit
    val threshold = limit?.toDoubleOrNull() ?: return null
    val shown = formatAmount(limit)
    return if ((amount.toDoubleOrNull() ?: 0.0) > threshold) {
        stringResource(R.string.bank_request_limit_above, shown)
    } else {
        stringResource(R.string.bank_request_limit_below, shown)
    }
}

/**
 * Raises or corrects a booking request — design chapter 12, artboard 3.
 *
 * The account picker's **label** changes with the movement, because the same control means
 * different things: for a deposit the account is where the money lands („Zielkonto"), for the
 * other two it is where the money comes from („Konto"), and a transfer needs both.
 *
 * @param state what the sheet holds.
 * @param accounts the accounts that may be picked.
 * @param targets where a transfer may go.
 * @param online whether the submit may be sent at all.
 * @param actions what the sheet reports back.
 */
@Composable
fun BankRequestSheet(
    state: BankRequestDraftState,
    accounts: List<BankAccountSummary>,
    targets: List<BankTransferTargetOption>,
    online: Boolean,
    actions: BankRequestSheetActions,
) {
    val editing = state.editing != null
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title =
            stringResource(
                if (editing) R.string.bank_request_sheet_edit_title else R.string.bank_request_sheet_title,
            ),
        modifier = Modifier.testTag(BANK_REQUEST_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
        ) {
            KindField(kind = state.kind, enabled = !editing, onKind = actions.onKind)
            AccountFields(
                state = state,
                accounts = accounts,
                targets = targets,
                editing = editing,
                actions = actions,
            )
            AmountField(state = state, accounts = accounts, onAmount = actions.onAmount)
            NoteField(note = state.note, onNote = actions.onNote)
            SubmitBar(state = state, online = online, editing = editing, actions = actions)
        }
    }
}

/**
 * The movement picker.
 *
 * Locked while an existing request is being corrected: the server refuses a change of kind, so an
 * enabled control here would offer an edit that always comes back as a 400.
 *
 * @param kind the current movement.
 * @param enabled whether it may be changed.
 * @param onKind reports a change.
 */
@Composable
private fun KindField(
    kind: BankRequestKind,
    enabled: Boolean,
    onKind: (BankRequestKind) -> Unit,
) {
    val kinds = BankRequestKind.entries
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        KrtFieldLabel(stringResource(R.string.bank_request_field_kind))
        KrtSegmentedControl(
            options = kinds.map { stringResource(it.labelRes()) },
            selectedIndex = kinds.indexOf(kind),
            onSelect = { onKind(kinds[it]) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            stretch = true,
            icons =
                listOf(
                    DesignR.drawable.ic_krt_bank_in,
                    DesignR.drawable.ic_krt_bank_out,
                    DesignR.drawable.ic_krt_swap,
                ),
        )
    }
}

/**
 * The one or two account pickers.
 *
 * @param state what the sheet holds.
 * @param accounts the accounts that may be picked.
 * @param targets where a transfer may go.
 * @param editing whether an existing request is being corrected, which locks the source account.
 * @param actions what the sheet reports back.
 */
@Composable
private fun AccountFields(
    state: BankRequestDraftState,
    accounts: List<BankAccountSummary>,
    targets: List<BankTransferTargetOption>,
    editing: Boolean,
    actions: BankRequestSheetActions,
) {
    var accountOpen by remember { mutableStateOf(false) }
    var targetOpen by remember { mutableStateOf(false) }
    val accountLabel =
        stringResource(
            if (state.kind == BankRequestKind.DEPOSIT) {
                R.string.bank_request_field_target
            } else {
                R.string.bank_request_field_account
            },
        )
    // A deposit lands on any active account (REQ-BANK-042); the other two need the request
    // grant the server reports per account, so offering them all would build a form the server
    // refuses on submit.
    val eligible =
        if (state.kind == BankRequestKind.DEPOSIT) accounts else accounts.filter { it.canRequest }
    val options =
        eligible.map { account ->
            KrtOption(
                value = account.id,
                label =
                    stringResource(
                        R.string.bank_request_account_option,
                        account.name,
                        formatAmount(account.balance.orEmpty()),
                    ),
            )
        }
    KrtSelectField(
        value = options.firstOrNull { it.value == state.accountId }?.label.orEmpty(),
        options = options,
        onSelect = { actions.onAccount(it.value) },
        expanded = accountOpen,
        onExpandedChange = { accountOpen = it },
        modifier = Modifier.fillMaxWidth(),
        label = accountLabel,
        selectedValue = state.accountId,
        enabled = !editing,
    )
    if (state.kind == BankRequestKind.TRANSFER) {
        val targetOptions = targets.map { KrtOption(value = it.id, label = it.label) }
        KrtSelectField(
            value = targetOptions.firstOrNull { it.value == state.targetAccountId }?.label.orEmpty(),
            options = targetOptions,
            onSelect = { actions.onTarget(it.value) },
            expanded = targetOpen,
            onExpandedChange = { targetOpen = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.bank_request_field_target),
            selectedValue = state.targetAccountId,
        )
    }
}

/**
 * The amount, and the approval line the artboard puts live underneath it.
 *
 * @param state what the sheet holds.
 * @param accounts the accounts, so the picked one's limit can be read.
 * @param onAmount reports a change.
 */
@Composable
private fun AmountField(
    state: BankRequestDraftState,
    accounts: List<BankAccountSummary>,
    onAmount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        KrtTextField(
            value = state.amount,
            onValueChange = onAmount,
            modifier = Modifier.fillMaxWidth().height(AMOUNT_FIELD_HEIGHT),
            label = stringResource(R.string.bank_request_field_amount),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textAlign = TextAlign.End,
            tabularFigures = true,
            valueStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = AMOUNT_TEXT_SIZE,
                    fontWeight = FontWeight.Bold,
                    color = amountTone(state.kind),
                ),
        )
        approvalLine(
            kind = state.kind,
            amount = state.amount,
            account = accounts.firstOrNull { it.id == state.accountId },
        )?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.InfoText,
                modifier = Modifier.testTag(BANK_REQUEST_LIMIT_TAG),
            )
        }
    }
}

/**
 * The purpose.
 *
 * @param note what has been typed.
 * @param onNote reports a change.
 */
@Composable
private fun NoteField(
    note: String,
    onNote: (String) -> Unit,
) {
    KrtTextField(
        value = note,
        onValueChange = onNote,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.bank_request_field_note),
        placeholder = stringResource(R.string.bank_request_note_placeholder),
    )
}

/**
 * The sheet's footer, plus what the last attempt refused with.
 *
 * @param state what the sheet holds.
 * @param online whether the submit may be sent.
 * @param editing whether this is a correction, which renames the confirming button.
 * @param actions what the sheet reports back.
 */
@Composable
private fun SubmitBar(
    state: BankRequestDraftState,
    online: Boolean,
    editing: Boolean,
    actions: BankRequestSheetActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        if (!online) {
            Text(
                text = stringResource(R.string.bank_request_offline),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        state.error?.let { error ->
            Text(
                text = bankRequestErrorMessage(error),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.DangerText,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s4),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            KrtGhostButton(
                text = stringResource(R.string.bank_request_cancel),
                onClick = actions.onDismiss,
                modifier = Modifier.weight(CANCEL_WEIGHT),
            )
            KrtCtaButton(
                text =
                    stringResource(
                        if (editing) R.string.bank_request_save else R.string.bank_request_submit,
                    ),
                onClick = actions.onSubmit,
                modifier = Modifier.weight(SUBMIT_WEIGHT),
                enabled = online && state.submittable && !state.saving,
                iconRes = DesignR.drawable.ic_krt_save,
            )
        }
    }
}

/**
 * What a refused write reads as.
 *
 * A 409 is its own sentence rather than a generic failure: somebody approved or booked the
 * request while the sheet was open, and the member has to re-read it rather than try again.
 *
 * @param error what the server refused with.
 * @return the sentence to show under the form.
 */
@Composable
internal fun bankRequestErrorMessage(error: ApiError): String =
    stringResource(
        when (error) {
            is ApiError.Forbidden -> R.string.bank_account_error_forbidden_message
            is ApiError.Validation -> R.string.bank_request_error_amount
            is ApiError.OptimisticLock -> R.string.conflict_inline
            is ApiError.Conflict -> R.string.refused_inline
            else -> R.string.write_failed
        },
    )

/**
 * The label a movement carries in the segmented control and on a row's chip.
 *
 * @return the string resource for that movement.
 */
@Suppress("ktlint:standard:function-naming")
internal fun BankRequestKind.labelRes(): Int =
    when (this) {
        BankRequestKind.DEPOSIT -> R.string.bank_request_kind_deposit
        BankRequestKind.WITHDRAWAL -> R.string.bank_request_kind_withdrawal
        BankRequestKind.TRANSFER -> R.string.bank_request_kind_transfer
    }

/**
 * Where a transfer may go, as the sheet needs it.
 *
 * A thin mirror of the data layer's own type so the composable does not have to import it, which
 * keeps this file previewable without the network module on the classpath.
 *
 * @property id the account.
 * @property label how it reads in the picker.
 */
data class BankTransferTargetOption(
    val id: String,
    val label: String,
)

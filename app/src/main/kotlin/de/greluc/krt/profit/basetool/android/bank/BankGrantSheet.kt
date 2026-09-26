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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BankGrantee
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.PickerOverflowNote

/** The create sheet, for the tests that open it. */
const val BANK_GRANT_SHEET_TAG: String = "bank-grant-sheet"

/**
 * What the create sheet reports back.
 *
 * @property onQuery the member picker's text changed.
 * @property onSelect a member was picked.
 * @property onDraftChanged a capability flag was flipped.
 * @property onCreate the grant is to be created.
 * @property onDismiss the sheet is to close.
 */
data class BankGrantSheetActions(
    val onQuery: (String) -> Unit,
    val onSelect: (BankGrantee) -> Unit,
    val onDraftChanged: (BankGranteeDraft) -> Unit,
    val onCreate: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „+ Grant hinzufügen": creates a bank grant on one account.
 *
 * The picker searches every member, so a pick without the Bank Employee role is possible and is
 * refused with `BANK_GRANTEE_MISSING_ROLE`; the sheet says so. All three flags may stay off, which
 * grants sight without booking rights.
 *
 * @param draft what the sheet holds.
 * @param accountName the account the grant will be on.
 * @param saving whether the creation is in flight.
 * @param error what the last attempt was refused with, or `null`.
 * @param actions what the sheet reports back.
 */
@Composable
fun BankGrantSheet(
    draft: BankGranteeDraft,
    accountName: String,
    saving: Boolean,
    error: ApiError?,
    actions: BankGrantSheetActions,
) {
    var expanded by remember { mutableStateOf(false) }
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.bank_grants_add_title),
        modifier = Modifier.testTag(BANK_GRANT_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            Text(
                text = stringResource(R.string.bank_grants_add_account, accountName),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtCombobox(
                query = draft.query,
                onQueryChange = {
                    expanded = true
                    actions.onQuery(it)
                },
                options = draft.options.map { KrtOption(value = it.id, label = it.handle) },
                onSelect = { option ->
                    expanded = false
                    actions.onSelect(BankGrantee(id = option.value, handle = option.label))
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_grants_add_member),
                placeholder = stringResource(R.string.bank_grants_add_member_placeholder),
                selectedValue = draft.selected?.id,
            )
            PickerOverflowNote(more = draft.moreOptions)
            Text(
                text = stringResource(R.string.bank_grants_add_flags_note),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtCheckboxRow(
                label = stringResource(R.string.bank_grants_deposit),
                checked = draft.canDeposit,
                onCheckedChange = { actions.onDraftChanged(draft.copy(canDeposit = it)) },
            )
            KrtCheckboxRow(
                label = stringResource(R.string.bank_grants_withdraw),
                checked = draft.canWithdraw,
                onCheckedChange = { actions.onDraftChanged(draft.copy(canWithdraw = it)) },
            )
            KrtCheckboxRow(
                label = stringResource(R.string.bank_grants_transfer),
                checked = draft.canTransfer,
                onCheckedChange = { actions.onDraftChanged(draft.copy(canTransfer = it)) },
            )
            error?.let {
                Text(
                    text = bankGrantErrorMessage(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
            KrtCtaButton(
                text = stringResource(R.string.bank_grants_add_confirm),
                onClick = actions.onCreate,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.selected != null && !saving,
            )
        }
    }
}

/**
 * The message for a refused grant write, chosen by the RFC 7807 `code`.
 *
 * Distinguishes a grantee without the Bank Employee role from one already on the matrix.
 *
 * @param error what came back.
 * @return the message to show.
 */
@Composable
internal fun bankGrantErrorMessage(error: ApiError): String =
    when (error.problem?.code) {
        CODE_MISSING_ROLE -> stringResource(R.string.bank_grants_add_error_role)
        CODE_DUPLICATE -> stringResource(R.string.bank_grants_add_error_duplicate)
        else -> bankConflictMessage(error)
    }

/**
 * The message for a refused bank write, chosen by the `BankConflictException` code.
 *
 * Each code the app can provoke gets its own sentence; other 409s fall through to the shared
 * concurrent-edit wording.
 *
 * @param error what came back.
 * @return the message to show.
 */
@Composable
internal fun bankConflictMessage(error: ApiError): String =
    when (error.problem?.code) {
        CODE_SELF_TRANSFER -> stringResource(R.string.bank_conflict_self_transfer)
        CODE_HOLDER_INACTIVE -> stringResource(R.string.bank_conflict_holder_inactive)
        CODE_HOLDER_OVERDRAFT -> stringResource(R.string.bank_conflict_holder_overdraft)
        CODE_ACCOUNT_CLOSED -> stringResource(R.string.bank_conflict_account_closed)
        CODE_OVERDRAFT -> stringResource(R.string.bank_conflict_overdraft)
        CODE_ALREADY_REVERSED -> stringResource(R.string.bank_conflict_already_reversed)
        else -> bankRequestErrorMessage(error)
    }

/** Source and destination of an Umbuchung are the same holder. */
private const val CODE_SELF_TRANSFER = "BANK_SELF_TRANSFER"

/** The destination holder may receive nothing new. */
private const val CODE_HOLDER_INACTIVE = "BANK_HOLDER_INACTIVE"

/** More was to be debited from a holder than the rules allow. */
private const val CODE_HOLDER_OVERDRAFT = "BANK_HOLDER_OVERDRAFT"

/** The account a leg needs — for an Umbuchung, the KRT account bearing the fee — is unusable. */
private const val CODE_ACCOUNT_CLOSED = "BANK_ACCOUNT_CLOSED"

/** An account would be driven negative. */
private const val CODE_OVERDRAFT = "BANK_OVERDRAFT"

/** The transaction already carries a counter-booking. */
private const val CODE_ALREADY_REVERSED = "BANK_ALREADY_REVERSED"

/** The grantee holds no Bank Employee role, which the server requires (REQ-BANK-008). */
private const val CODE_MISSING_ROLE = "BANK_GRANTEE_MISSING_ROLE"

/** The member already has an entry on this account. */
private const val CODE_DUPLICATE = "DUPLICATE_ENTITY"

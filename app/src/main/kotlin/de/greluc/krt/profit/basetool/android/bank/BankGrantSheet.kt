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
 * „+ Grant hinzufügen" — design chapter 12, artboard 7.
 *
 * **The picker searches every member, not only bank employees**, because the server's own search
 * does (`/users/search-bank` is `/users/search` with a widened role gate and nothing else). A member
 * without the Bank Employee role can therefore be picked, and the creation is then refused with
 * `BANK_GRANTEE_MISSING_ROLE`. The sheet says so plainly instead of hiding the possibility, which it
 * could only do by second-guessing the server's own list.
 *
 * All three flags may stay off: that is the deliberate „darf sehen, darf nichts buchen" entry, and
 * the sheet says as much rather than requiring a tick.
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
            modifier = Modifier.padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
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
                    // Built from the option itself rather than looked up in `draft.options`: the
                    // row already carries the id and the handle, and a lookup can only add a way
                    // to find nothing.
                    actions.onSelect(BankGrantee(id = option.value, handle = option.label))
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_grants_add_member),
                placeholder = stringResource(R.string.bank_grants_add_member_placeholder),
                selectedValue = draft.selected?.id,
            )
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
 * What to say about a refused grant write.
 *
 * Two conflicts are reachable from this sheet and they need different answers, so the RFC 7807
 * `code` decides rather than the bare 409: the picker searches every member, so the pick can be
 * someone who holds no Bank Employee role, and it can equally be someone already on the matrix.
 * "Pick someone else, and here is why" and "they are already listed" are not interchangeable.
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
 * What to say about a refused bank write.
 *
 * **A 409 from the bank is usually not an optimistic lock.** The shared wording answers every 409
 * with „gleichzeitig geändert", which is right for a stale version and wrong for all of
 * `BankConflictException`'s codes — a holder transfer refused because the fee-bearing KRT account is
 * missing was reported to the user as a concurrent edit, which sends them to reload a page that will
 * refuse again. Each code the app can provoke gets its own sentence; the rest still falls through.
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

/** The grantee holds no Bank Employee role, which the server requires (REQ-BANK-008). */
private const val CODE_MISSING_ROLE = "BANK_GRANTEE_MISSING_ROLE"

/** The member already has an entry on this account. */
private const val CODE_DUPLICATE = "DUPLICATE_ENTITY"

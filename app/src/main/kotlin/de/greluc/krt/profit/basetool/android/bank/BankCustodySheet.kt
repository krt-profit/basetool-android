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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/** The custody transfer sheet, for the tests that open it. */
const val BANK_CUSTODY_SHEET_TAG: String = "bank-custody-sheet"

/**
 * What the custody transfer sheet reports back.
 *
 * @property onDraftChanged the sheet's contents changed.
 * @property onConfirm the transfer is to be sent.
 * @property onDismiss the sheet is to close.
 */
data class BankCustodyActions(
    val onDraftChanged: (BankCustodyDraft) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „Halter-Umbuchung" — design chapter 12, artboard 8.
 *
 * The explanatory line is the web frontend's own, verbatim, and it carries the one fact that makes
 * the action safe to offer: it moves custody **between holders without touching an account**, and
 * the source may go negative. Softening either half would misrepresent what the server does.
 *
 * @param draft what the sheet holds.
 * @param peers the other active holders custody can move to.
 * @param saving whether the transfer is in flight.
 * @param error what the last attempt was refused with, or `null`.
 * @param actions what the sheet reports back.
 */
@Composable
fun BankCustodySheet(
    draft: BankCustodyDraft,
    peers: List<BankHolder>,
    saving: Boolean,
    error: ApiError?,
    actions: BankCustodyActions,
) {
    var expanded by remember { mutableStateOf(false) }
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.bank_holder_transfer_title),
        modifier = Modifier.testTag(BANK_CUSTODY_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            Text(
                text = stringResource(R.string.bank_holder_transfer_text),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.Gray1,
            )
            if (peers.isEmpty()) {
                // Without a second active holder the action cannot be completed at all. Saying so
                // beats a picker with nothing in it.
                Text(
                    text = stringResource(R.string.bank_holder_transfer_no_peers),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            } else {
                KrtSelectField(
                    value =
                        peers.firstOrNull { it.id == draft.destinationId }?.handle.orEmpty(),
                    options = peers.map { KrtOption(value = it.id, label = it.handle) },
                    onSelect = {
                        expanded = false
                        actions.onDraftChanged(draft.copy(destinationId = it.value))
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.bank_holder_transfer_target),
                    selectedValue = draft.destinationId,
                )
            }
            KrtTextField(
                value = draft.amount,
                onValueChange = { actions.onDraftChanged(draft.copy(amount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_holder_transfer_amount),
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                errorText = error?.let { bankConflictMessage(it) },
            )
            KrtTextField(
                value = draft.note,
                onValueChange = { actions.onDraftChanged(draft.copy(note = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bank_holder_transfer_note),
            )
            KrtCtaButton(
                text = stringResource(R.string.bank_holder_transfer_confirm),
                onClick = actions.onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.destinationId != null && draft.amount.isNotBlank() && !saving,
            )
        }
    }
}

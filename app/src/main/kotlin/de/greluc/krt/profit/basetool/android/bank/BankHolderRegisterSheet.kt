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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/** The holder registration sheet, for the tests that open it. */
const val BANK_HOLDER_REGISTER_TAG: String = "bank-holder-register-sheet"

/**
 * What the registration sheet reports back.
 *
 * @property onQuery the member picker's text changed.
 * @property onSelect a member was picked.
 * @property onConfirm the member is to be registered.
 * @property onDismiss the sheet is to close.
 */
data class BankHolderRegisterActions(
    val onQuery: (String) -> Unit,
    val onSelect: (BankGrantee) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „+ Halter registrieren" — the entry point for everything artboard 8 shows.
 *
 * Without a holder there is no custody to look at and no booking confirmation can name one, so this
 * is the first action of the register rather than an afterthought. The sheet states what a holder is
 * — and that custody is kept at org-unit level — because the word carries no such meaning on its
 * own.
 *
 * @param draft what the sheet holds.
 * @param saving whether the registration is in flight.
 * @param error what the last attempt was refused with, or `null`.
 * @param actions what the sheet reports back.
 */
@Composable
fun BankHolderRegisterSheet(
    draft: BankGranteeDraft,
    saving: Boolean,
    error: ApiError?,
    actions: BankHolderRegisterActions,
) {
    var expanded by remember { mutableStateOf(false) }
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.bank_holder_register_title),
        modifier = Modifier.testTag(BANK_HOLDER_REGISTER_TAG),
    ) {
        Column(
            modifier = Modifier.padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            Text(
                text = stringResource(R.string.bank_holder_register_text),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.Gray1,
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
            error?.let {
                Text(
                    text = bankGrantErrorMessage(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.DangerText,
                )
            }
            KrtCtaButton(
                text = stringResource(R.string.bank_holder_register_confirm),
                onClick = actions.onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.selected != null && !saving,
            )
        }
    }
}

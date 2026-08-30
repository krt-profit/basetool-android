/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.REFINERY_NOTE_LIMIT
import de.greluc.krt.profit.basetool.android.core.data.RefineryStoreLine
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/** The Einlagern form, for the tests that open it. */
const val REFINERY_STORE_FORM_TAG: String = "refinery-store-form"

/**
 * What the Einlagern form reports back.
 *
 * @property onLineChanged one line was edited.
 * @property onStoreAll every line is to be booked, in one call.
 * @property onDismiss the form is to close.
 */
data class RefineryStoreActions(
    val onLineChanged: (RefineryStoreLine) -> Unit,
    val onStoreAll: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „Einlagern" — design chapter 11, artboard 3.
 *
 * **The amount is the point.** Every line arrives at what the run computed and is meant to be
 * overridden; the handoff calls that override the reason the screen exists. The computed figure
 * stays visible above the field so a correction reads as one.
 *
 * **One submit for the run, not one per card.** The handoff has each card book and acknowledge on
 * its own. The server books whatever a call carries and then marks the order completed, refusing
 * every later call — so a per-card submit loses every material after the first, which is what a
 * device showed. The editing stays per line; only the sending is shared.
 *
 * @param lines the form.
 * @param busy whether the run is being booked right now.
 * @param error what the last attempt was refused with, or `null`.
 * @param actions what the form reports back.
 */
@Composable
fun RefineryStoreSheet(
    lines: List<RefineryStoreLine>,
    busy: Boolean,
    error: ApiError?,
    actions: RefineryStoreActions,
) {
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.refinery_store),
        modifier = Modifier.testTag(REFINERY_STORE_FORM_TAG),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            items(lines, key = { it.key }) { line ->
                StoreLineCard(line = line, actions = actions)
            }
            item(key = "submit") {
                Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                    error?.let {
                        Text(
                            text = stringResource(R.string.refinery_store_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = KrtPalette.DangerText,
                        )
                    }
                    KrtCtaButton(
                        text = stringResource(R.string.refinery_store_all),
                        onClick = actions.onStoreAll,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && lines.all { it.amount.isNotBlank() },
                    )
                }
            }
        }
    }
}

/**
 * One material's card.
 *
 * @param line what to draw.
 * @param actions what the card reports back.
 */
@Composable
private fun StoreLineCard(
    line: RefineryStoreLine,
    actions: RefineryStoreActions,
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            Text(
                text = line.materialName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
            )
            Text(
                text =
                    stringResource(
                        R.string.refinery_store_computed,
                        formatAmount(line.computed.toString()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = line.amount,
                onValueChange = { actions.onLineChanged(line.copy(amount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.refinery_store_amount),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            KrtCheckboxRow(
                label = stringResource(R.string.refinery_store_personal),
                checked = line.personal,
                // Personal and a job order exclude each other on the server (400). Clearing the
                // order here means the pair can never be sent, so the rule never arrives as a
                // mystery refusal.
                onCheckedChange = {
                    actions.onLineChanged(line.copy(personal = it, jobOrderId = null))
                },
            )
            Text(
                text = stringResource(R.string.refinery_store_personal_note),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = line.note,
                onValueChange = {
                    actions.onLineChanged(line.copy(note = it.take(REFINERY_NOTE_LIMIT)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.refinery_store_note),
                minLines = 2,
            )
        }
    }
}

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.REFINERY_NOTE_LIMIT
import de.greluc.krt.profit.basetool.android.core.data.RefineryStoreLine
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.isLogistician

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
    val onPickMember: (Int) -> Unit = {},
    val onMemberQuery: (String) -> Unit = {},
    val onMemberDismiss: () -> Unit = {},
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
 * @param memberPicker the receiver lookup, open for at most one line at a time.
 */
@Composable
fun RefineryStoreSheet(
    lines: List<RefineryStoreLine>,
    busy: Boolean,
    error: ApiError?,
    actions: RefineryStoreActions,
    memberPicker: RefineryMemberPickerState = RefineryMemberPickerState(),
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
            itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                StoreLineCard(
                    line = line,
                    index = index,
                    actions = actions,
                    memberPicker = memberPicker,
                )
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
    index: Int,
    actions: RefineryStoreActions,
    memberPicker: RefineryMemberPickerState,
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
            StoreReceiver(
                line = line,
                index = index,
                actions = actions,
                picker = memberPicker,
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

/**
 * Who this line's output is booked onto.
 *
 * **Two shapes, one rule.** A Logistician (through the hierarchy, so an admin and an officer too)
 * gets a roster picker; everyone else gets a disabled field naming themselves. That is the web
 * app's own conclusion, written down beside its combobox: offering a roster whose every foreign
 * choice answers 403 is worse than not offering one (REQ-SEC-039). The server refuses per line —
 * `canManageUserInventory(targetUserId)` — so this is a hint, not the gate.
 *
 * @param line the store line.
 * @param index its position, which is what the picker opens against.
 * @param actions what the form reports back.
 * @param picker the roster lookup's current state.
 */
@Composable
private fun StoreReceiver(
    line: RefineryStoreLine,
    index: Int,
    actions: RefineryStoreActions,
    picker: RefineryMemberPickerState,
) {
    if (!isLogistician()) {
        KrtTextField(
            value = stringResource(R.string.refinery_store_user_self),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.refinery_store_user),
            enabled = false,
        )
        Text(
            text = stringResource(R.string.refinery_store_user_locked),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    var expanded by remember(index) { mutableStateOf(false) }
    KrtCombobox(
        query = picker.query.ifEmpty { line.userName.orEmpty() },
        onQueryChange = {
            expanded = true
            if (picker.open != index) actions.onPickMember(index)
            actions.onMemberQuery(it)
        },
        options = picker.results.map { KrtOption(value = it.id, label = it.name) },
        onSelect = { option ->
            picker.results.firstOrNull { it.id == option.value }?.let { picked ->
                actions.onLineChanged(line.copy(userId = picked.id, userName = picked.name))
            }
            expanded = false
            actions.onMemberDismiss()
        },
        expanded = expanded && picker.open == index,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.refinery_store_user),
        placeholder = stringResource(R.string.refinery_store_user_search),
        // Stated when it bites and silent when it does not (ADR-0104).
        notice =
            when {
                picker.open != index -> null
                picker.loading -> stringResource(R.string.mission_member_searching)
                picker.more -> stringResource(R.string.picker_more_matches)
                else -> null
            },
    )
}

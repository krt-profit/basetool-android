/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the action that opens the item Übergabe. */
const val ORDER_ITEM_HANDOVER_OPEN_TAG: String = "order-item-handover-open"

/** Test handle for the sheet. */
const val ORDER_ITEM_HANDOVER_SHEET_TAG: String = "order-item-handover-sheet"

/** Test handle for its CTA. */
const val ORDER_ITEM_HANDOVER_SUBMIT_TAG: String = "order-item-handover-submit"

/**
 * What the item Übergabe sheet reports back.
 *
 * @property draft what is typed, or `null` when the sheet is closed.
 * @property onChange a field changed.
 * @property onSubmit send it.
 * @property onDismiss close it without sending.
 */
data class OrderItemHandoverActions(
    val draft: ItemHandoverDraft?,
    val onChange: ((ItemHandoverDraft) -> ItemHandoverDraft) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „Übergabe erfassen" for an item line — the write that finishes an item Auftrag.
 *
 * Design ch. 10's own note: „Getrennter Screen vom Material-Fall, weil die Einheit ‚Stück' ist …
 * dieselbe Form, andere Felder." The shape is the material handover's; the fields are counts, and
 * there is no stock row to book out because the units were built rather than fetched.
 *
 * > **The ceiling is what has been built, not what was ordered.** A unit can only be handed over
 * > once it has been manufactured, and the server refuses anything above the
 * > manufactured-but-undelivered count. The stepper stops there and the form says why.
 *
 * @param actions the draft and what it reports.
 */
@Composable
fun OrderItemHandoverSheet(actions: OrderItemHandoverActions) {
    val draft = actions.draft ?: return
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.order_handover_title),
        modifier = Modifier.testTag(ORDER_ITEM_HANDOVER_SHEET_TAG),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            Muted(
                text =
                    stringResource(
                        R.string.order_item_handover_subject,
                        draft.itemName,
                        draft.delivered,
                        draft.ordered,
                    ),
            )
            KrtStepperField(
                value = draft.amount,
                onValueChange = { v -> actions.onChange { it.copy(amount = v.filter { c -> c.isDigit() }) } },
                onDecrement = {
                    actions.onChange { it.copy(amount = ((it.units ?: 1) - 1).coerceAtLeast(1).toString()) }
                },
                onIncrement = {
                    actions.onChange {
                        it.copy(amount = ((it.units ?: 0) + 1).coerceAtMost(it.deliverable).toString())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.order_item_handover_amount),
                enabled = !draft.saving,
            )
            Muted(text = stringResource(R.string.order_item_handover_deliverable, draft.deliverable))
            if (draft.units != null && !draft.amountValid) {
                KrtFieldError(
                    text = stringResource(R.string.order_item_handover_too_many, draft.deliverable),
                )
            }
            Projection(draft = draft)
            KrtTextField(
                value = draft.recipient,
                onValueChange = { v -> actions.onChange { it.copy(recipient = v) } },
                label = stringResource(R.string.order_handover_recipient),
                enabled = !draft.saving,
            )
            Muted(text = stringResource(R.string.order_handover_append_only))
            draft.error?.let { ItemHandoverError(error = it) }
            KrtCtaButton(
                text = stringResource(R.string.order_handover_cta),
                onClick = actions.onSubmit,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(ORDER_ITEM_HANDOVER_SUBMIT_TAG),
                enabled = draft.submittable,
            )
        }
    }
}

/**
 * Where the line stands after this handover — the figure that closes an item Auftrag.
 *
 * @param draft what is typed.
 */
@Composable
private fun Projection(draft: ItemHandoverDraft) {
    val projected = draft.projected ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Muted(text = stringResource(R.string.order_handover_after), modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.order_production_after_value, projected, draft.ordered),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        if (draft.completes) {
            KrtChip(
                text = stringResource(R.string.order_handover_fulfils),
                tone = KrtChipTone.Success,
            )
        }
    }
}

/**
 * A muted line of prose.
 *
 * @param text what it says.
 * @param modifier layout modifier.
 */
@Composable
private fun Muted(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = modifier,
    )
}

/**
 * What the last write returned, in the app's own words.
 *
 * A `400` here is almost always the manufactured ceiling moving under the sheet — somebody else
 * handed the same units over first — so it says that rather than „ungültige Eingabe".
 *
 * @param error the refusal.
 */
@Composable
private fun ItemHandoverError(error: ApiError) {
    KrtFieldError(
        text =
            stringResource(
                when (error) {
                    is ApiError.Forbidden -> R.string.order_handover_not_allowed
                    is ApiError.Validation -> R.string.order_item_handover_rejected
                    else -> R.string.write_failed
                },
            ),
    )
}

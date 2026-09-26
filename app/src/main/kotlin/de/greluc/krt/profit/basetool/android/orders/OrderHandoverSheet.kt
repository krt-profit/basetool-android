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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.writeFailureText
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Übergabe sheet. */
const val ORDER_HANDOVER_SHEET_TAG: String = "order-handover-sheet"

/** Test handle for its CTA. */
const val ORDER_HANDOVER_SUBMIT_TAG: String = "order-handover-submit"

/** How the live preview's percentage is rendered. */
private const val PERCENT = 100

/**
 * What the Übergabe sheet reports back.
 *
 * @property draft what is typed, or `null` when the sheet is closed.
 * @property onChange a field changed.
 * @property onSubmit send it.
 * @property onDismiss close it without sending.
 */
data class OrderHandoverActions(
    val draft: OrderHandoverDraft?,
    val onChange: ((OrderHandoverDraft) -> OrderHandoverDraft) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * „Übergabe erfassen": records a material handover, the write that finishes an Auftrag.
 *
 * Shows a live preview of the line's progress after this handover, names the stock row the server
 * books out of, and is append-only. There is no „Ohne Lagerbezug erfassen" option, because the
 * endpoint requires `inventoryItemId`.
 *
 * @param actions the draft and what it reports.
 */
@Composable
fun OrderHandoverSheet(actions: OrderHandoverActions) {
    val draft = actions.draft ?: return
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.order_handover_title),
        modifier = Modifier.testTag(ORDER_HANDOVER_SHEET_TAG),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            Text(
                text =
                    stringResource(
                        R.string.order_handover_subject,
                        draft.materialName,
                        draft.alreadyDone.orEmpty(),
                        draft.needed.orEmpty(),
                        draft.unitWord(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = draft.amount,
                onValueChange = { v -> actions.onChange { it.copy(amount = v) } },
                label = stringResource(R.string.order_handover_amount, draft.unitWord()),
                enabled = !draft.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                tabularFigures = true,
            )
            Projection(draft)
            StockChoice(draft, actions)
            KrtTextField(
                value = draft.recipient,
                onValueChange = { v -> actions.onChange { it.copy(recipient = v) } },
                label = stringResource(R.string.order_handover_recipient),
                enabled = !draft.saving,
            )
            KrtTextField(
                value = draft.recipientSquadron,
                onValueChange = { v -> actions.onChange { it.copy(recipientSquadron = v) } },
                label = stringResource(R.string.order_handover_squadron),
                enabled = !draft.saving,
            )
            Text(
                text = stringResource(R.string.order_handover_append_only),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            draft.error?.let { HandoverError(error = it) }
            KrtCtaButton(
                text = stringResource(R.string.order_handover_cta),
                onClick = actions.onSubmit,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(ORDER_HANDOVER_SUBMIT_TAG),
                enabled = draft.submittable,
            )
        }
    }
}

/**
 * The position's own unit word, never a hardcoded SCU.
 *
 * @return the word to put after the figure, or an empty string when the server named no unit.
 */
@Composable
private fun OrderHandoverDraft.unitWord(): String =
    when (unit) {
        "PIECE" -> stringResource(R.string.materials_unit_piece)
        "SCU" -> stringResource(R.string.materials_unit_scu)
        else -> ""
    }

/**
 * Where the line stands after this handover — the number that finishes the Auftrag.
 *
 * @param draft what is typed.
 */
@Composable
private fun Projection(draft: OrderHandoverDraft) {
    val projected = draft.projected ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.order_handover_after),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(
                    R.string.order_handover_after_value,
                    draft.projectedAmount?.krtPlainAmount().orEmpty(),
                    draft.needed.orEmpty(),
                    draft.unitWord(),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        KrtChip(
            text =
                if (draft.completes) {
                    stringResource(R.string.order_handover_fulfils)
                } else {
                    "${(projected * PERCENT).toInt()} %"
                },
            tone = if (draft.completes) KrtChipTone.Success else KrtChipTone.Muted,
        )
    }
}

/**
 * Which stock row the handover books out of, as radio buttons over this order line's own rows.
 *
 * @param draft what is typed.
 * @param actions what it reports.
 */
@Composable
private fun StockChoice(
    draft: OrderHandoverDraft,
    actions: OrderHandoverActions,
) {
    KrtSectionTitle(text = stringResource(R.string.order_handover_stock))
    when {
        draft.loading -> {
            KrtSpinner()
        }

        draft.stock.isEmpty() -> {
            Text(
                text = stringResource(R.string.order_handover_no_stock),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }

        else -> {
            draft.stock.forEach { row ->
                KrtRadioRow(
                    selected = draft.stockId == row.id,
                    onSelect = { actions.onChange { it.copy(stockId = row.id) } },
                    label = row.krtLabel(),
                    enabled = !draft.saving,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.order_handover_stock_hint),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/**
 * One stock row, in the words the artboard uses: owner · place · quality · amount.
 *
 * @receiver the row.
 * @return its label.
 */
@Composable
private fun HandoverStockRow.krtLabel(): String =
    listOfNotNull(
        owner,
        location,
        quality?.let { stringResource(R.string.order_handover_quality, it) },
        amount.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/**
 * A quantity without scientific notation.
 *
 * @receiver the amount.
 * @return the plain decimal.
 */
private fun Double.krtPlainAmount(): String = java.math.BigDecimal(this.toString()).toPlainString()

/**
 * What the last write returned.
 *
 * A validation refusal is shown in the server's words; a `409` means the line was fulfilled while the
 * sheet was open and is shown as a conflict.
 *
 * @param error the refusal.
 */
@Composable
private fun HandoverError(error: ApiError) {
    KrtFieldError(
        text =
            error.writeFailureText(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
                    is ApiError.Conflict -> R.string.refused_inline
                    is ApiError.Forbidden -> R.string.order_handover_not_allowed
                    else -> R.string.write_failed
                },
            ),
    )
}

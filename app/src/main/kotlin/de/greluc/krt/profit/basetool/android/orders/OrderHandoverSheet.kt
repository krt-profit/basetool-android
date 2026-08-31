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
import de.greluc.krt.profit.basetool.android.ui.fieldMessage
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
 * „Übergabe erfassen" — the screen that lets an Auftrag be finished from the app.
 *
 * Design ch. 10 artboard 14, the round-8 parity programme's heaviest item: in the web the handover
 * is what closes an Auftrag, and until this existed the app could take one on and never end it.
 *
 * Three things the artboard insists on and this keeps:
 *
 * - **The live preview.** „Nach dieser Übergabe 300 / 400 · 75 %" — the figure that finishes an
 *   Auftrag is never formed in somebody's head. At 100 % the chip turns Success and says so.
 * - **The stock reference is the bridge into the Lager.** With a row, the server books it out;
 *   the field names that rather than leaving it to be discovered.
 * - **Append-only is written into the form, not into a modal afterwards.** Nothing here takes a
 *   handover back.
 *
 * > **One deviation, flagged not coded around.** The artboard offers „Ohne Lagerbezug erfassen".
 * > The endpoint cannot serve it — `inventoryItemId` is `@NotNull` — and the web's own form refuses
 * > to submit without a row. The option is absent; see the design gap list.
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
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = draft.amount,
                onValueChange = { v -> actions.onChange { it.copy(amount = v) } },
                label = stringResource(R.string.order_handover_amount),
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
            // In the form, not in a modal afterwards: a warning that arrives after the decision is
            // a warning nobody acted on.
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
 * Which stock row the handover books out of.
 *
 * Radios, not a combobox: the candidates are this order line's own rows and there are rarely more
 * than a handful, so a list that shows all of them beats one that has to be typed into.
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
    // The half of the field that says what choosing a row DOES — otherwise the booking-out is a
    // side effect discovered afterwards.
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
 * A validation refusal is shown in the server's own words: it names the field and the rule, which
 * is what design ch. 02 §6 draws under a field. A `409` keeps the sheet's own sentence — it means
 * the line was fulfilled while the sheet was open, the artboard's own case, and it reads as a
 * conflict rather than as a generic failure.
 *
 * @param error the refusal.
 */
@Composable
private fun HandoverError(error: ApiError) {
    KrtFieldError(
        text =
            error.fieldMessage() ?: stringResource(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
                    is ApiError.Forbidden -> R.string.order_handover_not_allowed
                    else -> R.string.write_failed
                },
            ),
    )
}

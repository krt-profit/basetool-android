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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the action that opens the Herstellung, on the item line it books against. */
const val ORDER_PRODUCTION_OPEN_TAG: String = "order-production-open"

/** Test handle for the Herstellung sheet. */
const val ORDER_PRODUCTION_SHEET_TAG: String = "order-production-sheet"

/** Test handle for its CTA. */
const val ORDER_PRODUCTION_SUBMIT_TAG: String = "order-production-submit"

/**
 * What the Herstellung sheet reports back.
 *
 * @property draft what is filled in, or `null` when the sheet is closed.
 * @property onAmount how many units this run produced.
 * @property onDraw how much comes off one stock row.
 * @property onSkip a material is consumed outside the tool, or no longer is.
 * @property onAutoFill fill one material's plan to its demand.
 * @property bookIn the Einlagerung section's own callbacks.
 * @property onSubmit book it.
 * @property onDismiss close it without booking.
 */
data class OrderProductionActions(
    val draft: ProductionDraft?,
    val onAmount: (String) -> Unit,
    val onDraw: (String, String, String) -> Unit,
    val onSkip: (String) -> Unit,
    val onAutoFill: (String) -> Unit,
    val bookIn: ProductionBookInActions,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * What the Einlagerung section reports back.
 *
 * @property onLocationQuery the place picker's search changed.
 * @property onLocation a place was picked.
 * @property onOwnerQuery the member picker's search changed.
 * @property onOwner a member was picked.
 * @property onOrgUnit a pool was picked.
 * @property onPersonal the personal flag was toggled.
 * @property onAllocate the earmark was toggled.
 */
data class ProductionBookInActions(
    val onLocationQuery: (String) -> Unit,
    val onLocation: (String, String) -> Unit,
    val onOwnerQuery: (String) -> Unit,
    val onOwner: (String?, String) -> Unit,
    val onOrgUnit: (String) -> Unit,
    val onPersonal: () -> Unit,
    val onAllocate: () -> Unit,
)

/**
 * „Herstellung erfassen" — the write that moves an item line's „hergestellt" figure.
 *
 * Design ch. 10 artboard 15. The artboard draws a smaller form than the endpoint can take, and the
 * three differences are deliberate and listed on the design gap list rather than coded around:
 *
 * - **Per material, not one switch.** The artboard offers a single „Zutaten aus dem Lager
 *   ausbuchen"; the server takes a plan over named stock rows that must cover each material's
 *   demand *exactly*, so the sheet carries the web's per-material „Nicht ausbuchen" instead.
 * - **The Einlagerung section is not in the artboard** and cannot be left out: produced units have
 *   to land somewhere, and `bookIn.locationId` is `@NotNull`.
 * - **„Verwendete Variante" and „Übergeben an" have no field here.** The variant is an order-level
 *   setting and handing over is the separate item-handover write.
 *
 * @param actions the draft and what it reports.
 */
@Composable
fun OrderProductionSheet(actions: OrderProductionActions) {
    val draft = actions.draft ?: return
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        title = stringResource(R.string.order_production_title),
        modifier = Modifier.testTag(ORDER_PRODUCTION_SHEET_TAG),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            Muted(
                text =
                    stringResource(
                        R.string.order_production_subject,
                        draft.itemName,
                        draft.manufactured,
                        draft.lineAmount,
                    ),
            )
            AmountField(draft = draft, onAmount = actions.onAmount)
            Projection(draft = draft)
            KrtSectionTitle(text = stringResource(R.string.order_production_consumption))
            KrtHint(explanation = stringResource(R.string.order_production_consumption_hint))
            draft.materials.forEach { material ->
                MaterialCard(draft = draft, material = material, actions = actions)
            }
            ProductionBookInSection(draft = draft, actions = actions.bookIn)
            draft.error?.let { ProductionError(error = it) }
            KrtCtaButton(
                text = stringResource(R.string.order_production_cta),
                onClick = actions.onSubmit,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(ORDER_PRODUCTION_SUBMIT_TAG),
                enabled = draft.submittable,
            )
        }
    }
}

/**
 * How many whole units this run produced, capped at what the line still owes.
 *
 * A stepper rather than a bare field: the common case is one, and the second most common is „one
 * more than last time" — both are a tap.
 *
 * @param draft what is filled in.
 * @param onAmount the count changed.
 */
@Composable
private fun AmountField(
    draft: ProductionDraft,
    onAmount: (String) -> Unit,
) {
    KrtStepperField(
        value = draft.amount,
        onValueChange = onAmount,
        onDecrement = { onAmount(((draft.units ?: 1) - 1).coerceAtLeast(1).toString()) },
        onIncrement = { onAmount(((draft.units ?: 0) + 1).coerceAtMost(draft.remaining).toString()) },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.order_production_amount),
        enabled = !draft.saving,
    )
    Muted(text = stringResource(R.string.order_production_remaining, draft.remaining))
    if (draft.units != null && !draft.amountValid) {
        KrtFieldError(text = stringResource(R.string.order_production_amount_too_many, draft.remaining))
    }
}

/**
 * Where the line stands after this booking — the figure that finishes an item Auftrag.
 *
 * @param draft what is filled in.
 */
@Composable
private fun Projection(draft: ProductionDraft) {
    val projected = draft.projected ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Muted(text = stringResource(R.string.order_production_after), modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.order_production_after_value, projected, draft.lineAmount),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        if (draft.completes) {
            KrtChip(
                text = stringResource(R.string.order_production_fulfils),
                tone = KrtChipTone.Success,
            )
        }
    }
}

/**
 * One required material: what it needs, where it comes from, and whether it is booked out at all.
 *
 * @param draft what is filled in.
 * @param material the material's own plan.
 * @param actions what the card reports.
 */
@Composable
private fun MaterialCard(
    draft: ProductionDraft,
    material: ProductionMaterialDraft,
    actions: OrderProductionActions,
) {
    val units = draft.units ?: 0
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = material.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f),
                )
                ReconcileChip(material = material, units = units)
            }
            KrtCheckboxRow(
                checked = material.skipped,
                onCheckedChange = { actions.onSkip(material.materialId) },
                label = stringResource(R.string.order_production_skip),
                enabled = !draft.saving,
            )
            if (material.skipped) {
                KrtHint(explanation = stringResource(R.string.order_production_skip_hint))
            } else {
                MaterialRows(draft = draft, material = material, actions = actions)
            }
        }
    }
}

/**
 * The stock rows one material may be drawn from, and what is taken off each.
 *
 * @param draft what is filled in.
 * @param material the material's own plan.
 * @param actions what the rows report.
 */
@Composable
private fun MaterialRows(
    draft: ProductionDraft,
    material: ProductionMaterialDraft,
    actions: OrderProductionActions,
) {
    when {
        material.loading -> {
            KrtSpinner()
        }

        material.rows.isEmpty() -> {
            Muted(text = stringResource(R.string.order_production_no_stock))
        }

        else -> {
            material.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Muted(text = row.krtSourceLabel(), modifier = Modifier.weight(1f))
                    KrtTextField(
                        value = material.amounts[row.id].orEmpty(),
                        onValueChange = { actions.onDraw(material.materialId, row.id, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = "0",
                        enabled = !draft.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        tabularFigures = true,
                    )
                }
            }
            KrtGhostButton(
                text = stringResource(R.string.order_production_autofill),
                onClick = { actions.onAutoFill(material.materialId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !draft.saving && draft.units != null,
            )
        }
    }
}

/**
 * „benötigt X · zugewiesen Y · Rest Z" — the gate, said out loud.
 *
 * Green only at an exact match, because that is the only plan the server accepts; red once more is
 * assigned than is needed, so an over-assignment is not read as „nearly there".
 *
 * @param material the material's own plan.
 * @param units how many are being built.
 */
@Composable
private fun ReconcileChip(
    material: ProductionMaterialDraft,
    units: Int,
) {
    if (material.skipped) {
        KrtChip(text = stringResource(R.string.order_production_skipped), tone = KrtChipTone.Muted)
        return
    }
    val rest = material.rest(units)
    KrtChip(
        text =
            stringResource(
                R.string.order_production_reconcile,
                material.demand(units).krtPlain(),
                material.assigned.krtPlain(),
                rest.krtPlain(),
            ),
        tone =
            when {
                material.covered(units) -> KrtChipTone.Success
                rest < 0 -> KrtChipTone.Danger
                else -> KrtChipTone.Muted
            },
    )
}

/**
 * One stock row, as the sheet names it: holder · place · quality · what it can give.
 *
 * @receiver the row.
 * @return its label.
 */
@Composable
private fun HandoverStockRow.krtSourceLabel(): String =
    listOfNotNull(
        owner,
        location,
        quality?.let { stringResource(R.string.order_handover_quality, it) },
        stringResource(R.string.order_production_available, available.krtPlain()),
    ).joinToString(" · ")

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
 * What the last booking returned, in the app's own words.
 *
 * A `409` here means the line moved while the sheet was open — somebody else booked production on
 * it — so it reads as a conflict rather than as a generic failure. A `422` is the coverage gate,
 * which the sheet's own gate should have caught first; saying so plainly beats „Fehler".
 *
 * @param error the refusal.
 */
@Composable
private fun ProductionError(error: ApiError) {
    KrtFieldError(
        text =
            stringResource(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
                    is ApiError.Forbidden -> R.string.order_production_not_allowed
                    is ApiError.Validation -> R.string.order_production_alloc_error
                    else -> R.string.write_failed
                },
            ),
    )
}

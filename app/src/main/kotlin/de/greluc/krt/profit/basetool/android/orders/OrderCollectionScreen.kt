/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MaterialCollectionRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the collection list. */
const val ORDER_COLLECTION_TAG: String = "order-collection"

/**
 * „Materialsammelübersicht" — the stock rows linked to one Auftrag (design ch. 10 artboard 16).
 *
 * - **Lieferstatus** — `PATCH /inventory/{id}/delivered` marks material as handed over.
 * - **Verknüpfung lösen** — removes the link and keeps the stock; a row with no earmarked amount is
 *   unlinked without confirmation.
 *
 * Besitzer and Standort are not editable here; moving stock happens in the Lager's book-out sheet.
 *
 * @param state what to draw.
 * @param actions what the rows report.
 * @param modifier layout modifier.
 */
@Composable
fun OrderCollectionScreen(
    state: OrderCollectionState,
    actions: CollectionActions,
    modifier: Modifier = Modifier,
) {
    ProvideScreenTopBar(
        title = stringResource(R.string.order_collection_title),
        subtitle = {
            Text(
                text = stringResource(R.string.orders_number, state.displayId),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
        },
    )
    if (state.loading) {
        KrtLoadingIndicator(
            text = stringResource(R.string.order_collection_title),
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        state = rememberRootListState(),
        modifier = modifier.fillMaxSize().testTag(ORDER_COLLECTION_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter(), vertical = KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        item(key = "rows-title") {
            KrtSectionTitle(
                text = stringResource(R.string.order_collection_entries),
                trailing = { Muted(text = state.rows.size.toString()) },
            )
        }
        if (state.rows.isEmpty()) {
            item(key = "rows-empty") {
                Muted(text = stringResource(R.string.order_collection_empty))
            }
        }
        items(state.rows, key = { it.entryId }) { row ->
            CollectionRow(row = row, state = state, actions = actions)
            KrtHairlineRule()
        }
        if (state.unbacked.isNotEmpty()) {
            item(key = "unbacked-title") {
                KrtSectionTitle(
                    text = stringResource(R.string.order_collection_unbacked),
                    trailing = { Muted(text = state.unbacked.size.toString()) },
                )
            }
            items(state.unbacked, key = { "unbacked-" + it.materialId }) { material ->
                UnbackedRow(material = material, state = state, actions = actions)
                KrtHairlineRule()
            }
        }
        item(key = "transfer-note") {
            KrtHint(explanation = stringResource(R.string.order_collection_transfer_note))
        }
    }
    UnlinkModal(state = state, actions = actions)
}

/**
 * One linked stock row: who holds it, where, how much of it this Auftrag has, and its two controls.
 *
 * @param row the row.
 * @param state the screen, for whether writes are offered.
 * @param actions what the row reports.
 */
@Composable
private fun CollectionRow(
    row: MaterialCollectionRow,
    state: OrderCollectionState,
    actions: CollectionActions,
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.materialName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f),
                )
                if (row.delivered) {
                    KrtChip(
                        text = stringResource(R.string.order_collection_delivered),
                        tone = KrtChipTone.Success,
                    )
                }
                KrtIconButton(
                    iconRes = DesignR.drawable.ic_krt_close,
                    label = stringResource(R.string.order_collection_unlink),
                    onClick = { actions.onUnlink(row) },
                    enabled = state.allowed && !state.saving,
                )
            }
            Muted(text = listOfNotNull(row.owner, row.location).joinToString(" · "))
            Muted(
                text =
                    stringResource(
                        R.string.order_collection_amount,
                        row.allocated?.toPlainString().orEmpty(),
                        row.quantity?.toPlainString().orEmpty(),
                    ),
            )
            row.quality?.let {
                Muted(text = stringResource(R.string.order_handover_quality, it.toInt()))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Muted(
                    text = stringResource(R.string.order_collection_delivered_label),
                    modifier = Modifier.weight(1f),
                )
                KrtToggle(
                    checked = row.delivered,
                    onCheckedChange = { actions.onDelivered(row) },
                    enabled = state.allowed && !state.saving && row.version != null,
                )
            }
        }
    }
}

/**
 * One required material with no stock behind it.
 *
 * Unlinked without a confirmation: there is no amount to lose.
 *
 * @param material the material.
 * @param state the screen, for whether writes are offered.
 * @param actions what the row reports.
 */
@Composable
private fun UnbackedRow(
    material: UnbackedMaterial,
    state: OrderCollectionState,
    actions: CollectionActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = material.name,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            modifier = Modifier.weight(1f),
        )
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_close,
            label = stringResource(R.string.order_collection_unlink),
            onClick = { actions.onUnlinkMaterial(material) },
            enabled = state.allowed && !state.saving,
        )
    }
}

/**
 * Confirms unlinking a row with an earmarked amount, naming what goes and what stays.
 *
 * @param state the screen.
 * @param actions the answer.
 */
@Composable
private fun UnlinkModal(
    state: OrderCollectionState,
    actions: CollectionActions,
) {
    val confirm = state.confirming ?: return
    KrtModal(
        title = stringResource(R.string.order_collection_unlink_title),
        confirmText = stringResource(R.string.order_collection_unlink),
        onConfirm = actions.onConfirmUnlink,
        onDismiss = actions.onDismissConfirm,
    ) {
        Muted(
            text =
                stringResource(
                    R.string.order_collection_unlink_body,
                    confirm.amount?.toPlainString().orEmpty(),
                    confirm.materialName,
                    listOfNotNull(confirm.owner, confirm.location).joinToString(" · "),
                ),
        )
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
 * What the Materialsammelübersicht reports back.
 *
 * @property onDelivered a row's delivered flag was flipped.
 * @property onUnlink a row's link is to go.
 * @property onUnlinkMaterial a material with no stock behind it is to go.
 * @property onConfirmUnlink the confirmation was answered yes.
 * @property onDismissConfirm it was backed out of.
 */
data class CollectionActions(
    val onDelivered: (MaterialCollectionRow) -> Unit,
    val onUnlink: (MaterialCollectionRow) -> Unit,
    val onUnlinkMaterial: (UnbackedMaterial) -> Unit,
    val onConfirmUnlink: () -> Unit,
    val onDismissConfirm: () -> Unit,
)

/**
 * The Materialsammelübersicht, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun OrderCollectionRoute(
    viewModel: OrderCollectionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrderCollectionScreen(
        state = state,
        actions =
            CollectionActions(
                onDelivered = viewModel::onDelivered,
                onUnlink = viewModel::onUnlink,
                onUnlinkMaterial = viewModel::onUnlinkMaterial,
                onConfirmUnlink = viewModel::onConfirmUnlink,
                onDismissConfirm = viewModel::onDismissConfirm,
            ),
        modifier = modifier,
    )
}

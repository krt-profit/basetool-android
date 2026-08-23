/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/** Test handle for the add sheet's save action. */
const val BLUEPRINTS_SAVE_TAG: String = "blueprints-save"

/** Below this many characters a catalogue search would return most of the catalogue. */
private const val MIN_PRODUCT_QUERY = 2

/**
 * The add sheet: search the catalogue, pick a product, optionally note why.
 *
 * A product the member already owns is listed but **not selectable** — the create would be refused
 * by the server, and a picker that offers it is one that sets up a failure. Saying "hast du schon"
 * beside it answers the question the member actually has, which is whether they own it.
 *
 * @param editor what the sheet holds.
 * @param onQuery the product search changed.
 * @param onChosen a product was picked.
 * @param onNote the note changed.
 * @param onSave the save action was taken.
 * @param onDismiss the sheet was closed.
 */
@Composable
fun BlueprintAddSheet(
    editor: BlueprintEditor.Adding,
    onQuery: (String) -> Unit,
    onChosen: (BlueprintProduct) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.blueprints_add_title)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            KrtTextField(
                value = editor.query,
                onValueChange = onQuery,
                label = stringResource(R.string.blueprints_product_search),
                enabled = !editor.saving,
            )
            editor.chosen?.let { chosen ->
                Text(
                    text = chosen.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!editor.searching && editor.query.trim().length < MIN_PRODUCT_QUERY && editor.chosen == null) {
                Muted(stringResource(R.string.blueprints_product_hint))
            } else if (!editor.searching &&
                editor.query.trim().length >= MIN_PRODUCT_QUERY &&
                editor.results.isEmpty()
            ) {
                Muted(stringResource(R.string.blueprints_product_none))
            }
            if (editor.results.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    editor.results.forEach { product ->
                        ProductRow(product = product, enabled = !editor.saving, onChosen = onChosen)
                    }
                    if (editor.capped) {
                        Muted(
                            pluralStringResource(
                                R.plurals.blueprints_product_capped,
                                editor.results.size,
                                editor.results.size,
                            ),
                        )
                    }
                }
            }
            KrtTextField(
                value = editor.note,
                onValueChange = onNote,
                label = stringResource(R.string.personal_inventory_field_note),
                enabled = !editor.saving,
            )
            editor.error?.let { SheetError(it) }
            SheetActions(
                saveEnabled = editor.submittable && !editor.saving,
                dismissEnabled = !editor.saving,
                onSave = onSave,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * One catalogue row.
 *
 * @param product the row.
 * @param enabled whether the sheet accepts input.
 * @param onChosen picks it.
 */
@Composable
private fun ProductRow(
    product: BlueprintProduct,
    enabled: Boolean,
    onChosen: (BlueprintProduct) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && !product.owned) { onChosen(product) }
                .padding(vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = product.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (product.owned) KrtPalette.TextMuted else KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (product.owned) {
            Muted(stringResource(R.string.blueprints_product_owned))
        }
    }
}

/**
 * The note sheet of an entry the member already owns.
 *
 * Only the note is editable. `PUT` also accepts `acquiredAt`, and the app deliberately does not
 * send it: the field is not offered, and re-sending a value the member cannot see would let a save
 * silently rewrite something they never looked at.
 *
 * @param editor what the sheet holds.
 * @param onNote the note changed.
 * @param onSave the save action was taken.
 * @param onDismiss the sheet was closed.
 */
@Composable
fun BlueprintNoteSheet(
    editor: BlueprintEditor.Editing,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.blueprints_edit_title)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            Text(
                text = editor.entry.productName,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
            )
            KrtTextField(
                value = editor.note,
                onValueChange = onNote,
                label = stringResource(R.string.personal_inventory_field_note),
                enabled = !editor.saving,
            )
            editor.error?.let { SheetError(it) }
            SheetActions(
                saveEnabled = !editor.saving,
                dismissEnabled = !editor.saving,
                onSave = onSave,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * The cancel/save pair both sheets end with.
 *
 * @param saveEnabled whether saving is possible.
 * @param dismissEnabled whether closing is possible.
 * @param onSave the save action.
 * @param onDismiss the close action.
 */
@Composable
private fun SheetActions(
    saveEnabled: Boolean,
    dismissEnabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        KrtGhostButton(
            text = stringResource(R.string.personal_inventory_cancel),
            onClick = onDismiss,
            enabled = dismissEnabled,
        )
        KrtCtaButton(
            text = stringResource(R.string.personal_inventory_save),
            onClick = onSave,
            modifier = Modifier.testTag(BLUEPRINTS_SAVE_TAG),
            enabled = saveEnabled,
        )
    }
}

/**
 * What a failed save says.
 *
 * @param error what came back.
 */
@Composable
private fun SheetError(error: ApiError) {
    KrtFieldError(
        text =
            stringResource(
                if (error is ApiError.OptimisticLock) R.string.conflict_body else R.string.write_failed,
            ),
    )
}

/**
 * A quiet line.
 *
 * @param text what it says.
 */
@Composable
private fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
}

/**
 * How a catalogue product reads in one line.
 *
 * @return the name with its manufacturer, which is what tells two similar products apart.
 */
private fun BlueprintProduct.label(): String =
    listOfNotNull(name.takeIf { it.isNotBlank() }, manufacturer).joinToString(" · ")

/**
 * The removal confirmation.
 *
 * @param entry the blueprint about to go.
 * @param deleting whether the removal is in flight.
 * @param onConfirm remove it.
 * @param onDismiss keep it.
 */
@Composable
fun BlueprintRemoveModal(
    entry: OwnedBlueprint,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.blueprints_remove_title),
        confirmText = stringResource(R.string.blueprints_remove),
        onConfirm = { if (!deleting) onConfirm() },
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        cancelText = stringResource(R.string.personal_inventory_cancel),
    ) {
        Text(
            text = stringResource(R.string.blueprints_remove_body, entry.productName),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

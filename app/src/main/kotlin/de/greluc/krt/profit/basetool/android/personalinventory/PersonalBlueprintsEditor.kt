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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectionCheckbox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.ui.writeFailureText

/** Test handle for the add sheet's save action. */
const val BLUEPRINTS_SAVE_TAG: String = "blueprints-save"

/** Between the names of the products a member has ticked. */
private const val CHOSEN_SEPARATOR = " · "

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
                    .padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            KrtTextField(
                value = editor.query,
                onValueChange = onQuery,
                // Inside the box, as the chapters draw every search field: it says what it
                // searches while it is empty and gives the room back once it is not.
                placeholder = stringResource(R.string.blueprints_product_search),
                enabled = !editor.saving,
            )
            if (editor.chosen.isNotEmpty()) {
                Text(
                    text = editor.chosen.joinToString(CHOSEN_SEPARATOR) { it.label() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ProductResults(editor = editor, onChosen = onChosen)
            KrtTextField(
                value = editor.note,
                onValueChange = onNote,
                label = stringResource(R.string.personal_inventory_field_note),
                enabled = !editor.saving && editor.noteApplies,
            )
            if (!editor.noteApplies) {
                Muted(stringResource(R.string.blueprints_note_single_only))
            }
            editor.outcome?.let { outcome ->
                Muted(
                    pluralStringResource(
                        R.plurals.blueprints_batch_result,
                        outcome.added,
                        outcome.added,
                        outcome.alreadyOwned,
                    ),
                )
            }
            editor.error?.let { SheetError(it) }
            SheetActions(
                saveEnabled = editor.submittable && !editor.saving,
                dismissEnabled = !editor.saving,
                onSave = onSave,
                onDismiss = onDismiss,
                // The CTA names the count, as the artboard draws it; with one picked it stays the
                // sheet's ordinary „Speichern".
                saveText =
                    if (editor.count > 1) {
                        pluralStringResource(
                            R.plurals.blueprints_take_over,
                            editor.count,
                            editor.count,
                        )
                    } else {
                        null
                    },
            )
        }
    }
}

/**
 * The catalogue half of the add sheet: the hint, the hits, the cap and the notice line.
 *
 * Its own composable because the sheet grew past detekt's complexity ceiling once the rows became
 * checkboxes — and because these four are one thought: what the search found, and what it did not.
 *
 * @param editor what the sheet holds.
 * @param onChosen a row was ticked or unticked.
 */
@Composable
private fun ProductResults(
    editor: BlueprintEditor.Adding,
    onChosen: (BlueprintProduct) -> Unit,
) {
    val typed = editor.query.trim().length
    if (!editor.searching && typed < MIN_PRODUCT_QUERY && editor.chosen.isEmpty()) {
        Muted(stringResource(R.string.blueprints_product_hint))
    } else if (!editor.searching && typed >= MIN_PRODUCT_QUERY && editor.offered.isEmpty()) {
        Muted(stringResource(R.string.blueprints_product_none))
    }
    if (editor.offered.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            editor.offered.forEach { product ->
                ProductRow(
                    product = product,
                    enabled = !editor.saving,
                    onChosen = onChosen,
                    picked = editor.chosen.any { it.productKey == product.productKey },
                )
            }
            if (editor.capped) {
                Muted(
                    pluralStringResource(
                        R.plurals.blueprints_product_capped,
                        editor.offered.size,
                        editor.offered.size,
                    ),
                )
            }
        }
    }
    // Why a hit can be missing: the web does not offer what the member already owns, and design
    // ch. 17 artboard 5 wants that said rather than left to be read as a broken search.
    Muted(stringResource(R.string.blueprints_product_owned_hidden))
}

/**
 * One catalogue row.
 *
 * @param product the row.
 * @param enabled whether the sheet accepts input.
 * @param onChosen picks it.
 * @param picked whether it is already ticked — a second tap takes it back off.
 */
@Composable
private fun ProductRow(
    product: BlueprintProduct,
    enabled: Boolean,
    onChosen: (BlueprintProduct) -> Unit,
    picked: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onChosen(product) }
                .padding(vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A box, not a radio: artboard 5 makes the row a checkbox, because picking several is the
        // normal case as soon as more than one hit fits.
        KrtSelectionCheckbox(checked = picked)
        Text(
            text = product.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
                    .padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
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
    saveText: String? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        KrtGhostButton(
            text = stringResource(R.string.personal_inventory_cancel),
            onClick = onDismiss,
            enabled = dismissEnabled,
        )
        KrtCtaButton(
            text = saveText ?: stringResource(R.string.personal_inventory_save),
            onClick = onSave,
            modifier = Modifier.testTag(BLUEPRINTS_SAVE_TAG),
            enabled = saveEnabled,
        )
    }
}

/**
 * What a failed save says.
 *
 * A validation refusal is shown in the server's own words, because it names the field that was
 * rejected and the sheet cannot. A conflict and everything else keep the sheet's own sentence.
 *
 * @param error what came back.
 */
@Composable
private fun SheetError(error: ApiError) {
    KrtFieldError(
        text =
            error.writeFailureText(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
                    is ApiError.Conflict -> R.string.refused_inline
                    else -> R.string.write_failed
                },
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

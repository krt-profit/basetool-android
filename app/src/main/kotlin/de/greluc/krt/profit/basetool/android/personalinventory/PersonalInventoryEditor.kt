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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/** Test handle for the editor sheet. */
const val PERSONAL_INVENTORY_EDITOR_TAG: String = "personal-inventory-editor"

/** Test handle for the save action. */
const val PERSONAL_INVENTORY_SAVE_TAG: String = "personal-inventory-save"

/**
 * The create/edit sheet.
 *
 * A KRT bottom sheet, never a platform dialog (design ch. 02). The fields are the ones the API
 * actually carries — name, quantity, place, note — rather than the shared Lager's material-and-
 * quality form the design's § 4 points at: a personal entry is free text at a UEX location with a
 * whole-number count, and there is no material, no quality and no SCU precision to offer. Recorded
 * as a deviation in the spec for the same reason the phase-2 ones were: the aggregate the design
 * assumes does not exist on the wire.
 *
 * @param editor what the editor holds.
 * @param locations the place picker's state.
 * @param onName the name changed.
 * @param onQuantity the quantity changed.
 * @param onStep the quantity was stepped.
 * @param onNote the note changed.
 * @param onLocationQuery the place search changed.
 * @param onLocationChosen a place was picked.
 * @param onSave the save action was taken.
 * @param onDismiss the sheet was closed.
 */
@Composable
fun PersonalInventoryEditor(
    editor: EditorState.Open,
    locations: LocationSearch,
    onName: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onStep: (Int) -> Unit,
    onNote: (String) -> Unit,
    onLocationQuery: (String) -> Unit,
    onLocationChosen: (PersonalLocation) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag(PERSONAL_INVENTORY_EDITOR_TAG),
        title =
            stringResource(
                if (editor.editing == null) {
                    R.string.personal_inventory_create
                } else {
                    R.string.personal_inventory_edit
                },
            ),
    ) {
        // Scrollable, and that is not cosmetic: with a place chosen and the keyboard up, the
        // action row was pushed past the bottom edge and the sheet could not be submitted at all.
        // Found on a device; a fixed-height sheet is only ever as tall as the shortest phone.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.personal_inventory_visible_only_to_you),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = editor.name,
                onValueChange = onName,
                label = stringResource(R.string.personal_inventory_field_name),
                enabled = !editor.saving,
            )
            KrtStepperField(
                value = editor.quantity,
                onValueChange = onQuantity,
                onDecrement = { onStep(-1) },
                onIncrement = { onStep(1) },
                label = stringResource(R.string.personal_inventory_field_quantity),
                enabled = !editor.saving,
            )
            LocationPicker(
                chosen = editor.location,
                locations = locations,
                enabled = !editor.saving,
                onQuery = onLocationQuery,
                onChosen = onLocationChosen,
            )
            KrtTextField(
                value = editor.note,
                onValueChange = onNote,
                label = stringResource(R.string.personal_inventory_field_note),
                enabled = !editor.saving,
            )
            editor.error?.let { EditorError(error = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = onDismiss,
                    enabled = !editor.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = onSave,
                    modifier = Modifier.testTag(PERSONAL_INVENTORY_SAVE_TAG),
                    enabled = editor.submittable && !editor.saving,
                )
            }
        }
    }
}

/**
 * What a failed save says.
 *
 * A conflict gets its own wording, because it is the one failure that is nobody's fault and has a
 * specific remedy. Everything else is one sentence: the member cannot act on the difference between
 * a 500 and a dropped connection while a sheet is open over their typing.
 *
 * @param error what came back.
 */
@Composable
private fun EditorError(error: ApiError) {
    KrtFieldError(
        text =
            stringResource(
                if (error is ApiError.OptimisticLock) R.string.conflict_body else R.string.write_failed,
            ),
    )
}

/**
 * The place picker.
 *
 * Search-driven rather than a list: UEX knows hundreds of places, and the server caps what it
 * returns. When the answer comes back full the cap is stated — a picker that silently drops the
 * place a member is looking for is worse than one that admits it did (ADR-0104).
 *
 * @param chosen the place already picked, or `null`.
 * @param locations the search state.
 * @param enabled whether the picker accepts input.
 * @param onQuery the search changed.
 * @param onChosen a place was picked.
 */
@Composable
private fun LocationPicker(
    chosen: PersonalLocation?,
    locations: LocationSearch,
    enabled: Boolean,
    onQuery: (String) -> Unit,
    onChosen: (PersonalLocation) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtTextField(
            value = locations.query,
            onValueChange = onQuery,
            label = stringResource(R.string.personal_inventory_field_location),
            placeholder = stringResource(R.string.personal_inventory_location_search),
            enabled = enabled,
        )
        chosen?.let {
            Text(
                text = it.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val typed = locations.query.trim().length
        // Nothing is said while a search is in flight: "kein Ort gefunden" that turns into a list
        // half a second later reads as a fault the member then has to un-believe.
        if (!locations.searching) {
            if (typed < MIN_QUERY && chosen == null) {
                Muted(stringResource(R.string.personal_inventory_location_hint))
            } else if (typed >= MIN_QUERY && locations.results.isEmpty()) {
                Muted(stringResource(R.string.personal_inventory_location_none))
            }
        }
        // A plain column, not a LazyColumn: the whole sheet scrolls, and two scroll containers in
        // the same direction cannot be nested. The list is capped at 25 rows, so nothing is lazy
        // about it anyway.
        if (locations.results.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                locations.results.forEach { place ->
                    Text(
                        text = place.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KrtPalette.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { onChosen(place) }
                                .padding(vertical = KrtSpacing.sm),
                    )
                }
                if (locations.capped) {
                    Muted(
                        pluralStringResource(
                            R.plurals.personal_inventory_location_capped,
                            locations.results.size,
                            locations.results.size,
                        ),
                    )
                }
            }
        }
    }
}

/** Below this many characters a place search would return most of the catalogue. */
private const val MIN_QUERY = 2

/**
 * A quiet line.
 *
 * @param text what it says.
 */
@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/**
 * How a place reads in one line.
 *
 * @return the name, with the system and parent that tell two same-named places apart.
 */
private fun PersonalLocation.label(): String =
    listOfNotNull(name.takeIf { it.isNotBlank() }, parent, system)
        .distinct()
        .joinToString(" · ")

/**
 * The delete confirmation.
 *
 * A KRT modal in its danger tone, naming the entry: "are you sure" without the name is a question
 * the member cannot actually answer.
 *
 * @param item the entry about to go.
 * @param deleting whether the delete is in flight.
 * @param onConfirm delete it.
 * @param onDismiss keep it.
 */
@Composable
fun PersonalInventoryDeleteModal(
    item: PersonalItem,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.personal_inventory_delete_title),
        confirmText = stringResource(R.string.personal_inventory_delete),
        onConfirm = { if (!deleting) onConfirm() },
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        cancelText = stringResource(R.string.personal_inventory_cancel),
    ) {
        Text(
            text = stringResource(R.string.personal_inventory_delete_body, item.name),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

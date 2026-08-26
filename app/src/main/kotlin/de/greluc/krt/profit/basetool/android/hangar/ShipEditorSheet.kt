/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.HomeLocation
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/** Test handle for the ship editor. */
const val SHIP_EDITOR_TAG: String = "ship-editor"

/** Test handle for the editor's save action. */
const val SHIP_SAVE_TAG: String = "ship-save"

/** How many hulls the picker shows before it asks the member to narrow the search. */
private const val HULL_RESULT_LIMIT = 8

/** Which half of the insurance segment is which. */
private const val INSURANCE_TAB_LTI = 0

/**
 * The create/edit sheet for one of the member's ships.
 *
 * **Insurance is a segment plus a number, not a text field.** The server accepts `LTI` or a whole
 * number of months from 0 to 120 and refuses everything else, so the app offers exactly those two
 * shapes. A free-text field would let a member type "lifetime" and learn it was wrong only after
 * the save.
 *
 * @param editor what the sheet holds.
 * @param hulls the ship-type catalogue.
 * @param places where a ship can be parked.
 * @param onName the name changed.
 * @param onHullQuery the hull search changed.
 * @param onHull a hull was picked.
 * @param onLti the insurance kind changed.
 * @param onMonths the month count changed.
 * @param onPlace a place was picked, or cleared.
 * @param onFitted the fitted switch changed.
 * @param onSave the save action was taken.
 * @param onDismiss the sheet was closed.
 */
@Composable
fun ShipEditorSheet(
    editor: ShipEditor.Open,
    hulls: List<ShipTypeOption>,
    places: List<HomeLocation>,
    onName: (String) -> Unit,
    onHullQuery: (String) -> Unit,
    onHull: (ShipTypeOption) -> Unit,
    onLti: (Boolean) -> Unit,
    onMonths: (String) -> Unit,
    onPlace: (HomeLocation?) -> Unit,
    onFitted: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag(SHIP_EDITOR_TAG),
        title =
            stringResource(
                if (editor.editing == null) R.string.hangar_ship_create else R.string.hangar_ship_edit,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            // Artboard 08.2's order: identify the ship, then name it, then state its facts.
            // Name-first asked a member to name a thing they had not chosen yet.
            HullPicker(
                chosen = editor.hull,
                query = editor.hullQuery,
                hulls = hulls,
                enabled = !editor.saving,
                onQuery = onHullQuery,
                onChosen = onHull,
            )
            KrtTextField(
                value = editor.name,
                onValueChange = onName,
                label = stringResource(R.string.hangar_field_name),
                enabled = !editor.saving,
            )
            // Versicherung and Ort share a row in the artboard: both are short facts about where
            // the hull stands, and full-width each they pushed Fitted off a phone screen.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    InsuranceField(
                        lti = editor.insuranceLti,
                        months = editor.insuranceMonths,
                        enabled = !editor.saving,
                        onLti = onLti,
                        onMonths = onMonths,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PlacePicker(
                        chosen = editor.place,
                        places = places,
                        enabled = !editor.saving,
                        onChosen = onPlace,
                    )
                }
            }
            // A bordered row that lights up when set, not a toggle on a bare line: the artboard
            // gives Fitted its own box with a second line saying what it means, because
            // "einsatzbereit" is a claim about the ship that somebody else will rely on.
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                KrtCheckboxRow(
                    checked = editor.fitted,
                    onCheckedChange = onFitted,
                    label = stringResource(R.string.hangar_field_fitted),
                    enabled = !editor.saving,
                )
                Text(
                    text = stringResource(R.string.hangar_field_fitted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    modifier = Modifier.padding(start = KrtSpacing.xl),
                )
            }
            editor.error?.let { error ->
                KrtFieldError(
                    text =
                        stringResource(
                            if (error is ApiError.OptimisticLock) {
                                R.string.conflict_inline
                            } else {
                                R.string.write_failed
                            },
                        ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = onDismiss,
                    enabled = !editor.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = onSave,
                    modifier = Modifier.testTag(SHIP_SAVE_TAG),
                    enabled = editor.submittable && !editor.saving,
                )
            }
        }
    }
}

/**
 * The hull picker.
 *
 * Searchable rather than a list: the catalogue runs to hundreds of hulls. Only the first few
 * matches are shown, and the notice says so — a list cut without saying so sends a member looking
 * for a hull that is right there under the fold.
 *
 * @param chosen the hull already picked, or `null`.
 * @param query what the member typed.
 * @param hulls the catalogue.
 * @param enabled whether the picker accepts input.
 * @param onQuery the search changed.
 * @param onChosen a hull was picked.
 */
@Composable
private fun HullPicker(
    chosen: ShipTypeOption?,
    query: String,
    hulls: List<ShipTypeOption>,
    enabled: Boolean,
    onQuery: (String) -> Unit,
    onChosen: (ShipTypeOption) -> Unit,
) {
    val term = query.trim()
    val matches =
        if (term.isEmpty()) {
            emptyList()
        } else {
            hulls.filter {
                it.name.contains(term, ignoreCase = true) ||
                    it.manufacturerName?.contains(term, ignoreCase = true) == true
            }
        }
    // Opening is the member's act, not a side effect of the query still being non-empty: after a
    // pick the query holds the chosen hull's name, and a list that reopens on that would cover the
    // field the moment it is answered.
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = matches.take(HULL_RESULT_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtCombobox(
            query = query,
            onQueryChange = {
                onQuery(it)
                open = true
            },
            options = shown.map { KrtOption(it.id, it.label()) },
            onSelect = { option ->
                hulls.firstOrNull { it.id == option.value }?.let(onChosen)
                open = false
            },
            expanded = open && shown.isNotEmpty(),
            onExpandedChange = { open = it },
            label = stringResource(R.string.hangar_field_type),
            placeholder = stringResource(R.string.hangar_type_search),
            selectedValue = chosen?.id,
            notice =
                if (matches.size > HULL_RESULT_LIMIT) {
                    pluralStringResource(
                        R.plurals.hangar_type_more,
                        matches.size - HULL_RESULT_LIMIT,
                        matches.size - HULL_RESULT_LIMIT,
                    )
                } else {
                    null
                },
            enabled = enabled,
        )
        chosen?.let {
            Text(
                text = it.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (term.isNotEmpty() && matches.isEmpty()) {
            Muted(stringResource(R.string.hangar_type_none))
        }
    }
}

/**
 * The insurance field: lifetime, or a number of months.
 *
 * @param lti whether lifetime is chosen.
 * @param months the month count as typed.
 * @param enabled whether the field accepts input.
 * @param onLti the kind changed.
 * @param onMonths the count changed.
 */
@Composable
private fun InsuranceField(
    lti: Boolean,
    months: String,
    enabled: Boolean,
    onLti: (Boolean) -> Unit,
    onMonths: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        KrtFieldLabel(text = stringResource(R.string.hangar_field_insurance), enabled = enabled)
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.hangar_insurance_lti),
                    stringResource(R.string.hangar_insurance_months),
                ),
            selectedIndex = if (lti) INSURANCE_TAB_LTI else 1,
            onSelect = { onLti(it == INSURANCE_TAB_LTI) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            stretch = true,
        )
        if (!lti) {
            KrtTextField(
                value = months,
                onValueChange = onMonths,
                placeholder = stringResource(R.string.hangar_insurance_months_placeholder),
                enabled = enabled,
                isError = months.isNotEmpty() && months.toIntOrNull()?.let { it > MAX_MONTHS } != false,
                errorText = stringResource(R.string.hangar_insurance_months_range),
            )
        }
    }
}

/** The most months the server accepts. */
private const val MAX_MONTHS = 120

/**
 * The place picker.
 *
 * A short list rather than a search: the org's home locations are a handful, and "kein Ort" is a
 * legitimate answer that a search field cannot express.
 *
 * @param chosen the place already picked, or `null`.
 * @param places the list.
 * @param enabled whether the picker accepts input.
 * @param onChosen a place was picked, or `null` to clear it.
 */
@Composable
private fun PlacePicker(
    chosen: HomeLocation?,
    places: List<HomeLocation>,
    enabled: Boolean,
    onChosen: (HomeLocation?) -> Unit,
) {
    // A closed list of the org's own places, so the select field rather than the combobox: there
    // is nothing to type, and an always-open column of every place buried the fields under it.
    var open by rememberSaveable { mutableStateOf(false) }
    val none = stringResource(R.string.hangar_location_none)
    KrtSelectField(
        value = chosen?.name ?: none,
        options = listOf(KrtOption(NO_PLACE, none)) + places.map { KrtOption(it.id, it.name) },
        onSelect = { option ->
            onChosen(places.firstOrNull { it.id == option.value })
            open = false
        },
        expanded = open,
        onExpandedChange = { open = it },
        label = stringResource(R.string.hangar_field_location),
        selectedValue = chosen?.id ?: NO_PLACE,
        enabled = enabled,
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
 * How a hull reads in one line.
 *
 * @return the hull with its maker, which is what tells two similar ones apart.
 */
private fun ShipTypeOption.label(): String =
    listOfNotNull(name.takeIf { it.isNotBlank() }, manufacturerName).joinToString(" · ")

/**
 * The removal confirmation.
 *
 * @param ship the ship about to go.
 * @param deleting whether the removal is in flight.
 * @param onConfirm remove it.
 * @param onDismiss keep it.
 */
@Composable
fun ShipDeleteModal(
    ship: Ship,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.hangar_ship_delete_title),
        confirmText = stringResource(R.string.personal_inventory_delete),
        onConfirm = { if (!deleting) onConfirm() },
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        cancelText = stringResource(R.string.personal_inventory_cancel),
    ) {
        Text(
            text =
                stringResource(
                    R.string.hangar_ship_delete_body,
                    ship.name?.takeIf { it.isNotBlank() } ?: ship.typeName,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

/** Value standing for "kein Ort" in the place select - no place has an empty id. */
private const val NO_PLACE = ""

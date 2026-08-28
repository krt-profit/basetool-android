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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test tag of the create form's scrolling body. */
const val ORDER_CREATE_TAG: String = "order-create"

/** Test tag of the submit action. */
const val ORDER_CREATE_SUBMIT_TAG: String = "order-create-submit"

/**
 * The minimum qualities an order line may ask for.
 *
 * The web offers exactly these two — „650" and „Keine" — and the value is a fixed grade rather than
 * a free figure, so a picker is right and a number field would invite a 400.
 */
private val MIN_QUALITIES: List<Int?> = listOf(null, 650)

/** How tall the comment field stands, matching the web's four-row textarea. */
private const val COMMENT_LINES = 4

/** How much has to be typed before „nothing matched" is a claim worth making. */
private const val MIN_QUERY = 2

/**
 * What the create form reports back.
 *
 * @property onResponsible the processing unit was picked.
 * @property onRequesting the customer unit was picked.
 * @property onHandle the contact handle was edited.
 * @property onComment the comment was edited.
 * @property onMaterialQuery a line's material picker was typed into.
 * @property onMaterialPicked a line's material was picked.
 * @property onAmount a line's amount was edited.
 * @property onMinQuality a line's minimum quality was picked.
 * @property onAddLine another line is wanted.
 * @property onRemoveLine a line is to go.
 * @property onSubmit the order is to be raised.
 */
data class OrderCreateActions(
    val onResponsible: (String) -> Unit,
    val onRequesting: (String) -> Unit,
    val onHandle: (String) -> Unit,
    val onComment: (String) -> Unit,
    val onMaterialQuery: (Int, String) -> Unit,
    val onMaterialPicked: (Int, Pair<String, String>) -> Unit,
    val onAmount: (Int, String) -> Unit,
    val onMinQuality: (Int, Int?) -> Unit,
    val onAddLine: () -> Unit,
    val onRemoveLine: (Int) -> Unit,
    val onSubmit: () -> Unit,
)

/**
 * „Neuer Auftrag" — the material order the web raises at `/orders/create`.
 *
 * Chapter 10 has no artboard for this form; artboard 1 only draws the „+" that opens it. The layout
 * follows chapter 11's create form, which is the nearest drawn precedent: one scrolling column, a
 * card per repeating line, a ghost button to add another, and a full-width CTA at the foot. Design
 * round 8 §1 asks for the drawing.
 *
 * **Material orders only** — an item order needs a blueprint picker per item and a derivation tree,
 * which is a screen of its own (round 8 §1.3).
 *
 * @param state what the form holds.
 * @param actions what it reports back.
 * @param modifier layout modifier.
 */
@Composable
fun OrderCreateScreen(
    state: OrderCreateState,
    actions: OrderCreateActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(ORDER_CREATE_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
    ) {
        item(key = "who") {
            WhoBlock(state = state, actions = actions)
        }
        item(key = "materials-header") {
            Text(
                text = stringResource(R.string.order_create_materials),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
            )
        }
        itemsIndexed(state.lines) { index, line ->
            LineCard(
                line = line,
                removable = state.lines.size > 1,
                materials = state.materials,
                truncated = state.materialsTruncated,
                onQuery = { actions.onMaterialQuery(index, it) },
                onPicked = { actions.onMaterialPicked(index, it) },
                onAmount = { actions.onAmount(index, it) },
                onMinQuality = { actions.onMinQuality(index, it) },
                onRemove = { actions.onRemoveLine(index) },
            )
        }
        item(key = "add-line") {
            KrtOutlineButton(
                text = stringResource(R.string.order_create_add_material),
                onClick = actions.onAddLine,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "comment") {
            KrtTextField(
                value = state.comment,
                onValueChange = actions.onComment,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.order_create_comment),
                // The web gives the comment a four-row textarea; a one-line field would hide most
                // of what a member writes into it.
                minLines = COMMENT_LINES,
            )
        }
        item(key = "cta") {
            SubmitBlock(state = state, onSubmit = actions.onSubmit)
        }
    }
}

/**
 * The two units and the contact handle.
 *
 * The processing picker is the profit-eligible subset and the customer picker is every active unit,
 * which is the distinction the backend enforces: a Bereich may raise an order but never work one.
 *
 * @param state what the form holds.
 * @param actions what it reports back.
 */
@Composable
private fun WhoBlock(
    state: OrderCreateState,
    actions: OrderCreateActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        UnitField(
            label = stringResource(R.string.order_create_responsible),
            units = state.responsibleOptions,
            selectedValue = state.responsibleId,
            onSelect = actions.onResponsible,
        )
        if (!state.loading && state.responsibleOptions.isEmpty()) {
            Text(
                // „No unit is enabled" is a statement about the organisation, and it may only be
                // made once the server has actually answered. A failed read says so instead —
                // telling a member their org has no processing unit when the phone simply could
                // not ask sends them to an administrator over a dropped connection.
                text =
                    stringResource(
                        if (state.error == null) {
                            R.string.order_create_responsible_none
                        } else {
                            R.string.order_create_units_unavailable
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.DangerText,
            )
        }
        UnitField(
            label = stringResource(R.string.order_create_requesting),
            units = state.requestingOptions,
            selectedValue = state.requestingId,
            onSelect = actions.onRequesting,
        )
        KrtTextField(
            value = state.handle,
            onValueChange = actions.onHandle,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.order_create_handle),
        )
    }
}

/**
 * One material line.
 *
 * Menge and Min. Qualität share a row because they are one thought — how much, of what grade — and
 * three fields across at 360 dp is not a layout the field height carries.
 *
 * @param line what to draw.
 * @param removable whether it may be taken away; the last line stays and is cleared instead.
 * @param materials the candidates the picker shows.
 * @param truncated whether the server holds further matches than the picker shows.
 * @param onQuery the picker was typed into.
 * @param onPicked a material was picked.
 * @param onAmount the amount was edited.
 * @param onMinQuality the minimum quality was picked.
 * @param onRemove the line is to go.
 */
@Composable
private fun LineCard(
    line: OrderLineDraft,
    removable: Boolean,
    materials: List<Pair<String, String>>,
    truncated: Boolean,
    onQuery: (String) -> Unit,
    onPicked: (Pair<String, String>) -> Unit,
    onAmount: (String) -> Unit,
    onMinQuality: (Int?) -> Unit,
    onRemove: () -> Unit,
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            MaterialField(
                shown = line.query,
                selectedValue = line.materialId,
                materials = materials,
                onQuery = onQuery,
                onPicked = onPicked,
            )
            if (truncated) {
                Text(
                    // ADR-0104: a picker that shows a capped page says so, rather than letting the
                    // member conclude the material they are looking for does not exist.
                    text = stringResource(R.string.order_create_more_matches),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            if (line.materialId == null && line.query.trim().length >= MIN_QUERY && materials.isEmpty()) {
                Text(
                    // An empty dropdown reads as a broken picker. Only orderable materials are
                    // offered here, so „nothing matched" is a real answer and worth saying.
                    text = stringResource(R.string.order_create_no_matches),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtTextField(
                    value = line.amount,
                    onValueChange = onAmount,
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.order_create_amount),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                MinQualityField(
                    selected = line.minQuality,
                    onSelect = onMinQuality,
                    modifier = Modifier.weight(1f),
                )
            }
            if (removable) {
                KrtQuietDangerButton(
                    text = stringResource(R.string.order_create_remove_material),
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = DesignR.drawable.ic_krt_trash,
                )
            }
        }
    }
}

/**
 * The material picker behind one line.
 *
 * @param shown what is in the text field.
 * @param selectedValue which material is picked, if any.
 * @param materials the candidates.
 * @param onQuery the field was typed into.
 * @param onPicked a candidate was chosen.
 */
@Composable
private fun MaterialField(
    shown: String,
    selectedValue: String?,
    materials: List<Pair<String, String>>,
    onQuery: (String) -> Unit,
    onPicked: (Pair<String, String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    KrtCombobox(
        query = shown,
        onQueryChange = {
            expanded = true
            onQuery(it)
        },
        options = materials.map { KrtOption(value = it.first, label = it.second) },
        onSelect = { option ->
            expanded = false
            onPicked(option.value to option.label)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.order_create_material),
        selectedValue = selectedValue,
    )
}

/**
 * The minimum-quality picker behind one line.
 *
 * @param selected the picked grade, or `null` for „keine".
 * @param onSelect a grade was picked.
 * @param modifier layout modifier.
 */
@Composable
private fun MinQualityField(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.order_create_min_quality_none)
    val options = MIN_QUALITIES.map { KrtOption(value = it?.toString().orEmpty(), label = it?.toString() ?: none) }
    KrtSelectField(
        value = selected?.toString() ?: none,
        options = options,
        onSelect = {
            expanded = false
            onSelect(it.value.toIntOrNull())
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
        label = stringResource(R.string.order_create_min_quality),
        selectedValue = selected?.toString().orEmpty(),
    )
}

/**
 * The unit picker used for both the processing and the customer unit.
 *
 * @param label which of the two this is.
 * @param units the candidates.
 * @param selectedValue which unit is picked.
 * @param onSelect a unit was picked.
 */
@Composable
private fun UnitField(
    label: String,
    units: List<OrgUnit>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    KrtSelectField(
        value = units.firstOrNull { it.id == selectedValue }?.name.orEmpty(),
        options = units.map { KrtOption(value = it.id, label = it.name) },
        onSelect = {
            expanded = false
            onSelect(it.value)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
        label = label,
        selectedValue = selectedValue,
    )
}

/**
 * The refusal, if there was one, and the submit.
 *
 * @param state what the form holds.
 * @param onSubmit the order is to be raised.
 */
@Composable
private fun SubmitBlock(
    state: OrderCreateState,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        state.error?.let {
            Text(
                text = stringResource(R.string.order_create_failed),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.DangerText,
            )
        }
        KrtCtaButton(
            text = stringResource(R.string.order_create_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().testTag(ORDER_CREATE_SUBMIT_TAG),
            // Validation-dimmed, without a padlock: nothing here is forbidden, it is unfinished.
            enabled = state.submittable,
        )
    }
}

/**
 * The create form, bound to its view model.
 *
 * Navigation on success is the host's, not this composable's: reacting to `created` here would fire
 * a side effect from composition.
 *
 * @param viewModel drives it.
 * @param modifier layout modifier.
 */
@Composable
fun OrderCreateRoute(
    viewModel: OrderCreateViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrderCreateScreen(
        state = state,
        actions =
            OrderCreateActions(
                onResponsible = viewModel::onResponsible,
                onRequesting = viewModel::onRequesting,
                onHandle = viewModel::onHandle,
                onComment = viewModel::onComment,
                onMaterialQuery = viewModel::onMaterialQuery,
                onMaterialPicked = viewModel::onMaterialPicked,
                onAmount = viewModel::onAmount,
                onMinQuality = viewModel::onMinQuality,
                onAddLine = viewModel::onAddLine,
                onRemoveLine = viewModel::onRemoveLine,
                onSubmit = viewModel::onSubmit,
            ),
        modifier = modifier,
    )
}

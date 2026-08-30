/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

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
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.RefineryGoodDraft
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDraft
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDateTimeField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtLocked
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** The create form, for the tests that read it. */
const val REFINERY_CREATE_TAG: String = "refinery-create"

/** Test handle for the note that says why a booked run's core is fixed. */
const val REFINERY_LOCKED_NOTE_TAG: String = "refinery-locked-note"

/**
 * What the create form reports back.
 *
 * @property onDraftChanged the form was edited.
 * @property onGoodChanged one goods line was edited.
 * @property onAddGood a line is to be added.
 * @property onRemoveGood a line is to be removed.
 * @property onMaterialQuery a material picker was typed into.
 * @property onCreate the order is to be created.
 */
data class RefineryCreateActions(
    val onDraftChanged: (RefineryOrderDraft) -> Unit,
    val onGoodChanged: (Int, RefineryGoodDraft) -> Unit,
    val onAddGood: () -> Unit,
    val onRemoveGood: (Int) -> Unit,
    val onMaterialQuery: (String) -> Unit,
    val onCreate: () -> Unit,
)

/**
 * „Neuer Raffinerieauftrag" — design chapter 11, artboards 4 and 5.
 *
 * One scrolling form rather than the two drawn screens: the artboards split it because a 412 dp
 * frame cannot show both halves at once, not because it is two steps. Nothing here is a wizard, and
 * a member who only wants to record what a run cost should not have to walk through goods to get
 * there.
 *
 * **„Endet" is computed, never typed** — start plus duration, shown as text. So is the profit
 * preview: ore sales less costs and other costs, which is the web's own definition.
 *
 * There is deliberately **no extractor import**: its handoff is consumed once in a browser and a
 * phone cannot receive it.
 *
 * @param state what the form holds.
 * @param actions what it reports back.
 * @param modifier layout modifier.
 */
@Composable
fun RefineryCreateScreen(
    state: RefineryCreateState,
    actions: RefineryCreateActions,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val locked = state.coreLocked
    val lockReason = stringResource(R.string.refinery_edit_locked_stored)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(REFINERY_CREATE_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
    ) {
        if (locked) {
            // Artboard 6 is explicit that the info block comes BEFORE the fields it explains: a
            // member who meets the lock first has to work out what it means; one who reads this
            // first meets a lock they were told about.
            item(key = "locked-note") {
                KrtHint(
                    explanation = lockReason,
                    modifier = Modifier.testTag(REFINERY_LOCKED_NOTE_TAG),
                )
            }
        }
        item(key = "where") {
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                PickerField(
                    label = stringResource(R.string.refinery_create_location),
                    options = state.refineries.map { KrtOption(value = it.first, label = it.second) },
                    selectedValue = draft.locationId,
                    shown = draft.locationName,
                    onSelect = {
                        actions.onDraftChanged(
                            draft.copy(locationId = it.value, locationName = it.label),
                        )
                    },
                    enabled = !locked,
                    lockReason = lockReason,
                )
                PickerField(
                    label = stringResource(R.string.refinery_create_method),
                    options = state.methods.map { KrtOption(value = it.id, label = it.name) },
                    selectedValue = draft.methodId,
                    shown = draft.methodName,
                    onSelect = {
                        actions.onDraftChanged(
                            draft.copy(methodId = it.value, methodName = it.label),
                        )
                    },
                    enabled = !locked,
                    lockReason = lockReason,
                )
                state.methods.firstOrNull { it.id == draft.methodId }?.let { method ->
                    Text(
                        text =
                            stringResource(
                                R.string.refinery_create_ratings,
                                method.ratingCost,
                                method.ratingSpeed,
                                method.ratingYield,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
        item(key = "goods-header") {
            Text(
                text = stringResource(R.string.refinery_create_goods),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
            )
        }
        itemsIndexed(state.draft.goods) { index, good ->
            GoodCard(
                good = good,
                removable = state.draft.goods.size > 1 && !locked,
                materials = state.materials,
                onQuery = actions.onMaterialQuery,
                onChanged = { actions.onGoodChanged(index, it) },
                onRemove = { actions.onRemoveGood(index) },
                enabled = !locked,
                lockReason = lockReason,
            )
        }
        if (!locked) {
            item(key = "add-good") {
                KrtOutlineButton(
                    text = stringResource(R.string.refinery_create_add_good),
                    onClick = actions.onAddGood,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "timing") {
            TimingBlock(state = state, draft = draft, onDraftChanged = actions.onDraftChanged)
        }
        item(key = "money") {
            MoneyBlock(state = state, draft = draft, onDraftChanged = actions.onDraftChanged)
        }
        item(key = "cta") {
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                state.error?.let {
                    Text(
                        text = stringResource(R.string.refinery_create_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.DangerText,
                    )
                }
                KrtCtaButton(
                    text =
                        stringResource(
                            if (state.editing) {
                                R.string.refinery_edit_submit
                            } else {
                                R.string.refinery_create_submit
                            },
                        ),
                    onClick = actions.onCreate,
                    modifier = Modifier.fillMaxWidth(),
                    // Validation-dimmed, without a padlock: nothing here is forbidden, it is
                    // unfinished — which the design distinguishes deliberately.
                    enabled = draft.sendable && !state.saving && !state.loading,
                )
            }
        }
    }
}

/**
 * One goods line.
 *
 * @param good what to draw.
 * @param removable whether it may be taken away — the last line stays.
 * @param materials the candidates the two pickers show.
 * @param onQuery a picker was typed into.
 * @param onChanged the line was edited.
 * @param onRemove the line is to go.
 * @param enabled whether the line may be edited; a booked run's goods are fixed.
 * @param lockReason what to tell a screen reader about a locked line.
 */
@Composable
private fun GoodCard(
    good: RefineryGoodDraft,
    removable: Boolean,
    materials: List<Pair<String, String>>,
    onQuery: (String) -> Unit,
    onChanged: (RefineryGoodDraft) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean = true,
    lockReason: String = "",
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            // A picker, not free text: the wire wants a material id, and a typed name carries
            // none — every line would be dropped and the form could never be sent.
            MaterialField(
                label = stringResource(R.string.refinery_create_input_material),
                shown = good.inputMaterialName,
                selectedValue = good.inputMaterialId,
                materials = materials,
                onQuery = onQuery,
                onSelect = {
                    onChanged(good.copy(inputMaterialId = it.first, inputMaterialName = it.second))
                },
                onType = { onChanged(good.copy(inputMaterialName = it, inputMaterialId = null)) },
                enabled = enabled,
                lockReason = lockReason,
            )
            NumberField(
                label = stringResource(R.string.refinery_create_input_quantity),
                value = good.inputQuantity,
                onValue = { onChanged(good.copy(inputQuantity = it)) },
                enabled = enabled,
                lockReason = lockReason,
            )
            MaterialField(
                label = stringResource(R.string.refinery_create_output_material),
                shown = good.outputMaterialName,
                selectedValue = good.outputMaterialId,
                materials = materials,
                onQuery = onQuery,
                onSelect = {
                    onChanged(good.copy(outputMaterialId = it.first, outputMaterialName = it.second))
                },
                onType = { onChanged(good.copy(outputMaterialName = it, outputMaterialId = null)) },
                enabled = enabled,
                lockReason = lockReason,
            )
            NumberField(
                label = stringResource(R.string.refinery_create_output_quantity),
                value = good.outputQuantity,
                onValue = { onChanged(good.copy(outputQuantity = it)) },
                enabled = enabled,
                lockReason = lockReason,
            )
            NumberField(
                label = stringResource(R.string.refinery_create_quality),
                value = good.quality,
                onValue = { onChanged(good.copy(quality = it)) },
                enabled = enabled,
                lockReason = lockReason,
            )
            NumberField(
                label = stringResource(R.string.refinery_create_bonus),
                value = good.yieldBonusPercent,
                onValue = { onChanged(good.copy(yieldBonusPercent = it)) },
                enabled = enabled,
                lockReason = lockReason,
            )
            if (removable) {
                KrtQuietDangerButton(
                    text = stringResource(R.string.refinery_create_remove_good),
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = DesignR.drawable.ic_krt_trash,
                )
            }
        }
    }
}

/**
 * Start, duration, and the end the two of them imply.
 *
 * @param state what the form holds.
 * @param draft the form.
 * @param onDraftChanged the form was edited.
 */
@Composable
private fun TimingBlock(
    state: RefineryCreateState,
    draft: RefineryOrderDraft,
    onDraftChanged: (RefineryOrderDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        Text(
            text = stringResource(R.string.refinery_create_timing),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = KrtPalette.White,
        )
        KrtDateTimeField(
            label = stringResource(R.string.refinery_create_started),
            date = draft.startedDate,
            time = draft.startedTime,
            onDate = { onDraftChanged(draft.copy(startedDate = it)) },
            onTime = { onDraftChanged(draft.copy(startedTime = it)) },
            // A run is entered after it was started, so the past is the normal case here.
            warnPast = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            NumberField(
                label = stringResource(R.string.refinery_create_hours),
                value = draft.durationHours,
                onValue = { onDraftChanged(draft.copy(durationHours = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.refinery_create_minutes),
                value = draft.durationMinutes,
                onValue = { onDraftChanged(draft.copy(durationMinutes = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        // Display, not a field: the design says „Endet" is derived, and a second editable time
        // would be a place for the two to disagree.
        state.endsAt?.let {
            Text(
                text = stringResource(R.string.refinery_create_ends, it.relativeToNow()),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * Costs, other costs, ore sales, and the profit they imply.
 *
 * @param state what the form holds.
 * @param draft the form.
 * @param onDraftChanged the form was edited.
 */
@Composable
private fun MoneyBlock(
    state: RefineryCreateState,
    draft: RefineryOrderDraft,
    onDraftChanged: (RefineryOrderDraft) -> Unit,
) {
    // Starts closed: the design says all three fields are usually zero, and a block that is usually
    // empty should not be the first thing between a member and the CTA.
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        KrtOutlineButton(
            text =
                if (open) {
                    stringResource(R.string.refinery_create_money_hide)
                } else {
                    stringResource(R.string.refinery_create_money_show)
                },
            onClick = { open = !open },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!open) {
            return@Column
        }
        NumberField(
            label = stringResource(R.string.refinery_create_expenses),
            value = draft.expenses,
            onValue = { onDraftChanged(draft.copy(expenses = it)) },
        )
        NumberField(
            label = stringResource(R.string.refinery_create_other_expenses),
            value = draft.otherExpenses,
            onValue = { onDraftChanged(draft.copy(otherExpenses = it)) },
        )
        NumberField(
            label = stringResource(R.string.refinery_create_ore_sales),
            value = draft.oreSales,
            onValue = { onDraftChanged(draft.copy(oreSales = it)) },
        )
        Text(
            text =
                stringResource(
                    R.string.refinery_create_profit,
                    formatAmount(state.profit.toString()),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        Text(
            text = stringResource(R.string.refinery_create_profit_help),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * A material picker.
 *
 * Typing clears the pick: a name that no longer matches what was chosen would otherwise send the
 * old id under a new label.
 *
 * @param label what it is.
 * @param shown what to display.
 * @param selectedValue which material is picked.
 * @param materials the candidates.
 * @param onQuery a search was typed.
 * @param onSelect a material was picked.
 * @param onType the text changed without a pick.
 * @param enabled whether it may be edited.
 * @param lockReason what a screen reader is told when it may not.
 */
@Composable
@Suppress("LongParameterList")
private fun MaterialField(
    label: String,
    shown: String,
    selectedValue: String?,
    materials: List<Pair<String, String>>,
    onQuery: (String) -> Unit,
    onSelect: (Pair<String, String>) -> Unit,
    onType: (String) -> Unit,
    enabled: Boolean = true,
    lockReason: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    KrtCombobox(
        query = shown,
        onQueryChange = {
            expanded = true
            onType(it)
            onQuery(it)
        },
        options = materials.map { KrtOption(value = it.first, label = it.second) },
        onSelect = { option ->
            expanded = false
            onSelect(option.value to option.label)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.krtLocked(locked = !enabled, stateLabel = lockReason).fillMaxWidth(),
        label = label,
        selectedValue = selectedValue,
        enabled = enabled,
    )
}

/**
 * A field that takes a figure.
 *
 * @param label what it is.
 * @param value what is in it.
 * @param onValue it was edited.
 * @param modifier layout modifier.
 * @param enabled whether it may be edited.
 * @param lockReason what a screen reader is told when it may not.
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lockReason: String = "",
) {
    KrtTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.krtLocked(locked = !enabled, stateLabel = lockReason).fillMaxWidth(),
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = enabled,
    )
}

/**
 * A field that picks from a list.
 *
 * @param label what it is.
 * @param options what may be picked.
 * @param selectedValue what is picked.
 * @param shown what to display for it.
 * @param onSelect something was picked.
 * @param enabled whether it may be edited.
 * @param lockReason what a screen reader is told when it may not.
 */
@Composable
private fun PickerField(
    label: String,
    options: List<KrtOption>,
    selectedValue: String?,
    shown: String,
    onSelect: (KrtOption) -> Unit,
    enabled: Boolean = true,
    lockReason: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    KrtSelectField(
        value = shown,
        options = options,
        onSelect = {
            expanded = false
            onSelect(it)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.krtLocked(locked = !enabled, stateLabel = lockReason).fillMaxWidth(),
        label = label,
        selectedValue = selectedValue,
        enabled = enabled,
    )
}

/**
 * The create form, bound to its view model.
 *
 * @param viewModel drives it.
 * @param modifier layout modifier.
 */
@Composable
fun RefineryCreateRoute(
    viewModel: RefineryCreateViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefineryCreateScreen(
        state = state,
        actions =
            RefineryCreateActions(
                onDraftChanged = viewModel::onDraftChanged,
                onGoodChanged = viewModel::onGoodChanged,
                onAddGood = viewModel::onAddGood,
                onRemoveGood = viewModel::onRemoveGood,
                onMaterialQuery = viewModel::onMaterialQuery,
                onCreate = viewModel::onCreate,
            ),
        modifier = modifier,
    )
}

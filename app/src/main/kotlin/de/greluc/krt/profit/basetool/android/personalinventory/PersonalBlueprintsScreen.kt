/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BlueprintIngredient
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.KrtListDetail
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the blueprint list. */
const val BLUEPRINTS_LIST_TAG: String = "blueprints-list"

/** Test handle for the "add" action. */
const val BLUEPRINTS_ADD_TAG: String = "blueprints-add"

/**
 * The Blueprints tab of "Mein Inventar" (design ch. 09 § 4).
 *
 * @param state what to draw.
 * @param onQueryChanged the search box changed.
 * @param onRefineryChanged the refining toggle changed.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the next page was asked for.
 * @param onAdd the add action was taken.
 * @param onEdit a row was tapped.
 * @param onDelete a row's remove action was taken.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalBlueprintsScreen(
    state: BlueprintsState,
    onQueryChanged: (String) -> Unit,
    onRefineryChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (OwnedBlueprint) -> Unit,
    onDelete: (OwnedBlueprint) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = isWideWindow()
    KrtListDetail(
        modifier = modifier,
        emptyDetailMessage = stringResource(R.string.blueprints_recipe_none),
        detail =
            if (!wide) {
                null
            } else {
                state.selectedId?.let { id ->
                    {
                        RecipePane(
                            recipe = state.recipe,
                            entry = state.items.firstOrNull { it.id == id },
                            online = state.online,
                            onEdit = onEdit,
                        )
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.online) {
                OfflineBand()
            }
            KrtTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
                placeholder = stringResource(R.string.blueprints_search),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KrtToggle(checked = state.withRefinery, onCheckedChange = onRefineryChanged)
                    Text(
                        text = stringResource(R.string.blueprints_with_refinery),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
                KrtCtaButton(
                    text = stringResource(R.string.blueprints_add),
                    onClick = onAdd,
                    modifier =
                        Modifier
                            .testTag(BLUEPRINTS_ADD_TAG)
                            .alpha(if (state.online) 1f else DISABLED_WRITE_ALPHA),
                    enabled = state.online,
                )
            }

            when (state.phase) {
                is BlueprintsPhase.Loading -> {
                    KrtLoadingIndicator(
                        text = stringResource(R.string.blueprints_title),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is BlueprintsPhase.Failed -> {
                    // A busy server gets the countdown of chapter 14; anything else gets the ordinary
                    // empty state, because a countdown in front of a 403 promises a retry that will
                    // answer exactly the same.
                    val retryIn = state.retryIn
                    if (retryIn != null) {
                        KrtRetryCountdown(
                            secondsLeft = retryIn,
                            title = stringResource(R.string.retry_busy_title),
                            message = stringResource(R.string.retry_busy_message, retryIn),
                            retryLabel = stringResource(R.string.retry_now),
                            onRetry = onRetryNow,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                        )
                    } else {
                        KrtEmptyState(
                            iconRes = DesignR.drawable.ic_krt_blueprint,
                            title = stringResource(R.string.blueprints_error_title),
                            message = stringResource(R.string.blueprints_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                        )
                    }
                }

                is BlueprintsPhase.Ready -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.items.isEmpty()) {
                            KrtRefreshableFill {
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_blueprint,
                                    title = stringResource(R.string.blueprints_empty_title),
                                    message =
                                        stringResource(
                                            if (state.query.isBlank()) {
                                                R.string.blueprints_empty_message
                                            } else {
                                                R.string.blueprints_empty_filtered_message
                                            },
                                        ),
                                    modifier = Modifier.padding(KrtSpacing.lg),
                                )
                            }
                        } else {
                            BlueprintList(
                                state = state,
                                onLoadMore = onLoadMore,
                                onEdit = onEdit,
                                onDelete = onDelete,
                                onSelect = onSelect,
                                selectable = wide,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The rows.
 *
 * @param state what to draw.
 * @param onLoadMore the next page was asked for.
 * @param onEdit a row was tapped.
 * @param onDelete a row's remove action was taken.
 * @param onSelect a row was picked for the detail pane; only used when [selectable].
 * @param selectable whether the window is wide enough for a detail pane to select into.
 */
@Composable
private fun BlueprintList(
    state: BlueprintsState,
    onLoadMore: () -> Unit,
    onEdit: (OwnedBlueprint) -> Unit,
    onDelete: (OwnedBlueprint) -> Unit,
    onSelect: (String) -> Unit,
    selectable: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(BLUEPRINTS_LIST_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        items(state.items, key = { it.id }) { entry ->
            BlueprintRow(
                entry = entry,
                craftability = state.craftability[entry.id],
                withRefinery = state.withRefinery,
                online = state.online,
                onEdit = { onEdit(entry) },
                onDelete = { onDelete(entry) },
                onSelect = { onSelect(entry.id) },
                selectable = selectable,
                selected = entry.id == state.selectedId,
            )
            KrtHairlineRule()
        }
        if (state.hasMore) {
            item(key = "more") {
                LaunchedEffect(state.items.size) { onLoadMore() }
                KrtLoadingIndicator(text = stringResource(R.string.blueprints_title))
            }
        } else {
            item(key = "end") {
                KrtEndOfList(text = stringResource(R.string.blueprints_end_of_list))
            }
        }
    }
}

/**
 * One owned blueprint.
 *
 * The remove action is offered **only when the server says the entry is removable**. Showing it
 * regardless would produce a button that answers 409 — a rule the member cannot see, rendered as a
 * failure.
 *
 * @param entry the row.
 * @param craftability what can be built from it, or `null` when that read has not answered.
 * @param withRefinery whether refining counts.
 * @param online whether writes are possible.
 * @param onEdit opens the note sheet.
 * @param onDelete asks to remove.
 * @param onSelect picks this row for the detail pane.
 * @param selectable whether a detail pane exists to select into.
 * @param selected whether this row is the one the pane is showing.
 */
@Composable
private fun BlueprintRow(
    entry: OwnedBlueprint,
    craftability: Craftability?,
    withRefinery: Boolean,
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    selectable: Boolean,
    selected: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // On a tablet the row picks the recipe shown beside it; on a phone it opens the
                // editor, because the phone has no detail pane for a recipe to appear in. Editing
                // is not lost on the tablet — it moves into the pane, where the row it applies to
                // is the one on screen. Selecting also stays available offline: reading a recipe
                // is not a write.
                .clickable(enabled = selectable || online) {
                    if (selectable) onSelect() else onEdit()
                }
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_ALPHA)
                    } else {
                        // Design ch. 09 artboard 4 draws a blueprint as a bordered tile on the
                        // surface fill, not as a line on the page ground.
                        MaterialTheme.colorScheme.surface
                    },
                )
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.productName,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CraftabilityChip(craftability = craftability, withRefinery = withRefinery)
        if (entry.removable) {
            KrtGhostButton(
                text = stringResource(R.string.blueprints_remove),
                onClick = onDelete,
                modifier = Modifier.alpha(if (online) 1f else DISABLED_WRITE_ALPHA),
                enabled = online,
            )
        }
    }
}

/**
 * Whether this one can be built.
 *
 * Absent while the craftability read has not answered — or has failed. A chip that said "nicht
 * baubar" because a request did not come back would be a claim about the member's stock made out of
 * an outage.
 *
 * @param craftability the entry, or `null`.
 * @param withRefinery whether refining counts.
 */
@Composable
private fun CraftabilityChip(
    craftability: Craftability?,
    withRefinery: Boolean,
) {
    if (craftability == null) {
        return
    }
    if (!craftability.recipeResolved) {
        KrtStatusBadge(
            text = stringResource(R.string.blueprints_recipe_unknown),
            tone = KrtStatusTone.Planned,
        )
        return
    }
    val count = if (withRefinery) craftability.craftableWithRefinery else craftability.craftable
    if (count > 0) {
        KrtStatusBadge(text = stringResource(R.string.blueprints_craftable), tone = KrtStatusTone.Active)
    } else {
        val missing = craftability.missingCount(withRefinery)
        KrtStatusBadge(
            text = pluralStringResource(R.plurals.blueprints_missing, missing, missing),
            tone = KrtStatusTone.Cancelled,
        )
    }
}

/** Tint of the row the detail pane is showing. */
private const val SELECTED_ROW_ALPHA = 0.12f

/**
 * The recipe pane of the tablet's master-detail (design ch. 09).
 *
 * Renders each ingredient with the quality it demands — `minQuality`, the lowest grade that still
 * satisfies the requirement — which is the "live ingredient quality" the chapter names.
 *
 * **Quantities print in the scale the server sent, never converted.** Converting between SCU and
 * units in the client is exactly the mistake that produced the refinery's hundred-fold stock bug,
 * and a recipe is read while standing at a terminal, so a wrong figure here costs real cargo.
 *
 * @param recipe the pane's state.
 * @param entry the selected row, for the heading and the edit action; `null` while the list has
 *   not caught up with the selection.
 * @param online whether writes are possible.
 * @param onEdit opens the editor for the selected blueprint.
 */
@Composable
private fun RecipePane(
    recipe: RecipeState,
    entry: OwnedBlueprint?,
    online: Boolean,
    onEdit: (OwnedBlueprint) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
    ) {
        if (entry != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtSectionTitle(text = entry.productName)
                KrtGhostButton(
                    text = stringResource(R.string.blueprints_edit),
                    onClick = { onEdit(entry) },
                    enabled = online,
                )
            }
        }
        // Idle draws nothing: KrtListDetail already shows its own prompt when nothing is
        // selected, and a second empty state under the heading would say it twice.
        // Idle is unreachable here — the pane is only composed once a row is selected, and
        // selecting sets Loading in the same update — so it is simply not drawn rather than
        // given a branch that does nothing.
        if (recipe is RecipeState.Loading) {
            KrtLoadingIndicator(text = stringResource(R.string.blueprints_recipe_loading))
        }
        if (recipe is RecipeState.Failed) {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_blueprint,
                title = stringResource(R.string.blueprints_recipe_error_title),
                message = stringResource(R.string.blueprints_recipe_error_message),
            )
        }
        if (recipe is RecipeState.Ready) {
            if (recipe.recipe.ingredients.isEmpty()) {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_blueprint,
                    title = stringResource(R.string.blueprints_recipe_empty_title),
                    message = stringResource(R.string.blueprints_recipe_empty_message),
                )
            } else {
                LazyColumn {
                    items(
                        recipe.recipe.ingredients,
                        key = { "${it.groupName}/${it.name}" },
                    ) { ingredient ->
                        IngredientRow(ingredient)
                    }
                }
            }
        }
    }
}

/**
 * One ingredient line: what it is, how much of it, and the quality it has to reach.
 *
 * @param ingredient the ingredient.
 */
@Composable
private fun IngredientRow(ingredient: BlueprintIngredient) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
            ingredient.groupName?.let { group ->
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        ingredient.minQuality?.let { quality ->
            KrtChip(text = stringResource(R.string.blueprints_recipe_quality, quality))
        }
        Text(
            text = amountLabel(ingredient),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

/**
 * The amount, in the scale the server actually sent.
 *
 * @param ingredient the ingredient to label.
 * @return an SCU figure when there is one, otherwise a unit figure, otherwise a dash. Never a
 *   conversion between the two.
 */
@Composable
private fun amountLabel(ingredient: BlueprintIngredient): String {
    val scu = ingredient.quantityScu
    val units = ingredient.quantityUnits
    return when {
        scu != null -> stringResource(R.string.blueprints_recipe_scu, scu)
        units != null -> pluralStringResource(R.plurals.blueprints_recipe_units, units, units)
        else -> stringResource(R.string.hangar_value_unknown)
    }
}

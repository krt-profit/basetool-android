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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToggle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the blueprint list. */
const val BLUEPRINTS_LIST_TAG: String = "blueprints-list"

/** Test handle for the "add" action. */
const val BLUEPRINTS_ADD_TAG: String = "blueprints-add"

/** How faded a write action is while there is no network (design ch. 14). */
private const val DISABLED_WRITE_ALPHA = 0.45f

/**
 * The Blueprints tab of "Mein Inventar" (design ch. 09 § 4).
 *
 * @param state what to draw.
 * @param onQueryChanged the search box changed.
 * @param onRefineryChanged the refining toggle changed.
 * @param onRefresh pull-to-refresh.
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
    onLoadMore: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (OwnedBlueprint) -> Unit,
    onDelete: (OwnedBlueprint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!state.online) {
            Text(
                text = stringResource(R.string.offline_writes_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
            )
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
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_blueprint,
                    title = stringResource(R.string.blueprints_error_title),
                    message = stringResource(R.string.blueprints_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
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
                        )
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
 */
@Composable
private fun BlueprintList(
    state: BlueprintsState,
    onLoadMore: () -> Unit,
    onEdit: (OwnedBlueprint) -> Unit,
    onDelete: (OwnedBlueprint) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(BLUEPRINTS_LIST_TAG)) {
        items(state.items, key = { it.id }) { entry ->
            BlueprintRow(
                entry = entry,
                craftability = state.craftability[entry.id],
                withRefinery = state.withRefinery,
                online = state.online,
                onEdit = { onEdit(entry) },
                onDelete = { onDelete(entry) },
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
                Text(
                    text = stringResource(R.string.blueprints_end_of_list),
                    style = MaterialTheme.typography.labelSmall,
                    color = KrtPalette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.md),
                )
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
 */
@Composable
private fun BlueprintRow(
    entry: OwnedBlueprint,
    craftability: Craftability?,
    withRefinery: Boolean,
    online: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = online, onClick = onEdit)
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

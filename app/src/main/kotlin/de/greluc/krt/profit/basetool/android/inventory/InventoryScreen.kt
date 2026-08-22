/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.InventoryGroup
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the tree. */
const val INVENTORY_TREE_TAG: String = "inventory-tree"

/** Width of the orange rail that marks a material group (design ch. 09). */
private val GROUP_RAIL = 4.dp

/** Width of the grey rail that marks a stack beneath it. */
private val STACK_RAIL = 2.dp

/** How far a stack is inset from its group. */
private val STACK_INSET = 16.dp

/** Height of a rail segment, matching a two-line row. */
private val RAIL_HEIGHT = 44.dp

/**
 * The Lager tree (design spec ch. 09 §1), read-only.
 *
 * Two levels: a **material group** with an orange rail, and the **stacks** inside it with a grey
 * one. The design's third level — the individual entry — is not drawn: it is where booking happens,
 * and booking is Phase 3.
 *
 * @param state what to draw.
 * @param onToggleGroup a group row was tapped.
 * @param onWithStockOnlyChanged the "Nur mit Bestand" chip was tapped.
 * @param onRefresh pull-to-refresh.
 * @param onLoadMore the load-more control was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onToggleGroup: (String) -> Unit,
    onWithStockOnlyChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md)) {
            KrtFilterChip(
                text = stringResource(R.string.inventory_with_stock_only),
                selected = state.withStockOnly,
                onClick = { onWithStockOnlyChanged(!state.withStockOnly) },
            )
        }

        when (state.phase) {
            is InventoryPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.inventory_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is InventoryPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_crate,
                    title = stringResource(R.string.inventory_error_title),
                    message = stringResource(R.string.inventory_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            }

            is InventoryPhase.Ready -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.visibleGroups.isEmpty()) {
                        InventoryEmpty(filtered = state.withStockOnly && state.groups.isNotEmpty())
                    } else {
                        InventoryTree(
                            state = state,
                            onToggleGroup = onToggleGroup,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The tree itself.
 *
 * @param state what to draw.
 * @param onToggleGroup a group was tapped.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun InventoryTree(
    state: InventoryState,
    onToggleGroup: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(INVENTORY_TREE_TAG)) {
        state.visibleGroups.forEach { group ->
            val materialId = group.materialId
            item(key = "group-${materialId ?: group.name}") {
                GroupRow(
                    group = group,
                    // A group the server sent without a material id cannot be asked for, so it does
                    // not offer a tap that would do nothing.
                    onClick = materialId?.let { { onToggleGroup(it) } },
                )
            }
            // A group nobody opened contributes nothing, which is the point of loading one only
            // when it is asked for — so the whole block is skipped rather than branching on null.
            val opened = materialId?.let { state.opened[it] }
            if (opened != null) {
                when (opened) {
                    is StackPhase.Loading -> {
                        item(key = "stacks-loading-$materialId") {
                            StackNote(text = stringResource(R.string.inventory_title))
                        }
                    }

                    is StackPhase.Failed -> {
                        item(key = "stacks-failed-$materialId") {
                            StackNote(text = stringResource(R.string.inventory_stacks_failed))
                        }
                    }

                    is StackPhase.Ready -> {
                        if (opened.stacks.isEmpty()) {
                            item(key = "stacks-empty-$materialId") {
                                StackNote(text = stringResource(R.string.inventory_stacks_empty))
                            }
                        } else {
                            opened.stacks.forEachIndexed { index, stack ->
                                item(key = "stack-$materialId-$index") {
                                    StackRow(stack = stack, unit = group.unit)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text =
                        pluralStringResource(
                            R.plurals.inventory_group_count,
                            state.total.toInt(),
                            state.groups.size,
                            state.total,
                        ),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.inventory_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * One material group.
 *
 * @param group the group.
 * @param onClick opens or closes it, or `null` when it cannot be opened.
 */
@Composable
private fun GroupRow(
    group: InventoryGroup,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(end = KrtSpacing.md, top = KrtSpacing.sm, bottom = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(width = GROUP_RAIL, color = MaterialTheme.colorScheme.primary)
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        group.quality?.let { KrtChip(text = stringResource(R.string.inventory_quality, formatAmount(it))) }
        Amount(value = group.amount, unit = group.unit)
    }
}

/**
 * One stack inside a group.
 *
 * @param stack the stack.
 * @param unit the group's quantity unit, since a stack carries none of its own.
 */
@Composable
private fun StackRow(
    stack: InventoryStack,
    unit: String?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = STACK_INSET, end = KrtSpacing.md, top = KrtSpacing.xs, bottom = KrtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(width = STACK_RAIL, color = KrtPalette.Gray3)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stack.title(),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.inventory_entry_count,
                        stack.entryCount,
                        stack.entryCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (stack.personal) {
            KrtChip(text = stringResource(R.string.inventory_personal), tone = KrtChipTone.Muted)
        }
        stack.quality?.let { KrtChip(text = stringResource(R.string.inventory_quality, formatAmount(it))) }
        Amount(value = stack.amount, unit = unit)
    }
}

/**
 * The stack's headline.
 *
 * @return the holder and the place, whichever of them the server attributed.
 */
private fun InventoryStack.title(): String =
    listOfNotNull(holder?.takeIf { it.isNotBlank() }, location?.takeIf { it.isNotBlank() })
        .joinToString(" · ")

/**
 * A quantity with its unit dimmed behind it, as the design has it.
 *
 * @param value the amount, unformatted.
 * @param unit the unit, or `null` when the server named none.
 */
@Composable
private fun Amount(
    value: String?,
    unit: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs), verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatAmount(value.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
        unit?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The depth rail.
 *
 * @param width how wide it is; the design uses 4 dp for a group and 2 dp for a stack.
 * @param color its colour.
 */
@Composable
private fun Rail(
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
) {
    Box(modifier = Modifier.width(width).height(RAIL_HEIGHT).background(color))
}

/**
 * A muted line standing in for a group's stacks.
 *
 * @param text what to say.
 */
@Composable
private fun StackNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier =
            Modifier.padding(
                start = STACK_INSET,
                end = KrtSpacing.md,
                top = KrtSpacing.xs,
                bottom = KrtSpacing.xs,
            ),
    )
}

/**
 * The empty state, which differs by whether the chip is what emptied it.
 *
 * @param filtered whether "Nur mit Bestand" hid everything on this page.
 */
@Composable
private fun InventoryEmpty(filtered: Boolean) {
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_crate,
        title =
            stringResource(
                if (filtered) R.string.inventory_empty_filtered_title else R.string.inventory_empty_title,
            ),
        message =
            stringResource(
                if (filtered) {
                    R.string.inventory_empty_filtered_message
                } else {
                    R.string.inventory_empty_message
                },
            ),
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
    )
}

/**
 * The Lager, bound to its view model.
 *
 * @param viewModel drives the tree.
 * @param modifier layout modifier.
 */
@Composable
fun InventoryRoute(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InventoryScreen(
        state = state,
        onToggleGroup = viewModel::onToggleGroup,
        onWithStockOnlyChanged = viewModel::onWithStockOnlyChanged,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        modifier = modifier,
    )
}

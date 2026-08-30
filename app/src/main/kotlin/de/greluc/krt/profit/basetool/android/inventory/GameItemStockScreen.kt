/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.GameItemStock
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the list. */
const val GAME_ITEM_LIST_TAG: String = "game-item-list"

/** Test handle for its search field. */
const val GAME_ITEM_SEARCH_TAG: String = "game-item-search"

/**
 * „Game-Items" — design ch. 09 artboard 21.
 *
 * A surface of its own because the Lager tree groups by **material**, where an item counted in
 * pieces disappears between SCU figures. The question here is „how many do we have and where", and
 * the unit is always pieces — which is why no row spells it out twice.
 *
 * Read-only. No price column: game items have no UEX price.
 *
 * > **The artboard's jump into the Lager tree is not reachable.** It asks for a row to open the
 * > item in the tree on its path; the app's tree is material-only and has no `catalog=ITEM` mode,
 * > so there is nowhere to jump to. A row therefore opens **in place** and shows the stacks that
 * > came with it — the same holders and places, one tap earlier. On the design gap list.
 *
 * @param state what to draw.
 * @param onQueryChanged the search changed.
 * @param onKindChanged a category chip was tapped.
 * @param onToggle a row was tapped.
 * @param onRetry the failure's retry.
 * @param modifier layout modifier.
 */
@Composable
@Suppress("LongParameterList")
fun GameItemStockScreen(
    state: GameItemStockState,
    onQueryChanged: (String) -> Unit,
    onKindChanged: (String?) -> Unit,
    onToggle: (GameItemStock) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        KrtTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = stringResource(R.string.game_items_search),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(KrtSpacing.md)
                    .testTag(GAME_ITEM_SEARCH_TAG),
        )
        if (state.kinds.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = KrtSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            ) {
                KrtFilterChip(
                    text = stringResource(R.string.game_items_filter_all),
                    selected = state.kind == null,
                    onClick = { onKindChanged(null) },
                )
                // Built from the values that turned up: `kind` is free text on the wire, so a fixed
                // set of chips would quietly hide whatever the catalogue grows next.
                state.kinds.forEach { kind ->
                    KrtFilterChip(
                        text = kind,
                        selected = state.kind == kind,
                        onClick = { onKindChanged(kind) },
                    )
                }
            }
        }
        when (state.phase) {
            is GameItemPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.game_items_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is GameItemPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_crate,
                    title = stringResource(R.string.game_items_failed_title),
                    message = stringResource(R.string.game_items_failed_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRetry,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            }

            is GameItemPhase.Ready -> {
                StockList(state = state, onToggle = onToggle)
            }
        }
    }
}

/**
 * The rows.
 *
 * @param state what to draw.
 * @param onToggle a row was tapped.
 */
@Composable
private fun StockList(
    state: GameItemStockState,
    onToggle: (GameItemStock) -> Unit,
) {
    val rows = state.visible
    if (rows.isEmpty()) {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_crate,
            title = stringResource(R.string.game_items_empty_title),
            message =
                stringResource(
                    if (state.items.isEmpty()) {
                        R.string.game_items_empty_message
                    } else {
                        R.string.game_items_empty_filtered
                    },
                ),
            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(GAME_ITEM_LIST_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        item(key = "count") {
            // „27 Items · 412 Stück" — of what is shown, which after a filter is the honest figure.
            Text(
                text =
                    pluralStringResource(R.plurals.game_items_count, rows.size, rows.size) +
                        SEPARATOR +
                        pluralStringResource(
                            R.plurals.game_items_pieces,
                            state.totalAmount.toInt(),
                            state.totalAmount.toInt(),
                        ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        items(rows, key = { it.id }) { item ->
            StockRow(
                item = item,
                expanded = state.expanded == item.id,
                onToggle = { onToggle(item) },
            )
        }
    }
}

/**
 * One item: what it is, who has it and where, and how many.
 *
 * @param item the row.
 * @param expanded whether its places are open.
 * @param onToggle it was tapped.
 */
@Composable
private fun StockRow(
    item: GameItemStock,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onToggle) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.secondLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = item.amount.toInt().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                )
                Text(
                    text = stringResource(R.string.game_items_unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        if (expanded && item.locations.isNotEmpty()) {
            KrtHairlineRule()
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                item.locations.forEach { place ->
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        }
    }
}

/** Between the two halves of a count line. */
private const val SEPARATOR = " · "

/**
 * „4 Halter · 3 Orte", or the place itself when there is only one.
 *
 * Naming the single place is worth the branch: „1 Ort" answers nothing, and the whole point of the
 * screen is *where*.
 *
 * @receiver the row.
 * @return the line.
 */
@Composable
private fun GameItemStock.secondLine(): String {
    val holders = pluralStringResource(R.plurals.game_items_holders, holders, holders)
    val where =
        when (locations.size) {
            0 -> null
            1 -> locations.first()
            else -> pluralStringResource(R.plurals.game_items_places, locations.size, locations.size)
        }
    return listOfNotNull(holders, where).joinToString(SEPARATOR)
}

/**
 * The screen, bound to its view model.
 *
 * @param viewModel drives it.
 * @param modifier layout modifier.
 */
@Composable
fun GameItemStockRoute(
    viewModel: GameItemStockViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }
    GameItemStockScreen(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onKindChanged = viewModel::onKindChanged,
        onToggle = viewModel::onToggleExpanded,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

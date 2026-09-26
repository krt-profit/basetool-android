/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MATERIAL_CATEGORY_UNSORTED
import de.greluc.krt.profit.basetool.android.core.data.MaterialPriceRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the material list. */
const val MATERIALS_LIST_TAG: String = "materials-list"

/** Test handle for the search field. */
const val MATERIALS_SEARCH_TAG: String = "materials-search"

/**
 * „Handel": the read-only material catalogue with its UEX prices.
 *
 * Each row's subtitle is the material's category.
 *
 * @param state what to draw.
 * @param actions what the filters report back.
 * @param onOpen a row was tapped.
 * @param onOpenMatrix open the Preis-Übersicht.
 * @param onOpenProfit open the Profitberechnung.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    state: MaterialsState,
    actions: MaterialsActions,
    onOpen: (String) -> Unit,
    onOpenMatrix: () -> Unit,
    onOpenProfit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialsOverflow(onOpenMatrix = onOpenMatrix, onOpenProfit = onOpenProfit)
    when (state.phase) {
        is MaterialsPhase.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.materials_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is MaterialsPhase.Failed -> {
            MaterialsFailure(state = state, actions = actions, modifier = modifier)
        }

        is MaterialsPhase.Ready -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!state.online) {
                        OfflineBand()
                    }
                    MaterialFilters(state = state, actions = actions)
                    MaterialRows(state = state, onOpen = onOpen)
                }
            }
        }
    }
}

/**
 * The two sibling trade surfaces, in the top bar's own menu.
 *
 * @param onOpenMatrix open the Preis-Übersicht.
 * @param onOpenProfit open the Profitberechnung.
 */
@Composable
private fun MaterialsOverflow(
    onOpenMatrix: () -> Unit,
    onOpenProfit: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val matrix = stringResource(R.string.materials_matrix_title)
    val profit = stringResource(R.string.materials_profit_title)
    val label = stringResource(R.string.materials_actions)
    ProvideScreenTopBar(
        actions = {
            KrtOverflowMenu(
                contentDescription = label,
                expanded = open,
                onExpandedChange = { open = it },
                items =
                    listOf(
                        KrtMenuItem(label = matrix, iconRes = DesignR.drawable.ic_krt_list) {
                            open = false
                            onOpenMatrix()
                        },
                        KrtMenuItem(label = profit, iconRes = DesignR.drawable.ic_krt_ship) {
                            open = false
                            onOpenProfit()
                        },
                    ),
            )
        },
    )
}

/**
 * The failure state, with chapter 14's countdown where the server asked to be asked again.
 *
 * @param state what to draw.
 * @param actions the retry.
 * @param modifier layout modifier.
 */
@Composable
private fun MaterialsFailure(
    state: MaterialsState,
    actions: MaterialsActions,
    modifier: Modifier = Modifier,
) {
    val retryIn = state.retryIn
    if (retryIn != null) {
        KrtRetryCountdown(
            secondsLeft = retryIn,
            title = stringResource(R.string.retry_busy_title),
            message = stringResource(R.string.retry_busy_message, retryIn),
            retryLabel = stringResource(R.string.retry_now),
            onRetry = actions.onRetryNow,
            modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
        )
    } else {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_list,
            title = stringResource(R.string.materials_error_title),
            message = stringResource(R.string.materials_error_message),
            actionText = stringResource(R.string.missions_retry),
            onAction = actions.onRefresh,
            modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
        )
    }
}

/**
 * The filter band: search, two number fields for the price bounds, and the category chips.
 *
 * @param state what is filtered.
 * @param actions what the fields report.
 */
@Composable
private fun MaterialFilters(
    state: MaterialsState,
    actions: MaterialsActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        KrtTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            modifier = Modifier.fillMaxWidth().testTag(MATERIALS_SEARCH_TAG),
            placeholder = stringResource(R.string.materials_search_placeholder),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            KrtTextField(
                value = state.minBuy,
                onValueChange = actions.onMinBuy,
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.materials_min_buy),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                tabularFigures = true,
            )
            KrtTextField(
                value = state.maxSell,
                onValueChange = actions.onMaxSell,
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.materials_max_sell),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                tabularFigures = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtFilterChip(
                text = stringResource(R.string.materials_category_all),
                selected = state.category == null,
                onClick = { actions.onCategory(null) },
            )
            state.categories.forEach { category ->
                KrtFilterChip(
                    text = category,
                    selected = state.category == category,
                    onClick = { actions.onCategory(category) },
                )
            }
        }
        if (state.filtered) {
            KrtGhostButton(
                text = stringResource(R.string.materials_reset_filters),
                onClick = actions.onResetFilters,
                modifier = Modifier.fillMaxWidth(),
                iconRes = DesignR.drawable.ic_krt_reset,
            )
        }
        Text(
            text =
                pluralStringResource(
                    R.plurals.materials_count,
                    state.visible.size,
                    state.visible.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The list itself, or the design's own „Keine Materialien gefunden.".
 *
 * @param state what to draw.
 * @param onOpen a row was tapped.
 */
@Composable
private fun MaterialRows(
    state: MaterialsState,
    onOpen: (String) -> Unit,
) {
    val rows = state.visible
    if (rows.isEmpty()) {
        KrtRefreshableFill {
            KrtEmptyState(
                iconRes = DesignR.drawable.ic_krt_list,
                title = stringResource(R.string.materials_empty_title),
                message = stringResource(R.string.materials_empty_message),
                modifier = Modifier.padding(KrtSpacing.s16),
            )
        }
        return
    }
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(MATERIALS_LIST_TAG),
        contentPadding =
            PaddingValues(
                horizontal = contentGutter().coerceAtLeast(KrtSpacing.s12),
                vertical = KrtSpacing.s8,
            ),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        items(rows, key = { it.id }) { row ->
            MaterialRow(row = row, onClick = { onOpen(row.id) })
        }
    }
}

/**
 * One material: its name, its category, and its sell price above its buy price.
 *
 * @param row the material.
 * @param onClick open its price page.
 */
@Composable
private fun MaterialRow(
    row: MaterialPriceRow,
    onClick: () -> Unit,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.category ?: MATERIAL_CATEGORY_UNSORTED,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.illegal) {
                KrtChip(text = stringResource(R.string.materials_illegal), tone = KrtChipTone.Danger)
            }
            Column(horizontalAlignment = Alignment.End) {
                PriceLine(
                    text = row.maxPriceSell?.let { stringResource(R.string.materials_price_sell, it.toPlainString()) },
                    color = KrtPalette.SuccessText,
                )
                PriceLine(
                    text = row.minPriceBuy?.let { stringResource(R.string.materials_price_buy, it.toPlainString()) },
                    color = KrtPalette.DangerText,
                )
            }
            KrtIcon(
                id = DesignR.drawable.ic_krt_chevron_right,
                contentDescription = null,
                tint = KrtPalette.Gray2,
            )
        }
    }
}

/**
 * One price, or an em dash when the server sent none; never `0,00`.
 *
 * @param text the rendered price, or `null` when the server sent none.
 * @param color the side's colour.
 */
@Composable
private fun PriceLine(
    text: String?,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text ?: stringResource(R.string.krt_empty_value),
        style = MaterialTheme.typography.bodyMedium,
        color = if (text == null) KrtPalette.TextMuted else color,
        textAlign = TextAlign.End,
    )
}

/**
 * Everything the Material-Übersicht reports back.
 *
 * @property onQuery the search changed.
 * @property onCategory a category chip was tapped, or „Alle".
 * @property onMinBuy the „Min. Einkaufspreis" bound changed.
 * @property onMaxSell the „Max. Verkaufspreis" bound changed.
 * @property onResetFilters every narrowing off at once.
 * @property onRefresh pull-to-refresh.
 * @property onRetryNow the member pressed the manual retry.
 */
data class MaterialsActions(
    val onQuery: (String) -> Unit,
    val onCategory: (String?) -> Unit,
    val onMinBuy: (String) -> Unit,
    val onMaxSell: (String) -> Unit,
    val onResetFilters: () -> Unit,
    val onRefresh: () -> Unit,
    val onRetryNow: () -> Unit,
)

/**
 * „Handel", bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param onOpen a row was tapped.
 * @param onOpenMatrix open the Preis-Übersicht.
 * @param onOpenProfit open the Profitberechnung.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialsRoute(
    viewModel: MaterialsViewModel,
    onOpen: (String) -> Unit,
    onOpenMatrix: () -> Unit,
    onOpenProfit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialsScreen(
        state = state,
        actions =
            MaterialsActions(
                onQuery = viewModel::onQuery,
                onCategory = viewModel::onCategory,
                onMinBuy = viewModel::onMinBuy,
                onMaxSell = viewModel::onMaxSell,
                onResetFilters = viewModel::onResetFilters,
                onRefresh = viewModel::onRefresh,
                onRetryNow = viewModel::onRetry,
            ),
        onOpen = onOpen,
        onOpenMatrix = onOpenMatrix,
        onOpenProfit = onOpenProfit,
        modifier = modifier,
    )
}

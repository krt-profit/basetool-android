/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MATERIAL_CATEGORY_UNSORTED
import de.greluc.krt.profit.basetool.android.core.data.MaterialSummary
import de.greluc.krt.profit.basetool.android.core.data.MaterialTerminalPrice
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTable
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableCell
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableColumn
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the price table. */
const val MATERIAL_PRICES_TAG: String = "material-prices"

/** The two price columns of the table, after the terminal name. */
private const val PRICE_COLUMNS = 3

/**
 * „Preise und Terminals" — one material's market (design spec ch. 16, artboard 2).
 *
 * > **This table stays a table on the phone**, which chapter 16 calls out as a deliberate exception
 * > to the design system's „a table is the tablet shape" rule: comparing prices *is* reading down a
 * > column, and a stack of cards makes that impossible. The way it stays inside the rules is that
 * > the column headings shorten on a narrow window rather than the page scrolling sideways.
 *
 * @param state what to draw.
 * @param onFilter the terminal search changed.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(
    state: MaterialDetailState,
    onFilter: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        is MaterialsPhase.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.materials_detail_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is MaterialsPhase.Failed -> {
            DetailFailure(state = state, onRefresh = onRefresh, onRetryNow = onRetryNow, modifier = modifier)
        }

        is MaterialsPhase.Ready -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                DetailBody(state = state, onFilter = onFilter)
            }
        }
    }
}

/**
 * The failure state.
 *
 * A 404 here is the design's „Material nicht gefunden" and is chapter 14's own picture, because a
 * material that does not exist is a signal-lost, not an empty price list.
 *
 * @param state what to draw.
 * @param onRefresh ask again.
 * @param onRetryNow the manual retry of the countdown.
 * @param modifier layout modifier.
 */
@Composable
private fun DetailFailure(
    state: MaterialDetailState,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryIn = state.retryIn
    if (retryIn != null) {
        KrtRetryCountdown(
            secondsLeft = retryIn,
            title = stringResource(R.string.retry_busy_title),
            message = stringResource(R.string.retry_busy_message, retryIn),
            retryLabel = stringResource(R.string.retry_now),
            onRetry = onRetryNow,
            modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
        )
    } else {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_list,
            title = stringResource(R.string.materials_detail_missing_title),
            message = stringResource(R.string.materials_detail_missing_message),
            actionText = stringResource(R.string.missions_retry),
            onAction = onRefresh,
            modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
        )
    }
}

/**
 * The head, the two best-price tiles, the terminal filter and the table.
 *
 * @param state what to draw.
 * @param onFilter the terminal search changed.
 */
@Composable
private fun DetailBody(
    state: MaterialDetailState,
    onFilter: (String) -> Unit,
) {
    val material = state.material
    ProvideScreenTopBar(
        title = material?.name ?: stringResource(R.string.materials_detail_title),
        subtitle = {
            material?.let {
                Text(
                    text = it.krtSubtitle(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                )
            }
        },
    )
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(MATERIAL_PRICES_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter(), vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        if (!state.online) {
            item(key = "offline") { OfflineBand() }
        }
        item(key = "best") { BestPrices(state = state) }
        item(key = "filter") {
            KrtTextField(
                value = state.filter,
                onValueChange = onFilter,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.materials_detail_filter_placeholder),
            )
        }
        item(key = "title") {
            KrtSectionTitle(
                text = stringResource(R.string.materials_detail_prices_title),
                trailing = {
                    Text(
                        text = state.visible.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                },
            )
        }
        item(key = "table") { PriceTable(state = state) }
    }
}

/**
 * The two figures the whole page exists to answer: where it sells dearest and buys cheapest.
 *
 * The same two words the list's filters use — „Max. Verkaufspreis" and „Min. Einkaufspreis" — so a
 * member who narrowed the list by one of them meets the same term here.
 *
 * @param state what to draw.
 */
@Composable
private fun BestPrices(state: MaterialDetailState) {
    val sell = state.bestSell
    val buy = state.bestBuy
    if (sell == null && buy == null) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            sell?.let {
                KrtFigureTile(
                    label = stringResource(R.string.materials_max_sell),
                    value = stringResource(R.string.materials_price_sell, it.price.toPlainString()),
                    tone = KrtFigureTone.Success,
                    modifier = Modifier.weight(1f),
                )
            }
            buy?.let {
                KrtFigureTile(
                    label = stringResource(R.string.materials_min_buy),
                    value = stringResource(R.string.materials_price_buy, it.price.toPlainString()),
                    tone = KrtFigureTone.Neutral,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            // The place goes under the figure, never inside the tile: the tile draws one number,
            // and a terminal name in it would read as part of that number.
            sell?.let { Place(name = it.terminal) }
            buy?.let { Place(name = it.terminal) }
        }
    }
}

/**
 * Where one of the two best prices is.
 *
 * @param name the terminal.
 */
@Composable
private fun RowScope.Place(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.weight(1f),
    )
}

/**
 * Terminal · Einkaufspreis · Verkaufspreis, or the design's own two empty sentences.
 *
 * „Keine Preisdaten verfügbar." means the material has no prices at all; „Keine Terminals
 * gefunden." means the filter matched none. They are different facts and the screen says which.
 *
 * @param state what to draw.
 */
@Composable
private fun PriceTable(state: MaterialDetailState) {
    if (state.prices.isEmpty()) {
        Text(
            text = stringResource(R.string.materials_detail_no_prices),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    val rows = state.visible
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.materials_detail_no_terminals),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    // Chapter 16: the headings shorten on a narrow window; the rule against sideways scrolling is
    // kept, and the table stays a table.
    val wide = isWideWindow()
    val columns =
        listOf(
            KrtTableColumn(title = stringResource(R.string.materials_detail_th_terminal), weight = 1.6f),
            KrtTableColumn(
                title =
                    stringResource(
                        if (wide) R.string.materials_detail_th_buy else R.string.materials_detail_th_buy_short,
                    ),
                weight = 1f,
                numeric = true,
            ),
            KrtTableColumn(
                title =
                    stringResource(
                        if (wide) R.string.materials_detail_th_sell else R.string.materials_detail_th_sell_short,
                    ),
                weight = 1f,
                numeric = true,
            ),
        )
    KrtTable(columns = columns, rowCount = rows.size, modifier = Modifier.fillMaxWidth()) { row, column ->
        val price = rows[row]
        val text =
            when (column) {
                0 -> price.terminal
                1 -> price.priceBuy?.toPlainString()
                else -> price.priceSell?.toPlainString()
            }
        KrtTableCell(
            text = text ?: stringResource(R.string.krt_empty_value),
            column = columns[column],
            // The best row on each side is the answer the page came for; the tint marks it where
            // it stands rather than making the reader compare a column of equals.
            emphasis = column in 1 until PRICE_COLUMNS && text != null && price.krtIsBest(state, column),
        )
    }
}

/**
 * Whether this row holds the best price in its column.
 *
 * @receiver the price row.
 * @param state the page, for the two best rows it already computed.
 * @param column `1` for the buy side, `2` for the sell side.
 * @return whether to emphasise it.
 */
private fun MaterialTerminalPrice.krtIsBest(
    state: MaterialDetailState,
    column: Int,
): Boolean =
    if (column == 1) {
        priceBuy != null && priceBuy == state.bestBuy?.price
    } else {
        priceSell != null && priceSell == state.bestSell?.price
    }

/**
 * „Veredelt · SCU", where the server said so.
 *
 * Unlike the list row this page **has** the type and the unit — `/materials/{id}` answers with the
 * whole record — so the artboard's subtitle is drawn here in full.
 *
 * @receiver the material.
 * @return the subtitle.
 */
@Composable
private fun MaterialSummary.krtSubtitle(): String {
    val kind =
        when (type) {
            "RAW" -> stringResource(R.string.materials_type_raw)
            "REFINED" -> stringResource(R.string.materials_type_refined)
            else -> category ?: MATERIAL_CATEGORY_UNSORTED
        }
    val measure =
        when (unit) {
            "PIECE" -> stringResource(R.string.materials_unit_piece)
            "SCU" -> stringResource(R.string.materials_unit_scu)
            else -> null
        }
    return listOfNotNull(kind, measure).joinToString(" · ")
}

/**
 * One material's price page, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialDetailRoute(
    viewModel: MaterialDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialDetailScreen(
        state = state,
        onFilter = viewModel::onFilter,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        modifier = modifier,
    )
}

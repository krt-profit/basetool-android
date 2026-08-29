/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the matrix. */
const val MATERIAL_MATRIX_TAG: String = "material-matrix"

/** Width of the sticky material column — design ch. 16 artboard 3 measures it at 104 dp. */
private val MATERIAL_COLUMN = 104.dp

/** Width of one terminal column. A price plus its sign needs this much at tabular figures. */
private val TERMINAL_COLUMN = 92.dp

/**
 * „Preis-Übersicht" — the Material × Terminal matrix (design spec ch. 16, artboard 3).
 *
 * > **The one surface in this app that scrolls sideways**, and it does so by the design's own
 * > instruction: comparing a material across terminals *is* reading along a row. The material
 * > column stays put while the terminals scroll under it, which is what keeps a horizontal scroll
 * > from losing the reader.
 *
 * The matrix is drawn **as it arrives**, with the loading line at the foot — chapter 16 rules out a
 * full-screen spinner by name.
 *
 * @param state what to draw.
 * @param actions what the filters report.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialMatrixScreen(
    state: MaterialMatrixState,
    actions: MatrixActions,
    modifier: Modifier = Modifier,
) {
    ProvideScreenTopBar(title = stringResource(R.string.materials_matrix_title))
    Column(modifier = modifier.fillMaxSize()) {
        MatrixFilters(state = state, actions = actions)
        MatrixBody(state = state, actions = actions)
    }
}

/**
 * The search, the mode switch and the star-system chips.
 *
 * @param state what is filtered.
 * @param actions what the controls report.
 */
@Composable
private fun MatrixFilters(
    state: MaterialMatrixState,
    actions: MatrixActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.materials_search_placeholder),
        )
        // One switch, not two figures per cell: a cell carrying both would need twice the width and
        // stop being scannable, which is the artboard's own reasoning.
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.materials_matrix_sell),
                    stringResource(R.string.materials_matrix_buy),
                ),
            selectedIndex = if (state.mode == MatrixMode.SELL) 0 else 1,
            onSelect = { actions.onMode(if (it == 0) MatrixMode.SELL else MatrixMode.BUY) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.systems.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KrtFilterChip(
                    text = stringResource(R.string.materials_matrix_systems_all),
                    selected = state.system == null,
                    onClick = { actions.onSystem(null) },
                )
                state.systems.forEach { system ->
                    KrtFilterChip(
                        text = system,
                        selected = state.system == system,
                        onClick = { actions.onSystem(system) },
                    )
                }
            }
        }
    }
}

/**
 * The table itself, the loading line, or the design's own „Keine Einträge gefunden.".
 *
 * @param state what to draw.
 * @param actions the retry.
 */
@Composable
private fun MatrixBody(
    state: MaterialMatrixState,
    actions: MatrixActions,
) {
    val rows = state.rows
    if (rows.isEmpty() && !state.loading) {
        MatrixEmpty(state = state, actions = actions)
        return
    }
    val columns = state.columns
    // ONE scroll state for the header and every row, which is what makes the header stay over its
    // own column while the body moves. Two would drift apart on the first fling.
    val horizontal = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        MatrixHeader(columns = columns, scroll = horizontal)
        KrtHairlineRule()
        LazyColumn(
            state = rememberRootListState(),
            modifier = Modifier.fillMaxSize().testTag(MATERIAL_MATRIX_TAG),
            contentPadding = PaddingValues(bottom = KrtSpacing.lg),
        ) {
            items(rows, key = { it.materialId }) { row ->
                MatrixBodyRow(row = row, columns = columns, mode = state.mode, scroll = horizontal)
                KrtHairlineRule()
            }
            if (state.loading) {
                item(key = "loading") {
                    Text(
                        text = stringResource(R.string.materials_matrix_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                        modifier = Modifier.padding(KrtSpacing.md),
                    )
                }
            }
        }
    }
}

/**
 * What to say when the matrix is empty — and it matters which emptiness it is.
 *
 * A failed read is not „nothing matches your filter", and telling somebody the second when the
 * first happened sends them to change a filter that was never the problem.
 *
 * @param state what to draw.
 * @param actions the retry.
 */
@Composable
private fun MatrixEmpty(
    state: MaterialMatrixState,
    actions: MatrixActions,
) {
    if (state.error != null) {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_list,
            title = stringResource(R.string.materials_error_title),
            message = stringResource(R.string.materials_matrix_error_message),
            actionText = stringResource(R.string.missions_retry),
            onAction = actions.onRetry,
            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
        )
        return
    }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_list,
        title = stringResource(R.string.materials_matrix_empty_title),
        message = stringResource(R.string.materials_empty_message),
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
    )
}

/**
 * The header: „Material" over the sticky column, then one terminal per column.
 *
 * @param columns the terminals.
 * @param scroll the shared horizontal scroll.
 */
@Composable
private fun MatrixHeader(
    columns: List<MatrixColumn>,
    scroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.SurfaceInput)
                .defaultMinSize(minHeight = KrtSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(text = stringResource(R.string.materials_matrix_material), width = MATERIAL_COLUMN)
        Row(modifier = Modifier.horizontalScroll(scroll)) {
            columns.forEach { column ->
                HeaderCell(text = column.name, width = TERMINAL_COLUMN, align = TextAlign.End)
            }
        }
    }
}

/**
 * One material's row: its name, then its price at every terminal.
 *
 * @param row the material.
 * @param columns the terminals, in the header's order.
 * @param mode which side is showing.
 * @param scroll the shared horizontal scroll.
 */
@Composable
private fun MatrixBodyRow(
    row: MatrixRow,
    columns: List<MatrixColumn>,
    mode: MatrixMode,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val best = row.best(mode)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = KrtSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(MATERIAL_COLUMN).padding(horizontal = KrtSpacing.sm),
        )
        Row(modifier = Modifier.horizontalScroll(scroll)) {
            columns.forEach { column ->
                val price = row.prices[column.id]
                Text(
                    text = price?.toPlainString() ?: stringResource(R.string.krt_empty_value),
                    style = MaterialTheme.typography.bodySmall,
                    // The best value of the row is tinted, never bolded and never given a second
                    // hue — the artboard is explicit about that, and a table where one cell shouts
                    // stops being a table.
                    color =
                        when {
                            price == null -> KrtPalette.TextMuted
                            price == best -> KrtPalette.SuccessText
                            else -> KrtPalette.White
                        },
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(TERMINAL_COLUMN).padding(horizontal = KrtSpacing.sm),
                )
            }
        }
    }
}

/**
 * One header cell.
 *
 * @param text what it says.
 * @param width the column's width.
 * @param align how it sits in the column.
 */
@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = KrtPalette.TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        modifier = Modifier.width(width).padding(horizontal = KrtSpacing.sm),
    )
}

/**
 * What the matrix reports back.
 *
 * @property onQuery the material search changed.
 * @property onSystem a star-system chip was tapped.
 * @property onMode the Verkauf/Einkauf switch moved.
 * @property onRetry start the read again.
 */
data class MatrixActions(
    val onQuery: (String) -> Unit,
    val onSystem: (String?) -> Unit,
    val onMode: (MatrixMode) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The Preis-Übersicht, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialMatrixRoute(
    viewModel: MaterialMatrixViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialMatrixScreen(
        state = state,
        actions =
            MatrixActions(
                onQuery = viewModel::onQuery,
                onSystem = viewModel::onSystem,
                onMode = viewModel::onMode,
                onRetry = viewModel::onRetry,
            ),
        modifier = modifier,
    )
}

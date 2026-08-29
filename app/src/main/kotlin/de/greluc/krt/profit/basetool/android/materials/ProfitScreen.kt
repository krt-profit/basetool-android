/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.materials

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.ProfitRow
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTable
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableCell
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableColumn
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the profit table. */
const val PROFIT_TABLE_TAG: String = "profit-table"

/** Test handle for the ship picker. */
const val PROFIT_SHIP_TAG: String = "profit-ship"

/**
 * „Profitberechnung" — one full load, priced per material (design spec ch. 16, artboard 4).
 *
 * > **A ship calculation, not a material one.** The first draft of chapter 16 read it as a
 * > quantity-and-quality form; it is `shipId` plus a system filter, and the answer is one row per
 * > material for a **full load** of that hull.
 *
 * Every figure is the server's. The app renders margins and profits and computes none — a margin is
 * money advice, and one derived here could not be reconciled with the web's own answer.
 *
 * > **The artboard's route sub-line („Lorville → ARC-L1") is not built.** `ProfitCalculationDto`
 * > names no terminals at all, which the design handoff flags itself. On the design gap list.
 *
 * @param state what to draw.
 * @param actions what the form reports.
 * @param modifier layout modifier.
 */
@Composable
fun ProfitScreen(
    state: ProfitState,
    actions: ProfitActions,
    modifier: Modifier = Modifier,
) {
    ProvideScreenTopBar(title = stringResource(R.string.materials_profit_title))
    LazyColumn(
        state = rememberRootListState(),
        modifier = modifier.fillMaxSize().testTag(PROFIT_TABLE_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        item(key = "ship") { ShipPicker(state = state, actions = actions) }
        item(key = "systems") { SystemChips(state = state, actions = actions) }
        item(key = "hints") { Hints(state = state) }
        item(key = "body") { ProfitBody(state = state, actions = actions) }
    }
}

/**
 * Which hull is being filled.
 *
 * @param state what to draw.
 * @param actions the pick.
 */
@Composable
private fun ShipPicker(
    state: ProfitState,
    actions: ProfitActions,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (state.loadingOptions) {
        KrtSpinner()
        return
    }
    KrtSelectField(
        value = state.ship?.krtLabel().orEmpty(),
        options = state.ships.map { KrtOption(value = it.id, label = it.krtLabel()) },
        onSelect = { option ->
            actions.onShip(option.value)
            open = false
        },
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth().testTag(PROFIT_SHIP_TAG),
        label = stringResource(R.string.materials_profit_ship),
        selectedValue = state.shipId,
    )
}

/**
 * „Systeme einschränken" — every system is in until one is switched off.
 *
 * @param state what to draw.
 * @param actions the toggle.
 */
@Composable
private fun SystemChips(
    state: ProfitState,
    actions: ProfitActions,
) {
    if (state.systems.size <= 1) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Text(
            text = stringResource(R.string.materials_profit_systems),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.systems.forEach { system ->
                KrtFilterChip(
                    text = system,
                    selected = system !in state.excluded,
                    onClick = { actions.onToggleSystem(system) },
                )
            }
        }
    }
}

/**
 * The two sentences that explain why a route can be missing or priced differently.
 *
 * The auto-load line always; the Hull-C line only for that hull, which is what the artboard asks
 * for and the only case in which the rule changes anything.
 *
 * @param state what to draw.
 */
@Composable
private fun Hints(state: ProfitState) {
    KrtHint(explanation = stringResource(R.string.materials_profit_autoload))
    if (state.hullCRule) {
        KrtHint(explanation = stringResource(R.string.materials_profit_hullc))
    }
}

/**
 * The answer: the table, the calculation line, the failure, or the sentence that asks for a ship.
 *
 * @param state what to draw.
 * @param actions the retry.
 */
@Composable
private fun ProfitBody(
    state: ProfitState,
    actions: ProfitActions,
) {
    when {
        state.error != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtFieldError(text = stringResource(R.string.materials_profit_error))
                KrtGhostButton(
                    text = stringResource(R.string.missions_retry),
                    onClick = actions.onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = DesignR.drawable.ic_krt_reset,
                )
            }
        }

        state.shipId == null && !state.loadingOptions -> {
            // A sentence, never a skeleton: a skeleton here would pretend a calculation is running
            // that nobody has asked for.
            Muted(text = stringResource(R.string.materials_profit_select_ship))
        }

        state.calculating -> {
            Muted(text = stringResource(R.string.materials_profit_calculating))
        }

        state.rows.isEmpty() -> {
            Muted(text = stringResource(R.string.materials_profit_no_data))
        }

        else -> {
            ProfitTable(rows = state.rows)
        }
    }
}

/**
 * The result table, in the web's own column set.
 *
 * Seven columns will not fit a phone, so the narrow window carries the three that answer the
 * question — Material, Max Profit, Marge — and the wide one adds „Gewinn / SCU". The rest stay
 * web-only rather than being squeezed into an unreadable row.
 *
 * @param rows what the server computed.
 */
@Composable
private fun ProfitTable(rows: List<ProfitRow>) {
    val wide = isWideWindow()
    val columns =
        listOfNotNull(
            KrtTableColumn(title = stringResource(R.string.materials_profit_col_material), weight = 1.6f),
            KrtTableColumn(
                title = stringResource(R.string.materials_profit_col_per_scu),
                weight = 1f,
                numeric = true,
            ).takeIf { wide },
            KrtTableColumn(
                title = stringResource(R.string.materials_profit_col_total),
                weight = 1.2f,
                numeric = true,
            ),
            KrtTableColumn(
                title = stringResource(R.string.materials_profit_col_margin),
                weight = 0.8f,
                numeric = true,
            ),
        )
    KrtTable(columns = columns, rowCount = rows.size, modifier = Modifier.fillMaxWidth()) { row, column ->
        val profit = rows[row]
        val text =
            when {
                column == 0 -> profit.materialName
                wide && column == 1 -> profit.profitPerScu?.toPlainString()
                column == columns.size - 1 -> profit.marginPercent?.toPlainString()
                else -> profit.maxProfitFullLoad?.toPlainString()
            }
        KrtTableCell(
            text = text ?: stringResource(R.string.krt_empty_value),
            column = columns[column],
        )
    }
}

/**
 * A muted line of prose.
 *
 * @param text what it says.
 */
@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/**
 * A hull with its hold, so two similar names can be told apart.
 *
 * @receiver the hull.
 * @return its label.
 */
@Composable
private fun ShipTypeOption.krtLabel(): String =
    stringResource(R.string.materials_profit_ship_label, name, scu ?: 0)

/**
 * What the Profitberechnung reports back.
 *
 * @property onShip a hull was picked.
 * @property onToggleSystem a star system was switched on or off.
 * @property onRetry run it again.
 */
data class ProfitActions(
    val onShip: (String) -> Unit,
    val onToggleSystem: (String) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The Profitberechnung, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun ProfitRoute(
    viewModel: ProfitViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfitScreen(
        state = state,
        actions =
            ProfitActions(
                onShip = viewModel::onShip,
                onToggleSystem = viewModel::onToggleSystem,
                onRetry = viewModel::onRetry,
            ),
        modifier = modifier,
    )
}

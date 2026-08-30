/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandGroup
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandRow
import de.greluc.krt.profit.basetool.android.core.data.MaterialDemandShare
import de.greluc.krt.profit.basetool.android.core.data.formatTypedAmount
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the list. */
const val MATERIAL_DEMAND_LIST_TAG: String = "material-demand-list"

/** Test handle for one material row. */
const val MATERIAL_DEMAND_ROW_TAG: String = "material-demand-row"

/** Height of the coverage bar. */
private val BAR_HEIGHT = 6.dp

/**
 * „Materialbedarf" — what every open Auftrag together still needs (design ch. 18 §1).
 *
 * The planning view. It lives in the web as `orders-material-demand.html` and had no artboard until
 * round 12; the entry point is the Auftragsliste's overflow rather than a navigation item, because
 * it is read **before an Einsatz**, not daily.
 *
 * The question the surface answers is „reicht es", so coverage is a bar rather than a percentage —
 * a bar answers at a glance what a number has to be compared to. Rights are the order list's: a
 * member without Logistiker reads it, they are not locked out of it.
 *
 * The second line says **what is promised and what is handed over**. It was drawn as „im Lager
 * frei: n" until 2026-08-30, and that line is struck (ch. 18 §1, B1): `MaterialDemandRowDto`
 * carries no stock field, and joining `/inventory/aggregated` would report the *total* rather than
 * the free amount because that read knows nothing about claims. Backend ask **G7** would bring it
 * back; until then the row says the two figures it actually has.
 *
 * @param state what to draw.
 * @param onFilterChanged a chip was tapped.
 * @param onToggle a material row was tapped.
 * @param onRetry the failure state's retry.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialDemandScreen(
    state: MaterialDemandState,
    onFilterChanged: (MaterialDemandFilter) -> Unit,
    onToggle: (MaterialDemandRow) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FilterChips(active = state.filter, onFilterChanged = onFilterChanged)
        if (state.phase is MaterialDemandPhase.Ready && !state.empty) {
            LeadLine(state = state)
        }
        KrtHairlineRule()
        when (state.phase) {
            is MaterialDemandPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.orders_demand_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is MaterialDemandPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_crate,
                    title = stringResource(R.string.orders_demand_failed_title),
                    message = stringResource(R.string.orders_demand_failed_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRetry,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            }

            is MaterialDemandPhase.Ready -> {
                DemandList(state = state, onToggle = onToggle)
            }
        }
    }
}

/**
 * The three chips: everything, only what is open, and by size.
 *
 * @param active which one is on.
 * @param onFilterChanged a chip was tapped.
 */
@Composable
private fun FilterChips(
    active: MaterialDemandFilter,
    onFilterChanged: (MaterialDemandFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        MaterialDemandFilter.entries.forEach { filter ->
            KrtFilterChip(
                text = stringResource(filter.labelRes()),
                selected = filter == active,
                onClick = { onFilterChanged(filter) },
            )
        }
    }
}

/**
 * What the chip's label is called.
 *
 * @return the string resource.
 */
private fun MaterialDemandFilter.labelRes(): Int =
    when (this) {
        MaterialDemandFilter.ALL -> R.string.orders_demand_filter_all
        MaterialDemandFilter.UNCOVERED -> R.string.orders_demand_filter_uncovered
        MaterialDemandFilter.BY_AMOUNT -> R.string.orders_demand_filter_by_amount
    }

/**
 * „12 Materialien · 4 ungedeckt" — the one line that says how big the problem is.
 *
 * @param state what is drawn.
 */
@Composable
private fun LeadLine(state: MaterialDemandState) {
    Text(
        text =
            stringResource(
                R.string.orders_demand_lead,
                pluralStringResource(R.plurals.orders_demand_materials, state.materialCount, state.materialCount),
                pluralStringResource(
                    R.plurals.orders_demand_uncovered,
                    state.uncoveredCount,
                    state.uncoveredCount,
                ),
            ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/**
 * The rows, grouped by org unit the way the server groups them.
 *
 * @param state what to draw.
 * @param onToggle a row was tapped.
 */
@Composable
private fun DemandList(
    state: MaterialDemandState,
    onToggle: (MaterialDemandRow) -> Unit,
) {
    val groups = state.visible
    if (groups.isEmpty()) {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_crate,
            title = stringResource(R.string.orders_demand_empty_title),
            message = stringResource(R.string.orders_demand_empty_message),
            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(MATERIAL_DEMAND_LIST_TAG),
        contentPadding = PaddingValues(vertical = KrtSpacing.s8),
    ) {
        groups.forEach { group ->
            item(key = "head-${group.orgUnitId ?: "none"}") { GroupHead(group = group) }
            items(items = group.rows, key = { "${group.orgUnitId}-${it.materialId}" }) { row ->
                DemandRow(
                    row = row,
                    expanded = state.expanded == row.materialId,
                    onToggle = { onToggle(row) },
                )
            }
        }
    }
}

/**
 * One org unit's heading.
 *
 * @param group the unit and its rows.
 */
@Composable
private fun GroupHead(group: MaterialDemandGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        group.orgUnitShorthand?.takeIf { it.isNotBlank() }?.let { KrtOrgBadge(text = it) }
        KrtSectionTitle(
            text =
                group.orgUnitName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.orders_demand_group_unassigned),
        )
    }
}

/**
 * One material: what is open, how far it is covered, and how many orders want it.
 *
 * @param row the material.
 * @param expanded whether its orders are open.
 * @param onToggle open or close it.
 */
@Composable
private fun DemandRow(
    row: MaterialDemandRow,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8)
                .testTag(MATERIAL_DEMAND_ROW_TAG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.materialName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        R.string.orders_demand_open,
                        formatAmount(formatTypedAmount(row.outstanding)),
                        row.unit,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.uncovered) KrtTheme.colors.warning else KrtTheme.colors.successText,
            )
        }
        CoverageBar(coverage = row.coverage, uncovered = row.uncovered)
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            Text(
                text =
                    stringResource(
                        R.string.orders_demand_progress,
                        formatAmount(formatTypedAmount(row.claimed)),
                        row.unit,
                        formatAmount(formatTypedAmount(row.booked)),
                        row.unit,
                    ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            Text(
                text = pluralStringResource(R.plurals.orders_demand_orders, row.orders.size, row.orders.size),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (expanded) {
            row.orders.forEach { share -> ShareRow(share = share, unit = row.unit) }
        }
    }
    KrtHairlineRule()
}

/**
 * The coverage bar: how much of what was asked for is already booked or promised.
 *
 * @param coverage between 0 and 1.
 * @param uncovered whether anything is still open, which decides the tint.
 */
@Composable
private fun CoverageBar(
    coverage: Float,
    uncovered: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = KrtSpacing.s4)
                .height(BAR_HEIGHT)
                .background(KrtPalette.Gray3),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(coverage)
                    .height(BAR_HEIGHT)
                    .background(if (uncovered) KrtTheme.colors.warning else KrtTheme.colors.successText),
        )
    }
}

/**
 * One order's share of this material, shown when the row is open.
 *
 * @param share the order and what it asks for.
 * @param unit what the amounts are counted in.
 */
@Composable
private fun ShareRow(
    share: MaterialDemandShare,
    unit: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.orders_number, share.displayId),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.Gray1,
        )
        Text(
            text = stringResource(share.status.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.orders_demand_share, formatAmount(formatTypedAmount(share.required)), unit),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.Gray1,
        )
    }
}

/**
 * „Materialbedarf", bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun MaterialDemandRoute(
    viewModel: MaterialDemandViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }
    MaterialDemandScreen(
        state = state,
        onFilterChanged = viewModel::onFilterChanged,
        onToggle = viewModel::onToggleExpanded,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

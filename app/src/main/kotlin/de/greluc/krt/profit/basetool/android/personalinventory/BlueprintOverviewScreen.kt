/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOverviewEntry
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOwner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the scrolling list. */
const val BLUEPRINT_OVERVIEW_TAG: String = "blueprint-overview"

/** Test handle for its search field. */
const val BLUEPRINT_OVERVIEW_SEARCH_TAG: String = "blueprint-overview-search"

/**
 * „Blueprint-Verfügbarkeit" — design ch. 17 artboard 6.
 *
 * The web page has exactly two columns, „Blueprint" and „Verfügbar bei", and the chapter's own
 * correction is explicit that there is **no buildability chip** here: the question this screen
 * answers is *who has it*, not *can it be built*. Buildability lives on the member's own blueprint
 * in „Mein Inventar".
 *
 * A card per blueprint rather than a table row, because the owner list wraps: names as data chips,
 * and an owner from outside the unit as a muted chip with the hint line the artboard quotes
 * verbatim.
 *
 * @param state what to draw.
 * @param onQueryChanged the search changed.
 * @param onFilterChanged a chip was tapped.
 * @param onRetry the failure's retry.
 * @param onLoadMore the list reached its end.
 * @param onRowShown a card appeared and wants its owners.
 * @param modifier layout modifier.
 */
@Composable
@Suppress("LongParameterList")
fun BlueprintOverviewScreen(
    state: BlueprintOverviewState,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (OverviewFilter) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRowShown: (BlueprintOverviewEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        KrtTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            // Inside the field, as artboard 17-6 draws it — a search box says what it searches
            // while it is empty and gives the room back once it is not. As a label it stood above
            // an empty box and kept a line of the list for a word the field no longer needed.
            placeholder = stringResource(R.string.blueprint_overview_search),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(KrtSpacing.s12)
                    .testTag(BLUEPRINT_OVERVIEW_SEARCH_TAG),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            KrtFilterChip(
                text = stringResource(R.string.blueprint_overview_filter_all),
                selected = state.filter == OverviewFilter.ALL,
                onClick = { onFilterChanged(OverviewFilter.ALL) },
            )
            KrtFilterChip(
                text = stringResource(R.string.blueprint_overview_filter_unrecorded),
                selected = state.filter == OverviewFilter.UNRECORDED,
                onClick = { onFilterChanged(OverviewFilter.UNRECORDED) },
            )
        }
        when (state.phase) {
            is OverviewPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.blueprint_overview_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is OverviewPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_blueprint,
                    title = stringResource(R.string.blueprint_overview_failed_title),
                    message = stringResource(R.string.blueprint_overview_failed_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRetry,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            }

            is OverviewPhase.Ready -> {
                OverviewList(
                    state = state,
                    onLoadMore = onLoadMore,
                    onRowShown = onRowShown,
                )
            }
        }
    }
}

/**
 * The rows themselves.
 *
 * @param state what to draw.
 * @param onLoadMore the list reached its end.
 * @param onRowShown a card appeared and wants its owners.
 */
@Composable
private fun OverviewList(
    state: BlueprintOverviewState,
    onLoadMore: () -> Unit,
    onRowShown: (BlueprintOverviewEntry) -> Unit,
) {
    val rows = state.visible
    if (rows.isEmpty()) {
        KrtEmptyState(
            iconRes = DesignR.drawable.ic_krt_blueprint,
            title = stringResource(R.string.blueprint_overview_empty_title),
            message = stringResource(R.string.blueprint_overview_empty_message),
            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(BLUEPRINT_OVERVIEW_TAG),
        contentPadding = PaddingValues(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
    ) {
        item(key = "count") {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.blueprint_overview_count,
                        state.total.toInt(),
                        state.total,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (state.filterIsPartial) {
            // What the filter can and cannot see. The endpoint takes a search term and paging and
            // no such filter, so „Nicht erfasst" narrows what has been loaded — said out loud
            // rather than left to read as a complete answer (ADR-0104).
            item(key = "filter-note") {
                Text(
                    text = stringResource(R.string.blueprint_overview_filter_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        items(rows, key = { it.productKey }) { entry ->
            LaunchedEffect(entry.productKey) { onRowShown(entry) }
            OverviewCard(entry = entry, owners = state.owners[entry.productKey])
        }
        if (state.hasMore) {
            item(key = "more") {
                LaunchedEffect(rows.size) { onLoadMore() }
                Text(
                    text = stringResource(R.string.blueprint_overview_loading_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
    }
}

/**
 * One blueprint and who has it.
 *
 * @param entry the row.
 * @param owners how far its owner list has got, or `null` before it was asked for.
 */
@Composable
private fun OverviewCard(
    entry: BlueprintOverviewEntry,
    owners: OwnersState?,
) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            Text(
                text = entry.productName,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
            Text(
                text = stringResource(R.string.blueprint_overview_owners),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            when (owners) {
                null, OwnersState.Idle, OwnersState.Loading -> {
                    Text(
                        text = stringResource(R.string.blueprint_overview_owners_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }

                is OwnersState.Failed -> {
                    Text(
                        text = stringResource(R.string.blueprint_overview_owners_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.DangerText,
                    )
                }

                is OwnersState.Ready -> {
                    OwnerChips(owners.owners)
                }
            }
        }
    }
}

/**
 * The owner chips, and the sentence a foreign owner needs.
 *
 * @param owners who holds it.
 */
@Composable
private fun OwnerChips(owners: List<BlueprintOwner>) {
    if (owners.isEmpty()) {
        Text(
            text = stringResource(R.string.blueprint_overview_owners_none),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
        // FlowRow, not one row per owner: the artboard runs the names along a line and lets them
        // wrap, which is why the card exists at all („Karte statt Tabellenzeile, weil die
        // Besitzerliste umbricht"). A row each turned five holders into five lines and pushed the
        // next blueprint off the screen.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            owners.forEach { owner ->
                // A DATA chip, not a primary one: the name is a value, and artboard 17-6 draws it
                // grey with white text for that reason. In the primary tone it wore the outline of
                // an Auswahl-Chip — chapter 18 §E6 calls a chip that looks like a switch and holds
                // a value the worse of the two mistakes, and this was its mirror image.
                KrtChip(text = owner.name, tone = KrtChipTone.Data)
                if (!owner.orgUnitMember) {
                    KrtChip(text = stringResource(R.string.blueprint_overview_owner_foreign))
                }
            }
        }
        // Quoted verbatim from the artboard, and only drawn when it applies: it explains why a
        // name that is not in the unit appears in a unit's list.
        if (owners.any { !it.orgUnitMember }) {
            Text(
                text = stringResource(R.string.blueprint_overview_owner_foreign_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The overview, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun BlueprintOverviewRoute(
    viewModel: BlueprintOverviewViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }
    BlueprintOverviewScreen(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onFilterChanged = viewModel::onFilterChanged,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onRowShown = viewModel::onRowShown,
        modifier = modifier,
    )
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the hangar list. */
const val HANGAR_LIST_TAG: String = "hangar-list"

/** Test handle for the hangar's search field. */
const val HANGAR_SEARCH_TAG: String = "hangar-search"

/** Test handle for the Meine Schiffe / Org-Einheit segment. */
const val HANGAR_SEGMENT_TAG: String = "hangar-segment"

/**
 * The Hangar (design spec ch. 08 §1), read-only.
 *
 * **The three-number band of the design's org tab is absent.** "Schiffe 42 · Fitted 31 · LTI 24" is
 * an aggregate over the whole org unit, and the API offers no such total: the overview is paged, so
 * adding up what is loaded would state a number the page cannot know. The per-type rows carry their
 * own counts, which are the server's.
 *
 * Adding, editing and importing ships are mutations and belong to Phase 3, so there is no FAB and
 * no overflow menu.
 *
 * @param state what to draw.
 * @param onSegmentSelected the segment was switched.
 * @param onSearchChanged a keystroke in the filter field.
 * @param onRefresh pull-to-refresh.
 * @param onLoadMore the load-more control was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarScreen(
    state: HangarState,
    onSegmentSelected: (HangarSegment) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.hangar_segment_mine),
                    stringResource(R.string.hangar_segment_org),
                ),
            selectedIndex = state.segment.ordinal,
            onSelect = { onSegmentSelected(HangarSegment.entries[it]) },
            stretch = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = KrtSpacing.md, end = KrtSpacing.md, top = KrtSpacing.md)
                    .testTag(HANGAR_SEGMENT_TAG),
        )
        KrtTextField(
            // The typed value, not the debounced one (REQ-APP-MIS-004).
            value = state.searchText,
            onValueChange = onSearchChanged,
            placeholder = stringResource(R.string.hangar_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md).testTag(HANGAR_SEARCH_TAG),
        )

        when (state.phase) {
            is HangarPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.hangar_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is HangarPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_ship,
                    title = stringResource(R.string.hangar_error_title),
                    message = stringResource(R.string.hangar_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            }

            is HangarPhase.Ready -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HangarBody(state = state, onLoadMore = onLoadMore)
                }
            }
        }
    }
}

/**
 * The list of whichever half is showing, or its empty state.
 *
 * @param state what to draw.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun HangarBody(
    state: HangarState,
    onLoadMore: () -> Unit,
) {
    val empty =
        if (state.segment == HangarSegment.MINE) state.ships.isEmpty() else state.types.isEmpty()
    if (empty) {
        HangarEmpty(segment = state.segment, narrowed = state.isNarrowed)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(HANGAR_LIST_TAG)) {
        if (state.segment == HangarSegment.MINE) {
            items(state.ships, key = { it.id }) { ship -> ShipCard(ship = ship) }
        } else {
            items(state.types, key = { it.typeName }) { type -> ShipTypeRow(type = type) }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text = state.countLabel(),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.hangar_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * How many of how many the showing half has loaded.
 *
 * @return the label, pluralised for the half's own unit — ships or ship types.
 */
@Composable
private fun HangarState.countLabel(): String =
    if (segment == HangarSegment.MINE) {
        pluralStringResource(R.plurals.hangar_ship_count, total.toInt(), ships.size, total)
    } else {
        pluralStringResource(R.plurals.hangar_type_count, total.toInt(), types.size, total)
    }

/**
 * One ship, as the design's card.
 *
 * The type is the headline because it is what identifies a ship at a glance; the member's own name
 * for it, when they gave one, sits beside it in quotes as the web app writes it.
 *
 * @param ship the ship.
 */
@Composable
private fun ShipCard(ship: Ship) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Text(
            text = ship.headline(),
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ship.manufacturerName?.takeIf { it.isNotBlank() }?.let { maker ->
            Text(
                text = maker,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtChip(
                text =
                    stringResource(
                        if (ship.fitted) R.string.hangar_fitted else R.string.hangar_not_fitted,
                    ),
                tone = if (ship.fitted) KrtChipTone.Success else KrtChipTone.Muted,
            )
            KrtChip(
                text = ship.insurance ?: stringResource(R.string.hangar_no_insurance),
                tone = KrtChipTone.Info,
            )
            ship.locationName?.takeIf { it.isNotBlank() }?.let { place ->
                Text(
                    text = place,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The card's headline.
 *
 * @return the type, plus the member's own name in quotes when they gave one.
 */
private fun Ship.headline(): String = name?.let { "$typeName „$it\"" } ?: typeName

/**
 * One aggregate row.
 *
 * @param type the ship type and its counts.
 */
@Composable
private fun ShipTypeRow(type: ShipTypeSummary) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Text(
            text = type.typeName,
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.hangar_type_row,
                    type.count.toInt(),
                    type.count.toInt(),
                    type.fittedCount.toInt(),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * The empty state, which differs by half and by whether a filter is applied.
 *
 * "You own no ship" and "your filter matches none" are different facts, and so are "you own none"
 * and "the org unit has none".
 *
 * @param segment which half is showing.
 * @param narrowed whether a filter is applied.
 */
@Composable
private fun HangarEmpty(
    segment: HangarSegment,
    narrowed: Boolean,
) {
    val title =
        when {
            narrowed -> R.string.hangar_empty_filtered_title
            segment == HangarSegment.MINE -> R.string.hangar_empty_mine_title
            else -> R.string.hangar_empty_org_title
        }
    val message =
        when {
            narrowed -> R.string.hangar_empty_filtered_message
            segment == HangarSegment.MINE -> R.string.hangar_empty_mine_message
            else -> R.string.hangar_empty_org_message
        }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_ship,
        title = stringResource(title),
        message = stringResource(message),
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
    )
}

/**
 * The Hangar, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun HangarRoute(
    viewModel: HangarViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HangarScreen(
        state = state,
        onSegmentSelected = viewModel::onSegmentSelected,
        onSearchChanged = viewModel::onSearchChanged,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        modifier = modifier,
    )
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Operation
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Operationen list. */
const val OPERATIONS_LIST_TAG: String = "operations-list"

/** Test handle for the Operationen search field. */
const val OPERATIONS_SEARCH_TAG: String = "operations-search"

/** Test handle for the Einsätze/Operationen segment. */
const val LIST_SEGMENT_TAG: String = "list-segment"

/**
 * Which half of the Einsätze/Operationen switch is showing.
 *
 * The two halves are **separate navigation destinations**, not two states of one screen. Both are
 * already in the graph — "Einsätze" in the bottom bar, "Operationen" behind "Mehr" — so making the
 * segment a local toggle would give each list two addresses, one of which lies to the navigation
 * bar about where the member is. Tapping the other half navigates.
 */
enum class ListSegment {
    /** The Einsatz list. */
    MISSIONS,

    /** The Operationen list. */
    OPERATIONS,
}

/**
 * The Einsätze/Operationen switch that sits above both lists (design ch. 06 §1).
 *
 * @param selected the half currently showing.
 * @param onSelect invoked with the other half when it is tapped; ignored for the current one.
 * @param modifier layout modifier.
 */
@Composable
fun ListSegmentBar(
    selected: ListSegment,
    onSelect: (ListSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    KrtSegmentedControl(
        options =
            listOf(
                stringResource(R.string.operations_segment_missions),
                stringResource(R.string.operations_segment_operations),
            ),
        selectedIndex = selected.ordinal,
        onSelect = { index ->
            val target = ListSegment.entries[index]
            if (target != selected) {
                onSelect(target)
            }
        },
        stretch = true,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = KrtSpacing.md, end = KrtSpacing.md, top = KrtSpacing.md)
                .testTag(LIST_SEGMENT_TAG),
    )
}

/**
 * The Operationen list (design spec ch. 06 §1, Operationen half).
 *
 * **The row is thinner than the design mock.** The mock shows "2 Einsätze · 18 Teilnehmer" and a
 * payout chip per row; the API's list DTO carries neither, and its own documentation states that
 * the bulk endpoints deliberately do not spend the aggregate queries those numbers would need.
 * Widening the backend was put to the repository owner and declined (2026-08-22) — the counts live
 * on the detail, which loads them anyway. Recorded in `docs/specs/operations.md` as an approved
 * deviation rather than left as a silent difference.
 *
 * @param state what to draw.
 * @param onSearchChanged a keystroke in the search field.
 * @param onStatusToggled a status chip was tapped; the screen sends the resulting whole set.
 * @param onResetFilters the reset chip was tapped.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param onOpenOperation a row was tapped.
 * @param onOpenMissions the Einsätze half of the segment was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(
    state: OperationsState,
    onSearchChanged: (String) -> Unit,
    onStatusToggled: (Set<OperationStatus>) -> Unit,
    onResetFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenOperation: (String) -> Unit,
    onOpenMissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ListSegmentBar(
            selected = ListSegment.OPERATIONS,
            onSelect = { onOpenMissions() },
        )
        OperationsFilterBar(
            state = state,
            onSearchChanged = onSearchChanged,
            onStatusToggled = onStatusToggled,
            onResetFilters = onResetFilters,
        )

        // The classified cause is deliberately not shown: an error code means nothing to a member.
        // The view model logged it, which is what a report can be matched against.
        when (state.phase) {
            is OperationsPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.operations_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is OperationsPhase.Failed -> {
                // A busy server gets the countdown of chapter 14; anything else gets the ordinary
                // empty state, because a countdown in front of a 403 promises a retry that will
                // answer exactly the same.
                val retryIn = state.retryIn
                if (retryIn != null) {
                    KrtRetryCountdown(
                        secondsLeft = retryIn,
                        title = stringResource(R.string.retry_busy_title),
                        message = stringResource(R.string.retry_busy_message, retryIn),
                        retryLabel = stringResource(R.string.retry_now),
                        onRetry = onRetryNow,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                    )
                } else {
                    KrtEmptyState(
                        iconRes = DesignR.drawable.ic_krt_clipboard_check,
                        title = stringResource(R.string.operations_error_title),
                        message = stringResource(R.string.operations_error_message),
                        actionText = stringResource(R.string.missions_retry),
                        onAction = onRefresh,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                    )
                }
            }

            is OperationsPhase.Ready -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.operations.isEmpty()) {
                        KrtRefreshableFill {
                            OperationsEmpty(
                                narrowed = state.isNarrowed,
                                onResetFilters = onResetFilters,
                            )
                        }
                    } else {
                        OperationsList(
                            state = state,
                            onOpenOperation = onOpenOperation,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search field plus the status chip row.
 *
 * No date-range and no "Vergangene" chip: an Operation has no start time of its own, and the
 * finished ones are the list's second group rather than something to switch off.
 *
 * @param state what is currently narrowed.
 * @param onSearchChanged a keystroke.
 * @param onStatusToggled the resulting whole status set after a chip tap.
 * @param onResetFilters clears everything.
 */
@Composable
private fun OperationsFilterBar(
    state: OperationsState,
    onSearchChanged: (String) -> Unit,
    onStatusToggled: (Set<OperationStatus>) -> Unit,
    onResetFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtTextField(
            // The typed value, not the debounced one — see REQ-APP-MIS-004.
            value = state.searchText,
            onValueChange = onSearchChanged,
            placeholder = stringResource(R.string.operations_search_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(OPERATIONS_SEARCH_TAG),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            FILTERABLE_OPERATION_STATUSES.forEach { status ->
                val selected = status in state.query.statuses
                KrtFilterChip(
                    text = stringResource(status.labelRes()),
                    selected = selected,
                    onClick = {
                        onStatusToggled(
                            if (selected) state.query.statuses - status else state.query.statuses + status,
                        )
                    },
                )
            }
            if (state.isNarrowed) {
                KrtFilterChip(
                    text = stringResource(R.string.missions_filter_reset),
                    selected = false,
                    onClick = onResetFilters,
                )
            }
        }
    }
}

/**
 * The grouped, paginated list.
 *
 * Grouped into "Laufend" and "Abgeschlossen" rather than by date, because an Operation has no date
 * of its own — its Einsätze do. Grouping is applied to the rows **already loaded**, so a group
 * heading never claims more than the page behind it holds.
 *
 * @param state what to draw.
 * @param onOpenOperation a row was tapped.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun OperationsList(
    state: OperationsState,
    onOpenOperation: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val running = state.operations.filter { it.isRunning }
    val finished = state.operations.filterNot { it.isRunning }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(OPERATIONS_LIST_TAG)) {
        if (running.isNotEmpty()) {
            item(key = "group-running") {
                KrtSectionTitle(
                    text = stringResource(R.string.operations_group_running),
                    modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                )
            }
            items(running, key = { it.id }) { operation ->
                OperationRow(operation = operation, onClick = { onOpenOperation(operation.id) })
            }
        }
        if (finished.isNotEmpty()) {
            item(key = "group-finished") {
                KrtSectionTitle(
                    text = stringResource(R.string.operations_group_finished),
                    modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                )
            }
            items(finished, key = { it.id }) { operation ->
                OperationRow(operation = operation, onClick = { onOpenOperation(operation.id) })
            }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text =
                        pluralStringResource(
                            R.plurals.operations_count,
                            state.total.toInt(),
                            state.operations.size,
                            state.total,
                        ),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.operations_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * One Operation row.
 *
 * @param operation the Operation.
 * @param onClick opens it.
 */
@Composable
private fun OperationRow(
    operation: Operation,
    onClick: () -> Unit,
) {
    // A card, not a padded Column: design ch. 06 draws the Operationen segment with the
    // same tile the Einsätze segment uses. See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = operation.name,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            KrtStatusBadge(text = operation.statusLabel(), tone = operation.statusTone())
        }
        operation.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                maxLines = 2,
            )
        }
    }
}

/**
 * The empty state, which says something different depending on why it is empty.
 *
 * @param narrowed whether a filter is applied.
 * @param onResetFilters clears the filters from inside the empty state.
 */
@Composable
private fun OperationsEmpty(
    narrowed: Boolean,
    onResetFilters: () -> Unit,
) {
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_clipboard_check,
        title =
            stringResource(
                if (narrowed) R.string.operations_empty_filtered_title else R.string.operations_empty_title,
            ),
        message =
            stringResource(
                if (narrowed) R.string.operations_empty_filtered_message else R.string.operations_empty_message,
            ),
        actionText = if (narrowed) stringResource(R.string.missions_filter_reset) else null,
        onAction = if (narrowed) onResetFilters else null,
        modifier = Modifier.padding(KrtSpacing.lg),
    )
}

/** The statuses offered as filter chips; [OperationStatus.UNKNOWN] is this build's word, not a server value. */
private val FILTERABLE_OPERATION_STATUSES =
    listOf(
        OperationStatus.PLANNED,
        OperationStatus.ACTIVE,
        OperationStatus.COMPLETED,
        OperationStatus.CANCELED,
    )

/**
 * The string resource naming this status.
 *
 * @return the resource id; [OperationStatus.UNKNOWN] has none and must not reach here.
 */
internal fun OperationStatus.labelRes(): Int =
    when (this) {
        OperationStatus.PLANNED -> R.string.operations_status_planned

        OperationStatus.ACTIVE -> R.string.operations_status_active

        OperationStatus.COMPLETED -> R.string.operations_status_completed

        OperationStatus.CANCELED -> R.string.operations_status_canceled

        // Never offered as a filter, and a row carrying it shows its raw server value instead.
        OperationStatus.UNKNOWN -> R.string.operations_title
    }

/**
 * The badge text for this Operation.
 *
 * @return the translated status, or the raw server value when this build does not know it.
 */
@Composable
internal fun Operation.statusLabel(): String =
    if (status == OperationStatus.UNKNOWN) {
        rawStatus.orEmpty()
    } else {
        stringResource(status.labelRes())
    }

/**
 * The badge tone for this Operation.
 *
 * @return the design system's tone; an unknown status is drawn as planned rather than as a problem.
 */
internal fun Operation.statusTone(): KrtStatusTone = status.tone()

/**
 * The design system's tone for a status.
 *
 * @return the tone.
 */
internal fun OperationStatus.tone(): KrtStatusTone =
    when (this) {
        OperationStatus.PLANNED, OperationStatus.UNKNOWN -> KrtStatusTone.Planned
        OperationStatus.ACTIVE -> KrtStatusTone.Active
        OperationStatus.COMPLETED -> KrtStatusTone.Completed
        OperationStatus.CANCELED -> KrtStatusTone.Cancelled
    }

/**
 * The Operationen list, bound to its view model.
 *
 * @param viewModel drives the list.
 * @param onOpenOperation a row was tapped.
 * @param onOpenMissions the Einsätze half of the segment was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun OperationsRoute(
    viewModel: OperationsViewModel,
    onOpenOperation: (String) -> Unit,
    onOpenMissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OperationsScreen(
        state = state,
        onSearchChanged = viewModel::onSearchChanged,
        onStatusToggled = viewModel::onStatusesChanged,
        onResetFilters = viewModel::onResetFilters,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onOpenOperation = onOpenOperation,
        onOpenMissions = onOpenMissions,
        modifier = modifier,
    )
}

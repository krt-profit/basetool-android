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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Operationen list. */
const val OPERATIONS_LIST_TAG: String = "operations-list"

/** Test handle for the Operationen search field. */
const val OPERATIONS_SEARCH_TAG: String = "operations-search"

/** Test handle for the Einsätze/Operationen segment. */
const val LIST_SEGMENT_TAG: String = "list-segment"

/** Test handle for the „Operation anlegen" action. */
const val OPERATION_CREATE_CTA_TAG: String = "operation-create-cta"

/**
 * Which half of the Einsätze/Operationen switch is showing; each half is its own navigation
 * destination, and tapping the other one navigates.
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
                .padding(start = KrtSpacing.s12, end = KrtSpacing.s12, top = KrtSpacing.s12)
                .testTag(LIST_SEGMENT_TAG),
    )
}

/**
 * The Operationen list (design spec ch. 06 §1, Operationen half).
 *
 * Rows carry no Einsatz or participant counts, because the list DTO has none; the counts live on
 * the detail (approved deviation in `docs/specs/operations.md`).
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
 * @param onCreate raise a new Operation, or `null` where the screen cannot navigate.
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
    onCreate: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ListSegmentBar(
            selected = ListSegment.OPERATIONS,
            onSelect = { onOpenMissions() },
        )
        onCreate?.let {
            KrtOutlineButton(
                text = stringResource(R.string.operation_form_title),
                onClick = it,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KrtSpacing.s12)
                        .testTag(OPERATION_CREATE_CTA_TAG),
                iconRes = DesignR.drawable.ic_krt_plus,
            )
        }
        OperationsFilterBar(
            state = state,
            onSearchChanged = onSearchChanged,
            onStatusToggled = onStatusToggled,
            onResetFilters = onResetFilters,
        )

        when (state.phase) {
            is OperationsPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.operations_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is OperationsPhase.Failed -> {
                val retryIn = state.retryIn
                if (retryIn != null) {
                    KrtRetryCountdown(
                        secondsLeft = retryIn,
                        title = stringResource(R.string.retry_busy_title),
                        message = stringResource(R.string.retry_busy_message, retryIn),
                        retryLabel = stringResource(R.string.retry_now),
                        onRetry = onRetryNow,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                    )
                } else {
                    KrtEmptyState(
                        iconRes = DesignR.drawable.ic_krt_clipboard_check,
                        title = stringResource(R.string.operations_error_title),
                        message = stringResource(R.string.operations_error_message),
                        actionText = stringResource(R.string.missions_retry),
                        onAction = onRefresh,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
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
 * Search field plus the status chip row; there is no date-range or „Vergangene" chip.
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
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        KrtTextField(
            value = state.searchText,
            onValueChange = onSearchChanged,
            placeholder = stringResource(R.string.operations_search_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(OPERATIONS_SEARCH_TAG),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
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
 * The paginated list, grouped into „Laufend" and „Abgeschlossen" over the rows already loaded.
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(OPERATIONS_LIST_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        if (running.isNotEmpty()) {
            item(key = "group-running") {
                KrtSectionTitle(
                    text = stringResource(R.string.operations_group_running),
                    modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
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
                    modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
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
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.operations_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.s12),
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
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = operation.name,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            KrtStatusPill(text = operation.statusLabel(), tone = operation.statusTone())
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
        modifier = Modifier.padding(KrtSpacing.s16),
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
 * @param onCreate raise a new Operation, or `null` where the screen cannot navigate.
 */
@Composable
fun OperationsRoute(
    viewModel: OperationsViewModel,
    onOpenOperation: (String) -> Unit,
    onOpenMissions: () -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
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
        onCreate = onCreate,
    )
}

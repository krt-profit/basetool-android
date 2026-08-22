/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import android.text.format.DateUtils
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import java.time.Instant
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the queue. */
const val ORDERS_LIST_TAG: String = "orders-list"

/** Test handle for one order's screen. */
const val ORDER_DETAIL_TAG: String = "order-detail"

/**
 * The Auftrag queue (design spec ch. 10 §1), read-only.
 *
 * **No priority drag, no create.** Reordering is a logistician's write and creating an order is the
 * public request form; both are mutations and belong to Phase 3.
 *
 * @param state what to draw.
 * @param onStatusToggled a status chip was tapped; the screen sends the resulting whole set.
 * @param onToggleMaterials a row's material list was opened or closed.
 * @param onRefresh pull-to-refresh.
 * @param onLoadMore the load-more control was tapped.
 * @param onOpenOrder a row was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    state: OrdersState,
    onStatusToggled: (Set<JobOrderStatus>) -> Unit,
    onToggleMaterials: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            FILTERABLE_STATUSES.forEach { status ->
                val selected = status in state.statuses
                KrtFilterChip(
                    text = stringResource(status.labelRes()),
                    selected = selected,
                    onClick = {
                        onStatusToggled(
                            if (selected) state.statuses - status else state.statuses + status,
                        )
                    },
                )
            }
        }

        when (state.phase) {
            is OrdersPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.orders_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is OrdersPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_clipboard_list,
                    title = stringResource(R.string.orders_error_title),
                    message = stringResource(R.string.orders_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            }

            is OrdersPhase.Ready -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.orders.isEmpty()) {
                        KrtEmptyState(
                            iconRes = DesignR.drawable.ic_krt_clipboard_list,
                            title = stringResource(R.string.orders_empty_title),
                            message = stringResource(R.string.orders_empty_message),
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                        )
                    } else {
                        OrdersList(
                            state = state,
                            onToggleMaterials = onToggleMaterials,
                            onLoadMore = onLoadMore,
                            onOpenOrder = onOpenOrder,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The paginated queue.
 *
 * @param state what to draw.
 * @param onToggleMaterials a row's material list was opened or closed.
 * @param onLoadMore the next page was asked for.
 * @param onOpenOrder a row was tapped.
 */
@Composable
private fun OrdersList(
    state: OrdersState,
    onToggleMaterials: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(ORDERS_LIST_TAG)) {
        items(state.orders, key = { it.id }) { order ->
            OrderCard(
                order = order,
                expanded = order.id in state.expanded,
                onToggleMaterials = { onToggleMaterials(order.id) },
                onClick = { onOpenOrder(order.id) },
            )
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text =
                        pluralStringResource(
                            R.plurals.orders_count,
                            state.total.toInt(),
                            state.orders.size,
                            state.total,
                        ),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.orders_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * One order in the queue.
 *
 * The material list is collapsed by default, as the web app has it. Its toggle is a separate tap
 * target from the card, so opening the list and opening the order cannot be confused.
 *
 * @param order the order.
 * @param expanded whether its material list is open.
 * @param onToggleMaterials opens or closes it.
 * @param onClick opens the order.
 */
@Composable
private fun OrderCard(
    order: JobOrder,
    expanded: Boolean,
    onToggleMaterials: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.orders_number, order.displayId),
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            order.priority?.let { KrtChip(text = stringResource(R.string.orders_priority, it)) }
            KrtStatusBadge(text = order.statusLabel(), tone = order.statusTone())
        }
        Text(
            text = order.parties(),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (order.materials.isNotEmpty()) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.orders_material_count,
                        order.materials.size,
                        order.materials.size,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleMaterials)
                        .padding(vertical = KrtSpacing.xs),
            )
            if (expanded) {
                order.materials.forEach { MaterialLine(material = it) }
            }
        }
    }
}

/**
 * One material line with its progress bar.
 *
 * @param material the line.
 */
@Composable
private fun MaterialLine(material: JobOrderMaterial) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            Text(
                text = material.name,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.orders_material_progress,
                        formatAmount(material.inStock.orEmpty()),
                        formatAmount(material.needed.orEmpty()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        material.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                color =
                    if (progress >= 1f) KrtPalette.SuccessText else MaterialTheme.colorScheme.primary,
                trackColor = KrtPalette.Gray3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The queue row's second line.
 *
 * @return "Für X · Durch Y", with whichever half the server sent.
 */
@Composable
private fun JobOrder.parties(): String =
    listOfNotNull(
        requestingOrgUnit?.let { stringResource(R.string.orders_for, it) },
        responsibleOrgUnit?.let { stringResource(R.string.orders_by, it) },
        createdAt?.relativeToNow(),
    ).joinToString(" · ")

/**
 * How long ago an instant is, in the platform's words.
 *
 * @return the localised relative span.
 */
@Composable
private fun Instant.relativeToNow(): String {
    LocalConfiguration.current
    return DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

/** The statuses offered as filter chips. */
private val FILTERABLE_STATUSES =
    listOf(
        JobOrderStatus.OPEN,
        JobOrderStatus.IN_PROGRESS,
        JobOrderStatus.REJECTED,
        JobOrderStatus.COMPLETED,
    )

/**
 * The string resource naming a status.
 *
 * @return the resource id; [JobOrderStatus.UNKNOWN] has none and must not reach here.
 */
private fun JobOrderStatus.labelRes(): Int =
    when (this) {
        JobOrderStatus.OPEN -> R.string.orders_status_open
        JobOrderStatus.IN_PROGRESS -> R.string.orders_status_in_progress
        JobOrderStatus.REJECTED -> R.string.orders_status_rejected
        JobOrderStatus.COMPLETED -> R.string.orders_status_completed
        JobOrderStatus.UNKNOWN -> R.string.orders_title
    }

/**
 * The badge text for an order.
 *
 * @return the translated status, or the raw server value for one this build does not know.
 */
@Composable
private fun JobOrder.statusLabel(): String =
    if (status == JobOrderStatus.UNKNOWN) rawStatus.orEmpty() else stringResource(status.labelRes())

/**
 * The badge tone for an order.
 *
 * @return the design system's tone. A rejected order is the only one drawn as a problem.
 */
private fun JobOrder.statusTone(): KrtStatusTone =
    when (status) {
        JobOrderStatus.OPEN, JobOrderStatus.UNKNOWN -> KrtStatusTone.Planned
        JobOrderStatus.IN_PROGRESS -> KrtStatusTone.Active
        JobOrderStatus.COMPLETED -> KrtStatusTone.Completed
        JobOrderStatus.REJECTED -> KrtStatusTone.Cancelled
    }

/**
 * One order in full (design spec ch. 10 §2), read-only.
 *
 * The design's four tabs become one scrolling page for the reason the Operation detail gives: three
 * short sections a member reads together are worse behind a control than beneath each other.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    state: OrderDetailState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = state.order
    val phase = state.phase
    when {
        order != null -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                OrderDetailBody(order = order)
            }
        }

        phase is OrderDetailPhase.Failed -> {
            OrderDetailFailure(error = phase.error, modifier = modifier)
        }

        else -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.order_detail_title),
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The order's head and its three sections.
 *
 * @param order the order.
 */
@Composable
private fun OrderDetailBody(order: JobOrder) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(ORDER_DETAIL_TAG)) {
        item(key = "head") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.orders_number, order.displayId),
                        style = MaterialTheme.typography.titleLarge,
                        color = KrtPalette.White,
                        modifier = Modifier.weight(1f),
                    )
                    KrtStatusBadge(text = order.statusLabel(), tone = order.statusTone())
                }
                Text(
                    text = order.parties(),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
                // Said plainly, because the alternative is a member reading a partial order as a
                // complete one (REQ-ORDERS-023).
                if (order.redacted) {
                    Text(
                        text = stringResource(R.string.orders_redacted),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.Warning,
                    )
                }
            }
        }
        order.comment?.let { comment ->
            item(key = "comment") {
                Section(title = stringResource(R.string.order_detail_comment)) {
                    Body(text = comment)
                }
            }
        }
        item(key = "materials-title") {
            KrtSectionTitle(
                text = stringResource(R.string.order_detail_materials),
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
            )
        }
        if (order.materials.isEmpty()) {
            item(key = "materials-empty") {
                Body(text = stringResource(R.string.order_detail_materials_empty))
            }
        } else {
            items(order.materials, key = { it.name }) { material ->
                Column(modifier = Modifier.padding(horizontal = KrtSpacing.md)) {
                    MaterialLine(material = material)
                }
            }
        }
        item(key = "assignees-title") {
            KrtSectionTitle(
                text = stringResource(R.string.order_detail_assignees),
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
            )
        }
        item(key = "assignees") {
            Body(
                text =
                    order.assignees.takeIf { it.isNotEmpty() }?.joinToString(", ")
                        ?: stringResource(R.string.order_detail_assignees_empty),
            )
        }
        item(key = "handovers-title") {
            KrtSectionTitle(
                text = stringResource(R.string.order_detail_handovers),
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
            )
        }
        if (order.handovers.isEmpty()) {
            item(key = "handovers-empty") {
                Body(text = stringResource(R.string.order_detail_handovers_empty))
            }
        } else {
            items(order.handovers, key = { it.id }) { handover ->
                Body(
                    text =
                        stringResource(
                            R.string.order_detail_handover_row,
                            handover.recipient.orEmpty(),
                            handover.at?.relativeToNow().orEmpty(),
                        ),
                )
            }
        }
    }
}

/**
 * A titled block.
 *
 * @param title the heading.
 * @param content what goes under it.
 */
@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        KrtSectionTitle(
            text = title,
            modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        )
        content()
    }
}

/**
 * A muted body line.
 *
 * @param text what to say.
 */
@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.xs),
    )
}

/**
 * The whole-screen failure, worded by cause.
 *
 * @param error what went wrong.
 * @param modifier layout modifier.
 */
@Composable
private fun OrderDetailFailure(
    error: ApiError,
    modifier: Modifier = Modifier,
) {
    val (titleRes, messageRes) =
        when (error) {
            is ApiError.Forbidden -> {
                R.string.order_detail_error_forbidden_title to
                    R.string.order_detail_error_forbidden_message
            }

            is ApiError.NotFound -> {
                R.string.order_detail_error_missing_title to
                    R.string.order_detail_error_missing_message
            }

            else -> {
                R.string.order_detail_error_title to R.string.order_detail_error_message
            }
        }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_clipboard_list,
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
    )
}

/**
 * The queue, bound to its view model.
 *
 * @param viewModel drives the queue.
 * @param onOpenOrder a row was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun OrdersRoute(
    viewModel: OrdersViewModel,
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrdersScreen(
        state = state,
        onStatusToggled = viewModel::onStatusesChanged,
        onToggleMaterials = viewModel::onToggleMaterials,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        onOpenOrder = onOpenOrder,
        modifier = modifier,
    )
}

/**
 * One order, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun OrderDetailRoute(
    viewModel: OrderDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrderDetailScreen(state = state, onRefresh = viewModel::onRefresh, modifier = modifier)
}

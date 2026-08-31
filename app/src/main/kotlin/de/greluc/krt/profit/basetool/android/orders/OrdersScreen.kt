/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeBand
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAgeThresholds
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAssignee
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
import de.greluc.krt.profit.basetool.android.core.data.krtHandedOver
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDataValue
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadgeKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTabs
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.DenialToast
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.fieldMessage
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import java.time.Instant
import java.time.temporal.ChronoUnit
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the queue. */
const val ORDERS_LIST_TAG: String = "orders-list"

/** Test tag of the queue's „+", which opens the create form. */
const val ORDERS_CREATE_TAG: String = "orders-create"

/** Test handle for one order's screen. */
const val ORDER_DETAIL_TAG: String = "order-detail"

/** Test tag of „An den Anfang" on the order detail. */
const val ORDER_PRIORITY_FRONT_TAG: String = "order-priority-front"

/** Test tag of „Höher" on the order detail. */
const val ORDER_PRIORITY_UP_TAG: String = "order-priority-up"

/** Test tag of „Niedriger" on the order detail. */
const val ORDER_PRIORITY_DOWN_TAG: String = "order-priority-down"

/** Test handle for an assignee's note action. */
const val ORDER_NOTE_TAG: String = "order-note"

/** Test handle for the note sheet. */
const val ORDER_NOTE_SHEET_TAG: String = "order-note-sheet"

/** Test handle for the note sheet's save action. */
const val ORDER_NOTE_SAVE_TAG: String = "order-note-save"

/** Test handle for the status sheet. */
const val ORDER_STATUS_SHEET_TAG: String = "order-status-sheet"

/** The statuses an order can be moved to from the app, in the order the picker draws them. */
internal val STATUS_CHOICES =
    listOf(
        JobOrderStatus.OPEN,
        JobOrderStatus.IN_PROGRESS,
        JobOrderStatus.COMPLETED,
        JobOrderStatus.REJECTED,
    )

/**
 * The Auftrag queue (design spec ch. 10 §1), read-only.
 *
 * **No priority drag.** Reordering is a logistician's write against `PUT /orders/{id}/priority` and
 * needs a drag affordance the design has not drawn; it is still outstanding. Creating an order is
 * built — the „+" the artboard draws opens [OrderCreateScreen].
 *
 * @param state what to draw.
 * @param onStatusToggled a status chip was tapped; the screen sends the resulting whole set.
 * @param onToggleMaterials a row's material list was opened or closed.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param onOpenOrder a row was tapped.
 * @param onCreate the „+" was tapped; opens the create form.
 * @param onOpenDemand the overflow's „Materialbedarf" was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    state: OrdersState,
    onStatusToggled: (Set<JobOrderStatus>) -> Unit,
    onToggleMaterials: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenDemand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrdersOverflow(onOpenDemand = onOpenDemand)
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.online) {
                OfflineBand()
            }
            // FlowRow, not Row: at font scale 1.3x a Row squeezes the last chip until its label
            // breaks character by character („ABG ESC HLO SSE N").
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
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
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                        )
                    } else {
                        KrtEmptyState(
                            iconRes = DesignR.drawable.ic_krt_clipboard_list,
                            title = stringResource(R.string.orders_error_title),
                            message = stringResource(R.string.orders_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                        )
                    }
                }

                is OrdersPhase.Ready -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.orders.isEmpty()) {
                            KrtRefreshableFill {
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_clipboard_list,
                                    title = stringResource(R.string.orders_empty_title),
                                    message = stringResource(R.string.orders_empty_message),
                                    modifier = Modifier.padding(KrtSpacing.s16),
                                )
                            }
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
        KrtFab(
            iconRes = DesignR.drawable.ic_krt_plus,
            label = stringResource(R.string.orders_create),
            onClick = onCreate,
            // Offline the „+" leads to a form that can only refuse, so it is dimmed here the way
            // every other list in the app dims its create action.
            enabled = state.online,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(KrtSpacing.s16)
                    .padding(bottom = LocalKrtBottomBarInset.current)
                    .testTag(ORDERS_CREATE_TAG),
        )
    }
}

/**
 * Moving the order in the queue — a Logistician's write.
 *
 * **Buttons, not a drag.** The web reorders by dragging a row, which needs the whole queue on
 * screen and a pointer that can hold one row while the rest scrolls. Neither is true on a phone.
 * What a Logistician actually decides is „this one sooner" or „this one later", and the endpoint
 * takes an absolute position, so the buttons express the intent and compute the position.
 *
 * Design ch. 18 §3 (E5/E8) ratified that for the Ablauf and said the queue takes **the same two
 * buttons at the same size**, so the stepwise pair is now the 40 × 44 icon pair, dimmed at the
 * front of the queue where „nach vorn" has nowhere to go — validation, not a lock.
 *
 * „An den Anfang" stays beside them as a labelled button: it is a jump rather than a step, the
 * ratified pair does not cover it, and it has no counterpart at the other end because the back of
 * the queue is a page count away and a control that guessed at its length would drop the order
 * somewhere nobody asked for.
 *
 * @param state what the detail holds.
 * @param actions what it reports back.
 */
@Composable
private fun PriorityControls(
    state: OrderDetailState,
    actions: OrderDetailActions,
) {
    val atFront = (state.order?.priority ?: 1) <= 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtGhostButton(
            text = stringResource(R.string.order_detail_priority_front),
            onClick = { actions.onRaisePriority(true) },
            modifier =
                Modifier
                    .testTag(ORDER_PRIORITY_FRONT_TAG)
                    .alpha(if (state.writable && !atFront) 1f else DISABLED_WRITE_ALPHA),
            enabled = state.writable && !atFront,
        )
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_chevron_up,
            label = stringResource(R.string.order_detail_priority_up),
            onClick = { actions.onRaisePriority(false) },
            modifier =
                Modifier
                    .testTag(ORDER_PRIORITY_UP_TAG)
                    .alpha(if (state.writable && !atFront) 1f else DISABLED_WRITE_ALPHA),
            enabled = state.writable && !atFront,
            width = QUEUE_MOVE_WIDTH,
            height = KrtSpacing.touchTarget,
        )
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_chevron_down,
            label = stringResource(R.string.order_detail_priority_down),
            onClick = actions.onLowerPriority,
            modifier =
                Modifier
                    .testTag(ORDER_PRIORITY_DOWN_TAG)
                    .alpha(if (state.writable) 1f else DISABLED_WRITE_ALPHA),
            enabled = state.writable,
            width = QUEUE_MOVE_WIDTH,
            height = KrtSpacing.touchTarget,
        )
    }
}

/** The queue's move pair takes the Ablauf's ratified 40 dp width (design ch. 18 §3). */
private val QUEUE_MOVE_WIDTH = 40.dp

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
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(ORDERS_LIST_TAG),
        contentPadding = PaddingValues(horizontal = KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        items(state.orders, key = { it.id }) { order ->
            OrderCard(
                order = order,
                ageThresholds = state.ageThresholds,
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
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.orders_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.s12),
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
    ageThresholds: JobOrderAgeThresholds,
    expanded: Boolean,
    onToggleMaterials: () -> Unit,
    onClick: () -> Unit,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
            verticalAlignment = Alignment.Top,
        ) {
            PriorityBlock(priority = order.priority)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.orders_number, order.displayId),
                        style = MaterialTheme.typography.titleMedium,
                        color = KrtPalette.White,
                    )
                    order.kindLabel()?.let { KrtChip(text = it, tone = order.kindTone()) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The row-level pill, not the page badge: a queue of twenty orders each
                    // shouting its status in a filled frame cannot be scanned, and artboard 10-1
                    // draws the dot-plus-word form here.
                    KrtStatusPill(text = order.statusLabel(), tone = order.statusTone())
                    order.createdAt?.let { created ->
                        Text(
                            // A day count, not a date. The shared `relativeToNow` switches to
                            // „15.08., 18:17" from two days out, which makes the reader do the
                            // arithmetic the colour beside it has already done — and the age is
                            // the whole point of this line. Artboard 1 draws „vor 94 Tagen".
                            text = ageText(created),
                            style = MaterialTheme.typography.bodySmall,
                            // The colour IS the information: an order nobody has picked up in
                            // three months has to look different from one raised yesterday, and
                            // the thresholds are the operator's (see JobOrderAgeThresholds).
                            color = ageThresholds.toneFor(created),
                        )
                    }
                }
                PartiesRow(order = order)
            }
            KrtIcon(
                id = DesignR.drawable.ic_krt_chevron_right,
                contentDescription = null,
                tint = KrtPalette.Gray2,
            )
        }
        // An order carries one kind of line or the other, and the disclosure names whichever it
        // has. An item order used to show no disclosure at all, so its positions were reachable
        // only by opening the order.
        if (order.materials.isNotEmpty()) {
            MaterialsDisclosure(
                labelRes = R.string.orders_materials_label,
                count = order.materials.size,
                expanded = expanded,
                onToggle = onToggleMaterials,
            )
            if (expanded) {
                order.materials.forEach { MaterialLine(material = it) }
            }
        } else if (order.items.isNotEmpty()) {
            MaterialsDisclosure(
                labelRes = R.string.orders_items_label,
                count = order.items.size,
                expanded = expanded,
                onToggle = onToggleMaterials,
            )
            if (expanded) {
                order.items.forEach { ItemLine(item = it) }
            }
        }
    }
}

/**
 * The queue position, as the design draws it: the number first, its meaning underneath.
 *
 * A chip reading "Prio 1" was the earlier form and it buried the one figure the queue is sorted
 * by among the other chips on the card. Rendered as a block it is scannable down the list, which
 * is what a priority is for.
 *
 * @param priority the position, or `null` for an order that carries none.
 */
@Composable
private fun PriorityBlock(priority: Int?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(PRIORITY_BLOCK_WIDTH),
    ) {
        Text(
            text = priority?.toString() ?: EM_DASH,
            style = MaterialTheme.typography.headlineSmall,
            color = if (priority != null) MaterialTheme.colorScheme.primary else KrtPalette.Gray2,
        )
        Text(
            text = stringResource(R.string.orders_priority_label).krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * Who the order is for and who is doing it, as two labelled org badges.
 *
 * These used to be one muted sentence, which lost the distinction the badge carries: a
 * Spezialkommando is drawn differently from a Staffel because "who owns this work" is the
 * question the queue is read for.
 *
 * @param order the order whose parties to draw.
 */
@Composable
private fun PartiesRow(order: JobOrder) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.orders_for_label).krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
        KrtOrgBadge(
            text = order.requestingOrgUnit ?: EM_DASH,
            kind = orgBadgeKind(order.requestingOrgUnit),
        )
        Text(
            text = stringResource(R.string.orders_by_label).krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
        KrtOrgBadge(
            text = order.responsibleOrgUnit ?: EM_DASH,
            kind = orgBadgeKind(order.responsibleOrgUnit),
        )
    }
}

/**
 * The material list's disclosure row.
 *
 * A separate tap target from the card, so opening the list and opening the order cannot be
 * confused; the chevron turns to say which of the two a tap will do.
 *
 * @param labelRes what the row is called; the two order kinds name their own lines.
 * @param count how many lines the order has.
 * @param expanded whether the list is open.
 * @param onToggle opens or closes it.
 */
@Composable
private fun MaterialsDisclosure(
    @StringRes labelRes: Int,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIcon(
            id =
                if (expanded) {
                    DesignR.drawable.ic_krt_chevron_down
                } else {
                    DesignR.drawable.ic_krt_chevron_right
                },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(labelRes).krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.orders_materials_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * Which badge a unit name gets.
 *
 * @param unit the unit's display name, or `null` when the order names none.
 * @return the badge kind; a Spezialkommando is told apart by its `SK` prefix, which is how the
 *   backend names them everywhere this app reads them.
 */
private fun orgBadgeKind(unit: String?): KrtOrgBadgeKind =
    when {
        unit == null -> KrtOrgBadgeKind.Muted
        unit.startsWith(SPECIAL_COMMAND_PREFIX, ignoreCase = true) -> KrtOrgBadgeKind.SpecialCommand
        else -> KrtOrgBadgeKind.Own
    }

/**
 * One material line with its progress bar.
 *
 * @param material the line.
 */
@Composable
internal fun MaterialLine(material: JobOrderMaterial) {
    // A card, as artboard 10-2 draws every position: the name, the two figures, the bar, and the
    // two facts underneath as chips. It was four loose lines between hairlines, which is what made
    // an order of five materials read as one paragraph.
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = material.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The booked figure is the data and stays bright; what was asked for is the scale it
            // is read against and stays muted. A figure the server did not send reads as a dash —
            // left empty it became „ / 500", which looks like a rendering fault rather than an
            // absent number (found on a device, on a material nothing is stocked of).
            KrtDataValue(text = material.inStock.orDash(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.orders_material_of, material.needed.orDash()),
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
                modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s8).height(POSITION_BAR),
                // No stop indicator: Material3 draws a dot at the far end of the track, which on an
                // empty bar is the only thing on it and reads as a value rather than as a scale.
                // The design system's meters are a filled rectangle and nothing else.
                drawStopIndicator = {},
            )
        }
        // Design ch. 10 artboard 2 puts both facts under the bar as chips: what is booked, and what
        // is already promised. „Who has already promised part of this" is what turns an open figure
        // into a plan.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s8),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            KrtChip(text = stringResource(R.string.orders_material_booked, material.inStock.orDash()))
            KrtChip(
                // „Zugesagt: —" when nobody has promised anything: the artboard writes the dash
                // alone, without a unit, because there is no quantity for the unit to belong to.
                text =
                    material.claimedAmount
                        ?.let { stringResource(R.string.orders_material_claimed, it) }
                        ?: stringResource(R.string.orders_material_claimed_none),
            )
        }
    }
}

/** The position bar — thicker than a default indicator, as artboard 10-2 draws it. */
private val POSITION_BAR = 6.dp

/**
 * The production action on one item line: whether the caller may, and what to do when they tap.
 *
 * @property allowed whether the caller holds the grant the endpoint requires.
 * @property denials where a refused tap is announced.
 * @property onProduce open the Herstellung for this line.
 */
data class ItemProduceGate(
    val allowed: Boolean,
    val denials: DenialState,
    val onProduce: () -> Unit,
)

/**
 * „Herstellung erfassen" on one item line — **drawn even when it is refused**.
 *
 * Hiding it was the alternative and is the thing this project's own gate rule forbids: this
 * organisation grants roles by hand, and a control nobody can see is a grant nobody asks for. So a
 * caller without the Logistician role gets the button at 45 %, the lock ahead of the label (design
 * ch. 09 artboard 13), and a tap that names the grant instead of writing anything.
 *
 * @param gate whether the caller may, and what to do about it.
 * @param labelRes what the button says.
 * @param iconRes its glyph while it is usable; a refused one always shows the lock.
 * @param tag the test handle.
 */
@Composable
private fun ProduceAction(
    gate: ItemProduceGate,
    @androidx.annotation.StringRes labelRes: Int = R.string.order_production_record,
    @androidx.annotation.DrawableRes iconRes: Int = DesignR.drawable.ic_krt_clipboard_check,
    tag: String = ORDER_PRODUCTION_OPEN_TAG,
) {
    val role =
        Gate(
            allowed = gate.allowed,
            reason = stringResource(R.string.gate_role_logistician),
            detail = stringResource(R.string.gate_role_logistician_detail),
        )
    val (dim, click) = rememberGated(role, gate.onProduce, gate.denials)
    KrtGhostButton(
        text = stringResource(labelRes),
        onClick = click,
        iconRes = if (gate.allowed) iconRes else DesignR.drawable.ic_krt_lock,
        modifier = Modifier.fillMaxWidth().then(dim).testTag(tag),
    )
}

/**
 * One item line of an order.
 *
 * The figures are counts, not quantities: built over asked-for, with the handed-over count under
 * them when any have moved. A blueprint the server flagged as changed since the order was raised
 * carries the web's warning chip — what will be built may no longer be what was costed.
 *
 * @param item the line.
 * @param produce the production action's gate and callback, or `null` on a line that cannot carry
 *   one at all — nothing left to build, or a row the server sent without an id or a version.
 * @param handOver the item-handover action's gate and callback, or `null` on a line with nothing
 *   built and undelivered.
 */
@Composable
internal fun ItemLine(
    item: JobOrderItem,
    produce: ItemProduceGate? = null,
    handOver: ItemProduceGate? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            Text(
                text = item.name ?: stringResource(R.string.order_detail_item_unnamed),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.orders_item_progress,
                        item.manufactured,
                        item.amount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (item.blueprintStale) {
            KrtChip(
                text = stringResource(R.string.order_detail_item_blueprint_stale),
                tone = KrtChipTone.Warning,
            )
        }
        if (item.delivered > 0) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.orders_item_delivered,
                        item.delivered,
                        item.delivered,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                color =
                    if (progress >= 1f) KrtPalette.SuccessText else MaterialTheme.colorScheme.primary,
                trackColor = KrtPalette.Gray3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // The write that moves this line's own figure. It sits on the line rather than in a tab
        // of its own because a production run is booked against one line, and the count above is
        // exactly what it changes.
        produce?.let { ProduceAction(gate = it) }
        // The other half of the same line's work: what was built, handed to somebody. Drawn beside
        // the Herstellung because both are booked against this one line.
        handOver?.let {
            ProduceAction(
                gate = it,
                labelRes = R.string.order_item_handover_record,
                iconRes = DesignR.drawable.ic_krt_bookout,
                tag = ORDER_ITEM_HANDOVER_OPEN_TAG,
            )
        }
    }
}

/**
 * A quantity, or a dash when the server stated none.
 *
 * @return the grouped figure, or `—`.
 */
private fun String?.orDash(): String = this?.let { formatAmount(it) }?.takeIf { it.isNotEmpty() } ?: "—"

/**
 * The order's kind, as the chip beside its number.
 *
 * @return `Material` or `Item` in the member's language, or `null` when the server named no type —
 *   in which case no chip is drawn rather than an empty one.
 */
@Composable
private fun JobOrder.kindLabel(): String? =
    when (type?.uppercase()) {
        TYPE_MATERIAL -> stringResource(R.string.orders_kind_material)
        TYPE_ITEM -> stringResource(R.string.orders_kind_item)
        else -> type
    }

/**
 * The tone that kind is drawn in.
 *
 * @return the design's two kind colours; anything unrecognised stays neutral rather than borrowing
 *   a colour that means something else.
 */
private fun JobOrder.kindTone(): KrtChipTone =
    when (type?.uppercase()) {
        TYPE_MATERIAL -> KrtChipTone.Primary
        TYPE_ITEM -> KrtChipTone.Info
        else -> KrtChipTone.Muted
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
internal fun JobOrderStatus.labelRes(): Int =
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
 * @param handover the Übergabe sheet's own state and callbacks.
 * @param itemHandover the item Übergabe sheet's own state and callbacks.
 * @param claims the Zusagen tab's own state and callbacks.
 * @param production the Herstellung sheet's own state and callbacks.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member asked for the failed first load again.
 * @param actions what the screen reports back.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    state: OrderDetailState,
    handover: OrderHandoverActions,
    itemHandover: OrderItemHandoverActions,
    production: OrderProductionActions,
    claims: ClaimActions,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    actions: OrderDetailActions,
    modifier: Modifier = Modifier,
) {
    val order = state.order
    val phase = state.phase
    val denials = rememberDenialState()
    when {
        order != null -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                OrderDetailBody(
                    state = state,
                    order = order,
                    actions = actions,
                    claims = claims,
                    denials = denials,
                )
                // At the screen, never inside the list: a LazyColumn recycles its rows, and a
                // toast owned by one would vanish the moment that row scrolled away.
                DenialToast(state = denials)
            }
            // Design ch. 14's conflict dialog -- but NOT for the note, which already has the
            // richer recovery chapter 10 draws: a refused note comes back as `rejectedNote` with
            // „Meine Fassung übernehmen", and a generic „Neu laden" over it would offer to throw
            // away the very text that flow exists to preserve.
            ConflictOn(
                error = state.error?.takeIf { state.rejectedNote == null },
                onReload = onRefresh,
            )
            state.noteDraft?.let { draft ->
                NoteSheet(draft = draft, state = state, actions = actions)
            }
            if (state.statusPickerOpen) {
                StatusSheet(current = order.status, state = state, actions = actions)
            }
            OrderHandoverSheet(actions = handover)
            OrderItemHandoverSheet(actions = itemHandover)
            OrderProductionSheet(actions = production)
            OrderClaimSheet(actions = claims)
        }

        phase is OrderDetailPhase.Failed -> {
            // A busy server gets the countdown of chapter 14; anything else gets the ordinary
            // failure state, because a countdown in front of a 403 promises a retry that will
            // answer exactly the same.
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
                OrderDetailFailure(error = phase.error, modifier = modifier)
            }
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
 * Everything the detail screen reports back.
 *
 * @property onToggleAssignment the caller put themselves on the order, or took themselves off.
 * @property onEditNote the caller opened their own note.
 * @property onNoteChanged the note text changed.
 * @property onSaveNote the note was saved.
 * @property onDismissNote the note editor was closed.
 * @property onOpenStatusPicker the status control was taken.
 * @property onRaisePriority move towards the front; `true` for the front outright.
 * @property onLowerPriority move one place towards the back.
 * @property onStatusChosen a status was picked.
 * @property onDismissStatusPicker the status picker was closed.
 */
data class OrderDetailActions(
    val onToggleAssignment: () -> Unit,
    val onEditNote: () -> Unit,
    val onNoteChanged: (String) -> Unit,
    val onSaveNote: () -> Unit,
    val onDismissNote: () -> Unit,
    /** Puts a note the server refused in an optimistic-lock race back into the editor. */
    val onReapplyRejectedNote: () -> Unit,
    val onOpenStatusPicker: () -> Unit,
    val onRaisePriority: (Boolean) -> Unit,
    val onLowerPriority: () -> Unit,
    val onStatusChosen: (JobOrderStatus) -> Unit,
    val onDismissStatusPicker: () -> Unit,
    /** Marks a status as intended without moving the order (design ch. 10 artboard 8). */
    val onStatusSelected: (JobOrderStatus) -> Unit,
    val onRecordHandover: (JobOrderMaterial) -> Unit,
    /** Opens „Herstellung erfassen" for one item line (design ch. 10 artboard 15). */
    val onRecordProduction: (JobOrderItem) -> Unit,
    /** Opens „Übergabe erfassen" for one item line — the item order's own handover. */
    val onRecordItemHandover: (JobOrderItem) -> Unit,
    /** Opens „Auftrag bearbeiten" (design ch. 10 artboards 10 and 11). */
    val onEditOrder: () -> Unit,
    /** Opens the Materialsammelübersicht (design ch. 10 artboard 16). */
    val onOpenCollection: () -> Unit,
    /** Applies the marked status, asking first when the target cannot be taken back. */
    val onApplyStatus: () -> Unit,
    /** Backs out of the terminal confirmation, keeping the choice on screen. */
    val onDismissStatusConfirm: () -> Unit,
    /** Switches to another page of the order (design ch. 10 artboard 2). */
    val onTabSelected: (OrderTab) -> Unit,
)

/**
 * The order's own „⋮" — today it holds one entry, „Auftrag bearbeiten".
 *
 * **Drawn even when it cannot be used**, in both of its two ways. A caller with neither edit gate
 * gets the entry with the Logistician reason; an **item** order gets it with a different one, since
 * that form is a capability this build does not have rather than a permission the caller lacks
 * (`PUT /orders/{id}/items` needs the blueprint-variant picker and the sub-assembly tree of
 * artboard 12). Hiding either would turn a rule into a mystery.
 *
 * @param state what the order is and who is reading it.
 * @param actions what the entry reports.
 * @param denials where a refused tap is announced.
 */
@Composable
private fun OrderOverflow(
    state: OrderDetailState,
    actions: OrderDetailActions,
    denials: DenialState,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.order_detail_actions)
    val edit = stringResource(R.string.order_edit_open)
    val itemsOnWeb = stringResource(R.string.order_edit_item_only_web)
    val collection = stringResource(R.string.order_collection_open)
    val status = stringResource(R.string.order_detail_change_status)
    val assignment =
        stringResource(
            if (state.myAssignment == null) {
                R.string.order_detail_assign_me
            } else {
                R.string.order_detail_unassign_me
            },
        )
    val gate =
        Gate(
            allowed = state.editMode != null && state.editableKind,
            reason =
                if (state.editableKind) {
                    stringResource(R.string.gate_role_logistician)
                } else {
                    itemsOnWeb
                },
            detail =
                if (state.editableKind) {
                    stringResource(R.string.gate_role_logistician_detail)
                } else {
                    stringResource(R.string.order_edit_item_only_web_detail)
                },
        )
    val (_, click) = rememberGated(gate, actions.onEditOrder, denials)
    KrtOverflowMenu(
        contentDescription = label,
        expanded = open,
        onExpandedChange = { open = it },
        items =
            listOfNotNull(
                // „Status change + Zuständigen setzen im Overflow" (ch. 10, artboard 2's handoff).
                // They were a filled CTA and a ghost button under the head, which put two controls
                // in front of every member on a screen most of them only read.
                KrtMenuItem(
                    label = assignment,
                    iconRes = DesignR.drawable.ic_krt_user_plus,
                ) {
                    open = false
                    actions.onToggleAssignment()
                }.takeIf { state.writable },
                // Only a Logistician is offered this, and only because the app can ask whether the
                // caller is one. The grant is also per order, so the refusal is named rather than
                // assumed away.
                KrtMenuItem(
                    label = status,
                    iconRes = DesignR.drawable.ic_krt_swap,
                ) {
                    open = false
                    actions.onOpenStatusPicker()
                }.takeIf { state.statusChangeable && state.writable },
                KrtMenuItem(
                    label = edit,
                    iconRes = DesignR.drawable.ic_krt_edit,
                    reason = gate.reason.takeIf { !gate.allowed },
                    locked = !gate.allowed,
                ) {
                    open = false
                    click()
                },
                // Not gated: the collection is a READ for anyone who may see the order. Only its
                // own writes are gated, and the screen draws those.
                KrtMenuItem(
                    label = collection,
                    iconRes = DesignR.drawable.ic_krt_crate,
                ) {
                    open = false
                    actions.onOpenCollection()
                },
            ),
    )
}

/**
 * The order's head and its three sections.
 *
 * @param state what to draw.
 * @param order the order.
 * @param actions what the screen reports back.
 * @param claims the Zusagen tab's own state and callbacks.
 * @param denials the screen's single refusal slot, for the controls a caller may not use.
 */
@Composable
private fun OrderDetailBody(
    state: OrderDetailState,
    order: JobOrder,
    actions: OrderDetailActions,
    claims: ClaimActions,
    denials: DenialState,
) {
    // The order's number and status live in the TOP BAR (design ch. 10 artboard 2), the same rule
    // the Einsatz detail follows. The parties move to the facts bar.
    //
    // **Published from outside the list, and exactly once.** It was published from inside a
    // `LazyColumn` item, and a lazy item's `onDispose` — which clears the slot — raced its own
    // `SideEffect` on every recomposition, so the shell kept falling back to the route's „Auftrag"
    // and neither the number nor the status ever appeared. A second call for the overflow alone,
    // with a null title, would have wiped them again even if the first had landed: the slot holds
    // one bar and the last writer wins.
    ProvideScreenTopBar(
        title = stringResource(R.string.orders_number, order.displayId),
        // What kind of order this is, beside its number — artboard 10-2 draws it there because a
        // Material order and an Item order are read and worked differently, and the number alone
        // says neither. The model carried `type` and nothing drew it.
        titleBadge = order.kindLabel()?.let { kind -> { KrtChip(text = kind, tone = order.kindTone()) } },
        actions = { OrderOverflow(state = state, actions = actions, denials = denials) },
        subtitle = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                // A dot and tinted text, not a filled badge: ch. 02 §3 defines the status pill as
                // „square 8 dp dot + tint text", and every detail head in the chapters (06-2,
                // 10-2, 11, 12) draws that one. The louder badge belongs where a status is the
                // page's whole subject.
                KrtStatusPill(text = order.statusLabel(), tone = order.statusTone())
                order.priority?.let {
                    Text(
                        text = stringResource(R.string.order_detail_priority, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
        },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ORDER_DETAIL_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        if (!state.online) {
            item(key = "offline") { OfflineBand() }
        }
        item(key = "head") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
            ) {
                state.error?.let { error -> WriteError(error = error) }
                // „Status change + Zuständigen setzen im Overflow" (ch. 10, artboard 2's handoff).
                // They were a filled CTA and a ghost button under the head, which put two controls
                // in front of every member on a screen most of them only read.
                if (state.priorityChangeable) {
                    PriorityControls(state = state, actions = actions)
                }
            }
        }
        item(key = "facts") { OrderFactsBar(order = order) }
        item(key = "redaction") { RedactionNotice(order = order) }
        item(key = "tabs") {
            // Not `OrderTab.entries`: Zusagen exist only on a Spezialkommando order, so the tab
            // set is the order's own rather than the enum's.
            val tabs = state.tabs
            KrtPageTabs(
                tabs =
                    tabs.map { tab ->
                        KrtPageTab(label = stringResource(tab.labelRes), count = tab.countIn(order))
                    },
                selectedIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
                onSelect = { actions.onTabSelected(tabs[it]) },
            )
        }
        when (state.tab) {
            OrderTab.POSITIONS -> {
                positionsTab(
                    order = order,
                    allowed = state.productionAllowed,
                    denials = denials,
                    items =
                        ItemLineBindings(
                            onProduce = actions.onRecordProduction,
                            onHandOver = actions.onRecordItemHandover,
                            tree = state.itemTree,
                            itemStock = state.itemStock,
                        ),
                )
            }

            OrderTab.ASSIGNEES -> {
                assigneesTab(order = order, state = state, actions = actions)
            }

            OrderTab.CLAIMS -> {
                claimsTab(state = state.claims, actions = claims)
            }

            OrderTab.HANDOVERS -> {
                handoversTab(order = order, onRecord = actions.onRecordHandover)
            }
        }
    }
}

/**
 * One member on the order, with their own note under their name.
 *
 * The caller's own row is the only one that offers anything: the note is theirs to write, and
 * putting someone else on an order is a Logistician action this app does not carry.
 *
 * @param assignee the row.
 * @param mine whether it is the caller's own.
 * @param writable whether a write may be offered at all.
 * @param onEditNote the note action was taken.
 */
@Composable
internal fun AssigneeRow(
    assignee: JobOrderAssignee,
    mine: Boolean,
    writable: Boolean,
    onEditNote: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    assignee.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.order_detail_assignee_unnamed),
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.primary else KrtPalette.White,
            )
            assignee.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        }
        if (mine) {
            KrtGhostButton(
                text = stringResource(R.string.order_detail_note),
                onClick = onEditNote,
                modifier =
                    Modifier
                        .testTag(ORDER_NOTE_TAG)
                        .alpha(if (writable) 1f else DISABLED_WRITE_ALPHA),
                enabled = writable,
            )
        }
    }
}

/**
 * The caller's own note on this order.
 *
 * @param draft what the editor holds.
 * @param state the screen, for the save gate and the last refusal.
 * @param actions what it reports back.
 */
@Composable
private fun NoteSheet(
    draft: String,
    state: OrderDetailState,
    actions: OrderDetailActions,
) {
    KrtBottomSheet(
        onDismiss = actions.onDismissNote,
        modifier = Modifier.testTag(ORDER_NOTE_SHEET_TAG),
        // „Notiz zur Zuweisung", as artboard 10-5 heads it — not „Notiz". The order already
        // carries the requester's Anmerkung, and a sheet titled with the bare word left it open
        // which of the two was being written.
        title = stringResource(R.string.order_detail_note_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            // Whose note this is, on the sheet itself: the API only ever lets a member write their
            // own, and the sheet is reached from a list of everybody's (design ch. 10 artboard 5).
            state.order?.let { order ->
                Text(
                    text = stringResource(R.string.order_detail_note_scope, order.displayId),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            state.rejectedNote?.let { refused -> NoteConflict(refused = refused, actions = actions) }
            Text(
                text = stringResource(R.string.order_detail_note_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = draft,
                onValueChange = { typed -> actions.onNoteChanged(typed.take(NOTE_MAX_LENGTH)) },
                label = stringResource(R.string.order_detail_note),
                // The example the chapter writes into the empty field: a note is free text and
                // the field alone says nothing about what belongs in it. Four lines high, as
                // artboard 10-5 draws it — a single-line box invites a single word.
                placeholder = stringResource(R.string.order_detail_note_placeholder),
                minLines = NOTE_LINES,
                enabled = !state.saving,
            )
            Text(
                text = stringResource(R.string.order_detail_note_counter, draft.length, NOTE_MAX_LENGTH),
                style = MaterialTheme.typography.labelSmall,
                // Design ch. 10 artboard 6: the counter turns warning-yellow before the ceiling,
                // not at it. A limit a member only learns about when the field stops accepting
                // characters costs them the sentence they were in the middle of.
                color =
                    if (draft.length >= NOTE_WARN_LENGTH) KrtPalette.Warning else KrtPalette.TextMuted,
                modifier = Modifier.align(Alignment.End),
            )
            // The conflict is drawn above as its own block, so it does not also arrive as a bare
            // error line saying the same thing twice.
            state.error?.takeIf { state.rejectedNote == null }?.let { error -> WriteError(error = error) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = actions.onDismissNote,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = actions.onSaveNote,
                    modifier = Modifier.testTag(ORDER_NOTE_SAVE_TAG),
                    // Design ch. 10: the CTA is live only when the draft differs from what the
                    // server holds. An enabled "Speichern" over an untouched field offers a write
                    // that would change nothing — and on a first, empty note it invites one that
                    // says nothing at all.
                    enabled =
                        state.writable &&
                            !state.saving &&
                            draft != state.myAssignment?.note.orEmpty(),
                )
            }
        }
    }
}

/**
 * What the last write returned.
 *
 * A validation refusal is shown in the server's own words, which name the field it rejected. A
 * `403` is ordinary here rather than exceptional: the Logistician grant is per order, so a
 * Logistician outside this order's slice is refused exactly like a member without it.
 *
 * @param error the refusal.
 */
@Composable
private fun WriteError(error: ApiError) {
    KrtFieldError(
        text =
            error.fieldMessage() ?: stringResource(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
                    is ApiError.Forbidden -> R.string.order_detail_not_allowed
                    else -> R.string.write_failed
                },
            ),
    )
}

/**
 * A muted body line.
 *
 * @param text what to say.
 */
@Composable
internal fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
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
        modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
    )
}

/**
 * The queue, bound to its view model.
 *
 * @param viewModel drives the queue.
 * @param onOpenOrder a row was tapped.
 * @param onCreate the „+" was tapped.
 * @param onOpenDemand the overflow's „Materialbedarf" was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun OrdersRoute(
    viewModel: OrdersViewModel,
    onOpenOrder: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenDemand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrdersScreen(
        state = state,
        onStatusToggled = viewModel::onStatusesChanged,
        onToggleMaterials = viewModel::onToggleMaterials,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onOpenOrder = onOpenOrder,
        onCreate = onCreate,
        onOpenDemand = onOpenDemand,
        modifier = modifier,
    )
}

/**
 * One order, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 * @param onEditOrder open the edit form for this order.
 * @param onOpenCollection open this order's linked stock rows.
 */
@Composable
fun OrderDetailRoute(
    viewModel: OrderDetailViewModel,
    modifier: Modifier = Modifier,
    onEditOrder: (String) -> Unit = {},
    onOpenCollection: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrderDetailScreen(
        state = state,
        handover =
            OrderHandoverActions(
                draft = state.handover,
                onChange = viewModel.handover::change,
                onSubmit = { viewModel.handover.submit(state.orderId) },
                onDismiss = viewModel.handover::dismiss,
            ),
        itemHandover =
            OrderItemHandoverActions(
                draft = state.itemHandover,
                onChange = viewModel.itemHandover::change,
                onSubmit = { viewModel.itemHandover.submit(state.orderId) },
                onDismiss = viewModel.itemHandover::dismiss,
            ),
        claims =
            ClaimActions(
                draft = state.claims.draft,
                onOpen = viewModel.claims::open,
                onChange = viewModel.claims::change,
                onSubmit = { viewModel.claims.submit(state.orderId) },
                onWithdraw = { viewModel.claims.withdraw(state.orderId) },
                onDismiss = viewModel.claims::dismiss,
            ),
        production =
            OrderProductionActions(
                draft = state.production,
                onAmount = viewModel.production::changeAmount,
                onDraw = viewModel.production::changeDraw,
                onSkip = viewModel.production::toggleSkip,
                onAutoFill = viewModel.production::autoFill,
                bookIn =
                    ProductionBookInActions(
                        onLocationQuery = viewModel.production::searchLocations,
                        onLocation = viewModel.production::chooseLocation,
                        onOwnerQuery = viewModel.production::searchMembers,
                        onOwner = viewModel.production::chooseOwner,
                        onOrgUnit = viewModel.production::chooseOrgUnit,
                        onPersonal = viewModel.production::togglePersonal,
                        onAllocate = viewModel.production::toggleAllocate,
                    ),
                onSubmit = viewModel.production::submit,
                onDismiss = viewModel.production::dismiss,
            ),
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        actions =
            OrderDetailActions(
                onToggleAssignment = viewModel::onToggleAssignment,
                onEditNote = viewModel::onEditNote,
                onNoteChanged = viewModel::onNoteChanged,
                onSaveNote = viewModel::onSaveNote,
                onDismissNote = viewModel::onDismissNote,
                onReapplyRejectedNote = viewModel::onReapplyRejectedNote,
                onOpenStatusPicker = viewModel::onOpenStatusPicker,
                onRaisePriority = viewModel::onRaisePriority,
                onLowerPriority = viewModel::onLowerPriority,
                onStatusChosen = viewModel::onStatusChosen,
                onDismissStatusPicker = viewModel::onDismissStatusPicker,
                onStatusSelected = viewModel::onStatusSelected,
                onApplyStatus = viewModel::onApplyStatus,
                onDismissStatusConfirm = viewModel::onDismissStatusConfirm,
                onTabSelected = viewModel::onTabSelected,
                onRecordHandover = { material ->
                    // The delivered figure comes from the handover LINES, never from
                    // `amount - openAmount`: the server's open remainder counts claims, not
                    // deliveries, so that subtraction would overstate every claimed line.
                    viewModel.handover.open(
                        orderId = state.orderId,
                        material = material,
                        alreadyDone = state.order?.krtHandedOver(material.materialId)?.toString(),
                    )
                },
                onRecordItemHandover = viewModel.itemHandover::open,
                onEditOrder = { onEditOrder(state.orderId) },
                onOpenCollection = { onOpenCollection(state.orderId) },
                onRecordProduction = { line ->
                    viewModel.production.open(
                        orderId = state.orderId,
                        item = line,
                        // The unit working the Auftrag is the book-in pool the web preselects —
                        // but only when the owner belongs to it, which is why the holder checks
                        // rather than the screen.
                        responsibleOrgUnitId = state.order?.responsibleOrgUnitId,
                    )
                },
            ),
        modifier = modifier,
    )
}

/** Width of the priority block, so every card's middle column starts on the same line. */
private val PRIORITY_BLOCK_WIDTH = 40.dp

/** What an absent figure reads as. */
private const val EM_DASH = "—"

/** How the backend names a Spezialkommando everywhere this app reads one. */
private const val SPECIAL_COMMAND_PREFIX = "SK"

/** `JobOrderDto.type` for a material order. */
private const val TYPE_MATERIAL = "MATERIAL"

/** `JobOrderDto.type` for an item order. */
private const val TYPE_ITEM = "ITEM"

/**
 * The colour an order's age is drawn in.
 *
 * @param createdAt when the order was raised.
 * @return the design's three age colours.
 */
@Composable
private fun JobOrderAgeThresholds.toneFor(createdAt: Instant): Color =
    when (bandFor(createdAt)) {
        JobOrderAgeBand.Old -> KrtPalette.DangerText
        JobOrderAgeBand.Ageing -> KrtPalette.Warning
        JobOrderAgeBand.Fresh -> KrtPalette.TextMuted
    }

/**
 * What a lost optimistic-lock race looks like on the note sheet.
 *
 * Design ch. 10 artboard 7. The field above has already been reset to what the server holds; this
 * shows the text that was refused and offers to put it back, because the alternative — dropping it
 * — loses a paragraph the member wrote to a colleague who happened to save first.
 *
 * @param refused the text the server would not take.
 * @param actions what the sheet reports back.
 */
@Composable
private fun NoteConflict(
    refused: String,
    actions: OrderDetailActions,
) {
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Inset) {
        Text(
            text = stringResource(R.string.order_detail_note_conflict_title),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.Warning,
        )
        Text(
            text = stringResource(R.string.order_detail_note_conflict_rejected),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(top = KrtSpacing.s4),
        )
        Text(
            text = refused,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.White,
        )
        KrtGhostButton(
            text = stringResource(R.string.order_detail_note_conflict_reapply),
            onClick = actions.onReapplyRejectedNote,
            modifier = Modifier.padding(top = KrtSpacing.s4),
        )
    }
}

/**
 * How long a note may be.
 *
 * The **contract's** limit, not the mockup's. `AssigneeNoteRequest.note` is capped at 500 on the
 * wire; design ch. 10 draws the counter at 250. Enforcing 250 here would refuse text the server
 * accepts, which is a worse failure than a counter that reads differently from an artboard — the
 * discrepancy is recorded in docs/DESIGN_PARITY_AUDIT.md for the owner to settle.
 */
private const val NOTE_MAX_LENGTH = 500

/** How many lines the note field opens at — artboard 10-5 draws a four-line box. */
private const val NOTE_LINES = 4

/**
 * Where the character counter turns yellow.
 *
 * Thirty characters of warning before the wall — design ch. 10 artboard 6 puts it at 470 of 500.
 */
private const val NOTE_WARN_LENGTH = 470

/**
 * How long ago an order was raised, as a day count.
 *
 * Today and yesterday keep their words — „heute" reads better than „vor 0 Tagen" — and everything
 * older counts days, because that is what the queue is judging and what its colour already says.
 *
 * @param created when it was raised.
 * @return the wording.
 */
@Composable
private fun ageText(created: Instant): String {
    val days = ChronoUnit.DAYS.between(created, Instant.now()).coerceAtLeast(0)
    return when (days) {
        0L -> stringResource(R.string.orders_age_today)
        1L -> stringResource(R.string.orders_age_yesterday)
        else -> pluralStringResource(R.plurals.orders_age_days, days.toInt(), days)
    }
}

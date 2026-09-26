/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.data.NotificationKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSwipeAction
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSwipeableRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the inbox list. */
const val NOTIFICATIONS_LIST_TAG: String = "notifications-list"

/** Width of the orange inset bar that marks an unread row (design ch. 07). */
private val UNREAD_BAR = 3.dp

/** Height of the type icon. */
private val TYPE_ICON = 20.dp

/**
 * The notification inbox (design spec ch. 07), fully interactive.
 *
 * Every action updates the list immediately. A delete removes the row at once but sends the call
 * only after a five-second undo window. Each action is available both by swipe and by an icon
 * button on the row.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the load-more control was tapped.
 * @param onOpen a row was tapped; the host decides whether its subject has a screen yet.
 * @param onMarkRead mark one notification read.
 * @param onMarkAllRead mark every unread notification read.
 * @param onDelete delete one notification, with the undo window.
 * @param onDeleteRead delete every already-read notification.
 * @param onUndoDelete take the pending delete back.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsState,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Notification) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteRead: () -> Unit,
    onUndoDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = INBOX_COLUMN_MAX).fillMaxSize()) {
            if (state.phase is NotificationsPhase.Ready && state.notifications.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                ) {
                    KrtGhostButton(
                        text = stringResource(R.string.notifications_mark_all_read),
                        onClick = onMarkAllRead,
                        modifier = Modifier.weight(1f),
                        enabled = state.unread > 0,
                        iconRes = DesignR.drawable.ic_krt_check,
                    )
                    KrtGhostButton(
                        text = stringResource(R.string.notifications_delete_read),
                        onClick = onDeleteRead,
                        modifier = Modifier.weight(1f),
                        enabled = state.notifications.any { it.read },
                        iconRes = DesignR.drawable.ic_krt_trash,
                    )
                }
            }

            when (state.phase) {
                is NotificationsPhase.Loading -> {
                    KrtLoadingIndicator(
                        text = stringResource(R.string.notifications_title),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsPhase.Failed -> {
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
                            iconRes = DesignR.drawable.ic_krt_bell,
                            title = stringResource(R.string.notifications_error_title),
                            message = stringResource(R.string.notifications_error_message),
                            actionText = stringResource(R.string.missions_retry),
                            onAction = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                        )
                    }
                }

                is NotificationsPhase.Ready -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.notifications.isEmpty()) {
                            KrtRefreshableFill {
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_bell,
                                    title = stringResource(R.string.notifications_empty_title),
                                    message = stringResource(R.string.notifications_empty_message),
                                    modifier = Modifier.padding(KrtSpacing.s16),
                                )
                            }
                        } else {
                            NotificationsList(
                                state = state,
                                onOpen = onOpen,
                                onLoadMore = onLoadMore,
                                onMarkRead = onMarkRead,
                                onDelete = onDelete,
                            )
                        }
                    }
                }
            }
        }
        if (state.pendingDelete != null) {
            KrtToast(
                title = stringResource(R.string.notifications_deleted_title),
                message = stringResource(R.string.notifications_deleted_message),
                actionLabel = stringResource(R.string.notifications_undo),
                onAction = onUndoDelete,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(KrtSpacing.s16),
            )
        }
    }
}

/**
 * The paginated list.
 *
 * @param state what to draw.
 * @param onOpen a row was tapped.
 * @param onLoadMore the next page was asked for.
 * @param onMarkRead mark one notification read.
 * @param onDelete delete one notification.
 */
@Composable
private fun NotificationsList(
    state: NotificationsState,
    onOpen: (Notification) -> Unit,
    onLoadMore: () -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(NOTIFICATIONS_LIST_TAG),
        contentPadding = PaddingValues(horizontal = contentGutter()),
    ) {
        items(state.notifications, key = { it.id }) { notification ->
            KrtSwipeableRow(
                onStartAction = if (notification.read) null else ({ onMarkRead(notification.id) }),
                onEndAction = { onDelete(notification.id) },
                startAction = {
                    KrtSwipeAction(
                        label = stringResource(R.string.notifications_swipe_read),
                        iconRes = DesignR.drawable.ic_krt_check,
                        background = KrtTheme.colors.success,
                    )
                },
                endAction = {
                    KrtSwipeAction(
                        label = stringResource(R.string.notifications_swipe_delete),
                        iconRes = DesignR.drawable.ic_krt_trash,
                        background = KrtTheme.colors.danger,
                    )
                },
            ) {
                NotificationRow(
                    notification = notification,
                    onClick = { onOpen(notification) },
                    onMarkRead = { onMarkRead(notification.id) },
                    onDelete = { onDelete(notification.id) },
                )
            }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text =
                        pluralStringResource(
                            R.plurals.notifications_showing,
                            state.total.toInt(),
                            state.notifications.size,
                            state.total,
                        ),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.notifications_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.s12),
                )
            }
        }
    }
}

/**
 * One notification row.
 *
 * Unread rows carry an orange inset bar, bold bright text and an orange icon; read rows are muted,
 * so the state is not signalled by colour alone.
 *
 * @param notification the notification.
 * @param onClick opens its subject.
 * @param onMarkRead marks it read; the control is hidden once it is.
 * @param onDelete deletes it, with the undo window.
 */
@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(end = KrtSpacing.s12, top = KrtSpacing.s8, bottom = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .width(UNREAD_BAR)
                    .height(ROW_BAR_HEIGHT)
                    .background(
                        if (notification.read) Color.Transparent else MaterialTheme.colorScheme.primary,
                    ),
        )
        KrtIcon(
            id = notification.kind.krtIconRes(),
            contentDescription = null,
            size = TYPE_ICON,
            tint = if (notification.read) KrtPalette.TextMuted else MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        ) {
            Text(
                text = notification.sentence(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
                color = if (notification.read) KrtPalette.TextMuted else KrtPalette.White,
            )
            Text(
                text = notification.timeLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        if (!notification.read) {
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_check,
                label = stringResource(R.string.notifications_mark_read_action),
                onClick = onMarkRead,
            )
        }
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_trash,
            label = stringResource(R.string.notifications_delete_action),
            onClick = onDelete,
        )
    }
}

/** The inbox's own content width on a tablet (design ch. 07: "rail + 720 dp column"). */
private val INBOX_COLUMN_MAX = 720.dp

/** Height of the unread inset bar, matching a two-line row. */
private val ROW_BAR_HEIGHT = 40.dp

/**
 * The sentence a member reads.
 *
 * @return the type's wording with the server's parameters substituted, or the generic wording when
 *   a placeholder cannot be filled.
 */
@Composable
private fun Notification.sentence(): String =
    notificationSentence(
        notification = this,
        template = stringResource(notificationTypeRes(type)),
        generic = stringResource(R.string.notifications_type_generic),
    )

/**
 * How long ago this notification was raised, via the shared [relativeToNow] ladder.
 *
 * @return e.g. "vor 4 Min.", or an empty string when the server sent no timestamp.
 */
@Composable
private fun Notification.timeLabel(): String {
    LocalConfiguration.current
    val raised = createdAt ?: return ""
    return raised.relativeToNow()
}

/**
 * The icon for a notification's source area, shared with the dashboard's unread band.
 *
 * @return the drawable id, per the design's rule.
 */
internal fun NotificationKind.krtIconRes(): Int =
    when (this) {
        NotificationKind.MISSION -> DesignR.drawable.ic_krt_target
        NotificationKind.ORDER -> DesignR.drawable.ic_krt_clipboard_list
        NotificationKind.BANK -> DesignR.drawable.ic_krt_bank
        NotificationKind.EXCHANGE -> DesignR.drawable.ic_krt_swap
        NotificationKind.SYSTEM -> DesignR.drawable.ic_krt_info
    }

/**
 * The inbox, bound to its view model.
 *
 * @param viewModel drives the inbox.
 * @param onOpen a row was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onOpen: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProvideScreenTopBar(
        actions =
            if (state.unread > 0) {
                {
                    KrtChip(
                        text =
                            pluralStringResource(
                                R.plurals.notifications_unread,
                                state.unread.toInt(),
                                state.unread,
                            ),
                        tone = KrtChipTone.Primary,
                        modifier = Modifier.padding(end = KrtSpacing.s16),
                    )
                }
            } else {
                null
            },
    )
    NotificationsScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onOpen = onOpen,
        onMarkRead = viewModel::onMarkRead,
        onMarkAllRead = viewModel::onMarkAllRead,
        onDelete = viewModel::onDelete,
        onDeleteRead = viewModel::onDeleteRead,
        onUndoDelete = viewModel::onUndoDelete,
        modifier = modifier,
    )
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import java.time.Instant
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the inbox list. */
const val NOTIFICATIONS_LIST_TAG: String = "notifications-list"

/** Width of the orange inset bar that marks an unread row (design ch. 07). */
private val UNREAD_BAR = 3.dp

/** Height of the type icon. */
private val TYPE_ICON = 20.dp

/**
 * The notification inbox (design spec ch. 07), read-only.
 *
 * **No mark-read, no delete, no swipe.** All three are mutations and belong to Phase 3; the design's
 * inbox is fully interactive and this one deliberately is not. A row shows whether it is unread and
 * opens its subject, which is what a read-only inbox can honestly offer.
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onLoadMore the load-more control was tapped.
 * @param onOpen a row was tapped; the host decides whether its subject has a screen yet.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.unread > 0) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.notifications_unread,
                        state.unread.toInt(),
                        state.unread,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
            )
        }

        when (state.phase) {
            is NotificationsPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.notifications_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is NotificationsPhase.Failed -> {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_bell,
                    title = stringResource(R.string.notifications_error_title),
                    message = stringResource(R.string.notifications_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
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
                                modifier = Modifier.padding(KrtSpacing.lg),
                            )
                        }
                    } else {
                        NotificationsList(state = state, onOpen = onOpen, onLoadMore = onLoadMore)
                    }
                }
            }
        }
    }
}

/**
 * The paginated list.
 *
 * @param state what to draw.
 * @param onOpen a row was tapped.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun NotificationsList(
    state: NotificationsState,
    onOpen: (Notification) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(NOTIFICATIONS_LIST_TAG)) {
        items(state.notifications, key = { it.id }) { notification ->
            NotificationRow(notification = notification, onClick = { onOpen(notification) })
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
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.notifications_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * One notification row.
 *
 * An unread row carries the design's orange inset bar, a bright bold sentence and an orange type
 * icon; a read one is muted throughout. The two differ in more than one channel on purpose — colour
 * alone would carry the whole distinction, and it is the channel a member with a colour-vision
 * deficiency does not have.
 *
 * @param notification the notification.
 * @param onClick opens its subject.
 */
@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(end = KrtSpacing.md, top = KrtSpacing.sm, bottom = KrtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
            id = notification.kind.iconRes(),
            // The icon repeats the row's source area, which the sentence already names. A screen
            // reader announcing it again would read every row twice.
            contentDescription = null,
            size = TYPE_ICON,
            tint = if (notification.read) KrtPalette.TextMuted else MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
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
    }
}

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
 * How long ago this notification was raised, in the platform's words.
 *
 * The platform's formatter is localised and correctly pluralised in every language Android ships,
 * which a hand-written "vor %d Min." is not.
 *
 * @return e.g. "vor 4 Min.", or an empty string when the server sent no timestamp.
 */
@Composable
private fun Notification.timeLabel(): String {
    // Read so the label recomposes on a locale change.
    LocalConfiguration.current
    return createdAt?.relativeToNow().orEmpty()
}

/**
 * How far in the past an instant is.
 *
 * @return the localised relative span.
 */
private fun Instant.relativeToNow(): String =
    DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

/**
 * The icon for a notification's source area.
 *
 * @return the drawable id, per the design's rule.
 */
private fun NotificationKind.iconRes(): Int =
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
    NotificationsScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        onOpen = onOpen,
        modifier = modifier,
    )
}

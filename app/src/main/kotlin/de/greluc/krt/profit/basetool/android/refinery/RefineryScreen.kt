/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrder
import de.greluc.krt.profit.basetool.android.core.data.RefineryPhase
import de.greluc.krt.profit.basetool.android.core.data.RefineryYield
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtColor
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.krtShortMoment
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the order list. */
const val REFINERY_LIST_TAG: String = "refinery-list"

/** Test handle for one order row. */
const val REFINERY_ROW_TAG: String = "refinery-row"

/** Test handle for the filter chip row. */
const val REFINERY_FILTERS_TAG: String = "refinery-filters"

/** The „Neuer Raffinerieauftrag" action on the list, for the tests that press it. */
const val REFINERY_CREATE_CTA_TAG: String = "refinery-create-cta"

/** Test handle for one row's status pill. */
const val REFINERY_PHASE_TAG: String = "refinery-phase"

/** Test handle for the „In Lager buchen" action. */
const val REFINERY_STORE_TAG: String = "refinery-store"

/** Test handle for the booking confirmation. */
const val REFINERY_STORE_CONFIRM_TAG: String = "refinery-store-confirm"

/** Test handle for the line that reports a completed booking. */
const val REFINERY_STORED_NOTICE_TAG: String = "refinery-stored-notice"

/** Minutes in an hour, for the remaining-time line. */
private const val MINUTES_PER_HOUR = 60L

/**
 * The unit's Raffinerie orders, as a filterable list without a total.
 *
 * @param state what to draw.
 * @param onFilterChanged a chip was tapped.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry.
 * @param onLoadMore the next page was asked for.
 * @param onOpenOrder a row was tapped.
 * @param modifier layout modifier.
 * @param onCreate the „Neuer Raffinerieauftrag" action, or `null` where the screen cannot navigate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefineryOrdersScreen(
    state: RefineryListState,
    onFilterChanged: (RefineryFilter) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
) {
    when (state.phase) {
        is RefineryPhaseState.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.refinery_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is RefineryPhaseState.Failed -> {
            val retryIn = state.retryIn
            if (retryIn != null) {
                KrtRetryCountdown(
                    secondsLeft = retryIn,
                    title = stringResource(R.string.retry_busy_title),
                    message = stringResource(R.string.retry_busy_message, retryIn),
                    retryLabel = stringResource(R.string.retry_now),
                    onRetry = onRetryNow,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_refinery,
                    title = stringResource(R.string.refinery_error_title),
                    message = stringResource(R.string.refinery_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            }
        }

        is RefineryPhaseState.Ready -> {
            Box(modifier = modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FilterRow(selected = state.filter, onFilterChanged = onFilterChanged)
                        if (state.orders.isEmpty()) {
                            KrtRefreshableFill {
                                val active = state.filter == RefineryFilter.ACTIVE
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_refinery,
                                    title =
                                        stringResource(
                                            if (active) {
                                                R.string.refinery_empty_active_title
                                            } else {
                                                R.string.refinery_empty_title
                                            },
                                        ),
                                    message =
                                        stringResource(
                                            if (active) {
                                                R.string.refinery_empty_active_message
                                            } else {
                                                R.string.refinery_empty_message
                                            },
                                        ),
                                    modifier = Modifier.padding(KrtSpacing.s16),
                                )
                            }
                        } else {
                            LazyColumn(
                                state = rememberRootListState(),
                                modifier = Modifier.fillMaxSize().testTag(REFINERY_LIST_TAG),
                                contentPadding = PaddingValues(KrtSpacing.s12),
                                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                            ) {
                                items(state.orders, key = { it.id }) { order ->
                                    OrderRow(
                                        order = order,
                                        now = state.now,
                                        isMine = state.myUserId != null && order.ownerId == state.myUserId,
                                        onClick = { onOpenOrder(order.id) },
                                    )
                                    KrtHairlineRule()
                                }
                                item(key = "footer") {
                                    if (state.hasMore) {
                                        KrtLoadMore(
                                            text =
                                                pluralStringResource(
                                                    R.plurals.refinery_load_more,
                                                    state.orders.size,
                                                    state.orders.size,
                                                ),
                                            onClick = onLoadMore,
                                            enabled = !state.loadingMore,
                                            modifier = Modifier.padding(KrtSpacing.s12),
                                        )
                                    } else {
                                        KrtEndOfList(
                                            text = stringResource(R.string.refinery_end_of_list),
                                            modifier = Modifier.padding(KrtSpacing.s12),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                onCreate?.let { create ->
                    if (isWideWindow()) {
                        KrtCtaButton(
                            text = stringResource(R.string.refinery_create_title),
                            onClick = create,
                            iconRes = DesignR.drawable.ic_krt_plus,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(KrtSpacing.s12)
                                    .testTag(REFINERY_CREATE_CTA_TAG),
                        )
                    } else {
                        KrtFab(
                            iconRes = DesignR.drawable.ic_krt_plus,
                            label = stringResource(R.string.refinery_create_title),
                            onClick = create,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(KrtSpacing.s16)
                                    .padding(bottom = LocalKrtBottomBarInset.current)
                                    .testTag(REFINERY_CREATE_CTA_TAG),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The filter chips, in a horizontally scrollable row.
 *
 * @param selected the active chip.
 * @param onFilterChanged a chip was tapped.
 */
@Composable
private fun FilterRow(
    selected: RefineryFilter,
    onFilterChanged: (RefineryFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8)
                .testTag(REFINERY_FILTERS_TAG),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        RefineryFilter.entries.forEach { filter ->
            KrtFilterChip(
                text = stringResource(filter.labelRes()),
                selected = filter == selected,
                onClick = { onFilterChanged(filter) },
            )
        }
    }
}

/**
 * One order row.
 *
 * @param order the order.
 * @param isMine whether the caller owns it.
 * @param onClick opens it.
 */
@Composable
private fun OrderRow(
    order: RefineryOrder,
    isMine: Boolean,
    now: OffsetDateTime,
    onClick: () -> Unit,
) {
    val phase = order.phaseAt(now)
    KrtCard(
        modifier = Modifier.fillMaxWidth().testTag(REFINERY_ROW_TAG),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = order.locationName.ifBlank { stringResource(R.string.refinery_station_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                order.krtStarted()?.let { started ->
                    Text(
                        text = SEPARATOR + started,
                        style = MaterialTheme.typography.titleMedium,
                        color = KrtPalette.White,
                        maxLines = 1,
                    )
                }
            }
            KrtStatusPill(
                text = stringResource(phase.labelRes()),
                tone = phase.tone(),
                modifier = Modifier.testTag(REFINERY_PHASE_TAG),
            )
        }
        OwnerLine(order = order, isMine = isMine)
        if (order.yields.isNotEmpty()) {
            KrtHairlineRule()
            order.yields.forEach { good -> GoodRow(good = good) }
        }
        CardFooter(order = order, phase = phase, now = now)
    }
}

/**
 * Whose run this is and its method, e.g. „Rhea · Dinyx Solventation".
 *
 * The caller's own row is drawn brighter with a „ (du)" suffix; without a known identity every card
 * just names its owner. The name is never cut; the method ellipsises.
 *
 * @param order the order.
 * @param isMine whether the caller owns it.
 */
@Composable
private fun OwnerLine(
    order: RefineryOrder,
    isMine: Boolean,
) {
    val method = secondLine(order)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIcon(
            id = DesignR.drawable.ic_krt_user,
            contentDescription = null,
            size = OWNER_GLYPH,
            tint = KrtPalette.TextMuted,
        )
        Text(
            text =
                order.ownerName.let { name ->
                    if (isMine) stringResource(R.string.refinery_owner_you, name) else name
                },
            style = MaterialTheme.typography.labelMedium,
            color = if (isMine) KrtPalette.Gray1 else KrtPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (method.isNotBlank()) {
            Text(
                text = SEPARATOR_DOT,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.Gray2,
            )
            Text(
                text = method,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The owner glyph, 12 dp on the phone card (design ch. 11, artboard 1). */
private val OWNER_GLYPH = 12.dp

/** The separator between the owner and the method. */
private const val SEPARATOR_DOT = "·"

/**
 * One refined good: its name, its quality in brackets, and how much of it there is.
 *
 * @param good the yield row.
 */
@Composable
private fun GoodRow(good: RefineryYield) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                good.quality
                    ?.let { stringResource(R.string.refinery_good_with_quality, good.materialName, it) }
                    ?: good.materialName,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amountText(good.amount, good.unitIsPiece),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

/**
 * The card's last row: where the run stands, its estimated UEX value with „≈", and the way in.
 *
 * The value is drawn white or green, never orange.
 *
 * @param order the order.
 * @param phase which of the three states it is in.
 * @param now the clock, for the remaining time.
 */
@Composable
private fun CardFooter(
    order: RefineryOrder,
    phase: RefineryPhase,
    now: OffsetDateTime,
) {
    val value = order.profit?.takeIf { it.isNotBlank() } ?: order.oreSales?.takeIf { it.isNotBlank() }
    if (phase != RefineryPhase.RUNNING && value == null) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val remaining = if (phase == RefineryPhase.RUNNING) remainingText(order.endsAt, now) else ""
        Text(
            text = remaining,
            style = MaterialTheme.typography.bodySmall,
            color = phase.tone().krtColor(),
            modifier = Modifier.weight(1f),
        )
        value?.let { amount ->
            Text(
                text = stringResource(R.string.refinery_value),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            Text(
                text = formatAmount(amount),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (amount.trim().startsWith("-") || amount.trim().startsWith("−")) {
                        KrtTheme.colors.dangerText
                    } else {
                        KrtPalette.SuccessText
                    },
            )
        }
        KrtIcon(
            id = DesignR.drawable.ic_krt_chevron_right,
            contentDescription = null,
            tint = KrtPalette.Gray2,
        )
    }
}

/**
 * The row's second line: the refining method.
 *
 * @param order the order.
 * @return the line, empty when the server named no method.
 */
private fun secondLine(order: RefineryOrder): String = order.methodName.takeIf { it.isNotBlank() }.orEmpty()

/**
 * What the card leads with: the station and the moment the run started.
 *
 * @receiver the run.
 * @return „ARC-L1 · 16.08. 22:41", or the station alone when the server sent no start.
 */
@Composable
private fun RefineryOrder.krtLead(): String {
    val station = locationName.ifBlank { stringResource(R.string.refinery_station_unknown) }
    return listOfNotNull(station, krtStarted()).joinToString(SEPARATOR)
}

/**
 * When the run started, as the chapter writes a moment.
 *
 * @receiver the run.
 * @return „16.08. 22:41", or `null` when the server sent no start.
 */
private fun RefineryOrder.krtStarted(): String? =
    startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.krtShortMoment()

/**
 * The remaining-time text, in minutes rounded up so a run is never shown as ready too early.
 *
 * @param endsAt when the run ends.
 * @param now the clock.
 * @return the text, or the unknown-time fallback.
 */
@Composable
private fun remainingText(
    endsAt: String?,
    now: OffsetDateTime,
): String {
    val end = endsAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    val minutes =
        end?.let { Duration.between(now, it).plusSeconds(SECONDS_PER_MINUTE - 1).toMinutes() }
    val hours = (minutes ?: 0L) / MINUTES_PER_HOUR
    return when {
        minutes == null || minutes <= 0L -> {
            stringResource(R.string.refinery_remaining_unknown)
        }

        hours > 0L -> {
            pluralStringResource(
                R.plurals.refinery_remaining_hours,
                hours.toInt(),
                hours,
                minutes % MINUTES_PER_HOUR,
            )
        }

        else -> {
            pluralStringResource(R.plurals.refinery_remaining_minutes, minutes.toInt(), minutes)
        }
    }
}

/** Seconds in a minute, for the round-up above. */
private const val SECONDS_PER_MINUTE = 60L

/** Separator between the parts of a row's second line. */
private const val SEPARATOR = " · "

/** Test handle for the order detail's `⋮`. */
const val REFINERY_DETAIL_MENU_TAG: String = "refinery-detail-menu"

/** Test handle for the deletion confirmation. */
const val REFINERY_DELETE_MODAL_TAG: String = "refinery-delete-modal"

/** Test handle for „Auftrag gelöscht.". */
const val REFINERY_DELETED_TOAST_TAG: String = "refinery-deleted-toast"

/** How long „Auftrag gelöscht." stands before the screen leaves — two seconds, as chapter 02. */
private const val DELETED_TOAST_MS = 2000L

/**
 * One order in full, with „In Lager buchen".
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry.
 * @param onStoreRequested the booking action was tapped.
 * @param onStoreConfirmed the confirmation was accepted.
 * @param onStoreDismissed the confirmation was dismissed.
 * @param modifier layout modifier.
 * @param menu the edit and delete actions, or `null` where the screen cannot navigate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefineryOrderDetailScreen(
    state: RefineryDetailState,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onStoreRequested: () -> Unit,
    onStoreConfirmed: () -> Unit,
    onStoreDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    menu: RefineryDetailMenu? = null,
) {
    when (state.phase) {
        is RefineryDetailPhase.Loading -> {
            KrtLoadingIndicator(
                text = stringResource(R.string.refinery_title),
                modifier = modifier.fillMaxSize(),
            )
        }

        is RefineryDetailPhase.Failed -> {
            val retryIn = state.retryIn
            if (retryIn != null) {
                KrtRetryCountdown(
                    secondsLeft = retryIn,
                    title = stringResource(R.string.retry_busy_title),
                    message = stringResource(R.string.retry_busy_message, retryIn),
                    retryLabel = stringResource(R.string.retry_now),
                    onRetry = onRetryNow,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_refinery,
                    title = stringResource(R.string.refinery_error_title),
                    message = stringResource(R.string.refinery_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
            }
        }

        is RefineryDetailPhase.Ready -> {
            val order = state.order
            if (order == null) {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_refinery,
                    title = stringResource(R.string.refinery_error_title),
                    message = stringResource(R.string.refinery_error_message),
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.s16),
                )
                return
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                OrderDetailBody(
                    state = state,
                    order = order,
                    menu = menu,
                    onStoreRequested = onStoreRequested,
                )
            }
            if (state.confirming) {
                StoreConfirmation(
                    order = order,
                    onConfirm = onStoreConfirmed,
                    onDismiss = onStoreDismissed,
                )
            }
            if (state.confirmingDelete) {
                DeleteConfirmation(
                    order = order,
                    busy = state.deleting,
                    onConfirm = menu?.onDeleteConfirmed ?: {},
                    onDismiss = menu?.onDeleteDismissed ?: {},
                )
            }
        }
    }
}

/**
 * What the Raffinerie detail's `⋮` offers.
 *
 * @property onEdit open the pre-filled form.
 * @property onDeleteRequested raise the deletion confirmation.
 * @property onDeleteConfirmed it was accepted.
 * @property onDeleteDismissed it was dismissed.
 */
data class RefineryDetailMenu(
    val onEdit: () -> Unit,
    val onDeleteRequested: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onDeleteDismissed: () -> Unit,
)

/**
 * The `⋮` of the order detail, with „Bearbeiten" and „Löschen".
 *
 * Both entries are always drawn; „Löschen" on a booked run is drawn locked with its reason.
 *
 * @param state what the detail holds.
 * @param menu the four callbacks.
 */
@Composable
private fun OrderMenu(
    state: RefineryDetailState,
    menu: RefineryDetailMenu,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val edit = stringResource(R.string.refinery_edit_title)
    val delete = stringResource(R.string.refinery_delete_action)
    val foreignReason = stringResource(R.string.refinery_write_locked_foreign)
    val lockedReason = if (state.mine) stringResource(R.string.refinery_delete_locked_stored) else foreignReason
    KrtOverflowMenu(
        contentDescription = edit,
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.testTag(REFINERY_DETAIL_MENU_TAG),
        items =
            listOf(
                KrtMenuItem(
                    label = edit,
                    iconRes = DesignR.drawable.ic_krt_edit,
                    locked = !state.mine,
                    reason = foreignReason.takeIf { !state.mine },
                    onClick = {
                        open = false
                        if (state.mine) {
                            menu.onEdit()
                        }
                    },
                ),
                KrtMenuItem(
                    label = delete,
                    iconRes = DesignR.drawable.ic_krt_trash,
                    danger = true,
                    locked = !state.deletable,
                    reason = lockedReason.takeIf { !state.deletable },
                    onClick = {
                        open = false
                        if (state.deletable) {
                            menu.onDeleteRequested()
                        }
                    },
                ),
            ),
    )
}

/**
 * The deletion confirmation: a danger modal without a typing hurdle.
 *
 * The body names what goes (the goods lines and an unbooked yield).
 *
 * @param order the run.
 * @param busy whether the deletion is in flight.
 * @param onConfirm it was accepted.
 * @param onDismiss it was dismissed.
 */
@Composable
private fun DeleteConfirmation(
    order: RefineryOrder,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.refinery_delete_title),
        onDismiss = onDismiss,
        tone = KrtModalTone.Danger,
        confirmText = stringResource(R.string.refinery_delete_confirm),
        onConfirm = { if (!busy) onConfirm() },
        modifier = Modifier.testTag(REFINERY_DELETE_MODAL_TAG),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.refinery_delete_body,
                        order.yields.size,
                        order.locationName,
                        order.yields.size,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
            Text(
                text = stringResource(R.string.refinery_delete_note),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The detail body itself.
 *
 * @param state what to draw.
 * @param order the loaded order.
 * @param menu the ⋮ this screen owns, or `null` where the caller draws none.
 * @param onStoreRequested the booking action was tapped.
 */
@Composable
private fun OrderDetailBody(
    state: RefineryDetailState,
    order: RefineryOrder,
    menu: RefineryDetailMenu?,
    onStoreRequested: () -> Unit,
) {
    val phase = order.phaseAt(state.now)
    ProvideScreenTopBar(
        title = stringResource(R.string.refinery_order_title),
        actions = menu?.let { { OrderMenu(state = state, menu = it) } },
        subtitle = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                KrtStatusPill(text = stringResource(phase.labelRes()), tone = phase.tone())
                val identity = order.krtLead()
                if (identity.isNotBlank()) {
                    Text(
                        text = identity,
                        style = MaterialTheme.typography.labelMedium,
                        color = KrtPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
    ) {
        if (!state.online) {
            OfflineBand()
        }
        KrtHudBox(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
                KrtKeyValueRow(
                    label = stringResource(R.string.refinery_station),
                    value =
                        order.locationName.ifBlank {
                            stringResource(R.string.refinery_station_unknown)
                        },
                )
                KrtKeyValueRow(
                    label = stringResource(R.string.refinery_method),
                    value =
                        order.methodName.ifBlank {
                            stringResource(R.string.refinery_method_unknown)
                        },
                )
                KrtKeyValueRow(
                    label = stringResource(R.string.refinery_started),
                    value = order.startedAt.asLocalTimestamp(),
                )
                KrtKeyValueRow(
                    label = stringResource(R.string.refinery_ready),
                    value =
                        if (phase == RefineryPhase.RUNNING) {
                            remainingText(order.endsAt, state.now)
                        } else {
                            order.endsAt
                                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                                ?.relativeToNow()
                                ?: order.endsAt.asLocalTimestamp()
                        },
                )
            }
        }
        KrtSectionTitle(text = stringResource(R.string.refinery_yield))
        order.yields.forEach { YieldRow(it) }
        order.profit?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.refinery_value).krtUppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                )
                Text(
                    text = formatAmount(it),
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.SuccessText,
                )
            }
        }
        if (state.stored) {
            Text(
                text = stringResource(R.string.refinery_stored_notice),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.SuccessText,
                modifier = Modifier.testTag(REFINERY_STORED_NOTICE_TAG),
            )
        }
        if (state.storable) {
            KrtCtaButton(
                text = stringResource(R.string.refinery_store),
                onClick = onStoreRequested,
                modifier = Modifier.fillMaxWidth().testTag(REFINERY_STORE_TAG),
                iconRes = DesignR.drawable.ic_krt_download,
            )
        }
    }
}

/**
 * One yield as a card: the material, its quality beneath it, and the amount right-aligned.
 *
 * @param good the yield.
 */
@Composable
private fun YieldRow(good: RefineryYield) {
    KrtCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = good.materialName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = KrtPalette.White,
                )
                good.quality?.let {
                    Text(
                        text = stringResource(R.string.refinery_quality, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
            }
            Text(
                text = amountText(good.amount, good.unitIsPiece),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KrtPalette.White,
            )
        }
    }
}

/**
 * An amount in the material's own unit, never a hardcoded SCU.
 *
 * @param amount the amount, already in the member's unit.
 * @param piece whether that unit is pieces.
 * @return the rendered figure.
 */
@Composable
private fun amountText(
    amount: Double,
    piece: Boolean,
): String {
    val unit =
        stringResource(if (piece) R.string.refinery_unit_piece else R.string.refinery_unit_scu)
    return "${formatAmount(BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString())} $unit"
}

/**
 * The booking confirmation, naming how many Lager entries it creates: one per material.
 *
 * @param order the order about to be booked.
 * @param onConfirm accepted.
 * @param onDismiss dismissed.
 */
@Composable
private fun StoreConfirmation(
    order: RefineryOrder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = order.yields.count { it.materialId != null }
    KrtModal(
        title = stringResource(R.string.refinery_store),
        confirmText = stringResource(R.string.refinery_store_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        cancelText = stringResource(R.string.refinery_store_cancel),
        modifier = Modifier.testTag(REFINERY_STORE_CONFIRM_TAG),
    ) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.refinery_store_question,
                    entries,
                    entries,
                    order.locationName.ifBlank { stringResource(R.string.refinery_station_unknown) },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

/**
 * Renders a UTC ISO wire timestamp as „16.08. 22:41" in the member's zone (REQ-APP-API-004).
 *
 * An unparseable value is shown as it came; a missing one falls back to the unknown-time wording.
 *
 * @return the formatted stamp.
 */
@Composable
private fun String?.asLocalTimestamp(): String {
    val raw = this?.takeIf { it.isNotBlank() }
    val instant = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val zone = remember { ZoneId.systemDefault() }
    val format =
        remember(zone) {
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
                .withZone(zone)
        }
    return when {
        raw == null -> stringResource(R.string.refinery_remaining_unknown)
        instant == null -> raw
        else -> format.format(instant)
    }
}

/**
 * The chip's label.
 *
 * @return the string resource.
 */
private fun RefineryFilter.labelRes(): Int =
    when (this) {
        RefineryFilter.ACTIVE -> R.string.refinery_filter_active
        RefineryFilter.ALL -> R.string.refinery_filter_all
        RefineryFilter.RUNNING -> R.string.refinery_filter_running
        RefineryFilter.READY -> R.string.refinery_filter_ready
        RefineryFilter.STORED -> R.string.refinery_filter_stored
    }

/**
 * The phase's label.
 *
 * @return the string resource.
 */
private fun RefineryPhase.labelRes(): Int =
    when (this) {
        RefineryPhase.RUNNING -> R.string.refinery_phase_running
        RefineryPhase.READY -> R.string.refinery_phase_ready
        RefineryPhase.STORED -> R.string.refinery_phase_stored
        RefineryPhase.CANCELLED -> R.string.refinery_phase_cancelled
    }

/**
 * The phase's colour, as a design-system status tone.
 *
 * „In Arbeit" is info blue (`Planned`), „Abholbereit" success green (`Active`), „Eingelagert" grey;
 * never orange.
 *
 * @return the tone.
 */
private fun RefineryPhase.tone(): KrtStatusTone =
    when (this) {
        RefineryPhase.RUNNING -> KrtStatusTone.Planned
        RefineryPhase.READY -> KrtStatusTone.Active
        RefineryPhase.STORED -> KrtStatusTone.Completed
        RefineryPhase.CANCELLED -> KrtStatusTone.Cancelled
    }

/**
 * The order list, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param onOpenOrder a row was tapped.
 * @param modifier layout modifier.
 * @param onCreate the „Neuer Raffinerieauftrag" action, or `null` where the host cannot navigate.
 */
@Composable
fun RefineryOrdersRoute(
    viewModel: RefineryViewModel,
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefineryOrdersScreen(
        state = state,
        onFilterChanged = viewModel::onFilterChanged,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onOpenOrder = onOpenOrder,
        onCreate = onCreate,
        modifier = modifier,
    )
}

/**
 * One order, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 * @param onEdit open the pre-filled form, or `null` where the screen cannot navigate; this also
 *   removes the whole `⋮`.
 * @param onDeleted the run was deleted and this screen has nothing left to draw.
 */
@Composable
fun RefineryOrderDetailRoute(
    viewModel: RefineryDetailViewModel,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDeleted: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            delay(DELETED_TOAST_MS)
            onDeleted?.invoke()
        }
    }
    if (state.deleted) {
        Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.BottomCenter) {
            KrtToast(
                title = stringResource(R.string.refinery_delete_title),
                message = stringResource(R.string.refinery_deleted),
                modifier =
                    Modifier
                        .padding(horizontal = KrtSpacing.s16)
                        .padding(bottom = KrtSpacing.s16 + LocalKrtBottomBarInset.current)
                        .testTag(REFINERY_DELETED_TOAST_TAG),
            )
        }
    }
    RefineryOrderDetailScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onStoreRequested = viewModel::onStoreFormRequested,
        onStoreConfirmed = viewModel::onStoreConfirmed,
        onStoreDismissed = viewModel::onStoreDismissed,
        modifier = modifier,
        menu =
            onEdit?.let {
                RefineryDetailMenu(
                    onEdit = it,
                    onDeleteRequested = viewModel::onDeleteRequested,
                    onDeleteConfirmed = viewModel::onDeleteConfirmed,
                    onDeleteDismissed = viewModel::onDeleteDismissed,
                )
            },
    )
    if (state.lines.isNotEmpty()) {
        RefineryStoreSheet(
            lines = state.lines,
            busy = state.busy != null,
            error = state.error,
            actions =
                RefineryStoreActions(
                    onLineChanged = viewModel::onLineChanged,
                    onStoreAll = viewModel::onStoreAll,
                    onDismiss = viewModel::onStoreFormDismissed,
                    onPickMember = viewModel.memberPicker::open,
                    onMemberQuery = viewModel.memberPicker::query,
                    onMemberDismiss = viewModel.memberPicker::dismiss,
                ),
            memberPicker = state.memberPicker,
        )
    }
}

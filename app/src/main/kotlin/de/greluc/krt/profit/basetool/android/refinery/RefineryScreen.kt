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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
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
 * The member's own Raffinerie orders (design spec ch. 11 §1).
 *
 * **No total.** Chapter 11's list shows chips and rows and no count, and that is also the only
 * honest option here: „In Arbeit" and „Abholbereit" are one server answer split on the device, so a
 * server total would describe the pair and a local one only the pages fetched so far.
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
            // The FAB rides over the list, as artboard 11-1 draws it — the same corner every
            // other list in the app puts its „anlegen" in. It sat above the list as a full-width
            // outline band, a shape no chapter draws, and cost a row of the list on every screen
            // for an action most members take once a session.
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
                                KrtEmptyState(
                                    iconRes = DesignR.drawable.ic_krt_refinery,
                                    title = stringResource(R.string.refinery_empty_title),
                                    message = stringResource(R.string.refinery_empty_message),
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
                                        onClick = { onOpenOrder(order.id) },
                                    )
                                    KrtHairlineRule()
                                }
                                item(key = "footer") {
                                    if (state.hasMore) {
                                        // The label counts what is on screen, not a server total: the
                                        // two live filters are a device-side split of one answer, so a
                                        // server count would name the unsplit pair. Saying "mehr
                                        // laden" beside the loaded count is the honest version, and it
                                        // is what keeps this from reading as a completeness claim.
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
                        // A bar across the foot of the list column, as chapter 11's tablet frame
                        // draws it — not a floating button. In a pane this narrow the FAB sat on
                        // top of a row and hid half of it, and the row it covered was a run the
                        // member might have come to collect.
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
 * The four chips of chapter 11.
 *
 * Horizontally scrollable rather than wrapped: „Abholbereit" and „Eingelagert" are long German
 * compounds, and a wrap would put one chip on its own line on a narrow phone.
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
 * @param onClick opens it.
 */
@Composable
private fun OrderRow(
    order: RefineryOrder,
    now: OffsetDateTime,
    onClick: () -> Unit,
) {
    val phase = order.phaseAt(now)
    // A card, not a padded Column: every design chapter draws its list items as bordered
    // tiles, and the app was drawing lines of text. See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(
        modifier = Modifier.fillMaxWidth().testTag(REFINERY_ROW_TAG),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = order.locationName.ifBlank { stringResource(R.string.refinery_station_unknown) },
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KrtStatusPill(
                text = stringResource(phase.labelRes()),
                tone = phase.tone(),
                // Tagged because the chip row above says the same words: „In Arbeit" is both a
                // filter and a status, and both are uppercased by the design system. Without this
                // a test asserting the row's status matches the chip as readily.
                modifier = Modifier.testTag(REFINERY_PHASE_TAG),
            )
        }
        Text(
            text = secondLine(order, phase),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Design ch. 11 artboard 1 lists the goods ON the card. Without them the card says an order
        // exists at a station and nothing about what is in it — and "what is in it" is the reason a
        // member opens the Raffinerie at all.
        if (order.yields.isNotEmpty()) {
            KrtHairlineRule()
            order.yields.forEach { good -> GoodRow(good = good) }
        }
        CardFooter(order = order, phase = phase, now = now)
    }
}

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
 * The card's last row: where the run stands, what it is worth, and the way in.
 *
 * The value is **data**, so it is white or green and never orange — chapter 11 is explicit, and the
 * reason is that orange in this design system means *action*, which an estimate is not. It is an
 * estimate (UEX), and the "≈" says so rather than a footnote nobody reads.
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
            // Muted, not success green: the countdown is a fact about a run still in progress, and
            // artboard 11-1 keeps green for „Abholbereit", which is the state worth spotting from
            // across the list. It was green even for „Restzeit unbekannt", so a run whose end the
            // server does not know read as one ready to collect.
            color = KrtPalette.TextMuted,
            modifier = Modifier.weight(1f),
        )
        value?.let { amount ->
            // Two texts, not one formatted string: „Wert ≈" is a muted label and the figure beside
            // it is the data (artboard 11-1). It WAS one string — and that string carries no
            // placeholder, so `stringResource(id, arg)` dropped the argument and every card in the
            // list read „Geschätzter Wert" with no number at all.
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
 * The row's second line: what a member needs to tell two orders apart at a glance.
 *
 * A running order leads with the time left, because that is the only thing about it that changes;
 * everything else leads with the yield, because that is what the order was for.
 *
 * @param order the order.
 * @param phase what the member sees.
 * @param now the clock.
 * @return the line.
 */
@Composable
private fun secondLine(
    order: RefineryOrder,
    phase: RefineryPhase,
): String {
    val method = order.methodName.takeIf { it.isNotBlank() }
    // A running order's remaining time belongs to the footer (artboard 11.1), where it sits beside
    // the value. Repeating it here put the same clock on the card twice.
    val detail =
        if (phase == RefineryPhase.RUNNING) {
            null
        } else {
            // The order's own unit is not knowable across mixed goods, so the row states SCU —
            // which every refining run in practice is. A single-good order takes the good's unit.
            val piece = order.yields.isNotEmpty() && order.yields.all { it.unitIsPiece }
            amountText(order.totalAmount, piece)
        }
    return listOfNotNull(method, detail).joinToString(SEPARATOR)
}

/**
 * The remaining-time text of chapter 11, at the granularity the clock ticks.
 *
 * Rounded up rather than down: a run with forty seconds left reads „noch 1 Min.", and a member who
 * walks over finds it done. Rounding down would show „noch 0 Min." for a whole minute, which reads
 * as ready and is not.
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
 * One order in full, with „In Lager buchen" (design spec ch. 11 §2).
 *
 * @param state what to draw.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry.
 * @param onStoreRequested the booking action was tapped.
 * @param onStoreConfirmed the confirmation was accepted.
 * @param onStoreDismissed the confirmation was dismissed.
 * @param modifier layout modifier.
 * @param menu the two actions of design ch. 11 artboards 6 and 7, or `null` where the screen
 *   cannot navigate.
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
 * @property onEdit open the pre-filled form (artboard 6).
 * @property onDeleteRequested raise the deletion confirmation (artboard 7).
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
 * The `⋮` of the order detail.
 *
 * Both entries are **drawn for everyone**: whether the caller owns this run or is a logistician is
 * the server's answer. „Löschen" on a booked run is drawn **locked with its reason** rather than
 * left out — the rule is real (its yield exists as Lager rows), but it is the app's rule, so the
 * member is told it rather than shown a menu that quietly lost an entry.
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
    val lockedReason = stringResource(R.string.refinery_delete_locked_stored)
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
                    onClick = {
                        open = false
                        menu.onEdit()
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
                        // A locked row keeps its tap target so it can state its reason —
                        // which the row itself draws. It must not go on to raise the
                        // confirmation for a deletion that will not happen.
                        if (state.deletable) {
                            menu.onDeleteRequested()
                        }
                    },
                ),
            ),
    )
}

/**
 * The deletion confirmation of artboard 7.
 *
 * A danger modal and **no typing hurdle**: the hurdle is reserved for wipe-grade actions (design
 * ch. 02 §7), and a refinery order is one row. The body names what goes — the goods lines and a
 * yield that was never booked — and the one thing that does not apply, because each sentence
 * heads off a different wrong conclusion.
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
    // The run's own head, as artboard 11-2 draws it: the status under the name and, beside it,
    // which refinery and which method. Both stood in the body under the section bar, which left
    // the bar naming the category („RAFFINERIEAUFTRAG") of a screen that shows exactly one.
    //
    // The artboard's „#7841" is mock — no order number exists on the wire, and the web's own
    // title is „Raffinerieauftrag Details" — so the head names what the app actually has.
    ProvideScreenTopBar(
        title = stringResource(R.string.refinery_order_title),
        // **One publisher for the whole bar.** The slot holds one head and the last writer wins,
        // so the overflow published on its own — with a null title — raced this one: whichever
        // ran last, the bar lost either its name or its ⋮. Same trap the Auftrag detail hit.
        actions = menu?.let { { OrderMenu(state = state, menu = it) } },
        subtitle = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                KrtStatusPill(text = stringResource(phase.labelRes()), tone = phase.tone())
                val identity =
                    listOf(order.locationName, order.methodName)
                        .filter { it.isNotBlank() }
                        .joinToString(SEPARATOR)
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
        // Artboard 2 puts the four facts in the HUD box, brackets and all — the same container the
        // rest of the app uses for a block of facts that belong together.
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
                            // „heute 06:41" rather than a full stamp: a finished run's end is read
                            // as "how long ago", which is what the artboard shows.
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
        // One row, not two. The artboard closes the yield block with „Geschätzter Wert" in the
        // success green; chapter 11 wants a UEX estimate behind it, no endpoint offers one, and the
        // recorded profit is the figure the web itself shows. Printing Ore Sales beside it repeated
        // an input as if it were a result. Deviation recorded in docs/specs/refinery.md.
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
 * One yield: the material, its grade beneath it, and the amount.
 *
 * A card rather than a label-value row. Artboard 2 sets the material bold with „Qualität 874"
 * under it and the amount right-aligned; run together on one line the two read as a single long
 * label and the figure stops being scannable.
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
 * An amount in the material's own unit.
 *
 * **Never a hardcoded SCU**, for the same reason as on the Materialbörse: an item counted in pieces
 * and labelled „SCU" is a quantity a member acts on. And never the wire's number either — the
 * repository has already turned units into SCU, and this only has to render what it produced
 * without inventing precision the run did not have.
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
 * The booking confirmation of chapter 11.
 *
 * It names the number of Lager entries the booking will create, because that is the part a member
 * cannot see from the button: one entry per material, not one per order.
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
 * Renders a wire timestamp the way chapter 11 writes one: „16.08. 22:41", in the member's zone.
 *
 * Found on a device: the detail printed `2026-08-24T02:53:02.557721Z` verbatim in both rows. The
 * wire is UTC ISO and the screen is the member's zone (`REQ-APP-API-004`) — the rule every other
 * screen in the app already follows.
 *
 * An unparseable value is shown as it came rather than replaced: a server that changed its format
 * is something to see. A missing one falls back to the unknown-time wording.
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
 * The phase's colour, mapped onto the design system's status tones.
 *
 * Chapter 11 names the three colours outright — „In Arbeit" info, „Abholbereit" success,
 * „Eingelagert" grey — so the mapping is to the design system's tones that carry those hues, not to
 * the tones whose names happen to match the phase. `Active` is the success green and belongs to
 * READY; `Planned` is the info blue and belongs to RUNNING. Nothing here is orange: the chapter
 * reserves that for the brand, and its rule that a value is „weiß/grün — nie orange" applies to the
 * status beside it as much as to the number.
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
 * @param onEdit open the pre-filled form, or `null` where the screen cannot navigate — which also
 *   takes the whole `⋮` away, because the deletion has nowhere to return to either.
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
    // The run is gone; the screen showing it has nothing left to show, so the caller takes over —
    // but not before the confirmation has been read. Artboard 7 puts „Auftrag gelöscht." on the
    // list the member lands on; a toast raised there would have to be handed across two view
    // models, so it is shown here for its two seconds and the navigation follows it.
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
                ),
        )
    }
}

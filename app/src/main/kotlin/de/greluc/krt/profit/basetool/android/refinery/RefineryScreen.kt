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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
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
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_refinery,
                    title = stringResource(R.string.refinery_error_title),
                    message = stringResource(R.string.refinery_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            }
        }

        is RefineryPhaseState.Ready -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    FilterRow(selected = state.filter, onFilterChanged = onFilterChanged)
                    if (state.orders.isEmpty()) {
                        KrtRefreshableFill {
                            KrtEmptyState(
                                iconRes = DesignR.drawable.ic_krt_refinery,
                                title = stringResource(R.string.refinery_empty_title),
                                message = stringResource(R.string.refinery_empty_message),
                                modifier = Modifier.padding(KrtSpacing.lg),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(REFINERY_LIST_TAG),
                            contentPadding = PaddingValues(KrtSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
                                        modifier = Modifier.padding(KrtSpacing.md),
                                    )
                                } else {
                                    KrtEndOfList(
                                        text = stringResource(R.string.refinery_end_of_list),
                                        modifier = Modifier.padding(KrtSpacing.md),
                                    )
                                }
                            }
                        }
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
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm)
                .testTag(REFINERY_FILTERS_TAG),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
 * @param now the clock the phase and the countdown are judged against.
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
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
            text = secondLine(order, phase, now),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
    now: OffsetDateTime,
): String {
    val method = order.methodName.takeIf { it.isNotBlank() }
    val detail =
        if (phase == RefineryPhase.RUNNING) {
            remainingText(order.endsAt, now)
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
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
                )
            } else {
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_refinery,
                    title = stringResource(R.string.refinery_error_title),
                    message = stringResource(R.string.refinery_error_message),
                    actionText = stringResource(R.string.missions_retry),
                    onAction = onRefresh,
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
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
                    modifier = modifier.fillMaxSize().padding(KrtSpacing.lg),
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
        }
    }
}

/**
 * The detail body itself.
 *
 * @param state what to draw.
 * @param order the loaded order.
 * @param onStoreRequested the booking action was tapped.
 */
@Composable
private fun OrderDetailBody(
    state: RefineryDetailState,
    order: RefineryOrder,
    onStoreRequested: () -> Unit,
) {
    val phase = order.phaseAt(state.now)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
    ) {
        if (!state.online) {
            OfflineBand()
        }
        KrtStatusPill(text = stringResource(phase.labelRes()), tone = phase.tone())
        KrtKeyValueRow(
            label = stringResource(R.string.refinery_station),
            value = order.locationName.ifBlank { stringResource(R.string.refinery_station_unknown) },
        )
        KrtKeyValueRow(
            label = stringResource(R.string.refinery_method),
            value = order.methodName.ifBlank { stringResource(R.string.refinery_method_unknown) },
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
                    order.endsAt.asLocalTimestamp()
                },
        )
        KrtSectionTitle(text = stringResource(R.string.refinery_yield))
        order.yields.forEach { YieldRow(it) }
        // Ore Sales and Gewinn/Verlust as DATA -- white, never orange. Chapter 11 asks for a UEX
        // estimate here; no endpoint provides one, and computing one on the device would print a
        // figure the web app never shows. The recorded figures are shown instead, labelled as what
        // they are. Deviation recorded in docs/specs/refinery.md.
        order.oreSales?.let {
            KrtKeyValueRow(label = stringResource(R.string.refinery_ore_sales), value = formatAmount(it))
        }
        order.profit?.let {
            KrtKeyValueRow(label = stringResource(R.string.refinery_profit), value = formatAmount(it))
        }
        if (state.stored) {
            Text(
                text = stringResource(R.string.refinery_stored_notice),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.testTag(REFINERY_STORED_NOTICE_TAG),
            )
        }
        if (state.storable) {
            KrtCtaButton(
                text = stringResource(R.string.refinery_store),
                onClick = onStoreRequested,
                modifier = Modifier.fillMaxWidth().testTag(REFINERY_STORE_TAG),
            )
        }
    }
}

/**
 * One yield row: material, quality, amount.
 *
 * @param good the yield.
 */
@Composable
private fun YieldRow(good: RefineryYield) {
    KrtKeyValueRow(
        label =
            listOfNotNull(
                good.materialName.takeIf { it.isNotBlank() },
                good.quality?.let { stringResource(R.string.refinery_quality, it) },
            ).joinToString(SEPARATOR),
        value = amountText(good.amount, good.unitIsPiece),
    )
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
 */
@Composable
fun RefineryOrdersRoute(
    viewModel: RefineryViewModel,
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefineryOrdersScreen(
        state = state,
        onFilterChanged = viewModel::onFilterChanged,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
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
fun RefineryOrderDetailRoute(
    viewModel: RefineryDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefineryOrderDetailScreen(
        state = state,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onStoreRequested = viewModel::onStoreRequested,
        onStoreConfirmed = viewModel::onStoreConfirmed,
        onStoreDismissed = viewModel::onStoreDismissed,
        modifier = modifier,
    )
}

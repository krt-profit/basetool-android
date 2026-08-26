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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRefreshableFill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the list itself, so a screen test can find it without matching localised copy. */
const val MISSIONS_LIST_TAG: String = "missions-list"

/** Test handle for the search field. */
const val MISSIONS_SEARCH_TAG: String = "missions-search"

/**
 * The Einsatz list (design spec ch. 06 §1).
 *
 * The segment above the search field switches to the Operationen list. It **navigates** rather than
 * swapping a local state: both lists are already destinations of their own — Einsätze in the bottom
 * bar, Operationen behind "Mehr" — so a local toggle would give each list a second address, and the
 * navigation bar would highlight the wrong root for one of them.
 *
 * @param state what to draw.
 * @param onSearchChanged a keystroke in the search field.
 * @param onStatusToggled a status chip was tapped; the screen sends the resulting whole set.
 * @param onIncludePastChanged the "Vergangene" chip was tapped.
 * @param onResetFilters the reset chip was tapped.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onLoadMore the "load more" control was tapped.
 * @param onOpenMission a row was tapped.
 * @param onOpenOperations the Operationen half of the segment was tapped.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionsScreen(
    state: MissionsState,
    onSearchChanged: (String) -> Unit,
    onStatusToggled: (Set<MissionStatus>) -> Unit,
    onIncludePastChanged: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMission: (String) -> Unit,
    onOpenOperations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    // Recomputed on every recomposition rather than remembered: "Heute" has to stop being today
    // when the day rolls over while the app is open.
    val today = LocalDate.now(zone)

    Column(modifier = modifier.fillMaxSize()) {
        ListSegmentBar(
            selected = ListSegment.MISSIONS,
            onSelect = { onOpenOperations() },
        )
        MissionsFilterBar(
            state = state,
            onSearchChanged = onSearchChanged,
            onStatusToggled = onStatusToggled,
            onIncludePastChanged = onIncludePastChanged,
            onResetFilters = onResetFilters,
        )

        // The classified cause is deliberately not shown: an error code means nothing to a member.
        // The view model logged it, which is what a report can be matched against.
        when (state.phase) {
            is MissionsPhase.Loading -> {
                KrtLoadingIndicator(
                    text = stringResource(R.string.missions_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is MissionsPhase.Failed -> {
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
                        iconRes = DesignR.drawable.ic_krt_target,
                        title = stringResource(R.string.missions_error_title),
                        message = stringResource(R.string.missions_error_message),
                        actionText = stringResource(R.string.missions_retry),
                        onAction = onRefresh,
                        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
                    )
                }
            }

            is MissionsPhase.Ready -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.missions.isEmpty()) {
                        KrtRefreshableFill {
                            MissionsEmpty(
                                narrowed = state.isNarrowed,
                                onResetFilters = onResetFilters,
                            )
                        }
                    } else {
                        MissionsList(
                            state = state,
                            zone = zone,
                            today = today,
                            onOpenMission = onOpenMission,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search field plus the filter chip row.
 *
 * @param state what is currently narrowed.
 * @param onSearchChanged a keystroke.
 * @param onStatusToggled the resulting whole status set after a chip tap.
 * @param onIncludePastChanged the past toggle.
 * @param onResetFilters clears everything.
 */
@Composable
private fun MissionsFilterBar(
    state: MissionsState,
    onSearchChanged: (String) -> Unit,
    onStatusToggled: (Set<MissionStatus>) -> Unit,
    onIncludePastChanged: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtTextField(
            // The typed value, not the debounced one. Binding a controlled field to the
            // debounced term feeds the previous value back on every recomposition and the
            // character the member just typed disappears (REQ-APP-MIS-004).
            value = state.searchText,
            onValueChange = onSearchChanged,
            placeholder = stringResource(R.string.missions_search_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(MISSIONS_SEARCH_TAG),
        )
        // FlowRow, not Row: at font scale 1.3x a Row squeezes the last chip until its label
        // breaks character by character („ABGEB ROCHE N"). Wrapping by chip keeps every filter
        // readable and reachable, which horizontal scrolling would not.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            FILTERABLE_STATUSES.forEach { status ->
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
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            KrtFilterChip(
                text =
                    stringResource(
                        if (state.query.includePast) {
                            R.string.missions_filter_past_on
                        } else {
                            R.string.missions_filter_past
                        },
                    ),
                selected = state.query.includePast,
                onClick = { onIncludePastChanged(!state.query.includePast) },
            )
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
 * @param state what to draw.
 * @param zone the device zone that decides which day a row belongs to.
 * @param today the device's current date.
 * @param onOpenMission a row was tapped.
 * @param onLoadMore the next page was asked for.
 */
@Composable
private fun MissionsList(
    state: MissionsState,
    zone: ZoneId,
    today: LocalDate,
    onOpenMission: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val sections = groupMissionsByDay(state.missions, zone, today)
    LazyColumn(
        state = rememberRootListState(),
        modifier = Modifier.fillMaxSize().testTag(MISSIONS_LIST_TAG),
        contentPadding = PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        sections.forEach { section ->
            item(key = "day-${section.day}") {
                KrtSectionTitle(
                    text = section.day.label(),
                    modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
                )
            }
            items(section.missions, key = { it.id }) { mission ->
                MissionRow(mission = mission, zone = zone, onClick = { onOpenMission(mission.id) })
            }
        }
        item(key = "footer") {
            if (state.hasMore) {
                KrtLoadMore(
                    text =
                        pluralStringResource(
                            R.plurals.missions_count,
                            state.total.toInt(),
                            state.missions.size,
                            state.total,
                        ),
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            } else {
                KrtEndOfList(
                    text = stringResource(R.string.missions_end_of_list),
                    modifier = Modifier.padding(KrtSpacing.md),
                )
            }
        }
    }
}

/**
 * One Einsatz row.
 *
 * @param mission the Einsatz.
 * @param zone the device zone used to format its times.
 * @param onClick opens it.
 */
@Composable
private fun MissionRow(
    mission: Mission,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    // A card, not a padded Column: every design chapter draws its list items as bordered
    // tiles, and the app was drawing lines of text. See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mission.name,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            KrtStatusBadge(text = mission.missionStatusLabel(), tone = mission.missionStatusTone())
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mission.timeLabel(zone),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.weight(1f),
            )
            mission.orgUnitShorthand?.takeIf { it.isNotBlank() }?.let { KrtOrgBadge(text = it) }
            // Design ch. 06 artboard 1 closes every row with a chevron. On a card whose whole
            // surface is the tap target, it is the only thing that says the card HAS a target —
            // without it the row reads as a summary rather than as a way in.
            KrtIcon(
                id = DesignR.drawable.ic_krt_chevron_right,
                contentDescription = null,
                tint = KrtPalette.Gray2,
            )
        }
    }
}

/**
 * The empty state, which says something different depending on why it is empty.
 *
 * "Nothing is scheduled" and "your filters match nothing" are different facts, and showing the
 * first when the second is true tells the member the squadron is idle when it is not.
 *
 * @param narrowed whether a filter is applied.
 * @param onResetFilters clears the filters from inside the empty state.
 */
@Composable
private fun MissionsEmpty(
    narrowed: Boolean,
    onResetFilters: () -> Unit,
) {
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_target,
        title =
            stringResource(
                if (narrowed) R.string.missions_empty_filtered_title else R.string.missions_empty_title,
            ),
        message =
            stringResource(
                if (narrowed) R.string.missions_empty_filtered_message else R.string.missions_empty_message,
            ),
        actionText = if (narrowed) stringResource(R.string.missions_filter_reset) else null,
        onAction = if (narrowed) onResetFilters else null,
        modifier = Modifier.padding(KrtSpacing.lg),
    )
}

/** The statuses offered as filter chips; [MissionStatus.UNKNOWN] is this build's word, not a server value. */
private val FILTERABLE_STATUSES =
    listOf(
        MissionStatus.PLANNED,
        MissionStatus.ACTIVE,
        MissionStatus.COMPLETED,
        MissionStatus.CANCELLED,
    )

/**
 * The string resource naming this status.
 *
 * @return the resource id; [MissionStatus.UNKNOWN] has none and must not reach here.
 */
private fun MissionStatus.labelRes(): Int =
    when (this) {
        MissionStatus.PLANNED -> R.string.missions_status_planned

        MissionStatus.ACTIVE -> R.string.missions_status_active

        MissionStatus.COMPLETED -> R.string.missions_status_completed

        MissionStatus.CANCELLED -> R.string.missions_status_cancelled

        // Never offered as a filter, and a row carrying it shows its raw server value instead.
        MissionStatus.UNKNOWN -> R.string.missions_title
    }

/**
 * The badge text for this Einsatz.
 *
 * @return the translated status, or the raw server value when this build does not know it — an
 *   untranslated word beats a missing badge, which would read as "no status".
 */
@Composable
internal fun Mission.missionStatusLabel(): String =
    if (status == MissionStatus.UNKNOWN) {
        rawStatus.orEmpty()
    } else {
        stringResource(status.labelRes())
    }

/**
 * The badge tone for this Einsatz.
 *
 * @return the design system's tone for the status; an unknown one is drawn as planned rather than
 *   as a problem, because "this build is older than the server" is not the member's fault.
 */
internal fun Mission.missionStatusTone(): KrtStatusTone =
    when (status) {
        MissionStatus.PLANNED, MissionStatus.UNKNOWN -> KrtStatusTone.Planned
        MissionStatus.ACTIVE -> KrtStatusTone.Active
        MissionStatus.COMPLETED -> KrtStatusTone.Completed
        MissionStatus.CANCELLED -> KrtStatusTone.Cancelled
    }

/**
 * The row's time line.
 *
 * A running Einsatz reads "seit 18:10" — an absolute time, because a member joining late wants to
 * know how far in they are. Anything else reads "TS 20:30 · in 2 Std.": the gathering time plus how
 * long until it, which is the pair the design mock shows.
 *
 * The relative half comes from the platform's own formatter rather than from strings of ours. It is
 * localised and correctly pluralised in every language Android ships, which a hand-written
 * "in %d Std." is not.
 *
 * @param zone the device zone.
 * @return the line, or an empty string when the server dated the Einsatz not at all.
 */
@Composable
private fun Mission.timeLabel(zone: ZoneId): String {
    // Read so the label recomposes with a locale change; the formatter itself follows the same one.
    LocalConfiguration.current
    val timeFormat = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }

    val running = actualStartTime?.takeIf { status == MissionStatus.ACTIVE }
    val gathering = meetingTime ?: plannedStartTime
    return when {
        running != null -> {
            stringResource(R.string.missions_running_since, timeFormat.format(running))
        }

        gathering == null -> {
            ""
        }

        else -> {
            val absolute =
                if (meetingTime != null) {
                    stringResource(R.string.missions_meeting_time, timeFormat.format(gathering))
                } else {
                    timeFormat.format(gathering)
                }
            "$absolute · ${gathering.relativeToNow()}"
        }
    }
}

/**
 * The heading text for a day section.
 *
 * @return "Heute"/"Morgen" from resources, a localised date for any other day, and the undated
 *   heading for Einsätze the server gave no time.
 */
@Composable
private fun MissionDay.label(): String =
    when (this) {
        MissionDay.Today -> {
            stringResource(R.string.missions_day_today)
        }

        MissionDay.Tomorrow -> {
            stringResource(R.string.missions_day_tomorrow)
        }

        MissionDay.Undated -> {
            stringResource(R.string.missions_day_undated)
        }

        // Design ch. 06 artboard 1 writes it "DIENSTAG · 19.08." — the weekday, then the date in
        // digits. FormatStyle.FULL spells the month out ("Donnerstag, 27. August 2026"), which on a
        // 411 dp phone is a heading wider than the rows it groups. The year is dropped for the same
        // reason it is missing from the artboard: this list only ever shows the near future.
        is MissionDay.On -> {
            date.format(DateTimeFormatter.ofPattern(stringResource(R.string.missions_day_pattern)))
        }
    }

/**
 * The Einsatz list, bound to its view model.
 *
 * @param viewModel drives the list.
 * @param onOpenMission a row was tapped.
 * @param onOpenOperations the Operationen half of the segment was tapped.
 * @param modifier layout modifier.
 */
@Composable
fun MissionsRoute(
    viewModel: MissionsViewModel,
    onOpenMission: (String) -> Unit,
    onOpenOperations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MissionsScreen(
        state = state,
        onSearchChanged = viewModel::onSearchChanged,
        onStatusToggled = viewModel::onStatusesChanged,
        onIncludePastChanged = viewModel::onIncludePastChanged,
        onResetFilters = viewModel::onResetFilters,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onOpenMission = onOpenMission,
        onOpenOperations = onOpenOperations,
        modifier = modifier,
    )
}

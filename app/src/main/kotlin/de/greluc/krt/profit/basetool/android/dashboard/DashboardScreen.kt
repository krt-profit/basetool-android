/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHeading
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRailCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.missions.missionStatusLabel
import de.greluc.krt.profit.basetool.android.missions.missionStatusTone
import de.greluc.krt.profit.basetool.android.notifications.krtIconRes
import de.greluc.krt.profit.basetool.android.notifications.notificationSentence
import de.greluc.krt.profit.basetool.android.notifications.notificationTypeRes
import de.greluc.krt.profit.basetool.android.ui.carriesClock
import de.greluc.krt.profit.basetool.android.ui.contentGutter
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.ui.relativeTo
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.ui.rememberRootListState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the dashboard's scrolling content. */
const val DASHBOARD_TAG: String = "dashboard"

/** How many lines of the announcement are shown while it is collapsed. */
private const val ANNOUNCEMENT_COLLAPSED_LINES = 2

/**
 * The dashboard, read-only: greeting, announcement, the Einsätze of the next seven days, four
 * shortcuts and the unread preview.
 *
 * @param state the fetched parts.
 * @param memberName the signed-in member's name, or `null` while unknown.
 * @param orgUnitName the active org unit's name, or `null` while unknown.
 * @param onMarkAnnouncementRead the notice's own action; clears its unread marker.
 * @param onRefresh pull-to-refresh.
 * @param onOpenMission an Einsatz row was tapped.
 * @param onOpenMissions the Einsatz band's header action.
 * @param onQuickAction opens the destination behind a shortcut tile.
 * @param onOpenInbox opens the inbox, from the unread band's header action and its rows.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    memberName: String?,
    orgUnitName: String?,
    onMarkAnnouncementRead: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMission: (String) -> Unit,
    onOpenMissions: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (isWideWindow()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = KrtSpacing.s12).testTag(DASHBOARD_TAG),
            ) {
                Greeting(memberName = memberName, orgUnitName = orgUnitName)
                state.announcement?.let {
                    AnnouncementBand(
                        text = it.content,
                        read = state.announcementRead,
                        onMarkRead = onMarkAnnouncementRead,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
                ) {
                    LazyColumn(
                        state = rememberRootListState(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = KrtSpacing.s12),
                    ) {
                        missionsSection(
                            state = state,
                            onOpenMission = onOpenMission,
                            onOpenMissions = onOpenMissions,
                        )
                    }
                    LazyColumn(
                        state = rememberRootListState(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = KrtSpacing.s12),
                    ) {
                        quickActionsSection(onQuickAction = onQuickAction)
                        unreadSection(state = state, onOpenInbox = onOpenInbox)
                    }
                }
            }
        } else {
            LazyColumn(
                state = rememberRootListState(),
                modifier = Modifier.fillMaxSize().testTag(DASHBOARD_TAG),
                contentPadding = PaddingValues(horizontal = contentGutter()),
            ) {
                item(key = "greeting") {
                    Greeting(memberName = memberName, orgUnitName = orgUnitName)
                }
                state.announcement?.let { announcement ->
                    item(key = "announcement") {
                        AnnouncementBand(
                            text = announcement.content,
                            read = state.announcementRead,
                            onMarkRead = onMarkAnnouncementRead,
                        )
                    }
                }
                missionsSection(
                    state = state,
                    onOpenMission = onOpenMission,
                    onOpenMissions = onOpenMissions,
                )
                quickActionsSection(onQuickAction = onQuickAction)
                unreadSection(state = state, onOpenInbox = onOpenInbox)
            }
        }
    }
}

/**
 * The dashboard's fixed set of four shortcuts between the Einsätze band and the inbox.
 *
 * Each opens the surface its action lives on rather than the action itself.
 *
 * @param onQuickAction opens the destination behind a tile.
 */
private fun LazyListScope.quickActionsSection(onQuickAction: (QuickAction) -> Unit) {
    item(key = "quick-title") {
        KrtSectionTitle(
            text = stringResource(R.string.dashboard_quick_actions),
            modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        )
    }
    item(key = "quick-tiles") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            QuickAction.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                ) {
                    pair.forEach { action ->
                        QuickActionTile(
                            action = action,
                            onClick = { onQuickAction(action) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One square, outlined shortcut tile with the glyph beside its label, so a long label can wrap.
 *
 * @param action which shortcut this is.
 * @param onClick opens it.
 * @param modifier layout modifier, carrying the row's equal-share weight.
 */
@Composable
private fun QuickActionTile(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxHeight()
                .heightIn(min = QUICK_TILE_MIN_HEIGHT)
                .background(KrtPalette.Gray4)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .clickable(onClick = onClick)
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIcon(
            id = action.iconRes,
            contentDescription = null,
            size = QUICK_TILE_ICON,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(action.labelRes).krtUppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = KrtPalette.Gray1,
        )
    }
}

/**
 * The "Einsätze" band of the dashboard.
 *
 * A `LazyListScope` extension so the same rows serve the phone's single list and the tablet's left
 * list.
 *
 * @param state what to draw.
 * @param onOpenMission opens one Einsatz.
 * @param onOpenMissions opens the full list.
 */
private fun LazyListScope.missionsSection(
    state: DashboardState,
    onOpenMission: (String) -> Unit,
    onOpenMissions: () -> Unit,
) {
    item(key = "missions-title") {
        KrtSectionTitle(
            text = stringResource(R.string.dashboard_missions),
            modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
            trailing = {
                if (state.missions.isNotEmpty()) {
                    SectionAction(
                        text = stringResource(R.string.dashboard_missions_all),
                        onClick = onOpenMissions,
                    )
                }
            },
        )
    }
    when {
        state.phase is DashboardPhase.Failed -> {
            item(key = "missions-failed") {
                MutedLine(text = stringResource(R.string.dashboard_missions_failed))
            }
        }

        state.missions.isEmpty() && state.phase is DashboardPhase.Ready -> {
            item(key = "missions-empty") {
                MutedLine(text = stringResource(R.string.dashboard_missions_empty))
            }
        }

        else -> {
            items(state.missions, key = { it.id }) { mission ->
                MissionBandRow(mission = mission, onClick = { onOpenMission(mission.id) })
            }
        }
    }
}

/**
 * The greeting and the context line beneath it.
 *
 * @param memberName the member's name, or `null`.
 * @param orgUnitName the active org unit, or `null`.
 */
@Composable
private fun Greeting(
    memberName: String?,
    orgUnitName: String?,
) {
    LocalConfiguration.current
    val zone = remember { ZoneId.systemDefault() }
    val date = LocalDate.now(zone)
    val today =
        stringResource(
            R.string.dashboard_date,
            date.format(DateTimeFormatter.ofPattern(stringResource(R.string.dashboard_date_pattern))),
            date.year + SC_YEAR_OFFSET,
        )

    KrtRailCard(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
        contentPadding = PaddingValues(KrtSpacing.s12),
    ) {
        KrtHeading(
            text =
                if (memberName.isNullOrBlank()) {
                    stringResource(R.string.dashboard_greeting_anonymous)
                } else {
                    stringResource(R.string.dashboard_greeting, memberName)
                },
            style = MaterialTheme.typography.headlineSmall,
        )
        orgUnitName?.takeIf { it.isNotBlank() }?.let { unit ->
            Text(
                text = stringResource(R.string.dashboard_context, unit, today),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * The announcement, collapsed to two lines until tapped.
 *
 * The expanded state is local and not saved.
 *
 * @param text the announcement.
 */
@Composable
private fun AnnouncementBand(
    text: String,
    read: Boolean,
    onMarkRead: () -> Unit,
) {
    var expanded by rememberSaveable(read) { mutableStateOf(!read) }
    val action =
        stringResource(
            if (expanded) R.string.dashboard_announcement_collapse else R.string.dashboard_announcement_expand,
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics { contentDescription = action }
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_announcement).krtUppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = KrtPalette.White,
            )
            if (!read) {
                KrtChip(text = stringResource(R.string.dashboard_announcement_unread), tone = KrtChipTone.Primary)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            maxLines = if (expanded) Int.MAX_VALUE else ANNOUNCEMENT_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (!read) {
            Text(
                text = stringResource(R.string.dashboard_announcement_mark_read).krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.Gray1,
                modifier =
                    Modifier
                        .clickable(onClick = onMarkRead)
                        .padding(vertical = KrtSpacing.s8),
            )
        }
        KrtHairlineRule()
    }
}

/**
 * One Einsatz in the seven-day band.
 *
 * @param mission the Einsatz.
 * @param onClick opens it.
 */
@Composable
private fun MissionBandRow(
    mission: Mission,
    onClick: () -> Unit,
) {
    KrtHudBox(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s14),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mission.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                mission.description?.let { briefing ->
                    Text(
                        text = briefing,
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KrtStatusPill(text = mission.missionStatusLabel(), tone = mission.missionStatusTone())
        }
        MissionFactsRow(mission = mission)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = KrtSpacing.s10)
                    .height(KrtSpacing.hairline)
                    .background(KrtPalette.Gray3),
        )
        MissionBandFooter(mission = mission)
    }
}

/**
 * When and where, each behind the glyph the design gives it.
 *
 * @param mission the Einsatz.
 */
@Composable
private fun MissionFactsRow(mission: Mission) {
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }
    val tick by produceState(0L) {
        while (true) {
            delay(COUNTDOWN_TICK_MS)
            value += 1
        }
    }
    val context = LocalContext.current
    val meeting =
        mission.meetingTime?.let { at ->
            remember(at, tick, context, zone) {
                val relative = at.relativeTo(Instant.now(), context, zone)
                if (at.carriesClock()) relative else "$relative · TS " + formatter.format(at)
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        meeting?.let { GlyphFact(icon = DesignR.drawable.ic_krt_clock, text = it) }
        mission.meetingPoint?.takeIf { it.isNotBlank() }?.let { place ->
            GlyphFact(icon = DesignR.drawable.ic_krt_map_pin, text = place)
        }
    }
}

/**
 * The band's last row: whose Einsatz it is, and the way in.
 *
 * @param mission the Einsatz.
 */
@Composable
private fun MissionBandFooter(mission: Mission) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mission.orgUnitShorthand?.takeIf { it.isNotBlank() }?.let { unit ->
            KrtChip(text = unit, tone = KrtChipTone.Primary)
        }
        mission.registeredCount?.let { count ->
            Text(
                text = pluralStringResource(R.plurals.mission_lifecycle_registered, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.dashboard_mission_open).krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        KrtIcon(
            id = DesignR.drawable.ic_krt_chevron_right,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One fact behind its glyph.
 *
 * @param icon the design's glyph for this fact.
 * @param text the fact.
 */
@Composable
private fun GlyphFact(
    @DrawableRes icon: Int,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIcon(id = icon, contentDescription = null, tint = KrtPalette.TextMuted)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A quiet single line, for the states that have no timestamp to show.
 *
 * @param text the line.
 */
@Composable
private fun MutedLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
    )
}

/**
 * A tappable line that leads to the full screen behind a band.
 *
 * @param text the label.
 * @param onClick where it goes.
 */
@Composable
private fun SectionAction(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(KrtSpacing.s4),
    )
}

/** Smallest a shortcut tile gets, so a wrapped label never squeezes the glyph out (artboard: 64). */
private val QUICK_TILE_MIN_HEIGHT = 64.dp

/** The glyph beside a shortcut's label, at the artboard's size. */
private val QUICK_TILE_ICON = 22.dp

/**
 * The offset between the Star Citizen calendar year and the real year (2956 is 2026).
 *
 * The greeting prints the real date followed by the SC year in brackets.
 */
private const val SC_YEAR_OFFSET = 930

/** How often the seven-day band re-reads its countdowns (design ch. 05: "each minute"). */
private const val COUNTDOWN_TICK_MS = 60_000L

/**
 * The unread band: a read-only preview of the newest unread notifications that opens the inbox.
 *
 * Nothing here marks a notification read. The band is absent when nothing is unread.
 *
 * @param state the fetched parts.
 * @param onOpenInbox opens the inbox.
 */
private fun LazyListScope.unreadSection(
    state: DashboardState,
    onOpenInbox: () -> Unit,
) {
    if (state.unread.isEmpty()) {
        return
    }
    item(key = "unread-title") {
        KrtSectionTitle(
            text = stringResource(R.string.dashboard_unread),
            modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
            trailing = {
                SectionAction(
                    text = stringResource(R.string.dashboard_unread_all),
                    onClick = onOpenInbox,
                )
            },
        )
    }
    items(state.unread, key = { "unread-${it.id}" }) { notification ->
        UnreadRow(
            notification = notification,
            onClick = onOpenInbox,
            modifier = Modifier.padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s4),
        )
    }
}

/**
 * One row of the unread band, styled and worded like the inbox's own rows.
 *
 * @param notification the row.
 * @param onClick opens the inbox.
 * @param modifier layout modifier.
 */
@Composable
private fun UnreadRow(
    notification: Notification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(KrtSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(UNREAD_BAR)
                    .height(UNREAD_BAR_HEIGHT)
                    .background(MaterialTheme.colorScheme.primary),
        )
        KrtIcon(
            id = notification.kind.krtIconRes(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text =
                notificationSentence(
                    notification = notification,
                    template = stringResource(notificationTypeRes(notification.type)),
                    generic = stringResource(R.string.notifications_type_generic),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        notification.createdAt?.let { raised ->
            Text(
                text = raised.relativeToNow(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/** Width of a row's unread inset bar. */
private val UNREAD_BAR = 3.dp

/** Height of that bar — a two-line row, as the inbox draws it. */
private val UNREAD_BAR_HEIGHT = 40.dp

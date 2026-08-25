/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.missions.missionStatusLabel
import de.greluc.krt.profit.basetool.android.missions.missionStatusTone
import de.greluc.krt.profit.basetool.android.notifications.notificationSentence
import de.greluc.krt.profit.basetool.android.notifications.notificationTypeRes
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Test handle for the dashboard's scrolling content. */
const val DASHBOARD_TAG: String = "dashboard"

/** How many lines of the announcement are shown while it is collapsed. */
private const val ANNOUNCEMENT_COLLAPSED_LINES = 2

/** How many unread notifications the preview band shows. */
private const val PREVIEW_ROWS = 3

/**
 * The dashboard (design spec ch. 05), read-only.
 *
 * The design's order is kept — greeting, announcement, Einsätze of the next seven days, then the
 * unread preview — because it is a one-handed reading order and not a layout preference.
 *
 * **The quick-action row is absent.** Its four entries (Check-In, Einbuchen, Auftrag, Angebot) are
 * all mutations, and three of them lead to screens Phase 2 does not build. Four buttons that do
 * nothing would be worse than the row arriving with what it promises.
 *
 * @param state the fetched parts.
 * @param memberName the signed-in member's name, or `null` while unknown.
 * @param orgUnitName the active org unit's name, or `null` while unknown.
 * @param unread the newest unread notifications, already limited by the caller.
 * @param unreadKnown whether the inbox has answered at all — "nothing unread" is a claim and
 *   may only be made once it has.
 * @param onRefresh pull-to-refresh.
 * @param onOpenMission an Einsatz row was tapped.
 * @param onOpenMissions the Einsatz band's header action.
 * @param onOpenNotifications the preview's header action.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    memberName: String?,
    orgUnitName: String?,
    unread: List<Notification>,
    unreadKnown: Boolean,
    onRefresh: () -> Unit,
    onOpenMission: (String) -> Unit,
    onOpenMissions: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (isWideWindow()) {
            // Design ch. 05 asks for a two-column grid on the tablet. The greeting and the
            // announcement stay full width — they address the member and the whole org, not one
            // of the two columns — and the two sections sit side by side below them.
            //
            // Two independent LazyColumns rather than one grid: the sections are different
            // lengths and scroll at their own pace, and a grid would tie the last Einsatz to
            // whatever notification happens to sit beside it.
            Column(modifier = Modifier.fillMaxSize().testTag(DASHBOARD_TAG)) {
                Greeting(memberName = memberName, orgUnitName = orgUnitName)
                state.announcement?.let { AnnouncementBand(text = it.content) }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.lg),
                ) {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        missionsSection(
                            state = state,
                            onOpenMission = onOpenMission,
                            onOpenMissions = onOpenMissions,
                        )
                    }
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        notificationsSection(
                            unread = unread,
                            unreadKnown = unreadKnown,
                            onOpenNotifications = onOpenNotifications,
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag(DASHBOARD_TAG)) {
                item(key = "greeting") {
                    Greeting(memberName = memberName, orgUnitName = orgUnitName)
                }
                state.announcement?.let { announcement ->
                    item(key = "announcement") {
                        AnnouncementBand(text = announcement.content)
                    }
                }
                missionsSection(
                    state = state,
                    onOpenMission = onOpenMission,
                    onOpenMissions = onOpenMissions,
                )
                notificationsSection(
                    unread = unread,
                    unreadKnown = unreadKnown,
                    onOpenNotifications = onOpenNotifications,
                )
            }
        }
    }
}

/**
 * The "Einsätze" half of the dashboard.
 *
 * A `LazyListScope` extension rather than a composable so the same rows can be the top half of one
 * list on a phone and the whole of the left list on a tablet, without either layout owning a copy.
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
            modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
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
            item(key = "missions-all") {
                LinkLine(
                    text = stringResource(R.string.dashboard_missions_all),
                    onClick = onOpenMissions,
                )
            }
        }
    }
}

/**
 * The "Benachrichtigungen" half of the dashboard.
 *
 * @param unread the unread rows, of which only the first few are previewed.
 * @param unreadKnown whether the inbox has answered yet.
 * @param onOpenNotifications opens the inbox.
 */
private fun LazyListScope.notificationsSection(
    unread: List<Notification>,
    unreadKnown: Boolean,
    onOpenNotifications: () -> Unit,
) {
    item(key = "notifications-title") {
        KrtSectionTitle(
            text = stringResource(R.string.dashboard_notifications),
            modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        )
    }
    if (unread.isEmpty()) {
        // Silence while the inbox is still answering: saying "nothing unread" before it
        // has is a claim about the member's inbox made out of not knowing yet.
        if (unreadKnown) {
            item(key = "notifications-empty") {
                MutedLine(text = stringResource(R.string.dashboard_notifications_empty))
            }
        }
    } else {
        items(unread.take(PREVIEW_ROWS), key = { it.id }) { notification ->
            MutedLine(text = notification.preview())
        }
        item(key = "notifications-all") {
            LinkLine(
                text = stringResource(R.string.dashboard_notifications_all),
                onClick = onOpenNotifications,
            )
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
    // Read so the date recomposes on a locale change, and recomputed rather than remembered so
    // "today" stops being today when the day rolls over with the app open.
    LocalConfiguration.current
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    Column(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Text(
            text =
                if (memberName.isNullOrBlank()) {
                    stringResource(R.string.dashboard_greeting_anonymous)
                } else {
                    stringResource(R.string.dashboard_greeting, memberName)
                },
            style = MaterialTheme.typography.titleLarge,
            color = KrtPalette.White,
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
 * Collapsing rather than truncating: an announcement is written to be read, and a notice cut off at
 * two lines with no way to see the rest is worse than none. The state is local and unsaved on
 * purpose — marking it read is a mutation and belongs to Phase 3, so the app must not pretend to
 * remember a decision it cannot store.
 *
 * @param text the announcement.
 */
@Composable
private fun AnnouncementBand(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.dashboard_announcement),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
            maxLines = if (expanded) Int.MAX_VALUE else ANNOUNCEMENT_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
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
    // A card, not a padded Row: every design chapter draws its list items as bordered tiles.
    // See docs/DESIGN_PARITY_AUDIT.md.
    KrtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mission.name,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KrtStatusBadge(text = mission.missionStatusLabel(), tone = mission.missionStatusTone())
        }
    }
}

/**
 * A muted line, used for the bands' empty and failed states.
 *
 * @param text what to say.
 */
@Composable
private fun MutedLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
    )
}

/**
 * A tappable line that leads to the full screen behind a band.
 *
 * @param text the label.
 * @param onClick where it goes.
 */
@Composable
private fun LinkLine(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm),
    )
}

/**
 * The one-line wording of a notification in the preview.
 *
 * @return the same sentence the inbox shows, so the two cannot describe one notification
 *   differently.
 */
@Composable
private fun Notification.preview(): String =
    notificationSentence(
        notification = this,
        template = stringResource(notificationTypeRes(type)),
        generic = stringResource(R.string.notifications_type_generic),
    )

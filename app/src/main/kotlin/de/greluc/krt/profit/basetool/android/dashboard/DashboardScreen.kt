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
 * @param onMarkAnnouncementRead the notice's own action; clears its unread marker.
 * @param onRefresh pull-to-refresh.
 * @param onOpenMission an Einsatz row was tapped.
 * @param onOpenMissions the Einsatz band's header action.
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
            Column(
                // The gutter sits on the column rather than on the two lists, so the greeting and
                // the announcement line up with the cards under them instead of running out to the
                // rail on one side and the screen edge on the other.
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
                    }
                }
            }
        } else {
            LazyColumn(
                state = rememberRootListState(),
                modifier = Modifier.fillMaxSize().testTag(DASHBOARD_TAG),
                // Zero on a phone, where chapter 05 draws the band full-bleed with its padding
                // inside the card. A medium window reaches this branch too - it has no room for
                // two columns but plenty to spare sideways - and there the gutter does apply.
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
            }
        }
    }
}

/**
 * The four shortcuts design chapter 05 puts between the Einsätze band and the inbox.
 *
 * The set is fixed rather than derived: the chapter names these four, and a dashboard whose
 * shortcuts move with the data is one a member cannot build muscle memory on. Each opens the
 * surface the action lives on rather than the action itself — there is no global "check in", only
 * a check-in on one Einsatz, and sending a member to a guessed Einsatz would be worse than sending
 * them to the list they can pick from.
 *
 * The chapter notes "(user pick)" for a later revision. Nothing in the handoff draws that picker,
 * so it is not invented here.
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
        // Two by two, not four across. The artboard's tiles are 194 dp on a 412 dp frame, which is
        // half the width, and that is what buys the labels their own words: "Einbuchen (Lager)"
        // says which Lager, "Boerse: Angebot" says an offer on what. Four across leaves about
        // 90 dp per tile, which is why they had been cut to "Einbuchen" and "Angebot" - a shortcut
        // whose label needs its icon to disambiguate it is not much of a shortcut.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
        ) {
            QuickAction.entries.chunked(2).forEach { pair ->
                // IntrinsicSize.Min so the pair share the taller tile's height. Without it the
                // shorter label's tile keeps its own smaller box and the row reads as two
                // different components.
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
 * One shortcut tile: the glyph **beside** its label, square, outlined.
 *
 * The artboard draws a flex row - a 22 dp orange glyph, then the label - on `Gray4` behind a
 * hairline `Gray3` outline, rather than a filled square with the glyph stacked over centred text.
 * The difference is not decoration: a row lets a long label wrap under itself and stay readable,
 * which is what makes the full wording fit at all.
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
            // No line cap. Foundations ch. 01: "Must survive font scale 1.3x without truncation —
            // never fix label widths (German compounds)." A cap of two lines held at 1.0x and cut
            // „CHECK-IN NÄCHSTER EI…" at 1.3x, which leaves a shortcut whose label no longer says
            // what it does. The tile grows instead; that is what heightIn(min=) means.
        )
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
        // The see-all action rides in the title's trailing slot, which is what that slot is for
        // („optional content pinned after the rule, e.g. a count or an action"). It hung under the
        // last card as a free-floating orange line, which reads as a row of the list rather than a
        // control belonging to the section. Artboard 1 shows the pattern on the inbox band.
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
    // Read so the date recomposes on a locale change, and recomputed rather than remembered so
    // "today" stops being today when the day rolls over with the app open.
    LocalConfiguration.current
    val zone = remember { ZoneId.systemDefault() }
    // Weekday spelled out, date numeric, which is the artboard's form. FormatStyle.FULL renders
    // the month as a word and is a rung longer than the line has room for beside the org unit.
    // The pattern is translatable so a locale can reorder the fields.
    val date = LocalDate.now(zone)
    val today =
        stringResource(
            R.string.dashboard_date,
            date.format(DateTimeFormatter.ofPattern(stringResource(R.string.dashboard_date_pattern))),
            date.year + SC_YEAR_OFFSET,
        )

    // The artboard puts the greeting in a filled block with the accent rail down its left edge,
    // not on the bare background — it is the chapter's first element and the only one that
    // addresses the member. Rendered as plain text it read as a caption above the announcement.
    KrtRailCard(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s12),
        contentPadding = PaddingValues(KrtSpacing.s12),
    ) {
        // Uppercase and orange, which is what artboard 1 draws and what the token artifact's
        // headline entries are annotated with. `headlineSmall` rather than a one-off style: the
        // mockup measures 20 sp at weight 900 with 1 sp of tracking, the scale has no such entry,
        // and headlineSmall (19/25/0.95, annotated "h3 - UPPERCASE") is the nearest token. A
        // hand-rolled TextStyle would match the mockup by a dp and leave the system by a rung.
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
 * Collapsing rather than truncating: an announcement is written to be read, and a notice cut off at
 * two lines with no way to see the rest is worse than none. The state is local and unsaved on
 * purpose — marking it read is a mutation and belongs to Phase 3, so the app must not pretend to
 * remember a decision it cannot store.
 *
 * @param text the announcement.
 */
@Composable
private fun AnnouncementBand(
    text: String,
    read: Boolean,
    onMarkRead: () -> Unit,
) {
    // Unread opens expanded. The whole reason a notice is marked unread is that the member has not
    // taken it in yet, and greeting them with three lines and an ellipsis asks them to work for it.
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
            // White and uppercase, as artboard 1 draws it. Orange would put the emphasis on the
            // word „Information" when the chip beside it is the thing worth noticing, and the
            // system reserves orange for the one thing on a screen a member should act on.
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
            // Its own tap target, not the card's: the card toggles the fold, and a member who
            // taps to read the rest of a notice must not thereby declare they have read it.
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
    // Design ch. 05 draws this as a hud-box with three rows: name and briefing beside the status,
    // then when and where, then the unit chip and the way in. It was a single line carrying the
    // name and the status badge — everything a member needs to decide whether to open it was
    // missing. See docs/DESIGN_PARITY_AUDIT.md.
    // A **hud-box**, brackets and all: the chapter draws this one card with them and nothing else
    // on the dashboard, which is what marks the Einsätze band as the thing the screen is for.
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
            // The row-level pill, not the page-level badge. The design system says which is
            // which in as many words -- the pill is "the row-level status indicator ... inside a
            // list the status must not compete with the record's name", the badge "belongs at the
            // top of a detail screen where a single status describes the whole record" -- and this
            // is a list. Artboard 1 draws the pill here too.
            KrtStatusPill(text = mission.missionStatusLabel(), tone = mission.missionStatusTone())
        }
        MissionFactsRow(mission = mission)
        // The rule the artboard puts above the footer: the unit and the way in are about the row
        // rather than about the Einsatz, and without it they read as a third fact.
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
    // Design ch. 05 draws the countdown FIRST and the clock time after it: "in 2 Std. · TS 21:00".
    // The relative half is what a member acts on — an absolute time alone makes them do the
    // subtraction, and they do it against the wrong timezone often enough to matter. The absolute
    // half stays because it is the one that survives being read out loud in TeamSpeak.
    //
    // Re-read once a minute so a countdown does not go stale while the dashboard sits open, which
    // is exactly what it does between the greeting and the first tap.
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
                // Same rule as the Einsatz list: once the relative half is itself a clock reading
                // („gestern, 21:14"), appending „· TS 21:14" prints the time twice.
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
 * How far ahead the Star Citizen calendar runs: its year 2956 is our 2026.
 *
 * The artboard dates the greeting „Sonntag, 17.08.2956" and the app prints both — the real date,
 * then the SC year in brackets (owner decision, 2026-08-26). Writing error copy in character is one
 * thing; a start screen that misstates today's date is another, and a member reading it beside a
 * calendar, Discord or the web tool would find three different years.
 */
private const val SC_YEAR_OFFSET = 930

/** How often the seven-day band re-reads its countdowns (design ch. 05: "each minute"). */
private const val COUNTDOWN_TICK_MS = 60_000L

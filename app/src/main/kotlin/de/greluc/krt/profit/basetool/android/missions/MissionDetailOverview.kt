/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtColor
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtFigure
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.carriesClock
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the lifecycle action, wherever it is drawn. */
const val MISSION_LIFECYCLE_TAG: String = "mission-lifecycle-action"

/**
 * The Verwaltung tab's outline button that starts or finishes the Einsatz — the only place the
 * lifecycle status can be advanced.
 *
 * Without the role it is drawn locked rather than hidden, and the toast names the missing role
 * (ADR-0011).
 *
 * @param lifecycle the Einsatz, the step on offer and where a refusal is announced.
 * @param enabled whether a write may run right now.
 * @param onAsk open the confirmation.
 */
@Composable
internal fun MissionLifecycleAction(
    lifecycle: MissionLifecycleUi,
    enabled: Boolean,
    onAsk: () -> Unit,
) {
    val detail = lifecycle.detail
    val next = lifecycle.next
    val denials = lifecycle.denials
    if (next == null) {
        return
    }
    val gate =
        Gate(
            allowed = detail.canManage,
            reason = stringResource(R.string.gate_role_mission_manager),
            detail = stringResource(R.string.gate_role_mission_manager_detail),
        )
    val (dim, click) = rememberGated(gate, onAsk, denials)
    KrtOutlineButton(
        text =
            stringResource(
                if (next == MissionStatus.ACTIVE) {
                    R.string.mission_lifecycle_start
                } else {
                    R.string.mission_lifecycle_complete
                },
            ),
        onClick = click,
        modifier = dim.fillMaxWidth().testTag(MISSION_LIFECYCLE_TAG),
        enabled = if (detail.canManage) enabled else true,
        iconRes =
            if (detail.canManage) {
                if (next == MissionStatus.ACTIVE) {
                    DesignR.drawable.ic_krt_login
                } else {
                    DesignR.drawable.ic_krt_check
                }
            } else {
                DesignR.drawable.ic_krt_lock
            },
    )
}

/**
 * The sticky head: title, status, org badge and the fact band.
 *
 * @param detail the Einsatz.
 */
@Composable
internal fun MissionDetailHead(detail: MissionDetail) {
    val zone = remember { ZoneId.systemDefault() }
    val time = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ProvideScreenTopBar(
            title = detail.name,
            subtitle = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    KrtStatusBadge(text = detail.statusLabel(), tone = detail.statusTone())
                    detail.orgUnitShorthand?.takeIf { it.isNotBlank() }?.let { KrtOrgBadge(text = it) }
                }
            },
        )
        val facts =
            run {
                buildList {
                    detail.meetingTime?.let {
                        add(stringResource(R.string.mission_detail_fact_meeting) to time.format(it))
                    }
                    detail.plannedStartTime?.let {
                        add(stringResource(R.string.mission_detail_fact_join) to time.format(it))
                    }
                    detail.meetingPoint?.takeIf { it.isNotBlank() }?.let {
                        add(stringResource(R.string.mission_detail_fact_place) to it)
                    }
                    detail.partyLeadName?.takeIf { it.isNotBlank() }?.let {
                        add(stringResource(R.string.mission_detail_fact_lead) to it)
                    }
                }
            }
        if (facts.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(KrtPalette.SurfaceInput)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s8),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                facts.forEach { (label, value) -> FactCell(label = label, value = value) }
            }
        }
        KrtHairlineRule()
    }
}

/**
 * One cell of the facts bar: an uppercase muted key and a bold white value on one line.
 *
 * @param label the key.
 * @param value the fact itself.
 */
@Composable
private fun FactCell(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FACT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = KrtPalette.White,
            maxLines = 1,
        )
    }
}

/**
 * Übersicht (design ch. 06 artboard 2): attendance, then the briefing card, then the description.
 *
 * @param detail the Einsatz.
 */
internal fun LazyListScope.overviewTab(detail: MissionDetail) {
    item(key = "attendance") { AttendanceBox(detail = detail) }
    item(key = "briefing") { BriefingCard(detail = detail) }
    item(key = "description-title") {
        KrtSectionTitle(text = stringResource(R.string.mission_detail_description))
    }
    item(key = "description") {
        Text(
            text = detail.description ?: stringResource(R.string.mission_detail_description_hidden),
            style = MaterialTheme.typography.bodyMedium,
            color = if (detail.description != null) KrtPalette.Gray1 else KrtPalette.TextMuted,
        )
    }
}

/**
 * How many are coming, how many are already there, and how long until it starts.
 *
 * @param detail the Einsatz.
 */
@Composable
private fun AttendanceBox(detail: MissionDetail) {
    val zone = remember { ZoneId.systemDefault() }
    val time = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }
    val start = detail.plannedStartTime ?: detail.meetingTime
    KrtHudBox(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = detail.registeredParticipants.toString(),
                        style = KrtFigure.total,
                        color = KrtPalette.White,
                    )
                    Text(
                        text = stringResource(R.string.mission_detail_registered).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = KrtPalette.TextMuted,
                    )
                }
                Text(
                    text =
                        buildAnnotatedString {
                            val count = detail.checkedInParticipants.toString()
                            val line = stringResource(R.string.mission_detail_checked_in_of, count)
                            append(line)
                            val at = line.indexOf(count)
                            if (at >= 0) {
                                addStyle(
                                    SpanStyle(color = KrtPalette.SuccessText),
                                    at,
                                    at + count.length,
                                )
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.Gray1,
                )
                AttendanceMeter(
                    registered = detail.registeredParticipants,
                    checkedIn = detail.checkedInParticipants,
                )
            }
            start?.let { at ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = at.relativeToNow(),
                        style = MaterialTheme.typography.titleSmall,
                        color = KrtPalette.White,
                    )
                    if (!at.carriesClock()) {
                        Text(
                            text = stringResource(R.string.mission_detail_start_at, time.format(at)),
                            style = MaterialTheme.typography.bodySmall,
                            color = KrtPalette.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The checked-in share as a square, flat bar; zero sign-ups draw an empty track.
 *
 * @param registered how many signed up.
 * @param checkedIn how many of them are already there.
 */
@Composable
private fun AttendanceMeter(
    registered: Int,
    checkedIn: Int,
) {
    val share = if (registered > 0) checkedIn.toFloat() / registered else 0f
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = KrtSpacing.s4)
                .height(METER_HEIGHT)
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, KrtPalette.Gray3),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(METER_HEIGHT)
                    .background(KrtPalette.Success),
        )
    }
}

/**
 * „Einsatz auf einen Blick" — Ziel, Teamspeak, Serverjoin, Treffpunkt, Dauer and Einsatzleiter.
 *
 * Dauer is computed from end and Teamspeak time; rows the server left empty are omitted.
 *
 * @param detail the Einsatz.
 */
@Composable
private fun BriefingCard(detail: MissionDetail) {
    val zone = remember { ZoneId.systemDefault() }
    val time = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }
    val rows =
        buildList {
            detail.objectives.firstOrNull()?.title?.takeIf { it.isNotBlank() }?.let {
                add(stringResource(R.string.mission_detail_brief_goal) to it)
            }
            detail.meetingTime?.let {
                add(stringResource(R.string.mission_detail_brief_meeting) to time.format(it))
            }
            detail.plannedStartTime?.let {
                add(stringResource(R.string.mission_detail_brief_join) to time.format(it))
            }
            detail.meetingPoint?.takeIf { it.isNotBlank() }?.let {
                add(stringResource(R.string.mission_detail_brief_place) to it)
            }
            durationLabel(detail, time)?.let { add(stringResource(R.string.mission_detail_brief_duration) to it) }
            detail.partyLeadName?.takeIf { it.isNotBlank() }?.let {
                add(stringResource(R.string.mission_detail_brief_lead) to it)
            }
        }
    if (rows.isEmpty()) {
        return
    }
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
        Text(
            text = stringResource(R.string.mission_detail_at_a_glance),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.TextMuted,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.s16, vertical = KrtSpacing.s12),
        )
        KrtHairlineRule()
        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = BRIEF_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                    modifier = Modifier.padding(start = KrtSpacing.s16).width(BRIEF_LABEL_WIDTH),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f).padding(end = KrtSpacing.s16),
                )
            }
            if (index != rows.lastIndex) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(KrtSpacing.hairline)
                            .background(KrtPalette.SurfaceInput),
                )
            }
        }
    }
}

/**
 * How long the Einsatz is planned to run, and when it ends.
 *
 * @param detail the Einsatz.
 * @param time formats the end time in the device's zone.
 * @return e.g. "~3 Std. (Ende 00:00)", or `null` when the server gave no end.
 */
@Composable
private fun durationLabel(
    detail: MissionDetail,
    time: DateTimeFormatter,
): String? {
    val end = detail.plannedEndTime ?: return null
    val from = detail.meetingTime ?: detail.plannedStartTime
    val hours = from?.let { java.time.Duration.between(it, end).toMinutes() }?.takeIf { it > 0 }
    return if (hours == null) {
        stringResource(R.string.mission_detail_brief_end_only, time.format(end))
    } else {
        stringResource(
            R.string.mission_detail_brief_duration_value,
            hours / MINUTES_PER_HOUR,
            time.format(end),
        )
    }
}

/** Gap between a fact's key and its value in the facts bar. */
private val FACT_GAP = 5.dp

/** Height of the attendance meter — 8 px in `.attendance-meter`. */
private val METER_HEIGHT = 8.dp

/** Minutes in an hour, for the briefing card's duration. */
private const val MINUTES_PER_HOUR = 60

/** Width of the briefing card's label column, so its six values start on one line. */
private val BRIEF_LABEL_WIDTH = 104.dp

/** Vertical padding of a briefing row — 9 px per the chapter's `dt`/`dd`. */
private val BRIEF_ROW_PADDING = 9.dp

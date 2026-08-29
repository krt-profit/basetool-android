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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.relativeToNow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        // The name and its status live in the TOP BAR on a detail (design ch. 06 artboard 2), not
        // in the content. Drawing them here as well repeated the Einsatz twice: once as a category
        // in the bar and once as a fact under it.
        ProvideScreenTopBar(
            title = detail.name,
            subtitle = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    KrtStatusBadge(text = detail.statusLabel(), tone = detail.statusTone())
                    detail.orgUnitShorthand?.takeIf { it.isNotBlank() }?.let { KrtOrgBadge(text = it) }
                }
            },
        )
        // Design ch. 06 artboard 2 / `.facts-bar`: one strip of KEY value pairs — TS, Join, Ort,
        // Leiter — key and value on the SAME line. It was a stacked key/value list, which cost four
        // lines of a head meant to stay out of the content's way, and which put "Ende" where the
        // chapter puts the Einsatzleiter. Ende is in the briefing card below, inside "Dauer".
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
            // `.facts-bar`: its own band on the input surface with a hairline under it, 8/16 padding
            // and a 16 dp gap — not a transparent row inside the title block.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(KrtPalette.SurfaceInput)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                facts.forEach { (label, value) -> FactCell(label = label, value = value) }
            }
        }
        KrtHairlineRule()
    }
}

/**
 * One cell of the facts bar: `KEY value`, side by side.
 *
 * `.fact-k` is Gray 2, uppercase, 10 sp with wide tracking; `.fact-v` is white and bold. They sit
 * on one line — stacking them doubles the bar's height and turns a strip into a table.
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
 * Übersicht, as design chapter 06 artboard 2 draws it: attendance, then the briefing card, then the
 * description.
 *
 * The order is the chapter's and it is the order a member reads in — how many are coming and when
 * it starts, then the six facts, then the prose. It used to be the prose alone.
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
        // An outsider read carries no description (ADR-0034). Saying so beats a blank section,
        // which reads as an Einsatz nobody bothered to describe.
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
 * The count is the largest thing on the screen because it is the one number a member checks before
 * deciding anything else. The meter under it is the checked-in share — a proportion is read faster
 * from a bar than from two numbers, and the two numbers are there anyway for the exact reading.
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
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = detail.registeredParticipants.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = KrtPalette.White,
                    )
                    Text(
                        // `.att-label`: uppercase, Gray 2 — a caption for the number, not a word
                        // in a sentence.
                        text = stringResource(R.string.mission_detail_registered).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = KrtPalette.TextMuted,
                    )
                }
                Text(
                    // `.attendance-sub b` puts the checked-in count in success green: the two
                    // numbers mean different things and the meter below is green for the same one.
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

/**
 * The checked-in share as a bar.
 *
 * Square and flat, like every other meter in this design system. An Einsatz nobody has signed up
 * for draws an empty track rather than a full one — zero of zero is not "everybody is here".
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
                .padding(top = KrtSpacing.xs)
                .height(METER_HEIGHT)
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, KrtPalette.Gray3),
    ) {
        Box(
            // Green, not orange. `.attendance-meter i` is success green and the CSS says why:
            // orange is not spent here, it stays on the Anmelden CTA beside it.
            modifier =
                Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(METER_HEIGHT)
                    .background(KrtPalette.Success),
        )
    }
}

/**
 * „Einsatz auf einen Blick" — the six facts the chapter puts above the prose.
 *
 * Ziel, Teamspeak, Serverjoin, Treffpunkt, Dauer, Einsatzleiter. A member scanning for one of them
 * should not have to read a paragraph to find it, which is the whole reason the chapter separates
 * this card from the Beschreibung. **Dauer is computed** (`Ende − Teamspeak`), because the server
 * sends the two timestamps and not the span between them.
 *
 * Rows the server left empty are dropped rather than drawn with a dash: an empty row states
 * nothing and costs a line.
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
                add(stringResource(R.string.mission_detail_fact_meeting) to time.format(it))
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
    // `card--flush`: the heading sits in its own band with a border under it, and each row is a
    // dt/dd pair separated by a hairline. A KrtKeyValueRow list has neither, so the six facts ran
    // together into a block a reader has to parse instead of scan.
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
        Text(
            text = stringResource(R.string.mission_detail_at_a_glance),
            style = MaterialTheme.typography.titleSmall,
            color = KrtPalette.TextMuted,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.md),
        )
        KrtHairlineRule()
        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = BRIEF_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.TextMuted,
                    modifier = Modifier.padding(start = KrtSpacing.lg).width(BRIEF_LABEL_WIDTH),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f).padding(end = KrtSpacing.lg),
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
 * @return e.g. "~3 Std. (Ende 00:00)", or `null` when the server gave no end — a duration invented
 *   from one timestamp would be a guess presented as a plan.
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

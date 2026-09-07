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
 * Starting the Einsatz, and finishing it: one action, in the Verwaltung tab.
 *
 * Design ch. 06 (F2) put this on the status badge — one surface, „no form field, no overflow entry,
 * no second place" — and the badge grew into a tinted band carrying the state, the head count and
 * this button, drawn on every visit to the screen. **The owner moved it here on 2026-09-07.** The
 * status is a chip in the head now (where artboard 2 always drew it); what is left is the action,
 * and it belongs with the other things only a manager may do rather than in front of the fifteen
 * members who cannot press it.
 *
 * The rule F2 was protecting still holds: this is the ONE place the lifecycle can be advanced. The
 * Verwaltung form's „Einsatz beenden" is a different write — it stamps the actual END TIME in the
 * schedule section and does not move the status — and the two sit in the same tab now, which is
 * where the difference is easiest to read.
 *
 * An **outline** button, not a filled one. The single filled orange on this screen belongs to
 * „Anmelden"; two of them would be the mistake the action hierarchy exists to prevent.
 *
 * Without the role it is **drawn locked** rather than hidden: it keeps its target, wears the lock,
 * and the toast names the role that is missing (ADR-0011).
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
        // A locked control keeps its target so it can explain itself; only a genuinely busy screen
        // disables it. Disabling a refused control is the thing the drawn-not-hidden rule avoids.
        enabled = if (detail.canManage) enabled else true,
        iconRes =
            if (detail.canManage) {
                // The enter arrow for starting, the tick for finishing — the same two the rest of
                // the app uses for those.
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
        // The name and its status live in the TOP BAR on a detail (design ch. 06 artboard 2), not
        // in the content. Drawing them here as well repeated the Einsatz twice: once as a category
        // in the bar and once as a fact under it.
        ProvideScreenTopBar(
            title = detail.name,
            subtitle = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    // The status badge is HERE now, beside the org badge — which is what
                    // artboard 2 draws and what this head omitted only because the lifecycle band
                    // below it carried the word instead. The band is gone (owner decision,
                    // 2026-09-07): a tinted box holding one word, one count and one button stood
                    // permanently between the head and the tabs on every visit, for an action a
                    // manager takes twice in the life of an Einsatz.
                    //
                    // `KrtStatusBadge` rather than the quiet `KrtStatusPill` the list rows use: the
                    // pill exists so ten rows do not each shout, and the badge is reserved for „the
                    // one status that describes a whole screen". This is that one.
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
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        // The attendance count is the band's hero figure — `KrtFigure.total`, the
                        // ladder numbers get since round 15, not the h1 heading rung.
                        text = detail.registeredParticipants.toString(),
                        style = KrtFigure.total,
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
                    // „Start 20:44" under „25.08., 20:44" prints the same clock reading twice.
                    // Once the distance to the start is itself a date-and-time — which is what it
                    // becomes as soon as the Einsatz is running — the absolute half has nothing
                    // left to add, exactly as in the Einsatz list.
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
                .padding(top = KrtSpacing.s4)
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
                // „Teamspeak", not the facts bar's „TS": the bar abbreviates because it has four
                // facts across 411 dp, and this table does not. Artboard 06-2 writes both words.
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

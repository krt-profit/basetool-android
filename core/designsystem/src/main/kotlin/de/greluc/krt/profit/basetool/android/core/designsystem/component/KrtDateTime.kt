/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle

/** How a date is written and read everywhere in the app: `TT.MM.JJJJ`. */
val KRT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** How a time is written and read everywhere in the app: `HH:MM`, 24 hours. */
val KRT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Test handle of the date half of a [KrtDateTimeField]. */
const val KRT_DATE_FIELD_TAG: String = "krt-date-field"

/** Test handle of the time half of a [KrtDateTimeField]. */
const val KRT_TIME_FIELD_TAG: String = "krt-time-field"

/** Seven columns, six rows — the fixed grid every month is drawn into. */
private const val GRID_CELLS = 42

/** A week's worth of columns. */
private const val WEEK_LENGTH = 7

/** Minutes move in fives; the artboard's own reason is that Einsätze never start at 19:23. */
private const val MINUTE_STEP = 5

/** Hours in a day, for the stepper's wrap-around. */
private const val HOURS = 24

/** Minutes in an hour, for the stepper's wrap-around. */
private const val MINUTES = 60

/** How long a press must be held before it starts repeating. */
private const val HOLD_DELAY_MS = 400L

/** The interval between repeats at the start of a hold. */
private const val HOLD_REPEAT_MS = 120L

/** How much each repeat shortens the next interval — this is the acceleration. */
private const val HOLD_ACCEL_MS = 12L

/** The fastest a hold may repeat. */
private const val HOLD_FASTEST_MS = 32L

/** Edge length of one day cell. */
private val DAY_CELL = KrtSpacing.touchTarget

/** Width of a stepper's value box. */
private val STEPPER_VALUE = 56.dp

/** How strongly the days between the two ends of a range are tinted. */
private const val RANGE_TINT_ALPHA = 0.16f

/** A disabled pair dims rather than recolouring, exactly as a disabled field does. */
private const val DISABLED_PAIR_ALPHA = 0.38f

/**
 * The neighbouring month's days, `#464646` — design ch. 02 §11.
 *
 * Held here rather than in `KrtPalette`: the foundations palette carries four greys and this is a
 * fifth, named by one artboard for one purpose. It is on the design gap list; until the palette
 * answers, the value lives at the only place that uses it rather than being approximated by Gray2
 * (too bright — a neighbour would read as selectable content) or Gray3 (too dark to read at all).
 */
private val NEIGHBOUR_MONTH = Color(0xFF464646)

/**
 * Reads a date the member sees back into a date the code can use.
 *
 * @receiver the display value, `TT.MM.JJJJ`, or anything half-typed.
 * @return the date, or `null` when the text is not a complete one.
 */
fun String.krtToLocalDate(): LocalDate? =
    try {
        LocalDate.parse(trim(), KRT_DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * Reads a time the member sees back into a time the code can use.
 *
 * @receiver the display value, `HH:MM`.
 * @return the time, or `null` when the text is not a complete one.
 */
fun String.krtToLocalTime(): LocalTime? =
    try {
        LocalTime.parse(trim(), KRT_TIME_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * One point in time as a **date and a time**, picked — never typed.
 *
 * Design ch. 02 §11. Both halves are targets, not inputs: the date opens a month grid, the time
 * opens two steppers. The member never types a timestamp, which is what made the Einsatz's
 * schedule read as paperwork, and never sees an ISO string — display is German throughout while
 * the wire value stays ISO-8601 at the repository seam.
 *
 * The date takes 1.35 fr with a calendar glyph and sits left; the time takes 1 fr with a clock
 * glyph and sits centred. Empty means empty: the placeholders say what is wanted rather than
 * pre-filling today, because a pre-filled today is saved by accident.
 *
 * A moment in the past is **named, not blocked** — the server decides whether it is legal.
 *
 * @param label what the pair means, drawn once above both halves.
 * @param date the date half in display form, `TT.MM.JJJJ`, or blank.
 * @param time the time half in display form, `HH:MM`, or blank.
 * @param onDate the member picked a date; the value is already formatted.
 * @param onTime the member picked a time; the value is already formatted.
 * @param modifier layout modifier.
 * @param enabled whether either half opens its picker.
 * @param warnPast whether a moment already gone is called out; off for fields that record
 *   something that happened, where the past is the point.
 * @param now the one clock the whole pair reads: what „past" is measured against, which day the
 *   grid marks, and what „Jetzt" means. Injectable so a test does not depend on the wall clock —
 *   and shared, so the two modals can never disagree about what today is.
 */
@Composable
fun KrtDateTimeField(
    label: String,
    date: String,
    time: String,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    warnPast: Boolean = true,
    now: LocalDateTime = LocalDateTime.now(),
) {
    var picking by remember { mutableStateOf(Picking.NONE) }
    val pickedDate = date.krtToLocalDate()
    val pickedTime = time.krtToLocalTime()

    Column(modifier = modifier.fillMaxWidth()) {
        KrtFieldLabel(text = label, enabled = enabled)
        Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            ValueBox(
                weight = DATE_WEIGHT,
                icon = R.drawable.ic_krt_calendar,
                value = date,
                placeholder = stringResource(R.string.krt_date_placeholder),
                description = stringResource(R.string.krt_pick_date_for, label),
                align = TextAlign.Start,
                enabled = enabled,
                tag = KRT_DATE_FIELD_TAG,
                onClick = { picking = Picking.DATE },
            )
            ValueBox(
                weight = 1f,
                icon = R.drawable.ic_krt_clock,
                value = time,
                placeholder = stringResource(R.string.krt_time_placeholder),
                description = stringResource(R.string.krt_pick_time_for, label),
                align = TextAlign.Center,
                enabled = enabled,
                tag = KRT_TIME_FIELD_TAG,
                onClick = { picking = Picking.TIME },
            )
        }
        if (warnPast && isPast(pickedDate, pickedTime, now)) {
            KrtFieldWarning(text = stringResource(R.string.krt_in_the_past))
        }
    }

    when (picking) {
        Picking.DATE -> {
            KrtDatePickerModal(
                initial = pickedDate,
                onPick = {
                    picking = Picking.NONE
                    onDate(it.format(KRT_DATE_FORMAT))
                },
                onDismiss = { picking = Picking.NONE },
                today = now.toLocalDate(),
            )
        }

        Picking.TIME -> {
            KrtTimePickerModal(
                initial = pickedTime,
                onPick = {
                    picking = Picking.NONE
                    onTime(it.format(KRT_TIME_FORMAT))
                },
                onDismiss = { picking = Picking.NONE },
                now = now.toLocalTime(),
            )
        }

        Picking.NONE -> {}
    }
}

/**
 * Whether a half-set pair already lies behind [now].
 *
 * A pair missing either half is not a moment yet, so it is not judged — warning while somebody is
 * still filling the second field would fire on every schedule that gets a date before a time.
 *
 * @param date the date half, or `null`.
 * @param time the time half, or `null`.
 * @param now the clock to measure against.
 * @return `true` only when both halves are set and together they are already gone.
 */
private fun isPast(
    date: LocalDate?,
    time: LocalTime?,
    now: LocalDateTime,
): Boolean {
    if (date == null || time == null) {
        return false
    }
    return LocalDateTime.of(date, time).isBefore(now)
}

/**
 * A date on its own — a due date, a deadline, a cut-off.
 *
 * The same target as the pair's left half, without a time beside it. Used where the domain has a
 * day but no hour: an Auftrag is due *on* a day.
 *
 * @param label what the date means.
 * @param date the value in display form, `TT.MM.JJJJ`, or blank.
 * @param onDate the member picked one; the value is already formatted.
 * @param modifier layout modifier.
 * @param enabled whether the picker opens.
 * @param today which day carries the hairline marker; injectable for tests.
 */
@Composable
fun KrtDateField(
    label: String,
    date: String,
    onDate: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    today: LocalDate = LocalDate.now(),
) {
    var picking by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        KrtFieldLabel(text = label, enabled = enabled)
        Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        Row {
            ValueBox(
                weight = 1f,
                icon = R.drawable.ic_krt_calendar,
                value = date,
                placeholder = stringResource(R.string.krt_date_placeholder),
                description = stringResource(R.string.krt_pick_date_for, label),
                align = TextAlign.Start,
                enabled = enabled,
                tag = KRT_DATE_FIELD_TAG,
                onClick = { picking = true },
            )
        }
    }

    if (picking) {
        KrtDatePickerModal(
            initial = date.krtToLocalDate(),
            onPick = {
                picking = false
                onDate(it.format(KRT_DATE_FORMAT))
            },
            onDismiss = { picking = false },
            today = today,
        )
    }
}

/**
 * Pick a day out of a month grid.
 *
 * Seven columns, 44 dp cells, the week starts Monday. The selection is a filled orange cell with
 * **black** text — the system's rule that filled orange carries black. Today is marked with a
 * hairline and no fill, so two oranges never compete. Neighbouring months are dimmed but tappable
 * and page the grid on.
 *
 * Deliberately **not** a Material3 `DatePicker`: its rounding, tonal surfaces and elevation break
 * three system rules at once.
 *
 * @param initial the day to open on and pre-select, or `null` to open on [today].
 * @param onPick the member confirmed a day.
 * @param onDismiss the modal was cancelled or dismissed.
 * @param modifier layout modifier.
 * @param title the modal's heading.
 * @param today which day carries the hairline marker; injectable for tests.
 */
@Composable
fun KrtDatePickerModal(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.krt_date_pick_title),
    today: LocalDate = LocalDate.now(),
) {
    var month by remember { mutableStateOf(YearMonth.from(initial ?: today)) }
    var picked by remember { mutableStateOf(initial) }
    val choose: (LocalDate) -> Unit = { day ->
        picked = day
        month = YearMonth.from(day)
    }

    KrtModal(
        title = title,
        confirmText = stringResource(R.string.krt_apply),
        onConfirm = { picked?.let(onPick) ?: onDismiss() },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        MonthHeader(
            month = month,
            onPrevious = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
        )
        WeekdayHeader()
        MonthGrid(month = month, today = today, onPick = choose) { day ->
            if (day == picked) DayTone.SELECTED else DayTone.PLAIN
        }
        DayShortcuts(today = today, onPick = choose)
    }
}

/**
 * Pick a period — the Einsatz list's date-range filter (design ch. 02 §11 d, spec §C7).
 *
 * Two head fields carry the ends; the active one wears the orange frame, a tap in the grid fills
 * it and hands the turn to the other. The days between the ends are tinted, the ends themselves
 * filled. One end alone is a legal, open range: `MissionQuery` has carried `from`/`until` and the
 * repository has sent them all along — only the picker was missing.
 *
 * Resetting is the filter chip's own ✕, not a fourth button in here.
 *
 * @param from the start of the period, or `null`.
 * @param until the end of the period, or `null`.
 * @param onPick the member confirmed; either end may be `null`.
 * @param onDismiss the modal was cancelled or dismissed.
 * @param modifier layout modifier.
 * @param today which day carries the hairline marker; injectable for tests.
 */
@Composable
fun KrtDateRangePickerModal(
    from: LocalDate?,
    until: LocalDate?,
    onPick: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var range by remember { mutableStateOf(DateRange(from, until)) }
    var editingEnd by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.from(from ?: today)) }

    KrtModal(
        title = stringResource(R.string.krt_range_pick_title),
        confirmText = stringResource(R.string.krt_filter_action),
        onConfirm = { onPick(range.from, range.until) },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        RangeHead(range = range, editingEnd = editingEnd, onEdit = { editingEnd = it })
        MonthHeader(
            month = month,
            onPrevious = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
        )
        WeekdayHeader()
        MonthGrid(
            month = month,
            today = today,
            onPick = { day ->
                range = range.with(day, editingEnd)
                editingEnd = !editingEnd
            },
            tone = { day -> range.toneOf(day) },
        )
        RangeShortcuts(today = today) {
            range = it
            month = YearMonth.from(it.from ?: today)
        }
    }
}

/**
 * Pick a time with two steppers.
 *
 * A scroll wheel has no hairline frame, no fixed height and no safe target, so the HUD vocabulary
 * has no shape for one. Arrows are full 44 dp targets, the value carries the orange frame and can
 * also be typed — the stepper is the fast way in, not the only one. Minutes move in fives and a
 * held arrow accelerates.
 *
 * @param initial the time to open on, or `null` to open on [now] rounded down to the step.
 * @param onPick the member confirmed a time.
 * @param onDismiss the modal was cancelled or dismissed.
 * @param modifier layout modifier.
 * @param now what „Jetzt" means; injectable so a test does not depend on the clock.
 */
@Composable
fun KrtTimePickerModal(
    initial: LocalTime?,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalTime = LocalTime.now(),
) {
    var value by remember { mutableStateOf(initial ?: now.truncatedToStep()) }

    KrtModal(
        title = stringResource(R.string.krt_time_pick_title),
        confirmText = stringResource(R.string.krt_apply),
        onConfirm = { onPick(value) },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperColumn(
                label = stringResource(R.string.krt_hour),
                value = value.hour,
                upLabel = stringResource(R.string.krt_hour_up),
                downLabel = stringResource(R.string.krt_hour_down),
                onChange = { value = value.withHour(it.floorMod(HOURS)) },
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.titleLarge,
                color = KrtPalette.Gray1,
            )
            StepperColumn(
                label = stringResource(R.string.krt_minute),
                value = value.minute,
                step = MINUTE_STEP,
                upLabel = stringResource(R.string.krt_minute_up),
                downLabel = stringResource(R.string.krt_minute_down),
                onChange = { value = value.withMinute(it.floorMod(MINUTES)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = KrtSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            KrtFilterChip(
                text = stringResource(R.string.krt_full_hour),
                selected = false,
                onClick = { value = value.withMinute(0) },
            )
            KrtFilterChip(
                text = stringResource(R.string.krt_half_hour),
                selected = false,
                onClick = { value = value.withMinute(HALF_HOUR) },
            )
            KrtFilterChip(
                text = stringResource(R.string.krt_now),
                selected = false,
                onClick = { value = now.truncatedToStep() },
            )
        }
    }
}

/** Half an hour, the second of the three time shortcuts. */
private const val HALF_HOUR = 30

/** The date half's share of the pair — 1.35 fr against the time's 1 fr. */
private const val DATE_WEIGHT = 1.35f

/** Which picker a [KrtDateTimeField] currently has open. */
private enum class Picking {
    /** Neither; the field is at rest. */
    NONE,

    /** The month grid. */
    DATE,

    /** The two steppers. */
    TIME,
}

/** How one day cell is drawn. */
private enum class DayTone {
    /** Nothing special — the month's own day. */
    PLAIN,

    /** An end of the selection: filled orange, black text. */
    SELECTED,

    /** Between the two ends of a range: tinted, ordinary text. */
    WITHIN,
}

/**
 * The two ends of a period, either of which may be missing.
 *
 * @property from the first day, or `null` for an open start.
 * @property until the last day, or `null` for an open end.
 */
private data class DateRange(
    val from: LocalDate?,
    val until: LocalDate?,
) {
    /**
     * Puts a day into one end, keeping the pair in order.
     *
     * A member who picks the end before the start has expressed a period, not a mistake, so the
     * ends are swapped rather than refused.
     *
     * @param day the day tapped.
     * @param end whether the tap fills the second end.
     * @return the new range.
     */
    fun with(
        day: LocalDate,
        end: Boolean,
    ): DateRange {
        val next = if (end) copy(until = day) else copy(from = day)
        val a = next.from
        val b = next.until
        return if (a != null && b != null && b.isBefore(a)) DateRange(b, a) else next
    }

    /**
     * How a day in the grid is drawn against this range.
     *
     * @param day the day being drawn.
     * @return its tone.
     */
    fun toneOf(day: LocalDate): DayTone {
        val a = from
        val b = until
        return when {
            day == a || day == b -> DayTone.SELECTED
            a != null && b != null && day.isAfter(a) && day.isBefore(b) -> DayTone.WITHIN
            else -> DayTone.PLAIN
        }
    }
}

/**
 * Rounds a time down onto the minute step.
 *
 * „Jetzt" on a five-minute stepper must land on a value the stepper can reach; 19:23 cannot be
 * stepped away from without first hitting 19:25.
 *
 * @receiver the time to round.
 * @return the same hour, minutes floored to the step.
 */
private fun LocalTime.truncatedToStep(): LocalTime = withMinute(minute / MINUTE_STEP * MINUTE_STEP)

/**
 * A modulo that is never negative, so stepping below zero wraps to the top of the range.
 *
 * @receiver the raw value, possibly negative.
 * @param bound the exclusive upper bound.
 * @return the wrapped value.
 */
private fun Int.floorMod(bound: Int): Int = ((this % bound) + bound) % bound

/**
 * One half of the pair: a target that shows a value or says what it wants.
 *
 * @param weight the half's share of the row.
 * @param icon the leading glyph — calendar or clock.
 * @param value the formatted value, or blank.
 * @param placeholder what to say while it is blank.
 * @param description what a screen reader announces; the visible label names the pair, not the
 *   half, so each half has to say which picker it opens.
 * @param align which edge the value sits against.
 * @param enabled whether the target opens its picker.
 * @param tag the test handle.
 * @param onClick open the picker.
 */
@Composable
private fun RowScope.ValueBox(
    weight: Float,
    @DrawableRes icon: Int,
    value: String,
    placeholder: String,
    description: String,
    align: TextAlign,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .weight(weight)
                .alpha(if (enabled) 1f else DISABLED_PAIR_ALPHA)
                .background(KrtPalette.SurfaceInput)
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .height(KrtSpacing.field)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = KrtSpacing.md)
                .testTag(tag)
                .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtIcon(
            id = icon,
            contentDescription = null,
            size = ICON_IN_FIELD,
            tint = KrtPalette.TextMuted,
        )
        Text(
            text = value.ifEmpty { placeholder },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isEmpty()) KrtPalette.TextMuted else KrtPalette.Gray1,
            textAlign = align,
            maxLines = 1,
        )
    }
}

/** The glyph size inside a field, per the icon canon. */
private val ICON_IN_FIELD = 16.dp

/**
 * The month being shown, with a chevron on either side.
 *
 * No month or year dropdown and no year carousel: the chevrons plus the three shortcut chips
 * covered nearly everything in practice, and a dropdown inside a modal is a second overlay.
 *
 * @param month what is on screen.
 * @param onPrevious page back.
 * @param onNext page on.
 */
@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtIconButton(
            iconRes = R.drawable.ic_krt_chevron_left,
            label = stringResource(R.string.krt_previous_month),
            onClick = onPrevious,
        )
        // LocalLocale, not Locale.getDefault(): the latter is not observable state, so the month
        // name would keep the locale the modal first composed under.
        val locale = LocalLocale.current.platformLocale
        Text(
            text =
                month.format(
                    remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) },
                ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = KrtPalette.White,
            textAlign = TextAlign.Center,
        )
        KrtIconButton(
            iconRes = R.drawable.ic_krt_chevron_right,
            label = stringResource(R.string.krt_following_month),
            onClick = onNext,
        )
    }
}

/** The seven column heads, starting Monday as the German week does. */
@Composable
private fun WeekdayHeader() {
    val locale = LocalLocale.current.platformLocale
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                modifier = Modifier.width(DAY_CELL),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Six weeks of day cells.
 *
 * The grid is a fixed 6×7 so it never changes height between months — a modal that grows by a row
 * when the member pages moves the buttons under their thumb.
 *
 * @param month the month in the middle of the grid.
 * @param today which day carries the hairline marker.
 * @param onPick a day was tapped.
 * @param tone how each day is drawn.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    tone: (LocalDate) -> DayTone,
) {
    val days = remember(month) { gridDays(month) }
    Column(modifier = Modifier.fillMaxWidth()) {
        days.chunked(WEEK_LENGTH).forEach { week ->
            Row {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        tone = tone(day),
                        outside = YearMonth.from(day) != month,
                        marked = day == today,
                        onClick = { onPick(day) },
                    )
                }
            }
        }
    }
}

/**
 * One day.
 *
 * @param day the date it stands for.
 * @param tone how it is drawn against the selection.
 * @param outside whether it belongs to a neighbouring month.
 * @param marked whether it is today.
 * @param onClick pick it.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    tone: DayTone,
    outside: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val fill =
        when (tone) {
            DayTone.SELECTED -> primary
            DayTone.WITHIN -> primary.copy(alpha = RANGE_TINT_ALPHA)
            DayTone.PLAIN -> Color.Transparent
        }
    val ink =
        when {
            tone == DayTone.SELECTED -> KrtPalette.Black
            outside -> NEIGHBOUR_MONTH
            else -> KrtPalette.Gray1
        }
    Box(
        modifier =
            Modifier
                .size(DAY_CELL)
                .background(fill)
                .then(
                    if (marked && tone != DayTone.SELECTED) {
                        Modifier.border(KrtSpacing.hairline, primary)
                    } else {
                        Modifier
                    },
                )
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
        )
    }
}

/**
 * The three day shortcuts of the single-date picker.
 *
 * @param today what „Heute" means.
 * @param onPick a shortcut was tapped.
 */
@Composable
private fun DayShortcuts(
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        KrtFilterChip(
            text = stringResource(R.string.krt_today),
            selected = false,
            onClick = { onPick(today) },
        )
        KrtFilterChip(
            text = stringResource(R.string.krt_tomorrow),
            selected = false,
            onClick = { onPick(today.plusDays(1)) },
        )
        KrtFilterChip(
            text = stringResource(R.string.krt_next_friday),
            selected = false,
            onClick = { onPick(today.nextFriday()) },
        )
    }
}

/**
 * The three period shortcuts of the range picker.
 *
 * @param today the day the periods are measured from.
 * @param onPick a shortcut was tapped.
 */
@Composable
private fun RangeShortcuts(
    today: LocalDate,
    onPick: (DateRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KrtSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        KrtFilterChip(
            text = stringResource(R.string.krt_this_week),
            selected = false,
            onClick = { onPick(DateRange(weekStart, weekStart.plusDays((WEEK_LENGTH - 1).toLong()))) },
        )
        KrtFilterChip(
            text = stringResource(R.string.krt_next_seven_days),
            selected = false,
            onClick = { onPick(DateRange(today, today.plusDays(WEEK_LENGTH.toLong()))) },
        )
        KrtFilterChip(
            text = stringResource(R.string.krt_this_month),
            selected = false,
            onClick = {
                val month = YearMonth.from(today)
                onPick(DateRange(month.atDay(1), month.atEndOfMonth()))
            },
        )
    }
}

/**
 * The two head fields of the range picker; the active one wears the orange frame.
 *
 * @param range the ends as they stand.
 * @param editingEnd whether the second end has the turn.
 * @param onEdit hand the turn to one end.
 */
@Composable
private fun RangeHead(
    range: DateRange,
    editingEnd: Boolean,
    onEdit: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = KrtSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        RangeEnd(
            label = stringResource(R.string.krt_range_from),
            day = range.from,
            active = !editingEnd,
            onClick = { onEdit(false) },
        )
        RangeEnd(
            label = stringResource(R.string.krt_range_until),
            day = range.until,
            active = editingEnd,
            onClick = { onEdit(true) },
        )
    }
}

/**
 * One end of the range as a small labelled field.
 *
 * @param label „Von" or „Bis".
 * @param day the day it holds, or `null`.
 * @param active whether it has the turn.
 * @param onClick give it the turn.
 */
@Composable
private fun RowScope.RangeEnd(
    label: String,
    day: LocalDate?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        KrtFieldLabel(text = label)
        Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(KrtPalette.SurfaceInput)
                    .border(
                        KrtSpacing.hairline,
                        if (active) MaterialTheme.colorScheme.primary else KrtPalette.Gray3,
                    )
                    .height(KrtSpacing.field)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = KrtSpacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = day?.format(KRT_DATE_FORMAT) ?: stringResource(R.string.krt_date_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = if (day == null) KrtPalette.TextMuted else KrtPalette.Gray1,
                maxLines = 1,
            )
        }
    }
}

/**
 * One stepper: an arrow, the framed value, an arrow.
 *
 * @param label „Stunde" or „Minute".
 * @param value the number it currently holds.
 * @param upLabel what a screen reader calls the upper arrow.
 * @param downLabel what a screen reader calls the lower arrow.
 * @param onChange the raw new value, which may be out of range and is wrapped by the caller.
 * @param step how far one press moves.
 */
@Composable
private fun StepperColumn(
    label: String,
    value: Int,
    upLabel: String,
    downLabel: String,
    onChange: (Int) -> Unit,
    step: Int = 1,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KrtFieldLabel(text = label)
        Box(modifier = Modifier.padding(top = KrtSpacing.xs))
        StepperArrow(
            icon = R.drawable.ic_krt_chevron_up,
            label = upLabel,
            onStep = { onChange(value + step) },
        )
        Box(
            modifier =
                Modifier
                    .width(STEPPER_VALUE)
                    .height(KrtSpacing.field)
                    .background(KrtPalette.SurfaceInput)
                    .border(KrtSpacing.hairline, MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
            )
        }
        StepperArrow(
            icon = R.drawable.ic_krt_chevron_down,
            label = downLabel,
            onStep = { onChange(value - step) },
        )
    }
}

/**
 * An arrow that repeats while it is held, accelerating as it goes.
 *
 * A tap steps once through the ordinary click path, so TalkBack activation works; a hold takes
 * over after [HOLD_DELAY_MS] and then suppresses the click that would otherwise land on release
 * and add one step too many.
 *
 * @param icon the chevron.
 * @param label what a screen reader announces.
 * @param onStep move one step.
 */
@Composable
private fun StepperArrow(
    @DrawableRes icon: Int,
    label: String,
    onStep: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(HOLD_DELAY_MS)
            repeated = true
            var wait = HOLD_REPEAT_MS
            while (true) {
                onStep()
                delay(wait)
                wait = maxOf(HOLD_FASTEST_MS, wait - HOLD_ACCEL_MS)
            }
        }
    }

    Box(
        modifier =
            Modifier
                .size(KrtSpacing.touchTarget)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        if (!repeated) {
                            onStep()
                        }
                        repeated = false
                    },
                )
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        KrtIcon(id = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The 42 days a month's grid shows, Monday first.
 *
 * @param month the month in the middle.
 * @return six weeks of consecutive days.
 */
private fun gridDays(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    return (0 until GRID_CELLS).map { start.plusDays(it.toLong()) }
}

/**
 * The next Friday strictly after this day — the third shortcut chip.
 *
 * Strictly after, so tapping it on a Friday moves a week rather than doing nothing.
 *
 * @receiver the day to count from.
 * @return the following Friday.
 */
private fun LocalDate.nextFriday(): LocalDate {
    val ahead = (DayOfWeek.FRIDAY.value - dayOfWeek.value + WEEK_LENGTH - 1) % WEEK_LENGTH + 1
    return plusDays(ahead.toLong())
}

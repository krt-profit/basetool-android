/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.greluc.krt.profit.basetool.android.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Which rung this instant falls on, against [now].
 *
 * Same boundary as [relativeTo] and deliberately the same expression, so the two cannot disagree
 * about what „gestern" means.
 *
 * @param now the instant to measure against.
 * @param zone the zone whose midnights separate the days.
 * @return the rung.
 */
fun Instant.timeRung(
    now: Instant,
    zone: ZoneId,
): KrtTimeRung {
    val days = ChronoUnit.DAYS.between(atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    return when {
        !isBefore(now) || days <= 0L -> KrtTimeRung.DISTANCE
        days == 1L -> KrtTimeRung.YESTERDAY
        else -> KrtTimeRung.DATED
    }
}

/**
 * The four forms the design's timestamps take, as chapter 07 writes them.
 *
 * `vor 4 Min.` · `vor 2 Std.` · `gestern, 21:14` · `15.08., 09:30`
 *
 * The first two come from the platform, which knows the plural rules and the abbreviations of
 * every locale the device carries. The last two do not: `DateUtils.getRelativeDateTimeString`
 * jumps straight from a relative span to a fully qualified date (`25.8.2026, 22:19`) and never
 * says „gestern" in German at all, which is why the yesterday and older rungs are composed here.
 * The boundary between them is the **calendar day**, not a count of elapsed hours — that is what
 * makes 21:14 read as „gestern" at two in the morning instead of „vor 5 Std.".
 *
 * Anything in the future stays relative on every rung: a countdown reads „in 2 Tagen", and an
 * absolute date would be the wrong answer for something that has not happened yet.
 *
 * @param now the instant to measure against, so a test can pick one.
 * @param context the resource lookup for the composed rungs and the 12/24-hour preference.
 * @param zone the zone whose midnights separate the days — the device's, at every call site.
 * @return the timestamp in the form the artboard writes for that distance.
 */
fun Instant.relativeTo(
    now: Instant,
    context: Context,
    zone: ZoneId,
): String {
    // A row written a moment ago can carry a timestamp a hair ahead of this device's clock, and
    // `getRelativeTimeSpanString` says „in 0 Min." for anything even a millisecond in the future.
    // Only that sliver is clamped: this function also renders genuinely future events — a mission
    // „in 5 Std." — and flattening those would be a worse lie than the one it fixes.
    val ahead = toEpochMilli() - now.toEpochMilli()
    val millis = if (ahead in 1..CLOCK_SKEW_MILLIS) now.toEpochMilli() else toEpochMilli()
    // Formatted in the zone this function was GIVEN, not in the device's.
    //
    // `DateUtils.formatDateTime` ignores any zone and uses the system default, so the function took
    // a zone for the day boundary and then printed the clock in a different one. On a device the
    // two are the same and nothing shows; on a CI runner defaulted to UTC the „gestern, 21:14" and
    // „15.08., 09:30" rungs came out two hours early, and the tests that assert a clock reading
    // were the only thing that noticed.
    //
    // `DateFormat.getTimeFormat` keeps what `DateUtils` was here for -- the member's own 12/24-hour
    // setting -- and accepts a zone.
    val time =
        DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone(zone) }
            .format(Date(millis))
    return when (timeRung(now, zone)) {
        KrtTimeRung.DISTANCE -> {
            DateUtils.getRelativeTimeSpanString(
                millis,
                now.toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString().decapitalised(context)
        }

        KrtTimeRung.YESTERDAY -> {
            context.getString(R.string.time_yesterday_at, time)
        }

        KrtTimeRung.DATED -> {
            context.getString(
                R.string.time_date_at,
                // A pattern rather than DateUtils.FORMAT_NUMERIC_DATE, which drops the leading
                // zero in German („15.8.") where the artboard writes „15.08.". The pattern is
                // translatable so a locale can reorder the fields; only the padding is fixed.
                DateTimeFormatter
                    .ofPattern(context.getString(R.string.time_date_pattern), locale(context))
                    .withZone(zone)
                    .format(this),
                time,
            )
        }
    }
}

/**
 * The same reading, against the clock and the zone of the device this runs on.
 *
 * @param now the instant to measure against; pass a ticking value to keep a countdown moving.
 * @return the timestamp in the form the artboard writes for that distance.
 */
@Composable
fun Instant.relativeToNow(now: Instant = Instant.now()): String =
    relativeTo(now, LocalContext.current, ZoneId.systemDefault())

/**
 * Whether this instant's rendered form already prints a time of day.
 *
 * @param now the instant to measure against.
 * @return `true` for the „gestern, 21:14" and „15.08., 09:30" rungs.
 */
fun Instant.carriesClock(now: Instant = Instant.now()): Boolean =
    timeRung(now, ZoneId.systemDefault()).carriesClock

/**
 * Lowers the leading capital the platform puts on a standalone span.
 *
 * German gets „Vor 2 Std." where every artboard writes „vor 2 Std."; English is unaffected,
 * because its abbreviated spans start with the number. Restricted to the first character so a
 * locale whose span opens on a proper noun keeps it.
 *
 * @param context the configuration whose locale decides how a character lowers.
 * @return the span with its first character lowered for that locale.
 */
private fun String.decapitalised(context: Context): String = replaceFirstChar { it.lowercase(locale(context)) }

/**
 * The locale the resources resolved to, which is the one the platform formatted the span in.
 *
 * @param context the configuration to read it from.
 * @return that locale, or the JVM default when the configuration carries none.
 */
private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

/**
 * How far ahead of this device a server timestamp may be and still count as "just now".
 *
 * One minute: below the resolution `getRelativeTimeSpanString` reports in anyway, so nothing that
 * would have rendered a real countdown is affected.
 */
private const val CLOCK_SKEW_MILLIS: Long = 60_000

/**
 * A wire timestamp as the chapters write a **day**: „12.07.".
 *
 * Day and month, no year — chapters 10 and 11 both write dates that way, because everything the
 * app shows a date for happened or is happening this game year, and a four-digit year in a
 * subtitle is four characters that never vary. Localised through the platform's own skeleton, so
 * an English build gets its own order rather than a German one transliterated.
 *
 * @param zone the zone whose calendar decides the day; the member's by default.
 * @return the formatted day, e.g. „12.07.".
 */
fun Instant.krtShortDay(zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter
        .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMM"))
        .withZone(zone)
        .format(this)

/**
 * A wire timestamp as the chapters write a **moment**: „16.08. 22:41".
 *
 * The day above plus the time, which is what chapter 11 leads a refinery run with now that it has
 * no number to lead with: on a Staffel with one refinery the station alone makes every card look
 * the same, and the time is what tells them apart.
 *
 * @param zone the zone the moment is read in; the member's by default.
 * @return the formatted moment, e.g. „16.08. 22:41".
 */
fun Instant.krtShortMoment(zone: ZoneId = ZoneId.systemDefault()): String {
    // Two skeletons joined by a space, not one „dd.MM. HH:mm" skeleton: asked for the whole thing
    // at once the platform inserts its locale's date/time separator and German came back as
    // „26.08., 22:41" — a comma the chapter does not write.
    val time =
        DateTimeFormatter
            .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm"))
            .withZone(zone)
    return "${krtShortDay(zone)} ${time.format(this)}"
}

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
 * Returns the rung this instant falls on against [now], using the same day boundary as [relativeTo].
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
 * Formats this instant as `vor 4 Min.`, `vor 2 Std.`, `gestern, 21:14` or `15.08., 09:30`, depending on its distance
 * from [now].
 *
 * The first two come from the platform; the yesterday and older rungs are composed here and split at
 * the calendar day, not at elapsed hours. A future instant stays relative on every rung.
 *
 * @param now the instant to measure against.
 * @param context the resource lookup for the composed rungs and the 12/24-hour preference.
 * @param zone the zone whose midnights separate the days.
 * @return the formatted timestamp.
 */
fun Instant.relativeTo(
    now: Instant,
    context: Context,
    zone: ZoneId,
): String {
    val ahead = toEpochMilli() - now.toEpochMilli()
    val millis = if (ahead in 1..CLOCK_SKEW_MILLIS) now.toEpochMilli() else toEpochMilli()
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
 * Lowers the first character of a platform span, which German capitalises („Vor 2 Std.").
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
 * How far ahead of this device a server timestamp may be and still count as "just now": one minute.
 */
private const val CLOCK_SKEW_MILLIS: Long = 60_000

/**
 * Formats this instant as a day without year, e.g. „12.07.", through the platform's localised skeleton.
 *
 * @param zone the zone whose calendar decides the day; the device's by default.
 * @return the formatted day.
 */
fun Instant.krtShortDay(zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter
        .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMM"))
        .withZone(zone)
        .format(this)

/**
 * Formats this instant as a day and time of day, e.g. „16.08. 22:41".
 *
 * @param zone the zone the moment is read in; the device's by default.
 * @return the formatted moment.
 */
fun Instant.krtShortMoment(zone: ZoneId = ZoneId.systemDefault()): String {
    val time =
        DateTimeFormatter
            .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm"))
            .withZone(zone)
    return "${krtShortDay(zone)} ${time.format(this)}"
}

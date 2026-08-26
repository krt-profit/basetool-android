/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.greluc.krt.profit.basetool.android.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    val millis = toEpochMilli()
    val time = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
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

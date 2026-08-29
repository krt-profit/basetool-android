/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** How a date reads on screen — the German order, which is what the design's fields show. */
private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** How a time reads on screen. */
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Splits a wire instant into the date and time halves the drawn field pair shows.
 *
 * **UTC on the wire, the device's zone on the screen** — the app's own contract. A blank or
 * unparseable value yields two blanks rather than throwing: a server that answers with something
 * this build cannot read must leave the field empty, not crash the tab.
 *
 * @receiver the wire value, ISO-8601, or blank.
 * @return `date to time`, both in the device zone, both blank when there is nothing to show.
 */
fun String.toKrtDateTime(): Pair<String, String> {
    val instant = trim().takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val local = instant?.atZone(ZoneId.systemDefault()) ?: return "" to ""
    return local.format(DATE_FORMAT) to local.format(TIME_FORMAT)
}

/**
 * Builds the wire instant back out of a date and a time half.
 *
 * Both halves are needed: a date with no time is not a point in time, and sending one would make
 * the server pick midnight on the member's behalf. Either half blank, or either half unreadable,
 * means „not given" — which for these fields is what the server reads as a clear.
 *
 * @param date the date half as typed, `TT.MM.JJJJ`.
 * @param time the time half as typed, `HH:MM`.
 * @return the ISO-8601 instant, or `null`.
 */
fun krtWireInstant(
    date: String,
    time: String,
): String? {
    val d = date.trim()
    val t = time.trim()
    if (d.isEmpty() || t.isEmpty()) {
        return null
    }
    return try {
        LocalDateTime
            .of(LocalDate.parse(d, DATE_FORMAT), LocalTime.parse(t, TIME_FORMAT))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()
    } catch (_: DateTimeParseException) {
        // A half-typed date is an ordinary state while somebody types, not an error to report.
        // The server validates what it is sent; the field simply does not send an unreadable value.
        null
    }
}

/**
 * The clock time a section's „Gespeichert" receipt shows.
 *
 * @return `HH:MM` in the device zone.
 */
fun krtClockNow(): String = LocalTime.now().format(TIME_FORMAT)

/**
 * When the Einsatz actually started, in words, for the schedule's state line.
 *
 * @receiver the wire value, or blank.
 * @return `TT.MM.JJJJ, HH:MM`, or blank when it has not started.
 */
fun String.toKrtStartedAt(): String {
    val (date, time) = toKrtDateTime()
    return if (date.isEmpty()) "" else "$date, $time"
}

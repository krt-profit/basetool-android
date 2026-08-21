/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.Mission
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The heading above one day's Einsätze.
 *
 * Structured rather than a finished string: the words are localised and the date is formatted in
 * the device's locale, both of which belong to the screen. Returning "Heute" from here would put
 * German into a module that has no string resources and would be wrong in English.
 */
sealed interface MissionDay {
    /** Einsätze starting on the device's today. */
    data object Today : MissionDay

    /** Einsätze starting on the device's tomorrow. */
    data object Tomorrow : MissionDay

    /**
     * Einsätze on any other day.
     *
     * @property date the day in the device's zone, which the screen renders as weekday + date.
     */
    data class On(
        val date: LocalDate,
    ) : MissionDay

    /**
     * Einsätze the server gave no usable time at all.
     *
     * Kept as a group of its own and placed last rather than dropped: an Einsatz with no date is a
     * data fault worth seeing, and hiding it would make the list disagree with the total it states.
     */
    data object Undated : MissionDay
}

/**
 * One day's worth of the list.
 *
 * @property day the heading
 * @property missions the Einsätze under it, in the order the server returned them
 */
data class MissionDaySection(
    val day: MissionDay,
    val missions: List<Mission>,
)

/**
 * Groups Einsätze into the dated sections the design's list is built from.
 *
 * **The device's zone decides the day, not UTC.** The wire is UTC; a member in Europe/Berlin
 * looking at an Einsatz at 23:30 UTC is looking at one that happens tomorrow, and grouping it under
 * today would put it under the wrong heading — visibly wrong exactly once a day, which is the kind
 * of bug that gets reported as "the app shows the wrong date sometimes".
 *
 * Server order is preserved inside each section, and the sections themselves keep the order in
 * which their first Einsatz appeared. The server already sorts by planned start, so re-sorting here
 * would be a second, weaker copy of a decision already made — except for [MissionDay.Undated],
 * which is forced last because it has no place on a timeline.
 *
 * @param missions the rows to group, in server order.
 * @param zone the device's zone.
 * @param today the device's current date, passed in rather than read here so a test can pin it.
 * @return the sections, in display order; empty when [missions] is empty.
 */
fun groupMissionsByDay(
    missions: List<Mission>,
    zone: ZoneId,
    today: LocalDate,
): List<MissionDaySection> {
    if (missions.isEmpty()) {
        return emptyList()
    }
    val tomorrow = today.plusDays(1)
    val sections = LinkedHashMap<MissionDay, MutableList<Mission>>()
    missions.forEach { mission ->
        val day = mission.groupingTime.toDay(zone, today, tomorrow)
        sections.getOrPut(day) { mutableListOf() }.add(mission)
    }
    return sections.entries
        .map { MissionDaySection(it.key, it.value.toList()) }
        // Undated last: it belongs to no point on the timeline, so wherever the first undated row
        // happened to appear is not where its heading belongs.
        .sortedBy { it.day == MissionDay.Undated }
}

/**
 * Resolves the heading one instant falls under.
 *
 * @param zone the device's zone.
 * @param today the device's current date.
 * @param tomorrow the day after [today], passed in so it is computed once per grouping.
 * @return the heading.
 */
private fun Instant?.toDay(
    zone: ZoneId,
    today: LocalDate,
    tomorrow: LocalDate,
): MissionDay {
    val date = this?.atZone(zone)?.toLocalDate() ?: return MissionDay.Undated
    return when (date) {
        today -> MissionDay.Today
        tomorrow -> MissionDay.Tomorrow
        else -> MissionDay.On(date)
    }
}

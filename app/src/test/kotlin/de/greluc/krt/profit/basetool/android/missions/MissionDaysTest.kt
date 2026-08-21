/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which day an Einsatz is filed under.
 *
 * Worth its own tests because the failure is invisible in the common case and wrong exactly once a
 * day: an Einsatz late in the evening is on a different UTC date than the member's, so grouping by
 * the wire's date rather than the device's puts it under yesterday's heading for everyone east of
 * Greenwich. That gets reported as "the app shows the wrong date sometimes", which is nearly
 * impossible to reproduce on demand.
 */
class MissionDaysTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-08-21")
    private val laterInTheWeek = LocalDate.parse("2026-08-25")

    private fun mission(
        id: String,
        planned: String? = null,
        actual: String? = null,
        meeting: String? = null,
    ) = Mission(
        id = id,
        name = "Einsatz $id",
        status = MissionStatus.PLANNED,
        rawStatus = "PLANNED",
        meetingTime = meeting?.let(Instant::parse),
        plannedStartTime = planned?.let(Instant::parse),
        actualStartTime = actual?.let(Instant::parse),
        plannedEndTime = null,
        isInternal = false,
        operationName = null,
        orgUnitName = null,
        orgUnitShorthand = null,
        meetingPoint = null,
    )

    @Test
    fun `an empty list produces no sections`() {
        assertEquals(emptyList<MissionDaySection>(), groupMissionsByDay(emptyList(), berlin, today))
    }

    @Test
    fun `today, tomorrow and any other day get their own headings`() {
        val sections =
            groupMissionsByDay(
                listOf(
                    mission("a", planned = "2026-08-21T17:00:00Z"),
                    mission("b", planned = "2026-08-22T17:00:00Z"),
                    mission("c", planned = "2026-08-25T17:00:00Z"),
                ),
                berlin,
                today,
            )

        assertEquals(
            listOf(MissionDay.Today, MissionDay.Tomorrow, MissionDay.On(laterInTheWeek)),
            sections.map { it.day },
        )
    }

    @Test
    fun `the device zone decides the day, not UTC`() {
        // 22:30 UTC on the 21st is 00:30 on the 22nd in Berlin. Grouping by the wire's date would
        // file it under today for a member for whom it is already tomorrow.
        val sections = groupMissionsByDay(listOf(mission("a", planned = "2026-08-21T22:30:00Z")), berlin, today)

        assertEquals(listOf(MissionDay.Tomorrow), sections.map { it.day })
    }

    @Test
    fun `several Einsaetze on one day share a section, in server order`() {
        val sections =
            groupMissionsByDay(
                listOf(
                    mission("a", planned = "2026-08-21T15:00:00Z"),
                    mission("b", planned = "2026-08-21T17:00:00Z"),
                ),
                berlin,
                today,
            )

        assertEquals(1, sections.size)
        assertEquals(listOf("a", "b"), sections.first().missions.map { it.id })
    }

    @Test
    fun `a running Einsatz is filed under the day it actually started`() {
        // The plan said yesterday, it started today. A member looking for it looks under today.
        val sections =
            groupMissionsByDay(
                listOf(mission("a", planned = "2026-08-20T17:00:00Z", actual = "2026-08-21T09:00:00Z")),
                berlin,
                today,
            )

        assertEquals(listOf(MissionDay.Today), sections.map { it.day })
    }

    @Test
    fun `the meeting time stands in when there is no start time at all`() {
        val sections = groupMissionsByDay(listOf(mission("a", meeting = "2026-08-21T16:00:00Z")), berlin, today)

        assertEquals(listOf(MissionDay.Today), sections.map { it.day })
    }

    @Test
    fun `an undated Einsatz is kept, and kept last`() {
        // Dropping it would make the list disagree with the total it states; leaving it where it
        // happened to arrive would put a heading with no date in the middle of a timeline.
        val sections =
            groupMissionsByDay(
                listOf(mission("a"), mission("b", planned = "2026-08-21T17:00:00Z")),
                berlin,
                today,
            )

        assertEquals(listOf(MissionDay.Today, MissionDay.Undated), sections.map { it.day })
        assertEquals(listOf("a"), sections.last().missions.map { it.id })
    }
}

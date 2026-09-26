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
 * Tests which day an Einsatz is filed under: the device's calendar date, not the wire's UTC date.
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

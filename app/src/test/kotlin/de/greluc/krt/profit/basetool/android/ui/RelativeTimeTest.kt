/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * The four rungs of chapter 07's timestamp ladder, in German.
 *
 * The chapter writes them out — `vor 4 Min.`, `vor 2 Std.`, `gestern, 21:14`, `15.08., 09:30` —
 * and the platform only supplies the first two. This pins all four, including the lower-case
 * opening the platform does not give and the calendar-day boundary that makes an evening
 * timestamp read as „gestern" rather than as a count of hours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-rDE")
class RelativeTimeTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /**
     * The device's own zone, deliberately **not** the one under test.
     *
     * The formatter takes a zone for the day boundary; it must print the clock in that zone too.
     * While it used `DateUtils.formatDateTime`, which silently uses the system default, the two
     * agreed on any German machine and disagreed on a CI runner set to UTC — where „09:30" came out
     * as „07:30". Pinning a different default here makes the mismatch a local failure instead of a
     * remote one.
     */
    @Before
    fun useAForeignDefaultZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    /** A fixed „now" so the rungs do not move with the wall clock. */
    private val now: Instant = local("2026-08-20T12:00")

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * A wall-clock reading in [zone].
     *
     * @param stamp an ISO local date-time, e.g. `2026-08-19T21:14`.
     * @return that reading as an instant.
     */
    private fun local(stamp: String): Instant = LocalDateTime.parse(stamp).atZone(zone).toInstant()

    /**
     * How the ladder renders a wall-clock reading against the fixed [now].
     *
     * @param stamp an ISO local date-time.
     * @return the rendered timestamp.
     */
    private fun at(stamp: String): String = local(stamp).relativeTo(now, context, zone)

    @Test
    fun `minutes read as the artboard writes them`() {
        assertEquals("vor 4 Min.", at("2026-08-20T11:56"))
    }

    @Test
    fun `hours read as the artboard writes them`() {
        assertEquals("vor 2 Std.", at("2026-08-20T10:00"))
    }

    /**
     * The rung the platform cannot reach.
     *
     * `getRelativeDateTimeString` answers this one with `19.8.2026, 21:14`, and the plain span
     * with „Vor 15 Std." — the chapter writes „gestern, 21:14".
     */
    @Test
    fun `yesterday evening is named, not counted in hours`() {
        assertEquals("gestern, 21:14", at("2026-08-19T21:14"))
    }

    /**
     * The question a row asks before pairing a clock with a distance.
     *
     * Design ch. 06 pairs an Einsatz as „TS 20:30 · in 2 Std."; its examples are all upcoming, so
     * the distance never carries a time of its own. The lower rungs do, and a row that printed both
     * read „TS 20:44 · gestern, 20:44".
     */
    @Test
    fun `the rung says whether the form already prints a clock`() {
        assertEquals(KrtTimeRung.DISTANCE, local("2026-08-20T10:00").timeRung(now, zone))
        assertEquals(KrtTimeRung.DISTANCE, local("2026-08-22T12:00").timeRung(now, zone))
        assertEquals(KrtTimeRung.YESTERDAY, local("2026-08-19T21:14").timeRung(now, zone))
        assertEquals(KrtTimeRung.DATED, local("2026-08-15T09:30").timeRung(now, zone))

        assertFalse(KrtTimeRung.DISTANCE.carriesClock)
        assertTrue(KrtTimeRung.YESTERDAY.carriesClock)
        assertTrue(KrtTimeRung.DATED.carriesClock)
    }

    @Test
    fun `older timestamps carry the day and the time, without the year`() {
        assertEquals("15.08., 09:30", at("2026-08-15T09:30"))
    }

    /**
     * A countdown stays relative however far out it sits, and keeps the platform's word for it.
     *
     * German has „übermorgen" and Android uses it; the count „in 2 Tagen" is the fallback of a
     * language that lacks the word. Both are the same rung of the ladder and both are relative,
     * which is the property that matters — an absolute date would be a factually correct answer
     * to a question nobody asked about something that has not happened yet.
     */
    @Test
    fun `the future stays a countdown past the day boundary`() {
        assertEquals("übermorgen", at("2026-08-22T12:00"))
        assertEquals("in 3 Tagen", at("2026-08-23T12:00"))
        assertEquals("in 2 Std.", at("2026-08-20T14:00"))
    }

    @Test
    fun `a timestamp a hair ahead of this device reads as past, not as a countdown`() {
        val now = Instant.parse("2026-08-27T21:00:00Z")
        // The server's clock is not this device's. Before the clamp, a booking written a moment
        // ago rendered „in 0 Min." — a thing that has already happened, described as pending.
        val justWritten = now.plusMillis(400)

        val text = justWritten.relativeTo(now, context, ZoneId.of("Europe/Berlin"))

        assertFalse(text, text.contains("in "))
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The date/time pair of design ch. 02 §11 — a picker, not two text fields.
 *
 * What these pin is the part that is easy to regress into: the halves must open modals rather than
 * accept typing, an empty pair must stay empty instead of helpfully pre-filling today, and a moment
 * already gone must be *named* rather than blocked. Every one of those was a defect the chapter was
 * written to correct.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class KrtDateTimeFieldTest {
    @get:Rule
    val compose = createComposeRule()

    /** An unset pair says what it wants and does not invent today. */
    @Test
    fun `an empty pair shows both placeholders`() {
        show(date = "", time = "")

        compose.onNodeWithText("Datum wählen").assertIsDisplayed()
        compose.onNodeWithText("--:--").assertIsDisplayed()
    }

    /** Tapping the date half opens the month grid rather than a keyboard. */
    @Test
    fun `the date half opens the month grid`() {
        show(date = "03.09.2026", time = "20:00")

        compose.onNodeWithTag(KRT_DATE_FIELD_TAG).performClick()

        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithText("HEUTE").assertIsDisplayed()
    }

    /** Picking a day reports it in German display form, never as an ISO string. */
    @Test
    fun `a picked day is reported in display form`() {
        var picked: String? = null
        show(date = "03.09.2026", time = "20:00", onDate = { picked = it })

        compose.onNodeWithTag(KRT_DATE_FIELD_TAG).performClick()
        compose.onNodeWithText("17").performClick()
        compose.onNodeWithText("ÜBERNEHMEN").performClick()

        assertEquals("17.09.2026", picked)
    }

    /** Tapping the time half opens the steppers, not a wheel. */
    @Test
    fun `the time half opens the steppers`() {
        show(date = "03.09.2026", time = "20:00")

        compose.onNodeWithTag(KRT_TIME_FIELD_TAG).performClick()

        compose.onNodeWithText("Stunde").assertIsDisplayed()
        compose.onNodeWithText("Minute").assertIsDisplayed()
        compose.onNodeWithText("20").assertIsDisplayed()
    }

    /** „Jetzt" rounds onto the five-minute step the stepper can actually reach. */
    @Test
    fun `now lands on the minute step`() {
        var picked: String? = null
        show(date = "03.09.2026", time = "", onTime = { picked = it }, now = AWKWARD_CLOCK)

        compose.onNodeWithTag(KRT_TIME_FIELD_TAG).performClick()
        compose.onNodeWithText("JETZT").performClick()
        compose.onNodeWithText("ÜBERNEHMEN").performClick()

        assertEquals("19:20", picked)
    }

    /** A moment already gone is named, and nothing is blocked by it. */
    @Test
    fun `a past moment is named`() {
        show(date = "01.09.2026", time = "18:00")

        compose.onNodeWithText("Liegt in der Vergangenheit").assertIsDisplayed()
    }

    /** A moment still ahead says nothing — the line is a warning, not a decoration. */
    @Test
    fun `a future moment is silent`() {
        show(date = "05.09.2026", time = "18:00")

        compose.onAllNodesWithText("Liegt in der Vergangenheit").assertCountEquals(0)
    }

    /** A half-set pair cannot be judged, so it is not judged. */
    @Test
    fun `a pair without a time is not called past`() {
        show(date = "01.09.2026", time = "")

        compose.onAllNodesWithText("Liegt in der Vergangenheit").assertCountEquals(0)
    }

    /**
     * Renders the pair against a fixed clock.
     *
     * @param date the date half.
     * @param time the time half.
     * @param onDate what a picked date reports to.
     * @param onTime what a picked time reports to.
     * @param now the clock „past" and „Jetzt" are measured against.
     */
    private fun show(
        date: String,
        time: String,
        onDate: (String) -> Unit = {},
        onTime: (String) -> Unit = {},
        now: LocalTime = MIDDAY,
    ) {
        compose.setContent {
            KrtTheme {
                KrtDateTimeField(
                    label = "Beginn",
                    date = date,
                    time = time,
                    onDate = onDate,
                    onTime = onTime,
                    now = LocalDateTime.of(TODAY, now),
                )
            }
        }
    }

    private companion object {
        /** The day every case is judged against; the chapter's own artboard month. */
        val TODAY: LocalDate = LocalDate.of(2026, 9, 3)

        /** The default clock — far from any boundary, so no case turns on the hour. */
        val MIDDAY: LocalTime = LocalTime.of(12, 0)

        /** 19:23: the artboard's own example of a time the five-minute stepper cannot reach. */
        val AWKWARD_CLOCK: LocalTime = LocalTime.of(19, 23)
    }
}

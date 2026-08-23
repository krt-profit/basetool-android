/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The full-screen retry state of design chapter 14. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KrtRetryCountdownTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows the countdown inside the ring`() {
        setContent(secondsLeft = 12)

        compose.onNodeWithTag("krt-retry-seconds").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun `a negative countdown reads as zero, not as a bug report to the member`() {
        // An overrun timer is our problem. Rendering "-2" hands it to the member instead.
        setContent(secondsLeft = -2)

        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun `the manual retry reports the press`() {
        var pressed = 0
        compose.setContent {
            KrtTheme {
                KrtRetryCountdown(
                    secondsLeft = 5,
                    title = "Signal instabil",
                    message = "Der Server ist ausgelastet.",
                    retryLabel = "Jetzt erneut versuchen",
                    onRetry = { pressed++ },
                )
            }
        }

        compose.onNodeWithTag("krt-retry-button").performClick()

        // Resetting the backoff is the caller's job; this only has to report that it happened.
        assertEquals(1, pressed)
    }

    @Test
    fun `both sentences of chapter 14 are shown`() {
        setContent(secondsLeft = 3)

        compose.onNodeWithText("Signal instabil").assertIsDisplayed()
        compose.onNodeWithText("Der Server ist ausgelastet.").assertIsDisplayed()
    }

    private fun setContent(secondsLeft: Int) {
        compose.setContent {
            KrtTheme {
                KrtRetryCountdown(
                    secondsLeft = secondsLeft,
                    title = "Signal instabil",
                    message = "Der Server ist ausgelastet.",
                    retryLabel = "Jetzt erneut versuchen",
                    onRetry = {},
                )
            }
        }
    }
}

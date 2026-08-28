/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A label that travels rather than being cut off.
 *
 * The assertion that matters is not the motion — Robolectric does not run the clock — but that the
 * **whole string stays in the semantics tree** at any width. A truncated `Text` reports the text it
 * was given, so an ellipsis is invisible to a screen reader *and* to a test; what would not survive
 * is a component that solved the overflow by shortening the string itself. Pinning it here is what
 * stops that from ever being the fix.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class KrtMarqueeTextTest {
    private companion object {
        /** Narrow enough that the label below cannot possibly fit. */
        val TOO_NARROW = 40.dp

        /** Wide enough that it does. */
        val ROOMY = 400.dp

        const val PLACE = "ARC-L1 Wide Forest Station · geteilt"
    }

    @get:Rule
    val compose = createComposeRule()

    private fun show(width: androidx.compose.ui.unit.Dp) {
        compose.setContent {
            KrtTheme {
                KrtMarqueeText(
                    text = PLACE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    modifier = Modifier.width(width),
                )
            }
        }
    }

    @Test
    fun `a label that fits is shown whole`() {
        show(ROOMY)

        compose.onNodeWithText(PLACE).assertIsDisplayed()
    }

    @Test
    fun `a label that does not fit is still the whole label`() {
        // The row shows part of it at a time; the string is never shortened. „ARC-L1 Wide Forest
        // Station" and „…Forest Depot" have to stay distinguishable to anything that reads the
        // screen rather than looks at it.
        show(TOO_NARROW)

        compose.onNodeWithText(PLACE).assertIsDisplayed()
    }

    @Test
    fun `an empty label draws nothing and does not fall over`() {
        compose.setContent {
            KrtTheme {
                KrtMarqueeText(
                    text = "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrtPalette.White,
                    modifier = Modifier.width(TOO_NARROW),
                )
            }
        }
        compose.waitForIdle()
    }
}

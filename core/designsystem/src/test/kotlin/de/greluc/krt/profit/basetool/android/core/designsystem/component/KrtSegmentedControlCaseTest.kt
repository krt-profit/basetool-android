/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Segment labels are drawn uppercase whatever case the caller passes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class KrtSegmentedControlCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a sentence-case label is drawn uppercase`() {
        compose.setContent {
            KrtTheme {
                KrtSegmentedControl(
                    options = listOf("Mitglied", "Verwaltung"),
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }

        compose.onNodeWithText("MITGLIED").assertIsDisplayed()
        compose.onNodeWithText("VERWALTUNG").assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("Mitglied", ignoreCase = false).fetchSemanticsNodes().size,
        )
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The open tab's underline has a measurable width, since `fillMaxWidth()` collapses to zero inside
 * the horizontally scrolling row.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class KrtPageTabsUnderlineTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the open tab's underline is as wide as the tab, not zero`() {
        compose.setContent {
            KrtTheme {
                KrtPageTabs(
                    tabs =
                        listOf(
                            KrtPageTab(label = "Konten"),
                            KrtPageTab(label = "Grants"),
                        ),
                    selectedIndex = 1,
                    onSelect = {},
                )
            }
        }

        val width = compose.onNodeWithTag(TAB_UNDERLINE_TAG, useUnmergedTree = true).fetchSemanticsNode().size.width
        val widthDp = with(compose.density) { width.toDp() }
        assertTrue("underline was $widthDp wide", widthDp >= 28.dp)
    }
}

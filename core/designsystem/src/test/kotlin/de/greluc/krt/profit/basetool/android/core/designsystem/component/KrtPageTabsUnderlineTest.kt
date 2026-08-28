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
 * The open tab's underline has to be visible, which means measurably wide.
 *
 * It once was not. The row scrolls horizontally, so it hands its children an **unbounded** width
 * constraint, and `fillMaxWidth()` collapses to zero under an infinite maximum — the underline was
 * in the composition, reported by every semantics query, and zero pixels wide on screen. Only a
 * device screenshot showed it, so the assertion here is on the measured width rather than on the
 * node existing.
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
        // The floor is the tab's own padding — 14 dp on each side — which a correctly filled
        // underline always spans. The label adds to it, but not by a predictable amount here:
        // Robolectric measures text with substitute metrics, so pinning the real on-device width
        // would make this test about the font rather than about the layout. Zero, which is what the
        // collapsed `fillMaxWidth()` produced, is what it has to reject.
        assertTrue("underline was $widthDp wide", widthDp >= 28.dp)
    }
}

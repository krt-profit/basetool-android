/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.navigation.LocalScreenTopBar
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.navigation.ScreenTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where a detail pane's published head goes on a tablet.
 *
 * A pushed detail publishes a [ScreenTopBar] and the shell draws it as the app bar's title, which
 * is right on a phone because the detail *is* the destination. In a list-detail it is a pane of a
 * section the rail is still highlighting, so a selected row used to leave the bar naming the row
 * while the rail named the section — the two disagreeing about where the member was.
 *
 * Both halves are pinned, because fixing only the first one is worse than the bug: a pane that
 * publishes nothing identifies nothing, and the list does not mark its selection either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-w1280dp-h800dp-xhdpi")
class ListDetailHeadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the detail pane draws the head its content published`() {
        composeRule.setContent {
            KrtTheme {
                KrtListDetail(detail = { ProvideScreenTopBar(title = "#1") }) { Text("die Liste") }
            }
        }

        composeRule.onNodeWithText("#1").assertIsDisplayed()
    }

    @Test
    fun `the detail pane's head does not reach the shell`() {
        // The whole point: the slot the shell reads must still be empty. If this ever fails, the
        // app bar has gone back to naming a row while the rail names the section.
        lateinit var shellSlot: MutableState<ScreenTopBar?>
        composeRule.setContent {
            shellSlot = remember { mutableStateOf<ScreenTopBar?>(null) }
            KrtTheme {
                CompositionLocalProvider(LocalScreenTopBar provides shellSlot) {
                    KrtListDetail(detail = { ProvideScreenTopBar(title = "#1") }) { Text("die Liste") }
                }
            }
        }
        composeRule.waitForIdle()

        assertNull(shellSlot.value)
    }

    @Test
    fun `the list still publishes to the shell`() {
        // Only the detail slot is redirected. The Lager's selection bar is published by the list,
        // and it has to keep reaching the shell — it replaces the whole bar by design.
        lateinit var shellSlot: MutableState<ScreenTopBar?>
        composeRule.setContent {
            shellSlot = remember { mutableStateOf<ScreenTopBar?>(null) }
            KrtTheme {
                CompositionLocalProvider(LocalScreenTopBar provides shellSlot) {
                    KrtListDetail(detail = null) { ProvideScreenTopBar(title = "die Liste") }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals("die Liste", shellSlot.value?.title)
    }
}

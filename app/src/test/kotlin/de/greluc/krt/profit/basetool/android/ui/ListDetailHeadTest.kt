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
 * Tests where a detail pane's head goes on a tablet: in a list-detail the app bar keeps naming the section, while the
 * pane shows its own head and the list marks its selection.
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

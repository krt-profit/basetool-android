/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the conflict dialog for a refused save (design chapter 14): a second refusal shows it again after the first was
 * dismissed, although `ApiError.OptimisticLock` values compare equal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class ConflictModalTest {
    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val TITLE = "Konflikt festgestellt"
        const val CANCEL = "ABBRECHEN"
        const val RELOAD = "NEU LADEN"
    }

    @Test
    fun `a refused save raises the dialog`() {
        compose.setContent {
            KrtTheme { ConflictOn(error = ApiError.OptimisticLock(), onReload = {}) }
        }

        compose.onNodeWithText(TITLE.uppercase()).assertIsDisplayed()
    }

    @Test
    fun `no dialog for a failure that is not a conflict`() {
        compose.setContent {
            KrtTheme { ConflictOn(error = ApiError.Server(status = 500), onReload = {}) }
        }

        compose.onNodeWithText(TITLE.uppercase()).assertDoesNotExist()
    }

    @Test
    fun `the reload action is reported once`() {
        var reloads = 0
        compose.setContent {
            KrtTheme { ConflictOn(error = ApiError.OptimisticLock(), onReload = { reloads += 1 }) }
        }

        compose.onNodeWithText(RELOAD).performClick()

        assertEquals(1, reloads)
    }

    /**
     * A new refusal equal to a dismissed one raises the dialog again.
     */
    @Test
    fun `a second refusal is raised again after the first was dismissed`() {
        val first = ApiError.OptimisticLock()
        val second = ApiError.OptimisticLock()

        assertEquals("the premise: two refusals compare equal", first, second)
        assertTrue("nothing dismissed yet", shouldRaiseConflict(error = first, seen = null))
        assertFalse("the one just dismissed", shouldRaiseConflict(error = first, seen = first))
        assertTrue(
            "a NEW refusal, equal to the dismissed one, must still be raised",
            shouldRaiseConflict(error = second, seen = first),
        )
    }

    @Test
    fun `a non-conflict failure raises nothing`() {
        assertFalse(shouldRaiseConflict(error = ApiError.Server(status = 500), seen = null))
        assertFalse(shouldRaiseConflict(error = null, seen = null))
    }
}

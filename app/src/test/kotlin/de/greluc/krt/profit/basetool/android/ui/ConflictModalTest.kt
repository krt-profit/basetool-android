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
 * The dialog design chapter 14 draws for a refused save, and the one thing about it that is subtle.
 *
 * The behaviour under test is not "does a dialog appear" — it is that a **second** refusal shows the
 * dialog again after the member dismissed the first. `ApiError.OptimisticLock` is a data class, so
 * two separate refusals compare equal, and the obvious implementation (`remember(error)`) treats the
 * second as the first: dismiss the dialog once and it never returns for the rest of the session,
 * leaving the member saving into a wall in silence.
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
     * The regression the identity check exists for.
     *
     * Dismiss the dialog, let a **new** refusal arrive that is equal to the first, and it must be
     * on screen again.
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

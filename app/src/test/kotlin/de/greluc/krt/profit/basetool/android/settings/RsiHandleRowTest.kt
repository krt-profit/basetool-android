/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

/**
 * The KONTO row for the RSI handle, against design ch. 19 artboard 10 (REQ-APP-SET-012).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class RsiHandleRowTest {
    private companion object {
        const val PURPOSE =
            "Nur dafür: ein verbundenes Tool darf fragen, ob ein Handle zu dir gehört. Der Server antwortet ja, nein " +
                "oder unbekannt — und gibt den Handle nie heraus."
        const val TAKEN = "Dieser Handle ist bereits einem anderen Profil zugeordnet."
    }

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        rsi: RsiHandleState,
        saves: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                RsiHandleRow(rsi = rsi, enabled = rsi.read, actions = RsiHandleActions(onSave = { saves += Unit }))
            }
        }
    }

    /** The purpose sentence answers „why" before anyone asks, so it stands with an empty handle too. */
    @Test
    fun `the purpose sentence stands under an empty field`() {
        show(RsiHandleState(read = true))

        compose.onNodeWithText(PURPOSE).assertIsDisplayed()
        compose.onNodeWithText(TAKEN).assertDoesNotExist()
    }

    @Test
    fun `a taken handle is said at the field`() {
        show(
            RsiHandleState(
                confirmed = null,
                read = true,
                draft = "GrafRotz_SC",
                refusal = RsiHandleRefusal.TAKEN,
            ),
        )

        compose.onNodeWithText(TAKEN).assertIsDisplayed()
        compose.onNodeWithText(PURPOSE).assertIsDisplayed()
    }

    @Test
    fun `saving is offered only for a change`() {
        val saves = mutableListOf<Unit>()
        show(RsiHandleState(confirmed = "GrafRotz", read = true, draft = "GrafRotz"), saves)

        compose.onNodeWithTag(SETTINGS_RSI_SAVE_TAG, useUnmergedTree = true).assertIsNotEnabled()
        assertEquals(emptyList<Unit>(), saves)
    }

    @Test
    fun `a changed handle can be saved`() {
        val saves = mutableListOf<Unit>()
        show(RsiHandleState(confirmed = "GrafRotz", read = true, draft = ""), saves)

        compose.onNodeWithTag(SETTINGS_RSI_SAVE_TAG, useUnmergedTree = true).assertIsEnabled().performClick()

        assertEquals(listOf(Unit), saves)
    }

    /** While the write runs the button gives way to the spinner, so it cannot be tapped twice. */
    @Test
    fun `a running save hides the button`() {
        show(RsiHandleState(read = true, draft = "GrafRotz", saving = true))

        compose.onNodeWithTag(SETTINGS_RSI_SAVE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}

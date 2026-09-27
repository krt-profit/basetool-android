/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.greluc.krt.profit.basetool.android.core.data.TermsDocument
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The re-consent overlay against design ch. 19 artboard 11 (REQ-APP-AUTH-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class ReconsentModalTest {
    @get:Rule
    val compose = createComposeRule()

    private val document =
        TermsDocument(
            version = "d1g3st",
            title = "Nutzungsbedingungen",
            intro = "Einleitung der neuen Fassung.",
            sections = emptyList(),
            lastUpdated = "Stand 26.09.2026",
        )

    private fun show(
        state: ReconsentState,
        signedOut: MutableList<Unit> = mutableListOf(),
        confirmed: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                ReconsentModal(
                    state = state,
                    onConfirm = { confirmed += Unit },
                    onRetry = {},
                    onSignOut = { signedOut += Unit },
                )
            }
        }
    }

    @Test
    fun `the overlay shows the wording and says the refused call goes out again`() {
        show(ReconsentState(open = true, document = document))

        compose.onNodeWithText("NUTZUNGSBEDINGUNGEN AKTUALISIERT").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Die Nutzungsbedingungen wurden geändert (Stand 26.09.2026). Um weiterzuarbeiten, bestätige die " +
                    "neue Fassung.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Einleitung der neuen Fassung.").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Nach der Bestätigung geht es genau dort weiter, wo du warst — der abgelehnte Aufruf wird wiederholt.",
            ).assertIsDisplayed()
    }

    /** Exactly two ways out: there is no close glyph. */
    @Test
    fun `the overlay offers no close glyph`() {
        show(ReconsentState(open = true, document = document))

        compose.onNodeWithContentDescription("Schließen").assertDoesNotExist()
    }

    @Test
    fun `the two ways out do what they say`() {
        val signedOut = mutableListOf<Unit>()
        val confirmed = mutableListOf<Unit>()
        show(ReconsentState(open = true, document = document), signedOut, confirmed)

        compose.onNode(hasText("BESTÄTIGEN") and hasClickAction()).performClick()
        compose.onNode(hasText("ABMELDEN") and hasClickAction()).performClick()

        assertEquals(listOf(Unit), confirmed)
        assertEquals(listOf(Unit), signedOut)
    }

    @Test
    fun `offline the confirmation is dimmed with its reason`() {
        show(ReconsentState(open = true, document = document, offline = true))

        compose.onNode(hasText("BESTÄTIGEN") and hasClickAction()).assertIsNotEnabled()
        compose
            .onNodeWithText("Offline — Bestätigen ist möglich, sobald die Verbindung zurück ist.")
            .assertIsDisplayed()
    }

    @Test
    fun `a closed overlay draws nothing`() {
        show(ReconsentState(open = false, document = document))

        compose.onNodeWithText("NUTZUNGSBEDINGUNGEN AKTUALISIERT").assertDoesNotExist()
    }
}

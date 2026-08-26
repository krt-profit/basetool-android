/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The way out of the app.
 *
 * Sign-out is the one control on this screen whose cost cannot be undone by tapping it again: the
 * stored refresh token and its Keystore key are destroyed, and the way back is the browser's
 * sign-in form. These tests hold the confirmation in place — a regression that wires the button
 * straight to [SettingsScreen]'s `onLogout` again would still look right on a screenshot.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Renders the settings screen with every callback stubbed but sign-out.
     *
     * @param loggedOut collects the sign-out invocations.
     */
    private fun show(loggedOut: MutableList<Unit>) {
        compose.setContent {
            KrtTheme {
                SettingsScreen(
                    accountName = "GrafRotz",
                    language = AppLanguage.German,
                    onLanguageChange = {},
                    appLockEnabled = false,
                    appLockAvailable = true,
                    onAppLockChange = {},
                    screenCaptureAllowed = false,
                    onScreenCaptureChange = {},
                    onOpenPrivacy = {},
                    onOpenImprint = {},
                    onOpenTerms = {},
                    onOpenLicenses = {},
                    onLogout = { loggedOut += Unit },
                    versionName = "0.1.0",
                    versionCode = 1,
                )
            }
        }
    }

    /** The button alone must not end the session — it opens the confirmation. */
    @Test
    fun `tapping sign out asks instead of signing out`() {
        val loggedOut = mutableListOf<Unit>()
        show(loggedOut)

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performClick()

        compose.onNodeWithTag(SETTINGS_LOGOUT_CONFIRM_TAG).assertIsDisplayed()
        assertEquals(emptyList<Unit>(), loggedOut)
    }

    /**
     * The body must name the consequence rather than ask a yes/no question — the rule the danger
     * tone carries. Asserting on the copy keeps a later edit from quietly emptying it.
     */
    @Test
    fun `the confirmation names what sign out costs`() {
        show(mutableListOf())

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performClick()

        compose
            .onNodeWithText(
                "Beendet die Sitzung und löscht den gespeicherten Anmelde-Schlüssel von diesem " +
                    "Gerät. Die nächste Anmeldung läuft wieder über das Anmeldeformular im Browser.",
            ).assertIsDisplayed()
    }

    /** Confirming is what actually ends the session. */
    @Test
    fun `confirming signs out once`() {
        val loggedOut = mutableListOf<Unit>()
        show(loggedOut)

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performClick()
        compose.onNodeWithText("JETZT ABMELDEN").performClick()

        assertEquals(listOf(Unit), loggedOut)
    }

    /** Cancelling leaves the session alone and closes the modal. */
    @Test
    fun `cancelling keeps the session`() {
        val loggedOut = mutableListOf<Unit>()
        show(loggedOut)

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performClick()
        compose.onNodeWithText("ABBRECHEN").performClick()

        assertEquals(emptyList<Unit>(), loggedOut)
        compose.onNodeWithTag(SETTINGS_LOGOUT_CONFIRM_TAG).assertIsNotDisplayed()
    }
}

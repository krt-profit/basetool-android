/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.PayoutPreference
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests the sign-out confirmation, since signing out destroys the stored refresh token and its Keystore key, and
 * when the payout row accepts a tap.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val openedConnectedApps = mutableListOf<Unit>()

    /**
     * Renders the settings screen with every callback stubbed but sign-out and the payout row.
     *
     * @param loggedOut collects the sign-out invocations.
     * @param preferences the server-side rows' state.
     * @param payouts collects the payout writes the row asks for.
     */
    private fun show(
        loggedOut: MutableList<Unit>,
        preferences: MemberPreferencesState = MemberPreferencesState(),
        payouts: MutableList<PayoutPreference> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                SettingsScreen(
                    orgUnitName = "Bereich Profit",
                    onSwitchOrgUnit = {},
                    preferences = preferences,
                    onPayout = { payouts += it },
                    onSharing = {},
                    accountName = "GrafRotz",
                    language = AppLanguage.German,
                    onLanguageChange = {},
                    appLockEnabled = false,
                    appLockAvailable = true,
                    onAppLockChange = {},
                    screenCaptureAllowed = false,
                    onScreenCaptureChange = {},
                    onRetryPreferences = {},
                    rsiActions = RsiHandleActions(),
                    onOpenPrivacy = {},
                    onOpenImprint = {},
                    onOpenTerms = {},
                    onOpenConnectedApps = { openedConnectedApps += Unit },
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

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performScrollTo().performClick()

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

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performScrollTo().performClick()

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

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performScrollTo().performClick()
        compose.onNodeWithText("JETZT ABMELDEN").performClick()

        assertEquals(listOf(Unit), loggedOut)
    }

    /** Cancelling leaves the session alone and closes the modal. */
    @Test
    fun `cancelling keeps the session`() {
        val loggedOut = mutableListOf<Unit>()
        show(loggedOut)

        compose.onNodeWithTag(SETTINGS_LOGOUT_TAG).performScrollTo().performClick()
        compose.onNodeWithText("ABBRECHEN").performClick()

        assertEquals(emptyList<Unit>(), loggedOut)
        compose.onNodeWithTag(SETTINGS_LOGOUT_CONFIRM_TAG).assertIsNotDisplayed()
    }

    /** A payout the member never chose is a read value, so the row takes the first choice. */
    @Test
    fun `a read but never chosen payout can be set`() {
        val payouts = mutableListOf<PayoutPreference>()
        show(
            loggedOut = mutableListOf(),
            preferences = MemberPreferencesState(payout = null, payoutRead = true, sharing = false, version = 1),
            payouts = payouts,
        )

        compose.onNodeWithText("Noch nicht gewählt").assertIsDisplayed()
        compose.onNodeWithText("Auszahlungspräferenz").assertIsEnabled().performClick()

        assertEquals(listOf(PayoutPreference.DONATE), payouts)
    }

    /** Without a read there is no version to echo, so the row stays shut. */
    @Test
    fun `an unread payout cannot be set`() {
        show(loggedOut = mutableListOf())

        compose.onNodeWithText("Auszahlungspräferenz").assertIsNotEnabled()
    }

    /** „Verbundene Anwendungen" stays web-only; the KONTO row opens the page (main repo REQ-XCH-032). */
    @Test
    fun `the connected applications row opens the web page`() {
        show(mutableListOf())

        compose.onNodeWithText("Verbundene Anwendungen").performScrollTo().performClick()

        assertEquals(listOf(Unit), openedConnectedApps)
    }
}

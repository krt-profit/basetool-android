/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.NotificationPreference
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Tests the BENACHRICHTIGUNGEN group: what each state draws, which way a tap reports, and that a
 * locked row cannot be switched.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class NotificationPreferencesGroupTest {
    @get:Rule
    val compose = createComposeRule()

    private val toggles = mutableListOf<Pair<String, Boolean>>()
    private var retries = 0

    private val rows =
        listOf(
            NotificationPreference("JOB_ORDER_CREATED", mutable = true, muted = false),
            NotificationPreference("BANK_BOOKING_REQUEST_CREATED", mutable = true, muted = true),
            NotificationPreference("ACCOUNT_DELETION_REQUESTED", mutable = false, muted = false),
            NotificationPreference("SOMETHING_THE_SERVER_ADDED_LATER", mutable = true, muted = false),
        )

    private fun show(state: NotificationPreferencesState) {
        compose.setContent {
            KrtTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NotificationPreferencesGroup(
                        preferences = state,
                        actions =
                            NotificationPreferenceActions(
                                onToggle = { type, receive -> toggles += type to receive },
                                onRetry = { retries++ },
                            ),
                    )
                }
            }
        }
    }

    @Test
    fun `before the first read the group says it is loading`() {
        show(NotificationPreferencesState(reading = true))

        compose.onNodeWithTag(NOTIFICATION_PREFS_LOADING_TAG).assertIsDisplayed()
    }

    @Test
    fun `a failed first read shows the message and a retry that reports`() {
        show(NotificationPreferencesState(readError = ApiError.Network(IOException("offline"))))

        compose.onNodeWithTag(NOTIFICATION_PREFS_READ_FAILED_TAG).assertIsDisplayed()
        compose.onNodeWithText("ERNEUT VERSUCHEN", ignoreCase = true).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `a list the server sent empty says so`() {
        show(NotificationPreferencesState(read = true))

        compose.onNodeWithTag(NOTIFICATION_PREFS_EMPTY_TAG).assertIsDisplayed()
    }

    @Test
    fun `the rows are grouped under their areas and an unknown type lands under Weitere`() {
        show(NotificationPreferencesState(read = true, rows = rows))

        listOf("Aufträge", "Kartellbank", "Konto & Verwaltung", "Weitere").forEach {
            compose.onNodeWithText(it, ignoreCase = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Neuer Auftrag für deine Einheit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Neue Benachrichtigungsart").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a switch is on unless its type is muted`() {
        show(NotificationPreferencesState(read = true, rows = rows))

        compose.onNodeWithTag(notificationPreferenceTag("JOB_ORDER_CREATED")).performScrollTo().assertIsOn()
        compose.onNodeWithTag(notificationPreferenceTag("BANK_BOOKING_REQUEST_CREATED")).performScrollTo().assertIsOff()
    }

    @Test
    fun `tapping an on switch reports the type as no longer wanted`() {
        show(NotificationPreferencesState(read = true, rows = rows))

        compose.onNodeWithTag(notificationPreferenceTag("JOB_ORDER_CREATED")).performScrollTo().performClick()

        assertEquals(listOf("JOB_ORDER_CREATED" to false), toggles)
    }

    @Test
    fun `tapping an off switch reports the type as wanted again`() {
        show(NotificationPreferencesState(read = true, rows = rows))

        compose.onNodeWithTag(
            notificationPreferenceTag("BANK_BOOKING_REQUEST_CREATED"),
        ).performScrollTo().performClick()

        assertEquals(listOf("BANK_BOOKING_REQUEST_CREATED" to true), toggles)
    }

    @Test
    fun `a type that cannot be muted is on, disabled and explains why`() {
        show(NotificationPreferencesState(read = true, rows = rows))

        val locked = compose.onNodeWithTag(notificationPreferenceTag("ACCOUNT_DELETION_REQUESTED")).performScrollTo()
        locked.assertIsOn()
        locked.assertIsNotEnabled()
        compose
            .onNodeWithText("Kann nicht abbestellt werden (gesetzliche Frist bzw. Sicherheitshinweis).")
            .assertIsDisplayed()
        locked.performClick()

        assertTrue("a locked row never reports", toggles.isEmpty())
    }

    @Test
    fun `a failed write is stated under the rows`() {
        show(
            NotificationPreferencesState(
                read = true,
                rows = rows,
                writeError = ApiError.Network(IOException("offline")),
            ),
        )

        compose.onNodeWithTag(NOTIFICATION_PREFS_WRITE_FAILED_TAG).performScrollTo().assertIsDisplayed()
    }
}

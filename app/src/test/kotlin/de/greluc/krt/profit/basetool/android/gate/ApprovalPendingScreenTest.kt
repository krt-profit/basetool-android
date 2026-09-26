/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.gate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.ApprovalStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests the gate's three messages: waiting for approval, refused, and approved without a role.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class ApprovalPendingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(status: ApprovalStatus) {
        compose.setContent {
            KrtTheme {
                ApprovalPendingScreen(
                    status = status,
                    accountName = "GrafRotz",
                    refreshing = false,
                    onRefresh = {},
                    onLogout = {},
                )
            }
        }
    }

    /**
     * The waiting state names the queue, because that is what the member is in.
     */
    @Test
    fun `a pending account is told an administrator has to act`() {
        render(ApprovalStatus.PENDING)

        compose.onNodeWithText("FREIGABE AUSSTEHEND").assertIsDisplayed()
    }

    /**
     * The refused state is terminal and says so.
     */
    @Test
    fun `a rejected account is not told to keep waiting`() {
        render(ApprovalStatus.REJECTED)

        compose.onNodeWithText("ZUGANG ABGELEHNT").assertIsDisplayed()
    }

    /**
     * A role-less account (REQ-SEC-053) gets its own copy instead of the pending-approval one.
     */
    @Test
    fun `a role-less account is told a role is missing, not an approval`() {
        render(ApprovalStatus.NO_ROLE)

        compose.onNodeWithText("KEINE ROLLE VERGEBEN").assertIsDisplayed()
    }

    /**
     * An unrecognised server status falls back to the waiting copy, not to the refused one.
     */
    @Test
    fun `an unknown status reads as waiting`() {
        render(ApprovalStatus.UNKNOWN)

        compose.onNodeWithText("FREIGABE AUSSTEHEND").assertIsDisplayed()
    }
}

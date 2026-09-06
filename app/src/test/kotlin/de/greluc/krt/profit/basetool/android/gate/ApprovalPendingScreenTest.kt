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
 * What the gate tells a member it is holding them for.
 *
 * The screen is one layout with three sets of words, and the words are the entire product: a
 * member who is waiting for an administrator, one who was refused, and one who is approved but
 * holds no role can each do something different about it — and only if they are told which of the
 * three they are. A wrong headline here is not a cosmetic defect, it is an instruction to wait for
 * something that is not coming.
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
     * The role-less state (main repo REQ-SEC-053) is its own copy.
     *
     * This is the case D13 exists for: before it, a role-less account fell through to the generic
     * refusal and was told its approval was pending — which is false, and points the member at an
     * administrator who has already approved them.
     */
    @Test
    fun `a role-less account is told a role is missing, not an approval`() {
        render(ApprovalStatus.NO_ROLE)

        compose.onNodeWithText("KEINE ROLLE VERGEBEN").assertIsDisplayed()
    }

    /**
     * An unrecognised server status falls back to the waiting copy, not to the refused one.
     *
     * "Not cleared" is all an unknown status means, and the refused wording would tell a member
     * their account was turned down on the strength of a value this build simply predates.
     */
    @Test
    fun `an unknown status reads as waiting`() {
        render(ApprovalStatus.UNKNOWN)

        compose.onNodeWithText("FREIGABE AUSSTEHEND").assertIsDisplayed()
    }
}

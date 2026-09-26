/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests the Konten tab's two calls to action, which look alike but are gated differently: „Konto anlegen" needs
 * Bank-Management, the direct booking `BANK_EMPLOYEE` plus a per-account grant.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BankStaffCtaBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val booked = mutableListOf<Unit>()
    private val created = mutableListOf<Unit>()
    private val locked = mutableListOf<Unit>()

    private fun render(management: Boolean) {
        compose.setContent {
            KrtTheme {
                BankStaffCtaBar(
                    management = management,
                    onDirectBooking = { booked += Unit },
                    onCreateAccount = { created += Unit },
                    onLocked = { locked += Unit },
                )
            }
        }
    }

    /**
     * A bank employee with a per-account grant but without Bank-Management may open the direct booking.
     */
    @Test
    fun `a bank employee without management may open the direct booking`() {
        render(management = false)

        compose.onNodeWithTag(BANK_DIRECT_OPEN_TAG).performClick()

        assertEquals(1, booked.size)
        assertEquals("no lock may be raised on this one", 0, locked.size)
    }

    /** Creating an account really is a management act, and keeps its lock. */
    @Test
    fun `the same caller is locked out of creating an account`() {
        render(management = false)

        compose.onNodeWithTag(BANK_CREATE_ACCOUNT_TAG).performClick()

        assertEquals(0, created.size)
        assertEquals(1, locked.size)
    }

    /** With the grant, the second one opens too. */
    @Test
    fun `management may create an account`() {
        render(management = true)

        compose.onNodeWithTag(BANK_CREATE_ACCOUNT_TAG).performClick()

        assertEquals(1, created.size)
        assertEquals(0, locked.size)
    }
}

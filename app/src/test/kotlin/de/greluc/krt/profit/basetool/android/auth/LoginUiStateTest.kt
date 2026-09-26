/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.auth

import de.greluc.krt.profit.basetool.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checks the `messageRes` each of the three `LoginUiState` members overrides.
 */
class LoginUiStateTest {
    @Test
    fun `Idle has nothing to say`() {
        assertNull(LoginUiState.Idle.messageRes)
    }

    @Test
    fun `Working announces the sign-in in progress`() {
        assertEquals(R.string.login_signing_in, LoginUiState.Working.messageRes)
    }

    @Test
    fun `Failed carries the message it was given`() {
        assertEquals(
            R.string.login_error_denied,
            LoginUiState.Failed(R.string.login_error_denied).messageRes,
        )
    }
}

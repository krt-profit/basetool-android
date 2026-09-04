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
 * The login's three states and the one thing the screen reads off them.
 *
 * Written when `LoginUiState` stopped being a `sealed class` and became a `sealed interface`
 * (ADR-0020): the Compose compiler puts a `$stable` field on every class it touches, so the sealed
 * class carried one on the parent and one on each member, which CodeQL reported as
 * `java/field-masks-super-field`. An interface carries none.
 *
 * That refactor moved `messageRes` from a constructor parameter of the parent to an abstract
 * property each member overrides — a change the compiler checks for existence but not for value.
 * Nothing else in the suite reads these three, so without this test the only thing standing
 * between `Working` and a silently wrong string resource is that nobody mistyped it.
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

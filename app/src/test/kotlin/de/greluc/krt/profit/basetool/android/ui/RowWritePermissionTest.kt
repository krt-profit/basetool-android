/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.greluc.krt.profit.basetool.android.core.data.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests who is offered a write on one Lager row: the server-computed `InventoryItemDto.canEdit` decides, which is right
 * for an admin, a foreign Logistician and a member on their own stock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RowWritePermissionTest {
    @get:Rule val compose = createComposeRule()

    private companion object {
        const val ME = "user-me"
        const val SOMEBODY_ELSE = "user-other"
    }

    /**
     * Evaluates the helper inside a composition with a given caller.
     *
     * @param caller who is asking, or `null` for an identity that has not been read.
     * @param canEdit the row's own flag, as the server sent it.
     * @param ownerId the row's holder.
     * @return what the helper answered.
     */
    private fun mayEdit(
        caller: Identity?,
        canEdit: Boolean?,
        ownerId: String?,
    ): Boolean {
        var answer = false
        compose.setContent {
            CompositionLocalProvider(LocalCaller provides caller) {
                answer = mayEditRowOf(canEdit, ownerId)
            }
        }
        return answer
    }

    @Test
    fun `the server's yes wins over a caller who owns nothing and holds nothing`() {
        val stranger = Identity(userId = ME, logistician = false)

        assertTrue(mayEdit(stranger, canEdit = true, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `the server's no wins over a grant the caller does hold`() {
        val logistician = Identity(userId = ME, logistician = true)

        assertFalse(mayEdit(logistician, canEdit = false, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `a member keeps their own row when the server said nothing`() {
        val member = Identity(userId = ME, logistician = false)

        assertTrue(mayEdit(member, canEdit = null, ownerId = ME))
    }

    @Test
    fun `a member does not reach somebody else's row when the server said nothing`() {
        val member = Identity(userId = ME, logistician = false)

        assertFalse(mayEdit(member, canEdit = null, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `an unread identity leaves the control enabled rather than locking it`() {
        assertTrue(mayEdit(caller = null, canEdit = null, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `an unread identity does not hand out the Logistician grant`() {
        var answer: Boolean? = true
        compose.setContent {
            CompositionLocalProvider(LocalCaller provides null) { answer = isLogistician() }
        }

        assertNull(answer)
    }

    @Test
    fun `an unknown permission locks the control without claiming a missing grant`() {
        val gate =
            Gate.of(
                permitted = null,
                reason = "Dafür brauchst du die Rolle Logistiker.",
                detail = "Ein Offizier vergibt sie.",
                unknownReason = "Berechtigung nicht prüfbar",
                unknownDetail = "Deine Berechtigungen konnten nicht geladen werden.",
            )

        assertFalse(gate.allowed)
        assertEquals("Berechtigung nicht prüfbar", gate.reason)
    }

    @Test
    fun `a held grant still opens the control`() {
        val gate =
            Gate.of(
                permitted = true,
                reason = "r",
                detail = "d",
                unknownReason = "u",
                unknownDetail = "ud",
            )

        assertTrue(gate.allowed)
    }

    @Test
    fun `a row that names no holder is open, as it always was`() {
        val member = Identity(userId = ME, logistician = false)

        assertTrue(mayEdit(member, canEdit = null, ownerId = null))
    }
}

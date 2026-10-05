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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests who is offered a bulk write over a Lager selection: only the caller's own rows, for every role, because
 * `bulk-checkout` and `bulk-rebook` refuse any other row (REQ-APP-INV-013).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BulkWritePermissionTest {
    @get:Rule val compose = createComposeRule()

    private companion object {
        const val ME = "user-me"
        const val SOMEBODY_ELSE = "user-other"
    }

    /**
     * Evaluates the helper inside a composition with a given caller.
     *
     * @param caller who is asking, or `null` for an identity that has not been read.
     * @param holderIds the selected rows' holders.
     * @return what the helper answered.
     */
    private fun mayBulk(
        caller: Identity?,
        holderIds: List<String?>,
    ): Boolean {
        var answer = false
        compose.setContent {
            CompositionLocalProvider(LocalCaller provides caller) {
                answer = mayBulkWriteRowsOf(holderIds)
            }
        }
        return answer
    }

    @Test
    fun `a member's own selection is offered`() {
        val member = Identity(userId = ME, logistician = false)

        assertTrue(mayBulk(member, listOf(ME, ME)))
    }

    @Test
    fun `a Logistician's selection holding another member's row is locked`() {
        val logistician = Identity(userId = ME, logistician = true)

        assertFalse(mayBulk(logistician, listOf(ME, SOMEBODY_ELSE)))
    }

    @Test
    fun `an admin's selection holding another member's row is locked`() {
        val admin = Identity(userId = ME, logistician = true, admin = true)

        assertFalse(mayBulk(admin, listOf(SOMEBODY_ELSE)))
    }

    @Test
    fun `a Logistician's own selection is offered`() {
        val logistician = Identity(userId = ME, logistician = true)

        assertTrue(mayBulk(logistician, listOf(ME)))
    }

    @Test
    fun `a row that names no holder is not counted as the caller's own`() {
        val member = Identity(userId = ME, logistician = false)

        assertFalse(mayBulk(member, listOf(ME, null)))
    }

    @Test
    fun `an unread identity leaves the decision to the server`() {
        assertTrue(mayBulk(caller = null, holderIds = listOf(SOMEBODY_ELSE)))
    }
}

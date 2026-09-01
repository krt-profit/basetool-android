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
 * Who is offered a write on one Lager row.
 *
 * **The defect this pins is not a rounding error in a rule but a different rule.** The helper used
 * to answer *own row, or `Identity.logistician`*, and its own KDoc claimed the approximation could
 * only ever be too generous — "it never hides an action the member could in fact perform". That was
 * false for the people it mattered most to: `isLogistician` on the me-response reports whether a
 * **Staffel membership row** carries the flag, an admin holds no Staffel membership by design, and
 * so the Lager's Zuordnung and Umbuchen were greyed out for the one role that may edit every row.
 *
 * The row now carries the server's own answer (`InventoryItemDto.canEdit`, computed by the same
 * gate the endpoint's `@PreAuthorize` reaches), and these tests state what that buys: a decision
 * that is right for an admin, for a foreign Logistician, and for a member on their own stock —
 * none of which the client could have worked out for itself.
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
        // The admin case, stated from the client's side: no membership grant, not the row's owner,
        // and still permitted. Nothing the app knows about this caller would have produced `true`.
        val stranger = Identity(userId = ME, logistician = false)

        assertTrue(mayEdit(stranger, canEdit = true, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `the server's no wins over a grant the caller does hold`() {
        // A Logistician standing in front of another Staffel's row. The grant is real and the
        // scope is not, which is precisely the half the client never had.
        val logistician = Identity(userId = ME, logistician = true)

        assertFalse(mayEdit(logistician, canEdit = false, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `a member keeps their own row when the server said nothing`() {
        // An older server, or a read that failed. Falling back to "own row" keeps a member working
        // on their own stock; widening it would be the client guessing the hierarchy again.
        val member = Identity(userId = ME, logistician = false)

        assertTrue(mayEdit(member, canEdit = null, ownerId = ME))
    }

    @Test
    fun `a member does not reach somebody else's row when the server said nothing`() {
        // Split from the case above rather than asserted beside it: the rule's setContent may run
        // once per composition, so two evaluations need two tests.
        val member = Identity(userId = ME, logistician = false)

        assertFalse(mayEdit(member, canEdit = null, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `an unread identity leaves the control enabled rather than locking it`() {
        // Unknown is not forbidden: refusing on an outage would lock a member out of their own
        // stock because a request timed out. The server stays the authority either way.
        assertTrue(mayEdit(caller = null, canEdit = null, ownerId = SOMEBODY_ELSE))
    }

    @Test
    fun `a row that names no holder is open, as it always was`() {
        val member = Identity(userId = ME, logistician = false)

        assertTrue(mayEdit(member, canEdit = null, ownerId = null))
    }
}

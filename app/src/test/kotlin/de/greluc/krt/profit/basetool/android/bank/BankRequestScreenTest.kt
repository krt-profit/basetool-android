/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankRequestApprover
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ProblemDetail
import de.greluc.krt.profit.basetool.android.core.network.ProblemFieldError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The member's booking-request surface — design chapter 12, artboards 1 and 3.
 *
 * These tests exist to pin the two places where the artboards describe a mechanism the API does
 * not have, and where this app therefore deliberately renders something else:
 *
 * 1. **There is no approval counter.** The artboards draw „1 / 2 FREIGABEN" and „BESTÄTIGEN (2/2)"
 *    and a footnote about a staggered ladder of two and three approvals. `BankBookingRequestDto`
 *    carries no count: one owner approval is either needed or not, and either granted or not
 *    (REQ-BANK-041). The KRT account's ladder escalates the *class* of approver with the amount
 *    (REQ-BANK-047), never the number of them.
 * 2. **A deposit is never approval-limited** (REQ-BANK-042), so the sheet says nothing about a
 *    threshold while EINZAHLUNG is selected, although artboard 3 shows the hint in that state.
 *
 * Both corrections went back to the design side; until the artboards follow, these assertions are
 * what stops the fiction from being reintroduced by someone reading the mockups.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BankRequestScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun account(
        limit: String? = "100000.0000",
        exempt: Boolean = false,
    ) = BankAccountSummary(
        id = "a1",
        accountNo = "K-001",
        name = "Einsatzkasse",
        orgUnitName = "Bereich Profit",
        balance = "84200.0000",
        delta30d = null,
        sparkline = emptyList(),
        canRequest = true,
        approvalLimit = limit,
        approvalExempt = exempt,
    )

    private fun request(
        status: BankRequestStatus = BankRequestStatus.PENDING,
        requiresApproval: Boolean = true,
        granted: Boolean = false,
        approver: BankRequestApprover? = BankRequestApprover.RESPONSIBLE_HOLDER,
    ) = BankBookingRequest(
        id = "r1",
        accountId = "a1",
        accountName = "Einsatzkasse",
        targetAccountId = null,
        kind = BankRequestKind.WITHDRAWAL,
        amount = "120000.0000",
        note = "Auszahlung Operation Rotschild",
        status = status,
        requester = "Rhea",
        rejectReason = null,
        applicableLimit = "100000.0000",
        requiresOwnerApproval = requiresApproval,
        ownerApprovalGranted = granted,
        ownerApprovalBy = if (granted) "Vex" else null,
        requiredApprover = approver,
        createdAt = "2026-08-01T00:00:00Z",
        version = 1,
    )

    private fun sheet(
        draft: BankRequestDraftState,
        accounts: List<BankAccountSummary> = listOf(account()),
    ) {
        compose.setContent {
            KrtTheme {
                BankRequestSheet(
                    state = draft,
                    accounts = accounts,
                    targets = emptyList(),
                    online = true,
                    actions =
                        BankRequestSheetActions(
                            onKind = {},
                            onAccount = {},
                            onTarget = {},
                            onAmount = {},
                            onNote = {},
                            onSubmit = {},
                            onDismiss = {},
                        ),
                )
            }
        }
    }

    private fun tab(
        row: BankRequestRow,
        online: Boolean = true,
    ) {
        compose.setContent {
            KrtTheme {
                BankRequestsTab(
                    state = BankRequestsState(rows = listOf(row), phase = BankPhase.Ready, online = online),
                    onRefresh = {},
                    actions =
                        BankRequestRowActions(
                            onGrant = {},
                            onRevoke = {},
                            onEdit = {},
                            onWithdraw = {},
                        ),
                )
            }
        }
    }

    @Test
    fun `a deposit shows no approval threshold at all`() {
        sheet(BankRequestDraftState(kind = BankRequestKind.DEPOSIT, accountId = "a1", amount = "500000"))

        assertEquals(0, compose.onAllNodesWithTag(BANK_REQUEST_LIMIT_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `a withdrawal under the limit states the threshold that would apply`() {
        sheet(BankRequestDraftState(kind = BankRequestKind.WITHDRAWAL, accountId = "a1", amount = "500"))

        compose.onNodeWithTag(BANK_REQUEST_LIMIT_TAG).assertIsDisplayed()
        compose.onNodeWithText("Ab 100.000 aUEC", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a withdrawal over the limit says the holder must approve first`() {
        sheet(BankRequestDraftState(kind = BankRequestKind.WITHDRAWAL, accountId = "a1", amount = "120000"))

        compose
            .onNodeWithText("Kontoverantwortlichen freigegeben", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun `no state of the sheet ever claims a number of approvals`() {
        sheet(BankRequestDraftState(kind = BankRequestKind.WITHDRAWAL, accountId = "a1", amount = "120000"))

        // The artboard's wording, which the API cannot support. A regression would reintroduce it
        // verbatim, so the literal is the assertion.
        assertEquals(
            0,
            compose.onAllNodesWithText("2 Freigaben", substring = true, ignoreCase = true).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            compose.onAllNodesWithText(
                "Freigaben nötig",
                substring = true,
                ignoreCase = true,
            ).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `an exempt caller is told nothing about a limit that will never bind them`() {
        sheet(
            draft = BankRequestDraftState(kind = BankRequestKind.WITHDRAWAL, accountId = "a1", amount = "120000"),
            accounts = listOf(account(exempt = true)),
        )

        assertEquals(0, compose.onAllNodesWithTag(BANK_REQUEST_LIMIT_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `an account with no limit gets no line either`() {
        sheet(
            draft = BankRequestDraftState(kind = BankRequestKind.WITHDRAWAL, accountId = "a1", amount = "120000"),
            accounts = listOf(account(limit = null)),
        )

        assertEquals(0, compose.onAllNodesWithTag(BANK_REQUEST_LIMIT_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `an awaiting request names the approver class instead of counting votes`() {
        tab(BankRequestRow(request = request(), mine = false, actionable = true))

        compose.onNodeWithText("Wartet auf Kontoverantwortlichen", ignoreCase = true).assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("1 / 2", substring = true, ignoreCase = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `the KRT ladder escalates who approves, and the chip says so`() {
        tab(
            BankRequestRow(
                request = request(approver = BankRequestApprover.ORGANISATIONSLEITUNG),
                mine = false,
                actionable = true,
            ),
        )

        compose.onNodeWithText("Wartet auf Organisationsleitung", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a holder may approve but never reject`() {
        tab(BankRequestRow(request = request(), mine = false, actionable = true))

        compose.onNodeWithText("Freigabe erteilen", ignoreCase = true).assertIsDisplayed()
        // Rejecting is a bank employee's act on their own surface. The member surface has no
        // endpoint for it, so an ABLEHNEN button here would be a control that cannot work.
        assertEquals(0, compose.onAllNodesWithText("Ablehnen", ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Bestätigen", ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a granted approval can be taken back`() {
        tab(BankRequestRow(request = request(granted = true), mine = false, actionable = true))

        compose.onNodeWithText("Freigabe zurücknehmen", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Freigegeben von Vex", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the requester edits and withdraws, and approves nothing`() {
        tab(BankRequestRow(request = request(), mine = true, actionable = false))

        compose.onNodeWithText("Bearbeiten", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Zurückziehen", ignoreCase = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Freigabe erteilen", ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `an approved request of ones own can no longer be edited`() {
        tab(BankRequestRow(request = request(granted = true), mine = true, actionable = false))

        assertEquals(0, compose.onAllNodesWithText("Bearbeiten", ignoreCase = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Zurückziehen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a decided request offers no action at all`() {
        tab(BankRequestRow(request = request(status = BankRequestStatus.CONFIRMED), mine = true, actionable = false))

        compose.onNodeWithText("Bestätigt", ignoreCase = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Zurückziehen", ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Bearbeiten", ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a request needing no approval carries no approval chip`() {
        tab(
            BankRequestRow(
                request = request(requiresApproval = false, approver = null),
                mine = true,
                actionable = false,
            ),
        )

        compose.onNodeWithText("Eingereicht", ignoreCase = true).assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("Wartet auf", substring = true, ignoreCase = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `a withdrawal is shown as money leaving`() {
        tab(BankRequestRow(request = request(), mine = true, actionable = false))

        compose.onNodeWithText("−120.000", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the sheet overrules the server on a rejected booking`() {
        // This is the one refusal the sheet knows better than the server does. A booking request
        // has exactly two values that can be wrong, and naming both is a shorter path to the fix
        // than a constraint message about whichever one the validator reached first — so the
        // screen's sentence wins here, where most write surfaces defer to the server's.
        compose.setContent {
            KrtTheme {
                Text(
                    text =
                        bankRequestErrorMessage(
                            ApiError.Validation(
                                ProblemDetail(
                                    fieldErrors =
                                        listOf(ProblemFieldError("amount", "numerischer Wert außerhalb des Bereichs")),
                                ),
                            ),
                        ),
                )
            }
        }

        compose.onNodeWithText("Betrag oder Zielkonto sind nicht gültig.").assertIsDisplayed()
        compose.onAllNodesWithText("numerischer Wert außerhalb des Bereichs").assertCountEquals(0)
    }
}

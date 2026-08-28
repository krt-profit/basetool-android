/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankRequestKind
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffAccount
import de.greluc.krt.profit.basetool.android.core.data.BankStaffTotals
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the Verwaltung scope's Übersicht renders — design chapter 12, artboard 4.
 *
 * The chips are the point of the screen. Each answers a different question about an account the
 * caller can see only because of their office, and getting one of them wrong would either hide a
 * fact a bank employee needs or claim one that is not true.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BankStaffScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun account(
        id: String = "a1",
        type: String = "ORG_UNIT",
        status: BankAccountStatus = BankAccountStatus.ACTIVE,
    ) = BankStaffAccount(
        id = id,
        accountNo = "K-001",
        name = "Einsatzkasse",
        type = type,
        status = status,
        balance = "84200.0000",
        delta30d = "12400.0000",
        sparkline = emptyList(),
    )

    private fun render(
        row: BankStaffRow,
        totals: BankStaffTotals? = BankStaffTotals("84200.0000", 1, 0),
        openTotal: Int = 0,
        partial: Boolean = false,
        management: Boolean = true,
    ) {
        compose.setContent {
            KrtTheme {
                BankStaffOverview(
                    state =
                        BankStaffState(
                            rows = listOf(row),
                            totals = totals,
                            openRequestTotal = openTotal,
                            countsPartial = partial,
                            management = management,
                            phase = BankPhase.Ready,
                        ),
                    onRefresh = {},
                    onOpenAccount = {},
                )
            }
        }
    }

    @Test
    fun `the account everyone may see says so`() {
        render(BankStaffRow(account(type = "CARTEL"), openRequests = 0, viewable = true))

        compose.onNodeWithText("Für alle sichtbar", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `an ordinary account claims no such thing`() {
        render(BankStaffRow(account(), openRequests = 0, viewable = true))

        assertEquals(
            0,
            compose.onAllNodesWithText("Für alle sichtbar", ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `open requests are counted on the row that has them`() {
        render(BankStaffRow(account(), openRequests = 2, viewable = true))

        compose.onNodeWithText("2 offene Anträge", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a row with no open request carries no counter at all`() {
        render(BankStaffRow(account(), openRequests = 0, viewable = true))

        // The KPI line under the total always states the queue's size, so the phrase is on screen
        // exactly once. A second occurrence would be a chip on a row that has nothing waiting.
        assertEquals(
            1,
            compose.onAllNodesWithText("offene Anträge", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `an account reached only through the office is marked, for management`() {
        render(BankStaffRow(account(), openRequests = 0, viewable = false), management = true)

        compose.onNodeWithText("ohne eigenen View-Grant", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `an employee's list is already grant-shaped, so nothing is marked`() {
        render(
            row = BankStaffRow(account(), openRequests = 0, viewable = false),
            totals = null,
            management = false,
        )

        // Only management sees beyond their own grants. Marking every row for an employee would
        // say nothing at all.
        assertEquals(
            0,
            compose.onAllNodesWithText("View-Grant", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `an employee gets no aggregate strip, and no zeroes standing in for one`() {
        render(
            row = BankStaffRow(account(), openRequests = 0, viewable = true),
            totals = null,
            management = false,
        )

        // The server withholds the strip from anyone who is not Bank-Management (REQ-BANK-010).
        // Rendering zeroes would tell an employee the bank is empty.
        assertEquals(
            0,
            compose.onAllNodesWithText("Gesamt", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            compose.onAllNodesWithText("offene Anträge", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `a closed account states that instead of the rest`() {
        render(
            BankStaffRow(
                account(type = "CARTEL", status = BankAccountStatus.CLOSED),
                openRequests = 3,
                viewable = false,
            ),
        )

        compose.onNodeWithText("Geschlossen", ignoreCase = true).assertIsDisplayed()
        // A closed account takes no bookings, so its open-request count and its grant status are
        // noise beside the one fact that matters.
        assertEquals(
            0,
            compose.onAllNodesWithText("View-Grant", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `the counts line reads as a floor when the queue could not be walked out`() {
        render(
            row = BankStaffRow(account(), openRequests = 0, viewable = true),
            totals = BankStaffTotals("84200.0000", OPEN_ACCOUNTS, 1),
            openTotal = OPEN_REQUESTS,
            partial = true,
        )

        compose
            .onNodeWithText("mindestens 3 offene Anträge", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun `a complete count states the number plainly`() {
        render(
            row = BankStaffRow(account(), openRequests = 0, viewable = true),
            totals = BankStaffTotals("84200.0000", OPEN_ACCOUNTS, 1),
            openTotal = OPEN_REQUESTS,
            partial = false,
        )

        compose.onNodeWithText("3 offene Anträge", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("mindestens", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `the delta carries its sign, which colour alone would not say`() {
        render(BankStaffRow(account(), openRequests = 0, viewable = true))

        compose.onNodeWithText("+12.400", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the staff queue offers both decisions, which is right HERE`() {
        compose.setContent {
            KrtTheme {
                BankStaffQueue(
                    state = BankStaffState(queue = listOf(pendingRequest()), phase = BankPhase.Ready),
                    onRefresh = {},
                    actions = BankStaffQueueActions(onConfirm = {}, onReject = {}),
                )
            }
        }

        // Unlike artboard 1's member row, this surface really does have POST .../confirm and
        // .../reject behind it.
        compose.onNodeWithText("Bestätigen", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Ablehnen", ignoreCase = true).assertIsDisplayed()
        // And still no counter.
        assertEquals(
            0,
            compose.onAllNodesWithText("/ 2", substring = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `a truncated queue says so instead of ending silently`() {
        compose.setContent {
            KrtTheme {
                BankStaffQueue(
                    state =
                        BankStaffState(
                            queue = listOf(pendingRequest()),
                            countsPartial = true,
                            phase = BankPhase.Ready,
                        ),
                    onRefresh = {},
                    actions = BankStaffQueueActions(onConfirm = {}, onReject = {}),
                )
            }
        }

        compose
            .onNodeWithText("gekürzt", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    /**
     * One undecided request against an account.
     *
     * @return the request.
     */
    private fun pendingRequest() =
        BankBookingRequest(
            id = "r1",
            accountId = "a1",
            accountName = "Einsatzkasse",
            targetAccountId = null,
            kind = BankRequestKind.WITHDRAWAL,
            amount = "120000.0000",
            note = "Auszahlung Operation Rotschild",
            status = BankRequestStatus.PENDING,
            requester = "Rhea",
            rejectReason = null,
            applicableLimit = null,
            requiresOwnerApproval = false,
            ownerApprovalGranted = false,
            ownerApprovalBy = null,
            requiredApprover = null,
            createdAt = "2026-08-01T00:00:00Z",
            version = 1,
        )

    private companion object {
        /** Four open accounts beside one closed one, so the counts line has all three parts. */
        const val OPEN_ACCOUNTS = 4L

        /** How many requests the queue holds in the two counts-line tests. */
        const val OPEN_REQUESTS = 3
    }
}

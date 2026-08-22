/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BankAccountDetail
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * What the bank renders.
 *
 * The assertion that matters most is the sign: the ledger stores magnitudes, and a withdrawal shown
 * as a deposit is a member reading their account backwards.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BankScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun account(id: String = "a1") =
        BankAccountSummary(
            id = id,
            accountNo = "K-001",
            name = "Einsatzkasse",
            orgUnitName = "Bereich Profit",
            balance = "84200.0000",
            delta30d = "12400.0000",
            sparkline = SPARKLINE,
        )

    private fun booking(
        id: String,
        type: String,
        amount: String = "12400.0000",
        note: String? = "Verkauf Quantainium",
    ) = BankBooking(
        id = id,
        type = type,
        amount = amount,
        note = note,
        holder = "Rhea",
        createdAt = Instant.parse("2026-08-22T10:00:00Z"),
    )

    /**
     * Renders the Konten list.
     *
     * @param state what to draw.
     * @param opened receives the id of a tapped card.
     */
    private fun showAccounts(
        state: BankAccountsState,
        opened: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                BankAccountsScreen(state = state, onRefresh = {}, onOpenAccount = { opened.add(it) })
            }
        }
    }

    /**
     * Renders one account.
     *
     * @param state what to draw.
     */
    private fun showAccount(state: BankAccountState) {
        compose.setContent {
            KrtTheme { BankAccountScreen(state = state, onRefresh = {}, onLoadMore = {}) }
        }
    }

    @Test
    fun `an account card states its name and its balance, grouped`() {
        showAccounts(BankAccountsState(accounts = listOf(account()), phase = BankPhase.Ready))

        compose.onNodeWithText("Einsatzkasse").assertIsDisplayed()
        compose.onNodeWithText("84.200").assertIsDisplayed()
        compose.onNodeWithTag(BANK_ACCOUNTS_TAG).assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens that account`() {
        val opened = mutableListOf<String>()
        showAccounts(
            BankAccountsState(
                accounts = listOf(account("a1"), account("a2").copy(name = "CARTEL")),
                phase = BankPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("CARTEL").performClick()

        assertEquals(listOf("a2"), opened)
    }

    @Test
    fun `no visible account is stated as a fact`() {
        // A member without a view grant sees only what is public to everyone, which can be nothing.
        showAccounts(BankAccountsState(phase = BankPhase.Ready))

        compose.onNodeWithText("Keine Konten").assertIsDisplayed()
    }

    @Test
    fun `a failed list offers a retry`() {
        showAccounts(BankAccountsState(phase = BankPhase.Failed(ApiError.Network(IOException("x")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the ledger signs a line by its kind, not by its digits`() {
        showAccount(
            BankAccountState(
                accountId = "a1",
                account =
                    BankAccountDetail("a1", "K-001", "Einsatzkasse", "84200.0000", "12400.0000", TWO),
                bookings =
                    listOf(
                        booking("p1", "DEPOSIT"),
                        booking("p2", "WITHDRAWAL", amount = "3200.0000", note = "Treibstoff"),
                    ),
                bookingTotal = TWO,
                phase = BankPhase.Ready,
            ),
        )

        compose.onNodeWithText("+12.400").assertIsDisplayed()
        compose.onNodeWithText("−3.200").assertIsDisplayed()
    }

    @Test
    fun `a kind this build does not know renders without a sign`() {
        // Better an unsigned figure than a direction nobody checked.
        showAccount(
            BankAccountState(
                accountId = "a1",
                account = BankAccountDetail("a1", "K-001", "Einsatzkasse", "1.0", null, ONE),
                bookings = listOf(booking("p3", "SOMETHING_NEW", amount = "500.0000", note = null)),
                bookingTotal = ONE,
                phase = BankPhase.Ready,
            ),
        )

        compose.onNodeWithText("500").assertIsDisplayed()
    }

    @Test
    fun `an empty ledger says so rather than leaving the section blank`() {
        showAccount(
            BankAccountState(
                accountId = "a1",
                account = BankAccountDetail("a1", "K-001", "Einsatzkasse", "0.0000", null, 0),
                phase = BankPhase.Ready,
            ),
        )

        compose.onNodeWithText("Noch keine Buchungen.").assertIsDisplayed()
    }

    @Test
    fun `a refused account is worded as a refusal`() {
        showAccount(BankAccountState(accountId = "a1", phase = BankPhase.Failed(ApiError.Forbidden())))

        compose.onNodeWithText("Access Denied").assertIsDisplayed()
        compose.onNodeWithText("Dieses Konto ist für dich nicht einsehbar.").assertIsDisplayed()
    }

    private companion object {
        /** Three points, enough for the line to be drawn at all. */
        val SPARKLINE = listOf(1.0, 2.5, 2.0)

        /** Two ledger lines. */
        const val TWO = 2L

        /** One ledger line. */
        const val ONE = 1L
    }
}

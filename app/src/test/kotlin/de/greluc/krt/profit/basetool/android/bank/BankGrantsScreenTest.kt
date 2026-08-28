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
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.BankAccountStatus
import de.greluc.krt.profit.basetool.android.core.data.BankGrant
import de.greluc.krt.profit.basetool.android.core.data.BankManagedAccount
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the grants matrix renders — design chapter 12, artboard 7.
 *
 * The matrix is where a drawing and the server disagree, so the tests pin the server's shape: three
 * capability rows and no approval one, and a standing that is taken away by removing the entry
 * rather than by clearing a fourth box. The locked variant is pinned too — artboard 6's handoff
 * asks for the chapter-09 padlock, and hiding the controls instead would pass a naive test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class BankGrantsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun account(
        id: String = "a1",
        name: String = "Einsatzkasse",
        type: String = "ORG_UNIT",
    ) = BankManagedAccount(
        id = id,
        accountNo = "K-001",
        name = name,
        type = type,
        status = BankAccountStatus.ACTIVE,
        balance = "84200.0000",
        orgUnitName = "Staffel Iridium",
        version = 3,
    )

    private fun grant(
        canDeposit: Boolean = true,
        canWithdraw: Boolean = false,
        canTransfer: Boolean = false,
    ) = BankGrant(
        userId = "u1",
        handle = "Rhea",
        accountId = "a1",
        canDeposit = canDeposit,
        canWithdraw = canWithdraw,
        canTransfer = canTransfer,
        version = 2,
    )

    private fun render(
        grants: List<BankGrant> = listOf(grant()),
        accountId: String? = "a1",
        management: Boolean = true,
        type: String = "ORG_UNIT",
        actions: BankGrantsActions = BankGrantsActions({}, {}, {}, {}, {}),
    ) {
        compose.setContent {
            KrtTheme {
                BankGrantsTab(
                    state =
                        BankLifecycleState(
                            phase = BankPhase.Ready,
                            grantAccountId = accountId,
                            grants = grants,
                        ),
                    accounts = listOf(account(type = type)),
                    management = management,
                    actions = actions,
                )
            }
        }
    }

    @Test
    fun `a standing is three capabilities, and approving is not one of them`() {
        render()

        compose.onNodeWithText("Einzahlen").assertIsDisplayed()
        compose.onNodeWithText("Auszahlen").assertIsDisplayed()
        compose.onNodeWithText("Transfer").assertIsDisplayed()
        // The artboard draws a „FREIGEBEN" column. There is no such flag: who may approve a
        // request is decided per request by `requiredApprover`, never per member.
        assertEquals(
            0,
            compose.onAllNodesWithText("Freigeben", ignoreCase = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `the screen says in plain text that the entry alone is the view grant`() {
        render()

        // Not a tooltip: a member with every box unticked can still see the account, and burying
        // that behind a long-press would make the matrix read as "no rights at all".
        compose
            .onNodeWithText("Wer hier steht, darf das Konto sehen", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `taking sight away is offered as removing the entry`() {
        render()

        compose.onNodeWithText("Eintrag entfernen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `without Bank-Management the controls are drawn locked rather than hidden`() {
        var locked = 0
        val changed = mutableListOf<BankGrant>()
        val revoked = mutableListOf<BankLifecyclePrompt.RevokeGrant>()
        render(
            management = false,
            actions =
                BankGrantsActions(
                    onSelectAccount = {},
                    onSetGrant = { changed.add(it) },
                    onRevoke = { revoked.add(it) },
                    onLocked = { locked++ },
                    onAdd = {},
                ),
        )

        compose.onNodeWithText("Einzahlen").performClick()
        compose.onNodeWithText("Eintrag entfernen", ignoreCase = true).performClick()

        assertEquals(2, locked)
        assertTrue(changed.isEmpty())
        assertTrue(revoked.isEmpty())
    }

    @Test
    fun `before an account is picked the matrix says so instead of showing an empty one`() {
        render(accountId = null, grants = emptyList())

        compose.onNodeWithText("Wähle ein Konto", substring = true).assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("Einzahlen").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `an account with nobody on it says that, rather than rendering nothing`() {
        render(grants = emptyList())

        compose.onNodeWithText("Für dieses Konto ist niemand eingetragen.").assertIsDisplayed()
    }

    @Test
    fun `removing an entry asks first, and says the sight goes with it`() {
        val asked = mutableListOf<BankLifecyclePrompt.RevokeGrant>()
        render(
            actions =
                BankGrantsActions(
                    onSelectAccount = {},
                    onSetGrant = {},
                    onRevoke = { asked.add(it) },
                    onLocked = {},
                    onAdd = {},
                ),
        )

        compose.onNodeWithText("Eintrag entfernen", ignoreCase = true).performClick()

        // The tab raises a confirmation rather than writing: on an ordinary account the removal
        // takes the member's sight of it away, which no checkbox on the card mentions.
        assertEquals(1, asked.size)
        assertEquals(false, asked.single().sightSurvives)
    }

    @Test
    fun `on the KRT account the entry never carried the sight, and the copy does not claim it`() {
        val asked = mutableListOf<BankLifecyclePrompt.RevokeGrant>()
        render(
            type = "CARTEL",
            actions =
                BankGrantsActions(
                    onSelectAccount = {},
                    onSetGrant = {},
                    onRevoke = { asked.add(it) },
                    onLocked = {},
                    onAdd = {},
                ),
        )

        // REQ-BANK-037: every KRT member sees this account by rule. Promising to revoke sight here
        // would be a promise the server does not keep.
        compose.onNodeWithText("sieht jedes Mitglied ohnehin", substring = true).assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("Sehen entziehst du", substring = true)
                .fetchSemanticsNodes().size,
        )

        compose.onNodeWithText("Eintrag entfernen", ignoreCase = true).performClick()
        assertTrue(asked.single().sightSurvives)
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrder
import de.greluc.krt.profit.basetool.android.core.data.RefineryServerStatus
import de.greluc.krt.profit.basetool.android.core.data.RefineryYield
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/**
 * Tests what the Raffinerie renders: the remaining time rounds up so „noch 0 Min." never stands in for ready, and „In
 * Lager buchen" appears only once the run has ended.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class RefineryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private companion object {
        val BEFORE: OffsetDateTime = OffsetDateTime.parse("2026-08-17T01:00:00Z")
        val AFTER: OffsetDateTime = OffsetDateTime.parse("2026-08-17T12:00:00Z")

        /** 40 seconds before the end — the case that must not read as „noch 0 Min.". */
        val ALMOST: OffsetDateTime = OffsetDateTime.parse("2026-08-17T03:40:20Z")
    }

    private fun order(
        id: String = "r1",
        status: RefineryServerStatus = RefineryServerStatus.IN_PROGRESS,
        materialId: String? = "m1",
    ) = RefineryOrder(
        id = id,
        ownerId = "u1",
        ownerName = "Rhea",
        locationId = "loc1",
        locationName = "ARC-L1 Wide Forest",
        methodName = "Dinyx-Solventierung",
        startedAt = "2026-08-16T22:41:00Z",
        endsAt = "2026-08-17T03:41:00Z",
        status = status,
        yields =
            listOf(
                RefineryYield(
                    materialId = materialId,
                    materialName = "Quantainium",
                    amount = 622.0,
                    unitIsPiece = false,
                    quality = 3,
                ),
            ),
        oreSales = "96900",
        profit = "84200",
        version = 2,
    )

    /**
     * Renders the list.
     *
     * @param now the clock.
     * @param filter the active chip.
     */
    private fun list(
        now: OffsetDateTime,
        filter: RefineryFilter = RefineryFilter.ALL,
        myUserId: String? = null,
        orders: List<RefineryOrder> = listOf(order()),
    ) {
        compose.setContent {
            KrtTheme {
                RefineryOrdersScreen(
                    state =
                        RefineryListState(
                            filter = filter,
                            loaded = orders,
                            phase = RefineryPhaseState.Ready,
                            myUserId = myUserId,
                            now = now,
                        ),
                    onFilterChanged = {},
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    onOpenOrder = {},
                )
            }
        }
    }

    /**
     * Every card names its owner, since the screen lists the whole unit's orders.
     */
    @Test
    fun `every card names its owner`() {
        list(now = BEFORE)

        compose.onNodeWithText("Rhea").assertIsDisplayed()
        compose.onNodeWithText("Dinyx-Solventierung").assertIsDisplayed()
    }

    /** The caller's own row says so, so finding yourself in a list of fourteen is a glance. */
    @Test
    fun `the caller's own order carries the du suffix`() {
        list(now = BEFORE, myUserId = "u1")

        compose.onNodeWithText("Rhea (du)").assertIsDisplayed()
    }

    /** And without an identity nobody is claimed — the name stands plain, wrong about nobody. */
    @Test
    fun `an unresolved identity claims no order`() {
        list(now = BEFORE, myUserId = null)

        compose.onNodeWithText("Rhea").assertIsDisplayed()
        compose.onAllNodesWithText("Rhea (du)").assertCountEquals(0)
    }

    /**
     * „Aktiv" is the default and its empty state names what it is hiding.
     *
     * A member whose runs are all booked would otherwise meet „für diesen Filter liegt nichts vor"
     * on a screen that silently drops everything they have — the no-silent-caps rule applied to a
     * filter rather than a page.
     */
    @Test
    fun `the active filter's empty state names the way out`() {
        list(
            now = AFTER,
            filter = RefineryFilter.ACTIVE,
            orders = listOf(order(status = RefineryServerStatus.COMPLETED)),
        )

        compose.onNodeWithText("Keine laufenden Aufträge").assertIsDisplayed()
        compose.onNodeWithText("Eingelagert", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a running order counts down and a finished one says it is ready`() {
        list(now = BEFORE)

        compose.onNodeWithTag(REFINERY_LIST_TAG).assertIsDisplayed()
        assertPhase("In Arbeit")
        compose.onNodeWithText("Dinyx-Solventierung").assertIsDisplayed()
        compose.onNodeWithText("noch 2 Std. 41 Min.").assertIsDisplayed()
    }

    @Test
    fun `forty seconds left reads as one minute, never as zero`() {
        list(now = ALMOST)

        compose.onNodeWithText("noch 1 Minute").assertIsDisplayed()
    }

    @Test
    fun `a finished run says which method produced it`() {
        list(now = AFTER)

        assertPhase("Abholbereit")
        compose.onNodeWithText("Dinyx-Solventierung").assertIsDisplayed()
    }

    @Test
    fun `a running order offers no booking`() {
        detail(now = BEFORE)

        compose.onNodeWithTag(REFINERY_STORE_TAG).assertDoesNotExist()
    }

    @Test
    fun `a finished run offers the booking`() {
        detail(now = AFTER)

        compose.onNodeWithTag(REFINERY_STORE_TAG).assertIsDisplayed()
    }

    @Test
    fun `the confirmation names how many Lager entries it will create`() {
        var confirmed = 0
        compose.setContent {
            KrtTheme {
                RefineryOrderDetailScreen(
                    state =
                        RefineryDetailState(
                            orderId = "r1",
                            order = order(),
                            phase = RefineryDetailPhase.Ready,
                            confirming = true,
                            now = AFTER,
                        ),
                    onRefresh = {},
                    onRetryNow = {},
                    onStoreRequested = {},
                    onStoreConfirmed = { confirmed++ },
                    onStoreDismissed = {},
                )
            }
        }

        compose.onNodeWithTag(REFINERY_STORE_CONFIRM_TAG).assertIsDisplayed()
        compose.onNodeWithText("BUCHEN").performClick()
        assertEquals(1, confirmed)
    }

    /**
     * Asserts the row's status pill reads [label].
     *
     * @param label the uppercase status text.
     */
    private fun assertPhase(label: String) {
        compose
            .onNode(
                hasTestTag(REFINERY_PHASE_TAG) and hasAnyDescendant(hasText(label)),
                useUnmergedTree = true,
            ).assertExists()
    }

    /**
     * Renders the detail.
     *
     * @param now the clock.
     */
    private fun detail(now: OffsetDateTime) {
        compose.setContent {
            KrtTheme {
                RefineryOrderDetailScreen(
                    state =
                        RefineryDetailState(
                            orderId = "r1",
                            order = order(),
                            phase = RefineryDetailPhase.Ready,
                            now = now,
                        ),
                    onRefresh = {},
                    onRetryNow = {},
                    onStoreRequested = {},
                    onStoreConfirmed = {},
                    onStoreDismissed = {},
                )
            }
        }
    }
}

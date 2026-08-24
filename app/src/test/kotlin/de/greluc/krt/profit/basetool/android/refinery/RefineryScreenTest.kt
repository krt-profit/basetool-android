/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
 * What the Raffinerie renders.
 *
 * The assertions that matter are the ones a member would act on: the remaining time is rounded up
 * so „noch 0 Min." never stands in for „ready", and „In Lager buchen" is absent until the run has
 * actually ended.
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
                    amount = 622,
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
    ) {
        compose.setContent {
            KrtTheme {
                RefineryOrdersScreen(
                    state =
                        RefineryListState(
                            filter = filter,
                            loaded = listOf(order()),
                            phase = RefineryPhaseState.Ready,
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

    @Test
    fun `a running order counts down and a finished one says it is ready`() {
        list(now = BEFORE)

        compose.onNodeWithTag(REFINERY_LIST_TAG).assertIsDisplayed()
        // By tag, not by text: the chip row says „In Arbeit" too, and the design system uppercases
        // both — matching on the words would pass while showing the wrong element.
        // By tag AND descendant text, on the unmerged tree. Two reasons, both learned here: the row
        // is clickable, so Compose merges its children into the row node and the pill's tag is
        // gone from the merged tree; and the tag sits on the pill's layout, whose label is a
        // child, so the tagged node carries no text of its own.
        assertPhase("IN ARBEIT")
        compose.onNodeWithText("Dinyx-Solventierung · noch 2 Std. 41 Min.").assertIsDisplayed()
    }

    @Test
    fun `forty seconds left reads as one minute, never as zero`() {
        // Rounding down would show "noch 0 Minuten" for a whole minute. That reads as ready, and
        // a member who walks over finds it still refining. The singular is the plural resource
        // doing its job — Android Lint rejects a bare "%d Min." and it is right to.
        list(now = ALMOST)

        compose.onNodeWithText("Dinyx-Solventierung · noch 1 Minute").assertIsDisplayed()
    }

    @Test
    fun `a finished run leads with its yield rather than a time`() {
        list(now = AFTER)

        assertPhase("ABHOLBEREIT")
        compose.onNodeWithText("Dinyx-Solventierung · 622 SCU").assertIsDisplayed()
    }

    @Test
    fun `a running order offers no booking`() {
        detail(now = BEFORE)

        // Absent rather than disabled: chapter 11 puts the action at the foot of the screen, and a
        // greyed button there invites a member to keep tapping it. Booking an unfinished run books
        // a yield that does not exist.
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

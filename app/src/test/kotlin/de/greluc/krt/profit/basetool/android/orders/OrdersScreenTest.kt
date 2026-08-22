/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.data.JobOrderStatus
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
 * What the Auftrag queue and one order render.
 *
 * The assertion that carries the most: a redacted order says so. A member reading a reduced order as
 * a complete one is the failure REQ-ORDERS-023 exists to prevent, and it is invisible unless the
 * screen states it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class OrdersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun order(
        id: String = "o1",
        displayId: String = "1042",
        status: JobOrderStatus = JobOrderStatus.IN_PROGRESS,
        redacted: Boolean = false,
        materials: List<JobOrderMaterial> = listOf(material()),
    ) = JobOrder(
        id = id,
        displayId = displayId,
        status = status,
        rawStatus = status.name,
        priority = 1,
        type = "MATERIAL",
        requestingOrgUnit = "Staffel 1",
        responsibleOrgUnit = "SK Vanguard",
        comment = "Qualität ist zweitrangig.",
        materials = materials,
        handovers = emptyList(),
        assignees = listOf("Vex"),
        createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        redacted = redacted,
    )

    private fun material() =
        JobOrderMaterial(
            name = "Quantainium",
            needed = "500.0",
            inStock = "125.0",
            claimCount = 1,
            open = "325.0",
        )

    /**
     * Renders the queue.
     *
     * @param state what to draw.
     * @param opened receives the id of a tapped row.
     * @param toggled receives the id of a row whose material list was tapped.
     */
    private fun showQueue(
        state: OrdersState,
        opened: MutableList<String> = mutableListOf(),
        toggled: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                OrdersScreen(
                    state = state,
                    onStatusToggled = {},
                    onToggleMaterials = { toggled.add(it) },
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenOrder = { opened.add(it) },
                )
            }
        }
    }

    /**
     * Renders one order.
     *
     * @param state what to draw.
     */
    private fun showDetail(state: OrderDetailState) {
        compose.setContent { KrtTheme { OrderDetailScreen(state = state, onRefresh = {}) } }
    }

    @Test
    fun `a queue row leads with its number and its parties`() {
        showQueue(OrdersState(orders = listOf(order()), total = 1, phase = OrdersPhase.Ready))

        compose.onNodeWithText("#1042").assertIsDisplayed()
        compose.onNodeWithText("Staffel 1", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ORDERS_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the material list is collapsed until its own control is tapped`() {
        // Collapsed by default, as the web app has it, and on a tap target of its own so opening
        // the list and opening the order cannot be confused.
        val toggled = mutableListOf<String>()
        showQueue(
            OrdersState(orders = listOf(order()), total = 1, phase = OrdersPhase.Ready),
            toggled = toggled,
        )

        compose.onAllNodesWithText("Quantainium").assertCountEquals(0)
        compose.onNodeWithText("1 Material").performClick()

        assertEquals(listOf("o1"), toggled)
    }

    @Test
    fun `an expanded row shows its materials with stock over need`() {
        showQueue(
            OrdersState(
                orders = listOf(order()),
                total = 1,
                phase = OrdersPhase.Ready,
                expanded = setOf("o1"),
            ),
        )

        compose.onNodeWithText("Quantainium").assertIsDisplayed()
        compose.onNodeWithText("125 / 500").assertIsDisplayed()
    }

    @Test
    fun `tapping a row opens that order`() {
        val opened = mutableListOf<String>()
        showQueue(
            OrdersState(
                orders = listOf(order(), order(id = "o2", displayId = "1043")),
                total = 2,
                phase = OrdersPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("#1043").performClick()

        assertEquals(listOf("o2"), opened)
    }

    @Test
    fun `an empty queue is stated`() {
        showQueue(OrdersState(phase = OrdersPhase.Ready))

        compose.onNodeWithText("Keine Aufträge").assertIsDisplayed()
    }

    @Test
    fun `a failed queue offers a retry`() {
        showQueue(OrdersState(phase = OrdersPhase.Failed(ApiError.Network(IOException("x")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a redacted order says so`() {
        // Otherwise a requester reads a reduced order as the whole one (REQ-ORDERS-023).
        showDetail(
            OrderDetailState(
                orderId = "o1",
                order = order(redacted = true),
                phase = OrderDetailPhase.Ready,
            ),
        )

        compose.onNodeWithText("Teile dieses Auftrags sind für dich ausgeblendet.").assertIsDisplayed()
    }

    @Test
    fun `an order that is not redacted says nothing about it`() {
        showDetail(
            OrderDetailState(orderId = "o1", order = order(), phase = OrderDetailPhase.Ready),
        )

        compose.onAllNodesWithText("Teile dieses Auftrags sind für dich ausgeblendet.")
            .assertCountEquals(0)
    }

    @Test
    fun `the detail shows the comment, the materials and who is on it`() {
        showDetail(
            OrderDetailState(orderId = "o1", order = order(), phase = OrderDetailPhase.Ready),
        )

        compose.onNodeWithText("Qualität ist zweitrangig.").assertIsDisplayed()
        compose.onNodeWithText("Quantainium").assertIsDisplayed()
        compose.onNodeWithText("Vex").assertIsDisplayed()
        compose.onNodeWithTag(ORDER_DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `an order with no handovers says so rather than leaving the section blank`() {
        showDetail(
            OrderDetailState(orderId = "o1", order = order(), phase = OrderDetailPhase.Ready),
        )

        compose.onNodeWithText("Noch keine Übergabe erfasst.").assertIsDisplayed()
    }

    @Test
    fun `a refused order is worded as a refusal`() {
        showDetail(OrderDetailState(orderId = "o1", phase = OrderDetailPhase.Failed(ApiError.Forbidden())))

        compose.onNodeWithText("Access Denied").assertIsDisplayed()
        compose.onNodeWithText("Dieser Auftrag ist für dich nicht einsehbar.").assertIsDisplayed()
    }
}

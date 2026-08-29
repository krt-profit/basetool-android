/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.JobOrder
import de.greluc.krt.profit.basetool.android.core.data.JobOrderAssignee
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
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
    private companion object {
        /** The order's optimistic lock, as the fixtures carry it. */
        const val ORDER_VERSION = 3L

        /** The assignee edge's own version, deliberately a different number. */
        const val EDGE_VERSION = 7L

        /** How many of the item the fixture's line asks for. */
        const val ITEM_AMOUNT = 3
    }

    @get:Rule
    val compose = createComposeRule()

    private fun order(
        id: String = "o1",
        displayId: String = "1042",
        status: JobOrderStatus = JobOrderStatus.IN_PROGRESS,
        redacted: Boolean = false,
        materials: List<JobOrderMaterial> = listOf(material()),
        items: List<JobOrderItem> = emptyList(),
    ) = JobOrder(
        id = id,
        displayId = displayId,
        status = status,
        rawStatus = status.name,
        priority = 1,
        type = "MATERIAL",
        requestingOrgUnit = "Staffel 1",
        responsibleOrgUnit = "SK Vanguard",
        responsibleOrgUnitId = null,
        comment = "Qualität ist zweitrangig.",
        materials = materials,
        items = items,
        handovers = emptyList(),
        assignees = emptyList(),
        createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        version = ORDER_VERSION,
        redacted = redacted,
    )

    private fun material() =
        JobOrderMaterial(
            materialId = "m1",
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
        refreshed: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                OrdersScreen(
                    state = state,
                    onStatusToggled = {},
                    onToggleMaterials = { toggled.add(it) },
                    onRefresh = { refreshed.add(Unit) },
                    onRetryNow = {},
                    onLoadMore = {},
                    onOpenOrder = { opened.add(it) },
                    onCreate = {},
                )
            }
        }
    }

    /**
     * Renders one order.
     *
     * @param state what to draw.
     */
    private fun showDetail(
        state: OrderDetailState,
        assigned: MutableList<Unit> = mutableListOf(),
        statuses: MutableList<JobOrderStatus> = mutableListOf(),
        notes: MutableList<String> = mutableListOf(),
        produced: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                OrderDetailScreen(
                    state = state,
                    handover =
                        OrderHandoverActions(
                            draft = null,
                            onChange = {},
                            onSubmit = {},
                            onDismiss = {},
                        ),
                    production =
                        OrderProductionActions(
                            draft = null,
                            onAmount = {},
                            onDraw = { _, _, _ -> },
                            onSkip = {},
                            onAutoFill = {},
                            bookIn =
                                ProductionBookInActions(
                                    onLocationQuery = {},
                                    onLocation = { _, _ -> },
                                    onOwnerQuery = {},
                                    onOwner = { _, _ -> },
                                    onOrgUnit = {},
                                    onPersonal = {},
                                    onAllocate = {},
                                ),
                            onSubmit = {},
                            onDismiss = {},
                        ),
                    onRefresh = {},
                    onRetryNow = {},
                    actions =
                        OrderDetailActions(
                            onToggleAssignment = { assigned.add(Unit) },
                            onEditNote = { notes.add("open") },
                            onNoteChanged = { notes.add(it) },
                            onSaveNote = { notes.add("save") },
                            onDismissNote = {},
                            onReapplyRejectedNote = { notes.add("reapply") },
                            onOpenStatusPicker = { statuses.add(JobOrderStatus.UNKNOWN) },
                            onStatusChosen = { statuses.add(it) },
                            onDismissStatusPicker = {},
                            onStatusSelected = { statuses.add(it) },
                            onApplyStatus = {},
                            onDismissStatusConfirm = {},
                            onRaisePriority = {},
                            onLowerPriority = {},
                            onTabSelected = {},
                            onRecordHandover = {},
                            onRecordProduction = { produced.add(it.id.orEmpty()) },
                        ),
                )
            }
        }
    }

    /**
     * A member without the grant still sees the Herstellung — and is told what to ask for.
     *
     * Hiding the control was the alternative and is what this project's gate rule forbids: roles
     * here are handed out by a person, and a feature nobody can see is a feature nobody requests
     * (ADR-0011). So the button is drawn, it takes the tap, it writes nothing, and it names the
     * role.
     */
    @Test
    fun `the production action is drawn for a member who may not use it`() {
        val produced = mutableListOf<String>()
        showDetail(
            OrderDetailState(
                orderId = "o1",
                order = order(items = listOf(itemLine())),
                phase = OrderDetailPhase.Ready,
                me = Identity("u1", logistician = false),
            ),
            produced = produced,
        )

        compose.onNodeWithTag(ORDER_PRODUCTION_OPEN_TAG).assertIsDisplayed().performClick()

        assertEquals(emptyList<String>(), produced)
        compose.onNodeWithText(
            "Dafür brauchst du die Rolle Logistiker.",
            substring = true,
        ).assertIsDisplayed()
    }

    /** And a Logistician's tap opens the sheet for that line. */
    @Test
    fun `a logistician's tap opens the production sheet for the line`() {
        val produced = mutableListOf<String>()
        showDetail(
            OrderDetailState(
                orderId = "o1",
                order = order(items = listOf(itemLine())),
                phase = OrderDetailPhase.Ready,
                me = Identity("u1", logistician = true),
            ),
            produced = produced,
        )

        compose.onNodeWithTag(ORDER_PRODUCTION_OPEN_TAG).performClick()

        assertEquals(listOf("i1"), produced)
    }

    /** A line with nothing left to build carries no production action — that is not a lock. */
    @Test
    fun `a finished line offers no production action`() {
        showDetail(
            OrderDetailState(
                orderId = "o1",
                order = order(items = listOf(itemLine(manufactured = ITEM_AMOUNT))),
                phase = OrderDetailPhase.Ready,
                me = Identity("u1", logistician = true),
            ),
        )

        compose.onAllNodesWithTag(ORDER_PRODUCTION_OPEN_TAG).assertCountEquals(0)
    }

    /**
     * One item line of an order.
     *
     * @param manufactured how many are already built.
     * @return the line.
     */
    private fun itemLine(manufactured: Int = 0) =
        JobOrderItem(
            id = "i1",
            name = "Ballistic Gatling",
            blueprintName = "Gatling — Standard",
            amount = ITEM_AMOUNT,
            manufactured = manufactured,
            delivered = 0,
            blueprintStale = false,
            version = ORDER_VERSION,
        )

    /**
     * One member on an order.
     *
     * @param userId who, by id.
     * @param note their note, or `null`.
     * @return the row.
     */
    private fun assignee(
        userId: String = "u1",
        note: String? = null,
    ) = JobOrderAssignee(userId = userId, name = "Rhea", note = note, version = EDGE_VERSION)

    @Test
    fun `a queue card carries everything the design puts on it`() {
        // Design ch. 10 draws a card, not a line: the queue is scanned for priority, kind, age and
        // who owns the work, and an earlier revision showed only the number, a Prio chip and one
        // muted sentence. Each assertion below is one thing that was missing from it.
        showQueue(OrdersState(orders = listOf(order()), total = 1, phase = OrdersPhase.Ready))

        compose.onNodeWithText("#1042").assertIsDisplayed()
        // The priority block: the figure the queue is sorted by, and its label underneath.
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("Prio").assertIsDisplayed()
        // The kind chip, which the card did not show at all. Uppercased by the chip, like the
        // badges below it.
        compose.onNodeWithText("MATERIAL").assertIsDisplayed()
        // Both parties as org badges. Uppercased by the badge, which is how the design draws them
        // and why asserting the raw "Staffel 1" would now be asserting the wrong thing.
        compose.onNodeWithText("STAFFEL 1").assertIsDisplayed()
        compose.onNodeWithText("SK VANGUARD").assertIsDisplayed()
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
        compose.onNodeWithText("Materialien").performClick()

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
    fun `a quantity the server did not send reads as a dash`() {
        // Left empty it rendered as " / 500", which looks like a rendering fault rather than an
        // absent number — found on a device, on an order for a material nothing is stocked of.
        showQueue(
            OrdersState(
                orders = listOf(order(materials = listOf(material().copy(inStock = null)))),
                total = 1,
                phase = OrdersPhase.Ready,
                expanded = setOf("o1"),
            ),
        )

        compose.onNodeWithText("— / 500").assertIsDisplayed()
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
    fun `an empty queue can still be pulled to refresh`() {
        // PullToRefreshBox hears the gesture through nested scroll, so an empty screen with nothing
        // to scroll swallowed the pull entirely — on a device the queue looked frozen at exactly the
        // moment a member wants to re-read it.
        val refreshed = mutableListOf<Unit>()
        showQueue(OrdersState(phase = OrdersPhase.Ready), refreshed = refreshed)

        compose.onRoot().performTouchInput { swipeDown() }
        compose.waitForIdle()

        assertEquals(1, refreshed.size)
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
            OrderDetailState(
                orderId = "o1",
                order = order().copy(assignees = listOf(assignee(note = "Nachtschicht"))),
                phase = OrderDetailPhase.Ready,
            ),
        )

        // The comment and the materials are the Positionen tab; who is on it is its own tab
        // (design ch. 10 artboard 2). Asserting all four on one screen asserted a layout this
        // screen deliberately no longer has.
        compose.onNodeWithText("Qualität ist zweitrangig.").assertIsDisplayed()
        compose.onNodeWithText("Quantainium").assertIsDisplayed()
        compose.onNodeWithTag(ORDER_DETAIL_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("ZUSTÄNDIG", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun `an order with no handovers says so rather than leaving the section blank`() {
        showDetail(
            OrderDetailState(
                orderId = "o1",
                order = order(),
                phase = OrderDetailPhase.Ready,
                tab = OrderTab.HANDOVERS,
            ),
        )

        compose.onNodeWithText("Noch keine Übergabe erfasst.").assertIsDisplayed()
    }

    @Test
    fun `a refused order is worded as a refusal`() {
        showDetail(OrderDetailState(orderId = "o1", phase = OrderDetailPhase.Failed(ApiError.Forbidden())))

        compose.onNodeWithText("Access Denied").assertIsDisplayed()
        compose.onNodeWithText("Dieser Auftrag ist für dich nicht einsehbar.").assertIsDisplayed()
    }

    @Test
    fun `an order the caller is not on offers to take it on`() {
        val taken = mutableListOf<Unit>()
        showDetail(ready(), assigned = taken)

        compose.onNodeWithTag(ORDER_ASSIGN_TAG).assertIsEnabled().performClick()

        assertEquals(1, taken.size)
        compose.onNodeWithText("Übernehmen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `an order the caller is on offers to step off`() {
        showDetail(ready(assignees = listOf(assignee())))

        compose.onNodeWithText("Abmelden", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `only the caller's own row offers the note`() {
        // Someone else's note is theirs to write. Offering an action that would be refused is how
        // a member concludes the app is unreliable.
        showDetail(
            ready(assignees = listOf(assignee(), JobOrderAssignee("u2", "Kell", "Frühschicht", 1L))),
        )

        compose.onAllNodesWithTag(ORDER_NOTE_TAG).assertCountEquals(1)
    }

    @Test
    fun `the status control is absent unless the caller is a Logistician`() {
        showDetail(ready())

        compose.onAllNodesWithTag(ORDER_STATUS_TAG).assertCountEquals(0)
    }

    @Test
    fun `a Logistician is offered the status control`() {
        val picked = mutableListOf<JobOrderStatus>()
        showDetail(ready(logistician = true), statuses = picked)

        compose.onNodeWithTag(ORDER_STATUS_TAG).assertIsEnabled().performClick()

        assertEquals(listOf(JobOrderStatus.UNKNOWN), picked)
    }

    @Test
    fun `the status picker marks where the order stands and reports a choice`() {
        val picked = mutableListOf<JobOrderStatus>()
        showDetail(ready(logistician = true).copy(statusPickerOpen = true), statuses = picked)

        compose.onNodeWithTag(ORDER_STATUS_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Abgeschlossen", ignoreCase = true).performClick()

        assertEquals(listOf(JobOrderStatus.COMPLETED), picked)
    }

    @Test
    fun `the note editor opens on what the row already says`() {
        showDetail(
            ready(assignees = listOf(assignee(note = "alt"))).copy(noteDraft = "alt"),
        )

        compose.onNodeWithTag(ORDER_NOTE_SHEET_TAG).assertIsDisplayed()
        // The row behind the sheet says it too, which is the point: the editor opens on what is
        // already there rather than on nothing.
        compose.onAllNodesWithText("alt").assertCountEquals(2)
    }

    @Test
    fun `a conflict is named and the editor keeps what was typed`() {
        showDetail(
            ready(assignees = listOf(assignee())).copy(
                noteDraft = "Nachtschicht",
                error = ApiError.OptimisticLock(),
            ),
        )

        compose.onNodeWithText("Nachtschicht").assertIsDisplayed()
        compose.onAllNodesWithText(
            "Jemand anderes hat diesen Eintrag inzwischen geändert. Deine Eingabe bleibt stehen — " +
                "lade neu und speichere erneut.",
        ).onFirst().assertExists()
    }

    @Test
    fun `a refusal on this order is said in the app's own words`() {
        showDetail(ready(logistician = true).copy(error = ApiError.Forbidden()))

        compose.onNodeWithText("Für diesen Auftrag fehlt dir die Berechtigung.").assertIsDisplayed()
    }

    @Test
    fun `offline the detail says so and offers no write`() {
        showDetail(ready().copy(online = false))

        compose.onNodeWithText("Kein Netz — Ändern ist gesperrt, bis die Verbindung zurück ist.")
            .assertIsDisplayed()
        compose.onNodeWithTag(ORDER_ASSIGN_TAG).assertIsNotEnabled()
    }

    /**
     * A loaded detail with the caller known.
     *
     * @param assignees who is on the order.
     * @param logistician whether the caller holds the grant.
     * @return the state.
     */
    private fun ready(
        assignees: List<JobOrderAssignee> = emptyList(),
        logistician: Boolean = false,
    ) = OrderDetailState(
        orderId = "o1",
        order = order().copy(assignees = assignees),
        phase = OrderDetailPhase.Ready,
        me = Identity("u1", logistician = logistician),
        // Every caller of this helper asserts something about the assignee rows, and those live on
        // their own tab now (design ch. 10 artboard 2).
        tab = OrderTab.ASSIGNEES,
    )
}

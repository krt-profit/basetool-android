/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.data.Notification
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
 * What the inbox renders.
 *
 * German is pinned, and so is a real phone size: Robolectric's default 320×470 dp display is
 * smaller than any device this app supports, and rows a member plainly sees would otherwise be
 * reported as not displayed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xhdpi")
class NotificationsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun notification(
        id: String,
        type: String = "JOB_ORDER_CREATED",
        params: Map<String, String> = mapOf("displayId" to "1042", "orgUnit" to "Staffel 1"),
        read: Boolean = false,
        entityType: String? = "JOB_ORDER",
    ) = Notification(
        id = id,
        type = type,
        params = params,
        entityType = entityType,
        entityId = "e1",
        read = read,
        createdAt = Instant.parse("2026-08-22T10:00:00Z"),
    )

    /**
     * Renders the inbox in [state].
     *
     * @param state what to draw.
     * @param opened receives the id of a tapped row.
     */
    private fun show(
        state: NotificationsState,
        opened: MutableList<String> = mutableListOf(),
        markedRead: MutableList<String> = mutableListOf(),
        deleted: MutableList<String> = mutableListOf(),
        markedAllRead: MutableList<Unit> = mutableListOf(),
        deletedRead: MutableList<Unit> = mutableListOf(),
        undone: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            KrtTheme {
                NotificationsScreen(
                    state = state,
                    onRefresh = {},
                    onRetryNow = {},
                    onLoadMore = {},
                    onOpen = { opened.add(it.id) },
                    onMarkRead = { markedRead.add(it) },
                    onMarkAllRead = { markedAllRead.add(Unit) },
                    onDelete = { deleted.add(it) },
                    onDeleteRead = { deletedRead.add(Unit) },
                    onUndoDelete = { undone.add(Unit) },
                )
            }
        }
    }

    @Test
    fun `a notification is worded from its type and the server's parameters`() {
        show(
            NotificationsState(
                notifications = listOf(notification("n1")),
                total = 1,
                phase = NotificationsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Neuer Auftrag #1042 für Staffel 1").assertIsDisplayed()
        compose.onNodeWithTag(NOTIFICATIONS_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `a renamed parameter degrades to the generic wording instead of showing braces`() {
        show(
            NotificationsState(
                notifications = listOf(notification("n1", params = mapOf("orderId" to "1042"))),
                total = 1,
                phase = NotificationsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Neue Benachrichtigung").assertIsDisplayed()
    }

    @Test
    fun `an unknown type still tells the member that something happened`() {
        show(
            NotificationsState(
                notifications = listOf(notification("n1", type = "SOMETHING_NEW", params = emptyMap())),
                total = 1,
                phase = NotificationsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Neue Benachrichtigung").assertIsDisplayed()
    }

    /**
     * The count is not in the list any more — it is a chip in the bar (design ch. 07).
     *
     * Asserted as an absence rather than deleted, because the line it replaced was here for a
     * reason: a member has to be told how many are unread. This pins that the answer is given
     * once, in the bar, and does not quietly come back to the top of the list as well.
     */
    @Test
    fun `the unread count is not restated inside the list`() {
        show(
            NotificationsState(
                notifications = listOf(notification("n1")),
                total = 1,
                unread = UNREAD,
                phase = NotificationsPhase.Ready,
            ),
        )

        compose.onAllNodesWithText("$UNREAD neu").assertCountEquals(0)
    }

    @Test
    fun `an empty inbox says what will appear here`() {
        show(NotificationsState(phase = NotificationsPhase.Ready))

        compose.onNodeWithText("Keine Benachrichtigungen").assertIsDisplayed()
        compose.onNodeWithText(
            "Neue Ereignisse aus Einsätzen, Aufträgen und Bank erscheinen hier.",
        ).assertIsDisplayed()
    }

    @Test
    fun `a failed inbox offers a retry`() {
        show(NotificationsState(phase = NotificationsPhase.Failed(ApiError.Network(IOException("offline")))))

        compose.onNodeWithText("Signal Lost").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a capped list says how many of how many it shows`() {
        // The web app's own "newest 50 of 123". A list that cannot say what it is not showing is
        // the silent truncation the main repo's ADR-0104 forbids.
        show(
            NotificationsState(
                notifications = listOf(notification("n1")),
                total = MANY,
                page = 0,
                hasMore = true,
                phase = NotificationsPhase.Ready,
            ),
        )

        compose.onNodeWithText("Zeige die neuesten 1 von 123 Benachrichtigungen", ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping a row reports that row`() {
        val opened = mutableListOf<String>()
        show(
            NotificationsState(
                notifications = listOf(notification("n1"), notification("n2", params = emptyMap())),
                total = 2,
                phase = NotificationsPhase.Ready,
            ),
            opened = opened,
        )

        compose.onNodeWithText("Neue Benachrichtigung").performClick()

        assertEquals(listOf("n2"), opened)
    }

    private companion object {
        /** An unread count small enough to read in the assertion. */
        const val UNREAD = 3L

        /** More than one page's worth. */
        const val MANY = 123L
    }
}

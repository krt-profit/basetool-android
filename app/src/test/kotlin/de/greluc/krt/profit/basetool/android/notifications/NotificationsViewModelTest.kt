/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.data.NotificationBulkResult
import de.greluc.krt.profit.basetool.android.core.data.NotificationPage
import de.greluc.krt.profit.basetool.android.core.data.NotificationSignal
import de.greluc.krt.profit.basetool.android.core.data.NotificationSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The badge and the inbox, which are one state on purpose.
 *
 * Two things are asserted that a happy-path run never shows: a failed count read must not blank the
 * badge, and the push stream must not be the only thing keeping it fresh.
 *
 * **Nothing here waits for the scheduler to run dry, on purpose.** `onForeground` starts a poll
 * loop that always has another delayed task queued, so a wait-until-idle never arrives: the test
 * would hang rather than fail, which is the worst way for a test to be wrong. Every step is either
 * `runCurrent()` or an explicit `advanceTimeBy`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers with whatever is queued and counts the reads.
     *
     * @property inboxAnswers responses for [inbox], the last one repeating once exhausted.
     * @property countAnswers responses for [unreadCount], likewise.
     */
    private class RecordingSource(
        val inboxAnswers: MutableList<ApiResult<NotificationPage>> = mutableListOf(),
        private val countAnswers: MutableList<ApiResult<Long>> = mutableListOf(),
    ) : NotificationSource {
        val signals = MutableSharedFlow<NotificationSignal>(extraBufferCapacity = 8)
        var inboxCalls = 0
        var countCalls = 0
        var streamOpens = 0

        fun queueInbox(answer: ApiResult<NotificationPage>) = inboxAnswers.add(answer)

        fun queueCount(answer: ApiResult<Long>) = countAnswers.add(answer)

        override suspend fun inbox(
            page: Int,
            pageSize: Int,
        ): ApiResult<NotificationPage> {
            inboxCalls++
            return if (inboxAnswers.size > 1) inboxAnswers.removeAt(0) else inboxAnswers.first()
        }

        override suspend fun unreadCount(): ApiResult<Long> {
            countCalls++
            return if (countAnswers.size > 1) countAnswers.removeAt(0) else countAnswers.first()
        }

        override fun changes(): Flow<NotificationSignal> {
            streamOpens++
            return signals
        }

        var markReadCalls = mutableListOf<String>()
        var markAllReadCalls = 0
        var deleteCalls = mutableListOf<String>()
        var deleteReadCalls = 0
        var markReadAnswer: ApiResult<Unit> = ApiResult.Success(Unit)
        var deleteAnswer: ApiResult<Unit> = ApiResult.Success(Unit)
        var bulkAnswer: ApiResult<NotificationBulkResult> =
            ApiResult.Success(NotificationBulkResult(affected = 0, unreadCount = 0))

        /**
         * Replaces the queued inbox answers instead of appending to them.
         *
         * `setUp` already queues a one-row page, and the fake returns the FIRST queued answer
         * while more than one is left — so a test that only appends is served the default and
         * never sees its own rows.
         */
        fun replaceInbox(answer: ApiResult<NotificationPage>) {
            inboxAnswers.clear()
            inboxAnswers.add(answer)
        }

        override suspend fun markRead(id: String): ApiResult<Unit> {
            markReadCalls.add(id)
            return markReadAnswer
        }

        override suspend fun markAllRead(): ApiResult<NotificationBulkResult> {
            markAllReadCalls++
            return bulkAnswer
        }

        override suspend fun delete(id: String): ApiResult<Unit> {
            deleteCalls.add(id)
            return deleteAnswer
        }

        override suspend fun deleteRead(): ApiResult<NotificationBulkResult> {
            deleteReadCalls++
            return bulkAnswer
        }
    }

    private fun notification(id: String) =
        Notification(
            id = id,
            type = "JOB_ORDER_CREATED",
            params = emptyMap(),
            entityType = "JOB_ORDER",
            entityId = "j1",
            read = false,
            createdAt = null,
        )

    private fun page(
        vararg rows: Notification,
        page: Int = 0,
        totalPages: Int = 1,
        total: Long = rows.size.toLong(),
    ) = NotificationPage(rows.toList(), page = page, totalPages = totalPages, totalElements = total)

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = RecordingSource()
        source.queueInbox(ApiResult.Success(page(notification("n1"))))
        source.queueCount(ApiResult.Success(UNREAD))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = NotificationsViewModel(source)

    @Test
    fun `the badge is read as soon as the app is in front, without the inbox`() =
        runTest(dispatcher) {
            // The bell is on every screen; the list is one screen. Reading fifty rows to draw a
            // number would be a page fetched for a badge.
            val model = viewModel()

            model.onForeground()
            runCurrent()

            assertEquals(UNREAD, model.state.value.unread)
            assertEquals(0, source.inboxCalls)
            model.onBackground()
        }

    @Test
    fun `the badge is polled while the app stays in front`() =
        runTest(dispatcher) {
            // The stream is best-effort: the server closes it every thirty minutes and drops the
            // oldest of six. A badge kept fresh only by push would go stale in exactly those cases.
            val model = viewModel()
            model.onForeground()
            runCurrent()
            val afterFirst = source.countCalls

            advanceTimeBy(TWO_POLLS_MS)
            runCurrent()

            assertTrue("expected further polls", source.countCalls > afterFirst)
            model.onBackground()
        }

    @Test
    fun `leaving the foreground stops the poll and closes the stream`() =
        runTest(dispatcher) {
            // Holding a socket open for a screen nobody is looking at spends battery to learn
            // something the member cannot see.
            val model = viewModel()
            model.onForeground()
            runCurrent()
            model.onBackground()
            val afterStop = source.countCalls

            advanceTimeBy(TWO_POLLS_MS)
            runCurrent()

            assertEquals(afterStop, source.countCalls)
        }

    @Test
    fun `a push signal re-reads the badge at once`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.onForeground()
            runCurrent()
            val before = source.countCalls

            source.signals.emit(NotificationSignal.refreshOnly())
            runCurrent()

            assertEquals(before + 1, source.countCalls)
            model.onBackground()
        }

    @Test
    fun `a push signal re-reads the list only once it has been opened`() =
        runTest(dispatcher) {
            // Refreshing a list nobody has opened would fetch fifty rows for a screen that is not
            // on show.
            val model = viewModel()
            model.onForeground()
            runCurrent()

            source.signals.emit(NotificationSignal.refreshOnly())
            runCurrent()
            assertEquals(0, source.inboxCalls)

            model.loadOnce()
            runCurrent()
            source.signals.emit(NotificationSignal.refreshOnly())
            runCurrent()

            assertEquals(2, source.inboxCalls)
            model.onBackground()
        }

    @Test
    fun `a failed count read leaves the badge alone rather than blanking it`() =
        runTest(dispatcher) {
            // Showing zero would tell the member their inbox is clear, which is a claim about
            // their notifications made out of an outage.
            source.queueCount(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            val model = viewModel()
            model.onForeground()
            runCurrent()
            assertEquals(UNREAD, model.state.value.unread)

            source.signals.emit(NotificationSignal.refreshOnly())
            runCurrent()

            assertEquals(UNREAD, model.state.value.unread)
            model.onBackground()
        }

    @Test
    fun `opening the inbox twice reads it once`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.loadOnce()
            runCurrent()
            model.loadOnce()
            runCurrent()

            assertEquals(1, source.inboxCalls)
            assertEquals(NotificationsPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `the next page is appended`() =
        runTest(dispatcher) {
            source.queueInbox(
                ApiResult.Success(page(notification("n2"), page = 1, totalPages = TWO_PAGES, total = TWO_ROWS)),
            )
            val model =
                NotificationsViewModel(
                    RecordingSource(
                        mutableListOf(
                            ApiResult.Success(page(notification("n1"), totalPages = TWO_PAGES, total = TWO_ROWS)),
                            ApiResult.Success(
                                page(notification("n2"), page = 1, totalPages = TWO_PAGES, total = TWO_ROWS),
                            ),
                        ),
                        mutableListOf(ApiResult.Success(UNREAD)),
                    ),
                )
            model.loadOnce()
            runCurrent()

            model.onLoadMore()
            runCurrent()

            assertEquals(listOf("n1", "n2"), model.state.value.notifications.map { it.id })
        }

    @Test
    fun `a failed inbox is a failure, not an empty list`() =
        runTest(dispatcher) {
            val failing =
                RecordingSource(
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("offline")))),
                    mutableListOf(ApiResult.Success(0L)),
                )
            val model = NotificationsViewModel(failing)

            model.loadOnce()
            runCurrent()

            assertTrue(model.state.value.phase is NotificationsPhase.Failed)
        }

    @Test
    fun `marking read flips the row and the badge before the call lands`() =
        runTest(dispatcher) {
            source.replaceInbox(ApiResult.Success(page(notification("n1"))))
            val model = viewModel()
            model.onForeground()
            runCurrent()
            model.loadOnce()
            runCurrent()
            val unreadBefore = model.state.value.unread

            model.onMarkRead("n1")

            // Asserted before runCurrent: the point of an optimistic update is that the member
            // does not wait for the network to see what they asked for.
            assertTrue("row should read as read at once", model.state.value.notifications.first().read)
            assertEquals(unreadBefore - 1, model.state.value.unread)
            runCurrent()
            assertEquals(listOf("n1"), source.markReadCalls)
            model.onBackground()
        }

    @Test
    fun `a failed mark-read puts the row and the badge back`() =
        runTest(dispatcher) {
            source.replaceInbox(ApiResult.Success(page(notification("n1"))))
            source.markReadAnswer = ApiResult.Failure(ApiError.Network(IOException("boom")))
            val model = viewModel()
            model.onForeground()
            runCurrent()
            model.loadOnce()
            runCurrent()
            val unreadBefore = model.state.value.unread

            model.onMarkRead("n1")
            runCurrent()

            assertTrue("row must be unread again", !model.state.value.notifications.first().read)
            assertEquals(unreadBefore, model.state.value.unread)
            model.onBackground()
        }

    @Test
    fun `mark-all-read takes the badge from the server, not from an assumption`() =
        runTest(dispatcher) {
            // Another device may have produced an unread row while the call was in flight.
            // Assuming zero would hide it until the next poll.
            source.replaceInbox(ApiResult.Success(page(notification("n1"))))
            source.bulkAnswer =
                ApiResult.Success(NotificationBulkResult(affected = 3, unreadCount = 1L))
            val model = viewModel()
            model.onForeground()
            runCurrent()
            model.loadOnce()
            runCurrent()

            model.onMarkAllRead()
            runCurrent()

            assertEquals(1, source.markAllReadCalls)
            assertEquals(1L, model.state.value.unread)
            model.onBackground()
        }

    @Test
    fun `a delete does not reach the server while it can still be taken back`() =
        runTest(dispatcher) {
            // This is the whole reason the call waits instead of the row being restored
            // afterwards: the server cannot un-delete a notification.
            source.replaceInbox(
                ApiResult.Success(page(notification("n1"), notification("n2"))),
            )
            val model = viewModel()
            model.loadOnce()
            runCurrent()

            model.onDelete("n1")
            runCurrent()

            assertEquals(1, model.state.value.notifications.size)
            assertTrue("nothing may have been deleted yet", source.deleteCalls.isEmpty())
        }

    @Test
    fun `the delete lands once the window has passed`() =
        runTest(dispatcher) {
            source.replaceInbox(ApiResult.Success(page(notification("n1"))))
            val model = viewModel()
            model.loadOnce()
            runCurrent()

            model.onDelete("n1")
            advanceTimeBy(UNDO_WINDOW_PASSED_MS)
            runCurrent()

            assertEquals(listOf("n1"), source.deleteCalls)
            assertEquals(null, model.state.value.pendingDelete)
        }

    @Test
    fun `undo restores the row at its own place and cancels the call`() =
        runTest(dispatcher) {
            // Restoring to the top would reorder an inbox whose whole ordering is chronological.
            source.replaceInbox(
                ApiResult.Success(page(notification("n1"), notification("n2"), notification("n3"))),
            )
            val model = viewModel()
            model.loadOnce()
            runCurrent()

            model.onDelete("n2")
            model.onUndoDelete()
            advanceTimeBy(UNDO_WINDOW_PASSED_MS)
            runCurrent()

            assertEquals(
                listOf("n1", "n2", "n3"),
                model.state.value.notifications.map { it.id },
            )
            assertTrue("the call must never have gone out", source.deleteCalls.isEmpty())
        }

    @Test
    fun `leaving the screen commits a delete that is still pending`() =
        runTest(dispatcher) {
            // Without this a member who deleted a row and immediately left would find it back.
            source.replaceInbox(ApiResult.Success(page(notification("n1"))))
            val model = viewModel()
            model.loadOnce()
            runCurrent()

            model.onDelete("n1")
            model.onBackground()
            runCurrent()

            assertEquals(listOf("n1"), source.deleteCalls)
        }

    @Test
    fun `a second delete commits the first instead of queueing two undos`() =
        runTest(dispatcher) {
            source.replaceInbox(
                ApiResult.Success(page(notification("n1"), notification("n2"))),
            )
            val model = viewModel()
            model.loadOnce()
            runCurrent()

            model.onDelete("n1")
            model.onDelete("n2")
            runCurrent()

            assertEquals(listOf("n1"), source.deleteCalls)
            assertEquals("n2", model.state.value.pendingDelete?.notification?.id)
        }

    @Test
    fun `delete-read removes only the rows already seen`() =
        runTest(dispatcher) {
            val seen = notification("n1").copy(read = true)
            source.replaceInbox(ApiResult.Success(page(seen, notification("n2"))))
            source.bulkAnswer =
                ApiResult.Success(NotificationBulkResult(affected = 1, unreadCount = 1L))
            val model = viewModel()
            model.loadOnce()
            runCurrent()

            model.onDeleteRead()
            runCurrent()

            assertEquals(listOf("n2"), model.state.value.notifications.map { it.id })
            assertEquals(1, source.deleteReadCalls)
        }

    private companion object {
        /** A count distinctive enough that a zeroed badge is visible in an assertion. */
        const val UNREAD = 7L

        /** Comfortably past the view model's five-second undo window. */
        const val UNDO_WINDOW_PASSED_MS = 6_000L

        /** Long enough for at least two poll ticks. */
        const val TWO_POLLS_MS = 130_000L

        /** A two-page result. */
        const val TWO_PAGES = 2

        /** Its total. */
        const val TWO_ROWS = 2L
    }
}

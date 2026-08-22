/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.notifications

import de.greluc.krt.profit.basetool.android.core.data.Notification
import de.greluc.krt.profit.basetool.android.core.data.NotificationPage
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
        private val inboxAnswers: MutableList<ApiResult<NotificationPage>> = mutableListOf(),
        private val countAnswers: MutableList<ApiResult<Long>> = mutableListOf(),
    ) : NotificationSource {
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
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

        override fun changes(): Flow<Unit> {
            streamOpens++
            return signals
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

            source.signals.emit(Unit)
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

            source.signals.emit(Unit)
            runCurrent()
            assertEquals(0, source.inboxCalls)

            model.loadOnce()
            runCurrent()
            source.signals.emit(Unit)
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

            source.signals.emit(Unit)
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

    private companion object {
        /** A count distinctive enough that a zeroed badge is visible in an assertion. */
        const val UNREAD = 7L

        /** Long enough for at least two poll ticks. */
        const val TWO_POLLS_MS = 130_000L

        /** A two-page result. */
        const val TWO_PAGES = 2

        /** Its total. */
        const val TWO_ROWS = 2L
    }
}

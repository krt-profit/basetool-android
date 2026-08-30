/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The notification reads, and the one rule the push channel has to obey: only a real notification
 * event is a reason to re-read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** A count distinctive enough to spot in an assertion. */
        const val UNREAD = 7L

        val INBOX =
            """
            {
              "content": [
                {"id": "n1", "type": "JOB_ORDER_CREATED",
                 "params": {"displayId": "1042", "orgUnit": "Staffel 1"},
                 "entityType": "JOB_ORDER", "entityId": "j1",
                 "read": false, "createdAt": "2026-08-22T10:00:00Z"},
                {"id": "n2", "type": "BANK_BOOKING_REQUEST_CONFIRMED", "params": {},
                 "read": true, "createdAt": "2026-08-22T09:00:00Z"}
              ],
              "page": 0, "size": 50, "totalElements": 2, "totalPages": 1, "sort": []
            }
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            NotificationRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Enqueues one response.
     *
     * @param body the response body.
     * @param contentType its media type.
     */
    private fun respond(
        body: String,
        contentType: String = "application/json",
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", contentType)
                .body(body)
                .build(),
        )
    }

    /**
     * The URL the repository requested.
     *
     * @return the recorded target.
     */
    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    @Test
    fun `the inbox maps onto the model`() =
        runTest {
            respond(INBOX)

            val result = repository.inbox()

            assertTrue(result is ApiResult.Success)
            val page = (result as ApiResult.Success).value
            assertEquals(2, page.notifications.size)
            val first = page.notifications.first()
            assertEquals("n1", first.id)
            assertEquals("JOB_ORDER_CREATED", first.type)
            assertEquals("1042", first.params["displayId"])
            assertEquals("JOB_ORDER", first.entityType)
            assertFalse(first.read)
            assertEquals(Instant.parse("2026-08-22T10:00:00Z"), first.createdAt)
            assertTrue(page.notifications[1].read)
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `the icon is derived from the type, and an unknown type is not a failure`() =
        runTest {
            respond(INBOX)

            val page = (repository.inbox() as ApiResult.Success).value

            assertEquals(NotificationKind.ORDER, page.notifications.first().kind)
            assertEquals(NotificationKind.BANK, page.notifications[1].kind)
            assertEquals(NotificationKind.SYSTEM, NotificationKind.from("SOMETHING_NEW"))
        }

    @Test
    fun `a row without an id is dropped, and the server total is not lowered`() =
        runTest {
            respond(
                """
                {"content": [{"type": "X", "params": {}}, {"id": "n1", "type": "X", "params": {}}],
                 "page": 0, "totalElements": 2, "totalPages": 1}
                """.trimIndent(),
            )

            val page = (repository.inbox() as ApiResult.Success).value

            assertEquals(1, page.notifications.size)
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `a row without a type is kept, because the screen has a sentence for it`() =
        runTest {
            // Dropping it would hide a notification the server thought worth raising.
            respond("""{"content": [{"id": "n1", "params": {}}], "page": 0, "totalElements": 1, "totalPages": 1}""")

            val page = (repository.inbox() as ApiResult.Success).value

            assertEquals(1, page.notifications.size)
            assertEquals("", page.notifications.first().type)
            assertEquals(NotificationKind.SYSTEM, page.notifications.first().kind)
        }

    @Test
    fun `the page size asked for is the web app's own fifty`() =
        runTest {
            respond(INBOX)

            repository.inbox()

            assertEquals("50", requestedUrl().queryParameter("size"))
        }

    @Test
    fun `the unread count is read from its own endpoint`() =
        runTest {
            respond("""{"count": $UNREAD}""")

            val result = repository.unreadCount()

            assertEquals(UNREAD, (result as ApiResult.Success).value)
        }

    @Test
    fun `a missing count is zero rather than a failure`() =
        runTest {
            respond("{}")

            assertEquals(0L, (repository.unreadCount() as ApiResult.Success).value)
        }

    @Test
    fun `only a notification event is a reason to re-read`() =
        runTest {
            // `connected`, `heartbeat` and `replaced` are the stream's own bookkeeping. A caller
            // re-reading on every heartbeat would poll every twenty seconds while believing it was
            // using push.
            respond(
                "event: connected\ndata: ok\n\n" +
                    "event: heartbeat\ndata: ok\n\n" +
                    "event: notification\ndata: new\n\n" +
                    "event: replaced\ndata: ok\n\n",
                contentType = "text/event-stream",
            )

            val signals = repository.changes().toList()

            assertEquals(1, signals.size)
        }

    @Test
    fun `an unparseable timestamp costs the timestamp, not the row`() =
        runTest {
            respond(
                """{"content": [{"id": "n1", "type": "X", "params": {}, "createdAt": "not-a-time"}],
                    "page": 0, "totalElements": 1, "totalPages": 1}""",
            )

            val page = (repository.inbox() as ApiResult.Success).value

            assertEquals(1, page.notifications.size)
            assertNull(page.notifications.first().createdAt)
        }
}

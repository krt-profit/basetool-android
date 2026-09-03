/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A book-out that empties the stack answers `204`, and that is a success.
 *
 * `POST /inventory/{id}/book-out` returns `200` with the remaining row — unless the book-out takes
 * the last of it, and then the row is gone and there is no row to return. The app read every
 * response through a parser that required one, so the successful case surfaced as „Konnte nicht
 * gespeichert werden."
 *
 * What that cost is the point. The member retried, and every retry was a truthful `403`, because
 * the row the first call had already removed no longer exists — the gate refuses an id it cannot
 * find. Production, 2026-09-03: one `204` at 06:15:12 followed by four `403`s on the same id, from
 * a member who believed nothing had happened. Their material had in fact been booked out on the
 * first press.
 *
 * So the failure mode is worse than a wrong error message: the app told a member the opposite of
 * what the server did, and the retries it invited produced real refusals that looked like
 * confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookOutEmptiesTheRowTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            InventoryRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a book-out that removes the row is a success, not a parse failure`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())

            val result =
                repository.bookOut(
                    id = ENTRY,
                    version = 3L,
                    draft = BookOutDraft(amount = "4", kind = BookOutKind.DISCARD),
                )

            assertTrue(
                "204 means the stack is empty and the row is gone — the book-out worked",
                result is ApiResult.Success,
            )
        }

    @Test
    fun `a partial book-out still succeeds when the row comes back`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .body("""{"id": "$ENTRY", "amount": 2}""")
                    .build(),
            )

            val result =
                repository.bookOut(
                    id = ENTRY,
                    version = 3L,
                    draft = BookOutDraft(amount = "2", kind = BookOutKind.DISCARD),
                )

            assertTrue("200 with the remaining row is the other half", result is ApiResult.Success)
        }

    @Test
    fun `a refusal is still a failure`() =
        runTest {
            // The guard that matters: tolerating an empty body must not turn into tolerating
            // anything. A 403 on a row the caller may not edit stays a failure.
            server.enqueue(MockResponse.Builder().code(HTTP_FORBIDDEN).build())

            val result =
                repository.bookOut(
                    id = ENTRY,
                    version = 3L,
                    draft = BookOutDraft(amount = "4", kind = BookOutKind.DISCARD),
                )

            assertTrue("403 is not success", result is ApiResult.Failure)
        }

    private companion object {
        const val ENTRY = "11111111-1111-4111-8111-111111111111"
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_FORBIDDEN = 403
    }
}

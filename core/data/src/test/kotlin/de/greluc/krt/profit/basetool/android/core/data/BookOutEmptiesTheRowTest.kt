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
 * A book-out that empties the stack answers `204` with no row, and that must read as a success.
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

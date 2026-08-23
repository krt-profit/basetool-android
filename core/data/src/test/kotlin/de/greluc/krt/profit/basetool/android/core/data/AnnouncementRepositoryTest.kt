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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The announcement read, whose whole subtlety is what "nothing to announce" looks like on the wire.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnouncementRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_SERVER_ERROR = 500
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: AnnouncementRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            AnnouncementRepository(
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
     * @param status the status code.
     * @param body the body, empty by default.
     */
    private fun respond(
        status: Int,
        body: String = "",
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .apply { if (body.isNotEmpty()) setHeader("Content-Type", "application/json") }
                .body(body)
                .build(),
        )
    }

    @Test
    fun `an announcement maps onto the model`() =
        runTest {
            respond(
                HTTP_OK,
                """{"id": "a1", "content": "Wartung am Dienstag", "updatedAt": "2026-08-22T10:00:00Z"}""",
            )

            val result = repository.current()

            val announcement = (result as ApiResult.Success).value
            assertEquals("Wartung am Dienstag", announcement?.content)
            assertEquals(Instant.parse("2026-08-22T10:00:00Z"), announcement?.updatedAt)
        }

    @Test
    fun `a 204 means no announcement, not a failure`() =
        runTest {
            respond(HTTP_NO_CONTENT)

            val result = repository.current()

            assertTrue(result is ApiResult.Success)
            assertNull((result as ApiResult.Success).value)
        }

    @Test
    fun `a blank announcement is no announcement`() =
        runTest {
            // The backend suppresses blank ones with a 204 already, but the field is nullable on
            // the wire and a banner made of whitespace would be a visible defect for the sake of
            // trusting a shape.
            respond(HTTP_OK, """{"id": "a1", "content": "   "}""")

            assertNull((repository.current() as ApiResult.Success).value)
        }

    @Test
    fun `an outage is still an outage`() =
        runTest {
            respond(HTTP_SERVER_ERROR, "{}")

            assertTrue(repository.current() is ApiResult.Failure)
        }
}

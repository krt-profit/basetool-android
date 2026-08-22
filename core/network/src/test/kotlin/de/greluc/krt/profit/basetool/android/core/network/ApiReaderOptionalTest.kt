/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

/**
 * The optional-body read.
 *
 * It exists for one endpoint's honest answer: `GET /api/v1/announcement` says `204 No Content` when
 * there is nothing to announce. Read through the ordinary path that empty body fails to parse and
 * becomes a server error — an error banner where the correct rendering is no banner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiReaderOptionalTest {
    @Serializable
    private data class Payload(
        val content: String? = null,
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_FORBIDDEN = 403
    }

    private lateinit var server: MockWebServer
    private lateinit var reader: ApiReader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        reader =
            ApiReader(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
                json = Json { ignoreUnknownKeys = true },
                logTag = "test",
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
    fun `a 204 is a result, not a failure`() =
        runTest {
            respond(HTTP_NO_CONTENT)

            val result = reader.getOptional("/announcement", Payload.serializer())

            assertTrue(result is ApiResult.Success)
            assertNull((result as ApiResult.Success).value)
        }

    @Test
    fun `a 200 with an empty body is treated the same way`() =
        runTest {
            // A server answering "nothing" with a zero-length body rather than a status is being
            // sloppy, not broken, and the difference is invisible to the member either way.
            respond(HTTP_OK)

            val result = reader.getOptional("/announcement", Payload.serializer())

            assertNull((result as ApiResult.Success).value)
        }

    @Test
    fun `a body is parsed as usual`() =
        runTest {
            respond(HTTP_OK, """{"content": "Wartung"}""")

            val result = reader.getOptional("/announcement", Payload.serializer())

            assertEquals("Wartung", (result as ApiResult.Success).value?.content)
        }

    @Test
    fun `a refusal is still a failure`() =
        runTest {
            // The optional read is about an absent body, not about tolerating errors.
            respond(HTTP_FORBIDDEN, """{"title": "Forbidden"}""")

            val result = reader.getOptional("/announcement", Payload.serializer())

            assertTrue(result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Forbidden)
        }
}

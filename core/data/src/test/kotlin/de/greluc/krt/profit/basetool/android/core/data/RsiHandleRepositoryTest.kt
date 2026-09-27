/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * The member's RSI handle on the wire `openapi.json` describes (server REQ-SEC-072).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RsiHandleRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CONFLICT = 409
        const val VERSION = 7L
        const val PATH = "/api/v1/users/me/rsi-handle"
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: MemberPreferencesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MemberPreferencesRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(
        body: String,
        code: Int = HTTP_OK,
        type: String = "application/json",
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader("Content-Type", type)
                .body(body)
                .build(),
        )
    }

    @Test
    fun `an unset handle reads as none, with the row's version`() =
        runTest {
            respond("""{"version": 7}""")

            val result = repository.rsiHandle()

            assertEquals(PATH, server.takeRequest().url.encodedPath)
            assertEquals(ApiResult.Success(RsiHandle(handle = null, version = VERSION)), result)
        }

    @Test
    fun `a save sends the trimmed handle with the version it echoes`() =
        runTest {
            respond("""{"rsiHandle": "GrafRotz_SC", "version": 8}""")

            val result = repository.setRsiHandle(" GrafRotz_SC ", VERSION)

            val request = server.takeRequest()
            val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
            assertEquals("PUT", request.method)
            assertEquals(PATH, request.url.encodedPath)
            assertEquals("GrafRotz_SC", body.getValue("rsiHandle").jsonPrimitive.content)
            assertEquals("7", body.getValue("version").jsonPrimitive.content)
            assertEquals("GrafRotz_SC", (result as ApiResult.Success).value.handle)
        }

    /** A blank response handle is the server saying there is none. */
    @Test
    fun `clearing sends an empty handle and reads none back`() =
        runTest {
            respond("""{"rsiHandle": "", "version": 8}""")

            val result = repository.setRsiHandle("", VERSION)

            val body = Json.parseToJsonElement(server.takeRequest().body?.utf8().orEmpty()).jsonObject
            assertEquals("", body.getValue("rsiHandle").jsonPrimitive.content)
            assertNull((result as ApiResult.Success).value.handle)
        }

    @Test
    fun `a taken handle comes back as a conflict carrying its code`() =
        runTest {
            respond(
                """{"status": 409, "code": "DUPLICATE_ENTITY", "title": "Duplicate entity"}""",
                code = HTTP_CONFLICT,
                type = "application/problem+json",
            )

            val result = repository.setRsiHandle("someone_else", VERSION)

            val error = (result as ApiResult.Failure).error
            assertTrue(error is ApiError.Conflict)
            assertEquals("DUPLICATE_ENTITY", error.problem?.code)
        }
}

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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The notification switches on the wire: the list keeps a type this build has never seen, a write
 * names its type in the path and carries only `muted`, and a refusal arrives classified.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPreferencesRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400

        val LIST =
            """
            [
              {"type": "JOB_ORDER_CREATED", "mutable": true, "muted": false},
              {"type": "BANK_BOOKING_REQUEST_CREATED", "mutable": true, "muted": true},
              {"type": "ACCOUNT_DELETION_REQUESTED", "mutable": false, "muted": false},
              {"type": "SOMETHING_THE_SERVER_ADDED_LATER", "mutable": true, "muted": false}
            ]
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: NotificationPreferencesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            NotificationPreferencesRepository(
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
        contentType: String = "application/json",
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader("Content-Type", contentType)
                .body(body)
                .build(),
        )
    }

    @Test
    fun `the list maps every flag and keeps the server's order`() =
        runTest {
            respond(LIST)

            val rows = (repository.preferences() as ApiResult.Success).value

            assertEquals(
                listOf(
                    NotificationPreference("JOB_ORDER_CREATED", mutable = true, muted = false),
                    NotificationPreference("BANK_BOOKING_REQUEST_CREATED", mutable = true, muted = true),
                    NotificationPreference("ACCOUNT_DELETION_REQUESTED", mutable = false, muted = false),
                    NotificationPreference("SOMETHING_THE_SERVER_ADDED_LATER", mutable = true, muted = false),
                ),
                rows,
            )
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/notifications/preferences", request.target)
        }

    @Test
    fun `a type the contract does not list is kept under its own name`() =
        runTest {
            respond(LIST)

            val types = (repository.preferences() as ApiResult.Success).value.map { it.type }

            assertTrue("SOMETHING_THE_SERVER_ADDED_LATER" in types)
        }

    @Test
    fun `a row without a type, or that is no object, is dropped`() =
        runTest {
            respond("""[{"mutable": true, "muted": true}, 7, {"type": "", "mutable": true}, {"type": "A_B"}]""")

            val rows = (repository.preferences() as ApiResult.Success).value

            assertEquals(listOf(NotificationPreference("A_B", mutable = false, muted = false)), rows)
        }

    @Test
    fun `an absent flag reads as locked and not muted`() =
        runTest {
            respond("""[{"type": "JOB_ORDER_CREATED"}]""")

            val row = (repository.preferences() as ApiResult.Success).value.single()

            assertFalse(row.mutable)
            assertFalse(row.muted)
        }

    @Test
    fun `a body that is no list is an empty list, not a crash`() =
        runTest {
            respond("""{"unexpected": true}""")

            assertEquals(emptyList<NotificationPreference>(), (repository.preferences() as ApiResult.Success).value)
        }

    @Test
    fun `a write names the type in the path and sends only muted`() =
        runTest {
            respond("""{"type": "JOB_ORDER_CREATED", "mutable": true, "muted": true}""")

            val result = repository.setMuted("JOB_ORDER_CREATED", muted = true)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/notifications/preferences/JOB_ORDER_CREATED", request.target)
            assertEquals("""{"muted":true}""", request.body?.utf8())
            assertEquals(
                NotificationPreference("JOB_ORDER_CREATED", mutable = true, muted = true),
                (result as ApiResult.Success).value,
            )
        }

    @Test
    fun `a write of an unknown type answers under the type that was sent`() =
        runTest {
            respond("""{"type": "SOMETHING_THE_SERVER_ADDED_LATER", "mutable": true, "muted": false}""")

            val row =
                (
                    repository.setMuted(
                        "SOMETHING_THE_SERVER_ADDED_LATER",
                        muted = false,
                    ) as ApiResult.Success
                ).value

            assertEquals("SOMETHING_THE_SERVER_ADDED_LATER", row.type)
            assertFalse(row.muted)
        }

    @Test
    fun `a type that cannot be muted comes back as a validation refusal`() =
        runTest {
            respond(
                """{"status": 400, "code": "VALIDATION_ERROR", "title": "Bad Request"}""",
                code = HTTP_BAD_REQUEST,
                contentType = "application/problem+json",
            )

            val result = repository.setMuted("ACCOUNT_DELETION_REQUESTED", muted = true)

            assertTrue((result as ApiResult.Failure).error is ApiError.Validation)
        }
}

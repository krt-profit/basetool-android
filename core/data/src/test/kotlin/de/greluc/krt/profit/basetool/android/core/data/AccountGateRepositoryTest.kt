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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gate read, including the two answers that arrive as refusals rather than as data.
 *
 * Robolectric because the repository logs through the project facade, which calls
 * `android.util.Log` — unmocked in a plain JVM test, which would then fail on the diagnostic
 * instead of the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountGateRepositoryTest {
    private companion object {
        /** The status the backend uses for BOTH gates and for a real authorisation failure. */
        const val HTTP_FORBIDDEN = 403

        /** A normal answer. */
        const val HTTP_OK = 200
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: AccountGateRepository

    /**
     * Starts a server and points a repository at it.
     */
    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            AccountGateRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    /**
     * Shuts the server down.
     */
    @After
    fun tearDown() {
        server.close()
    }

    /**
     * The ordinary approved answer.
     */
    @Test
    fun `reads an approved account`() =
        runTest {
            server.enqueue(json("""{"approvalStatus":"ACTIVE"}"""))

            val result = repository.registrationStatus()

            assertEquals(ApiResult.Success(ApprovalStatus.ACTIVE), result)
            assertTrue(ApprovalStatus.ACTIVE.isCleared)
        }

    /**
     * A pending account answered as data.
     */
    @Test
    fun `reads a pending account`() =
        runTest {
            server.enqueue(json("""{"approvalStatus":"PENDING"}"""))

            assertEquals(ApiResult.Success(ApprovalStatus.PENDING), repository.registrationStatus())
        }

    /**
     * A status this build predates must not be rounded up to approved.
     *
     * The gate is the safe side of that decision: an unrecognised status keeps the member out and
     * asks again, rather than admitting them to an app whose every request the server will refuse.
     */
    @Test
    fun `an unknown status does not clear the gate`() =
        runTest {
            server.enqueue(json("""{"approvalStatus":"SUSPENDED"}"""))

            val result = repository.registrationStatus()

            assertEquals(ApiResult.Success(ApprovalStatus.UNKNOWN), result)
            assertTrue(!ApprovalStatus.UNKNOWN.isCleared)
        }

    /**
     * A body without the field is the same non-answer as an unknown value.
     */
    @Test
    fun `a missing field does not clear the gate`() =
        runTest {
            server.enqueue(json("{}"))

            assertEquals(ApiResult.Success(ApprovalStatus.UNKNOWN), repository.registrationStatus())
        }

    /**
     * The refusal that means exactly what the caller asked.
     *
     * Whether this endpoint is refused depends on the deployment's filter order, so a
     * `PENDING_APPROVAL` problem body has to read as "pending" rather than as a failed request —
     * otherwise a waiting member is shown a connectivity error.
     */
    @Test
    fun `a PENDING_APPROVAL refusal is the answer, not an error`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(HTTP_FORBIDDEN)
                    .setHeader("Content-Type", "application/problem+json")
                    .body("""{"status":403,"code":"PENDING_APPROVAL","title":"Freigabe ausstehend"}""")
                    .build(),
            )

            assertEquals(ApiResult.Success(ApprovalStatus.PENDING), repository.registrationStatus())
        }

    /**
     * A genuine authorisation failure stays a failure.
     *
     * This is the counterpart to the case above and the reason the fold is written against the
     * stable code rather than against the 403 status: both arrive as 403, and only one of them
     * means "waiting".
     */
    @Test
    fun `a plain 403 stays a failure`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(HTTP_FORBIDDEN)
                    .setHeader("Content-Type", "application/problem+json")
                    .body("""{"status":403,"code":"FORBIDDEN"}""")
                    .build(),
            )

            val result = repository.registrationStatus()

            assertTrue("expected a failure, got $result", result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Forbidden)
        }

    /**
     * A 200 whose body is not JSON is a server fault, not a connectivity one.
     *
     * Reporting it as [ApiError.Network] would tell the member to check their connection, which
     * cannot possibly help — the server answered.
     */
    @Test
    fun `an unreadable body is reported as a server fault`() =
        runTest {
            server.enqueue(json("not json at all"))

            val result = repository.registrationStatus()

            assertTrue("expected a failure, got $result", result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Server)
        }

    /**
     * A request that never reaches the server is a network failure.
     */
    @Test
    fun `an unreachable server is a network failure`() =
        runTest {
            server.close()

            val result = repository.registrationStatus()

            assertTrue("expected a failure, got $result", result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Network)
        }

    /**
     * Builds a 200 with a JSON body.
     *
     * @param body the raw body
     * @return the queued response
     */
    private fun json(body: String): MockResponse =
        MockResponse
            .Builder()
            .code(HTTP_OK)
            .setHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

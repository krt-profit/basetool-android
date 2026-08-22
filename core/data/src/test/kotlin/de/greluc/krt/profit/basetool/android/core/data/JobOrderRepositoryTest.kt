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

/**
 * The job-order reads.
 *
 * The two rules with teeth: `redacted` must survive to the screen, and the progress bar must not
 * invent a full bar out of a zero need.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobOrderRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** A quarter of the need is in stock in the fixture. */
        const val QUARTER = 0.25f

        /** The queue's total. */
        const val TOTAL = 2L

        val QUEUE =
            """
            {"content": [
               {"id": "o1", "displayId": 1042, "status": "IN_PROGRESS", "priority": 1,
                "type": "MATERIAL", "createdAt": "2026-08-01T10:00:00Z",
                "requestingOrgUnit": {"name": "Staffel 1"},
                "responsibleOrgUnit": {"name": "SK Vanguard"},
                "materials": [
                  {"material": {"name": "Quantainium"}, "amount": 500.0000,
                   "currentStock": 125.0000, "claims": [{"id": "c1", "amount": 50.0}],
                   "openAmount": 325.0000}
                ],
                "redacted": true},
               {"displayId": 9, "status": "OPEN"}
             ],
             "page": 0, "totalElements": $TOTAL, "totalPages": 1}
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: JobOrderRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            JobOrderRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Enqueues one JSON body.
     *
     * @param body the response body.
     */
    private fun respond(body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", "application/json")
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
    fun `an order maps onto the model`() =
        runTest {
            respond(QUEUE)

            val page = (repository.queue() as ApiResult.Success).value

            val first = page.orders.first()
            assertEquals("o1", first.id)
            assertEquals("1042", first.displayId)
            assertEquals(JobOrderStatus.IN_PROGRESS, first.status)
            assertEquals("Staffel 1", first.requestingOrgUnit)
            assertEquals("SK Vanguard", first.responsibleOrgUnit)
            assertEquals("Quantainium", first.materials.single().name)
            // "500.0", not "500": a Double round-trips through its own toString, and the
            // screen's formatter is what strips the tail. The repository does not pretend to
            // know how many digits matter.
            assertEquals("500.0", first.materials.single().needed)
            assertEquals(1, first.materials.single().claimCount)
        }

    @Test
    fun `redacted survives to the screen`() =
        runTest {
            // The flag is what tells a requester they are looking at a reduced order
            // (REQ-ORDERS-023). Losing it would present the gaps as the whole truth.
            respond(QUEUE)

            assertTrue((repository.queue() as ApiResult.Success).value.orders.first().redacted)
        }

    @Test
    fun `an absent redacted flag means not redacted`() =
        runTest {
            // Treating its absence as "something is missing" would put a caveat on every order an
            // older server sends.
            respond("""{"content": [{"id": "o1", "displayId": 1}], "page": 0, "totalElements": 1, "totalPages": 1}""")

            assertFalse((repository.queue() as ApiResult.Success).value.orders.first().redacted)
        }

    @Test
    fun `a row without an id is dropped and the server total stands`() =
        runTest {
            respond(QUEUE)

            val page = (repository.queue() as ApiResult.Success).value
            assertEquals(1, page.orders.size)
            assertEquals(TOTAL, page.totalElements)
        }

    @Test
    fun `progress is stock over need, clamped`() =
        runTest {
            respond(QUEUE)

            val material = (repository.queue() as ApiResult.Success).value.orders.first().materials.single()

            assertEquals(QUARTER, material.progress)
        }

    @Test
    fun `a need of zero has no progress rather than a full bar`() {
        // Nothing was asked for, so nothing can be complete — and a full green bar would say the
        // opposite.
        val material = JobOrderMaterial("Quantainium", "0", "10", 0, null)

        assertNull(material.progress)
    }

    @Test
    fun `progress never exceeds one`() {
        val material = JobOrderMaterial("Quantainium", "100", "250", 0, null)

        assertEquals(1f, material.progress)
    }

    @Test
    fun `each selected status is its own parameter, and an unknown one is never sent`() =
        runTest {
            respond(QUEUE)

            repository.queue(setOf(JobOrderStatus.OPEN, JobOrderStatus.UNKNOWN))

            assertEquals(listOf("OPEN"), requestedUrl().queryParameterValues("status"))
        }

    @Test
    fun `the org scope is never sent from the client`() =
        runTest {
            // Which orders a member sees follows from the active-org-unit header. A client-side
            // scope would be a second, weaker copy of a server-side rule.
            respond(QUEUE)

            repository.queue()

            assertNull(requestedUrl().queryParameter("squadronId"))
        }

    @Test
    fun `an order payload without an id is a not-found, not a blank screen`() =
        runTest {
            respond("""{"displayId": 7}""")

            assertTrue(repository.detail("o1") is ApiResult.Failure)
        }
}

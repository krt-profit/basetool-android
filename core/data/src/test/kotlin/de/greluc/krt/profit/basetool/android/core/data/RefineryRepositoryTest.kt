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
import java.time.OffsetDateTime

/**
 * The Raffinerie reads and the booking write.
 *
 * The rules with teeth here are the two the server cannot enforce for us: the „Abholbereit" phase
 * is derived from a clock rather than read, and a booking must never be sent with an empty item
 * list — the endpoint marks the order stored regardless of what that list contains.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefineryRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** The fixture's refined output, in SCU. */
        const val YIELD_SCU = 622

        /** Well before every fixture's end time. */
        val BEFORE: OffsetDateTime = OffsetDateTime.parse("2026-08-16T23:00:00Z")

        /** Well after them. */
        val AFTER: OffsetDateTime = OffsetDateTime.parse("2026-08-17T12:00:00Z")

        /**
         * Two rows: one running with a server-sent `endsAt`, one already stored.
         *
         * The stored row deliberately carries an `endsAt` in the past as well, so a phase read
         * purely off the clock would call it „Abholbereit" and the status check is what saves it.
         */
        val ORDERS =
            """
            {"content": [
               {"id": "r1", "status": "IN_PROGRESS",
                "location": {"id": "loc1", "name": "ARC-L1 Wide Forest"},
                "refiningMethod": {"name": "Dinyx-Solventierung"},
                "startedAt": "2026-08-16T22:41:00Z", "durationMinutes": 300,
                "endsAt": "2026-08-17T03:41:00Z",
                "oreSales": 96900, "profit": 84200, "version": 2,
                "goods": [
                  {"inputMaterial": {"id": "m0", "name": "Quantainium (Raw)"},
                   "inputQuantity": 800,
                   "outputMaterial": {"id": "m1", "name": "Quantainium"},
                   "outputQuantity": 622, "quality": 3}
                ]},
               {"id": "r2", "status": "COMPLETED",
                "location": {"id": "loc1", "name": "ARC-L1 Wide Forest"},
                "startedAt": "2026-08-14T10:00:00Z", "durationMinutes": 60,
                "endsAt": "2026-08-14T11:00:00Z",
                "goods": []}
             ],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        /** A detail response with no `endsAt` — the field the detail DTO does not carry. */
        val DETAIL =
            """
            {"id": "r1", "status": "IN_PROGRESS",
             "location": {"id": "loc1", "name": "ARC-L1 Wide Forest"},
             "refiningMethod": {"name": "Dinyx-Solventierung"},
             "startedAt": "2026-08-16T22:41:00Z", "durationMinutes": 300,
             "goods": [
               {"inputMaterial": {"id": "m0", "name": "Quantainium (Raw)"},
                "inputQuantity": 800,
                "outputMaterial": {"id": "m1", "name": "Quantainium"},
                "outputQuantity": 622, "quality": 3},
               {"inputMaterial": {"id": "m2", "name": "Titanium (Raw)"},
                "inputQuantity": 100, "outputQuantity": 80}
             ]}
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: RefineryRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            RefineryRepository(
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
            respond(ORDERS)

            val page = (repository.myOrders() as ApiResult.Success).value

            val first = page.orders.first()
            assertEquals("r1", first.id)
            assertEquals("loc1", first.locationId)
            assertEquals("ARC-L1 Wide Forest", first.locationName)
            assertEquals("Dinyx-Solventierung", first.methodName)
            assertEquals(RefineryServerStatus.IN_PROGRESS, first.status)
            // The OUTPUT material, not the input: the ore went in and no longer exists.
            assertEquals("Quantainium", first.yields.single().materialName)
            assertEquals("m1", first.yields.single().materialId)
            assertEquals(YIELD_SCU, first.totalAmount)
        }

    @Test
    fun `the phase follows the clock, not the response`() =
        runTest {
            respond(ORDERS)

            val running = (repository.myOrders() as ApiResult.Success).value.orders.first()

            // One order, two answers. The server said IN_PROGRESS both times; only the clock moved.
            assertEquals(RefineryPhase.RUNNING, running.phaseAt(BEFORE))
            assertEquals(RefineryPhase.READY, running.phaseAt(AFTER))
        }

    @Test
    fun `a stored order stays stored however late it is read`() =
        runTest {
            respond(ORDERS)

            val stored = (repository.myOrders() as ApiResult.Success).value.orders[1]

            // Its end time is in the past too. A phase derived from the clock alone would call
            // this "Abholbereit" and offer to book a yield that is already in the Lager.
            assertEquals(RefineryPhase.STORED, stored.phaseAt(AFTER))
            assertFalse(stored.canStoreAt(AFTER))
        }

    @Test
    fun `the detail computes the end time the list is sent`() =
        runTest {
            respond(DETAIL)

            val order = (repository.detail("r1") as ApiResult.Success).value

            // The detail DTO has no endsAt. Without this the detail would show "Restzeit
            // unbekannt" for an order the list beside it was counting down.
            assertEquals("2026-08-17T03:41Z", order.endsAt)
            assertEquals(RefineryPhase.RUNNING, order.phaseAt(BEFORE))
        }

    @Test
    fun `a good without an output material keeps its input name and cannot be booked`() =
        runTest {
            respond(DETAIL)

            val order = (repository.detail("r1") as ApiResult.Success).value

            val unrefined = order.yields[1]
            // Named, so the member sees the row; unbookable, because a booking addresses a
            // material by id and this one has none.
            assertEquals("Titanium (Raw)", unrefined.materialName)
            assertNull(unrefined.materialId)
            // The order as a whole is still bookable — one of its two goods can be.
            assertTrue(order.canStoreAt(AFTER))
        }

    @Test
    fun `a booking sends one item per bookable good`() =
        runTest {
            respond(DETAIL)
            val order = (repository.detail("r1") as ApiResult.Success).value
            requestedUrl()
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

            assertTrue(repository.store(order) is ApiResult.Success)

            val request = server.takeRequest()
            val body = request.body?.utf8().orEmpty()
            assertTrue(request.target.endsWith("/api/v1/refinery-orders/r1/store"))
            // One item, not two: the good without an output material is left out rather than sent
            // with a null id.
            assertEquals(1, Regex("\"materialId\"").findAll(body).count())
            assertTrue(body.contains("\"materialId\":\"m1\""))
            assertTrue(body.contains("\"locationId\":\"loc1\""))
            assertTrue(body.contains("\"quality\":3"))
        }

    @Test
    fun `a booking with nothing to book is refused before it is sent`() =
        runTest {
            respond(
                """
                {"id": "r3", "status": "IN_PROGRESS",
                 "location": {"id": "loc1", "name": "ARC-L1"},
                 "startedAt": "2026-08-14T10:00:00Z", "durationMinutes": 1,
                 "goods": [{"inputMaterial": {"id": "m9", "name": "Ore"},
                            "inputQuantity": 10, "outputQuantity": 8}]}
                """.trimIndent(),
            )
            val order = (repository.detail("r3") as ApiResult.Success).value

            val result = repository.store(order)

            // Nothing was sent. The endpoint marks an order COMPLETED whatever the item list holds,
            // so an empty list is the quiet way to lose a whole run's yield.
            assertTrue(result is ApiResult.Failure)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `the status filter travels as repeated query parameters`() =
        runTest {
            respond(ORDERS)

            repository.myOrders(
                setOf(RefineryServerStatus.OPEN, RefineryServerStatus.IN_PROGRESS),
            )

            val url = requestedUrl()
            assertEquals(
                listOf("OPEN", "IN_PROGRESS").sorted(),
                url.queryParameterValues("status").filterNotNull().sorted(),
            )
        }

    @Test
    fun `an unknown status is never echoed back to the server`() =
        runTest {
            respond(ORDERS)

            repository.myOrders(setOf(RefineryServerStatus.UNKNOWN))

            // UNKNOWN is this build's name for a status the server added. Sending it back would
            // turn one unrecognised row into a 400 on the whole page.
            assertTrue(requestedUrl().queryParameterValues("status").isEmpty())
        }
}

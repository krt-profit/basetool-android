/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The Einsatz list read: what reaches the wire, and what the app does with answers it did not
 * expect.
 *
 * Robolectric because the repository logs through the project facade, which calls
 * `android.util.Log` — unmocked in a plain JVM test, which would fail on the diagnostic rather than
 * on the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionRepositoryTest {
    private companion object {
        /** A normal answer. */
        const val HTTP_OK = 200

        /** The server is up but broken. */
        const val HTTP_SERVER_ERROR = 500

        /** Slack on the lower-bound assertion, so a slow test machine cannot fail it. */
        const val CLOCK_SLACK_SECONDS = 5L

        /** A total large enough that one page cannot hold it. */
        const val MANY_ELEMENTS = 60L

        /** How many pages [MANY_ELEMENTS] spans. */
        const val MANY_PAGES = 3

        /** One well-formed row, enough for the mapping assertions. */
        val ONE_PAGE =
            """
            {
              "content": [
                {
                  "id": "m1",
                  "name": "Vertikaler Abbau — Lyria",
                  "status": "PLANNED",
                  "meetingTime": "2026-08-21T18:30:00Z",
                  "plannedStartTime": "2026-08-21T19:00:00Z",
                  "isInternal": false,
                  "meetingPoint": "ARC-L1",
                  "owningSquadron": {"name": "Staffel 1", "shorthand": "S1"},
                  "operation": {"name": "Operation Rotschild"}
                }
              ],
              "page": 0, "size": 25, "totalElements": 1, "totalPages": 1, "sort": []
            }
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: MissionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MissionRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
                clock = ServerClock(),
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
     * @param status the status code.
     */
    private fun respond(
        body: String,
        status: Int = HTTP_OK,
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    /**
     * The URL the repository actually requested.
     *
     * @return the recorded request target, parsed so query parameters can be read by name rather
     *   than matched as a substring — which would pass on a double-encoded value.
     */
    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    @Test
    fun `a page maps onto the model`() =
        runTest {
            respond(ONE_PAGE)

            val result = repository.search(MissionQuery.NONE)

            assertTrue(result is ApiResult.Success)
            val page = (result as ApiResult.Success).value
            assertEquals(1, page.missions.size)
            val mission = page.missions.first()
            assertEquals("m1", mission.id)
            assertEquals("Vertikaler Abbau — Lyria", mission.name)
            assertEquals(MissionStatus.PLANNED, mission.status)
            assertEquals(Instant.parse("2026-08-21T18:30:00Z"), mission.meetingTime)
            assertEquals(Instant.parse("2026-08-21T19:00:00Z"), mission.plannedStartTime)
            assertEquals("S1", mission.orgUnitShorthand)
            assertEquals("Operation Rotschild", mission.operationName)
            assertEquals(1L, page.totalElements)
            assertFalse(page.hasMore)
        }

    @Test
    fun `the search term is encoded exactly once`() =
        runTest {
            // `&` and `=` are the characters that either truncate the request or arrive
            // double-encoded when a query string is built by concatenation. The failure mode is a
            // search that silently matches nothing, which reads as "no Einsätze" rather than a bug.
            respond(ONE_PAGE)

            repository.search(MissionQuery(text = "Abbau & Eskorte = 2"))

            assertEquals("Abbau & Eskorte = 2", requestedUrl().queryParameter("query"))
        }

    @Test
    fun `a blank search term is left off the wire entirely`() =
        runTest {
            respond(ONE_PAGE)

            repository.search(MissionQuery(text = "   "))

            assertNull(requestedUrl().queryParameter("query"))
        }

    @Test
    fun `each selected status becomes its own repeated parameter`() =
        runTest {
            respond(ONE_PAGE)

            repository.search(
                MissionQuery(statuses = setOf(MissionStatus.PLANNED, MissionStatus.ACTIVE)),
            )

            assertEquals(
                setOf("PLANNED", "ACTIVE"),
                requestedUrl().queryParameterValues("status").toSet(),
            )
        }

    @Test
    fun `UNKNOWN is never sent as a status filter`() =
        runTest {
            // It is this build's word for "a status I do not recognise", not a server value. Sending
            // it would filter on a status the backend has never heard of and return nothing.
            respond(ONE_PAGE)

            repository.search(MissionQuery(statuses = setOf(MissionStatus.UNKNOWN, MissionStatus.ACTIVE)))

            assertEquals(listOf("ACTIVE"), requestedUrl().queryParameterValues("status"))
        }

    @Test
    fun `hiding past Einsaetze sends a lower bound taken from the server clock`() =
        runTest {
            // The bound is the SERVER's now, not the device's. A phone running a few minutes fast
            // would otherwise hide an Einsatz that is about to start -- the one a member most needs
            // to see.
            respond(ONE_PAGE)
            val before = Instant.now()

            repository.search(MissionQuery(includePast = false))

            val sent = requestedUrl().queryParameter("start")
            assertNotNull("a lower bound is what hides the past", sent)
            val bound = Instant.parse(sent)
            assertFalse("the bound must not predate the call", bound.isBefore(before.minusSeconds(CLOCK_SLACK_SECONDS)))
        }

    @Test
    fun `showing past Einsaetze sends no lower bound`() =
        runTest {
            respond(ONE_PAGE)

            repository.search(MissionQuery(includePast = true))

            assertNull(requestedUrl().queryParameter("start"))
        }

    @Test
    fun `an explicit range wins over the past toggle`() =
        runTest {
            respond(ONE_PAGE)
            val from = Instant.parse("2026-01-01T00:00:00Z")

            repository.search(MissionQuery(from = from, includePast = false))

            assertEquals(from.toString(), requestedUrl().queryParameter("start"))
        }

    @Test
    fun `the sort is one the backend whitelists`() =
        runTest {
            // An unlisted sort field is answered with 400, so this is not a free-form string: the
            // list would fail to load entirely rather than merely arrive in another order.
            respond(ONE_PAGE)

            repository.search(MissionQuery.NONE)

            assertEquals("plannedStartTime,asc", requestedUrl().queryParameter("sort"))
        }

    @Test
    fun `a row without an id is dropped, and the server's total is left alone`() =
        runTest {
            // It cannot be opened, so offering it produces a tap that does nothing. Lowering the
            // total to match would hide the fault instead of surfacing it.
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"ACTIVE"},{"name":"B","status":"ACTIVE"}],
                 "page":0,"size":25,"totalElements":2,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val page = (repository.search(MissionQuery.NONE) as ApiResult.Success).value

            assertEquals(listOf("m1"), page.missions.map { it.id })
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `a status this build has never heard of still renders`() =
        runTest {
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"BRIEFING"}],
                 "page":0,"size":25,"totalElements":1,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val mission = (repository.search(MissionQuery.NONE) as ApiResult.Success).value.missions.first()

            assertEquals(MissionStatus.UNKNOWN, mission.status)
            assertEquals("BRIEFING", mission.rawStatus)
        }

    @Test
    fun `an unparseable timestamp costs that row its label, not the page`() =
        runTest {
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"ACTIVE","plannedStartTime":"tomorrow"}],
                 "page":0,"size":25,"totalElements":1,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val result = repository.search(MissionQuery.NONE)

            assertTrue(result is ApiResult.Success)
            assertNull((result as ApiResult.Success).value.missions.first().plannedStartTime)
        }

    @Test
    fun `an empty page is a success, not a failure`() =
        runTest {
            // "No Einsätze match" and "the list could not be loaded" are different screens, and
            // showing the second for the first is how a member is told something is broken when it
            // is not.
            respond("""{"content":[],"page":0,"size":25,"totalElements":0,"totalPages":0,"sort":[]}""")

            val result = repository.search(MissionQuery.NONE)

            assertTrue(result is ApiResult.Success)
            assertTrue((result as ApiResult.Success).value.missions.isEmpty())
        }

    @Test
    fun `a server error is a failure the caller can show`() =
        runTest {
            respond("""{"title":"boom"}""", HTTP_SERVER_ERROR)

            assertTrue(repository.search(MissionQuery.NONE) is ApiResult.Failure)
        }

    @Test
    fun `more pages are reported when the server says so`() =
        runTest {
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"ACTIVE"}],
                 "page":0,"size":25,"totalElements":$MANY_ELEMENTS,"totalPages":$MANY_PAGES,"sort":[]}
                """.trimIndent(),
            )

            val page = (repository.search(MissionQuery.NONE) as ApiResult.Success).value

            assertTrue(page.hasMore)
            assertEquals(MANY_ELEMENTS, page.totalElements)
        }
}

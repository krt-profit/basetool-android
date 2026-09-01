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
import mockwebserver3.RecordedRequest
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
 * The member's own stock — the app's first writes.
 *
 * The assertions that carry the most are the two about `version`: it is read from the server,
 * echoed unchanged on the next save, and a server that refuses the echo must surface as a conflict
 * the member can act on rather than as a generic error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalInventoryRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_CONFLICT = 409

        const val VERSION = 7L
        const val QUANTITY = 12
        const val UEX_ID = 4711

        val PAGE =
            """
            {"content": [
               {"id": "p1", "name": "Medpens", "note": "Notfallkiste", "quantity": $QUANTITY,
                "locationUexId": $UEX_ID, "locationType": "SPACE_STATION",
                "locationName": "Everus Harbour", "version": $VERSION},
               {"name": "eine Zeile ohne id", "quantity": 1}
             ],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        val SAVED =
            """
            {"id": "p1", "name": "Medpens", "quantity": $QUANTITY, "locationUexId": $UEX_ID,
             "locationType": "CITY", "locationName": "Lorville", "version": ${VERSION + 1}}
            """.trimIndent()

        val LOCATIONS =
            """
            [{"uexId": $UEX_ID, "type": "SPACE_STATION", "name": "Everus Harbour",
              "starSystemName": "Stanton", "parentName": "Hurston"},
             {"uexId": 12, "type": "CITY", "name": "Lorville"}]
            """.trimIndent()

        val CONFLICT =
            """
            {"type": "https://profit-base.online/problems/optimistic-lock",
             "title": "Gleichzeitige Änderung", "status": $HTTP_CONFLICT,
             "detail": "Der Eintrag wurde zwischenzeitlich geändert.", "code": "OPTIMISTIC_LOCK"}
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: PersonalInventoryRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            PersonalInventoryRepository(
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
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader(
                    "Content-Type",
                    if (code ==
                        HTTP_CONFLICT
                    ) {
                        "application/problem+json"
                    } else {
                        "application/json"
                    },
                )
                .body(body)
                .build(),
        )
    }

    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    private fun sentBody(request: RecordedRequest) = Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    private fun draft() =
        PersonalItemDraft(
            name = "Medpens",
            quantity = QUANTITY,
            locationUexId = UEX_ID,
            locationKind = PersonalLocationKind.CITY,
            note = "Notfallkiste",
        )

    @Test
    fun `a page carries the row, its place and its version`() =
        runTest {
            respond(PAGE)

            val result = repository.page()

            val page = (result as ApiResult.Success).value
            assertEquals(1, page.items.size)
            val item = page.items.first()
            assertEquals("Medpens", item.name)
            assertEquals(QUANTITY, item.quantity)
            assertEquals(PersonalLocationKind.SPACE_STATION, item.locationKind)
            assertEquals("Everus Harbour", item.locationName)
            assertEquals(VERSION, item.version)
        }

    @Test
    fun `a row without an id is dropped, and the server's total is kept`() =
        runTest {
            // It cannot be opened, edited or deleted, so offering it produces a tap that does
            // nothing. Lowering the total to match would hide the fault instead of showing it.
            respond(PAGE)

            val page = (repository.page() as ApiResult.Success).value

            assertEquals(1, page.items.size)
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `a search sends the term unencoded and once`() =
        runTest {
            respond(PAGE)

            repository.page(query = "  Med & Pens  ")

            assertEquals("Med & Pens", requestedUrl().queryParameter("q"))
        }

    @Test
    fun `a blank search sends no term at all`() =
        runTest {
            respond(PAGE)

            repository.page(query = "   ")

            assertNull(requestedUrl().queryParameter("q"))
        }

    @Test
    fun `a create sends what the member typed and nothing else`() =
        runTest {
            // No version on a create: there is nothing yet to conflict with, and the contract's
            // required list says so (REQ-API-009).
            respond(SAVED, HTTP_CREATED)

            repository.create(draft())

            val body = sentBody(server.takeRequest())
            assertEquals("Medpens", body["name"]?.jsonPrimitive?.content)
            assertEquals(QUANTITY.toString(), body["quantity"]?.jsonPrimitive?.content)
            assertEquals(UEX_ID.toString(), body["locationUexId"]?.jsonPrimitive?.content)
            assertEquals("CITY", body["locationType"]?.jsonPrimitive?.content)
            assertNull("a create carries no version", body["version"])
        }

    @Test
    fun `an update echoes the version it read`() =
        runTest {
            respond(SAVED)

            repository.update(id = "p1", version = VERSION, draft = draft())

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals(VERSION.toString(), sentBody(request)["version"]?.jsonPrimitive?.content)
        }

    @Test
    fun `a save returns the new version, which the next save has to echo`() =
        runTest {
            // The response is not a courtesy: a client that kept its old version would 409 on its
            // own second save.
            respond(SAVED)

            val saved = (repository.update("p1", VERSION, draft()) as ApiResult.Success).value

            assertEquals(VERSION + 1, saved.version)
        }

    @Test
    fun `a conflict is reported as one, not as a generic failure`() =
        runTest {
            respond(CONFLICT, HTTP_CONFLICT)

            val result = repository.update("p1", VERSION, draft())

            assertTrue(
                "expected OptimisticLock, got $result",
                (result as ApiResult.Failure).error is ApiError.OptimisticLock,
            )
        }

    @Test
    fun `a delete succeeds on a body-less answer`() =
        runTest {
            // 204 has nothing to parse. Running it through the response parser would turn every
            // successful delete into a reported server error.
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())

            val result = repository.delete("p1")

            assertTrue("expected success, got $result", result is ApiResult.Success)
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun `the picker asks for one place more than it shows`() =
        runTest {
            // This endpoint answers a bare array with no total, so the extra row IS the overflow
            // signal (ADR-0104). Asking for exactly the render cap left „25 back" meaning both
            // „that is all of them" and „there are more", which the screen then had to guess at.
            respond(LOCATIONS)

            val page = (repository.locations("ever") as ApiResult.Success).value

            assertEquals(
                (PersonalInventoryRepository.LOCATION_LIMIT + 1).toString(),
                requestedUrl().queryParameter("limit"),
            )
            assertEquals(2, page.rows.size)
            assertEquals(PersonalLocationKind.SPACE_STATION, page.rows.first().kind)
            assertEquals("Stanton", page.rows.first().system)
            assertNull("an absent parent stays absent", page.rows.last().parent)
            assertFalse("two rows out of a probe of 26 is the whole answer", page.more)
        }

    @Test
    fun `a full page reports the overflow and never renders the sentinel`() =
        runTest {
            val cap = PersonalInventoryRepository.LOCATION_LIMIT
            respond(
                (0..cap).joinToString(prefix = "[", postfix = "]") {
                    """{"uexId": $it, "type": "CITY", "name": "Ort $it"}"""
                },
            )

            val page = (repository.locations("o") as ApiResult.Success).value

            // The probe row is counted and then dropped: the member sees the cap, not cap + 1.
            assertEquals(cap, page.rows.size)
            assertTrue(page.more)
        }

    @Test
    fun `exactly a full page without the sentinel is a complete answer`() =
        runTest {
            val cap = PersonalInventoryRepository.LOCATION_LIMIT
            respond(
                (1..cap).joinToString(prefix = "[", postfix = "]") {
                    """{"uexId": $it, "type": "CITY", "name": "Ort $it"}"""
                },
            )

            val page = (repository.locations("o") as ApiResult.Success).value

            // The case the old `size >= LIMIT` check got wrong: 25 places and no 26th means the
            // catalogue holds 25, and the screen claimed it was hiding something.
            assertEquals(cap, page.rows.size)
            assertFalse(page.more)
        }

    @Test
    fun `a place kind this build does not know does not break the row`() =
        runTest {
            respond("""[{"uexId": 1, "type": "MOON_OUTPOST", "name": "Irgendwo"}]""")

            val places = (repository.locations("x") as ApiResult.Success).value.rows

            assertEquals(PersonalLocationKind.UNKNOWN, places.single().kind)
        }
}

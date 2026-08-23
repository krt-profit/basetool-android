/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * The hangar reads, whose interesting part is the flattening: the wire nests type, manufacturer and
 * location, and the card shows their names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HangarRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** The aggregate row's count, distinctive in an assertion. */
        const val THREE = 3L

        /** How many of them are fitted. */
        const val TWO = 2L

        const val HTTP_NO_CONTENT = 204

        /** The version the fixture's ship was read at. */
        const val VERSION = 4L

        val SAVED_SHIP =
            """
            {"id": "s1", "name": "Meridian", "shipType": {"id": "t1", "name": "Carrack"},
             "insurance": "LTI", "location": {"id": "l1", "name": "ARC-L1"}, "fitted": true,
             "version": $VERSION}
            """.trimIndent()

        val SHIP_TYPES =
            """
            {"content": [
               {"id": "t1", "name": "Carrack", "manufacturer": {"name": "Anvil Aerospace"}},
               {"id": "t2", "name": "Prospector", "manufacturer": {"name": "MISC"}},
               {"name": "ein Typ ohne id"}
             ],
             "page": 0, "totalElements": 3, "totalPages": 1}
            """.trimIndent()

        const val HOME_LOCATIONS = """[{"id": "l1", "name": "ARC-L1"}, {"name": "ohne id"}]"""

        val SHIPS =
            """
            {
              "content": [
                {"id": "s1", "name": "Meridian", "fitted": true, "insurance": "LTI",
                 "version": $VERSION,
                 "shipType": {"id": "t1", "name": "Carrack",
                              "manufacturer": {"id": "m1", "name": "Anvil Aerospace"}},
                 "location": {"id": "l1", "name": "ARC-L1"}},
                {"id": "s2", "fitted": false,
                 "shipType": {"id": "t2", "name": "Prospector"}}
              ],
              "page": 0, "size": 25, "totalElements": 2, "totalPages": 1, "sort": []
            }
            """.trimIndent()

        val OVERVIEW =
            """
            {
              "content": [
                {"shipType": {"name": "Carrack", "manufacturer": {"name": "Anvil Aerospace"}},
                 "count": 3, "fittedCount": 2}
              ],
              "page": 0, "totalElements": 1, "totalPages": 1
            }
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: HangarRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            HangarRepository(
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
    private fun draft() =
        ShipDraft(
            name = "Meridian",
            typeId = "t1",
            insurance = "LTI",
            locationId = "l1",
            fitted = true,
        )

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
    fun `a ship is flattened to what the card shows`() =
        runTest {
            respond(SHIPS)

            val page = (repository.myShips() as ApiResult.Success).value

            val first = page.ships.first()
            assertEquals("Meridian", first.name)
            assertEquals("Carrack", first.typeName)
            assertEquals("Anvil Aerospace", first.manufacturerName)
            assertEquals("ARC-L1", first.locationName)
            assertEquals("LTI", first.insurance)
            assertTrue(first.fitted)
        }

    @Test
    fun `a ship without a name, maker, place or insurance is still a ship`() =
        runTest {
            // All four are optional in the web app's own form. A card missing them must render, not
            // vanish.
            respond(SHIPS)

            val second = (repository.myShips() as ApiResult.Success).value.ships[1]

            assertNull(second.name)
            assertNull(second.manufacturerName)
            assertNull(second.locationName)
            assertNull(second.insurance)
            assertEquals("Prospector", second.typeName)
            assertFalse(second.fitted)
        }

    @Test
    fun `a blank name is treated as no name`() =
        runTest {
            respond(
                """{"content": [{"id": "s1", "name": "   ", "shipType": {"name": "Aurora"}}],
                    "page": 0, "totalElements": 1, "totalPages": 1}""",
            )

            assertNull((repository.myShips() as ApiResult.Success).value.ships.first().name)
        }

    @Test
    fun `a row without an id is dropped and the server total stands`() =
        runTest {
            respond(
                """{"content": [{"shipType": {"name": "Aurora"}}, {"id": "s1", "shipType": {"name": "Titan"}}],
                    "page": 0, "totalElements": 2, "totalPages": 1}""",
            )

            val page = (repository.myShips() as ApiResult.Success).value
            assertEquals(1, page.ships.size)
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `the aggregate keeps the server's counts`() =
        runTest {
            respond(OVERVIEW)

            val page = (repository.orgOverview() as ApiResult.Success).value

            val row = page.types.single()
            assertEquals("Carrack", row.typeName)
            assertEquals("Anvil Aerospace", row.manufacturerName)
            assertEquals(THREE, row.count)
            assertEquals(TWO, row.fittedCount)
        }

    @Test
    fun `a blank filter is left off the wire`() =
        runTest {
            respond(SHIPS)

            repository.myShips(search = "   ")

            assertNull(requestedUrl().queryParameter("search"))
        }

    @Test
    fun `a filter is encoded exactly once`() =
        runTest {
            respond(SHIPS)

            repository.myShips(search = "Carrack & Co")

            assertEquals("Carrack & Co", requestedUrl().queryParameter("search"))
        }

    @Test
    fun `the aggregate is read from its own path, never from the all-ships one`() =
        runTest {
            // `/hangar/ships` reads every member's ships behind a permission most members lack, and
            // is deliberately not on the vhost's allow-list.
            respond(OVERVIEW)

            repository.orgOverview()

            assertEquals("/api/v1/hangar/squadron-overview", requestedUrl().encodedPath)
        }

    @Test
    fun `a ship carries the ids and the version an edit has to send back`() =
        runTest {
            // A read-only card had no use for these; a writing one cannot save without them.
            respond(SHIPS)

            val ship = (repository.myShips() as ApiResult.Success).value.ships.first()

            assertEquals("t1", ship.typeId)
            assertEquals("l1", ship.locationId)
            assertEquals(VERSION, ship.version)
        }

    @Test
    fun `a create sends the hull and the insurance, and no version`() =
        runTest {
            // There is nothing yet to conflict with, and the frozen contract records the
            // difference (REQ-API-009).
            respond(SAVED_SHIP)

            repository.create(draft())

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/hangar/ships", request.target.substringBefore('?'))
            val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals("t1", body["shipTypeId"]?.jsonPrimitive?.content)
            assertEquals("LTI", body["insurance"]?.jsonPrimitive?.content)
            assertNull(body["version"])
        }

    @Test
    fun `an update echoes the version and goes to the ship's own path`() =
        runTest {
            respond(SAVED_SHIP)

            repository.update(id = "s1", version = VERSION, draft = draft())

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/hangar/ships/s1", request.target.substringBefore('?'))
            assertEquals(
                VERSION.toString(),
                Json.parseToJsonElement(request.body!!.utf8())
                    .jsonObject["version"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `a write never goes to the admin path`() =
        runTest {
            // /hangar/users/{id}/ships names a member and is the admin surface. The vhost does not
            // admit it and neither does this client.
            respond(SAVED_SHIP)

            repository.update(id = "s1", version = VERSION, draft = draft())

            assertFalse(server.takeRequest().target.contains("/users/"))
        }

    @Test
    fun `a saved ship without an id is a broken contract, not a silent drop`() =
        runTest {
            // The list is keyed by id. A row that cannot be keyed would vanish on the next render
            // with nothing said about it.
            respond("""{"name": "Meridian"}""")

            val result = repository.update(id = "s1", version = VERSION, draft = draft())

            assertTrue("expected a failure, got $result", result is ApiResult.Failure)
        }

    @Test
    fun `a delete answers on 204`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())

            val result = repository.delete("s1")

            assertTrue("expected success, got $result", result is ApiResult.Success)
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun `the hull picker matches on the maker as well as the hull`() =
        runTest {
            // "Anvil" is how somebody looks for a Carrack they cannot spell.
            respond(SHIP_TYPES)

            val hulls = (repository.shipTypes("anvil") as ApiResult.Success).value

            assertEquals(listOf("Carrack"), hulls.map { it.name })
        }

    @Test
    fun `a hull without an id never reaches the picker`() =
        runTest {
            respond(SHIP_TYPES)

            val hulls = (repository.shipTypes("") as ApiResult.Success).value

            assertEquals(2, hulls.size)
        }

    @Test
    fun `the places come from the home-location list`() =
        runTest {
            respond(HOME_LOCATIONS)

            val places = (repository.homeLocations() as ApiResult.Success).value

            assertEquals(
                "/api/v1/locations/home-locations",
                server.takeRequest().target.substringBefore('?'),
            )
            assertEquals(listOf("ARC-L1"), places.map { it.name })
        }
}

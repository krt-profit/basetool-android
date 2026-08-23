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

        val SHIPS =
            """
            {
              "content": [
                {"id": "s1", "name": "Meridian", "fitted": true, "insurance": "LTI",
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
}

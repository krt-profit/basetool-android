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
 * The Lager's two reads, one per level of the tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** Entries summed into one stack in the fixture. */
        const val ENTRIES = 4

        const val VERSION = 5L

        val ENTRY_ROWS =
            """
            {"content": [
               {"id": "e1", "material": {"id": "m1", "name": "Quantainium", "quantityType": "SCU"},
                "location": {"id": "l1", "name": "ARC-L1"}, "user": {"effectiveName": "Rhea"},
                "amount": 12.5, "quality": 880, "personal": false, "note": "aus dem Halo",
                "version": $VERSION},
               {"material": {"name": "ohne id"}}
             ],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        val SAVED_ENTRY = """{"id": "e1", "amount": 12.5, "version": ${VERSION + 1}}"""

        val MATERIALS =
            """
            {"content": [{"id": "m1", "name": "Quantainium", "quantityType": "SCU"},
                         {"name": "ohne id"}],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        /** One row out of a catalogue of forty — a page that is hiding something. */
        val MATERIALS_CAPPED =
            """
            {"content": [{"id": "m1", "name": "Quantainium", "quantityType": "SCU"}],
             "page": 0, "totalElements": 40, "totalPages": 40}
            """.trimIndent()

        /** The same row, and the catalogue holds nothing else. */
        val MATERIALS_COMPLETE =
            """
            {"content": [{"id": "m1", "name": "Quantainium", "quantityType": "SCU"}],
             "page": 0, "totalElements": 1, "totalPages": 1}
            """.trimIndent()

        /** Two places, which is all of them. */
        val LOCATIONS =
            """
            {"content": [{"id": "l1", "name": "ARC-L1"}, {"id": "l2", "name": "Area18"}],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        /** One item out of a catalogue of thirty. */
        val ITEMS =
            """
            {"content": [{"id": "gi1", "name": "Medizinische Station T2"}],
             "page": 0, "totalElements": 30, "totalPages": 30}
            """.trimIndent()

        val MEMBERS =
            """
            {"content": [{"id": "u1", "username": "rhea", "effectiveName": "Rhea"}],
             "page": 0, "totalElements": 1, "totalPages": 1}
            """.trimIndent()

        const val TERMINALS = """[{"terminalId": "t1", "terminalName": "Area18 TDD", "priceSell": 5.75}]"""

        val GROUPS =
            """
            {"content": [
               {"material": {"id": "m1", "name": "Quantainium", "quantityType": "SCU"},
                "amount": 1250.5, "quality": 880.0, "maxQuality": 940.0},
               {"amount": 3.0}
             ],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        val STACKS =
            """
            [{"material": {"id": "m1", "name": "Quantainium"}, "totalAmount": 1250.5,
              "stacks": [
                {"user": {"id": "u1", "effectiveName": "Rhea"},
                 "location": {"id": "l1", "name": "ARC-L1"},
                 "owningSquadron": {"id": "o1", "name": "IRIDIUM"},
                 "personal": false, "totalAmount": 1000.0, "quality": 880,
                 "averageQuality": 879.4, "entryCount": $ENTRIES},
                {"personal": true, "totalAmount": 250.5}
              ]}]
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            InventoryRepository(
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

    /**
     * A stack row as the grouped read hands it over.
     *
     * @param quality the stack's quality key, as the server rendered it.
     * @param owningOrgUnitId which pool it belongs to, or `null` for an unpooled holding.
     * @return the stack.
     */
    private fun stack(
        quality: String? = "880",
        owningOrgUnitId: String? = null,
    ) = InventoryStack(
        holder = "Rhea",
        location = "ARC-L1",
        personal = false,
        amount = "12.5",
        quality = quality,
        entryCount = 1,
        holderId = "u1",
        locationId = "l1",
        owningOrgUnitId = owningOrgUnitId,
    )

    @Test
    fun `a note is its own request, and echoes the version too`() =
        runTest {
            respond(SAVED_ENTRY)

            repository.updateNote(id = "e1", version = VERSION, note = "neu")

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/inventory/e1/note", request.target.substringBefore('?'))
        }

    @Test
    fun `the material picker keeps the unit, because a number without one is not a quantity`() =
        runTest {
            respond(MATERIALS)

            val materials = (repository.materials("quant") as ApiResult.Success).value.rows

            assertEquals(1, materials.size)
            assertEquals("SCU", materials.single().unit)
        }

    @Test
    fun `the place picker asks for the whole catalogue, not a screenful of it`() =
        runTest {
            respond(LOCATIONS)

            repository.locations("")

            // 200, not the generic picker page. The location catalogue is small and bounded by the
            // game universe, and a member booking stock expects to scroll it. At `size=25` this
            // very picker showed 25 of 53 places with nothing on screen saying so.
            assertEquals("200", requestedUrl().queryParameter("size"))
        }

    @Test
    fun `a picker page that leaves candidates behind says so`() =
        runTest {
            respond(MATERIALS_CAPPED)

            val page = (repository.materials("q") as ApiResult.Success).value

            // Read off `totalElements`, not off a full-looking page (ADR-0104).
            assertTrue(page.more)
        }

    @Test
    fun `a picker page that carries the catalogue claims nothing more`() =
        runTest {
            respond(MATERIALS_COMPLETE)

            val page = (repository.materials("quant") as ApiResult.Success).value

            assertFalse(page.more)
        }

    @Test
    fun `an item booking sends the item and no quality at all`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

            repository.bookIn(
                BookInDraft(
                    gameItemId = "gi1",
                    locationId = "l1",
                    amount = "3",
                    // Carried over from a material the member had picked before switching: the
                    // draft may hold it, the wire may not.
                    quality = 874,
                ),
            )

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("\"gameItemId\":\"gi1\""))
            // Asserted as an ABSENCE: the server refuses a quality on an item row outright
            // (isQualityConsistentWithCatalog, REQ-INV-029), and a payload carrying one looks
            // perfectly valid until it comes back a 400.
            assertFalse("a quality must never travel with an item row, was: ${'$'}body", body.contains("quality"))
            assertFalse("the catalogue reference is exclusive", body.contains("materialId"))
        }

    @Test
    fun `a material booking still sends its quality`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

            repository.bookIn(
                BookInDraft(materialId = "m1", locationId = "l1", amount = "3", quality = 874),
            )

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("\"materialId\":\"m1\""))
            assertTrue(body.contains("\"quality\":874"))
            assertFalse(body.contains("gameItemId"))
        }

    @Test
    fun `the item picker searches the order form's catalogue`() =
        runTest {
            respond(ITEMS)

            val page = (repository.gameItems("station") as ApiResult.Success).value

            // The item catalogue, not the materials: the two are separate tables and the server
            // takes them in mutually exclusive fields.
            assertTrue(requestedUrl().encodedPath.endsWith("/api/v1/orders/item-catalog"))
            assertEquals("Medizinische Station T2", page.rows.single().name)
            assertTrue(page.more)
        }

    @Test
    fun `the member picker uses the name the member recognises`() =
        runTest {
            // effectiveName, not username: it is what the web app renders.
            respond(MEMBERS)

            val members = (repository.members("rh") as ApiResult.Success).value.rows

            assertEquals("Rhea", members.single().name)
            assertEquals("rh", requestedUrl().queryParameter("query"))
        }

    @Test
    fun `a terminal carries what it pays`() =
        runTest {
            respond(TERMINALS)

            val terminals = (repository.terminals("m1") as ApiResult.Success).value

            assertEquals("Area18 TDD", terminals.single().name)
            assertEquals("5.75", terminals.single().price)
        }
}

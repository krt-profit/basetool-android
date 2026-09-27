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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
 * The grouped Lager reads of both scopes and the „Mein Lager" writes, against the wire the backend's
 * `openapi.json` describes (REQ-INV-007, -036, -046, -052).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LagerRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val VERSION = 7L

        val MY_MATERIALS =
            """
            [{"material": {"id": "m1", "name": "Quantainium", "quantityType": "SCU"}, "totalAmount": 522.0,
              "stacks": [
                {"user": {"id": "u1"}, "location": {"id": "l1", "name": "ARC-L1"},
                 "owningSquadron": {"id": "o1", "name": "Bereich Profit"},
                 "personal": false, "totalAmount": 442.0, "quality": 874, "entryCount": 1},
                {"user": {"id": "u1"}, "location": {"id": "l1", "name": "ARC-L1"},
                 "personal": true, "stolen": true, "totalAmount": 80.0, "quality": 874, "entryCount": 1}
              ]}]
            """.trimIndent()

        val MY_ITEMS =
            """
            [{"gameItem": {"id": "gi1", "name": "Medpen (Hemozal)"}, "totalAmount": 24.0,
              "stacks": [{"location": {"id": "l2", "name": "Lorville"}, "personal": true, "totalAmount": 24.0}]}]
            """.trimIndent()

        const val ENTRIES =
            """{"content": [{"id": "e1", "gameItem": {"id": "gi1", "name": "Medpen (Hemozal)"},
                "amount": 24.0, "personal": true, "stolen": true, "version": 3}],
               "page": 0, "totalElements": 1, "totalPages": 1}"""

        const val ROW = """{"id": "e2", "amount": 40.0, "version": 8}"""

        fun entry(personal: Boolean) =
            InventoryEntry(
                id = "e1",
                materialName = "Laranite",
                materialId = "m2",
                unit = "SCU",
                locationName = "Lorville",
                locationId = "l2",
                holder = null,
                holderId = "u1",
                amount = "80",
                quality = "733",
                personal = personal,
                note = null,
                version = VERSION,
            )
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: LagerRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            LagerRepository(httpClient = OkHttpClient(), baseUrl = server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private fun RecordedRequest.params(name: String): List<String> = url.queryParameterValues(name).filterNotNull()

    private fun RecordedRequest.json() = Json.parseToJsonElement(body?.utf8().orEmpty()).jsonObject

    @Test
    fun `Mein Lager reads both catalogues with the filter, materials first`() =
        runTest {
            respond(MY_MATERIALS)
            respond(MY_ITEMS)

            val result =
                repository.grouped(
                    LagerScope.MY,
                    LagerFilter(personal = PersonalFilter.PERSONAL_ONLY, locationIds = setOf("l1")),
                ) as ApiResult.Success

            val materials = server.takeRequest()
            val items = server.takeRequest()
            assertEquals("/api/v1/inventory/my-inventory/grouped", materials.url.encodedPath)
            assertEquals(listOf("true"), materials.params("personalOnly"))
            assertEquals(listOf("l1"), materials.params("locationIds"))
            assertTrue(materials.params("catalog").isEmpty())
            assertEquals(listOf("ITEM"), items.params("catalog"))
            assertEquals(listOf("m1", null), result.value.map { it.group.materialId })
            assertEquals("gi1", result.value[1].group.gameItemId)
            assertEquals("item:gi1", result.value[1].group.key)
            assertEquals("PIECE", result.value[1].group.unit)
        }

    @Test
    fun `a grouped stack carries its unit, its personal flag and the stolen marker`() =
        runTest {
            respond(MY_MATERIALS)
            respond("[]")

            val stacks = (repository.grouped(LagerScope.MY, LagerFilter()) as ApiResult.Success).value.single().stacks

            assertEquals("Bereich Profit", stacks[0].owningOrgUnitName)
            assertFalse(stacks[0].personal)
            assertTrue(stacks[1].personal)
            assertTrue(stacks[1].stolen)
            assertNull(stacks[1].owningOrgUnitId)
        }

    @Test
    fun `the filtered Org-Lager reads the org's grouped view without a personal dimension`() =
        runTest {
            respond(MY_MATERIALS)

            repository.grouped(
                LagerScope.ORG,
                LagerFilter(personal = PersonalFilter.SHARED_ONLY, stolen = StolenFilter.WITHOUT),
            )

            val request = server.takeRequest()
            assertEquals("/api/v1/inventory/all/grouped", request.url.encodedPath)
            assertEquals(listOf("true"), request.params("nonStolenOnly"))
            assertTrue(request.params("nonPersonalOnly").isEmpty())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a Mein Lager stack is read by its whole identity`() =
        runTest {
            respond(ENTRIES)
            val group = InventoryGroup(null, "Medpen (Hemozal)", "PIECE", "24", null, null, gameItemId = "gi1")
            val stack =
                InventoryStack(
                    holder = null,
                    location = "Lorville",
                    personal = true,
                    amount = "24",
                    quality = null,
                    entryCount = 1,
                    locationId = "l2",
                    owningOrgUnitId = "o1",
                    stolen = true,
                )

            val entries = (repository.stackEntries(LagerScope.MY, group, stack) as ApiResult.Success).value

            val request = server.takeRequest()
            assertEquals("/api/v1/inventory/my-inventory/stack/entries", request.url.encodedPath)
            assertEquals(listOf("gi1"), request.params("gameItemId"))
            assertEquals(listOf("ITEM"), request.params("catalog"))
            assertEquals(listOf("l2"), request.params("locationId"))
            assertEquals(listOf("true"), request.params("personal"))
            assertEquals(listOf("true"), request.params("stolen"))
            assertEquals(listOf("o1"), request.params("owningOrgUnitId"))
            assertTrue(request.params("userId").isEmpty())
            assertEquals("Medpen (Hemozal)", entries.single().materialName)
            assertEquals("PIECE", entries.single().unit)
            assertTrue(entries.single().stolen)
        }

    @Test
    fun `select all asks per kind and per catalogue and keeps each row's kind`() =
        runTest {
            respond("""["p1"]""")
            respond("""["p2"]""")
            respond("""["s1"]""")
            respond("[]")

            val ids = (repository.myEntryIds(LagerFilter()) as ApiResult.Success).value

            assertEquals(mapOf("p1" to true, "p2" to true, "s1" to false), ids)
            val first = server.takeRequest()
            assertEquals("/api/v1/inventory/my-inventory/entry-ids", first.url.encodedPath)
            assertEquals(listOf("true"), first.params("personalOnly"))
            assertEquals(listOf("ITEM"), server.takeRequest().params("catalog"))
            assertEquals(listOf("true"), server.takeRequest().params("nonPersonalOnly"))
        }

    @Test
    fun `a personal row moving into the shared Lager sends its pool and its version`() =
        runTest {
            respond(ROW)

            repository.rebookPersonal(entry(personal = true), "40", "o1", mergeStock = true)

            val request = server.takeRequest()
            assertEquals("/api/v1/inventory/e1/personal-rebook", request.url.encodedPath)
            val body = request.json()
            assertEquals("40.0", body.getValue("amount").jsonPrimitive.content)
            assertEquals(VERSION.toString(), body.getValue("version").jsonPrimitive.content)
            assertEquals("o1", body.getValue("targetOwningOrgUnitId").jsonPrimitive.content)
            assertEquals("true", body.getValue("mergeStock").jsonPrimitive.content)
        }

    @Test
    fun `a shared row becoming personal sends no pool, since it keeps its own`() =
        runTest {
            respond(ROW)

            repository.rebookPersonal(entry(personal = false), "80", "o1", mergeStock = false)

            val body = server.takeRequest().json()
            assertTrue(body["targetOwningOrgUnitId"] == null || body["targetOwningOrgUnitId"] == JsonNull)
        }

    @Test
    fun `the selection rebooks in the personal modes and reports what it skipped`() =
        runTest {
            respond("""{"rebooked": 1, "skipped": 1}""")
            respond("""{"rebooked": 2, "skipped": 0}""")

            val shared =
                repository.bulkRebookPersonal(listOf("a", "b"), personal = false, "o1", mergeStock = true)
                    as ApiResult.Success
            repository.bulkRebookPersonal(listOf("a"), personal = true, "o1", mergeStock = false)

            assertEquals(BulkRebookResult(1, 1), shared.value)
            val first = server.takeRequest().json()
            assertEquals("DEPERSONALIZE", first.getValue("mode").jsonPrimitive.content)
            assertEquals("o1", first.getValue("targetOwningOrgUnitId").jsonPrimitive.content)
            assertEquals(2, first.getValue("itemIds").jsonArray.size)
            val second = server.takeRequest().json()
            assertEquals("PERSONALIZE", second.getValue("mode").jsonPrimitive.content)
            assertTrue(second["targetOwningOrgUnitId"] == null || second["targetOwningOrgUnitId"] == JsonNull)
        }

    @Test
    fun `the org-unit change can set no unit at all`() =
        runTest {
            respond(ROW)

            repository.changeOrgUnit(entry(personal = true), null, mergeStock = false)

            val request = server.takeRequest()
            assertEquals("/api/v1/inventory/e1/org-unit", request.url.encodedPath)
            val body = request.json()
            assertEquals(VERSION.toString(), body.getValue("version").jsonPrimitive.content)
            assertTrue(body["targetOwningOrgUnitId"] == null || body["targetOwningOrgUnitId"] == JsonNull)
        }

    @Test
    fun `the selection's org-unit change reports changed and skipped`() =
        runTest {
            respond("""{"changed": 2, "skipped": 1}""")

            val result =
                repository.bulkChangeOrgUnit(
                    listOf("a", "b", "c"),
                    "o1",
                    mergeStock = false,
                ) as ApiResult.Success

            assertEquals(BulkChangeResult(2, 1), result.value)
            val request = server.takeRequest()
            assertEquals("/api/v1/inventory/bulk-org-unit", request.url.encodedPath)
            assertEquals("o1", request.json().getValue("targetOwningOrgUnitId").jsonPrimitive.content)
        }
}

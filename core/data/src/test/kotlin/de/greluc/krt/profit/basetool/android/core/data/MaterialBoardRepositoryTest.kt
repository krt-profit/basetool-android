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
 * The Materialbörse reads and writes.
 *
 * Two rules carry real weight. An item row names itself in different fields from a material row, so
 * reading only the material ones renders every item blank; and the unit travels with the row,
 * because an item counted in pieces and labelled „SCU" is a quantity a member would act on off-tool.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialBoardRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** The pledge count and version the fixture answers with after the toggle. */
        const val PLEDGES_AFTER = 3
        const val VERSION_AFTER = 5L

        /** One material offer of somebody else's, and one item offer of the caller's own. */
        val OFFERS =
            """
            {"content": [
               {"id": "o1", "kind": "MATERIAL",
                "material": {"id": "m1", "name": "Quantainium", "quantityType": "SCU"},
                "owner": {"effectiveName": "Vex"},
                "ownerOrgUnits": [{"shorthand": "SK VG", "name": "SK Vanguard"}],
                "mine": false, "quality": 3, "amount": 240.0, "availableAmount": 240.0,
                "releasedAt": "2026-08-20T10:00:00Z", "remark": "Nur am Wochenende",
                "interestCount": 2, "viewerInterested": false,
                "status": "ACTIVE", "version": 4},
               {"id": "o2", "kind": "ITEM",
                "itemName": "Size 3 Shield", "itemQuantity": 6,
                "owner": {"effectiveName": "Nova"},
                "ownerOrgUnits": [], "mine": true,
                "interestCount": 1, "interestedHandles": ["Vex"],
                "viewerInterested": false, "status": "ACTIVE", "version": 1}
             ],
             "page": 0, "totalElements": 2, "totalPages": 1}
            """.trimIndent()

        /** One request, whose quality field is a MINIMUM rather than an offered grade. */
        val REQUESTS =
            """
            {"content": [
               {"id": "q1", "kind": "MATERIAL",
                "material": {"id": "m2", "name": "Titanium", "quantityType": "SCU"},
                "requestedAmount": 100.0, "minQuality": 2,
                "owner": {"effectiveName": "Ash"}, "ownerOrgUnits": [],
                "mine": false, "postedAt": "2026-08-21T08:00:00Z",
                "interestCount": 0, "viewerInterested": false,
                "status": "ACTIVE", "version": 2}
             ],
             "page": 0, "totalElements": 1, "totalPages": 1}
            """.trimIndent()

        /** The same offer after the caller said they can supply. */
        val AFTER_INTEREST =
            """
            {"id": "o1", "kind": "MATERIAL",
             "material": {"id": "m1", "name": "Quantainium", "quantityType": "SCU"},
             "owner": {"effectiveName": "Vex"}, "ownerOrgUnits": [],
             "mine": false, "quality": 3, "amount": 240.0,
             "interestCount": 3, "viewerInterested": true,
             "status": "ACTIVE", "version": 5}
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: MaterialBoardRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MaterialBoardRepository(
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

    @Test
    fun `a material offer maps onto the row`() =
        runTest {
            respond(OFFERS)

            val page = (repository.board(BoardSide.OFFERS) as ApiResult.Success).value

            val first = page.entries.first()
            assertEquals("Quantainium", first.materialName)
            assertFalse(first.unitIsPiece)
            assertEquals("240.0", first.amount)
            assertEquals("Vex", first.ownerName)
            assertEquals(listOf("SK VG"), first.ownerOrgUnits)
            assertEquals("Nur am Wochenende", first.remark)
            assertTrue(first.canSignal)
        }

    @Test
    fun `an item offer reads its own fields and counts pieces`() =
        runTest {
            respond(OFFERS)

            val item = (repository.board(BoardSide.OFFERS) as ApiResult.Success).value.entries[1]

            // An item names itself in itemName/itemQuantity, not in material/amount. Reading only
            // the material fields would render every item row blank with an empty amount.
            assertEquals("Size 3 Shield", item.materialName)
            assertEquals("6", item.amount)
            assertTrue(item.unitIsPiece)
            // The caller's own row: no „Ich kann liefern", because the server refuses it.
            assertFalse(item.canSignal)
            assertEquals(listOf("Vex"), item.interestedHandles)
        }

    @Test
    fun `a row nobody owns carries no supporter list`() =
        runTest {
            respond(OFFERS)

            val other = (repository.board(BoardSide.OFFERS) as ApiResult.Success).value.entries[0]

            // REQ-MARKET-006: the server sends the handles only to the owner. Rendering an empty
            // list instead of nothing would imply nobody had answered.
            assertNull(other.interestedHandles)
        }

    @Test
    fun `a request reads its minimum quality and its own amount field`() =
        runTest {
            respond(REQUESTS)

            val row = (repository.board(BoardSide.REQUESTS) as ApiResult.Success).value.entries.single()

            assertEquals(BoardSide.REQUESTS, row.side)
            assertEquals("Titanium", row.materialName)
            assertEquals("100.0", row.amount)
            assertEquals(2, row.quality)
        }

    @Test
    fun `the interest toggle answers with the updated row`() =
        runTest {
            respond(OFFERS)
            val entry = (repository.board(BoardSide.OFFERS) as ApiResult.Success).value.entries.first()
            server.takeRequest()
            respond(AFTER_INTEREST)

            val updated = (repository.setInterest(entry, interested = true) as ApiResult.Success).value

            // In place, not by re-reading: the count, the caller's flag and the version move
            // together, and a page re-read would scroll the member back to the top.
            assertTrue(updated.viewerInterested)
            assertEquals(PLEDGES_AFTER, updated.interestCount)
            assertEquals(VERSION_AFTER, updated.version)
            assertTrue(server.takeRequest().target.endsWith("/offers/o1/interest"))
        }

    @Test
    fun `taking the pledge back sends a DELETE to the same path`() =
        runTest {
            respond(OFFERS)
            val entry = (repository.board(BoardSide.OFFERS) as ApiResult.Success).value.entries.first()
            server.takeRequest()
            respond(AFTER_INTEREST)

            repository.setInterest(entry, interested = false)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertTrue(request.target.endsWith("/offers/o1/interest"))
        }

    @Test
    fun `a request's writes address the request family, not the offer one`() =
        runTest {
            respond(REQUESTS)
            val entry = (repository.board(BoardSide.REQUESTS) as ApiResult.Success).value.entries.single()
            server.takeRequest()
            respond(REQUESTS.substringAfter("\"content\": [").substringBeforeLast("]").trim().trimEnd(','))

            repository.withdraw(entry)

            // The two halves are different families. One shared path builder is exactly where a
            // request would end up deactivating an offer with the same id.
            assertTrue(server.takeRequest().target.endsWith("/api/v1/material-requests/q1/deactivate"))
        }

    @Test
    fun `creating an offer sends the inventory entry and the amount`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

            repository.createOffer(inventoryItemId = "i1", amount = 12.5, remark = "  ")

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("\"inventoryItemId\":\"i1\""))
            assertTrue(body.contains("\"offeredAmount\":12.5"))
            // A blank remark is dropped rather than sent as an empty string, which the server
            // would store and every reader would render as a note that says nothing.
            assertFalse(body.contains("\"remark\""))
        }

    @Test
    fun `creating a request sends the material, the amount and an optional quality`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_OK).build())

            repository.createRequest(materialId = "m2", amount = 100.0, minQuality = null, remark = "bald")

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("\"materialId\":\"m2\""))
            assertTrue(body.contains("\"requestedAmount\":100.0"))
            assertTrue(body.contains("\"remark\":\"bald\""))
            assertFalse(body.contains("\"minQuality\""))
        }
}

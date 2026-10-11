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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Materialbörse's request half: the verb and exact path of its seven operations, all under
 * `/api/v1/material-exchange/requests`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialBoardRequestPathTest {
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
     * Enqueues one JSON answer.
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
     * The next recorded request as `METHOD path`, without the query.
     *
     * @return the verb and the request path, space-separated.
     */
    private fun nextCall(): String = server.takeRequest().let { "${it.method} ${it.target.substringBefore('?')}" }

    @Test
    fun `the request board is read, posted to and edited under the exchange`() =
        runTest {
            respond(REQUESTS)
            repeat(WRITES) { respond("{}") }
            val entry = (repository.board(BoardSide.REQUESTS) as ApiResult.Success).value.rows.single()

            repository.createRequest(materialId = "m2", amount = 10.0, minQuality = null, remark = null)
            repository.createItemRequest(productKey = "p1", quantity = 2, minQuality = null, remark = null)
            repository.updateRequest(entry, amount = 5.0, minQuality = null, remark = null)
            repository.setInterest(entry, interested = true)
            repository.setInterest(entry, interested = false)
            repository.withdraw(entry)

            assertEquals("GET $BASE", nextCall())
            assertEquals("POST $BASE", nextCall())
            assertEquals("POST $BASE/item", nextCall())
            assertEquals("PUT $BASE/q1", nextCall())
            assertEquals("POST $BASE/q1/interest", nextCall())
            assertEquals("DELETE $BASE/q1/interest", nextCall())
            assertEquals("POST $BASE/q1/deactivate", nextCall())
        }

    private companion object {
        const val HTTP_OK = 200
        const val WRITES = 6
        const val BASE = "/api/v1/material-exchange/requests"

        val REQUESTS =
            """
            {"content": [
               {"id": "q1", "kind": "MATERIAL",
                "material": {"id": "m2", "name": "Titanium", "quantityType": "SCU"},
                "requestedAmount": 100.0, "minQuality": 2,
                "owner": {"effectiveName": "Ash"}, "ownerOrgUnits": [],
                "mine": true, "interestCount": 0, "viewerInterested": false,
                "status": "ACTIVE", "version": 2}
             ],
             "page": 0, "totalElements": 1, "totalPages": 1}
            """.trimIndent()
    }
}

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
 * The Lager's two reads, one per level of the tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryRepositoryTest {
    private companion object {
        const val HTTP_OK = 200

        /** Entries summed into one stack in the fixture. */
        const val ENTRIES = 4

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
                {"user": {"effectiveName": "Rhea"}, "location": {"name": "ARC-L1"},
                 "personal": false, "totalAmount": 1000.0, "averageQuality": 880.0,
                 "entryCount": $ENTRIES},
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

    @Test
    fun `a group row maps onto the model`() =
        runTest {
            respond(GROUPS)

            val page = (repository.groups() as ApiResult.Success).value

            val first = page.groups.first()
            assertEquals("m1", first.materialId)
            assertEquals("Quantainium", first.name)
            assertEquals("SCU", first.unit)
            assertEquals("1250.5", first.amount)
            // "880.0": the repository keeps the Double's own rendering and lets the screen's
            // formatter decide how many digits a member sees.
            assertEquals("880.0", first.quality)
        }

    @Test
    fun `a group without a material id is kept, because it still holds something`() =
        runTest {
            // Dropping it would quietly lower what the tree adds up to. It simply cannot be opened,
            // and the screen reflects that by not offering the tap.
            respond(GROUPS)

            val page = (repository.groups() as ApiResult.Success).value

            assertEquals(2, page.groups.size)
            assertNull(page.groups[1].materialId)
        }

    @Test
    fun `an amount never reaches the screen in scientific notation`() =
        runTest {
            // A Double prints as 1.0E7 past seven digits, and a warehouse figure that reads like a
            // physics constant is one a member cannot check.
            respond(
                """{"content": [{"material": {"id": "m1", "name": "Q"}, "amount": 12500000.0}],
                        "page": 0, "totalElements": 1, "totalPages": 1}""",
            )

            // Kotlin renders this Double as "1.25E7"; the plain form is what a member can read.
            assertEquals("12500000", (repository.groups() as ApiResult.Success).value.groups.first().amount)
        }

    @Test
    fun `a stack maps onto the model`() =
        runTest {
            respond(STACKS)

            val stacks = (repository.stacks("m1") as ApiResult.Success).value

            assertEquals(2, stacks.size)
            val first = stacks.first()
            assertEquals("Rhea", first.holder)
            assertEquals("ARC-L1", first.location)
            assertFalse(first.personal)
            assertEquals("1000.0", first.amount)
            assertEquals(ENTRIES, first.entryCount)
            assertTrue(stacks[1].personal)
        }

    @Test
    fun `the stacks are asked for by material id`() =
        runTest {
            respond(STACKS)

            repository.stacks("m1")

            val url = requestedUrl()
            assertEquals("/api/v1/inventory/all/grouped", url.encodedPath)
            assertEquals("m1", url.queryParameter("materialIds"))
        }

    @Test
    fun `an answer with no group yields no stacks rather than a failure`() =
        runTest {
            // A group emptied between the tree loading and the tap is an ordinary race, not an
            // error to put on screen.
            respond("[]")

            assertTrue((repository.stacks("m1") as ApiResult.Success).value.isEmpty())
        }

    @Test
    fun `the tree's first level is read from the aggregate, never from the flat list`() =
        runTest {
            // `/inventory/all` would pull every entry in the warehouse to draw a dozen headings.
            respond(GROUPS)

            repository.groups()

            assertEquals("/api/v1/inventory/aggregated", requestedUrl().encodedPath)
        }
}

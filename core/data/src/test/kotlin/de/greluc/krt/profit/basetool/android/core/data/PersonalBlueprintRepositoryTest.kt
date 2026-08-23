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
 * The owned blueprints, their craftability and the catalogue behind "hinzufügen".
 *
 * The assertion worth naming: `removable` defaults to **false** when the server is silent. Guessing
 * the other way offers a delete the server then refuses, which reads as a broken button rather than
 * as a rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalBlueprintRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val VERSION = 3L

        /** Rows in the fixture, one of which has no id and is dropped. */
        const val ROWS_IN_FIXTURE = 3L

        /** How many the fixture reports as buildable once refining counts. */
        const val WITH_REFINERY = 2

        val PAGE =
            """
            {"content": [
               {"id": "b1", "productKey": "anvil.hornet", "productName": "F7A Hornet",
                "note": "vom Event", "acquiredAt": "2026-07-01", "removable": true,
                "version": $VERSION},
               {"id": "b2", "productName": "Prospector"},
               {"productName": "eine Zeile ohne id"}
             ],
             "page": 0, "totalElements": 3, "totalPages": 1}
            """.trimIndent()

        val CRAFTABILITY =
            """
            [{"blueprintId": "b1", "recipeResolved": true, "craftable": 0,
              "craftableWithRefinery": 2, "limitingMaterialName": "Quantainium",
              "limitingMaterialNameWithRefinery": null,
              "materials": [
                {"materialName": "Quantainium", "requiredScu": 10.0, "availableScu": 4.0,
                 "missingScu": 6.0, "missingScuWithRefinery": 0.0},
                {"materialName": "Agricium", "requiredScu": 2.0, "availableScu": 9.0,
                 "missingScu": 0.0, "missingScuWithRefinery": 0.0}
              ]},
             {"recipeResolved": true, "craftable": 1}]
            """.trimIndent()

        val PRODUCTS =
            """
            [{"productKey": "anvil.hornet", "name": "F7A Hornet", "manufacturerName": "Anvil",
              "ownedByCurrentUser": true},
             {"name": "ohne Schluessel"}]
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: PersonalBlueprintRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            PersonalBlueprintRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
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

    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    @Test
    fun `a page carries the product, the note and the version`() =
        runTest {
            respond(PAGE)

            val page = (repository.page() as ApiResult.Success).value

            assertEquals(2, page.items.size)
            val first = page.items.first()
            assertEquals("F7A Hornet", first.productName)
            assertEquals("vom Event", first.note)
            assertEquals(VERSION, first.version)
            assertTrue(first.removable)
        }

    @Test
    fun `a row the server did not call removable is not offered a delete`() =
        runTest {
            // Guessing the other way offers an action the server then refuses with a 409, which
            // reads as a broken button rather than as a rule.
            respond(PAGE)

            val page = (repository.page() as ApiResult.Success).value

            assertFalse(page.items.last().removable)
        }

    @Test
    fun `a row without an id is dropped, and the total is left alone`() =
        runTest {
            respond(PAGE)

            val page = (repository.page() as ApiResult.Success).value

            assertEquals(2, page.items.size)
            assertEquals(ROWS_IN_FIXTURE, page.totalElements)
        }

    @Test
    fun `craftability is asked for once, with refining included`() =
        runTest {
            // One call for the whole list: asking per row would be one request per card on a
            // screen that scrolls. Refining is included so the screen can offer both answers
            // without a second round trip.
            respond(CRAFTABILITY)

            val byId = (repository.craftability() as ApiResult.Success).value

            assertEquals("true", requestedUrl().queryParameter("includeRefinery"))
            assertEquals(setOf("b1"), byId.keys)
            val entry = byId.getValue("b1")
            assertEquals(0, entry.craftable)
            assertEquals(WITH_REFINERY, entry.craftableWithRefinery)
            assertEquals("Quantainium", entry.limitingMaterial)
        }

    @Test
    fun `the shortfall count follows whether refining is allowed for`() =
        runTest {
            respond(CRAFTABILITY)

            val entry = (repository.craftability() as ApiResult.Success).value.getValue("b1")

            assertEquals(1, entry.missingCount(withRefinery = false))
            assertEquals(0, entry.missingCount(withRefinery = true))
        }

    @Test
    fun `a craftability entry naming no blueprint is dropped`() =
        runTest {
            // It cannot be shown against a row, and keying it by an empty id would attach it to
            // the wrong one.
            respond(CRAFTABILITY)

            val byId = (repository.craftability() as ApiResult.Success).value

            assertEquals(1, byId.size)
        }

    @Test
    fun `adding sends the catalogue key and nothing invented`() =
        runTest {
            respond("""{"id": "b9", "productKey": "anvil.hornet", "productName": "F7A Hornet", "version": 0}""")

            repository.add(productKey = "anvil.hornet", note = "vom Event")

            val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
            assertEquals("anvil.hornet", body["productKey"]?.jsonPrimitive?.content)
            assertEquals("vom Event", body["note"]?.jsonPrimitive?.content)
            assertNull("a create carries no version", body["version"])
        }

    @Test
    fun `a note change echoes the version and sends nothing else`() =
        runTest {
            // The contract requires `version` and nothing more: acquiredAt is left untouched
            // rather than re-sent, so an app that never offers that field cannot clear it.
            respond("""{"id": "b1", "productName": "F7A Hornet", "note": "neu", "version": 4}""")

            repository.updateNote(id = "b1", version = VERSION, note = "neu")

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals(VERSION.toString(), body["version"]?.jsonPrimitive?.content)
            assertEquals("neu", body["note"]?.jsonPrimitive?.content)
            assertNull(body["acquiredAt"])
        }

    @Test
    fun `the picker asks for a bounded number of products and drops the keyless`() =
        runTest {
            // A row without a key cannot be added, so offering it would be a tap that fails.
            respond(PRODUCTS)

            val products = (repository.products("hornet") as ApiResult.Success).value

            assertEquals(
                PersonalBlueprintRepository.PRODUCT_LIMIT.toString(),
                requestedUrl().queryParameter("limit"),
            )
            assertEquals(1, products.size)
            assertTrue("the picker has to know what is already owned", products.single().owned)
            assertEquals("Anvil", products.single().manufacturer)
        }
}

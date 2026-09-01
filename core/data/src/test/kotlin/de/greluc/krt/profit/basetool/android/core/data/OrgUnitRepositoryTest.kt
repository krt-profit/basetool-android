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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The switcher's two reads, and what the app does with the answers it did not expect.
 *
 * Robolectric because the repository logs through the project facade, which calls
 * `android.util.Log` — unmocked in a plain JVM test, which would fail on the diagnostic rather
 * than on the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrgUnitRepositoryTest {
    private companion object {
        /** A normal answer. */
        const val HTTP_OK = 200

        /** The server is up but broken. */
        const val HTTP_SERVER_ERROR = 500
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: OrgUnitRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            OrgUnitRepository(
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

    @Test
    fun `memberships map onto the model, kinds included`() =
        runTest {
            respond(
                """
                [
                  {"orgUnitId":"a1","orgUnitName":"Staffel 1","orgUnitShorthand":"S1","kind":"SQUADRON"},
                  {"orgUnitId":"b2","orgUnitName":"SK Vanguard","orgUnitShorthand":"SKV",
                   "kind":"SPECIAL_COMMAND","isProfitEligible":false}
                ]
                """.trimIndent(),
            )

            val result = repository.memberships()

            assertTrue(result is ApiResult.Success)
            val units = (result as ApiResult.Success).value
            assertEquals(
                listOf(
                    OrgUnit("a1", "Staffel 1", "S1", OrgUnitKind.SQUADRON),
                    OrgUnit("b2", "SK Vanguard", "SKV", OrgUnitKind.SPECIAL_COMMAND),
                ),
                units,
            )
            // The pinnable-units endpoint, not the membership list: an admin holds no Staffel
            // membership and would otherwise be offered no unit at all (REQ-SEC-048).
            assertEquals("/api/v1/me/org-units", server.takeRequest().target)
        }

    @Test
    fun `a kind this build has never heard of is still offered`() =
        runTest {
            // The reader coerces an unknown enum constant to null (REQ-APP-API-005), which is what
            // keeps a server-side addition from crashing a build in the field. The unit is
            // perfectly usable — it has an id and a name — so hiding it would lose the member a
            // scope over a label the app could not read.
            respond("""[{"orgUnitId":"c3","orgUnitName":"Neue Einheit","kind":"FLOTTENKOMMANDO"}]""")

            val result = repository.memberships()

            assertEquals(
                listOf(OrgUnit("c3", "Neue Einheit", "", OrgUnitKind.UNKNOWN)),
                (result as ApiResult.Success).value,
            )
        }

    @Test
    fun `an entry without an id is dropped, because it cannot be pinned`() =
        runTest {
            respond(
                """[{"orgUnitId":"a1","orgUnitName":"Staffel 1"},{"orgUnitName":"Ohne Id"}]""",
            )

            val result = repository.memberships()

            assertEquals(listOf("a1"), (result as ApiResult.Success).value.map { it.id })
        }

    @Test
    fun `a unit with no name at all falls back to something pointable`() =
        runTest {
            respond("""[{"orgUnitId":"a1","orgUnitShorthand":"S1"}]""")

            val units = (repository.memberships() as ApiResult.Success).value

            assertEquals("S1", units.single().name)
        }

    @Test
    fun `no memberships is a success, not a failure`() =
        runTest {
            // A member who belongs to nothing yet is an ordinary case — the shell has to render.
            respond("[]")

            assertEquals(emptyList<OrgUnit>(), (repository.memberships() as ApiResult.Success).value)
        }

    @Test
    fun `a server error surfaces as a failure the caller can show`() =
        runTest {
            respond("""{"title":"nope"}""", status = HTTP_SERVER_ERROR)

            assertTrue(repository.memberships() is ApiResult.Failure)
        }

    @Test
    fun `the server default is read, and naming none is still a success`() =
        runTest {
            respond("""{"orgUnitId":"a1"}""")
            assertEquals("a1", (repository.serverDefault() as ApiResult.Success).value)
            assertEquals("/api/v1/me/active-org-unit", server.takeRequest().target)

            respond("{}")
            assertNull((repository.serverDefault() as ApiResult.Success).value)
        }
}

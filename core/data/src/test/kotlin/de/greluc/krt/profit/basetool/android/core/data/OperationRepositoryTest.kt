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
 * The Operationen reads: what reaches the wire, and what the app makes of what comes back.
 *
 * Robolectric for the same reason the Einsatz repository test needs it — the repository logs
 * through the project facade, which calls `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403

        /** A total large enough that one page cannot hold it. */
        const val MANY_ELEMENTS = 60L

        /** How many pages [MANY_ELEMENTS] spans. */
        const val MANY_PAGES = 3

        val ONE_PAGE =
            """
            {
              "content": [
                {"id": "o1", "name": "Operation Rotschild", "status": "ACTIVE",
                 "description": "Bergbau im Aaron Halo"},
                {"id": "o2", "name": "Operation Eisvogel", "status": "COMPLETED"}
              ],
              "page": 0, "size": 25, "totalElements": 2, "totalPages": 1, "sort": []
            }
            """.trimIndent()

        val HEAD =
            """
            {"id": "o1", "name": "Operation Rotschild", "status": "ACTIVE",
             "payoutPreliminary": true, "version": 3}
            """.trimIndent()

        val ROLLUP =
            """
            {"operationId": "o1", "totalSum": 74700.0000, "truncated": true,
             "missions": [{"missionId": "m1", "missionName": "Vertikaler Abbau", "totalSum": 86400.0000},
                          {"missionId": "m2", "missionName": "Konvoi-Eskorte", "totalSum": -11700.0000}]}
            """.trimIndent()

        /** The share the fixture's first participant was weighted at. */
        const val EXPECTED_PERCENTAGE = 12.5

        val PAYOUTS =
            """
            {"totalDonations": 4150.0000,
             "payouts": [
               {"participantId": "u1", "participantName": "Rhea", "payoutPreference": "PAYOUT",
                "shareAmount": 4150.0000, "payoutAmount": 4129.2500, "paidOut": true,
                "participationPercentage": 12.5, "personalExpenses": 300.0000,
                "transferFee": 20.7500, "paidOutAt": "2026-08-30T10:15:00Z",
                "paidOutByName": "Kestrel"},
               {"participantId": "u2", "participantName": "Dorn", "payoutPreference": "DONATE",
                "shareAmount": 0.0000, "donatedAmount": 4150.0000, "payoutAmount": 0.0000,
                "paidOut": false}
             ]}
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: OperationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            OperationRepository(
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

    /**
     * The URL the repository actually requested.
     *
     * @return the recorded target, parsed so parameters can be read by name rather than matched as
     *   a substring — which would pass on a double-encoded value.
     */
    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    @Test
    fun `a page maps onto the model`() =
        runTest {
            respond(ONE_PAGE)

            val result = repository.search(OperationQuery.NONE)

            assertTrue(result is ApiResult.Success)
            val page = (result as ApiResult.Success).value
            assertEquals(2, page.operations.size)
            val first = page.operations.first()
            assertEquals("o1", first.id)
            assertEquals("Operation Rotschild", first.name)
            assertEquals(OperationStatus.ACTIVE, first.status)
            assertEquals("Bergbau im Aaron Halo", first.description)
            assertTrue(first.isRunning)
            assertFalse(page.operations[1].isRunning)
            assertEquals(2L, page.totalElements)
            assertFalse(page.hasMore)
        }

    @Test
    fun `CANCELED with one L is the operation spelling and must map`() =
        runTest {
            // The backend writes CANCELED here and CANCELLED on a mission. Mirroring the server is
            // what keeps the badge off UNKNOWN; "correcting" the spelling here would break it.
            respond(
                """
                {"content": [{"id": "o9", "name": "Abgesagt", "status": "CANCELED"}],
                 "page": 0, "totalElements": 1, "totalPages": 1}
                """.trimIndent(),
            )

            val result = repository.search(OperationQuery.NONE)

            val operation = (result as ApiResult.Success).value.operations.first()
            assertEquals(OperationStatus.CANCELED, operation.status)
            assertFalse(operation.isRunning)
        }

    @Test
    fun `a row without an id is dropped but the server total is not lowered`() =
        runTest {
            // It cannot be opened, so offering it would produce a tap that does nothing. Quietly
            // lowering the stated total would hide the fault instead of showing it.
            respond(
                """
                {"content": [{"name": "Namenlos", "status": "ACTIVE"},
                             {"id": "o1", "name": "Rotschild", "status": "ACTIVE"}],
                 "page": 0, "totalElements": 2, "totalPages": 1}
                """.trimIndent(),
            )

            val page = (repository.search(OperationQuery.NONE) as ApiResult.Success).value

            assertEquals(1, page.operations.size)
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `the search term is encoded exactly once`() =
        runTest {
            respond(ONE_PAGE)

            repository.search(OperationQuery(text = "Rot schild & co"))

            assertEquals("Rot schild & co", requestedUrl().queryParameter("query"))
        }

    @Test
    fun `an unknown status is never sent to the server`() =
        runTest {
            // UNKNOWN is this build's word for "the server said something new"; sending it back
            // would be a filter for a status that does not exist.
            respond(ONE_PAGE)

            repository.search(
                OperationQuery(statuses = setOf(OperationStatus.ACTIVE, OperationStatus.UNKNOWN)),
            )

            assertEquals(listOf("ACTIVE"), requestedUrl().queryParameterValues("status"))
        }

    @Test
    fun `paging is reported from the envelope`() =
        runTest {
            respond(
                """
                {"content": [], "page": 0, "totalElements": $MANY_ELEMENTS, "totalPages": $MANY_PAGES}
                """.trimIndent(),
            )

            val page = (repository.search(OperationQuery.NONE) as ApiResult.Success).value

            assertTrue(page.hasMore)
            assertEquals(MANY_ELEMENTS, page.totalElements)
        }

    @Test
    fun `the overview folds three reads into one answer`() =
        runTest {
            respond(HEAD)
            respond(ROLLUP)
            respond(PAYOUTS)

            val result = repository.overview("o1")

            assertTrue(result is ApiResult.Success)
            val overview = (result as ApiResult.Success).value
            assertEquals("Operation Rotschild", overview.detail.name)
            assertEquals(true, overview.detail.payoutPreliminary)
            assertEquals("74700.0000", overview.rollup.total)
            assertTrue(overview.rollup.truncated)
            assertEquals(2, overview.rollup.missions.size)
            assertEquals("Vertikaler Abbau", overview.rollup.missions.first().missionName)
            assertEquals(2, overview.payouts.participants)
            assertEquals("4150.0000", overview.payouts.totalDonations)
            assertTrue(overview.payouts.rows.first().paidOut)
            assertTrue(overview.payouts.rows[1].donating)
        }

    @Test
    fun `a payout carries what it is made of, not just the total`() =
        runTest {
            // 4150 earned, 4129.25 transferred. The 20.75 gap is the fee, and 300 of what does
            // arrive is the member's own outlay coming back — the app showed the total and dropped
            // both, leaving a figure nobody could check.
            respond(HEAD)
            respond(ROLLUP)
            respond(PAYOUTS)

            val result = repository.overview("o1")

            val row = (result as ApiResult.Success).value.payouts.rows.first()
            assertEquals(EXPECTED_PERCENTAGE, row.participationPercentage)
            assertEquals("300.0000", row.personalExpenses)
            assertEquals("20.7500", row.transferFee)
            assertEquals("Kestrel", row.paidOutByName)
        }

    @Test
    fun `every digit the server sent survives the mapping`() =
        runTest {
            // The wire carries a fixed-scale decimal and the screen formats it. Nothing here may
            // round or go through a Double -- that is how a total gains an error the server never
            // had.
            respond(HEAD)
            respond(ROLLUP)
            respond(PAYOUTS)

            val overview = (repository.overview("o1") as ApiResult.Success).value

            assertEquals("4129.2500", overview.payouts.rows.first().payout)
            assertEquals("-11700.0000", overview.rollup.missions[1].total)
        }

    @Test
    fun `a refused roll-up fails the whole overview`() =
        runTest {
            // All three endpoints carry the identical canSeeOperation gate, so a refusal on one is
            // a refusal on the screen. Rendering a head over a missing roll-up would show an
            // Operation that claims to have earned nothing.
            respond(HEAD)
            respond("{}", status = HTTP_FORBIDDEN)

            val result = repository.overview("o1")

            assertTrue(result is ApiResult.Failure)
        }

    @Test
    fun `a missing truncated flag is read as not truncated`() =
        runTest {
            // The field is a warning. Inventing one where the server sent none would put a caveat
            // on a complete list.
            respond(HEAD)
            respond("""{"operationId": "o1", "totalSum": 1.0, "missions": []}""")
            respond("""{"payouts": []}""")

            val overview = (repository.overview("o1") as ApiResult.Success).value

            assertFalse(overview.rollup.truncated)
            assertNull(overview.payouts.totalDonations)
            assertEquals(0, overview.payouts.participants)
        }

    @Test
    fun `a donating participant's earned share is the amount they gave away`() {
        val donor =
            OperationPayout(
                participantId = "p1",
                participantName = "Dorn",
                donating = true,
                share = "0.00",
                donated = "4150.00",
                payout = "0.00",
                paidOut = false,
            )

        assertEquals("4150.00", donor.earnedShare)
    }

    @Test
    fun `the share range spans the smallest and the largest earned`() {
        val equal =
            OperationPayouts(
                totalDonations = "4150.00",
                rows =
                    listOf(
                        OperationPayout("p1", "Dorn", true, "0.00", "4150.00", "0.00", false),
                        OperationPayout("p2", "Vex", false, "4150.00", null, "4129.25", false),
                    ),
            )
        val unequal =
            OperationPayouts(
                totalDonations = null,
                rows =
                    listOf(
                        OperationPayout("p1", "Dorn", false, "4150.00", null, "4129.25", false),
                        OperationPayout("p2", "Vex", false, "2075.00", null, "2064.63", false),
                    ),
            )

        assertEquals("4150.00" to "4150.00", equal.shareRange)
        assertEquals("2075.00" to "4150.00", unequal.shareRange)
        assertNull(OperationPayouts(totalDonations = null, rows = emptyList()).shareRange)
        assertNull(
            "a range from a subset would understate the spread without saying so",
            OperationPayouts(
                totalDonations = null,
                rows =
                    listOf(
                        OperationPayout("p1", "Dorn", false, "4150.00", null, "4129.25", false),
                        OperationPayout("p2", "Vex", false, null, null, null, false),
                    ),
            ).shareRange,
        )
    }
}

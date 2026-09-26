/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiError
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
import java.time.Instant

/**
 * The Einsatz detail read in both shapes the endpoint answers: full, and redacted for anonymous or
 * role-less callers (ADR-0034), which must parse as a legitimate answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionDetailRepositoryTest {
    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404

        /** What [FULL] says signed up, and how many of those checked in. */
        const val REGISTERED = 14
        const val CHECKED_IN = 9

        /** A member's view: every collection populated. */
        val FULL =
            """
            {
              "id": "m1",
              "name": "Vertikaler Abbau — Lyria",
              "description": "Quantainium-Abbau an der Lyria-Südwand.",
              "status": "PLANNED",
              "meetingTime": "2026-08-21T18:30:00Z",
              "plannedStartTime": "2026-08-21T19:00:00Z",
              "plannedEndTime": "2026-08-21T23:00:00Z",
              "isInternal": false,
              "meetingPoint": "ARC-L1",
              "operation": {"name": "Operation Rotschild"},
              "owningSquadron": {"name": "Staffel 1", "shorthand": "S1"},
              "partyLeadUser": {"effectiveName": "Rhea"},
              "registeredParticipants": 14,
              "checkedInParticipants": 9,
              "participants": [
                {"id": "p1", "user": {"effectiveName": "Rhea"}, "startTime": "2026-08-21T18:35:00Z",
                 "plannedMissionJobType": {"name": "Pilot", "archetype": "CREW"}, "comment": "bringt Prospector"},
                {"id": "p2", "guestName": "Dorn"}
              ],
              "assignedUnits": [
                {"id": "u1", "name": "Einheit Alpha", "highValueUnit": true,
                 "ship": {"name": "Carrack Meridian"},
                 "responsibleUser": {"effectiveName": "Rhea"},
                 "crew": [{"id": "c1", "participantName": "Dorn",
                           "jobTypes": [{"name": "Turret", "archetype": "CREW"}]}]}
              ],
              "steps": [{"id": "s1", "title": "Sammeln im Teamspeak", "meta": "20:30 · Kanal", "done": true}],
              "objectives": [{"id": "o1", "title": "500 SCU Quantainium", "kind": "PRIMARY"}],
              "frequencies": [{"id": "f1", "name": "KRT/Einsatz-1", "value": 148.50,
                               "frequencyType": {"name": "Einsatz"}}]
            }
            """.trimIndent()

        /** What an outsider receives: the redaction of ADR-0034 applied. */
        val REDACTED =
            """
            {
              "id": "m1",
              "name": "Vertikaler Abbau — Lyria",
              "status": "PLANNED",
              "plannedStartTime": "2026-08-21T19:00:00Z",
              "isInternal": false,
              "owningSquadron": {"name": "Staffel 1", "shorthand": "S1"},
              "registeredParticipants": 14,
              "checkedInParticipants": 9,
              "participants": [{"id": "p1", "user": {"effectiveName": "Rhea"}}],
              "assignedUnits": [],
              "steps": [],
              "objectives": [],
              "frequencies": []
            }
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: MissionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MissionRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

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
    fun `a member's Einsatz maps onto every tab`() =
        runTest {
            respond(FULL)

            val detail = (repository.detail("m1") as ApiResult.Success).value

            assertEquals("m1", detail.id)
            assertEquals("Vertikaler Abbau — Lyria", detail.name)
            assertEquals("Quantainium-Abbau an der Lyria-Südwand.", detail.description)
            assertEquals(MissionStatus.PLANNED, detail.status)
            assertEquals(Instant.parse("2026-08-21T18:30:00Z"), detail.meetingTime)
            assertEquals("ARC-L1", detail.meetingPoint)
            assertEquals("Rhea", detail.partyLeadName)
            assertEquals(REGISTERED, detail.registeredParticipants)
            assertEquals(CHECKED_IN, detail.checkedInParticipants)
            assertEquals(2, detail.participants.size)
            assertEquals(1, detail.units.size)
            assertEquals(1, detail.steps.size)
            assertEquals(1, detail.objectives.size)
            assertEquals(1, detail.frequencies.size)
            assertEquals("/api/v1/missions/m1", server.takeRequest().target)
        }

    /**
     * A frequency row shows `name` (or the type's) as the label and the number as the value.
     */
    @Test
    fun `a frequency's label and its number do not swap places`() =
        runTest {
            respond(FULL)

            val frequency = (repository.detail("m1") as ApiResult.Success).value.frequencies.single()

            assertEquals("Einsatz", frequency.type)
            assertEquals("148.50", frequency.value)
        }

    @Test
    fun `a check-in is read from the participant's start time, there being no flag on the wire`() =
        runTest {
            respond(FULL)

            val participants = (repository.detail("m1") as ApiResult.Success).value.participants

            assertTrue("Rhea has a start time", participants.first { it.name == "Rhea" }.checkedIn)
            assertFalse("Dorn has none", participants.first { it.name == "Dorn" }.checkedIn)
        }

    @Test
    fun `the planned job wins over the desired one`() =
        runTest {
            respond(FULL)

            val rhea = (repository.detail("m1") as ApiResult.Success).value.participants.first { it.name == "Rhea" }

            assertEquals("Pilot", rhea.role)
        }

    @Test
    fun `a unit carries its ship, its HVU flag and its crew's roles`() =
        runTest {
            respond(FULL)

            val unit = (repository.detail("m1") as ApiResult.Success).value.units.single()

            assertEquals("Einheit Alpha", unit.name)
            assertEquals("Carrack Meridian", unit.shipName)
            assertTrue(unit.highValue)
            assertEquals("Rhea", unit.responsibleName)
            assertEquals(listOf("Turret"), unit.crew.single().roles)
        }

    @Test
    fun `the outsider redaction is a success, not a failure`() =
        runTest {
            respond(REDACTED)

            val result = repository.detail("m1")

            assertTrue(result is ApiResult.Success)
            val detail = (result as ApiResult.Success).value
            assertNull("the description is the field the redaction hides", detail.description)
            assertNull(detail.partyLeadName)
            assertTrue(detail.units.isEmpty())
            assertEquals(1, detail.participants.size)
            assertNull("a redacted participant carries no comment", detail.participants.single().comment)
        }

    @Test
    fun `an id the server omits falls back to the one that was asked for`() =
        runTest {
            respond("""{"name":"Ohne Id","status":"PLANNED"}""")

            val detail = (repository.detail("m1") as ApiResult.Success).value

            assertEquals("m1", detail.id)
        }

    @Test
    fun `an unknown enum constant is a documented fragility, not a handled case`() {
        runTest {
            respond(FULL.replace("""archetype": "CREW""", """archetype": "LOGISTICS"""))

            val result = repository.detail("m1")

            assertTrue(
                "when this starts passing, the mitigation landed and this test should assert it instead",
                result is ApiResult.Failure,
            )
        }
    }

    @Test
    fun `a refused Einsatz is a Forbidden failure the screen can word for itself`() =
        runTest {
            respond("""{"title":"Guests cannot view internal missions."}""", HTTP_FORBIDDEN)

            val result = repository.detail("m1")

            assertTrue(result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Forbidden)
        }

    @Test
    fun `a stale link is a NotFound failure`() =
        runTest {
            respond("""{"title":"not found"}""", HTTP_NOT_FOUND)

            val result = repository.detail("m1")

            assertTrue((result as ApiResult.Failure).error is ApiError.NotFound)
        }

    @Test
    fun `the finances come from two calls and arrive as one tab`() =
        runTest {
            respond("""{"total":74700,"incomeSum":86400,"incomeCount":3,"expenseSum":11700,"expenseCount":2}""")
            respond(
                """
                {"content":[
                   {"id":"f1","type":"INCOME","amount":86400,"note":"Verkauf",
                    "participant":{"id":"p1","user":{"effectiveName":"Rhea"}}},
                   {"id":"f2","type":"EXPENSE","amount":11700,"note":"Treibstoff"}],
                 "page":0,"size":50,"totalElements":2,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val finances = (repository.finances("m1") as ApiResult.Success).value

            assertEquals("74700", finances.total)
            assertEquals("86400", finances.incomeSum)
            assertEquals(2L, finances.expenseCount)
            assertEquals(2, finances.entries.size)
            assertTrue(finances.entries.first { it.id == "f1" }.income)
            assertFalse(finances.entries.first { it.id == "f2" }.income)
            assertEquals("Rhea", finances.entries.first { it.id == "f1" }.participantName)
            assertEquals(2L, finances.totalEntries)
            assertEquals("/api/v1/missions/m1/finance-entries/summary", server.takeRequest().target)
        }

    @Test
    fun `amounts are carried verbatim, never through a Double`() =
        runTest {
            respond("""{"total":1234567.89,"incomeSum":1234567.89,"incomeCount":1,"expenseSum":0,"expenseCount":0}""")
            respond("""{"content":[],"page":0,"size":50,"totalElements":0,"totalPages":0,"sort":[]}""")

            val finances = (repository.finances("m1") as ApiResult.Success).value

            assertEquals("1234567.89", finances.total)
        }

    @Test
    fun `a refused summary fails the whole tab rather than showing half of it`() =
        runTest {
            respond("""{"title":"forbidden"}""", HTTP_FORBIDDEN)

            val result = repository.finances("m1")

            assertTrue(result is ApiResult.Failure)
            assertTrue((result as ApiResult.Failure).error is ApiError.Forbidden)
        }

    @Test
    fun `a refused entries page fails the tab too`() =
        runTest {
            respond("""{"total":0,"incomeSum":0,"incomeCount":0,"expenseSum":0,"expenseCount":0}""")
            respond("""{"title":"forbidden"}""", HTTP_FORBIDDEN)

            assertTrue(repository.finances("m1") is ApiResult.Failure)
        }
}

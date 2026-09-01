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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The Einsatz list read: what reaches the wire, and what the app does with answers it did not
 * expect.
 *
 * Robolectric because the repository logs through the project facade, which calls
 * `android.util.Log` — unmocked in a plain JVM test, which would fail on the diagnostic rather than
 * on the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionRepositoryTest {
    private companion object {
        /** A normal answer. */
        const val HTTP_OK = 200

        /** The server is up but broken. */
        const val HTTP_SERVER_ERROR = 500

        /** Slack on the lower-bound assertion, so a slow test machine cannot fail it. */
        const val CLOCK_SLACK_SECONDS = 5L

        /** The version the roster fixture carries, so the echo assertion is not a bare literal. */
        const val ROSTER_ROW_VERSION = 4L

        /** The Kern section's counter in the fixtures; distinct from the other two on purpose. */
        const val CORE_VERSION = 3L

        /** The Zeitplan section's counter. */
        const val SCHEDULE_VERSION = 7L

        /** The flags section's counter. */
        const val FLAGS_VERSION = 1L

        /** A total large enough that one page cannot hold it. */
        const val MANY_ELEMENTS = 60L

        /** How many pages [MANY_ELEMENTS] spans. */
        const val MANY_PAGES = 3

        /** One well-formed row, enough for the mapping assertions. */
        val ONE_PAGE =
            """
            {
              "content": [
                {
                  "id": "m1",
                  "name": "Vertikaler Abbau — Lyria",
                  "status": "PLANNED",
                  "meetingTime": "2026-08-21T18:30:00Z",
                  "plannedStartTime": "2026-08-21T19:00:00Z",
                  "isInternal": false,
                  "meetingPoint": "ARC-L1",
                  "owningSquadron": {"name": "Staffel 1", "shorthand": "S1"},
                  "operation": {"name": "Operation Rotschild"}
                }
              ],
              "page": 0, "size": 25, "totalElements": 1, "totalPages": 1, "sort": []
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
     * @return the recorded request target, parsed so query parameters can be read by name rather
     *   than matched as a substring — which would pass on a double-encoded value.
     */
    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    /**
     * A reader pointed at the same MockWebServer, for the structure repository.
     *
     * @return the reader.
     */
    private fun reader() =
        de.greluc.krt.profit.basetool.android.core.network.ApiReader(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().removeSuffix("/"),
            json = de.greluc.krt.profit.basetool.android.core.contract.KrtJson,
            logTag = "MissionStructureTest",
        )

    @Test
    fun `a page maps onto the model`() =
        runTest {
            respond(ONE_PAGE)

            val result = repository.search(MissionQuery.NONE)

            assertTrue(result is ApiResult.Success)
            val page = (result as ApiResult.Success).value
            assertEquals(1, page.missions.size)
            val mission = page.missions.first()
            assertEquals("m1", mission.id)
            assertEquals("Vertikaler Abbau — Lyria", mission.name)
            assertEquals(MissionStatus.PLANNED, mission.status)
            assertEquals(Instant.parse("2026-08-21T18:30:00Z"), mission.meetingTime)
            assertEquals(Instant.parse("2026-08-21T19:00:00Z"), mission.plannedStartTime)
            assertEquals("S1", mission.orgUnitShorthand)
            assertEquals("Operation Rotschild", mission.operationName)
            assertEquals(1L, page.totalElements)
            assertFalse(page.hasMore)
        }

    @Test
    fun `the search term is encoded exactly once`() =
        runTest {
            // `&` and `=` are the characters that either truncate the request or arrive
            // double-encoded when a query string is built by concatenation. The failure mode is a
            // search that silently matches nothing, which reads as "no Einsätze" rather than a bug.
            respond(ONE_PAGE)

            repository.search(MissionQuery(text = "Abbau & Eskorte = 2"))

            assertEquals("Abbau & Eskorte = 2", requestedUrl().queryParameter("query"))
        }

    @Test
    fun `a blank search term is left off the wire entirely`() =
        runTest {
            respond(ONE_PAGE)

            repository.search(MissionQuery(text = "   "))

            assertNull(requestedUrl().queryParameter("query"))
        }

    @Test
    fun `each selected status becomes its own repeated parameter`() =
        runTest {
            respond(ONE_PAGE)

            repository.search(
                MissionQuery(statuses = setOf(MissionStatus.PLANNED, MissionStatus.ACTIVE)),
            )

            assertEquals(
                setOf("PLANNED", "ACTIVE"),
                requestedUrl().queryParameterValues("status").toSet(),
            )
        }

    @Test
    fun `UNKNOWN is never sent as a status filter`() =
        runTest {
            // It is this build's word for "a status I do not recognise", not a server value. Sending
            // it would filter on a status the backend has never heard of and return nothing.
            respond(ONE_PAGE)

            repository.search(MissionQuery(statuses = setOf(MissionStatus.UNKNOWN, MissionStatus.ACTIVE)))

            assertEquals(listOf("ACTIVE"), requestedUrl().queryParameterValues("status"))
        }

    @Test
    fun `hiding past Einsaetze asks for the two statuses that are not over`() =
        runTest {
            // Not a lower bound on the start: that also hid every RUNNING Einsatz, whose gathering
            // time is by definition in the past. Found on a device, and the reason the design's
            // own "seit 15:57" row could never appear.
            respond(ONE_PAGE)

            repository.search(MissionQuery(includePast = false))

            // One takeRequest only: the helper consumes the queue, and a second call blocks.
            val url = requestedUrl()
            assertEquals(listOf("PLANNED", "ACTIVE"), url.queryParameterValues("status"))
            assertNull("the past is hidden by status, not by time", url.queryParameter("start"))
        }

    @Test
    fun `showing past Einsaetze narrows nothing`() =
        runTest {
            // No status at all: the server answers with everything the caller may see, which for a
            // member is all four.
            respond(ONE_PAGE)

            repository.search(MissionQuery(includePast = true))

            val url = requestedUrl()
            assertEquals(emptyList<String>(), url.queryParameterValues("status"))
            assertNull(url.queryParameter("start"))
        }

    @Test
    fun `a ticked status wins over the past toggle`() =
        runTest {
            // Subtracting the finished ones from an explicit "show me the finished ones" would
            // answer with an empty list.
            respond(ONE_PAGE)

            repository.search(MissionQuery(statuses = setOf(MissionStatus.COMPLETED), includePast = false))

            assertEquals(listOf("COMPLETED"), requestedUrl().queryParameterValues("status"))
        }

    @Test
    fun `an explicit range is sent as it is`() =
        runTest {
            respond(ONE_PAGE)
            val from = Instant.parse("2026-01-01T00:00:00Z")

            repository.search(MissionQuery(from = from, includePast = false))

            assertEquals(from.toString(), requestedUrl().queryParameter("start"))
        }

    @Test
    fun `the sort is one the backend whitelists`() =
        runTest {
            // An unlisted sort field is answered with 400, so this is not a free-form string: the
            // list would fail to load entirely rather than merely arrive in another order.
            respond(ONE_PAGE)

            repository.search(MissionQuery.NONE)

            assertEquals("plannedStartTime,asc", requestedUrl().queryParameter("sort"))
        }

    @Test
    fun `a row without an id is dropped, and the server's total is left alone`() =
        runTest {
            // It cannot be opened, so offering it produces a tap that does nothing. Lowering the
            // total to match would hide the fault instead of surfacing it.
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"ACTIVE"},{"name":"B","status":"ACTIVE"}],
                 "page":0,"size":25,"totalElements":2,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val page = (repository.search(MissionQuery.NONE) as ApiResult.Success).value

            assertEquals(listOf("m1"), page.missions.map { it.id })
            assertEquals(2L, page.totalElements)
        }

    @Test
    fun `a status this build has never heard of still renders`() =
        runTest {
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"BRIEFING"}],
                 "page":0,"size":25,"totalElements":1,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val mission = (repository.search(MissionQuery.NONE) as ApiResult.Success).value.missions.first()

            assertEquals(MissionStatus.UNKNOWN, mission.status)
            assertEquals("BRIEFING", mission.rawStatus)
        }

    @Test
    fun `an unparseable timestamp costs that row its label, not the page`() =
        runTest {
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"ACTIVE","plannedStartTime":"tomorrow"}],
                 "page":0,"size":25,"totalElements":1,"totalPages":1,"sort":[]}
                """.trimIndent(),
            )

            val result = repository.search(MissionQuery.NONE)

            assertTrue(result is ApiResult.Success)
            assertNull((result as ApiResult.Success).value.missions.first().plannedStartTime)
        }

    @Test
    fun `an empty page is a success, not a failure`() =
        runTest {
            // "No Einsätze match" and "the list could not be loaded" are different screens, and
            // showing the second for the first is how a member is told something is broken when it
            // is not.
            respond("""{"content":[],"page":0,"size":25,"totalElements":0,"totalPages":0,"sort":[]}""")

            val result = repository.search(MissionQuery.NONE)

            assertTrue(result is ApiResult.Success)
            assertTrue((result as ApiResult.Success).value.missions.isEmpty())
        }

    @Test
    fun `a server error is a failure the caller can show`() =
        runTest {
            respond("""{"title":"boom"}""", HTTP_SERVER_ERROR)

            assertTrue(repository.search(MissionQuery.NONE) is ApiResult.Failure)
        }

    @Test
    fun `more pages are reported when the server says so`() =
        runTest {
            respond(
                """
                {"content":[{"id":"m1","name":"A","status":"ACTIVE"}],
                 "page":0,"size":25,"totalElements":$MANY_ELEMENTS,"totalPages":$MANY_PAGES,"sort":[]}
                """.trimIndent(),
            )

            val page = (repository.search(MissionQuery.NONE) as ApiResult.Success).value

            assertTrue(page.hasMore)
            assertEquals(MANY_ELEMENTS, page.totalElements)
        }

    /**
     * The whole point of `setPlannedFunction` taking the row rather than an id: `PUT
     * …/participants/{id}` is a **replace**. The server clears `desiredMissionJobType` and
     * `comment` when they are absent, and assigns `startTime` unconditionally — so a request that
     * carried only the new function would wipe the member's stated wish, their note, **and check
     * them out**. Three silent losses with no error and no visible cause.
     */
    @Test
    fun `setPlannedFunction echoes the fields it is not changing`() =
        runTest {
            respond("""{"id":"p1","version":8}""")
            val row =
                MissionParticipant(
                    id = "p1",
                    userId = "u1",
                    name = "Rhea",
                    role = null,
                    checkedIn = true,
                    comment = "bringt Eskorte mit",
                    donating = true,
                    desiredJobTypeId = "wish-1",
                    desiredJobName = "Pilot",
                    plannedJobTypeId = null,
                    version = 7L,
                    startTime = "2026-08-28T19:00:00Z",
                    endTime = null,
                )

            repository.setPlannedFunction("m1", row, jobTypeId = "job-2")

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue("the new function must be sent", body.contains(""""plannedMissionJobTypeId":"job-2""""))
            assertTrue("the wish must survive", body.contains(""""desiredMissionJobTypeId":"wish-1""""))
            assertTrue("the note must survive", body.contains(""""comment":"bringt Eskorte mit""""))
            assertTrue("the check-in must survive", body.contains(""""startTime":"2026-08-28T19:00:00Z""""))
            assertTrue("the payout must survive", body.contains(""""payoutPreference":"DONATE""""))
            assertTrue("the version must be echoed", body.contains(""""version":7"""))
        }

    /** Tapping the assigned function clears it, which is a null the server is meant to act on. */
    @Test
    fun `setPlannedFunction sends a null to clear the assignment`() =
        runTest {
            respond("""{"id":"p1","version":9}""")
            val row =
                MissionParticipant(
                    id = "p1",
                    userId = "u1",
                    name = "Rhea",
                    role = "Pilot",
                    checkedIn = false,
                    comment = null,
                    donating = null,
                    plannedJobTypeId = "job-2",
                    version = 8L,
                )

            repository.setPlannedFunction("m1", row, jobTypeId = null)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertFalse(
                "a cleared assignment must not send the old id back",
                body.contains(""""plannedMissionJobTypeId":"job-2""""),
            )
        }

    /**
     * `canEdit` is the server's verdict on whether the caller may act on other rows, and an absent
     * one has to read as "no" — an older server that omits the field must leave the manager actions
     * locked rather than offer writes it would refuse.
     */
    @Test
    fun `an absent canEdit leaves the roster unmanageable`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")

            val result = repository.detail("m1")

            assertTrue(result is ApiResult.Success)
            assertFalse((result as ApiResult.Success).value.canManage)
        }

    /** And a `true` is carried through untouched. */
    @Test
    fun `canEdit is carried into the detail`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria","canEdit":true}""")

            val result = repository.detail("m1")

            assertTrue((result as ApiResult.Success).value.canManage)
        }

    /**
     * The domain row keeps both job types apart even though `role` collapses them for display —
     * without that, the echo above cannot send back a wish it never kept.
     */
    @Test
    fun `a participant keeps its wish, its assignment and its version apart`() =
        runTest {
            respond(
                """
                {"id":"m1","name":"Lyria","participants":[{
                  "id":"p1","version":4,"comment":"note",
                  "desiredMissionJobType":{"id":"j1","name":"Pilot","archetype":"MISSION"},
                  "plannedMissionJobType":{"id":"j2","name":"Turret","archetype":"MISSION"},
                  "startTime":"2026-08-28T19:00:00Z"
                }]}
                """.trimIndent(),
            )

            val row = (repository.detail("m1") as ApiResult.Success).value.participants.single()

            assertEquals("j1", row.desiredJobTypeId)
            assertEquals("Pilot", row.desiredJobName)
            assertEquals("j2", row.plannedJobTypeId)
            // `role` shows the assignment, falling back to the wish — the display rule, unchanged.
            assertEquals("Turret", row.role)
            assertEquals(ROSTER_ROW_VERSION, row.version)
            assertTrue("a start time is what a check-in is", row.checkedIn)
        }

    /**
     * The catalogue holds two archetypes and the backend refuses the wrong one on write — "Planned
     * JobType Pilot is not of archetype MISSION", a 400 found on a device. CREW types are the roles
     * inside an Einheit; they share their names with the mission ones, so an unfiltered read looks
     * right on screen and fails only when somebody presses the chip.
     */
    @Test
    fun `the Funktionen catalogue asks for the MISSION archetype`() =
        runTest {
            respond("""{"content":[]}""")

            repository.jobTypes()

            assertEquals("MISSION", requestedUrl().queryParameter("archetype"))
        }

    /**
     * The three section counters are separate on purpose: a manager fixing the briefing must not
     * collide with a colleague moving the start time. A client that echoed one counter for all
     * three would reintroduce exactly the screen-wide lock the server went to the trouble of
     * splitting.
     */
    @Test
    fun `the three section counters are carried apart`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria","coreVersion":3,"scheduleVersion":7,"flagsVersion":1}""")

            val detail = (repository.detail("m1") as ApiResult.Success).value

            assertEquals(CORE_VERSION, detail.coreVersion)
            assertEquals(SCHEDULE_VERSION, detail.scheduleVersion)
            assertEquals(FLAGS_VERSION, detail.flagsVersion)
        }

    /** An absent counter becomes 0, which the server never issues — so a write with it is refused. */
    @Test
    fun `an absent section counter reads as zero`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")

            val detail = (repository.detail("m1") as ApiResult.Success).value

            assertEquals(0L, detail.coreVersion)
        }

    @Test
    fun `patching the Kern section sends its own counter`() =
        runTest {
            respond("""{"id":"m1","name":"Neu"}""")

            repository.patchCore(
                "m1",
                name = "Neu",
                description = "d",
                meetingPoint = "ARC-L1",
                calendarLink = null,
                status = null,
                operationId = null,
                version = CORE_VERSION,
            )

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            val body = request.body?.utf8().orEmpty()
            assertTrue(body.contains(""""version":3"""))
            assertTrue(body.contains(""""name":"Neu""""))
            assertTrue(body.contains(""""meetingPoint":"ARC-L1""""))
        }

    /**
     * Setting the actual start time is what opens an Einsatz for check-in: the server refuses every
     * check-in until it is set, so this write is the one that unblocks the roster.
     */
    @Test
    fun `patching the Zeitplan carries the actual start time`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")

            repository.patchSchedule(
                "m1",
                meetingTime = null,
                plannedStartTime = null,
                plannedEndTime = null,
                actualStartTime = "2026-08-28T19:00:00Z",
                actualEndTime = null,
                version = SCHEDULE_VERSION,
            )

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains(""""actualStartTime":"2026-08-28T19:00:00Z""""))
            assertTrue(body.contains(""""version":7"""))
        }

    @Test
    fun `patching the flags sends the internal switch and its counter`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")

            repository.patchFlags("m1", internal = true, version = FLAGS_VERSION)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains(""""isInternal":true"""))
            assertTrue(body.contains(""""version":1"""))
        }

    /**
     * The structure lives on its own repository, and this is what it sends.
     *
     * A frequency needs **both** a label and a value: the catalogue names the channel's purpose and
     * the value is the setting on it, so the write carries the pair.
     */
    @Test
    fun `a custom frequency carries its label and its value`() =
        runTest {
            respond("""[{"id":"f1","name":"Einsatz-1","value":121.50}]""")
            val structure = MissionStructureRepository(reader = reader())

            val result =
                structure.addCustomFrequency(
                    "m1",
                    current = mission(),
                    name = "Einsatz-1",
                    value = "121.5",
                )

            val request = server.takeRequest()
            assertTrue("a custom frequency has only a slim endpoint", request.target.endsWith("/custom/slim"))
            val body = request.body?.utf8().orEmpty()
            assertTrue(body.contains(""""name":"Einsatz-1""""))
            assertTrue(body.contains("121.5"))
            // And the answer maps back the right way round: a custom frequency has no type, so its
            // own name is the label, and the number is the value.
            val saved = (result as ApiResult.Success).value.frequencies.single()
            assertEquals("Einsatz-1", saved.type)
            assertEquals("121.50", saved.value)
        }

    @Test
    fun `adding an Einheit sends its name and its HVU mark`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")
            val structure = MissionStructureRepository(reader = reader())

            structure.addUnit("m1", name = "Einheit Alpha", highValue = true)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val body = request.body?.utf8().orEmpty()
            assertTrue(body.contains(""""name":"Einheit Alpha""""))
            assertTrue(body.contains(""""highValueUnit":true"""))
        }

    /**
     * Crew is keyed by **participant**, not by user: somebody has to be on the roster before they
     * can be put aboard an Einheit.
     */
    @Test
    fun `crew is assigned by participant id`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")
            val structure = MissionStructureRepository(reader = reader())

            structure.addCrew("m1", unitId = "u1", participantId = "p2", jobTypeIds = emptySet())

            val request = server.takeRequest()
            // The PLAIN endpoint, not /slim: the slim one answers with the narrow object and the
            // plain one with the whole Einsatz, which is what the screen swaps.
            assertTrue(request.target.endsWith("/units/u1/crew"))
            assertTrue(request.body?.utf8().orEmpty().contains(""""participantId":"p2""""))
        }

    /** The party lead carries its own section counter, like the other three. */
    @Test
    fun `the party lead carries its own counter`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")

            repository.setPartyLead("m1", userId = "u9", guestName = null, version = FLAGS_VERSION)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains(""""userId":"u9""""))
            assertTrue(body.contains(""""version":1"""))
        }

    /** Adding a manager names the member in the path and carries no body. */
    @Test
    fun `adding a manager names the member in the path`() =
        runTest {
            respond("""{"id":"m1","name":"Lyria"}""")

            repository.addManager("m1", userId = "u9")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.target.endsWith("/managers/u9"))
        }

    /**
     * A bare Einsatz, for the writes that splice their answer onto one.
     *
     * @return the Einsatz.
     */
    private fun mission() =
        MissionDetail(
            id = "m1",
            name = "Lyria",
            description = null,
            status = MissionStatus.PLANNED,
            rawStatus = "PLANNED",
            meetingTime = null,
            plannedStartTime = null,
            actualStartTime = null,
            actualEndTime = null,
            plannedEndTime = null,
            isInternal = false,
            meetingPoint = null,
            operationId = null,
            operationName = null,
            orgUnitName = null,
            orgUnitShorthand = null,
            partyLeadName = null,
            registeredParticipants = 0,
            checkedInParticipants = 0,
            participants = emptyList(),
            units = emptyList(),
            steps = emptyList(),
            objectives = emptyList(),
            frequencies = emptyList(),
        )
}

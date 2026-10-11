/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

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
 * The Ablauf and Ziele writes: the verb and the exact path each one sends, under the Einsatz and
 * without a suffix.
 *
 * Robolectric, because the repository logs through `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionTimelineRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MissionTimelineRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MissionTimelineRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Enqueues an empty list, which every write of this repository answers with. */
    private fun respondWithList() {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", "application/json")
                .body("[]")
                .build(),
        )
    }

    /**
     * The next recorded request as `METHOD target`.
     *
     * @return the verb and the request target, space-separated.
     */
    private fun nextCall(): String = server.takeRequest().let { "${it.method} ${it.target}" }

    @Test
    fun `the Ablauf is written under the Einsatz`() =
        runTest {
            repeat(STEP_WRITES) { respondWithList() }
            val current = mission()

            repository.addStep("m1", current, title = "Start", meta = null)
            repository.updateStep("m1", current, stepId = "s1", title = "Landung", meta = null)
            repository.toggleStep("m1", current, stepId = "s1", done = true)
            repository.reorderSteps("m1", current, stepIds = listOf("s2", "s1"))
            repository.removeStep("m1", current, stepId = "s1")

            assertEquals("POST /api/v1/missions/m1/steps", nextCall())
            assertEquals("PUT /api/v1/missions/m1/steps/s1", nextCall())
            assertEquals("PATCH /api/v1/missions/m1/steps/s1/done", nextCall())
            assertEquals("PUT /api/v1/missions/m1/steps/reorder", nextCall())
            assertEquals("DELETE /api/v1/missions/m1/steps/s1?stepsVersion=${current.stepsVersion}", nextCall())
        }

    @Test
    fun `the Ziele are written under the Einsatz`() =
        runTest {
            repeat(OBJECTIVE_WRITES) { respondWithList() }
            val current = mission()

            repository.addObjective("m1", current, title = "Fracht", kind = MissionObjectiveKind.PRIMARY)
            repository.updateObjective("m1", current, "o1", title = "Fracht", kind = MissionObjectiveKind.SECONDARY)
            repository.reorderObjectives("m1", current, objectiveIds = listOf("o2", "o1"))
            repository.removeObjective("m1", current, objectiveId = "o1")

            assertEquals("POST /api/v1/missions/m1/objectives", nextCall())
            assertEquals("PUT /api/v1/missions/m1/objectives/o1", nextCall())
            assertEquals("PUT /api/v1/missions/m1/objectives/reorder", nextCall())
            assertEquals(
                "DELETE /api/v1/missions/m1/objectives/o1?objectivesVersion=${current.objectivesVersion}",
                nextCall(),
            )
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

    private companion object {
        const val HTTP_OK = 200
        const val STEP_WRITES = 5
        const val OBJECTIVE_WRITES = 4
    }
}

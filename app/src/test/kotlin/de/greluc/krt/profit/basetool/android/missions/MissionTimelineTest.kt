/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionObjectiveKind
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.data.MissionTimelineSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Ablauf and the Ziele.
 *
 * The property worth a class: these endpoints answer with the **list**, never with the Einsatz, so
 * the client owns the section counter and a client that leaves it stale breaks the *second* edit in
 * a sitting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionTimelineTest {
    private companion object {
        /** The Ablauf section's counter in the fixture. */
        const val STEPS_VERSION = 4L

        /** The Ziele section's counter — deliberately different. */
        const val OBJECTIVES_VERSION = 9L
    }

    private val dispatcher = StandardTestDispatcher()

    /** Every write, as `(what, the counter it echoed)`. */
    private val calls = mutableListOf<Pair<String, Long>>()

    /** The id order the last reorder sent. */
    private var reordered: List<String> = emptyList()

    private var draft = MissionTimelineDraft()
    private var detail = detail()
    private var failure: ApiError? = null

    private fun detail() =
        MissionDetail(
            id = "m1",
            name = "Vertikaler Abbau",
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
            canManage = true,
            stepsVersion = STEPS_VERSION,
            objectivesVersion = OBJECTIVES_VERSION,
        )

    private fun timeline(scope: kotlinx.coroutines.CoroutineScope) =
        MissionTimeline(
            missionId = "m1",
            source = RecordingSource(),
            scope = scope,
            read = { draft to detail },
            write = { d, saved ->
                draft = d
                saved?.let { detail = it }
            },
        )

    /**
     * The whole point. The write answers with a list and no counter, so the repository advances the
     * local one — and the holder must be handed that advanced Einsatz, or the next write echoes a
     * counter the server has already consumed and answers `409`.
     */
    @Test
    fun `a second step edit echoes the advanced counter, not the one it started with`() =
        runTest(dispatcher) {
            val subject = timeline(this)
            draft = draft.copy(stepTitle = "Anflug")

            subject.saveStep()
            advanceUntilIdle()
            draft = draft.copy(stepTitle = "Abbau")
            subject.saveStep()
            advanceUntilIdle()

            assertEquals(
                listOf("add" to STEPS_VERSION, "add" to STEPS_VERSION + 1),
                calls,
            )
        }

    /** The Ziele carry their own counter, and the two sections never borrow from each other. */
    @Test
    fun `a Ziel echoes the objectives counter, never the steps one`() =
        runTest(dispatcher) {
            val subject = timeline(this)
            draft = draft.copy(objectiveTitle = "Laranit sichern")

            subject.saveObjective()
            advanceUntilIdle()

            assertEquals(listOf("add-objective" to OBJECTIVES_VERSION), calls)
        }

    /** Editing loads the row and the save rewrites it rather than appending a second one. */
    @Test
    fun `editing a step rewrites it instead of appending`() =
        runTest(dispatcher) {
            val subject = timeline(this)

            subject.editStep(MissionStepEdit(id = "s1", title = "Anflug", meta = "20:00"))
            assertEquals("Anflug", draft.stepTitle)
            assertEquals("20:00", draft.stepMeta)
            subject.saveStep()
            advanceUntilIdle()

            assertEquals(listOf("update" to STEPS_VERSION), calls)
        }

    /** A blank title is not a step. Nothing is sent, so the server never has to say so. */
    @Test
    fun `a blank step writes nothing`() =
        runTest(dispatcher) {
            val subject = timeline(this)
            draft = draft.copy(stepTitle = "   ")

            subject.saveStep()
            advanceUntilIdle()

            assertTrue(calls.isEmpty())
        }

    /**
     * Ticking sends the state it is to be **in**, not a flip. Two managers tapping at once then
     * converge on „done" instead of cancelling each other out.
     */
    @Test
    fun `ticking sends the target state`() =
        runTest(dispatcher) {
            val subject = timeline(this)

            subject.toggleStep("s1", done = true)
            advanceUntilIdle()

            assertEquals(listOf("toggle" to STEPS_VERSION), calls)
        }

    /** A refusal keeps what was typed: re-typing it to find out what went wrong is a charge. */
    @Test
    fun `a refused save keeps the editor filled`() =
        runTest(dispatcher) {
            failure = ApiError.Forbidden()
            val subject = timeline(this)
            draft = draft.copy(stepTitle = "Anflug")

            subject.saveStep()
            advanceUntilIdle()

            assertEquals("Anflug", draft.stepTitle)
            assertTrue(draft.error is ApiError.Forbidden)
        }

    /** Cancelling drops the editor without touching the Einsatz. */
    @Test
    fun `cancelling clears the editor and writes nothing`() =
        runTest(dispatcher) {
            val subject = timeline(this)
            subject.editStep(MissionStepEdit(id = "s1", title = "Anflug", meta = null))

            subject.cancel()
            advanceUntilIdle()

            assertEquals("", draft.stepTitle)
            assertNull(draft.editingStepId)
            assertTrue(calls.isEmpty())
        }

    /**
     * The reorder sends the **whole** id list in its new order, taken from the Einsatz as last
     * read — so a step somebody else added is carried along rather than dropped.
     */
    @Test
    fun `moving a step sends every id in the new order`() =
        runTest(dispatcher) {
            detail =
                detail().copy(
                    steps =
                        listOf(
                            de.greluc.krt.profit.basetool.android.core.data.MissionStep("a", "A", null, false),
                            de.greluc.krt.profit.basetool.android.core.data.MissionStep("b", "B", null, false),
                            de.greluc.krt.profit.basetool.android.core.data.MissionStep("c", "C", null, false),
                        ),
                )
            val subject = timeline(this)

            subject.moveStep("c", up = true)
            advanceUntilIdle()

            assertEquals(listOf("a", "c", "b"), reordered)
            assertEquals(listOf("reorder" to STEPS_VERSION), calls)
        }

    /** A tap at the end of the list is a no-op, not a request the server has to refuse. */
    @Test
    fun `moving the first row up writes nothing`() =
        runTest(dispatcher) {
            detail =
                detail().copy(
                    steps =
                        listOf(
                            de.greluc.krt.profit.basetool.android.core.data.MissionStep("a", "A", null, false),
                        ),
                )
            val subject = timeline(this)

            subject.moveStep("a", up = true)
            advanceUntilIdle()

            assertTrue(calls.isEmpty())
        }

    /** Records what was written and the counter it echoed. */
    private inner class RecordingSource : MissionTimelineSource {
        override suspend fun addStep(
            missionId: String,
            current: MissionDetail,
            title: String,
            meta: String?,
        ): ApiResult<MissionDetail> = step("add", current)

        override suspend fun updateStep(
            missionId: String,
            current: MissionDetail,
            stepId: String,
            title: String,
            meta: String?,
        ): ApiResult<MissionDetail> = step("update", current)

        override suspend fun toggleStep(
            missionId: String,
            current: MissionDetail,
            stepId: String,
            done: Boolean,
        ): ApiResult<MissionDetail> = step("toggle", current)

        override suspend fun removeStep(
            missionId: String,
            current: MissionDetail,
            stepId: String,
        ): ApiResult<MissionDetail> = step("remove", current)

        override suspend fun reorderSteps(
            missionId: String,
            current: MissionDetail,
            stepIds: List<String>,
        ): ApiResult<MissionDetail> {
            reordered = stepIds
            return step("reorder", current)
        }

        override suspend fun reorderObjectives(
            missionId: String,
            current: MissionDetail,
            objectiveIds: List<String>,
        ): ApiResult<MissionDetail> {
            reordered = objectiveIds
            return objective("reorder-objective", current)
        }

        override suspend fun addObjective(
            missionId: String,
            current: MissionDetail,
            title: String,
            kind: MissionObjectiveKind,
        ): ApiResult<MissionDetail> = objective("add-objective", current)

        override suspend fun updateObjective(
            missionId: String,
            current: MissionDetail,
            objectiveId: String,
            title: String,
            kind: MissionObjectiveKind,
        ): ApiResult<MissionDetail> = objective("update-objective", current)

        override suspend fun removeObjective(
            missionId: String,
            current: MissionDetail,
            objectiveId: String,
        ): ApiResult<MissionDetail> = objective("remove-objective", current)

        /**
         * Answers a step write the way the repository does — the counter advanced by one.
         *
         * @param what which write.
         * @param current the Einsatz it was handed.
         * @return the spliced Einsatz, or the queued refusal.
         */
        private fun step(
            what: String,
            current: MissionDetail,
        ): ApiResult<MissionDetail> {
            calls.add(what to current.stepsVersion)
            return failure?.let { ApiResult.Failure(it) }
                ?: ApiResult.Success(current.copy(stepsVersion = current.stepsVersion + 1))
        }

        /**
         * The same for a Ziel.
         *
         * @param what which write.
         * @param current the Einsatz it was handed.
         * @return the spliced Einsatz, or the queued refusal.
         */
        private fun objective(
            what: String,
            current: MissionDetail,
        ): ApiResult<MissionDetail> {
            calls.add(what to current.objectivesVersion)
            return failure?.let { ApiResult.Failure(it) }
                ?: ApiResult.Success(current.copy(objectivesVersion = current.objectivesVersion + 1))
        }
    }
}

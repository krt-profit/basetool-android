/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.MissionAdminSource
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Editing the Einsatz itself.
 *
 * The one property worth a whole test class: the three sections are locked **independently**, and a
 * client that saves them together throws that away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionAdminTest {
    private companion object {
        /** The Kern section's counter in the fixture. */
        const val CORE_VERSION = 3L

        /** The Zeitplan section's counter — deliberately different from the other two. */
        const val SCHEDULE_VERSION = 7L

        /** The flags section's counter. */
        const val FLAGS_VERSION = 1L
    }

    private val dispatcher = StandardTestDispatcher()

    /** Every patch call, as `(section, version)`. */
    private val calls = mutableListOf<Pair<MissionSection, Long>>()

    private var form: MissionAdminForm? = null
    private var saved: MissionDetail? = null
    private var canManage = true

    private fun detail(started: Boolean = false) =
        MissionDetail(
            id = "m1",
            name = "Vertikaler Abbau",
            description = "Briefing",
            status = MissionStatus.PLANNED,
            rawStatus = "PLANNED",
            meetingTime = null,
            plannedStartTime = null,
            actualStartTime = if (started) Instant.parse("2026-08-28T19:00:00Z") else null,
            plannedEndTime = null,
            isInternal = false,
            meetingPoint = "ARC-L1",
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
            coreVersion = CORE_VERSION,
            scheduleVersion = SCHEDULE_VERSION,
            flagsVersion = FLAGS_VERSION,
        )

    private fun admin(
        scope: kotlinx.coroutines.CoroutineScope,
        detail: MissionDetail = detail(),
    ) = MissionAdmin(
        missionId = "m1",
        source = RecordingSource(detail),
        scope = scope,
        read = { MissionAdminContext(form, detail, canManage) },
        write = { form = it },
        onSaved = { saved = it },
    )

    @Test
    fun `the sheet opens filled from the Einsatz`() =
        runTest(dispatcher) {
            admin(this).open()

            val open = assertNotNull(form).let { form!! }
            assertEquals("Vertikaler Abbau", open.name)
            assertEquals("Briefing", open.description)
            assertEquals("ARC-L1", open.meetingPoint)
        }

    /** A caller who may not manage gets no sheet at all, not an empty one. */
    @Test
    fun `it does not open for a caller who may not manage`() =
        runTest(dispatcher) {
            canManage = false

            admin(this).open()

            assertNull(form)
        }

    /**
     * The point of the whole class. Each save sends **its own** section's counter, so a manager
     * fixing the briefing does not collide with a colleague moving the start time. One save for all
     * three would reintroduce exactly the screen-wide lock the server split apart.
     */
    @Test
    fun `each section is saved with its own counter`() =
        runTest(dispatcher) {
            val subject = admin(this)
            subject.open()

            subject.save(MissionSection.CORE)
            advanceUntilIdle()
            subject.save(MissionSection.SCHEDULE)
            advanceUntilIdle()
            subject.save(MissionSection.FLAGS)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    MissionSection.CORE to CORE_VERSION,
                    MissionSection.SCHEDULE to SCHEDULE_VERSION,
                    MissionSection.FLAGS to FLAGS_VERSION,
                ),
                calls,
            )
        }

    /**
     * „Der Einsatz läuft jetzt" writes the Zeitplan section with a timestamp the member never had
     * to type — and it is what the server needs before it accepts a single check-in.
     */
    @Test
    fun `starting the Einsatz stamps an actual start time`() =
        runTest(dispatcher) {
            val subject = admin(this)
            subject.open()

            subject.startNow()
            advanceUntilIdle()

            assertEquals(listOf(MissionSection.SCHEDULE to SCHEDULE_VERSION), calls)
            assertTrue("the form must now read as started", form?.started == true)
        }

    /** A closed sheet writes nothing, whichever action is raised against it. */
    @Test
    fun `nothing is saved while the sheet is closed`() =
        runTest(dispatcher) {
            val subject = admin(this)

            subject.save(MissionSection.CORE)
            subject.startNow()
            advanceUntilIdle()

            assertTrue(calls.isEmpty())
        }

    @Test
    fun `dismissing clears the form`() =
        runTest(dispatcher) {
            val subject = admin(this)
            subject.open()

            subject.dismiss()

            assertNull(form)
        }

    /**
     * The answer re-fills the form, so the other two sections' counters arrive fresh — a manager can
     * make a second edit without a 409 from a version they never saw.
     */
    @Test
    fun `a successful save re-fills the form from the answer`() =
        runTest(dispatcher) {
            val subject = admin(this)
            subject.open()
            form = form?.copy(name = "getippt")

            subject.save(MissionSection.CORE)
            advanceUntilIdle()

            assertEquals("Vertikaler Abbau", form?.name)
            assertNotNull(saved)
        }

    /** Records which section was written and with which counter. */
    private inner class RecordingSource(
        private val answer: MissionDetail,
    ) : MissionAdminSource {
        override suspend fun patchCore(
            missionId: String,
            name: String,
            description: String?,
            meetingPoint: String?,
            version: Long,
        ): ApiResult<MissionDetail> {
            calls.add(MissionSection.CORE to version)
            return ApiResult.Success(answer)
        }

        override suspend fun patchSchedule(
            missionId: String,
            meetingTime: String?,
            plannedStartTime: String?,
            plannedEndTime: String?,
            actualStartTime: String?,
            version: Long,
        ): ApiResult<MissionDetail> {
            calls.add(MissionSection.SCHEDULE to version)
            return ApiResult.Success(answer.copy(actualStartTime = Instant.parse("2026-08-28T19:00:00Z")))
        }

        override suspend fun patchFlags(
            missionId: String,
            internal: Boolean,
            version: Long,
        ): ApiResult<MissionDetail> {
            calls.add(MissionSection.FLAGS to version)
            return ApiResult.Success(answer)
        }
    }
}

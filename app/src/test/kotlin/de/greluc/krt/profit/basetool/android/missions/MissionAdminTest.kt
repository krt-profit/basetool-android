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
            actualEndTime = null,
            plannedEndTime = null,
            isInternal = false,
            meetingPoint = "ARC-L1",
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
    fun `a running Einsatz can be ended, and the end is echoed once it is set`() =
        runTest(dispatcher) {
            val subject = admin(this, detail(started = true))
            subject.open()
            advanceUntilIdle()

            // Ending is not a status: activation stamps the START server-side and nothing stamps
            // the end, so this field is the only thing that closes an Einsatz — and with it every
            // participant's open end-time.
            assertEquals(false, form?.ended)
            subject.endMission()
            assertEquals(true, form?.endingNow)
            assertNotNull("the pair opens filled with now", form?.endDate?.takeIf { it.isNotBlank() })

            subject.cancelEndMission()
            assertEquals(false, form?.endingNow)
        }

    @Test
    fun `the Kern section is where an Einsatz joins an Operation`() =
        runTest(dispatcher) {
            val source = RecordingSource(detail())
            val subject =
                MissionAdmin(
                    missionId = "m1",
                    source = source,
                    scope = this,
                    read = { MissionAdminContext(form, detail(), canManage) },
                    write = { form = it },
                    onSaved = { saved = it },
                )
            subject.open()
            advanceUntilIdle()

            // The picker fills in after the form: the tab opens on what is already known.
            assertEquals(listOf("op1" to "Bergung Hurston"), form?.operations)

            subject.change(MissionSection.CORE) { it.copy(operationId = "op1") }
            subject.save(MissionSection.CORE)
            advanceUntilIdle()

            // The Operation's own form has no such field because the wire has none, so this is
            // the only place it can be set - and the app used to offer it in neither.
            assertEquals(listOf("op1"), source.cores)
        }

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

    // Starting the Einsatz is no longer this holder's action: design ch. 06 (F2) moved the
    // lifecycle onto the status badge, and the write is a Kern patch carrying the new status
    // rather than a Zeitplan patch carrying a timestamp. Its test lives with the view model.

    /** A closed sheet writes nothing, whichever action is raised against it. */
    @Test
    fun `nothing is saved while the sheet is closed`() =
        runTest(dispatcher) {
            val subject = admin(this)

            subject.save(MissionSection.CORE)
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
        /** Which Operation each Kern write carried. */
        val cores = mutableListOf<String?>()

        override suspend fun unitShipOptions(missionId: String): List<Pair<String, String>> =

            listOf("s1" to "Carrack · Anvil Carrack")

        override suspend fun operationOptions(): List<Pair<String, String>> =
            listOf("op1" to "Bergung Hurston")

        override suspend fun patchCore(
            missionId: String,
            name: String,
            description: String?,
            meetingPoint: String?,
            calendarLink: String?,
            status: String?,
            operationId: String?,
            version: Long,
        ): ApiResult<MissionDetail> {
            calls.add(MissionSection.CORE to version)
            cores.add(operationId)
            return ApiResult.Success(answer)
        }

        override suspend fun patchSchedule(
            missionId: String,
            meetingTime: String?,
            plannedStartTime: String?,
            plannedEndTime: String?,
            actualStartTime: String?,
            actualEndTime: String?,
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

        override suspend fun setPartyLead(
            missionId: String,
            userId: String?,
            guestName: String?,
            version: Long,
        ): ApiResult<MissionDetail> = error("the structure has its own test")

        override suspend fun addManager(
            missionId: String,
            userId: String,
        ): ApiResult<MissionDetail> = error("the structure has its own test")

        override suspend fun removeManager(
            missionId: String,
            userId: String,
        ): ApiResult<MissionDetail> = error("the structure has its own test")

        override suspend fun addParticipant(
            missionId: String,
            userId: String,
        ): ApiResult<MissionDetail> = error("the structure has its own test")
    }
}

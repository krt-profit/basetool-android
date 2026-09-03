/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionPage
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manager's half of the Teilnehmer tab.
 *
 * Its own class beside [MissionRoster] rather than more cases in `MissionDetailViewModelTest`,
 * which had grown past what detekt allows one class to carry — and because the roster answers one
 * question the rest of that screen does not: **may this caller act on somebody else's row, and does
 * the write carry the row whole.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionRosterTest {
    private companion object {
        /** The version the fixture row carries, echoed on every write against it. */
        const val ROW_VERSION = 3L
    }

    private val dispatcher = StandardTestDispatcher()

    /** What was asked for: `(participantId, jobTypeId)` per assignment. */
    private val assigned = mutableListOf<Pair<String, String?>>()

    /** The rows the writes carried, so the echo can be asserted. */
    private val echoed = mutableListOf<MissionParticipant>()

    /** `(participantId, checkedIn)` per check-in write. */
    private val checkIns = mutableListOf<Pair<String, Boolean>>()

    /** `(participantId, donating)` per payout write. */
    private val payouts = mutableListOf<Pair<String, Boolean>>()

    /** How many times the Funktionen catalogue was asked for. */
    private var catalogueReads = 0

    /**
     * Somebody other than the caller: a wish, a note, no assignment, and a version.
     *
     * @param checkedIn whether they are checked in.
     * @return the row.
     */
    private fun row(checkedIn: Boolean = false) =
        MissionParticipant(
            id = "p2",
            userId = "u2",
            name = "Dorn",
            role = null,
            checkedIn = checkedIn,
            comment = "bringt Eskorte mit",
            donating = null,
            desiredJobTypeId = "j1",
            desiredJobName = "Pilot",
            plannedJobTypeId = null,
            version = ROW_VERSION,
            startTime = null,
        )

    /**
     * A roster over [rows], with the gate open or shut.
     *
     * @param scope the test's scope.
     * @param rows what the screen last read; a row not in here cannot be written to.
     * @return the roster under test.
     */
    private fun roster(
        scope: TestScope,
        vararg rows: MissionParticipant,
    ): MissionRoster {
        val source = RecordingSource()
        return MissionRoster(
            missionId = "m1",
            source = source,
            scope = scope,
            rowToManage = { id -> rows.firstOrNull { it.id == id } },
            write = { request -> scope.launch { request() } },
        )
    }

    @Test
    fun `checking another member in names their row`() =
        runTest(dispatcher) {
            roster(this, row()).checkIn("p2", checkInPossible = true)
            advanceUntilIdle()

            assertEquals(listOf("p2" to true), checkIns)
        }

    /**
     * The server refuses a check-in before the Einsatz has started, so the manager's row action
     * follows the same rule the caller's own one does rather than sending a request that 400s.
     */
    @Test
    fun `nobody is checked in before the Einsatz has started`() =
        runTest(dispatcher) {
            roster(this, row()).checkIn("p2", checkInPossible = false)
            advanceUntilIdle()

            assertTrue(checkIns.isEmpty())
        }

    /**
     * The row comes from what is on screen, so a tap on an id the client has never read writes
     * nothing — a guessed version is the concurrent-edit collision the version exists to catch.
     */
    @Test
    fun `an unknown participant id is not written to`() =
        runTest(dispatcher) {
            roster(this).checkIn("ghost", checkInPossible = true)
            roster(this).payout("ghost")
            roster(this).assign("ghost", MissionJobType("j2", "Turret"))
            advanceUntilIdle()

            assertTrue(checkIns.isEmpty())
            assertTrue(payouts.isEmpty())
            assertTrue(assigned.isEmpty())
        }

    /** The payout toggle flips whatever the row currently says. */
    @Test
    fun `the payout toggle flips the row it was read from`() =
        runTest(dispatcher) {
            roster(this, row()).payout("p2")
            advanceUntilIdle()

            assertEquals(listOf("p2" to true), payouts)
        }

    /**
     * The write carries the row as read, which is what lets the repository echo the fields it is
     * not changing. Without it the server clears the wish and the note and checks the member out.
     */
    @Test
    fun `assigning a Funktion carries the whole row`() =
        runTest(dispatcher) {
            roster(this, row()).assign("p2", MissionJobType("j2", "Turret"))
            advanceUntilIdle()

            assertEquals(listOf("p2" to "j2"), assigned)
            val sent = echoed.single()
            assertEquals("the wish must travel with it", "j1", sent.desiredJobTypeId)
            assertEquals("the note must travel with it", "bringt Eskorte mit", sent.comment)
            assertEquals("the version must travel with it", ROW_VERSION, sent.version)
        }

    /** Tapping the assigned Funktion clears it, as the same control does on the sign-up sheet. */
    @Test
    fun `tapping the assigned Funktion clears it`() =
        runTest(dispatcher) {
            val assignedRow = row().copy(plannedJobTypeId = "j2")
            roster(this, assignedRow).assign("p2", MissionJobType("j2", "Turret"))
            advanceUntilIdle()

            assertEquals(listOf("p2" to null), assigned)
        }

    /**
     * The catalogue is a request most members would never use: their select is locked, and a locked
     * select needs the row's own Funktion, not the list of alternatives.
     */
    @Test
    fun `the catalogue is not read for a caller who cannot assign`() =
        runTest(dispatcher) {
            roster(this).loadJobTypes(canManage = false, known = emptyList()) { }
            advanceUntilIdle()

            assertEquals(0, catalogueReads)
        }

    /** And it is read only once for a manager who opens the tab twice. */
    @Test
    fun `the catalogue is read once`() =
        runTest(dispatcher) {
            val subject = roster(this)
            var known: List<MissionJobType> = emptyList()
            subject.loadJobTypes(canManage = true, known = known) { known = it }
            advanceUntilIdle()
            subject.loadJobTypes(canManage = true, known = known) { known = it }
            advanceUntilIdle()

            assertEquals(1, catalogueReads)
            assertEquals(listOf(MissionJobType("j1", "Pilot")), known)
        }

    /** Records what the roster asked the network for, and answers plausibly. */
    private inner class RecordingSource : MissionSource {
        override suspend fun search(
            query: MissionQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<MissionPage> = error("the roster never searches")

        override suspend fun detail(id: String): ApiResult<MissionDetail> =
            error("the roster is handed its rows, it does not read them")

        override suspend fun finances(missionId: String): ApiResult<MissionFinances> =
            error("the roster does not touch the books")

        override suspend fun join(
            missionId: String,
            desiredJobTypeId: String?,
            donate: Boolean,
        ): ApiResult<MissionDetail> = error("a manager does not sign anybody up from here")

        override suspend fun leave(
            missionId: String,
            participantId: String,
        ): ApiResult<Unit> = error("removing a row is not part of this slice")

        override suspend fun addFinanceEntry(
            missionId: String,
            participantId: String,
            income: Boolean,
            amount: String,
            note: String?,
        ): ApiResult<Unit> = error("the roster does not touch the books")

        override suspend fun updateFinanceEntry(
            entryId: String,
            income: Boolean,
            amount: String,
            note: String?,
            version: Long?,
        ): ApiResult<Unit> = error("the roster does not touch the books")

        override suspend fun deleteFinanceEntry(entryId: String): ApiResult<Unit> =
            error("the roster does not touch the books")

        override suspend fun jobTypes(): ApiResult<List<MissionJobType>> {
            catalogueReads++
            return ApiResult.Success(listOf(MissionJobType("j1", "Pilot")))
        }

        override suspend fun setCheckedIn(
            missionId: String,
            participantId: String,
            checkedIn: Boolean,
        ): ApiResult<MissionParticipant> {
            checkIns.add(participantId to checkedIn)
            return ApiResult.Success(row(checkedIn = checkedIn))
        }

        override suspend fun setDonating(
            missionId: String,
            participantId: String,
            donating: Boolean,
        ): ApiResult<MissionParticipant> {
            payouts.add(participantId to donating)
            return ApiResult.Success(row())
        }

        override suspend fun setPlannedFunction(
            missionId: String,
            participant: MissionParticipant,
            jobTypeId: String?,
        ): ApiResult<MissionParticipant> {
            assigned.add(participant.id to jobTypeId)
            echoed.add(participant)
            return ApiResult.Success(participant.copy(plannedJobTypeId = jobTypeId))
        }
    }
}

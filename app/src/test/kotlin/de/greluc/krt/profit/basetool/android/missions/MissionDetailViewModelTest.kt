/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinanceEntry
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionPage
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * The detail screen's own rules, and the one that matters most: the Einsatz and its money load on
 * **separate timelines**.
 *
 * A member can be allowed to see an Einsatz and still be refused its books
 * (`isMemberOrAbove` + `canSeeMission` guard the Finanzen endpoints alone). Folding the two reads
 * together would either hide the Einsatz behind a permission it does not need, or claim the money
 * loaded when it did not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionDetailViewModelTest {
    private companion object {
        /** The booking's optimistic lock. */
        const val ENTRY_VERSION = 4L
    }

    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers with whatever is queued and counts what was asked for.
     *
     * @property detailAnswers responses for [detail], the last one repeating once exhausted.
     * @property financeAnswers responses for [finances], likewise.
     */
    private class RecordingSource(
        private val detailAnswers: MutableList<ApiResult<MissionDetail>> = mutableListOf(),
        private val financeAnswers: MutableList<ApiResult<MissionFinances>> = mutableListOf(),
    ) : MissionSource {
        var detailCalls = 0
        var financeCalls = 0

        fun queueDetail(answer: ApiResult<MissionDetail>) = detailAnswers.add(answer)

        fun queueFinances(answer: ApiResult<MissionFinances>) = financeAnswers.add(answer)

        override suspend fun search(
            query: MissionQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<MissionPage> = error("the detail screen never searches")

        override suspend fun detail(id: String): ApiResult<MissionDetail> {
            detailCalls++
            return if (detailAnswers.size > 1) detailAnswers.removeAt(0) else detailAnswers.first()
        }

        override suspend fun finances(missionId: String): ApiResult<MissionFinances> {
            financeCalls++
            return if (financeAnswers.size > 1) financeAnswers.removeAt(0) else financeAnswers.first()
        }

        /**
         * The caller's own row as this fake hands it back.
         *
         * Defined on the fake rather than on the test class: a nested class cannot reach the
         * outer one's helpers, and the row is the fake's own answer anyway.
         *
         * @param checkedIn whether it is checked in.
         * @param donating whether the share is donated.
         * @return the row.
         */
        fun row(
            checkedIn: Boolean = false,
            donating: Boolean? = null,
        ) = MissionParticipant(
            id = "p1",
            userId = "u1",
            name = "Rhea",
            role = null,
            checkedIn = checkedIn,
            comment = null,
            donating = donating,
        )

        val joins = mutableListOf<String>()
        val leaves = mutableListOf<Pair<String, String>>()
        val checkIns = mutableListOf<Pair<String, Boolean>>()
        val preferences = mutableListOf<Pair<String, Boolean>>()
        var writeAnswer: ApiResult<MissionParticipant>? = null
        var joinAnswer: ApiResult<MissionDetail>? = null
        var leaveAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        var jobTypeAnswer: List<MissionJobType> = listOf(MissionJobType("j1", "Pilot"))
        val joinRequests = mutableListOf<Triple<String, String?, Boolean>>()

        override suspend fun jobTypes(): ApiResult<List<MissionJobType>> =
            ApiResult.Success(jobTypeAnswer)

        override suspend fun join(
            missionId: String,
            userId: String,
            desiredJobTypeId: String?,
            donate: Boolean,
        ): ApiResult<MissionDetail> {
            joins.add(missionId)
            joinRequests.add(Triple(userId, desiredJobTypeId, donate))
            return joinAnswer ?: detail(missionId)
        }

        override suspend fun leave(
            missionId: String,
            participantId: String,
        ): ApiResult<Unit> {
            leaves.add(missionId to participantId)
            return leaveAnswer
        }

        override suspend fun setCheckedIn(
            missionId: String,
            participantId: String,
            checkedIn: Boolean,
        ): ApiResult<MissionParticipant> {
            checkIns.add(participantId to checkedIn)
            return writeAnswer ?: ApiResult.Success(row(checkedIn = checkedIn))
        }

        override suspend fun setPlannedFunction(
            missionId: String,
            participant: MissionParticipant,
            jobTypeId: String?,
        ): ApiResult<MissionParticipant> = error("the manager's roster has its own test")

        override suspend fun setDonating(
            missionId: String,
            participantId: String,
            donating: Boolean,
        ): ApiResult<MissionParticipant> {
            preferences.add(participantId to donating)
            return writeAnswer ?: ApiResult.Success(row(donating = donating))
        }

        val booked = mutableListOf<List<Any?>>()
        val rewritten = mutableListOf<List<Any?>>()
        val removed = mutableListOf<String>()
        var bookAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun addFinanceEntry(
            missionId: String,
            participantId: String,
            income: Boolean,
            amount: String,
            note: String?,
        ): ApiResult<Unit> {
            booked.add(listOf(missionId, participantId, income, amount, note))
            return bookAnswer
        }

        override suspend fun updateFinanceEntry(
            entryId: String,
            income: Boolean,
            amount: String,
            note: String?,
            version: Long?,
        ): ApiResult<Unit> {
            rewritten.add(listOf(entryId, income, amount, note, version))
            return bookAnswer
        }

        override suspend fun deleteFinanceEntry(entryId: String): ApiResult<Unit> {
            removed.add(entryId)
            return bookAnswer
        }
    }

    private fun detail(
        name: String = "Vertikaler Abbau",
        started: Boolean = true,
        canManage: Boolean = false,
        vararg roster: MissionParticipant,
    ) = MissionDetail(
        id = "m1",
        name = name,
        description = null,
        status = MissionStatus.PLANNED,
        rawStatus = "PLANNED",
        meetingTime = null,
        plannedStartTime = null,
        actualStartTime = if (started) Instant.parse("2026-08-23T12:00:00Z") else null,
        plannedEndTime = null,
        isInternal = false,
        meetingPoint = null,
        operationName = null,
        orgUnitName = null,
        orgUnitShorthand = null,
        partyLeadName = null,
        registeredParticipants = roster.size,
        checkedInParticipants = roster.count { it.checkedIn },
        participants = roster.toList(),
        units = emptyList(),
        steps = emptyList(),
        objectives = emptyList(),
        frequencies = emptyList(),
        canManage = canManage,
    )

    private fun finances() =
        MissionFinances(
            total = "74700",
            incomeSum = "86400",
            incomeCount = 3,
            expenseSum = "11700",
            expenseCount = 2,
            entries = emptyList(),
            totalEntries = 0,
        )

    /**
     * One booking, as the caller's own.
     *
     * @return the entry.
     */
    private fun entry() =
        MissionFinanceEntry(
            id = "e1",
            income = true,
            amount = "12000",
            note = "Erlös",
            participantName = "Rhea",
            participantId = "p1",
            version = ENTRY_VERSION,
        )

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = RecordingSource()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The caller, as the identity read answers.
     *
     * @property answer what to return.
     */
    private class FakeIdentity(
        private val answer: ApiResult<Identity>,
    ) : IdentitySource {
        override fun forget() = Unit

        override suspend fun myUserId(): ApiResult<String> =
            when (answer) {
                is ApiResult.Failure -> answer
                is ApiResult.Success -> ApiResult.Success(answer.value.userId)
            }

        override suspend fun me(): ApiResult<Identity> = answer
    }

    private class FakeConnectivity(
        initial: Boolean = true,
    ) : Connectivity {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> get() = state
    }

    private fun viewModel(
        identity: ApiResult<Identity> = ApiResult.Success(Identity("u1", logistician = false)),
        connectivity: Connectivity = FakeConnectivity(),
    ) = MissionDetailViewModel(source, FakeIdentity(identity), connectivity, "m1")

    /**
     * One participant row, the caller's own.
     *
     * @param checkedIn whether it is checked in.
     * @param donating whether the share is donated.
     * @return the row.
     */
    private fun mine(
        checkedIn: Boolean = false,
        donating: Boolean? = null,
    ) = MissionParticipant(
        id = "p1",
        userId = "u1",
        name = "Rhea",
        role = null,
        checkedIn = checkedIn,
        comment = null,
        donating = donating,
    )

    @Test
    fun `the Einsatz loads and the Uebersicht tab is the one showing`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()

            model.load()
            advanceUntilIdle()

            assertEquals(MissionDetailPhase.Ready, model.state.value.phase)
            assertEquals("Vertikaler Abbau", model.state.value.detail?.name)
            assertEquals(MissionTab.OVERVIEW, model.state.value.tab)
        }

    @Test
    fun `the money is not fetched until its tab is opened`() =
        runTest(dispatcher) {
            // Six tabs come from one response. The seventh is two more calls most members never
            // look at, and one a member without the permission cannot make succeed at all.
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()

            model.load()
            advanceUntilIdle()

            assertEquals(0, source.financeCalls)
            assertEquals(MissionFinancesPhase.Idle, model.state.value.finances)
        }

    @Test
    fun `opening the Finanzen tab fetches it once, and only once`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            model.onTabSelected(MissionTab.PARTICIPANTS)
            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()

            assertEquals("switching back must not re-fetch", 1, source.financeCalls)
            assertTrue(model.state.value.finances is MissionFinancesPhase.Ready)
        }

    @Test
    fun `a refused Finanzen tab leaves the Einsatz intact`() =
        runTest(dispatcher) {
            // The ordinary case for a member who may see the Einsatz but not its books. Turning
            // that into a failed screen would hide an Einsatz behind a permission it does not need.
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()

            assertEquals(MissionDetailPhase.Ready, model.state.value.phase)
            assertTrue(model.state.value.finances is MissionFinancesPhase.Failed)
        }

    @Test
    fun `the Finanzen tab can be retried without reloading the Einsatz around it`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            val detailCallsBefore = source.detailCalls

            model.onRetryFinances()
            advanceUntilIdle()

            assertTrue(model.state.value.finances is MissionFinancesPhase.Ready)
            assertEquals("the Einsatz was not re-read", detailCallsBefore, source.detailCalls)
        }

    @Test
    fun `a refused Einsatz is reported with its cause, so the screen can word it`() =
        runTest(dispatcher) {
            // What an outsider gets for an internal or terminal Einsatz. Distinguishable from an
            // outage, which is why the error is carried rather than flattened to a boolean.
            source.queueDetail(ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel()

            model.load()
            advanceUntilIdle()

            val phase = model.state.value.phase
            assertTrue(phase is MissionDetailPhase.Failed)
            assertTrue((phase as MissionDetailPhase.Failed).error is ApiError.Forbidden)
        }

    @Test
    fun `a refresh keeps the Einsatz on screen while it runs`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail("Alt")))
            source.queueDetail(ApiResult.Success(detail("Neu")))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            assertEquals(MissionDetailPhase.Ready, model.state.value.phase)
            assertEquals("Alt", model.state.value.detail?.name)

            advanceUntilIdle()
            assertEquals("Neu", model.state.value.detail?.name)
        }

    @Test
    fun `a refresh re-reads the money only when its tab was already opened`() =
        runTest(dispatcher) {
            // Refreshing must not silently acquire a permission-dependent read the member never
            // asked for -- nor skip one they are looking at.
            source.queueDetail(ApiResult.Success(detail()))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            advanceUntilIdle()
            assertEquals("never opened, so never fetched", 0, source.financeCalls)

            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            model.onRefresh()
            advanceUntilIdle()
            assertEquals("opened, so refreshed with the rest", 2, source.financeCalls)
        }

    @Test
    fun `signing up opens the sheet rather than joining outright`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            assertNull(model.state.value.mySignUp)

            model.onToggleSignUp()
            advanceUntilIdle()

            // Two answers belong to the moment of signing up — where the share goes and which
            // function is wanted — so the tap opens the sheet that collects them (design ch. 06,
            // artboard 3) and nothing is written yet.
            assertNotNull(model.state.value.joinSheet)
            assertEquals(emptyList<String>(), source.joins)
        }

    @Test
    fun `the sheet reads the Funktionen when it opens, not with the Einsatz`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onJoinSheetOpened()
            advanceUntilIdle()

            assertEquals(listOf("Pilot"), model.state.value.joinSheet?.jobTypes?.map { it.name })
        }

    @Test
    fun `the sign-up carries the payout choice and the desired function`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onJoinSheetOpened()
            advanceUntilIdle()
            val pilot = model.state.value.joinSheet!!.jobTypes.first()

            model.onDesiredFunction(pilot)
            model.onJoinPayout(donate = true)
            model.onJoinConfirmed()
            advanceUntilIdle()

            assertEquals(listOf(Triple("u1", pilot.id, true)), source.joinRequests)
            assertNull("a landed sign-up closes its sheet", model.state.value.joinSheet)
        }

    /**
     * „Wunsch" has to be retractable.
     *
     * A chip row with no way back makes an optional field compulsory in practice — whichever chip
     * was touched first would be sent.
     */
    @Test
    fun `tapping the chosen function again clears it`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onJoinSheetOpened()
            advanceUntilIdle()
            val pilot = model.state.value.joinSheet!!.jobTypes.first()

            model.onDesiredFunction(pilot)
            model.onDesiredFunction(pilot)

            assertNull(model.state.value.joinSheet?.desired)
        }

    @Test
    fun `a refused sign-up keeps the sheet and the answers in it`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onJoinSheetOpened()
            advanceUntilIdle()
            model.onJoinPayout(donate = true)
            source.joinAnswer = ApiResult.Failure(ApiError.Forbidden())

            model.onJoinConfirmed()
            advanceUntilIdle()

            val sheet = model.state.value.joinSheet
            assertNotNull("nothing was written, so nothing is taken away", sheet)
            assertEquals(true, sheet?.donate)
            assertEquals(ApiError.Forbidden(), sheet?.error)
        }

    /** A catalogue that will not load must not block an optional field. */
    @Test
    fun `the sheet still works when the Funktionen cannot be read`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            source.jobTypeAnswer = emptyList()
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onJoinSheetOpened()
            advanceUntilIdle()

            assertNotNull(model.state.value.joinSheet)
            assertEquals(emptyList<Any>(), model.state.value.joinSheet?.jobTypes)
        }

    @Test
    fun `withdrawing removes the caller's own row and re-reads the roster`() =
        runTest(dispatcher) {
            // The withdrawal answers 204, so the counts above the roster would otherwise be the
            // app's guess rather than the server's.
            source.queueDetail(ApiResult.Success(detail("Vertikaler Abbau", roster = arrayOf(source.row()))))
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onToggleSignUp()
            advanceUntilIdle()

            assertEquals(listOf("m1" to "p1"), source.leaves)
            assertNull(model.state.value.mySignUp)
        }

    @Test
    fun `nothing is offered while the app does not know who the caller is`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail("Vertikaler Abbau", roster = arrayOf(source.row()))))
            val model = viewModel(identity = ApiResult.Failure(ApiError.NotFound()))
            model.load()
            advanceUntilIdle()

            assertEquals(false, model.state.value.writable)
            assertNull(model.state.value.mySignUp)
        }

    @Test
    fun `checking in patches the caller's row and the count above it`() =
        runTest(dispatcher) {
            // The slim endpoint answers with the row alone. Re-reading the whole Einsatz for one
            // timestamp would make a check-in cost what opening the screen costs.
            source.queueDetail(ApiResult.Success(detail("Vertikaler Abbau", roster = arrayOf(source.row()))))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onToggleCheckIn()
            advanceUntilIdle()

            assertEquals(listOf("p1" to true), source.checkIns)
            assertEquals(true, model.state.value.mySignUp?.checkedIn)
            assertEquals(1, model.state.value.detail?.checkedInParticipants)
            assertEquals("no second read for one row", 1, source.detailCalls)
        }

    @Test
    fun `checking out is the same action once checked in`() =
        runTest(dispatcher) {
            source.queueDetail(
                ApiResult.Success(detail("Vertikaler Abbau", roster = arrayOf(source.row(checkedIn = true)))),
            )
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onToggleCheckIn()
            advanceUntilIdle()

            assertEquals(listOf("p1" to false), source.checkIns)
        }

    @Test
    fun `the payout preference flips between paid out and donated`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail("Vertikaler Abbau", roster = arrayOf(source.row()))))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onTogglePayoutPreference()
            advanceUntilIdle()

            assertEquals(listOf("p1" to true), source.preferences)
            assertEquals(true, model.state.value.mySignUp?.donating)
        }

    @Test
    fun `a refusal is kept and the row is left as it was`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail("Vertikaler Abbau", roster = arrayOf(source.row()))))
            source.writeAnswer = ApiResult.Failure(ApiError.Forbidden())
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onToggleCheckIn()
            advanceUntilIdle()

            assertTrue(model.state.value.error is ApiError.Forbidden)
            assertEquals(false, model.state.value.mySignUp?.checkedIn)
        }

    @Test
    fun `checking in is not offered before the Einsatz has started`() =
        runTest(dispatcher) {
            // The server refuses it — "Cannot check in before mission actual start time is set",
            // found on a device — so the control is absent rather than returning a 400.
            source.queueDetail(
                ApiResult.Success(detail("Vertikaler Abbau", started = false, roster = arrayOf(source.row()))),
            )
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            assertEquals(false, model.state.value.checkInPossible)

            model.onToggleCheckIn()
            advanceUntilIdle()

            assertTrue(source.checkIns.isEmpty())
        }

    @Test
    fun `nothing is written while the device has no network`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel(connectivity = FakeConnectivity(initial = false))
            model.load()
            advanceUntilIdle()

            model.onToggleSignUp()
            advanceUntilIdle()

            assertTrue(source.joins.isEmpty())
            assertEquals(false, model.state.value.online)
        }

    @Test
    fun `booking needs a sign-up to book against`() =
        runTest(dispatcher) {
            // The create names a participant, and the only one the app may name is the caller's
            // own. Without a sign-up there is nothing to name.
            source.queueDetail(ApiResult.Success(detail()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            assertEquals(false, model.state.value.bookingPossible)

            model.onAddEntry()

            assertNull(model.state.value.entryDraft)
        }

    @Test
    fun `a booking carries the direction, the amount and the caller's own row`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail(roster = arrayOf(source.row()))))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onAddEntry()
            model.onEntryIncomeChanged(false)
            model.onEntryAmountChanged("2500")
            model.onEntryNoteChanged("Treibstoff")

            model.onSaveEntry()
            advanceUntilIdle()

            assertEquals(listOf("m1", "p1", false, "2500", "Treibstoff"), source.booked.single())
            assertNull(model.state.value.entryDraft)
        }

    @Test
    fun `an amount of nothing is not a booking`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail(roster = arrayOf(source.row()))))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onAddEntry()

            model.onEntryAmountChanged("0")

            assertEquals(false, model.state.value.entryDraft?.submittable)
        }

    @Test
    fun `editing a booking echoes its version`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail(roster = arrayOf(source.row()))))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onEditEntry(entry())
            model.onEntryAmountChanged("40")

            model.onSaveEntry()
            advanceUntilIdle()

            assertEquals(listOf("e1", true, "40", "Erlös", ENTRY_VERSION), source.rewritten.single())
        }

    @Test
    fun `the editor opens on a number the field can hold`() =
        runTest(dispatcher) {
            // The wire carries `12000.0000` and the field takes digits alone.
            source.queueDetail(ApiResult.Success(detail(roster = arrayOf(source.row()))))
            val model = viewModel()
            model.load()
            advanceUntilIdle()

            model.onEditEntry(entry().copy(amount = "12000.0000"))

            assertEquals("12000", model.state.value.entryDraft?.amount)
        }

    @Test
    fun `a refused booking keeps the editor open with what was typed`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail(roster = arrayOf(source.row()))))
            source.bookAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onAddEntry()
            model.onEntryAmountChanged("30")

            model.onSaveEntry()
            advanceUntilIdle()

            assertEquals("30", model.state.value.entryDraft?.amount)
            assertTrue(model.state.value.error is ApiError.OptimisticLock)
        }

    @Test
    fun `a booking re-reads the tab, because the totals moved with it`() =
        runTest(dispatcher) {
            source.queueDetail(ApiResult.Success(detail(roster = arrayOf(source.row()))))
            source.queueFinances(ApiResult.Success(finances()))
            val model = viewModel()
            model.load()
            advanceUntilIdle()
            model.onTabSelected(MissionTab.FINANCES)
            advanceUntilIdle()
            val before = source.financeCalls

            model.onDeleteEntry(entry())
            advanceUntilIdle()

            assertEquals(listOf("e1"), source.removed)
            assertEquals(before + 1, source.financeCalls)
        }
}

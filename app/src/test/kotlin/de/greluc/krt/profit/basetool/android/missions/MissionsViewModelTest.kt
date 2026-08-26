/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.Mission
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionPage
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionQuery
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * The Einsatz list's own rules: when a filter change reaches the server, what a failure costs, and
 * what paging must not lose.
 *
 * The source is a recording fake rather than a mock, because most of what matters here is *which
 * query* reached it and *how often* — not that a method was called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** A total larger than the loaded page, so `hasMore` has something to be true about. */
    private val manyElements = 7L

    /** How many pages [manyElements] spans. */
    private val manyPages = 4

    /** Comfortably shorter than the 300 ms debounce. */
    private val insideDebounceMs = 100L

    /**
     * Records every search and answers with whatever is queued.
     *
     * @property answers the responses to give, in order; the last one repeats once exhausted.
     */
    private class RecordingSource(
        private val answers: MutableList<ApiResult<MissionPage>> = mutableListOf(),
    ) : MissionSource {
        /** Every (query, page) pair that reached the source, in order. */
        val calls = mutableListOf<Pair<MissionQuery, Int>>()

        /**
         * Queues one answer.
         *
         * @param answer what the next call returns.
         */
        fun queue(answer: ApiResult<MissionPage>) {
            answers.add(answer)
        }

        override suspend fun search(
            query: MissionQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<MissionPage> {
            calls.add(query to page)
            return if (answers.size > 1) answers.removeAt(0) else answers.first()
        }

        // The list never opens an Einsatz; a stub that throws says so louder than one that returns
        // something plausible.
        override suspend fun detail(id: String): ApiResult<MissionDetail> = error("the list never reads a detail")

        override suspend fun finances(missionId: String): ApiResult<MissionFinances> =
            error("the list never reads finances")

        override suspend fun jobTypes(): ApiResult<List<MissionJobType>> =
            error("this fake never reads the Funktionen catalogue")

        override suspend fun join(
            missionId: String,
            userId: String,
            desiredJobTypeId: String?,
            donate: Boolean,
        ): ApiResult<MissionDetail> = error("this fake never signs anybody up")

        override suspend fun leave(
            missionId: String,
            participantId: String,
        ): ApiResult<Unit> = error("this fake never withdraws anybody")

        override suspend fun setCheckedIn(
            missionId: String,
            participantId: String,
            checkedIn: Boolean,
        ): ApiResult<MissionParticipant> = error("this fake never checks anybody in")

        override suspend fun setDonating(
            missionId: String,
            participantId: String,
            donating: Boolean,
        ): ApiResult<MissionParticipant> = error("this fake never changes a preference")

        override suspend fun addFinanceEntry(
            missionId: String,
            participantId: String,
            income: Boolean,
            amount: String,
            note: String?,
        ): ApiResult<Unit> = error("this fake books nothing")

        override suspend fun updateFinanceEntry(
            entryId: String,
            income: Boolean,
            amount: String,
            note: String?,
            version: Long?,
        ): ApiResult<Unit> = error("this fake books nothing")

        override suspend fun deleteFinanceEntry(entryId: String): ApiResult<Unit> =
            error("this fake books nothing")
    }

    private fun mission(id: String) =
        Mission(
            id = id,
            name = "Einsatz $id",
            status = MissionStatus.PLANNED,
            rawStatus = "PLANNED",
            meetingTime = null,
            plannedStartTime = Instant.parse("2026-08-21T19:00:00Z"),
            actualStartTime = null,
            plannedEndTime = null,
            isInternal = false,
            operationName = null,
            orgUnitName = null,
            orgUnitShorthand = null,
            meetingPoint = null,
        )

    private fun page(
        ids: List<String>,
        page: Int = 0,
        totalPages: Int = 1,
        total: Long = ids.size.toLong(),
    ) = ApiResult.Success(
        MissionPage(
            missions = ids.map(::mission),
            page = page,
            totalPages = totalPages,
            totalElements = total,
        ),
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

    @Test
    fun `the first page populates the list and its total`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a", "b"), total = manyElements, totalPages = manyPages))
            val viewModel = MissionsViewModel(source)

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(MissionsPhase.Ready, state.phase)
            assertEquals(listOf("a", "b"), state.missions.map { it.id })
            assertEquals(manyElements, state.total)
            assertTrue(state.hasMore)
        }

    @Test
    fun `an empty result is Ready and empty, never a failure`() =
        runTest(dispatcher) {
            // "No Einsätze match your filter" and "the list is broken" are different screens, and
            // showing the second for the first tells a member something is wrong when nothing is.
            source.queue(page(emptyList(), totalPages = 0))
            val viewModel = MissionsViewModel(source)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(MissionsPhase.Ready, viewModel.state.value.phase)
            assertTrue(viewModel.state.value.isEmpty)
        }

    @Test
    fun `a failed first page is reported with its cause`() =
        runTest(dispatcher) {
            val error = ApiError.Network(IOException("offline"))
            source.queue(ApiResult.Failure(error))
            val viewModel = MissionsViewModel(source)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(MissionsPhase.Failed(error), viewModel.state.value.phase)
        }

    @Test
    fun `typing is debounced into one request`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()
            val before = source.calls.size

            "Lyria".forEachIndexed { index, _ -> viewModel.onSearchChanged("Lyria".take(index + 1)) }
            advanceUntilIdle()

            assertEquals("five keystrokes must cost one request", before + 1, source.calls.size)
            assertEquals("Lyria", source.calls.last().first.text)
        }

    @Test
    fun `a keystroke reaches the state synchronously, ahead of the debounce`() =
        runTest(dispatcher) {
            // The field is a CONTROLLED component: the screen renders whatever the state holds. A
            // state that lagged the debounce would feed the previous value straight back and the
            // character the member just typed would vanish as they typed it. Measured on a device
            // before this existed: the search field accepted nothing at all, while the view model
            // tests -- which never render a field -- were green.
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()

            viewModel.onSearchChanged("Ly")

            assertEquals("Ly", viewModel.state.value.searchText)
            assertTrue("the reset must be offered from the first keystroke", viewModel.state.value.isNarrowed)
            assertEquals("nothing may have reached the server yet", "", viewModel.state.value.query.text)
        }

    @Test
    fun `resetting clears the typed value too, not just the query`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            viewModel.onSearchChanged("Lyria")
            advanceUntilIdle()

            viewModel.onResetFilters()
            advanceUntilIdle()

            assertEquals("", viewModel.state.value.searchText)
            assertFalse(viewModel.state.value.isNarrowed)
        }

    @Test
    fun `a keystroke does not reach the server before the debounce elapses`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()
            val before = source.calls.size

            viewModel.onSearchChanged("Ly")
            advanceTimeBy(insideDebounceMs)

            assertEquals(before, source.calls.size)
        }

    @Test
    fun `a tapped filter reaches the server immediately`() =
        runTest(dispatcher) {
            // Unlike typing: a chip is one deliberate act, and making the member wait 300 ms for it
            // would read as the app being slow rather than as the app being careful.
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()
            val before = source.calls.size

            viewModel.onStatusesChanged(setOf(MissionStatus.ACTIVE))
            advanceUntilIdle()

            assertEquals(before + 1, source.calls.size)
            assertEquals(setOf(MissionStatus.ACTIVE), source.calls.last().first.statuses)
        }

    @Test
    fun `setting the same filter again does not re-fetch`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            viewModel.onStatusesChanged(setOf(MissionStatus.ACTIVE))
            advanceUntilIdle()
            val before = source.calls.size

            viewModel.onStatusesChanged(setOf(MissionStatus.ACTIVE))
            advanceUntilIdle()

            assertEquals(before, source.calls.size)
        }

    @Test
    fun `a filter change starts again at page zero and replaces the rows`() =
        runTest(dispatcher) {
            // Appending would leave the previous filter's Einsätze underneath the new filter's,
            // which reads as the filter not having worked.
            source.queue(page(listOf("a", "b"), totalPages = 2))
            source.queue(page(listOf("c")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()

            viewModel.onIncludePastChanged(true)
            advanceUntilIdle()

            assertEquals(0, source.calls.last().second)
            assertEquals(listOf("c"), viewModel.state.value.missions.map { it.id })
        }

    @Test
    fun `the next page appends rather than replacing`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a"), page = 0, totalPages = 2, total = 2))
            source.queue(page(listOf("b"), page = 1, totalPages = 2, total = 2))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()

            viewModel.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), viewModel.state.value.missions.map { it.id })
            assertFalse(viewModel.state.value.hasMore)
        }

    @Test
    fun `load more is ignored when the server has no further page`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()
            val before = source.calls.size

            viewModel.onLoadMore()
            advanceUntilIdle()

            assertEquals(before, source.calls.size)
        }

    @Test
    fun `a failed next page keeps the rows already on screen`() =
        runTest(dispatcher) {
            // A working list must not be replaced by an error because its continuation failed; the
            // member can simply scroll again.
            source.queue(page(listOf("a"), page = 0, totalPages = 2, total = 2))
            source.queue(ApiResult.Failure(ApiError.Network(IOException("offline"))))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()

            viewModel.onLoadMore()
            advanceUntilIdle()

            assertEquals(MissionsPhase.Ready, viewModel.state.value.phase)
            assertEquals(listOf("a"), viewModel.state.value.missions.map { it.id })
            assertFalse(viewModel.state.value.loadingMore)
        }

    @Test
    fun `resetting clears the search field as well as the chips`() =
        runTest(dispatcher) {
            // Clearing only the query object would leave the old term in the field, and the next
            // keystroke would restore a filter the member believes they removed.
            source.queue(page(listOf("a")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            viewModel.onSearchChanged("Lyria")
            advanceUntilIdle()

            viewModel.onResetFilters()
            advanceUntilIdle()

            assertFalse("the reset itself must leave nothing narrowed", viewModel.state.value.isNarrowed)
            assertEquals("", source.calls.last().first.text)

            // And the next keystroke starts from empty rather than resuming the old term, which is
            // what a field cleared only in the query object -- not in the typed value -- would do.
            viewModel.onSearchChanged("L")
            advanceUntilIdle()

            assertEquals("L", source.calls.last().first.text)
        }

    @Test
    fun `a refresh keeps the rows visible instead of flashing back to a spinner`() =
        runTest(dispatcher) {
            source.queue(page(listOf("a")))
            source.queue(page(listOf("a", "b")))
            val viewModel = MissionsViewModel(source)
            viewModel.load()
            advanceUntilIdle()

            viewModel.onRefresh()
            // Before the answer lands: still Ready, still showing the old rows.
            assertEquals(MissionsPhase.Ready, viewModel.state.value.phase)
            assertEquals(listOf("a"), viewModel.state.value.missions.map { it.id })

            advanceUntilIdle()
            assertEquals(listOf("a", "b"), viewModel.state.value.missions.map { it.id })
            assertFalse(viewModel.state.value.refreshing)
        }
}

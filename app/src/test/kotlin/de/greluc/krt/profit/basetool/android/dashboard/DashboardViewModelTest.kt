/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.dashboard

import de.greluc.krt.profit.basetool.android.core.data.Announcement
import de.greluc.krt.profit.basetool.android.core.data.AnnouncementSource
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
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Duration

/**
 * The dashboard's two reads.
 *
 * The rule worth a test: they fail **independently**. A member who cannot reach the announcement
 * still needs to know what is starting tonight, and the reverse holds as well.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers the Einsatz search and records the query it was asked for.
     *
     * @property answers responses, the last one repeating once exhausted.
     */
    private class RecordingMissions(
        private val answers: MutableList<ApiResult<MissionPage>> = mutableListOf(),
    ) : MissionSource {
        val queries = mutableListOf<MissionQuery>()
        var pageSizes = mutableListOf<Int>()

        override suspend fun search(
            query: MissionQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<MissionPage> {
            queries.add(query)
            pageSizes.add(pageSize)
            return if (answers.size > 1) answers.removeAt(0) else answers.first()
        }

        override suspend fun detail(id: String): ApiResult<MissionDetail> = error("not used")

        override suspend fun finances(missionId: String): ApiResult<MissionFinances> = error("not used")

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

    /**
     * Answers the announcement read, and remembers what the member has marked.
     *
     * The read flag lives on the member rather than on the notice, so this fake models both
     * halves: `lastRead` is the id the server holds, and `markRead` moves it.
     *
     * @property answer what the announcement read returns.
     * @property lastReadId the id the member has already marked, or `null`.
     * @property fail whether the mark-read call refuses.
     */
    private class FixedAnnouncement(
        private val answer: ApiResult<Announcement?>,
        var lastReadId: String? = null,
        private val fail: Boolean = false,
    ) : AnnouncementSource {
        var marked = 0

        override suspend fun current(): ApiResult<Announcement?> = answer

        override suspend fun lastRead(): ApiResult<String?> = ApiResult.Success(lastReadId)

        override suspend fun markRead(id: String): ApiResult<String?> {
            marked += 1
            if (fail) {
                return ApiResult.Failure(ApiError.Network(IOException("no route")))
            }
            lastReadId = id
            return ApiResult.Success(id)
        }
    }

    private fun mission(id: String) =
        Mission(
            id = id,
            name = "Vertikaler Abbau",
            status = MissionStatus.PLANNED,
            rawStatus = "PLANNED",
            meetingTime = null,
            plannedStartTime = null,
            actualStartTime = null,
            plannedEndTime = null,
            isInternal = false,
            operationName = null,
            orgUnitName = null,
            orgUnitShorthand = null,
            meetingPoint = null,
        )

    private fun page(vararg rows: Mission) =
        MissionPage(rows.toList(), page = 0, totalPages = 1, totalElements = rows.size.toLong())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        missions: MissionSource,
        announcements: AnnouncementSource,
    ) = DashboardViewModel(missions, announcements, ServerClock())

    /**
     * The marker is off until the app knows, not on.
     *
     * "Unread" is an invitation to act. Showing one for the frames before the read flag lands, and
     * then taking it back, teaches a member that the marker means nothing.
     */
    @Test
    fun `an unmarked notice reads as unread once the flag lands`() =
        runTest(dispatcher) {
            val notice = Announcement("a-1", "Wartung", null)
            val source = FixedAnnouncement(ApiResult.Success(notice), lastReadId = null)
            val model = viewModel(RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1"))))), source)

            assertTrue("starts counted as read", model.state.value.announcementRead)
            model.load()
            advanceUntilIdle()

            assertEquals(false, model.state.value.announcementRead)
        }

    @Test
    fun `a notice the member already marked reads as read`() =
        runTest(dispatcher) {
            val notice = Announcement("a-1", "Wartung", null)
            val source = FixedAnnouncement(ApiResult.Success(notice), lastReadId = "a-1")
            val model = viewModel(RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1"))))), source)

            model.load()
            advanceUntilIdle()

            assertTrue(model.state.value.announcementRead)
        }

    @Test
    fun `marking clears the marker and is sent once`() =
        runTest(dispatcher) {
            val source = FixedAnnouncement(ApiResult.Success(Announcement("a-1", "Wartung", null)))
            val model = viewModel(RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1"))))), source)
            model.load()
            advanceUntilIdle()

            model.onAnnouncementRead()
            model.onAnnouncementRead()
            advanceUntilIdle()

            assertTrue(model.state.value.announcementRead)
            assertEquals("a second tap has nothing left to send", 1, source.marked)
            assertEquals("a-1", source.lastReadId)
        }

    /**
     * A refused mark puts the marker back.
     *
     * A band that says "gelesen" while the server disagrees will say „UNGELESEN" again at the next
     * start, and the member will have learnt that the button does not stick.
     */
    @Test
    fun `a refused mark restores the marker`() =
        runTest(dispatcher) {
            val source = FixedAnnouncement(ApiResult.Success(Announcement("a-1", "Wartung", null)), fail = true)
            val model = viewModel(RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1"))))), source)
            model.load()
            advanceUntilIdle()

            model.onAnnouncementRead()
            advanceUntilIdle()

            assertEquals(false, model.state.value.announcementRead)
        }

    @Test
    fun `both parts load`() =
        runTest(dispatcher) {
            val missions = RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1")))))
            val model =
                viewModel(missions, FixedAnnouncement(ApiResult.Success(Announcement("a-1", "Wartung", null))))

            model.load()
            advanceUntilIdle()

            assertEquals(DashboardPhase.Ready, model.state.value.phase)
            assertEquals(1, model.state.value.missions.size)
            assertEquals("Wartung", model.state.value.announcement?.content)
        }

    @Test
    fun `no announcement is a result, not a failure`() =
        runTest(dispatcher) {
            // The server answers 204 when there is nothing to announce. The correct rendering is no
            // banner, and the Einsatz band must be unaffected.
            val missions = RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1")))))
            val model = viewModel(missions, FixedAnnouncement(ApiResult.Success(null)))

            model.load()
            advanceUntilIdle()

            assertNull(model.state.value.announcement)
            assertEquals(DashboardPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `a failed announcement does not blank the Einsatz band`() =
        runTest(dispatcher) {
            // Two unrelated reads behind unrelated permissions. One outage must not take the other
            // down, or a member loses tonight's Einsatz to a broken notice.
            val missions = RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1")))))
            val model =
                viewModel(
                    missions,
                    FixedAnnouncement(ApiResult.Failure(ApiError.Network(IOException("offline")))),
                )

            model.load()
            advanceUntilIdle()

            assertNull(model.state.value.announcement)
            assertEquals(DashboardPhase.Ready, model.state.value.phase)
            assertEquals(1, model.state.value.missions.size)
        }

    @Test
    fun `a failed Einsatz band does not hide the announcement`() =
        runTest(dispatcher) {
            val missions =
                RecordingMissions(mutableListOf(ApiResult.Failure(ApiError.Network(IOException("offline")))))
            val model =
                viewModel(missions, FixedAnnouncement(ApiResult.Success(Announcement("a-1", "Wartung", null))))

            model.load()
            advanceUntilIdle()

            assertEquals(DashboardPhase.Failed, model.state.value.phase)
            assertNotNull(model.state.value.announcement)
        }

    @Test
    fun `the band asks for a seven-day window, bounded at both ends`() =
        runTest(dispatcher) {
            // Unbounded above, the "next 7 days" band would show whatever the server had, and the
            // heading would be a lie.
            val missions = RecordingMissions(mutableListOf(ApiResult.Success(page())))
            val model = viewModel(missions, FixedAnnouncement(ApiResult.Success(null)))

            model.load()
            advanceUntilIdle()

            val query = missions.queries.single()
            val from = requireNotNull(query.from)
            val until = requireNotNull(query.until)
            assertEquals(Duration.ofDays(SEVEN), Duration.between(from, until))
        }

    @Test
    fun `the band is a summary, not the list`() =
        runTest(dispatcher) {
            val missions = RecordingMissions(mutableListOf(ApiResult.Success(page())))
            val model = viewModel(missions, FixedAnnouncement(ApiResult.Success(null)))

            model.load()
            advanceUntilIdle()

            assertTrue("the band must not pull a full page", missions.pageSizes.single() <= MAX_BAND)
        }

    @Test
    fun `a refresh keeps what is on screen while it runs`() =
        runTest(dispatcher) {
            val missions =
                RecordingMissions(
                    mutableListOf(
                        ApiResult.Success(page(mission("m1"))),
                        ApiResult.Success(page(mission("m2"))),
                    ),
                )
            val model = viewModel(missions, FixedAnnouncement(ApiResult.Success(null)))
            model.load()
            advanceUntilIdle()

            model.onRefresh()
            assertEquals(DashboardPhase.Ready, model.state.value.phase)
            assertEquals("m1", model.state.value.missions.single().id)

            advanceUntilIdle()
            assertEquals("m2", model.state.value.missions.single().id)
        }

    private companion object {
        /** The window the design names. */
        const val SEVEN = 7L

        /** What still counts as a summary rather than a list. */
        const val MAX_BAND = 10
    }
}

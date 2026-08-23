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
import de.greluc.krt.profit.basetool.android.core.data.MissionPage
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
    }

    /**
     * Answers the announcement read.
     *
     * @property answer what to return.
     */
    private class FixedAnnouncement(
        private val answer: ApiResult<Announcement?>,
    ) : AnnouncementSource {
        override suspend fun current(): ApiResult<Announcement?> = answer
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

    @Test
    fun `both parts load`() =
        runTest(dispatcher) {
            val missions = RecordingMissions(mutableListOf(ApiResult.Success(page(mission("m1")))))
            val model =
                viewModel(missions, FixedAnnouncement(ApiResult.Success(Announcement("Wartung", null))))

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
                viewModel(missions, FixedAnnouncement(ApiResult.Success(Announcement("Wartung", null))))

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

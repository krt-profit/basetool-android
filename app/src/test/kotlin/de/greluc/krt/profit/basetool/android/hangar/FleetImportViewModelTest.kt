/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import de.greluc.krt.profit.basetool.android.core.data.FleetImportResult
import de.greluc.krt.profit.basetool.android.core.data.HangarSource
import de.greluc.krt.profit.basetool.android.core.data.HomeLocation
import de.greluc.krt.profit.basetool.android.core.data.Ship
import de.greluc.krt.profit.basetool.android.core.data.ShipDraft
import de.greluc.krt.profit.basetool.android.core.data.ShipPage
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeOption
import de.greluc.krt.profit.basetool.android.core.data.ShipTypePage
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.core.network.ProblemDetail
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What the Fleetview import does with a paste, a file, and a refusal. */
@OptIn(ExperimentalCoroutinesApi::class)
class FleetImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Puts the main dispatcher under the test's control. */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /** Gives it back. */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a paste is uploaded as a file, because that is what the endpoint takes`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val viewModel = FleetImportViewModel(source, AlwaysOnline)

            viewModel.onPasted("[{\"name\":\"Meridian\"}]")
            viewModel.onImport()
            advanceUntilIdle()

            val (name, bytes) = source.uploads.single()
            assertTrue("a paste has to be named as one in the server's log", name.endsWith(".json"))
            assertEquals("[{\"name\":\"Meridian\"}]", bytes.decodeToString())
        }

    @Test
    fun `a picked file wins over text left in the box`() =
        runTest(dispatcher) {
            // The member chose the file second, so the file is what they meant. Sending the box's
            // content instead would import something they had scrolled past and forgotten.
            val source = RecordingSource()
            val viewModel = FleetImportViewModel(source, AlwaysOnline)

            viewModel.onPasted("[{\"name\":\"stale\"}]")
            viewModel.onFilePicked("meine-flotte.json", "[{\"name\":\"chosen\"}]".toByteArray())
            viewModel.onImport()
            advanceUntilIdle()

            val (name, bytes) = source.uploads.single()
            assertEquals("meine-flotte.json", name)
            assertEquals("[{\"name\":\"chosen\"}]", bytes.decodeToString())
        }

    @Test
    fun `the tally arrives and the form is emptied`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            source.result =
                ApiResult.Success(
                    FleetImportResult(
                        imported = 2,
                        skipped = 1,
                        duplicates = 3,
                        skippedShips = listOf("Nonexistent Hull"),
                        duplicateShips = listOf("Carrack", "Prospector", "Cutlass Black"),
                    ),
                )
            val viewModel = FleetImportViewModel(source, AlwaysOnline)

            viewModel.onPasted("[]")
            viewModel.onImport()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(2, state.result?.imported)
            assertEquals(listOf("Nonexistent Hull"), state.result?.skippedShips)
            assertEquals("the box is cleared once the import landed", "", state.pasted)
            assertNull(state.fileBytes)
        }

    @Test
    fun `a refusal keeps what the member typed`() =
        runTest(dispatcher) {
            // Clearing the box on a 400 would make the member fetch the export again to fix a typo
            // the server just told them about.
            val source = RecordingSource()
            source.result =
                ApiResult.Failure(
                    ApiError.Validation(
                        ProblemDetail(detail = "The uploaded file must contain a JSON array at the root."),
                    ),
                )
            val viewModel = FleetImportViewModel(source, AlwaysOnline)

            viewModel.onPasted("{\"ships\":[]}")
            viewModel.onImport()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("{\"ships\":[]}", state.pasted)
            assertTrue(state.error is ApiError.Validation)
            assertTrue("the CTA has to come back", state.submittable)
        }

    @Test
    fun `offline, nothing is sent`() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val viewModel = FleetImportViewModel(source, Offline)
            advanceUntilIdle()

            viewModel.onPasted("[]")
            viewModel.onImport()
            advanceUntilIdle()

            assertTrue("an upload with no route would fail as a server error", source.uploads.isEmpty())
        }

    /** A hangar that records what was uploaded and answers with whatever the test set. */
    private class RecordingSource : HangarSource {
        val uploads = mutableListOf<Pair<String, ByteArray>>()
        var result: ApiResult<FleetImportResult> =
            ApiResult.Success(FleetImportResult(0, 0, 0, emptyList(), emptyList()))

        override suspend fun importFleetview(
            fileName: String,
            bytes: ByteArray,
        ): ApiResult<FleetImportResult> {
            uploads.add(fileName to bytes)
            return result
        }

        override suspend fun myShips(
            search: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<ShipPage> = ApiResult.Success(ShipPage(emptyList(), 0, 0, 0))

        override suspend fun orgOverview(
            search: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<ShipTypePage> = ApiResult.Success(ShipTypePage(emptyList(), 0, 0, 0))

        override suspend fun create(draft: ShipDraft): ApiResult<Ship> = error("not used")

        override suspend fun update(
            id: String,
            version: Long?,
            draft: ShipDraft,
        ): ApiResult<Ship> = error("not used")

        override suspend fun delete(id: String): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun shipTypes(query: String): ApiResult<List<ShipTypeOption>> =
            ApiResult.Success(emptyList())

        override suspend fun homeLocations(): ApiResult<List<HomeLocation>> = ApiResult.Success(emptyList())

        override suspend fun clearHangar(): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun setHomeLocationForAll(locationId: String): ApiResult<Unit> =
            ApiResult.Success(Unit)
    }

    /** A device with a route to the server. */
    private object AlwaysOnline : Connectivity {
        override val online: Flow<Boolean> = MutableStateFlow(true)
    }

    /** A device without one. */
    private object Offline : Connectivity {
        override val online: Flow<Boolean> = MutableStateFlow(false)
    }
}

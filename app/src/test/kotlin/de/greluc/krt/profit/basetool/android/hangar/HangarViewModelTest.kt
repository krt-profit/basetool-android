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
import de.greluc.krt.profit.basetool.android.core.data.ShipTypeSummary
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The Hangar's two halves.
 *
 * The rule with teeth: the halves keep separate rows and separate failures. Sharing them would let
 * a switch show the other half's content for a frame, and a failure on one present itself as a
 * failure of the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HangarViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers both halves and records what was asked.
     *
     * @property shipAnswers responses for [myShips], the last repeating.
     * @property typeAnswers responses for [orgOverview], likewise.
     */
    private class RecordingSource(
        private val shipAnswers: MutableList<ApiResult<ShipPage>> = mutableListOf(),
        private val typeAnswers: MutableList<ApiResult<ShipTypePage>> = mutableListOf(),
    ) : HangarSource {
        val shipSearches = mutableListOf<String>()
        val typeSearches = mutableListOf<String>()
        val shipPages = mutableListOf<Int>()

        override suspend fun myShips(
            search: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<ShipPage> {
            shipSearches.add(search)
            shipPages.add(page)
            return if (shipAnswers.size > 1) shipAnswers.removeAt(0) else shipAnswers.first()
        }

        override suspend fun orgOverview(
            search: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<ShipTypePage> {
            typeSearches.add(search)
            return if (typeAnswers.size > 1) typeAnswers.removeAt(0) else typeAnswers.first()
        }

        val created = mutableListOf<ShipDraft>()
        val updated = mutableListOf<Pair<String, Long?>>()
        val deleted = mutableListOf<String>()
        val imported = mutableListOf<Pair<String, Int>>()
        var cleared = false
        var bulkHomeLocation: String? = null
        var importResult: ApiResult<FleetImportResult> =
            ApiResult.Success(FleetImportResult(0, 0, 0, emptyList(), emptyList()))
        var clearResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var saveAnswer: ApiResult<Ship> = ApiResult.Success(SAVED)

        override suspend fun create(draft: ShipDraft): ApiResult<Ship> {
            created.add(draft)
            return saveAnswer
        }

        override suspend fun update(
            id: String,
            version: Long?,
            draft: ShipDraft,
        ): ApiResult<Ship> {
            updated.add(id to version)
            return saveAnswer
        }

        override suspend fun delete(id: String): ApiResult<Unit> {
            deleted.add(id)
            return ApiResult.Success(Unit)
        }

        override suspend fun importFleetview(
            fileName: String,
            bytes: ByteArray,
        ): ApiResult<FleetImportResult> {
            imported.add(fileName to bytes.size)
            return importResult
        }

        override suspend fun clearHangar(): ApiResult<Unit> {
            cleared = true
            return clearResult
        }

        var bulkResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun setHomeLocationForAll(locationId: String): ApiResult<Unit> {
            bulkHomeLocation = locationId
            return bulkResult
        }

        override suspend fun shipTypes(query: String): ApiResult<List<ShipTypeOption>> =
            ApiResult.Success(listOf(ShipTypeOption("t1", "Carrack", "Anvil Aerospace")))

        override suspend fun homeLocations(): ApiResult<List<HomeLocation>> =
            ApiResult.Success(listOf(HomeLocation("l1", "ARC-L1")))
    }

    /**
     * A network that is there unless a case says otherwise.
     *
     * @property state what the flow reports.
     */
    private class FakeConnectivity(
        initial: Boolean = true,
    ) : Connectivity {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> get() = state
    }

    private fun ship(id: String) =
        Ship(
            id = id,
            name = "Meridian",
            typeName = "Carrack",
            manufacturerName = "Anvil Aerospace",
            insurance = "LTI",
            locationName = "ARC-L1",
            fitted = true,
            typeId = "t1",
            locationId = "l1",
            version = VERSION,
        )

    private fun shipPage(
        vararg rows: Ship,
        page: Int = 0,
        totalPages: Int = 1,
    ) = ShipPage(rows.toList(), page = page, totalPages = totalPages, totalElements = rows.size.toLong())

    private fun typePage(vararg rows: ShipTypeSummary) =
        ShipTypePage(rows.toList(), page = 0, totalPages = 1, totalElements = rows.size.toLong())

    private fun summary(name: String) =
        ShipTypeSummary(typeName = name, manufacturerName = null, count = 3, fittedCount = 2)

    private lateinit var source: RecordingSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source =
            RecordingSource(
                mutableListOf(ApiResult.Success(shipPage(ship("s1")))),
                mutableListOf(ApiResult.Success(typePage(summary("Carrack")))),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HangarViewModel(source, FakeConnectivity())

    @Test
    fun `the screen opens on the member's own ships`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(HangarSegment.MINE, model.state.value.segment)
            assertEquals(1, model.state.value.ships.size)
            assertEquals(0, source.typeSearches.size)
        }

    @Test
    fun `switching to the org half reads it from page zero`() =
        runTest(dispatcher) {
            // Keeping whatever was last loaded would show a member an aggregate from ten minutes
            // ago under a header that says it is current.
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()

            model.onSegmentSelected(HangarSegment.ORG)
            advanceUntilIdle()

            assertEquals(HangarSegment.ORG, model.state.value.segment)
            assertEquals(1, model.state.value.types.size)
            assertEquals(HangarPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `tapping the half already showing does nothing`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val before = source.shipPages.size

            model.onSegmentSelected(HangarSegment.MINE)
            advanceUntilIdle()

            assertEquals(before, source.shipPages.size)
        }

    @Test
    fun `the two halves keep separate rows`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            model.onSegmentSelected(HangarSegment.ORG)
            advanceUntilIdle()

            // The ships are still there, untouched, and the aggregate did not overwrite them.
            assertEquals(1, model.state.value.ships.size)
            assertEquals(1, model.state.value.types.size)
        }

    @Test
    fun `the filter is typed immediately and sent once`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val before = source.shipSearches.size

            model.onSearchChanged("Car")
            assertEquals("Car", model.state.value.searchText)

            advanceTimeBy(DEBOUNCE_SETTLE_MS)
            advanceUntilIdle()

            assertEquals(1, source.shipSearches.size - before)
            assertEquals("Car", source.shipSearches.last())
        }

    @Test
    fun `the filter applies to whichever half is showing`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            model.onSegmentSelected(HangarSegment.ORG)
            advanceUntilIdle()
            val shipsBefore = source.shipSearches.size

            model.onSearchChanged("Car")
            advanceTimeBy(DEBOUNCE_SETTLE_MS)
            advanceUntilIdle()

            assertEquals("Car", source.typeSearches.last())
            assertEquals("the other half must not be re-read", shipsBefore, source.shipSearches.size)
        }

    @Test
    fun `a failure is reported, never shown as an empty hangar`() =
        runTest(dispatcher) {
            val failing =
                RecordingSource(
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("offline")))),
                    mutableListOf(ApiResult.Success(typePage())),
                )
            val model = HangarViewModel(failing, FakeConnectivity())

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is HangarPhase.Failed)
        }

    @Test
    fun `the next page is appended`() =
        runTest(dispatcher) {
            val paged =
                RecordingSource(
                    mutableListOf(
                        ApiResult.Success(shipPage(ship("s1"), totalPages = TWO_PAGES)),
                        ApiResult.Success(shipPage(ship("s2"), page = 1, totalPages = TWO_PAGES)),
                    ),
                    mutableListOf(ApiResult.Success(typePage())),
                )
            val model = HangarViewModel(paged, FakeConnectivity())
            model.loadOnce()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("s1", "s2"), model.state.value.ships.map { it.id })
        }

    private companion object {
        /** The version the fixture's ship was read at. */
        const val VERSION = 4L

        /** The most months the server accepts, and one more. */
        const val MAX_MONTHS = "120"
        const val TOO_MANY_MONTHS = "121"

        val SAVED =
            Ship(
                id = "s1",
                name = "Meridian",
                typeName = "Carrack",
                manufacturerName = "Anvil Aerospace",
                insurance = "LTI",
                locationName = "ARC-L1",
                fitted = true,
                typeId = "t1",
                locationId = "l1",
                version = VERSION + 1,
            )

        /** Comfortably past the 300 ms debounce. */
        const val DEBOUNCE_SETTLE_MS = 400L

        /** A two-page result. */
        const val TWO_PAGES = 2
    }

    @Test
    fun `an edit is seeded from the row, hull and place included`() =
        runTest(dispatcher) {
            // A member who only flips "fitted" must not have to pick the hull again.
            val model = viewModel()
            advanceUntilIdle()

            model.onEdit(ship("s1"))
            advanceUntilIdle()

            val editor = model.state.value.editor as ShipEditor.Open
            assertEquals("t1", editor.hull?.id)
            assertEquals("l1", editor.place?.id)
            assertEquals(true, editor.insuranceLti)
        }

    @Test
    fun `a month count outside the server's range cannot be submitted`() =
        runTest(dispatcher) {
            // The server accepts LTI or 0..120 and refuses everything else. Offering a save it will
            // reject teaches the member that the app is unreliable.
            val model = viewModel()
            model.onCreate()
            model.onHullChosen(ShipTypeOption("t1", "Carrack", "Anvil Aerospace"))
            model.onInsuranceLtiChanged(false)

            model.onInsuranceMonthsChanged(TOO_MANY_MONTHS)
            assertEquals(false, (model.state.value.editor as ShipEditor.Open).submittable)

            model.onInsuranceMonthsChanged(MAX_MONTHS)
            assertEquals(true, (model.state.value.editor as ShipEditor.Open).submittable)
        }

    @Test
    fun `a save sends what the editor holds`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onCreate()
            model.onShipNameChanged("  Meridian  ")
            model.onHullChosen(ShipTypeOption("t1", "Carrack", "Anvil Aerospace"))
            model.onFittedChanged(true)
            model.onSave()
            advanceUntilIdle()

            val draft = source.created.single()
            assertEquals("Meridian", draft.name)
            assertEquals("t1", draft.typeId)
            assertEquals("LTI", draft.insurance)
            assertEquals(true, draft.fitted)
        }

    @Test
    fun `an edit echoes the version the row was read at`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onEdit(ship("s1"))
            model.onSave()
            advanceUntilIdle()

            assertEquals("s1" to VERSION, source.updated.single())
        }

    @Test
    fun `a conflict keeps the editor as it was`() =
        runTest(dispatcher) {
            source.saveAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val model = viewModel()
            advanceUntilIdle()

            model.onEdit(ship("s1"))
            model.onShipNameChanged("Meridian II")
            model.onSave()
            advanceUntilIdle()

            val editor = model.state.value.editor as ShipEditor.Open
            assertEquals("Meridian II", editor.name)
            assertTrue(editor.error is ApiError.OptimisticLock)
        }

    @Test
    fun `nothing is written while the device has no network`() =
        runTest(dispatcher) {
            val model = HangarViewModel(source, FakeConnectivity(initial = false))
            advanceUntilIdle()

            model.onEdit(ship("s1"))
            model.onSave()
            model.onDeleteRequested(ship("s1"))
            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(source.updated.isEmpty())
            assertTrue(source.deleted.isEmpty())
        }

    @Test
    fun `a delete asks first`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onDeleteRequested(ship("s1"))
            advanceUntilIdle()
            assertTrue(source.deleted.isEmpty())

            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("s1"), source.deleted)
        }

    /**
     * An emptied hangar and a hangar that was always empty look the same.
     *
     * The count is the only thing that says the write landed, and it has to be taken before the
     * write — afterwards the list is gone (design ch. 08, artboard 6).
     */
    @Test
    fun `emptying the hangar reports how many ships went`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val had = model.state.value.ships.size

            model.onClearRequested()
            model.onClearConfirmed()
            advanceUntilIdle()

            assertEquals(had, model.state.value.cleared)
            model.onClearedAcknowledged()
            assertNull(model.state.value.cleared)
        }

    @Test
    fun `a refused bulk home location keeps the sheet and the place that was picked`() =
        runTest(dispatcher) {
            source.bulkResult = ApiResult.Failure(ApiError.Forbidden())
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            model.onBulkHomeLocationRequested()
            model.onBulkHomeLocationChosen(HomeLocation("l1", "ARC-L1"))

            model.onBulkHomeLocationApplied()
            advanceUntilIdle()

            val sheet = model.state.value.bulkHomeLocation
            assertEquals(ApiError.Forbidden(), sheet?.error)
            assertEquals("ARC-L1", sheet?.place?.name)
            assertNull("nothing was written, so nothing is confirmed", model.state.value.homeLocationSet)
        }

    @Test
    fun `a bulk home location that lands closes the sheet and names the count`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.loadOnce()
            advanceUntilIdle()
            val had = model.state.value.ships.size
            model.onBulkHomeLocationRequested()
            model.onBulkHomeLocationChosen(HomeLocation("l1", "ARC-L1"))

            model.onBulkHomeLocationApplied()
            advanceUntilIdle()

            assertNull(model.state.value.bulkHomeLocation)
            assertEquals(had, model.state.value.homeLocationSet)
        }
}

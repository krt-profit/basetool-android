/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import de.greluc.krt.profit.basetool.android.core.data.PersonalInventorySource
import de.greluc.krt.profit.basetool.android.core.data.PersonalItem
import de.greluc.krt.profit.basetool.android.core.data.PersonalItemDraft
import de.greluc.krt.profit.basetool.android.core.data.PersonalItemPage
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocation
import de.greluc.krt.profit.basetool.android.core.data.PersonalLocationKind
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rules of the app's first write screen.
 *
 * Two of them carry the phase, not just this screen: a save is impossible while the device has no
 * network (never queued — the version would age in the queue), and a conflict leaves what the member
 * typed exactly where it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalInventoryViewModelTest {
    private companion object {
        const val VERSION = 7L
        const val UEX_ID = 4711
        const val QUANTITY = 12

        /** What the repository asks the server for, mirrored so the cap test reads as one. */
        const val LOCATION_CAP = 25
    }

    private val dispatcher = StandardTestDispatcher()

    private class FakeConnectivity(
        initial: Boolean = true,
    ) : Connectivity {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> get() = state
    }

    /**
     * Answers everything and records what it was asked to save.
     *
     * @property saveAnswer what a create or update returns.
     * @property deleteAnswer what a delete returns.
     * @property rows what a page read answers with.
     */
    private class FakeSource(
        var saveAnswer: ApiResult<PersonalItem> = ApiResult.Success(item()),
        var deleteAnswer: ApiResult<Unit> = ApiResult.Success(Unit),
        val rows: List<PersonalItem> = listOf(item()),
    ) : PersonalInventorySource {
        val created = mutableListOf<PersonalItemDraft>()
        val updated = mutableListOf<Triple<String, Long, PersonalItemDraft>>()
        val deleted = mutableListOf<String>()
        val searched = mutableListOf<String>()
        var pageReads = 0
        var locationAnswer: List<PersonalLocation> = emptyList()

        override suspend fun page(
            query: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<PersonalItemPage> {
            pageReads++
            return ApiResult.Success(
                PersonalItemPage(
                    items = rows,
                    page = 0,
                    totalElements = rows.size.toLong(),
                    totalPages = 1,
                ),
            )
        }

        override suspend fun create(draft: PersonalItemDraft): ApiResult<PersonalItem> {
            created.add(draft)
            return saveAnswer
        }

        override suspend fun update(
            id: String,
            version: Long,
            draft: PersonalItemDraft,
        ): ApiResult<PersonalItem> {
            updated.add(Triple(id, version, draft))
            return saveAnswer
        }

        override suspend fun delete(id: String): ApiResult<Unit> {
            deleted.add(id)
            return refusals[id]?.let { ApiResult.Failure(it) } ?: deleteAnswer
        }

        /** Rows the server refuses, keyed by id — a bulk deletion can half-succeed. */
        val refusals = mutableMapOf<String, ApiError>()

        override suspend fun locations(query: String): ApiResult<List<PersonalLocation>> {
            searched.add(query)
            return ApiResult.Success(locationAnswer)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a bulk deletion deletes one at a time and names what was skipped`() =
        runTest(dispatcher) {
            val first = item()
            val second = item().copy(id = "p2", name = "Bexalit")
            val source = FakeSource(rows = listOf(first, second))
            // There is no bulk endpoint, so the loop can half-succeed — which is the whole reason
            // the result is reported rather than assumed.
            source.refusals["p2"] = ApiError.Forbidden()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onSelectAll()
            assertEquals(2, model.state.value.selection.size)
            model.onBulkDeleteRequested()
            assertTrue(model.state.value.confirmingBulkDelete)
            model.onBulkDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("p1", "p2"), source.deleted)
            assertEquals(1, model.state.value.bulkResult?.deleted)
            assertEquals(1, model.state.value.bulkResult?.skipped)
            // The refused row stays selected, so the member can see which one did not go.
            assertEquals(setOf("p2"), model.state.value.selection)
        }

    @Test
    fun `selecting and unselecting starts and ends the mode`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            assertFalse(model.state.value.selecting)
            model.onToggleSelected(item())
            assertTrue(model.state.value.selecting)
            model.onToggleSelected(item())
            assertFalse(model.state.value.selecting)
        }

    @Test
    fun `nothing selected sends no deletion`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onBulkDeleteRequested()
            model.onBulkDeleteConfirmed()
            advanceUntilIdle()

            assertFalse(model.state.value.confirmingBulkDelete)
            assertTrue(source.deleted.isEmpty())
        }

    private fun viewModel(
        source: PersonalInventorySource,
        connectivity: Connectivity = FakeConnectivity(),
    ) = PersonalInventoryViewModel(source, connectivity)

    private fun place() =
        PersonalLocation(
            uexId = UEX_ID,
            kind = PersonalLocationKind.CITY,
            name = "Lorville",
            system = "Stanton",
            parent = "Hurston",
        )

    @Test
    fun `a save is refused while the device has no network`() =
        runTest(dispatcher) {
            // Not queued: the version would age while it waits, and the server would be right to
            // refuse it. The screen says so up front instead (design ch. 14).
            val source = FakeSource()
            val connectivity = FakeConnectivity(initial = false)
            val model = viewModel(source, connectivity)
            model.loadOnce()
            advanceUntilIdle()

            model.onCreate()
            model.onNameChanged("Medpens")
            model.onLocationChosen(place())
            model.onSave()
            advanceUntilIdle()

            assertTrue("nothing may be sent while offline", source.created.isEmpty())
            assertTrue(model.state.value.editor is EditorState.Open)
        }

    @Test
    fun `the offline state follows the device`() =
        runTest(dispatcher) {
            val connectivity = FakeConnectivity(initial = false)
            val model = viewModel(FakeSource(), connectivity)
            advanceUntilIdle()
            assertEquals(false, model.state.value.online)

            connectivity.state.value = true
            advanceUntilIdle()

            assertEquals(true, model.state.value.online)
        }

    @Test
    fun `a conflict keeps what the member typed`() =
        runTest(dispatcher) {
            // The one failure that is nobody's fault. Clearing the form would make the member pay
            // for somebody else's edit.
            val source = FakeSource(saveAnswer = ApiResult.Failure(ApiError.OptimisticLock()))
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onEdit(item())
            model.onNameChanged("Medpens, neu benannt")
            model.onLocationChosen(place())
            model.onSave()
            advanceUntilIdle()

            val editor = model.state.value.editor as EditorState.Open
            assertEquals("Medpens, neu benannt", editor.name)
            assertEquals(place(), editor.location)
            assertTrue("the conflict is reported", editor.error is ApiError.OptimisticLock)
        }

    @Test
    fun `an edit echoes the version the row was read at`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onEdit(item())
            model.onSave()
            advanceUntilIdle()

            assertEquals(VERSION, source.updated.single().second)
        }

    @Test
    fun `a successful save closes the editor and re-reads the list`() =
        runTest(dispatcher) {
            // Re-read rather than patched in place: the server owns the new version, and the row
            // the member sees next has to be the row the next edit will be composed against.
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()
            val readsBefore = source.pageReads

            model.onEdit(item())
            model.onSave()
            advanceUntilIdle()

            assertEquals(EditorState.Closed, model.state.value.editor)
            assertEquals(readsBefore + 1, source.pageReads)
        }

    @Test
    fun `a new entry is created, not updated`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onCreate()
            model.onNameChanged("  Medpens  ")
            model.onQuantityChanged("12")
            model.onLocationChosen(place())
            model.onNoteChanged("  Notfallkiste  ")
            model.onSave()
            advanceUntilIdle()

            val draft = source.created.single()
            assertTrue(source.updated.isEmpty())
            assertEquals("Medpens", draft.name)
            assertEquals(QUANTITY, draft.quantity)
            assertEquals("Notfallkiste", draft.note)
            assertEquals(UEX_ID, draft.locationUexId)
        }

    @Test
    fun `a quantity keeps only digits and never falls below one`() =
        runTest(dispatcher) {
            val model = viewModel(FakeSource())
            model.onCreate()

            model.onQuantityChanged("1a2 b")
            assertEquals("12", (model.state.value.editor as EditorState.Open).quantity)

            model.onQuantityChanged("1")
            model.onQuantityStepped(-1)
            assertEquals("1", (model.state.value.editor as EditorState.Open).quantity)
        }

    @Test
    fun `an editor without a place cannot be submitted`() =
        runTest(dispatcher) {
            val model = viewModel(FakeSource())
            model.onCreate()
            model.onNameChanged("Medpens")

            assertEquals(false, (model.state.value.editor as EditorState.Open).submittable)

            model.onLocationChosen(place())

            assertEquals(true, (model.state.value.editor as EditorState.Open).submittable)
        }

    @Test
    fun `a place search is debounced and needs two characters`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.onCreate()

            model.onLocationQueryChanged("l")
            advanceUntilIdle()
            assertTrue("one character searches nothing", source.searched.isEmpty())

            model.onLocationQueryChanged("lo")
            model.onLocationQueryChanged("lor")
            advanceUntilIdle()

            assertEquals(listOf("lor"), source.searched)
        }

    @Test
    fun `a full answer is reported as capped, because the rest is not gone`() =
        runTest(dispatcher) {
            // ADR-0104: a picker that silently drops the place a member is looking for is worse
            // than one that admits the list was cut.
            val source = FakeSource()
            source.locationAnswer = List(LOCATION_CAP) { place().copy(uexId = it) }
            val model = viewModel(source)
            model.onCreate()

            model.onLocationQueryChanged("lorville")
            advanceUntilIdle()

            assertTrue(model.state.value.locations.capped)
        }

    @Test
    fun `a delete asks first, and only then deletes`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onDeleteRequested(item())
            advanceUntilIdle()
            assertTrue("nothing is deleted before the confirmation", source.deleted.isEmpty())
            assertEquals(item(), model.state.value.pendingDelete)

            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("p1"), source.deleted)
            assertNull(model.state.value.pendingDelete)
        }

    @Test
    fun `a delete is refused while the device has no network`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source, FakeConnectivity(initial = false))
            model.loadOnce()
            advanceUntilIdle()

            model.onDeleteRequested(item())
            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(source.deleted.isEmpty())
        }

    @Test
    fun `a failed delete is reported once`() =
        runTest(dispatcher) {
            val source = FakeSource(deleteAnswer = ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onDeleteRequested(item())
            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(model.state.value.lastFailure is ApiError.Forbidden)

            model.onFailureShown()

            assertNull(model.state.value.lastFailure)
        }
}

/**
 * One row, the same in every case.
 *
 * @return the fixture.
 */
private fun item() =
    PersonalItem(
        id = "p1",
        name = "Medpens",
        note = "Notfallkiste",
        quantity = 12,
        locationUexId = 4711,
        locationKind = PersonalLocationKind.CITY,
        locationName = "Lorville",
        version = 7L,
    )

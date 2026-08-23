/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.CraftabilityMaterial
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprintPage
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Blueprints tab's rules.
 *
 * The one that matters most on a bad day: craftability is a **second** read, and its failure must
 * not take the list with it. A member who cannot see what is buildable can still see what they own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalBlueprintsViewModelTest {
    private companion object {
        const val VERSION = 3L
    }

    private val dispatcher = StandardTestDispatcher()

    private class FakeConnectivity(
        initial: Boolean = true,
    ) : Connectivity {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> get() = state
    }

    /**
     * Answers everything and records the writes.
     *
     * @property craftabilityAnswer what the second read returns.
     */
    private class FakeSource(
        var craftabilityAnswer: ApiResult<Map<String, Craftability>> =
            ApiResult.Success(mapOf("b1" to craftability())),
    ) : PersonalBlueprintSource {
        val added = mutableListOf<Pair<String, String?>>()
        val notes = mutableListOf<Triple<String, Long, String?>>()
        val removed = mutableListOf<String>()
        val searched = mutableListOf<String>()
        var products: List<BlueprintProduct> = emptyList()
        var saveAnswer: ApiResult<OwnedBlueprint> = ApiResult.Success(entry())

        override suspend fun page(
            query: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<OwnedBlueprintPage> =
            ApiResult.Success(
                OwnedBlueprintPage(listOf(entry()), page = 0, totalElements = 1, totalPages = 1),
            )

        override suspend fun craftability(): ApiResult<Map<String, Craftability>> = craftabilityAnswer

        override suspend fun add(
            productKey: String,
            note: String?,
        ): ApiResult<OwnedBlueprint> {
            added.add(productKey to note)
            return saveAnswer
        }

        override suspend fun updateNote(
            id: String,
            version: Long,
            note: String?,
        ): ApiResult<OwnedBlueprint> {
            notes.add(Triple(id, version, note))
            return saveAnswer
        }

        override suspend fun remove(id: String): ApiResult<Unit> {
            removed.add(id)
            return ApiResult.Success(Unit)
        }

        override suspend fun products(query: String): ApiResult<List<BlueprintProduct>> {
            searched.add(query)
            return ApiResult.Success(products)
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

    private fun viewModel(
        source: PersonalBlueprintSource,
        connectivity: Connectivity = FakeConnectivity(),
    ) = PersonalBlueprintsViewModel(source, connectivity)

    @Test
    fun `the list and its craftability arrive together`() =
        runTest(dispatcher) {
            val model = viewModel(FakeSource())

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, model.state.value.items.size)
            assertEquals(setOf("b1"), model.state.value.craftability.keys)
        }

    @Test
    fun `a failed craftability read leaves the list standing`() =
        runTest(dispatcher) {
            // The rows are still true. A chip that said "nicht baubar" because a request did not
            // come back would be a claim about the member's stock made out of an outage.
            val source = FakeSource(craftabilityAnswer = ApiResult.Failure(ApiError.Forbidden()))
            val model = viewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, model.state.value.items.size)
            assertTrue(model.state.value.phase is BlueprintsPhase.Ready)
            assertTrue("no chip is better than a guessed one", model.state.value.craftability.isEmpty())
        }

    @Test
    fun `the refining switch changes the answer without a second read`() =
        runTest(dispatcher) {
            // Both counts come from the same call, which is why it asks for them together.
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRefineryChanged(true)
            advanceUntilIdle()

            assertTrue(model.state.value.withRefinery)
            assertEquals(setOf("b1"), model.state.value.craftability.keys)
        }

    @Test
    fun `a product the member already owns cannot be submitted`() =
        runTest(dispatcher) {
            // The server would refuse the create, and a picker that offers it sets up a failure.
            val model = viewModel(FakeSource())
            model.onAdd()

            model.onProductChosen(BlueprintProduct("anvil.hornet", "F7A Hornet", "Anvil", owned = true))

            assertEquals(false, (model.state.value.editor as BlueprintEditor.Adding).submittable)
        }

    @Test
    fun `adding sends the catalogue key and the note`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onAdd()
            model.onProductChosen(BlueprintProduct("anvil.hornet", "F7A Hornet", "Anvil", owned = false))
            model.onNoteChanged("  vom Event  ")
            model.onSave()
            advanceUntilIdle()

            assertEquals(listOf("anvil.hornet" to "vom Event"), source.added)
            assertEquals(BlueprintEditor.Closed, model.state.value.editor)
        }

    @Test
    fun `a note change echoes the version`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onEdit(entry())
            model.onNoteChanged("neu")
            model.onSave()
            advanceUntilIdle()

            assertEquals(Triple("b1", VERSION, "neu"), source.notes.single())
        }

    @Test
    fun `a conflict keeps the note the member typed`() =
        runTest(dispatcher) {
            val source = FakeSource()
            source.saveAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onEdit(entry())
            model.onNoteChanged("neu")
            model.onSave()
            advanceUntilIdle()

            val editor = model.state.value.editor as BlueprintEditor.Editing
            assertEquals("neu", editor.note)
            assertTrue(editor.error is ApiError.OptimisticLock)
        }

    @Test
    fun `nothing is written while the device has no network`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source, FakeConnectivity(initial = false))
            model.loadOnce()
            advanceUntilIdle()

            model.onEdit(entry())
            model.onNoteChanged("neu")
            model.onSave()
            model.onDeleteRequested(entry())
            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(source.notes.isEmpty())
            assertTrue(source.removed.isEmpty())
        }

    @Test
    fun `a catalogue search is debounced and needs two characters`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.onAdd()

            model.onProductQueryChanged("h")
            advanceUntilIdle()
            assertTrue(source.searched.isEmpty())

            model.onProductQueryChanged("ho")
            model.onProductQueryChanged("hor")
            advanceUntilIdle()

            assertEquals(listOf("hor"), source.searched)
        }

    @Test
    fun `removing asks first`() =
        runTest(dispatcher) {
            val source = FakeSource()
            val model = viewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onDeleteRequested(entry())
            advanceUntilIdle()
            assertTrue(source.removed.isEmpty())

            model.onDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("b1"), source.removed)
        }
}

/**
 * One owned blueprint, the same in every case.
 *
 * @return the fixture.
 */
private fun entry() =
    OwnedBlueprint(
        id = "b1",
        productKey = "anvil.hornet",
        productName = "F7A Hornet",
        note = "vom Event",
        acquiredAt = "2026-07-01",
        removable = true,
        version = 3L,
    )

/**
 * One craftability entry: short of one material now, fine once refining counts.
 *
 * @return the fixture.
 */
private fun craftability() =
    Craftability(
        blueprintId = "b1",
        recipeResolved = true,
        craftable = 0,
        craftableWithRefinery = 2,
        limitingMaterial = "Quantainium",
        limitingMaterialWithRefinery = null,
        materials =
            listOf(
                CraftabilityMaterial(
                    name = "Quantainium",
                    requiredScu = 10.0,
                    availableScu = 4.0,
                    missingScu = 6.0,
                    missingScuWithRefinery = 0.0,
                ),
            ),
    )

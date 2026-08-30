/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import de.greluc.krt.profit.basetool.android.core.data.BlueprintBatchResult
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOverviewEntry
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOverviewPage
import de.greluc.krt.profit.basetool.android.core.data.BlueprintOwner
import de.greluc.krt.profit.basetool.android.core.data.BlueprintProduct
import de.greluc.krt.profit.basetool.android.core.data.BlueprintRecipe
import de.greluc.krt.profit.basetool.android.core.data.Craftability
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprint
import de.greluc.krt.profit.basetool.android.core.data.OwnedBlueprintPage
import de.greluc.krt.profit.basetool.android.core.data.PersonalBlueprintSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * „Blueprint-Verfügbarkeit" (design ch. 17 artboard 6, `REQ-APP-PI-014`).
 *
 * Three rules are worth pinning: owners load per row and one row's failure stays that row's, the
 * „Nicht erfasst" filter is a client-side narrowing that says so while more pages exist, and a row
 * is asked for its owners only once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlueprintOverviewTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers the two overview reads and counts them.
     *
     * @property page what [overview] returns.
     * @property ownerAnswers what [owners] returns, keyed by product key.
     */
    private class RecordingSource(
        private val page: BlueprintOverviewPage,
        private val ownerAnswers: Map<String, ApiResult<List<BlueprintOwner>>> = emptyMap(),
    ) : PersonalBlueprintSource {
        val ownerCalls = mutableListOf<String>()

        override suspend fun page(
            query: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<OwnedBlueprintPage> = error("the overview reads no personal list")

        override suspend fun craftability(): ApiResult<Map<String, Craftability>> =
            error("no craftability here")

        override suspend fun add(
            productKey: String,
            note: String?,
        ): ApiResult<OwnedBlueprint> = error("the overview writes nothing")

        override suspend fun updateNote(
            id: String,
            version: Long,
            note: String?,
        ): ApiResult<OwnedBlueprint> = error("the overview writes nothing")

        override suspend fun remove(id: String): ApiResult<Unit> = error("the overview writes nothing")

        override suspend fun products(query: String): ApiResult<List<BlueprintProduct>> =
            error("the overview searches no catalogue")

        override suspend fun removeAll(): ApiResult<Int> = error("not part of this test")

        override suspend fun recipe(id: String): ApiResult<BlueprintRecipe> = error("no recipes here")

        override suspend fun addAll(productKeys: List<String>): ApiResult<BlueprintBatchResult> =
            error("the overview writes nothing")

        override suspend fun overview(
            query: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<BlueprintOverviewPage> = ApiResult.Success(this.page)

        override suspend fun owners(productKey: String): ApiResult<List<BlueprintOwner>> {
            ownerCalls.add(productKey)
            return ownerAnswers[productKey] ?: ApiResult.Success(emptyList())
        }
    }

    /** Puts `viewModelScope` on the test dispatcher. */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /** Puts it back. */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** One row's failed owner read stays that row's; the list is unaffected. */
    @Test
    fun oneRowsFailureStaysThatRows() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    page = page(entry("a", 2), entry("b", 0)),
                    ownerAnswers =
                        mapOf(
                            "a" to ApiResult.Success(listOf(BlueprintOwner("Rhea", true))),
                            "b" to ApiResult.Failure(ApiError.Server(status = SERVER_ERROR)),
                        ),
                )
            val model = BlueprintOverviewViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRowShown(entry("a", 2))
            model.onRowShown(entry("b", 0))
            advanceUntilIdle()

            assertTrue(model.state.value.phase is OverviewPhase.Ready)
            assertEquals(2, model.state.value.entries.size)
            assertTrue(model.state.value.owners["a"] is OwnersState.Ready)
            assertTrue(model.state.value.owners["b"] is OwnersState.Failed)
        }

    /** A row is asked for its owners once, however often it scrolls back into view. */
    @Test
    fun ownersAreReadOncePerRow() =
        runTest(dispatcher) {
            val source = RecordingSource(page = page(entry("a", 1)))
            val model = BlueprintOverviewViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            model.onRowShown(entry("a", 1))
            advanceUntilIdle()
            model.onRowShown(entry("a", 1))
            advanceUntilIdle()

            assertEquals(listOf("a"), source.ownerCalls)
        }

    /** „Nicht erfasst" narrows what is loaded, and says so while the server has more. */
    @Test
    fun theUnrecordedFilterSaysWhatItCanSee() =
        runTest(dispatcher) {
            val source =
                RecordingSource(page = page(entry("a", 2), entry("b", 0), more = true))
            val model = BlueprintOverviewViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            assertFalse(model.state.value.filterIsPartial)
            model.onFilterChanged(OverviewFilter.UNRECORDED)

            assertEquals(listOf("b"), model.state.value.visible.map { it.productKey })
            // The endpoint has no such filter, so the screen has to admit what it narrowed.
            assertTrue(model.state.value.filterIsPartial)
        }

    /**
     * One row.
     *
     * @param key its product key.
     * @param owners how many hold it.
     * @return the row.
     */
    private fun entry(
        key: String,
        owners: Long,
    ) = BlueprintOverviewEntry(productKey = key, productName = key.uppercase(), ownerCount = owners)

    /**
     * One page of rows.
     *
     * @param entries the rows.
     * @param more whether the server has another page.
     * @return the page.
     */
    private fun page(
        vararg entries: BlueprintOverviewEntry,
        more: Boolean = false,
    ) = BlueprintOverviewPage(
        entries = entries.toList(),
        page = 0,
        totalPages = if (more) 2 else 1,
        totalElements = entries.size.toLong(),
    )

    private companion object {
        /** A 500 is one of the two answers the per-row read has to survive. */
        const val SERVER_ERROR = 500
    }
}

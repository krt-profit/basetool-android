/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import de.greluc.krt.profit.basetool.android.core.data.AllocationKind
import de.greluc.krt.profit.basetool.android.core.data.AllocationTarget
import de.greluc.krt.profit.basetool.android.core.data.BookInDraft
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MaterialOption
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
 * Booking in as „Persönlich" (design ch. 19, artboard 3): the row is personal and carries no earmark,
 * even when the member had typed some before ticking the box.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalBookInTest {
    private val dispatcher = StandardTestDispatcher()

    private class Recording : StubInventory() {
        val drafts = mutableListOf<BookInDraft>()

        override suspend fun bookIn(draft: BookInDraft): ApiResult<Unit> {
            drafts.add(draft)
            return ApiResult.Success(Unit)
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
    fun `a personal book-in is sent personal and without the earmarks typed before`() =
        runTest(dispatcher) {
            val source = Recording()
            val model = BookingViewModel(source, LagerOnline)
            var personalSaved = 0
            model.openBookIn(onPersonalSaved = { personalSaved++ }) {}
            advanceUntilIdle()
            model.onMaterialChosen(MaterialOption("m1", "Quantainium", "SCU"))
            model.onPlaceChosen(LocationOption("l1", "ARC-L1"))
            model.onAmountChanged("80")
            model.onQualityChanged("874")
            model.splits.add(AllocationKind.JOB_ORDER, AllocationTarget("o1", "#1042"))
            model.splits.amount(AllocationKind.JOB_ORDER, "o1", "900")
            assertFalse("an overbooked split blocks a shared book-in", model.state.value!!.submittable)

            model.splits.personal(true)
            assertTrue("a personal book-in ignores the split", model.state.value!!.submittable)
            model.onSave()
            advanceUntilIdle()

            val draft = source.drafts.single()
            assertTrue(draft.personal)
            assertTrue(draft.jobOrderAllocations.isEmpty())
            assertTrue(draft.missionAllocations.isEmpty())
            assertEquals(1, personalSaved)
        }

    @Test
    fun `a shared book-in does not report itself as personal`() =
        runTest(dispatcher) {
            val source = Recording()
            val model = BookingViewModel(source, LagerOnline)
            var personalSaved = 0
            model.openBookIn(onPersonalSaved = { personalSaved++ }) {}
            advanceUntilIdle()
            model.onMaterialChosen(MaterialOption("m1", "Quantainium", "SCU"))
            model.onPlaceChosen(LocationOption("l1", "ARC-L1"))
            model.onAmountChanged("80")
            model.onQualityChanged("874")
            model.onSave()
            advanceUntilIdle()

            assertFalse(source.drafts.single().personal)
            assertEquals(0, personalSaved)
        }
}

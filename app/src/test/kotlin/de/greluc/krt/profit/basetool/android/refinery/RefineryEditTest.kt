/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.refinery

import de.greluc.krt.profit.basetool.android.core.data.RefineryCreateSource
import de.greluc.krt.profit.basetool.android.core.data.RefineryGoodDraft
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrder
import de.greluc.krt.profit.basetool.android.core.data.RefineryOrderDraft
import de.greluc.krt.profit.basetool.android.core.data.RefineryServerStatus
import de.greluc.krt.profit.basetool.android.core.data.RefiningMethod
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
 * „Raffinerieauftrag bearbeiten" — design ch. 11 artboard 6 (`REQ-APP-REF-011`).
 *
 * The edit is the create form pre-filled, so what is worth pinning is the part that is *not* the
 * create: the read that fills it, the `version` echo, and the lock a booked run puts on its core.
 *
 * Robolectric because the failure paths log, and an unmocked `android.util.Log` throws inside
 * `viewModelScope` where nothing reports it — the write then looks as though it had succeeded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefineryEditTest {
    private val dispatcher = StandardTestDispatcher()

    private companion object {
        /** The lock the edited run arrives with, and has to leave with. */
        const val VERSION = 4L
    }

    /**
     * Answers the three reads and records the two writes.
     *
     * @property existing what [orderDraft] returns.
     */
    private class RecordingSource(
        private val existing: ApiResult<RefineryOrderDraft>? = null,
    ) : RefineryCreateSource {
        val updated = mutableListOf<Pair<String, RefineryOrderDraft>>()
        val created = mutableListOf<RefineryOrderDraft>()

        override suspend fun refineries(): ApiResult<List<Pair<String, String>>> =
            ApiResult.Success(listOf("loc1" to "ARC-L1"))

        override suspend fun methods(): ApiResult<List<RefiningMethod>> =
            ApiResult.Success(emptyList())

        override suspend fun searchMaterials(query: String): ApiResult<List<Pair<String, String>>> =
            ApiResult.Success(emptyList())

        override suspend fun createOrder(draft: RefineryOrderDraft): ApiResult<String> {
            created.add(draft)
            return ApiResult.Success("r9")
        }

        override suspend fun orderDraft(orderId: String): ApiResult<RefineryOrderDraft> =
            existing ?: error("this test does not read an order")

        override suspend fun updateOrder(
            orderId: String,
            draft: RefineryOrderDraft,
        ): ApiResult<Unit> {
            updated.add(orderId to draft)
            return ApiResult.Success(Unit)
        }

        override suspend fun deleteOrder(orderId: String): ApiResult<Unit> =
            error("the form deletes nothing")
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

    /** Editing reads the run, fills the form, and sends the version back with it. */
    @Test
    fun editPrefillsAndEchoesTheVersion() =
        runTest(dispatcher) {
            val source = RecordingSource(ApiResult.Success(loaded()))
            val model = RefineryCreateViewModel(source, "r1")
            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.editing)
            assertEquals("ARC-L1", model.state.value.draft.locationName)
            assertEquals(VERSION, model.state.value.draft.version)
            assertFalse("an in-progress run is fully editable", model.state.value.coreLocked)

            model.onCreate()
            advanceUntilIdle()

            assertEquals(1, source.updated.size)
            assertEquals("r1", source.updated.first().first)
            assertEquals(VERSION, source.updated.first().second.version)
            assertTrue("editing never posts a second order", source.created.isEmpty())
        }

    /** A booked run's core is locked — the app's rule, because no server gate enforces it. */
    @Test
    fun aStoredRunLocksItsCore() =
        runTest(dispatcher) {
            val source = RecordingSource(ApiResult.Success(loaded().copy(stored = true)))
            val model = RefineryCreateViewModel(source, "r1")
            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.coreLocked)
        }

    /** Raising an order still posts, and locks nothing. */
    @Test
    fun theCreateIsUnchanged() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val model = RefineryCreateViewModel(source)
            model.loadOnce()
            advanceUntilIdle()

            assertFalse(model.state.value.editing)
            assertFalse(model.state.value.coreLocked)
        }

    /** A booked run may not be deleted, and the confirmation is not even raised for one. */
    @Test
    fun aStoredRunIsNotDeletable() {
        val open = detailState(RefineryServerStatus.IN_PROGRESS)
        val booked = detailState(RefineryServerStatus.COMPLETED)

        assertTrue(open.deletable)
        assertFalse(booked.deletable)
    }

    /**
     * One run as the server would send it back into the form.
     *
     * @return the pre-filled form.
     */
    private fun loaded(): RefineryOrderDraft =
        RefineryOrderDraft(
            locationId = "loc1",
            locationName = "ARC-L1",
            methodId = "met1",
            methodName = "Dinyx Solventation",
            goods =
                listOf(
                    RefineryGoodDraft(
                        inputMaterialId = "m1",
                        inputMaterialName = "Quantainium (Raw)",
                        inputQuantity = "620",
                        outputQuantity = "442",
                    ),
                ),
            version = VERSION,
        )

    /**
     * A detail state carrying one run in the given state.
     *
     * @param status where the run stands.
     * @return the state.
     */
    private fun detailState(status: RefineryServerStatus): RefineryDetailState =
        RefineryDetailState(
            orderId = "r1",
            order =
                RefineryOrder(
                    id = "r1",
                    locationId = "loc1",
                    locationName = "ARC-L1",
                    methodName = "Dinyx Solventation",
                    startedAt = null,
                    endsAt = null,
                    status = status,
                    yields = emptyList(),
                    oreSales = null,
                    profit = null,
                    version = VERSION,
                ),
        )
}

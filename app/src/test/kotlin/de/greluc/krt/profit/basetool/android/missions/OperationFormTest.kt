/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.data.OperationDetail
import de.greluc.krt.profit.basetool.android.core.data.OperationDraft
import de.greluc.krt.profit.basetool.android.core.data.OperationOverview
import de.greluc.krt.profit.basetool.android.core.data.OperationPage
import de.greluc.krt.profit.basetool.android.core.data.OperationPayouts
import de.greluc.krt.profit.basetool.android.core.data.OperationQuery
import de.greluc.krt.profit.basetool.android.core.data.OperationRollup
import de.greluc.krt.profit.basetool.android.core.data.OperationSource
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Operation form's rules (REQ-APP-OPS-014).
 *
 * Four are worth a test each: a blank name cannot be sent, an empty description is sent as absent
 * rather than as an empty line, the edit reads the Operation first and echoes its **version**
 * (without which a concurrent edit would be a silent overwrite instead of a 409), and a refusal
 * keeps what was typed instead of clearing the form.
 *
 * Robolectric, not plain JUnit: the failure path logs, and an unmocked `android.util.Log` throws
 * inside `viewModelScope` where nothing reports it — the write then looks as if it had simply not
 * failed. That cost one debugging round here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationFormTest {
    private val dispatcher = StandardTestDispatcher()

    private companion object {
        /** The optimistic lock the edited Operation arrives with, and must leave with. */
        const val VERSION = 7L
    }

    /**
     * Records the writes and answers the one read.
     *
     * @property overviewAnswer what [overview] returns, or `null` where the test never reads.
     */
    private class RecordingSource(
        private val overviewAnswer: ApiResult<OperationOverview>? = null,
    ) : OperationSource {
        val created = mutableListOf<OperationDraft>()
        val updated = mutableListOf<Pair<String, OperationDraft>>()
        var createAnswer: ApiResult<String> = ApiResult.Success("op-9")
        var updateAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun search(
            query: OperationQuery,
            page: Int,
            pageSize: Int,
        ): ApiResult<OperationPage> = error("the form never searches")

        override suspend fun overview(id: String): ApiResult<OperationOverview> =
            overviewAnswer ?: error("this test does not read")

        override suspend fun setPaidOut(
            operationId: String,
            participantKey: String,
            paidOut: Boolean,
        ): ApiResult<Unit> = error("the form confirms nothing")

        override suspend fun create(draft: OperationDraft): ApiResult<String> {
            created.add(draft)
            return createAnswer
        }

        override suspend fun update(
            operationId: String,
            draft: OperationDraft,
        ): ApiResult<Unit> {
            updated.add(operationId to draft)
            return updateAnswer
        }
    }

    /** Puts the view models' `viewModelScope` on the test dispatcher. */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /** Puts it back. */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A form with no name is not sendable, and pressing the CTA anyway writes nothing. */
    @Test
    fun blankNameCannotBeSent() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val model = OperationFormViewModel(source)

            assertFalse(model.state.value.submittable)
            model.onSubmit()
            advanceUntilIdle()
            assertTrue(source.created.isEmpty())

            model.onName("  ")
            assertFalse("whitespace is not a name", model.state.value.submittable)

            model.onName("Operation Rotschild")
            assertTrue(model.state.value.submittable)
        }

    /** The create trims, drops an empty description, and reports the id the server assigned. */
    @Test
    fun createSendsTheTrimmedDraft() =
        runTest(dispatcher) {
            val source = RecordingSource()
            val model = OperationFormViewModel(source)

            model.onName("  Operation Rotschild  ")
            model.onDescription("   ")
            model.onStatus(OperationStatus.ACTIVE)
            model.onSubmit()
            advanceUntilIdle()

            assertEquals(1, source.created.size)
            assertEquals("Operation Rotschild", source.created.first().name)
            // Absent rather than an empty string: the server stores what it is sent, and a blank
            // line reads as "somebody deliberately wrote nothing here".
            assertNull(source.created.first().description)
            assertEquals(OperationStatus.ACTIVE, source.created.first().status)
            assertEquals("op-9", model.state.value.saved)
        }

    /** Editing reads the Operation first and echoes the version it came with. */
    @Test
    fun editPrefillsAndEchoesTheVersion() =
        runTest(dispatcher) {
            val source = RecordingSource(ApiResult.Success(overview(version = VERSION)))
            val model = OperationFormViewModel(source, "op-1")

            assertTrue("the form waits for the read", model.state.value.loading)
            advanceUntilIdle()

            assertEquals("Operation Rotschild", model.state.value.name)
            assertEquals(OperationStatus.PLANNED, model.state.value.status)
            assertEquals(VERSION, model.state.value.version)

            model.onName("Operation Rotschild II")
            model.onSubmit()
            advanceUntilIdle()

            assertEquals(1, source.updated.size)
            assertEquals("op-1", source.updated.first().first)
            assertEquals(VERSION, source.updated.first().second.version)
            assertEquals("op-1", model.state.value.saved)
        }

    /** A refused write leaves the form exactly as it was, so nothing has to be typed again. */
    @Test
    fun refusalKeepsWhatWasTyped() =
        runTest(dispatcher) {
            val source = RecordingSource()
            source.createAnswer = ApiResult.Failure(ApiError.Forbidden())
            val model = OperationFormViewModel(source)

            model.onName("Operation Rotschild")
            model.onSubmit()
            advanceUntilIdle()

            assertEquals(ApiError.Forbidden(), model.state.value.error)
            assertEquals("Operation Rotschild", model.state.value.name)
            assertNull("a refused write did not happen", model.state.value.saved)
            assertFalse(model.state.value.saving)
        }

    /**
     * One Operation as the server sends it.
     *
     * @param version its optimistic lock.
     * @return the overview.
     */
    private fun overview(version: Long?): OperationOverview =
        OperationOverview(
            detail =
                OperationDetail(
                    id = "op-1",
                    name = "Operation Rotschild",
                    status = OperationStatus.PLANNED,
                    rawStatus = "PLANNED",
                    description = null,
                    payoutPreliminary = null,
                    version = version,
                ),
            rollup = OperationRollup(total = null, truncated = false, missions = emptyList()),
            payouts = OperationPayouts(totalDonations = null, rows = emptyList()),
        )
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverDto
import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandoverSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderMaterial
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recording that material changed hands — the write that finishes an Auftrag.
 *
 * The properties worth a class: the live projection is what the member decides on, and a stock row
 * is mandatory on the wire, so the form must not be submittable without one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderHandoverTest {
    private companion object {
        /** The line asks for this much. */
        const val NEEDED = "400"

        /** And this much has already changed hands. */
        const val DONE = "180"

        /** 180 + 12,5, so the comma-as-separator assertion has a name for its expectation. */
        const val DONE_PLUS_TWELVE_AND_A_HALF = 192.5

        /** 180 + 120, the artboard’s own example. */
        const val DONE_PLUS_ONE_TWENTY = 300.0

        /** 300 of 400. */
        const val THREE_QUARTERS = 0.75
    }

    private val dispatcher = StandardTestDispatcher()

    /** Every write, as the amount it carried. */
    private val writes = mutableListOf<Triple<String, String, String>>()

    private var draft: OrderHandoverDraft? = null
    private var reloads = 0
    private var stock: ApiResult<List<HandoverStockRow>> =
        ApiResult.Success(
            listOf(
                HandoverStockRow(id = "s1", owner = "Rhea", location = "ARC-L1", quality = 874, amount = "442"),
                HandoverStockRow(id = "s2", owner = "Dorn", location = "Port Olisar", quality = 810, amount = "90"),
            ),
        )
    private var writeAnswer: ApiResult<JobOrderHandoverDto> = ApiResult.Success(JobOrderHandoverDto())

    private fun material() =
        JobOrderMaterial(
            materialId = "m1",
            name = "Laranite",
            needed = NEEDED,
            inStock = "442",
            claimCount = 1,
            open = "220",
        )

    private fun handover(scope: kotlinx.coroutines.CoroutineScope) =
        OrderHandover(
            source = RecordingSource(),
            scope = scope,
            read = { draft },
            write = { draft = it },
            onRecorded = { reloads += 1 },
        )

    /**
     * The number that finishes an Auftrag is computed, never formed in somebody's head.
     *
     * 180 already + 120 typed against a need of 400 is 300, which is 75 % — the artboard's own
     * example, and the reason the preview exists.
     */
    @Test
    fun `the projection adds what is typed to what has already changed hands`() =
        runTest(dispatcher) {
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()

            subject.change { it.copy(amount = "120") }

            assertEquals(DONE_PLUS_ONE_TWENTY, draft?.projectedAmount)
            assertEquals(THREE_QUARTERS, draft?.projected)
            assertFalse("300 of 400 does not finish the line", draft?.completes == true)
        }

    /** And at the need it says so, which is what closes the Auftrag. */
    @Test
    fun `reaching the needed amount reads as fulfilled`() =
        runTest(dispatcher) {
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()

            subject.change { it.copy(amount = "220") }

            assertTrue(draft?.completes == true)
        }

    /** A German keyboard sends a comma, and a member who types what their locale shows is not wrong. */
    @Test
    fun `a comma is a decimal point`() =
        runTest(dispatcher) {
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()

            subject.change { it.copy(amount = "12,5") }

            assertEquals(DONE_PLUS_TWELVE_AND_A_HALF, draft?.projectedAmount)
        }

    /**
     * A stock row is `@NotNull` on the wire, so the form must not offer to send without one. With
     * two candidates nothing is preselected — picking for the member would book out a row they did
     * not choose.
     */
    @Test
    fun `two candidates leave the choice open and the form unsubmittable`() =
        runTest(dispatcher) {
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()

            subject.change { it.copy(amount = "10", recipient = "Vex") }

            assertNull(draft?.stockId)
            assertFalse(draft?.submittable == true)
        }

    /** One candidate is not a choice: preselecting it turns the common case into a single tap. */
    @Test
    fun `a single candidate is preselected`() =
        runTest(dispatcher) {
            stock =
                ApiResult.Success(
                    listOf(
                        HandoverStockRow(id = "only", owner = "Rhea", location = null, quality = null, amount = "50"),
                    ),
                )
            val subject = handover(this)

            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()

            assertEquals("only", draft?.stockId)
        }

    /** A blank recipient is refused before the server has to say so — the handle is `@NotBlank`. */
    @Test
    fun `a blank recipient is not submittable`() =
        runTest(dispatcher) {
            stock =
                ApiResult.Success(
                    listOf(HandoverStockRow(id = "only", owner = null, location = null, quality = null, amount = "50")),
                )
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()

            subject.change { it.copy(amount = "10", recipient = "   ") }

            assertFalse(draft?.submittable == true)
        }

    /** A successful write closes the sheet and makes the caller re-read the Auftrag. */
    @Test
    fun `a recorded handover closes the sheet and reloads`() =
        runTest(dispatcher) {
            stock =
                ApiResult.Success(
                    listOf(HandoverStockRow(id = "only", owner = null, location = null, quality = null, amount = "50")),
                )
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()
            subject.change { it.copy(amount = "10", recipient = "Vex") }

            subject.submit("o1")
            advanceUntilIdle()

            assertEquals(listOf(Triple("only", "10", "Vex")), writes)
            assertNull("the sheet closes on success", draft)
            assertEquals(1, reloads)
        }

    /** A refusal keeps the sheet and everything in it: re-typing to retry is a charge for a reply. */
    @Test
    fun `a refused handover keeps the sheet filled`() =
        runTest(dispatcher) {
            stock =
                ApiResult.Success(
                    listOf(HandoverStockRow(id = "only", owner = null, location = null, quality = null, amount = "50")),
                )
            writeAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val subject = handover(this)
            subject.open("o1", material(), alreadyDone = DONE)
            advanceUntilIdle()
            subject.change { it.copy(amount = "10", recipient = "Vex") }

            subject.submit("o1")
            advanceUntilIdle()

            assertNotNull(draft)
            assertEquals("10", draft?.amount)
            assertTrue(draft?.error is ApiError.OptimisticLock)
            assertEquals(0, reloads)
        }

    /** A line the server sent without a material id cannot be addressed, so the sheet stays shut. */
    @Test
    fun `a material without an id does not open the sheet`() =
        runTest(dispatcher) {
            val subject = handover(this)

            subject.open("o1", material().copy(materialId = null), alreadyDone = DONE)
            advanceUntilIdle()

            assertNull(draft)
        }

    /** Records what was asked for and what was written. */
    private inner class RecordingSource : JobOrderHandoverSource {
        override suspend fun stockFor(
            orderId: String,
            materialId: String,
        ): ApiResult<List<HandoverStockRow>> = stock

        override suspend fun record(
            orderId: String,
            inventoryItemId: String,
            amount: String,
            recipientHandle: String,
            recipientSquadron: String?,
            handoverTime: String,
        ): ApiResult<JobOrderHandoverDto> {
            writes.add(Triple(inventoryItemId, amount, recipientHandle))
            return writeAnswer
        }
    }
}

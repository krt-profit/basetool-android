/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderHandoverDto
import de.greluc.krt.profit.basetool.android.core.contract.model.JobOrderItemHandoverDto
import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.data.JobOrderHandoverSource
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
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
 * Handing finished items over — the write that closes an item Auftrag.
 *
 * The property that carries the class: the ceiling is **manufactured minus delivered**, never
 * ordered minus delivered. A unit nobody has built cannot be handed over, and the server answers
 * 400 for the attempt — so a form that offered the obvious subtraction would spend the member's
 * entry on a refusal they could not explain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderItemHandoverTest {
    private companion object {
        /** The line asks for six. */
        const val ORDERED = 6

        /** Four have been built. */
        const val BUILT = 4

        /** One has already changed hands, so three may still go. */
        const val DELIVERED = 1

        /** Which is this many. */
        const val DELIVERABLE = 3
    }

    private val dispatcher = StandardTestDispatcher()

    private var draft: ItemHandoverDraft? = null
    private var reloads = 0
    private val writes = mutableListOf<Triple<String, Int, String>>()
    private var answer: ApiResult<JobOrderItemHandoverDto> = ApiResult.Success(JobOrderItemHandoverDto())

    private fun line(
        built: Int = BUILT,
        delivered: Int = DELIVERED,
    ) = JobOrderItem(
        id = "i1",
        name = "Ballistic Gatling",
        blueprintName = null,
        amount = ORDERED,
        manufactured = built,
        delivered = delivered,
        blueprintStale = false,
    )

    private fun holder(scope: kotlinx.coroutines.CoroutineScope) =
        OrderItemHandover(
            source = RecordingSource(),
            scope = scope,
            read = { draft },
            write = { draft = it },
            onRecorded = { reloads += 1 },
        )

    /**
     * The cap is what has been built, not what was ordered.
     *
     * Six ordered, four built, one delivered: three may go — never five.
     */
    @Test
    fun `the ceiling is the manufactured-but-undelivered count`() =
        runTest(dispatcher) {
            val subject = holder(this)

            subject.open(line())

            assertEquals(DELIVERABLE, draft?.deliverable)

            subject.change { it.copy(amount = DELIVERABLE.toString(), recipient = "Vex") }
            assertTrue(draft?.submittable == true)

            subject.change { it.copy(amount = (DELIVERABLE + 1).toString()) }
            assertFalse("one more than was built cannot be handed over", draft?.submittable == true)
        }

    /** The projection counts against what was ordered, which is what closes the line. */
    @Test
    fun `reaching the ordered count reads as fulfilled`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.open(line(built = ORDERED, delivered = DELIVERED))

            subject.change { it.copy(amount = (ORDERED - DELIVERED).toString()) }

            assertEquals(ORDERED, draft?.projected)
            assertTrue(draft?.completes == true)
        }

    /** A blank handle is refused before the server has to say so — it is `@NotBlank`. */
    @Test
    fun `a blank recipient is not submittable`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.open(line())

            subject.change { it.copy(amount = "1", recipient = "   ") }

            assertFalse(draft?.submittable == true)
        }

    /** A line with nothing built and undelivered has nothing to hand over, so the sheet stays shut. */
    @Test
    fun `a line with nothing deliverable does not open`() =
        runTest(dispatcher) {
            val subject = holder(this)

            subject.open(line(built = DELIVERED, delivered = DELIVERED))

            assertEquals(0, draft?.deliverable)
            assertFalse("and nothing can be sent from it", draft?.submittable == true)
        }

    /** A line the server sent without an id cannot be addressed by the write. */
    @Test
    fun `a line without an id does not open the sheet`() =
        runTest(dispatcher) {
            val subject = holder(this)

            subject.open(line().copy(id = null))

            assertNull(draft)
        }

    /** A recorded handover closes the sheet and makes the caller re-read the Auftrag. */
    @Test
    fun `a recorded handover closes the sheet and reloads`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.open(line())
            subject.change { it.copy(amount = "2", recipient = " Vex ") }

            subject.submit("o1")
            advanceUntilIdle()

            assertEquals(listOf(Triple("i1", 2, "Vex")), writes)
            assertNull("the sheet closes on success", draft)
            assertEquals(1, reloads)
        }

    /** A refusal keeps everything typed: re-entering it is a charge for a reply. */
    @Test
    fun `a refused handover keeps the sheet filled`() =
        runTest(dispatcher) {
            answer = ApiResult.Failure(ApiError.Validation(null))
            val subject = holder(this)
            subject.open(line())
            subject.change { it.copy(amount = "2", recipient = "Vex") }

            subject.submit("o1")
            advanceUntilIdle()

            assertNotNull(draft)
            assertEquals("2", draft?.amount)
            assertTrue(draft?.error is ApiError.Validation)
            assertEquals(0, reloads)
        }

    /** Records what was written. */
    private inner class RecordingSource : JobOrderHandoverSource {
        override suspend fun stockFor(
            orderId: String,
            materialId: String,
        ): ApiResult<List<HandoverStockRow>> = error("the item handover reads no stock")

        override suspend fun record(
            orderId: String,
            inventoryItemId: String,
            amount: String,
            recipientHandle: String,
            recipientSquadron: String?,
            handoverTime: String,
        ): ApiResult<JobOrderHandoverDto> = error("that is the material handover")

        override suspend fun recordItemHandover(
            orderId: String,
            itemId: String,
            amount: Int,
            recipientHandle: String,
            handoverTime: String,
        ): ApiResult<JobOrderItemHandoverDto> {
            writes.add(Triple(itemId, amount, recipientHandle))
            return answer
        }
    }
}

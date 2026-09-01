/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.BookInOptions
import de.greluc.krt.profit.basetool.android.core.data.GameItemOption
import de.greluc.krt.profit.basetool.android.core.data.HandoverStockRow
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItem
import de.greluc.krt.profit.basetool.android.core.data.JobOrderItemRequirement
import de.greluc.krt.profit.basetool.android.core.data.JobOrderProductionSource
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.MemberOption
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitOption
import de.greluc.krt.profit.basetool.android.core.data.PickerPage
import de.greluc.krt.profit.basetool.android.core.data.ProductionBooking
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * „Herstellung erfassen" — booking a production run against one item line.
 *
 * The properties worth a class are the ones the server also checks, because a form that lets a
 * member send a plan the server refuses has wasted their whole entry: the demand scales with the
 * run, the coverage is **exact** rather than "enough", a skipped material drops out of both, and
 * the produced units need a place to land.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderProductionTest {
    private companion object {
        /** The line asks for six units. */
        const val LINE_AMOUNT = 6

        /** Two are already built, so four are open. */
        const val BUILT = 2

        /** The whole line needs this much Laranite. */
        const val LINE_DEMAND = 300.0

        /** Which makes three units cost half of it. */
        const val HALF_LINE_DEMAND = 150.0

        /** The line's version, echoed by the write. */
        const val LINE_VERSION = 7L

        /** The first stock row's version. */
        const val ROW_ONE_VERSION = 3L

        /** What the first row can actually give up. */
        const val ROW_ONE_AVAILABLE = 100.0

        /** And what the second one holds. */
        const val ROW_TWO_AVAILABLE = 80.0

        /** Three of the six, the run every assertion here books. */
        const val THREE = 3

        /** Two draws, one per candidate row. */
        const val TWO_DRAWS = 2
    }

    private val dispatcher = StandardTestDispatcher()

    private var draft: ProductionDraft? = null
    private var reloads = 0
    private val booked = mutableListOf<ProductionBooking>()

    private var stock: List<HandoverStockRow> =
        listOf(
            HandoverStockRow(
                id = "r1",
                owner = "Rhea",
                location = "ARC-L1",
                quality = 874,
                amount = "120",
                stock = 120.0,
                slice = ROW_ONE_AVAILABLE,
                version = ROW_ONE_VERSION,
            ),
            HandoverStockRow(
                id = "r2",
                owner = "Dorn",
                location = "Lorville",
                quality = 810,
                amount = "80",
                stock = ROW_TWO_AVAILABLE,
                slice = 200.0,
                version = 4L,
            ),
        )
    private var writeAnswer: ApiResult<Unit> = ApiResult.Success(Unit)
    private var memberships: List<OrgUnitOption> =
        listOf(OrgUnitOption(id = "ou1", name = "STAFFEL 1"), OrgUnitOption(id = "ou2", name = "SK VANGUARD"))

    private fun line() =
        JobOrderItem(
            id = "i1",
            gameItemId = "g1",
            name = "Ballistic Gatling",
            blueprintName = "Gatling — Standard",
            blueprintId = "b1",
            amount = LINE_AMOUNT,
            manufactured = BUILT,
            delivered = 0,
            blueprintStale = false,
            requirements =
                listOf(
                    JobOrderItemRequirement(
                        materialId = "m1",
                        name = "Laranite",
                        unit = "SCU",
                        requiredTotal = LINE_DEMAND,
                    ),
                ),
            version = LINE_VERSION,
        )

    private fun production(scope: kotlinx.coroutines.CoroutineScope) =
        OrderProduction(
            source = RecordingSource(),
            options = FakeOptions(),
            myUserId = { "me" },
            scope = scope,
            slot = ProductionSlot(read = { draft }, write = { draft = it }),
            onBooked = { reloads += 1 },
        )

    /** Opens the sheet with the plan loaded and a place picked, ready to be reconciled. */
    private fun TestScope.opened(): OrderProduction {
        val subject = production(this)
        subject.open("o1", line(), responsibleOrgUnitId = "ou2")
        advanceUntilIdle()
        subject.chooseLocation("loc1", "ARC-L1")
        return subject
    }

    /**
     * A partial run costs a partial share of the line's demand.
     *
     * The server sends the demand for the **whole** line, so three of six units cost half of it.
     * Getting this wrong would put a figure on screen that the coverage gate then refuses, with no
     * way for the member to see why.
     */
    @Test
    fun `the demand scales with how many are being built`() =
        runTest(dispatcher) {
            val subject = opened()

            subject.changeAmount("3")

            assertEquals(HALF_LINE_DEMAND, draft?.materials?.first()?.demand(THREE))
        }

    /**
     * Exactly, not „at least".
     *
     * `JobOrderItemProductionService` refuses a plan that over- or under-covers the demand, so a
     * form that allowed either would spend the member's whole entry on a 422.
     */
    @Test
    fun `under-covering and over-covering both hold the submit`() =
        runTest(dispatcher) {
            val subject = opened()
            subject.changeAmount("3")

            subject.changeDraw("m1", "r1", "100")
            assertFalse("100 of 150 is not covered", draft?.submittable == true)

            subject.changeDraw("m1", "r2", "80")
            assertFalse("180 of 150 is over-covered", draft?.submittable == true)

            subject.changeDraw("m1", "r2", "50")
            assertTrue("150 of 150 covers it", draft?.submittable == true)
        }

    /**
     * The plan is arithmetic nobody should do on a phone.
     *
     * Filling takes from the rows in order and never asks a row for more than it can give — the
     * cap being `min(earmark, stock)`, because an earmark can outlive the material it was made
     * against.
     */
    @Test
    fun `covering the demand fills the rows in order and respects each cap`() =
        runTest(dispatcher) {
            val subject = opened()
            subject.changeAmount("3")

            subject.autoFill("m1")

            val material = draft?.materials?.first()
            assertEquals("100", material?.amounts?.get("r1"))
            assertEquals("50", material?.amounts?.get("r2"))
            assertTrue(draft?.submittable == true)
        }

    /**
     * A material consumed outside the tool is recorded and not booked out.
     *
     * Its demand drops out of the gate — otherwise the run could never be booked — and its id goes
     * on `skippedMaterialIds` so the server drops it too.
     */
    @Test
    fun `a skipped material covers itself and is named in the write`() =
        runTest(dispatcher) {
            val subject = opened()
            subject.changeAmount("3")

            subject.toggleSkip("m1")

            assertTrue("a skipped material never blocks the gate", draft?.submittable == true)

            subject.submit()
            advanceUntilIdle()

            assertEquals(listOf("m1"), booked.single().skippedMaterialIds)
            assertTrue("nothing is drawn for it", booked.single().consumption.isEmpty())
        }

    /**
     * Only rows earmarked to **this** Auftrag are candidates.
     *
     * A production booking draws against the promise; a row without one would be refused, so
     * offering it would be offering a choice that cannot be made.
     */
    @Test
    fun `a row with no earmark is not offered`() =
        runTest(dispatcher) {
            stock =
                stock +
                HandoverStockRow(
                    id = "r3",
                    owner = "Vex",
                    location = "Area18",
                    quality = null,
                    amount = "999",
                    stock = 999.0,
                    slice = 0.0,
                    version = 1L,
                )
            val subject = opened()

            subject.changeAmount("1")

            assertEquals(listOf("r1", "r2"), draft?.materials?.first()?.rows?.map { it.id })
        }

    /**
     * Produced units need somewhere to be.
     *
     * `bookIn.locationId` is `@NotNull`, and the artboard does not draw the section at all — which
     * is exactly why the gate is asserted here rather than left to the server.
     */
    @Test
    fun `without a place the run cannot be booked`() =
        runTest(dispatcher) {
            val subject = production(this)
            subject.open("o1", line(), responsibleOrgUnitId = null)
            advanceUntilIdle()
            subject.changeAmount("3")
            subject.autoFill("m1")

            assertFalse(draft?.submittable == true)

            subject.chooseLocation("loc1", "ARC-L1")

            assertTrue(draft?.submittable == true)
        }

    /** Personal stock never carries earmarks, and the combination is a 400 rather than a warning. */
    @Test
    fun `booking it in personally drops the order earmark`() =
        runTest(dispatcher) {
            val subject = opened()

            subject.togglePersonal()

            assertFalse(draft?.bookIn?.allocate == true)
            assertFalse(draft?.bookIn?.toWire()?.allocateToOrder == true)
        }

    /**
     * The Auftrag's own unit is the preselected pool — when the owner is in it.
     *
     * The server validates the pick against the **owner's** memberships, so a unit they do not
     * belong to would be a 400; preselecting the responsible one mirrors the web and keeps the
     * „more than one membership and no pick" branch unreachable.
     */
    @Test
    fun `the responsible unit is preselected when the owner belongs to it`() =
        runTest(dispatcher) {
            val subject = production(this)

            subject.open("o1", line(), responsibleOrgUnitId = "ou2")
            advanceUntilIdle()

            assertEquals("ou2", draft?.bookIn?.orgUnitId)
        }

    /** With one membership there is nothing to choose, and the server resolves it itself. */
    @Test
    fun `a single membership is not sent`() =
        runTest(dispatcher) {
            memberships = listOf(OrgUnitOption(id = "ou1", name = "STAFFEL 1"))
            val subject = opened()

            assertNull(draft?.bookIn?.toWire()?.owningOrgUnitId)
        }

    /** A booked run closes the sheet, re-reads the Auftrag, and carries the line's own version. */
    @Test
    fun `a booked run closes the sheet and reloads`() =
        runTest(dispatcher) {
            val subject = opened()
            subject.changeAmount("3")
            subject.autoFill("m1")

            subject.submit()
            advanceUntilIdle()

            val booking = booked.single()
            assertEquals(THREE, booking.amount)
            assertEquals(LINE_VERSION, booking.version)
            assertEquals(TWO_DRAWS, booking.consumption.size)
            assertEquals(ROW_ONE_VERSION, booking.consumption.first().version)
            assertEquals("loc1", booking.bookIn.locationId)
            assertNull("the sheet closes on success", draft)
            assertEquals(1, reloads)
        }

    /** A refusal keeps the plan: re-entering a whole consumption plan is a charge for a reply. */
    @Test
    fun `a refused run keeps the plan on screen`() =
        runTest(dispatcher) {
            writeAnswer = ApiResult.Failure(ApiError.OptimisticLock())
            val subject = opened()
            subject.changeAmount("3")
            subject.autoFill("m1")

            subject.submit()
            advanceUntilIdle()

            assertNotNull(draft)
            assertEquals("3", draft?.amount)
            assertEquals("100", draft?.materials?.first()?.amounts?.get("r1"))
            assertTrue(draft?.error is ApiError.OptimisticLock)
            assertEquals(0, reloads)
        }

    /** More than is open cannot be built, whatever the plan says. */
    @Test
    fun `more than is open is refused before the server has to`() =
        runTest(dispatcher) {
            val subject = opened()

            subject.changeAmount("5")
            subject.autoFill("m1")

            assertFalse("only four are open", draft?.submittable == true)
        }

    /** A line the server sent without a version cannot be locked, so the sheet stays shut. */
    @Test
    fun `a line without a version does not open the sheet`() =
        runTest(dispatcher) {
            val subject = production(this)

            subject.open("o1", line().copy(version = null), responsibleOrgUnitId = null)
            advanceUntilIdle()

            assertNull(draft)
        }

    /** Records what was asked for and what was written. */
    private inner class RecordingSource : JobOrderProductionSource {
        override suspend fun linkedStock(
            orderId: String,
            materialId: String,
        ): ApiResult<List<HandoverStockRow>> = ApiResult.Success(stock)

        override suspend fun bookProduction(booking: ProductionBooking): ApiResult<Unit> {
            booked.add(booking)
            return writeAnswer
        }
    }

    /** Where produced stock may land. */
    private inner class FakeOptions : BookInOptions {
        override suspend fun gameItems(query: String): ApiResult<PickerPage<GameItemOption>> =
            ApiResult.Success(PickerPage())

        override suspend fun locations(query: String): ApiResult<PickerPage<LocationOption>> =
            ApiResult.Success(PickerPage(listOf(LocationOption(id = "loc1", name = "ARC-L1"))))

        override suspend fun members(query: String): ApiResult<PickerPage<MemberOption>> =
            ApiResult.Success(PickerPage(listOf(MemberOption(id = "u2", name = "Dorn"))))

        override suspend fun orgUnitsFor(userId: String): ApiResult<List<OrgUnitOption>> =
            ApiResult.Success(memberships)
    }
}

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import de.greluc.krt.profit.basetool.android.core.data.ClaimBucket
import de.greluc.krt.profit.basetool.android.core.data.ClaimQuality
import de.greluc.krt.profit.basetool.android.core.data.MaterialClaim
import de.greluc.krt.profit.basetool.android.core.data.MaterialClaimSource
import de.greluc.krt.profit.basetool.android.core.data.OrgUnit
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitKind
import de.greluc.krt.profit.basetool.android.core.data.OrgUnitSource
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
import java.math.BigDecimal

/**
 * Zusagen — a Staffel signing up to deliver part of a Spezialkommando order.
 *
 * The properties worth a class: a claim belongs to a **unit**, so the picker offers exactly the
 * units the server would accept — profit-eligible squadrons the caller belongs to — and setting and
 * changing are one call, so an existing pledge opens filled in rather than making somebody retype
 * what they already promised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderClaimsTest {
    private companion object {
        /** What the bucket needs. */
        val REQUIRED: BigDecimal = BigDecimal("400")

        /** What is already pledged. */
        val CLAIMED: BigDecimal = BigDecimal("280")

        /** Which leaves this much. */
        val OPEN: BigDecimal = BigDecimal("120")

        /** The caller's own Staffel's pledge. */
        val MINE: BigDecimal = BigDecimal("100")

        /** What the „change the pledge" test raises it to. */
        const val RAISED = 150.0

        /** And what the comma test types, once parsed. */
        const val TWELVE_AND_A_HALF = 12.5
    }

    private val dispatcher = StandardTestDispatcher()

    private var state = ClaimsState()
    private val upserts = mutableListOf<Triple<String, String, Double>>()
    private val withdrawals = mutableListOf<String>()
    private var writeAnswer: ApiResult<Unit> = ApiResult.Success(Unit)
    private var memberships: List<OrgUnit> =
        listOf(
            OrgUnit("s1", "Staffel 1", "S1", OrgUnitKind.SQUADRON, profitEligible = true),
            // A Spezialkommando places orders and never claims against one; the server refuses it.
            OrgUnit("sk1", "SK Vanguard", "SKV", OrgUnitKind.SPECIAL_COMMAND, profitEligible = true),
            // And a squadron nobody marked profit-eligible is outside the order workflow.
            OrgUnit("s2", "Staffel 2", "S2", OrgUnitKind.SQUADRON, profitEligible = false),
        )

    private fun bucket() =
        ClaimBucket(
            materialId = "m1",
            materialName = "Laranite",
            unit = "SCU",
            quality = ClaimQuality.NONE,
            required = REQUIRED,
            claimed = CLAIMED,
            open = OPEN,
            claims =
                listOf(
                    MaterialClaim(id = "c1", orgUnitId = "iri", orgUnitName = "IRI", amount = BigDecimal("120")),
                    MaterialClaim(id = "c2", orgUnitId = "s1", orgUnitName = "S1", amount = MINE),
                ),
        )

    private fun holder(scope: kotlinx.coroutines.CoroutineScope) =
        OrderClaims(
            source = RecordingSource(),
            orgUnits = FakeUnits(),
            scope = scope,
            read = { state },
            write = { state = it },
        )

    /**
     * The picker offers exactly what the server accepts.
     *
     * A Spezialkommando cannot claim, and a squadron nobody marked profit-eligible is outside the
     * workflow — offering either would turn a filled sheet into a 400.
     */
    @Test
    fun `only profit-eligible squadrons may pledge`() =
        runTest(dispatcher) {
            val subject = holder(this)

            subject.load("o1")
            advanceUntilIdle()

            assertEquals(listOf("s1"), state.units.map { it.id })
            assertEquals("s1", state.defaultUnit?.id)
        }

    /** Setting and changing are one call, so an existing pledge opens filled in. */
    @Test
    fun `an existing pledge opens with its amount and its withdrawal`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.load("o1")
            advanceUntilIdle()

            subject.open(bucket(), state.units.first())

            assertEquals("100", state.draft?.amount)
            assertEquals("c2", state.draft?.claimId)
            assertTrue(state.draft?.submittable == true)
        }

    /** A first pledge opens empty and offers no withdrawal — there is nothing to take back. */
    @Test
    fun `a first pledge opens empty and cannot be withdrawn`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.load("o1")
            advanceUntilIdle()

            subject.open(bucket().copy(claims = emptyList()), state.units.first())

            assertEquals("", state.draft?.amount)
            assertNull(state.draft?.claimId)
            assertFalse("nothing typed yet", state.draft?.submittable == true)
        }

    /** A pledge is sent for the Staffel, and the buckets are re-read because the server recomputes. */
    @Test
    fun `a pledge is sent for the unit and the buckets are re-read`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.load("o1")
            advanceUntilIdle()
            subject.open(bucket(), state.units.first())
            subject.change { it.copy(amount = "150") }

            subject.submit("o1")
            advanceUntilIdle()

            assertEquals(listOf(Triple("m1", "s1", RAISED)), upserts)
            assertNull("the sheet closes on success", state.draft)
        }

    /** A German keyboard's comma is a decimal point here too. */
    @Test
    fun `a comma is a decimal point`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.load("o1")
            advanceUntilIdle()
            subject.open(bucket(), state.units.first())
            subject.change { it.copy(amount = "12,5") }

            subject.submit("o1")
            advanceUntilIdle()

            assertEquals(TWELVE_AND_A_HALF, upserts.single().third, 0.0)
        }

    /** Withdrawing addresses the existing pledge by id and asks nothing first. */
    @Test
    fun `withdrawing takes the pledge back without a confirmation`() =
        runTest(dispatcher) {
            val subject = holder(this)
            subject.load("o1")
            advanceUntilIdle()
            subject.open(bucket(), state.units.first())

            subject.withdraw("o1")
            advanceUntilIdle()

            assertEquals(listOf("c2"), withdrawals)
            assertNull(state.draft)
        }

    /**
     * Overclaim is the server's own refusal, and the sheet keeps what was typed.
     *
     * Design ch. 10 artboard 13 says overclaim is allowed; `MaterialClaimService` answers 400 for
     * it (REQ-ORDERS-024). The artboard is on the design gap list.
     */
    @Test
    fun `a refused pledge keeps the sheet and its amount`() =
        runTest(dispatcher) {
            writeAnswer = ApiResult.Failure(ApiError.Validation(null))
            val subject = holder(this)
            subject.load("o1")
            advanceUntilIdle()
            subject.open(bucket(), state.units.first())
            subject.change { it.copy(amount = "500") }

            subject.submit("o1")
            advanceUntilIdle()

            assertNotNull(state.draft)
            assertEquals("500", state.draft?.amount)
            assertTrue(state.draft?.error is ApiError.Validation)
        }

    /** Records what was written. */
    private inner class RecordingSource : MaterialClaimSource {
        override suspend fun buckets(orderId: String): ApiResult<List<ClaimBucket>> =
            ApiResult.Success(listOf(bucket()))

        override suspend fun upsert(
            orderId: String,
            materialId: String,
            quality: ClaimQuality,
            orgUnitId: String,
            amount: Double,
        ): ApiResult<Unit> {
            upserts.add(Triple(materialId, orgUnitId, amount))
            return writeAnswer
        }

        override suspend fun withdraw(
            orderId: String,
            claimId: String,
        ): ApiResult<Unit> {
            withdrawals.add(claimId)
            return writeAnswer
        }
    }

    /** The caller's own units. */
    private inner class FakeUnits : OrgUnitSource {
        override suspend fun memberships(): ApiResult<List<OrgUnit>> = ApiResult.Success(memberships)

        override suspend fun activeAllKinds(): ApiResult<List<OrgUnit>> = ApiResult.Success(memberships)

        override suspend fun serverDefault(): ApiResult<String?> = ApiResult.Success(null)
    }
}

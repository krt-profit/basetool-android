/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.data.DirectBookingKind
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
 * The fields the Direktbuchung carries beyond the four the artboard draws.
 *
 * Its own class rather than more of `BankStaffViewModelTest`: these five fields are one subject —
 * what a booking made from the app records, against what the same booking made in the browser
 * records — and the sibling class had grown past what detekt will hold in one file.
 *
 * The property that carries them: **each field's rule belongs to something other than the form.**
 * The reason is demanded by the ACCOUNT, the split's two halves are bound by a server-side rule no
 * generated client can see, and the counterparty is one identity out of two. A form that made any
 * of those up would send something the server refuses after everything else has been typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankDirectBookingFieldsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Three account kinds demand a reason, and the form asks for it instead of collecting a 409.
     *
     * `BANK_JUSTIFICATION_REQUIRED` is what the server answers a blank one with — after the member
     * has typed the amount, picked the holder and pressed the CTA. The rule belongs to the
     * ACCOUNT, so it travels with the account rather than being re-derived at submit time.
     */
    @Test
    fun `a KRT withdrawal cannot be sent without a reason`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.WITHDRAWAL,
                    accountType = "CARTEL",
                    amount = "5000",
                    holderId = "h1",
                )
            }

            assertTrue(model.state.value.direct?.justificationRequired == true)
            assertFalse(model.state.value.direct?.submittable(null) == true)

            model.directBooking.edit { it.copy(justification = "Bargeld-Uebergabe") }

            assertTrue(model.state.value.direct?.submittable(null) == true)
        }

    /** An ordinary account asks for none, and an unknown kind does not invent one. */
    @Test
    fun `an org-unit account needs no reason, and neither does an unknown kind`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.WITHDRAWAL,
                    accountType = "ORG_UNIT",
                    amount = "5000",
                    holderId = "h1",
                )
            }
            assertFalse(model.state.value.direct?.justificationRequired == true)
            assertTrue(model.state.value.direct?.submittable(null) == true)

            model.directBooking.edit { it.copy(accountType = null) }
            assertFalse(model.state.value.direct?.justificationRequired == true)
        }

    /** A deposit has no justification on the wire, so no account kind can demand one. */
    @Test
    fun `a deposit onto the KRT account needs no reason`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(accountType = "CARTEL", amount = "5000", holderId = "h1")
            }

            assertFalse(model.state.value.direct?.justificationRequired == true)
        }

    /**
     * The split's two halves travel together or not at all.
     *
     * `BankDepositRequest` carries an `@AssertTrue` refusing a split without a percentage — and it
     * is `@Schema(hidden = true)`, so no generated client and no contract test can see it. The
     * rule has to be kept by hand, which is what this pins.
     */
    @Test
    fun `a split deposit needs a percentage in range`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(amount = "1000", holderId = "h1", splitEnabled = true)
            }
            assertFalse("no percentage yet", model.state.value.direct?.submittable(null) == true)

            model.directBooking.edit { it.copy(splitPercent = "0") }
            assertFalse("below the range", model.state.value.direct?.submittable(null) == true)

            model.directBooking.edit { it.copy(splitPercent = "101") }
            assertFalse("above the range", model.state.value.direct?.submittable(null) == true)

            model.directBooking.edit { it.copy(splitPercent = "20") }
            assertTrue(model.state.value.direct?.submittable(null) == true)

            model.directBooking.confirm(null)
            advanceUntilIdle()
            val sent = source.directBookings.single()
            assertTrue(sent.splitEnabled)
            assertEquals("20", sent.splitPercent)
        }

    /** The preview rounds half-up on the share and takes the remainder by subtraction. */
    @Test
    fun `the split preview always adds back to the deposit`() =
        runTest(dispatcher) {
            val model = staffModel(RecordingStaff())
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(amount = "1001", holderId = "h1", splitEnabled = true, splitPercent = "33")
            }

            val preview = requireNotNull(model.state.value.direct?.splitPreview)
            assertEquals(java.math.BigDecimal("330"), preview.first)
            assertEquals(java.math.BigDecimal("671"), preview.second)
        }

    /**
     * A counterparty is a member OR a name, never both.
     *
     * Sending both would leave the server to decide which one the member meant, and the toggle is
     * the answer to exactly that question.
     */
    @Test
    fun `switching the counterparty identity clears the other one`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(amount = "1000", holderId = "h1", counterpartyUserId = "u9")
            }
            model.directBooking.edit {
                it.copy(
                    counterpartyExternal = true,
                    counterpartyUserId = null,
                    counterpartyExternalName = "Fremder",
                )
            }
            model.directBooking.confirm(null)
            advanceUntilIdle()

            val sent = source.directBookings.single()
            assertEquals("Fremder", sent.counterpartyExternalName)
            assertEquals(null, sent.counterpartyUserId)
        }

    /** A transfer has no counterparty on the wire, so nothing typed for one may ride along. */
    @Test
    fun `a transfer sends no counterparty`() =
        runTest(dispatcher) {
            val source = RecordingStaff()
            val model = staffModel(source)
            model.loadOnce()
            advanceUntilIdle()
            model.directBooking.open("acc-1")
            model.directBooking.edit {
                it.copy(
                    amount = "1000",
                    holderId = "h1",
                    counterpartyUserId = "u9",
                    counterpartyOrgUnitId = "ou1",
                )
            }
            model.directBooking.edit {
                it.copy(
                    kind = DirectBookingKind.TRANSFER,
                    destinationAccountId = "acc-2",
                    destinationHolderId = "h2",
                )
            }
            model.directBooking.confirm(null)
            advanceUntilIdle()

            val sent = source.directBookings.single()
            assertEquals(null, sent.counterpartyUserId)
            assertEquals(null, sent.counterpartyOrgUnitId)
        }
}

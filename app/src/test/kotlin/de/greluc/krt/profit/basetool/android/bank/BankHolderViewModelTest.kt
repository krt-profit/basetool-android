/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.data.BankBookingRequest
import de.greluc.krt.profit.basetool.android.core.data.BankConfirmation
import de.greluc.krt.profit.basetool.android.core.data.BankHolder
import de.greluc.krt.profit.basetool.android.core.data.BankHolderBooking
import de.greluc.krt.profit.basetool.android.core.data.BankHolderBookingPage
import de.greluc.krt.profit.basetool.android.core.data.BankHolderSource
import de.greluc.krt.profit.basetool.android.core.data.BankRequestPage
import de.greluc.krt.profit.basetool.android.core.data.BankRequestStatus
import de.greluc.krt.profit.basetool.android.core.data.BankStaffDashboard
import de.greluc.krt.profit.basetool.android.core.data.BankStaffSource
import de.greluc.krt.profit.basetool.android.core.data.DirectBooking
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One holder's custody — design chapter 12, artboard 8.
 *
 * The rules with teeth here are about what a transfer may not do: it may not go out without a
 * destination or an amount, and a failure to read the register must not take the custody figure off
 * the screen, because the figure and its postings are what the screen is for.
 *
 * Robolectric because `KrtLog` reaches `android.util.Log`, whose unmocked stub throws inside
 * `viewModelScope` — the supervisor swallows it and the state simply stops moving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankHolderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Answers the holder reads and records every transfer. */
    private class RecordingHolders : BankHolderSource {
        var answer: ApiResult<BankHolder> = ApiResult.Success(aHolder("h1"))
        var bookings: ApiResult<BankHolderBookingPage> =
            ApiResult.Success(BankHolderBookingPage(emptyList(), 0, 0, 0))
        var transferAnswer: ApiResult<Unit> = ApiResult.Success(Unit)
        val transfers = mutableListOf<List<String?>>()
        val pagesRead = mutableListOf<Int>()

        override suspend fun holder(id: String): ApiResult<BankHolder> = answer

        override suspend fun holderBookings(
            id: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<BankHolderBookingPage> {
            pagesRead.add(page)
            return bookings
        }

        override suspend fun transferCustody(
            sourceHolderId: String,
            destinationHolderId: String,
            amount: String,
            note: String?,
        ): ApiResult<Unit> {
            transfers.add(listOf(sourceHolderId, destinationHolderId, amount, note))
            return transferAnswer
        }
    }

    /** Supplies only the holder register the transfer picks its counterparty from. */
    private class RegisterOnlyStaff(
        var holders: ApiResult<List<BankHolder>> = ApiResult.Success(emptyList()),
    ) : BankStaffSource {
        override suspend fun staffDashboard(): ApiResult<BankStaffDashboard> =
            ApiResult.Failure(ApiError.Forbidden())

        override suspend fun requestQueue(
            statuses: Set<BankRequestStatus>,
            page: Int,
            pageSize: Int,
        ): ApiResult<BankRequestPage> = ApiResult.Success(BankRequestPage(emptyList(), 0, 1, 0))

        override suspend fun holders(): ApiResult<List<BankHolder>> = holders

        override suspend fun confirmRequest(
            confirmation: BankConfirmation,
        ): ApiResult<BankBookingRequest> = ApiResult.Failure(ApiError.Forbidden())

        val directBookings = mutableListOf<DirectBooking>()
        var directAnswer: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun bookDirectly(booking: DirectBooking): ApiResult<Unit> {
            directBookings.add(booking)
            return directAnswer
        }

        override suspend fun rejectRequest(
            id: String,
            reason: String,
            version: Long,
        ): ApiResult<BankBookingRequest> = ApiResult.Failure(ApiError.Forbidden())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(
        source: BankHolderSource,
        staff: BankStaffSource = RegisterOnlyStaff(),
    ) = BankHolderViewModel(source = source, staff = staff, holderId = "h1")

    @Test
    fun `a transfer names the shown holder as its source`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onTransfer()
            viewModel.onDraftChanged(
                BankCustodyDraft(destinationId = "h2", amount = "500", note = "Übergabe"),
            )
            viewModel.onConfirmTransfer()
            advanceUntilIdle()

            assertEquals(listOf(listOf("h1", "h2", "500", "Übergabe")), source.transfers)
            assertNull(viewModel.state.value.draft)
        }

    @Test
    fun `a transfer without a destination is not sent`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onTransfer()
            viewModel.onDraftChanged(BankCustodyDraft(amount = "500"))
            viewModel.onConfirmTransfer()
            advanceUntilIdle()

            assertTrue(source.transfers.isEmpty())
        }

    @Test
    fun `a transfer without an amount is not sent as a zero`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onTransfer()
            viewModel.onDraftChanged(BankCustodyDraft(destinationId = "h2", amount = "   "))
            viewModel.onConfirmTransfer()
            advanceUntilIdle()

            // An empty amount would otherwise reach the server as a zero-value posting, which is a
            // real ledger entry saying nothing happened.
            assertTrue(source.transfers.isEmpty())
        }

    @Test
    fun `a refused transfer keeps the sheet open and states why`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            source.transferAnswer = ApiResult.Failure(ApiError.Validation())
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()

            viewModel.onTransfer()
            viewModel.onDraftChanged(BankCustodyDraft(destinationId = "h2", amount = "500"))
            viewModel.onConfirmTransfer()
            advanceUntilIdle()

            assertEquals("500", viewModel.state.value.draft?.amount)
            assertTrue(viewModel.state.value.error is ApiError.Validation)
        }

    @Test
    fun `a register that cannot be read leaves the custody figure standing`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            source.answer = ApiResult.Success(aHolder("h1", held = "118600.0000"))
            val staff = RegisterOnlyStaff(ApiResult.Failure(ApiError.Forbidden()))
            val viewModel = model(source, staff)
            viewModel.loadOnce()
            advanceUntilIdle()

            // Only the transfer needs the register; the figure and its postings are the screen.
            assertEquals("118600.0000", viewModel.state.value.holder?.totalHeld)
            assertTrue(viewModel.state.value.phase is BankPhase.Ready)
            assertTrue(viewModel.state.value.peers.isEmpty())
        }

    @Test
    fun `the register offers only other active holders`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            val staff =
                RegisterOnlyStaff(
                    ApiResult.Success(
                        listOf(
                            aHolder("h1"),
                            aHolder("h2"),
                            aHolder("h3", active = false),
                        ),
                    ),
                )
            val viewModel = model(source, staff)
            viewModel.loadOnce()
            advanceUntilIdle()

            // Not oneself, and not someone who may receive nothing new anyway.
            assertEquals(listOf("h2"), viewModel.state.value.peers.map { it.id })
        }

    @Test
    fun `paging past the last page is refused rather than sent`() =
        runTest(dispatcher) {
            val source = RecordingHolders()
            source.bookings =
                ApiResult.Success(
                    BankHolderBookingPage(listOf(booking()), page = 0, totalElements = 2, totalPages = 1),
                )
            val viewModel = model(source)
            viewModel.loadOnce()
            advanceUntilIdle()
            source.pagesRead.clear()

            viewModel.onPage(1)
            viewModel.onPage(-1)
            advanceUntilIdle()

            assertTrue(source.pagesRead.isEmpty())
        }

    private companion object {
        /**
         * One holder.
         *
         * @param id their id.
         * @param active whether they may still receive custody.
         * @param held what they hold.
         * @return the holder.
         */
        fun aHolder(
            id: String,
            active: Boolean = true,
            held: String = "0.0000",
        ) = BankHolder(id = id, handle = "Rhea", active = active, totalHeld = held, version = 1)

        /**
         * One posting.
         *
         * @return the posting.
         */
        fun booking() =
            BankHolderBooking(
                id = "p1",
                transactionId = "t1",
                type = "DEPOSIT",
                amount = "500.0000",
                note = null,
                createdAt = "2026-08-27T10:00:00Z",
                counterAccount = null,
                counterHolder = "Dorn",
                reversed = false,
            )
    }
}

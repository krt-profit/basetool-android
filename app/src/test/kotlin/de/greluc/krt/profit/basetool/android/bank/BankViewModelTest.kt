/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.bank

import de.greluc.krt.profit.basetool.android.core.data.BankAccountDetail
import de.greluc.krt.profit.basetool.android.core.data.BankAccountSummary
import de.greluc.krt.profit.basetool.android.core.data.BankBooking
import de.greluc.krt.profit.basetool.android.core.data.BankBookingPage
import de.greluc.krt.profit.basetool.android.core.data.BankSource
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
import java.io.IOException

/**
 * The bank's two screens.
 *
 * The rule with teeth on the detail: the account and its first ledger page fail **together**. A
 * balance over a missing ledger reads as an account with no history rather than one that did not
 * load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * Answers all three reads and counts them.
     *
     * @property balanceAnswers responses for [balances], the last repeating.
     * @property accountAnswers responses for [account], likewise.
     * @property ledgerAnswers responses for [bookings], likewise.
     */
    private class RecordingSource(
        private val balanceAnswers: MutableList<ApiResult<List<BankAccountSummary>>> = mutableListOf(),
        private val accountAnswers: MutableList<ApiResult<BankAccountDetail>> = mutableListOf(),
        private val ledgerAnswers: MutableList<ApiResult<BankBookingPage>> = mutableListOf(),
    ) : BankSource {
        var balanceCalls = 0
        val ledgerPages = mutableListOf<Int>()

        override suspend fun balances(): ApiResult<List<BankAccountSummary>> {
            balanceCalls++
            return if (balanceAnswers.size > 1) balanceAnswers.removeAt(0) else balanceAnswers.first()
        }

        override suspend fun account(id: String): ApiResult<BankAccountDetail> =
            if (accountAnswers.size > 1) accountAnswers.removeAt(0) else accountAnswers.first()

        override suspend fun bookings(
            id: String,
            page: Int,
            pageSize: Int,
        ): ApiResult<BankBookingPage> {
            ledgerPages.add(page)
            return if (ledgerAnswers.size > 1) ledgerAnswers.removeAt(0) else ledgerAnswers.first()
        }
    }

    private fun summary(id: String) =
        BankAccountSummary(id, "K-001", "Einsatzkasse", "Bereich Profit", "1.0", null, emptyList())

    private fun detail() = BankAccountDetail("a1", "K-001", "Einsatzkasse", "84200.0000", null, TWO)

    private fun booking(id: String) = BankBooking(id, "DEPOSIT", "1.0", null, null, null)

    private fun ledger(
        vararg rows: BankBooking,
        page: Int = 0,
        totalPages: Int = 1,
    ) = BankBookingPage(rows.toList(), page = page, totalPages = totalPages, totalElements = TWO)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the accounts load once`() =
        runTest(dispatcher) {
            val source = RecordingSource(mutableListOf(ApiResult.Success(listOf(summary("a1")))))
            val model = BankViewModel(source)

            model.loadOnce()
            advanceUntilIdle()
            model.loadOnce()
            advanceUntilIdle()

            assertEquals(1, source.balanceCalls)
            assertEquals(BankPhase.Ready, model.state.value.phase)
        }

    @Test
    fun `no visible account is a result, not a failure`() =
        runTest(dispatcher) {
            val source = RecordingSource(mutableListOf(ApiResult.Success(emptyList())))
            val model = BankViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertEquals(BankPhase.Ready, model.state.value.phase)
            assertTrue(model.state.value.accounts.isEmpty())
        }

    @Test
    fun `a failed list is a failure`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))))
            val model = BankViewModel(source)

            model.loadOnce()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is BankPhase.Failed)
        }

    @Test
    fun `the account and its first ledger page arrive together`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(ApiResult.Success(ledger(booking("p1")))),
                )
            val model = BankAccountViewModel(source, "a1")

            model.load()
            advanceUntilIdle()

            assertEquals(BankPhase.Ready, model.state.value.phase)
            assertEquals("Einsatzkasse", model.state.value.account?.name)
            assertEquals(1, model.state.value.bookings.size)
        }

    @Test
    fun `a failed ledger fails the screen rather than showing a history-less account`() =
        runTest(dispatcher) {
            // Both reads carry the same gate, so a split state would model a case the server cannot
            // produce — and a balance over a missing ledger reads as an account with no history.
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(ApiResult.Failure(ApiError.Network(IOException("x")))),
                )
            val model = BankAccountViewModel(source, "a1")

            model.load()
            advanceUntilIdle()

            assertTrue(model.state.value.phase is BankPhase.Failed)
            assertNull(model.state.value.account)
        }

    @Test
    fun `a refused account carries its cause so the screen can word it`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Failure(ApiError.Forbidden())),
                    mutableListOf(ApiResult.Success(ledger())),
                )
            val model = BankAccountViewModel(source, "a1")

            model.load()
            advanceUntilIdle()

            val phase = model.state.value.phase
            assertTrue(phase is BankPhase.Failed)
            assertTrue((phase as BankPhase.Failed).error is ApiError.Forbidden)
        }

    @Test
    fun `older ledger lines are appended`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(
                        ApiResult.Success(ledger(booking("p1"), totalPages = TWO_PAGES)),
                        ApiResult.Success(ledger(booking("p2"), page = 1, totalPages = TWO_PAGES)),
                    ),
                )
            val model = BankAccountViewModel(source, "a1")
            model.load()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("p1", "p2"), model.state.value.bookings.map { it.id })
            assertEquals(listOf(0, 1), source.ledgerPages)
        }

    @Test
    fun `a failed continuation keeps the ledger on screen`() =
        runTest(dispatcher) {
            val source =
                RecordingSource(
                    mutableListOf(ApiResult.Success(emptyList())),
                    mutableListOf(ApiResult.Success(detail())),
                    mutableListOf(
                        ApiResult.Success(ledger(booking("p1"), totalPages = TWO_PAGES)),
                        ApiResult.Failure(ApiError.Network(IOException("x"))),
                    ),
                )
            val model = BankAccountViewModel(source, "a1")
            model.load()
            advanceUntilIdle()

            model.onLoadMore()
            advanceUntilIdle()

            assertEquals(1, model.state.value.bookings.size)
            assertEquals(BankPhase.Ready, model.state.value.phase)
        }

    private companion object {
        /** A two-line ledger. */
        const val TWO = 2L

        /** Its page count. */
        const val TWO_PAGES = 2
    }
}

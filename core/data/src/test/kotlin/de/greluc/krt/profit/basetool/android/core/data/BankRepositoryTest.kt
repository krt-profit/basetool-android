/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
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
 * The org bank reads.
 *
 * Two things carry the weight: every amount stays a string so no rounding can enter, and the
 * direction of a booking comes from its kind rather than from its sign.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankRepositoryTest {
    private companion object {
        /** What the bank writes answer with: accepted, no body. */
        const val HTTP_ACCEPTED = 202

        /** A booking that actually moved the ledger. */
        const val HTTP_CREATED = 201

        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403

        /** How many points the sparkline fixture carries. */
        const val THREE_POINTS = 3

        /** How many lines the ledger holds. */
        const val LEDGER_TOTAL = 42L

        val BALANCES =
            """
            [
              {"accountId": "a1", "accountNo": "K-001", "accountName": "Einsatzkasse",
               "orgUnitName": "Bereich Profit", "balance": 84200.0000, "delta30d": 12400.0000,
               "sparkline": [1.0, 2.5, 2.0]},
              {"accountNo": "K-002", "accountName": "Ohne Id"}
            ]
            """.trimIndent()

        val ACCOUNT =
            """
            {"detail": {"account": {"id": "a1", "accountNo": "K-001", "name": "Einsatzkasse",
                                    "balance": 84200.0000},
                        "delta30d": 12400.0000, "bookingCount": $LEDGER_TOTAL}}
            """.trimIndent()

        val LEDGER =
            """
            {"content": [
               {"postingId": "p1", "type": "DEPOSIT", "amount": 12400.0000,
                "note": "Verkauf Quantainium", "holderHandle": "Rhea",
                "createdAt": "2026-08-22T10:00:00Z"},
               {"postingId": "p2", "type": "WITHDRAWAL", "amount": 3200.0000,
                "transferFee": 16.0000, "counterpartyHandle": "Kestrel"},
               {"postingId": "p3", "type": "WIPE_RESET", "amount": 0.0000}
             ],
             "page": 0, "totalElements": $LEDGER_TOTAL, "totalPages": 2}
            """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: BankRepository
    private lateinit var staff: BankStaffRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().removeSuffix("/")
        repository = BankRepository(httpClient = OkHttpClient(), baseUrl = base)
        staff = BankStaffRepository(httpClient = OkHttpClient(), baseUrl = base)
    }

    /**
     * A `202` is **not** a booking, and the difference has to survive the repository.
     *
     * Over the KRT employee ceiling the server does not refuse the withdrawal — it files it as a
     * band-routed approval request and answers `202` with a `pendingRequest` where a booking would
     * have carried a `transaction` (REQ-BANK-047, ADR-0109). Both are 2xx, so a client that only
     * asks "was it successful" closes its sheet on a withdrawal that moved nothing.
     */
    @Test
    fun `a filed withdrawal is reported as filed, not as booked`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_ACCEPTED)
                    .setHeader("Content-Type", "application/json")
                    .body("""{"pendingRequest":{"id":"r1","type":"WITHDRAWAL"}}""")
                    .build(),
            )

            val result =
                staff.bookDirectly(
                    DirectBooking(
                        kind = DirectBookingKind.WITHDRAWAL,
                        accountId = "acc-1",
                        amount = "100000",
                        holderId = "h1",
                    ),
                )

            assertEquals(BankDirectOutcome.REQUEST_FILED, (result as ApiResult.Success).value)
        }

    /** Under the ceiling the same call books, and says so. */
    @Test
    fun `a booked transfer is reported as booked`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_CREATED)
                    .setHeader("Content-Type", "application/json")
                    .body("""{"transaction":{"id":"t1"}}""")
                    .build(),
            )

            val result =
                staff.bookDirectly(
                    DirectBooking(
                        kind = DirectBookingKind.TRANSFER,
                        accountId = "acc-1",
                        amount = "1000",
                        holderId = "h1",
                        destinationAccountId = "acc-2",
                        destinationHolderId = "h2",
                    ),
                )

            assertEquals(BankDirectOutcome.BOOKED, (result as ApiResult.Success).value)
        }

    /**
     * A deposit has no ceiling, so it cannot be filed and its answer is discarded.
     *
     * Pinned so that the asymmetry is deliberate rather than an oversight: `bookDeposit` answers
     * `201` with the transaction and never a `pendingRequest`.
     */
    @Test
    fun `a deposit is always booked`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_CREATED).build())

            val result =
                staff.bookDirectly(
                    DirectBooking(
                        kind = DirectBookingKind.DEPOSIT,
                        accountId = "acc-1",
                        amount = "1000",
                        holderId = "h1",
                    ),
                )

            assertEquals(BankDirectOutcome.BOOKED, (result as ApiResult.Success).value)
        }

    @Test
    fun `the fee flag rides on a withdrawal and stays off a deposit`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_CREATED)
                    .setHeader("Content-Type", "application/json")
                    .body("{}")
                    .build(),
            )
            staff.bookDirectly(
                DirectBooking(
                    kind = DirectBookingKind.WITHDRAWAL,
                    accountId = "acc-1",
                    amount = "100000",
                    holderId = "h1",
                    feeInclusive = true,
                ),
            )
            assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("\"feeInclusive\":true"))

            server.enqueue(MockResponse.Builder().code(HTTP_ACCEPTED).build())
            staff.bookDirectly(
                DirectBooking(
                    kind = DirectBookingKind.DEPOSIT,
                    accountId = "acc-1",
                    amount = "100000",
                    holderId = "h1",
                    feeInclusive = true,
                ),
            )
            // A deposit is fee-free, so the flag decides nothing and must not travel: a field that
            // changes nothing invites the next reader to think it did.
            assertFalse(server.takeRequest().body?.utf8().orEmpty().contains("feeInclusive"))
        }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Enqueues one response.
     *
     * @param body the response body.
     * @param status the status code.
     */
    private fun respond(
        body: String,
        status: Int = HTTP_OK,
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    /**
     * The URL the repository requested.
     *
     * @return the recorded target.
     */
    private fun requestedUrl(): HttpUrl = ("http://localhost" + server.takeRequest().target).toHttpUrl()

    @Test
    fun `an account row maps onto the model`() =
        runTest {
            respond(BALANCES)

            val accounts = (repository.balances() as ApiResult.Success).value

            val first = accounts.first()
            assertEquals("a1", first.id)
            assertEquals("Einsatzkasse", first.name)
            assertEquals("K-001", first.accountNo)
            assertEquals("Bereich Profit", first.orgUnitName)
            assertEquals("84200.0000", first.balance)
            assertEquals("12400.0000", first.delta30d)
            assertEquals(THREE_POINTS, first.sparkline.size)
        }

    @Test
    fun `an account without an id is dropped, because it cannot be opened`() =
        runTest {
            respond(BALANCES)

            assertEquals(1, (repository.balances() as ApiResult.Success).value.size)
        }

    @Test
    fun `the account detail is read out of its nesting`() =
        runTest {
            respond(ACCOUNT)

            val account = (repository.account("a1") as ApiResult.Success).value

            assertEquals("Einsatzkasse", account.name)
            assertEquals("K-001", account.accountNo)
            assertEquals("84200.0000", account.balance)
            assertEquals("12400.0000", account.delta30d)
            assertEquals(LEDGER_TOTAL, account.bookingCount)
        }

    @Test
    fun `the direction of a booking comes from its kind, never from its sign`() =
        runTest {
            // The ledger stores every amount as a positive magnitude. Reading the sign off the
            // number would show every withdrawal as a deposit.
            respond(LEDGER)

            val lines = (repository.bookings("a1") as ApiResult.Success).value.bookings

            assertEquals(true, lines[0].incoming)
            assertEquals(false, lines[1].incoming)
            assertEquals("3200.0000", lines[1].amount)
        }

    @Test
    fun `a kind this build does not know is neither in nor out`() =
        runTest {
            // Better an unsigned figure than a direction nobody checked.
            respond(LEDGER)

            assertNull((repository.bookings("a1") as ApiResult.Success).value.bookings[2].incoming)
        }

    @Test
    fun `a booking keeps the fee it cost and the recipient it named`() =
        runTest {
            // Both are on the wire and both were dropped, which left a member re-reading a past
            // transfer with an amount that did not match what left the account and no record of
            // who received it.
            respond(LEDGER)

            val line = (repository.bookings("a1") as ApiResult.Success).value.bookings[1]

            assertEquals("16.0000", line.transferFee)
            assertEquals("Kestrel", line.counterpartyHandle)
        }

    @Test
    fun `every digit the server sent survives`() =
        runTest {
            respond(LEDGER)

            assertEquals(
                "12400.0000",
                (repository.bookings("a1") as ApiResult.Success).value.bookings.first().amount,
            )
        }

    @Test
    fun `the ledger reports its paging`() =
        runTest {
            respond(LEDGER)

            val page = (repository.bookings("a1") as ApiResult.Success).value

            assertEquals(LEDGER_TOTAL, page.totalElements)
            assertTrue(page.hasMore)
        }

    @Test
    fun `a refused account is a failure, not an empty one`() =
        runTest {
            respond("{}", status = HTTP_FORBIDDEN)

            assertTrue(repository.account("a1") is ApiResult.Failure)
        }

    @Test
    fun `the member-facing paths are used, never the bank-employee ones`() =
        runTest {
            // `/bank/accounts/**` lists every account in the organisation behind a bank role. This
            // app must never reach for it.
            respond(BALANCES)
            repository.balances()

            assertEquals("/api/v1/org-units/bank/balances", requestedUrl().encodedPath)
        }
}

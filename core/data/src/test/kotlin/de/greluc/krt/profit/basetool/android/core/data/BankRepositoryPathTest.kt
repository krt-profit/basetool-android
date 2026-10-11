/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The org bank's member surface: the verb and exact path each of its 24 operations sends, all under
 * `/api/v1/bank/org-units`.
 *
 * Every call is answered with an empty object; only what reached the wire is asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankRepositoryPathTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: BankRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = BankRepository(httpClient = OkHttpClient(), baseUrl = server.url("/").toString().removeSuffix("/"))
        repeat(OPERATIONS) {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .body("{}")
                    .build(),
            )
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * The next recorded request as `METHOD target`.
     *
     * @return the verb and the request target, space-separated.
     */
    private fun nextCall(): String = server.takeRequest().let { "${it.method} ${it.target}" }

    @Test
    fun `the accounts are read and configured under the org-unit prefix`() =
        runTest {
            repository.balances()
            repository.account("a1")
            repository.settings("a1")
            repository.bookings("a1", page = 0, pageSize = PAGE_SIZE)
            repository.setBalanceTarget("a1", target = "500", version = 1)
            repository.setRoleVisibility("a1", "BANK_EMPLOYEE", granted = true)
            repository.setRoleVisibility("a1", "BANK_EMPLOYEE", granted = false)
            repository.setAllMembersVisibility("a1", granted = true)

            assertEquals("GET $BASE/balances", nextCall())
            assertEquals("GET $BASE/accounts/a1", nextCall())
            assertEquals("GET $BASE/accounts/a1/settings", nextCall())
            assertEquals("GET $BASE/accounts/a1/transactions?page=0&size=$PAGE_SIZE", nextCall())
            assertEquals("PUT $BASE/accounts/a1/balance-target", nextCall())
            assertEquals("POST $BASE/accounts/a1/visibility/role/BANK_EMPLOYEE", nextCall())
            assertEquals("DELETE $BASE/accounts/a1/visibility/role/BANK_EMPLOYEE", nextCall())
            assertEquals("PUT $BASE/accounts/a1/visibility/all-members/true", nextCall())
        }

    @Test
    fun `every approval limit tier is written and cleared under the account`() =
        runTest {
            val targets =
                listOf(
                    BankLimitTarget.AllMembers to "all-members",
                    BankLimitTarget.AreaMembers to "area-members",
                    BankLimitTarget.Role("OFFICER") to "role/OFFICER",
                    BankLimitTarget.User("u1") to "user/u1",
                )

            targets.forEach { (target, _) -> repository.setApprovalLimit("a1", target, limit = "100") }
            targets.forEach { (target, _) -> repository.clearApprovalLimit("a1", target) }

            targets.forEach { (_, leaf) -> assertEquals("PUT $BASE/accounts/a1/approval-limit/$leaf", nextCall()) }
            targets.forEach { (_, leaf) -> assertEquals("DELETE $BASE/accounts/a1/approval-limit/$leaf", nextCall()) }
        }

    @Test
    fun `the booking requests are raised and handled under the org-unit prefix`() =
        runTest {
            repository.ownRequests()
            repository.foreignRequests()
            repository.transferTargets()
            repository.createRequest(BankRequestDraft(accountId = "a1", kind = BankRequestKind.DEPOSIT, amount = "10"))
            repository.updateRequest("r1", version = 1, amount = "20", note = null)
            repository.cancelRequest("r1", version = 1)
            repository.setOwnerApproval("r1", granted = true)
            repository.setOwnerApproval("r1", granted = false)

            assertEquals("GET $BASE/requests", nextCall())
            assertEquals("GET $BASE/requests/foreign", nextCall())
            assertEquals("GET $BASE/transfer-targets", nextCall())
            assertEquals("POST $BASE/requests", nextCall())
            assertEquals("PUT $BASE/requests/r1", nextCall())
            assertEquals("POST $BASE/requests/r1/cancel", nextCall())
            assertEquals("POST $BASE/requests/r1/owner-approval", nextCall())
            assertEquals("DELETE $BASE/requests/r1/owner-approval", nextCall())
        }

    private companion object {
        const val HTTP_OK = 200
        const val OPERATIONS = 24
        const val PAGE_SIZE = 25
        const val BASE = "/api/v1/bank/org-units"
    }
}

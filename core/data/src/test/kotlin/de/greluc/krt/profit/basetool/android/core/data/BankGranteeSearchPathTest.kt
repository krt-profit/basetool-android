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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The grantee and counterparty picker of the bank: where it searches and with which parameters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankGranteeSearchPathTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: BankStaffRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            BankStaffRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `the picker searches the bank members with the term as query`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .body("""{"content": [], "page": 0, "totalElements": 0, "totalPages": 0}""")
                    .build(),
            )

            repository.searchGrantees("Rhea")

            val request = server.takeRequest()
            val url = ("http://localhost" + request.target).toHttpUrl()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/bank/members/search", url.encodedPath)
            assertEquals("Rhea", url.queryParameter("query"))
            assertEquals("0", url.queryParameter("page"))
        }

    private companion object {
        const val HTTP_OK = 200
    }
}

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
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The payout preference and the blueprint sharing: the verb and path each of the four operations
 * sends, with the version each read returned echoed on the matching write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemberPreferencesPathTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MemberPreferencesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MemberPreferencesRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Enqueues one JSON answer.
     *
     * @param body the response body.
     */
    private fun respond(body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    @Test
    fun `the payout preference lives under the missions the member signs up to`() =
        runTest {
            respond("""{"defaultPayoutPreference": "DONATE", "version": 3}""")
            respond("""{"defaultPayoutPreference": "PAYOUT", "version": 4}""")

            val read = repository.payoutPreference() as ApiResult.Success
            val written = repository.setPayoutPreference(PayoutPreference.PAYOUT, version = read.value.version)

            val get = server.takeRequest()
            assertEquals("GET /api/v1/missions/me/payout-preference", "${get.method} ${get.target}")
            val put = server.takeRequest()
            assertEquals("PUT /api/v1/missions/me/payout-preference", "${put.method} ${put.target}")
            assertEquals(PayoutPreference.PAYOUT, (written as ApiResult.Success).value.preference)
        }

    @Test
    fun `the blueprint sharing lives under the blueprints`() =
        runTest {
            respond("""{"shareBlueprintsGlobally": false, "version": 3}""")
            respond("""{"shareBlueprintsGlobally": true, "version": 4}""")

            val read = repository.blueprintSharing() as ApiResult.Success
            val written = repository.setBlueprintSharing(sharing = true, version = read.value.version)

            val get = server.takeRequest()
            assertEquals("GET /api/v1/blueprints/me/sharing", "${get.method} ${get.target}")
            val put = server.takeRequest()
            assertEquals("PUT /api/v1/blueprints/me/sharing", "${put.method} ${put.target}")
            assertEquals(true, (written as ApiResult.Success).value.sharing)
        }

    private companion object {
        const val HTTP_OK = 200
    }
}

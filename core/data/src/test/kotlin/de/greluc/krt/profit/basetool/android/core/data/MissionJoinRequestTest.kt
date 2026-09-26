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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Signing up goes to `…/join`, the participant path the API vhost's allow-list admits (ADR-0154).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionJoinRequestTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MissionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MissionRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respondWithMission() {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .setHeader("Content-Type", "application/json")
                .body("""{"id": "m1", "name": "Einsatz", "status": "PLANNED", "participants": []}""")
                .build(),
        )
    }

    @Test
    fun `the sign-up posts to join, never to the add-anybody path`() =
        runTest {
            respondWithMission()

            val result = repository.join(missionId = "m1", desiredJobTypeId = "j1", donate = false)

            assertTrue("the sign-up should have succeeded", result is ApiResult.Success)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(
                "the vhost allow-list exposes …/join, not …/participants/add",
                "/api/v1/missions/m1/join",
                request.url.encodedPath,
            )
        }

    @Test
    fun `the body carries the sheet's two answers and names nobody`() =
        runTest {
            respondWithMission()

            repository.join(missionId = "m1", desiredJobTypeId = "j1", donate = true)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue("the desired Funktion travels", body.contains("\"desiredJobTypeId\""))
            assertTrue("the payout choice travels", body.contains("DONATE"))
            assertFalse("a sign-up names nobody", body.contains("\"userId\""))
            assertFalse("nor a guest", body.contains("\"guestName\""))
        }

    @Test
    fun `declining to donate still says so, rather than omitting the answer`() =
        runTest {
            respondWithMission()

            repository.join(missionId = "m1", desiredJobTypeId = null, donate = false)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue("PAYOUT is an answer, not an absence", body.contains("PAYOUT"))
        }

    private companion object {
        const val HTTP_OK = 200
    }
}

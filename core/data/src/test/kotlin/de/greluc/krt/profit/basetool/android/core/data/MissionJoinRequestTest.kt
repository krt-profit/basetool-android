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
 * Signing up must go to `…/join` — the path the API vhost actually exposes.
 *
 * Not a style preference and not a tidier URL. The vhost in front of the backend is a
 * **default-deny allow-list**, and for participants it exposes `…/join` plus three `…/slim` paths.
 * It does **not** expose `…/participants/add`, which is where this app used to send its sign-ups:
 * every one of them was refused at the edge and never reached the backend, so signing up failed
 * with the sheet's generic „Konnte nicht gespeichert werden." while signing *off* — which uses an
 * allow-listed `…/slim` path — kept working. Reported 2026-09-02, after release; backend ADR-0154.
 *
 * The path is therefore an assertion, not an implementation detail. Nothing else in this repository
 * can see the allow-list: it lives in the main repo's vhost runbook, and the test stack has no vhost
 * at all, which is exactly why the original choice was verified and still wrong.
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
            // `join` derives the member from the token. A body that could name somebody else is
            // what forced the old route onto the add-anybody endpoint in the first place.
            assertFalse("a sign-up names nobody", body.contains("\"userId\""))
            assertFalse("nor a guest", body.contains("\"guestName\""))
        }

    @Test
    fun `declining to donate still says so, rather than omitting the answer`() =
        runTest {
            respondWithMission()

            repository.join(missionId = "m1", desiredJobTypeId = null, donate = false)

            val body = server.takeRequest().body?.utf8().orEmpty()
            // An omitted payoutPreference would mean "no answer" and hand the decision back to the
            // profile default (REQ-MISSION-002) — which is not what a member who unticked the box
            // asked for.
            assertTrue("PAYOUT is an answer, not an absence", body.contains("PAYOUT"))
        }

    private companion object {
        const val HTTP_OK = 200
    }
}

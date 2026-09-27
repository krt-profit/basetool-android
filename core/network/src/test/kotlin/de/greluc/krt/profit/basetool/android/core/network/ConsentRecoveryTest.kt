/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A call refused for missing consent waits for it and is issued once more (ADR-0025).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentRecoveryTest {
    @Serializable
    private data class Payload(
        val content: String? = null,
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val TERMS_REFUSAL = """{"status": 403, "code": "TERMS_NOT_ACCEPTED"}"""
    }

    /**
     * A recovery that answers [answer] and counts how often it was asked.
     *
     * @property answer what the member decided.
     */
    private class CountingConsent(
        private val answer: Boolean,
    ) : ConsentRecovery {
        var asked = 0

        override suspend fun awaitConsent(): Boolean {
            asked += 1
            return answer
        }
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun reader(consent: ConsentRecovery) =
        ApiReader(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().removeSuffix("/"),
            json = Json { ignoreUnknownKeys = true },
            logTag = "test",
            consent = consent,
        )

    private fun respond(
        code: Int,
        body: String,
    ) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader("Content-Type", if (code == HTTP_OK) "application/json" else "application/problem+json")
                .body(body)
                .build(),
        )
    }

    @Test
    fun `a refused read is issued again once consent is given`() =
        runTest {
            respond(HTTP_FORBIDDEN, TERMS_REFUSAL)
            respond(HTTP_OK, """{"content": "ok"}""")
            val consent = CountingConsent(answer = true)

            val result = reader(consent).get("/api/v1/things", Payload.serializer())

            assertEquals(ApiResult.Success(Payload("ok")), result)
            assertEquals(1, consent.asked)
            assertEquals(2, server.requestCount)
        }

    /** The server refused before acting, so the second write is a new request with the same body. */
    @Test
    fun `a refused write is sent again with the same body`() =
        runTest {
            respond(HTTP_FORBIDDEN, TERMS_REFUSAL)
            respond(HTTP_OK, """{"content": "booked"}""")

            val result =
                reader(CountingConsent(answer = true)).post(
                    path = "/api/v1/things",
                    body = "Quantainium",
                    bodySerializer = String.serializer(),
                    deserializer = Payload.serializer(),
                )

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("POST", second.method)
            assertEquals(first.body?.utf8(), second.body?.utf8())
            assertEquals(ApiResult.Success(Payload("booked")), result)
        }

    @Test
    fun `a declined consent returns the refusal without a second request`() =
        runTest {
            respond(HTTP_FORBIDDEN, TERMS_REFUSAL)

            val result = reader(CountingConsent(answer = false)).get("/api/v1/things", Payload.serializer())

            assertTrue((result as ApiResult.Failure).error is ApiError.TermsAcceptanceRequired)
            assertEquals(1, server.requestCount)
        }

    /** The re-issue happens once: a second refusal is returned, never looped on. */
    @Test
    fun `a second refusal is returned as it is`() =
        runTest {
            respond(HTTP_FORBIDDEN, TERMS_REFUSAL)
            respond(HTTP_FORBIDDEN, TERMS_REFUSAL)
            val consent = CountingConsent(answer = true)

            val result = reader(consent).postAccepted("/api/v1/things")

            assertTrue((result as ApiResult.Failure).error is ApiError.TermsAcceptanceRequired)
            assertEquals(1, consent.asked)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `any other refusal never asks for consent`() =
        runTest {
            respond(HTTP_FORBIDDEN, """{"status": 403, "code": "FORBIDDEN"}""")
            val consent = CountingConsent(answer = true)

            reader(consent).getOptional("/api/v1/things", Payload.serializer())

            assertEquals(0, consent.asked)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `without a recovery the refusal passes through`() =
        runTest {
            respond(HTTP_FORBIDDEN, TERMS_REFUSAL)

            val result = reader(ConsentRecovery.None).getBytes("/api/v1/report.pdf")

            assertTrue((result as ApiResult.Failure).error is ApiError.TermsAcceptanceRequired)
            assertEquals(1, server.requestCount)
        }
}

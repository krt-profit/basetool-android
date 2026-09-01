/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * The classification the whole error UI hangs off.
 *
 * The case that matters most is 403: the backend uses it for a pending registration, for unaccepted
 * terms and for a real authorisation failure, and a client branching on the status alone shows the
 * wrong screen for two of the three. Each is asserted separately for that reason.
 *
 * Robolectric because the mapper logs through the project facade, which calls `android.util.Log` —
 * unmocked in a plain JVM test that would then fail on the diagnostic rather than the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiErrorMapperTest {
    private val mapper = ApiErrorMapper()

    /**
     * Builds a response as OkHttp would hand it to the mapper.
     *
     * @param status the HTTP status
     * @param body the raw body, empty for none
     * @param contentType the body's media type
     * @param headers extra response headers, e.g. `Retry-After`
     * @return the assembled response
     */
    private fun response(
        status: Int,
        body: String = "",
        contentType: String = "application/problem+json",
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val builder =
            Response
                .Builder()
                .request(Request.Builder().url("https://api.profit-base.online/api/v1/terms/status").build())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test")
                .body(body.toResponseBody(contentType.toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun problemBody(code: String): String =
        """{"type":"https://profit-base.online/problems/x","title":"T","status":403,"detail":"D",""" +
            """"code":"$code","correlationId":"corr-9"}"""

    @Test
    fun `a pending registration is its own state, not a generic forbidden`() {
        val error = mapper.map(response(status = 403, body = problemBody(ProblemDetail.CODE_PENDING_APPROVAL)))

        assertTrue(error is ApiError.PendingApproval)
        assertEquals("corr-9", error.problem?.correlationId)
    }

    @Test
    fun `an unaccepted terms version is its own state, not a generic forbidden`() {
        val error = mapper.map(response(status = 403, body = problemBody(ProblemDetail.CODE_TERMS_ACCEPTANCE_REQUIRED)))

        assertTrue(error is ApiError.TermsAcceptanceRequired)
    }

    @Test
    fun `a 403 without a known code is a plain authorisation failure`() {
        val error = mapper.map(response(status = 403, body = problemBody("ACCESS_DENIED")))

        assertTrue(error is ApiError.Forbidden)
    }

    @Test
    fun `a rate limit carries its retry-after`() {
        val error =
            mapper.map(
                response(
                    status = 429,
                    body = problemBody(ProblemDetail.CODE_RATE_LIMIT_EXCEEDED),
                    headers = mapOf("Retry-After" to "42"),
                ),
            )

        assertEquals(42.seconds, (error as ApiError.RateLimited).retryAfter)
    }

    @Test
    fun `a rate limit without the header is still a rate limit`() {
        val error = mapper.map(response(status = 429, body = problemBody(ProblemDetail.CODE_RATE_LIMIT_EXCEEDED)))

        assertNull((error as ApiError.RateLimited).retryAfter)
    }

    @Test
    fun `a conflict is the optimistic-lock state the reload prompt hangs off`() {
        val error = mapper.map(response(status = 409, body = problemBody(ProblemDetail.CODE_OPTIMISTIC_LOCK)))

        assertTrue(error is ApiError.OptimisticLock)
    }

    @Test
    fun `a business refusal is not dressed up as a concurrent edit`() {
        // BUSINESS_CONFLICT and its relatives (BANK_ACCOUNT_NOT_EMPTY, BANK_NOT_REVERSIBLE,
        // ENTITY_IN_USE, ...) are 409s that nobody raced for. Mapping them onto OptimisticLock told
        // the member somebody else had changed the row and to reload — untrue, and the advice
        // cannot succeed because reloading does not change the rule that fired.
        val error = mapper.map(response(status = 409, body = problemBody("BUSINESS_CONFLICT")))

        assertTrue(error is ApiError.Conflict)
    }

    @Test
    fun `a business refusal keeps the server's reason, which is the only text that explains it`() {
        val error = mapper.map(response(status = 409, body = problemBody("BANK_ACCOUNT_NOT_EMPTY")))

        // The screen has no sentence of its own that would be true here.
        assertEquals("D", (error as ApiError.Conflict).problem?.detail)
    }

    @Test
    fun `an edge error page classifies by status instead of exploding`() {
        // NPM answers HTML, not problem+json. Losing the status here would leave the UI with
        // nothing to show; only the localised prose is unavailable.
        val error =
            mapper.map(
                response(
                    status = 502,
                    body = "<html><head><title>502 Bad Gateway</title></head></html>",
                    contentType = "text/html",
                ),
            )

        assertTrue(error is ApiError.ServiceUnavailable)
        assertNull(error.problem)
    }

    @Test
    fun `an empty body classifies by status`() {
        val error = mapper.map(response(status = 404))

        assertTrue(error is ApiError.NotFound)
    }

    @Test
    fun `an unknown 5xx keeps its status for the report`() {
        val error = mapper.map(response(status = HTTP_INSUFFICIENT_STORAGE))

        assertEquals(HTTP_INSUFFICIENT_STORAGE, (error as ApiError.Server).status)
    }

    @Test
    fun `an unknown field in the body does not break parsing`() {
        // REQ-API-009 permits additive change on the contract set; a client that rejects a new
        // field would turn the permitted case into a break.
        val error =
            mapper.map(
                response(
                    status = 401,
                    body = """{"code":"UNAUTHENTICATED","title":"T","somethingNew":{"a":1}}""",
                ),
            )

        assertTrue(error is ApiError.Unauthenticated)
        assertEquals("T", error.problem?.title)
    }

    @Test
    fun `a validation body keeps its field errors, in the shape the backend sends them`() {
        // Verbatim from the backend's GlobalExceptionHandler: an ARRAY under `fieldErrors` and the
        // same content again as a legacy map under `errors`. Declaring the array as a map made
        // kotlinx reject the whole body, so every 400 arrived with no title, no detail and no
        // correlation id — found on the device, where adding a frequency failed in silence.
        val error =
            mapper.map(
                response(
                    status = 400,
                    body =
                        """
                        {"code":"VALIDATION_FAILED","title":"Ungültige Eingabe",
                         "detail":"Die Anfrage wurde nicht akzeptiert.",
                         "correlationId":"c-1",
                         "errors":{"value":"numerischer Wert außerhalb des gültigen Bereichs"},
                         "fieldErrors":[{"field":"value","message":"numerischer Wert außerhalb des gültigen Bereichs","code":"Digits"}]}
                        """.trimIndent(),
                ),
            )

        assertTrue(error is ApiError.Validation)
        assertEquals("c-1", error.problem?.correlationId)
        assertEquals("value", error.problem?.fieldErrors?.single()?.field)
        assertEquals("Digits", error.problem?.fieldErrors?.single()?.code)
        assertEquals(
            "numerischer Wert außerhalb des gültigen Bereichs",
            error.problem
                ?.fieldErrors
                ?.single()
                ?.message,
        )
        assertEquals(1, error.problem?.errors?.size)
    }

    private companion object {
        /** A 5xx the mapper has no special case for — it must survive with its status intact. */
        const val HTTP_INSUFFICIENT_STORAGE = 507
    }
}

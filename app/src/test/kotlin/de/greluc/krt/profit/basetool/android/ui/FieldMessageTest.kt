/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ProblemDetail
import de.greluc.krt.profit.basetool.android.core.network.ProblemFieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests which of a problem body's prose carriers the app shows: `fieldErrors` over the duplicate `errors` map, `detail`
 * only when no field is named, and `null` to hand the sentence back to the screen.
 */
class FieldMessageTest {
    /**
     * A validation refusal carrying [problem].
     *
     * @param problem the parsed body.
     * @return the error to ask.
     */
    private fun validation(problem: ProblemDetail?): ApiError = ApiError.Validation(problem)

    @Test
    fun `a named field is answered in the server's own words`() {
        val error =
            validation(
                ProblemDetail(
                    detail = "Validierung fehlgeschlagen",
                    fieldErrors = listOf(ProblemFieldError("frequency", "Menge muss größer als 0 sein.")),
                ),
            )

        assertEquals("Menge muss größer als 0 sein.", error.fieldMessage())
    }

    @Test
    fun `two named fields read as one line, separated the way the design separates facts`() {
        val error =
            validation(
                ProblemDetail(
                    fieldErrors =
                        listOf(
                            ProblemFieldError("name", "darf nicht leer sein"),
                            ProblemFieldError("amount", "muss positiv sein"),
                        ),
                ),
            )

        assertEquals("darf nicht leer sein · muss positiv sein", error.fieldMessage())
    }

    @Test
    fun `the array wins over the legacy map, so the same sentence is not printed twice`() {
        val error =
            validation(
                ProblemDetail(
                    fieldErrors = listOf(ProblemFieldError("amount", "muss positiv sein")),
                    errors = mapOf("amount" to "muss positiv sein"),
                ),
            )

        assertEquals("muss positiv sein", error.fieldMessage())
    }

    @Test
    fun `the legacy map is read when the array is absent`() {
        val error = validation(ProblemDetail(errors = mapOf("amount" to "muss positiv sein")))

        assertEquals("muss positiv sein", error.fieldMessage())
    }

    @Test
    fun `an entry with no message of its own does not become an empty line`() {
        val error =
            validation(
                ProblemDetail(
                    detail = "Validierung fehlgeschlagen",
                    fieldErrors =
                        listOf(
                            ProblemFieldError("a", null),
                            ProblemFieldError("b", "   "),
                        ),
                ),
            )

        assertEquals("Validierung fehlgeschlagen", error.fieldMessage())
    }

    @Test
    fun `detail stands in when the refusal named no field`() {
        val error = validation(ProblemDetail(detail = "Die Datei muss ein JSON-Array enthalten."))

        assertEquals("Die Datei muss ein JSON-Array enthalten.", error.fieldMessage())
    }

    @Test
    fun `a refusal that said nothing hands the sentence back to the screen`() {
        assertNull(validation(ProblemDetail(title = "Bad Request")).fieldMessage())
        assertNull(validation(ProblemDetail(detail = "  ")).fieldMessage())
        assertNull(validation(null).fieldMessage())
    }

    @Test
    fun `only a validation refusal speaks for itself`() {
        val body = ProblemDetail(detail = "Access Denied")

        assertNull(ApiError.Forbidden(body).fieldMessage())
        assertNull(ApiError.OptimisticLock(body).fieldMessage())
        assertNull(ApiError.NotFound(body).fieldMessage())
        assertNull(ApiError.Server(status = 500, problem = body).fieldMessage())
    }
}

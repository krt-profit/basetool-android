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
 * Which of a problem body's three carriers of prose the app shows, and when it shows none.
 *
 * The rule this pins is a precedence, not a lookup: the backend sends the same content twice — as
 * the `fieldErrors` array and again as the legacy `errors` map — so a reader that took both would
 * print every sentence twice. It also sends `detail` on refusals that name no field at all, which
 * is the only case where the generic body text is better than the screen's own copy.
 *
 * The `null` cases are the load-bearing ones: `null` is what hands the sentence back to the screen,
 * and a screen that mapped a 403 to „Du darfst das nicht" must not have that replaced by whatever
 * the server wrote about the request.
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
        // The backend sends both shapes for the same refusal. Reading them in sequence rather than
        // as alternatives would put „muss positiv sein · muss positiv sein" under the field.
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
        // `message` is nullable on the wire, and a blank one rendered as an error box with nothing
        // in it — visually a refusal that refuses to say what it refused.
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
        // Every variant carries a problem body, and on these the server's prose is about the
        // request rather than about what the member should do next — which is the screen's to say.
        val body = ProblemDetail(detail = "Access Denied")

        assertNull(ApiError.Forbidden(body).fieldMessage())
        assertNull(ApiError.OptimisticLock(body).fieldMessage())
        assertNull(ApiError.NotFound(body).fieldMessage())
        assertNull(ApiError.Server(status = 500, problem = body).fieldMessage())
    }
}

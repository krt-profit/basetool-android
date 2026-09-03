/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ProblemDetail

/**
 * The server's own words for a validation refusal, when it named what was wrong.
 *
 * RFC 7807 bodies from this backend carry `fieldErrors` — a localised sentence per offending field
 * („numerischer Wert außerhalb des gültigen Bereichs (<3 digits>.<2 digits> erwartet)") — and the
 * app was throwing every one of them away in favour of „Konnte nicht gespeichert werden." Design
 * ch. 02 §6 draws the field error naming the fault („Menge muss größer als 0 sein."), which is
 * exactly what the server already sent.
 *
 * [ApiError.Conflict] is answered too, and for the same reason. It is a `409` that is **not** a
 * concurrent edit — a rule refusing, such as an account that still holds a balance or a request
 * that has already been decided. What rule fired is something only the server knows, so its
 * `detail` is the whole message; the screen has no sentence of its own that would be true.
 *
 * [ApiError.OptimisticLock] is deliberately still excluded: there the member's next step („reload
 * and save again") matters more than the server's phrasing, and the reload modal carries it.
 * A 403 or a dropped connection likewise needs the screen's own copy.
 *
 * @return the sentence to show, or `null` when the server named nothing and the caller's own copy
 *   has to stand in.
 */
fun ApiError.fieldMessage(): String? =
    when (this) {
        // A rule refused: the server's `detail` names which one, and nothing else can.
        is ApiError.Conflict -> problem?.detail?.takeIf { it.isNotBlank() }

        is ApiError.Validation -> problem?.namedFields()

        else -> null
    }

/**
 * The sentences a validation body named, or its overall detail when it named no field.
 *
 * @return the joined message, or `null` when the body carried nothing sayable.
 */
private fun ProblemDetail.namedFields(): String? {
    val named =
        fieldErrors
            ?.mapNotNull { it.message }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: errors
                ?.values
                ?.filter { it.isNotBlank() }
                .orEmpty()
    return if (named.isNotEmpty()) {
        named.joinToString(FIELD_MESSAGE_SEPARATOR)
    } else {
        detail?.takeIf { it.isNotBlank() }
    }
}

/** Between two field messages, the same separator the design uses between two facts on a line. */
private const val FIELD_MESSAGE_SEPARATOR = " · "

/**
 * What a write surface shows when a write fails.
 *
 * The server's own sentence when there is one ([fieldMessage]); otherwise the screen's copy **plus
 * the technical reference** — the HTTP status and the correlation id.
 *
 * That suffix is not decoration. Until 2026-09-03 every refusal a member could hit read the same:
 * „Konnte nicht gespeichert werden." An edge refusal, an authorisation refusal and a parse failure
 * were indistinguishable to the member *and* to whoever they reported it to, and two separate
 * defects that week were diagnosed only by reading the production log. The status alone separates
 * those three, and the correlation id finds the exact request in one grep.
 *
 * Kept deliberately short and appended in parentheses: it is a reference to quote, not an
 * explanation to read. A member who does not care can ignore it; a member reporting a problem can
 * type six characters and save an afternoon.
 *
 * @param fallback the screen's own sentence, used when the server named nothing.
 * @return the message to show.
 */
@Composable
fun ApiError.writeFailureText(
    @StringRes fallback: Int,
): String {
    val named = fieldMessage()
    val own = stringResource(fallback)
    val status = httpStatus()
    val reference = problem?.correlationId?.takeIf { it.isNotBlank() }
    return when {
        named != null -> named
        status == null -> own
        reference == null -> "$own (" + stringResource(R.string.write_failed_status, status) + ")"
        else -> "$own (" + stringResource(R.string.write_failed_reference, status, reference) + ")"
    }
}

/**
 * The HTTP status behind a failure, where one exists.
 *
 * [ApiError.Network] never reached a server, so it has none — and saying „HTTP 0" would be worse
 * than saying nothing. Everything else either carries the status itself or can read it from the
 * problem body the server sent.
 *
 * @return the status, or `null` when the request produced no HTTP response.
 */
private fun ApiError.httpStatus(): Int? =
    when (this) {
        is ApiError.Network -> null
        is ApiError.Server -> status
        else -> problem?.status
    }

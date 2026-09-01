/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

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

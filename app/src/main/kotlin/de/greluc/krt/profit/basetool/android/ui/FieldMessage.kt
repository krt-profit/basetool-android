/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import de.greluc.krt.profit.basetool.android.core.network.ApiError

/**
 * The server's own words for a validation refusal, when it named what was wrong.
 *
 * RFC 7807 bodies from this backend carry `fieldErrors` — a localised sentence per offending field
 * („numerischer Wert außerhalb des gültigen Bereichs (<3 digits>.<2 digits> erwartet)") — and the
 * app was throwing every one of them away in favour of „Konnte nicht gespeichert werden." Design
 * ch. 02 §6 draws the field error naming the fault („Menge muss größer als 0 sein."), which is
 * exactly what the server already sent.
 *
 * Only [ApiError.Validation] is answered here. A 403, a 409 or a dropped connection needs the
 * screen's own sentence, because the server's is about the request rather than about what the
 * member should do next.
 *
 * @return the sentence to show, or `null` when the server named nothing and the caller's own copy
 *   has to stand in.
 */
fun ApiError.fieldMessage(): String? {
    val problem = (this as? ApiError.Validation)?.problem ?: return null
    val named =
        problem.fieldErrors
            ?.mapNotNull { it.message }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: problem.errors
                ?.values
                ?.filter { it.isNotBlank() }
                .orEmpty()
    return if (named.isNotEmpty()) {
        named.joinToString(FIELD_MESSAGE_SEPARATOR)
    } else {
        problem.detail?.takeIf { it.isNotBlank() }
    }
}

/** Between two field messages, the same separator the design uses between two facts on a line. */
private const val FIELD_MESSAGE_SEPARATOR = " · "

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
 * The server's own words for a validation refusal or an [ApiError.Conflict], when it sent any.
 *
 * Uses the RFC 7807 `fieldErrors` or a conflict's `detail`. [ApiError.OptimisticLock], a 403 and
 * network failures return `null` so the screen's own copy applies.
 *
 * @return the sentence to show, or `null` when the server named nothing and the caller's own copy
 *   has to stand in.
 */
fun ApiError.fieldMessage(): String? =
    when (this) {
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
 * The server's own sentence when there is one ([fieldMessage]); otherwise the screen's copy plus the
 * HTTP status and correlation id in parentheses, for reporting.
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
 * @return the status, or `null` when the request produced no HTTP response, as for
 *   [ApiError.Network].
 */
private fun ApiError.httpStatus(): Int? =
    when (this) {
        is ApiError.Network -> null
        is ApiError.Server -> status
        else -> problem?.status
    }

/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

/**
 * What one API call produced: a value, or a named [ApiError].
 *
 * Kotlin's own `Result` is deliberately not used. It carries a `Throwable`, so every [ApiError]
 * would have to be wrapped in an exception and unwrapped at each call site — turning a sealed
 * hierarchy the compiler can check into a cast that fails at runtime. It also treats every failure
 * as exceptional, and most of these are not: a pending approval and an unaccepted terms version are
 * ordinary states of a healthy account, each with a screen of its own.
 *
 * @param T the value a successful call yields
 */
sealed interface ApiResult<out T> {
    /**
     * The call succeeded.
     *
     * @param T the value type
     * @property value the parsed response
     */
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    /**
     * The call did not produce a usable value.
     *
     * @property error what went wrong, already classified
     */
    data class Failure(
        val error: ApiError,
    ) : ApiResult<Nothing>
}

/**
 * The value, or `null` when the call failed.
 *
 * For the callers that only need the happy path — a background poll that leaves the last known
 * state on screen, for instance — writing a full `when` adds nothing.
 *
 * @param T the value type
 * @return the value on success, `null` on failure
 */
fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

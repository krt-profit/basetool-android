/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

/**
 * What one API call produced: a value, or a named [ApiError], as a sealed type the compiler can
 * check exhaustively.
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
 * The value, or `null` when the call failed, for callers that only need the happy path.
 *
 * @param T the value type
 * @return the value on success, `null` on failure
 */
fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

/**
 * Turns the value of a success into something else, and hands a failure on untouched.
 *
 * `inline`, so [transform] may call suspending functions from a suspending caller.
 *
 * @param T the value type of this result
 * @param R the value type of the returned result
 * @param transform applied to the value of a success; not called for a failure
 * @return a success carrying the transformed value, or this failure unchanged
 */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }

/**
 * Continues with a second call that can fail on its own, and hands a failure of the first on
 * untouched.
 *
 * @param T the value type of this result
 * @param R the value type of the returned result
 * @param transform the next step, given the value of a success; not called for a failure
 * @return what [transform] returned, or this failure unchanged
 */
inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> transform(value)
        is ApiResult.Failure -> this
    }

/**
 * Runs [action] with the error of a failure, as a side effect, and returns this result unchanged.
 *
 * @param T the value type
 * @param action called with the classified error of a failure; not called for a success
 * @return this result
 */
inline fun <T> ApiResult<T>.onFailure(action: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}

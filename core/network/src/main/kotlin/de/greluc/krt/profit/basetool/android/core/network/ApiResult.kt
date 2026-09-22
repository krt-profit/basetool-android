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

/**
 * Turns the value of a success into something else, and hands a failure on untouched.
 *
 * This is the shape nearly every repository method has — read a DTO, map it to the domain model,
 * let the classified [ApiError] through — and it replaces the `when` that said so in four lines:
 * `is Failure -> result` / `is Success -> ApiResult.Success(transform(result.value))`. The failure
 * branch is the part worth centralising: it is the one a hand-written copy can get subtly wrong,
 * for instance by re-wrapping the error and losing its type.
 *
 * `inline`, so [transform] may call suspending functions when the caller is suspending, and
 * allocates no lambda.
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
 * For the sequence "read, then — only if that worked — read or write again": the second step's own
 * failure is returned as it is, so the caller sees whichever step refused, classified by that step.
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
 * Runs [action] with the error of a failure, and returns this result unchanged either way.
 *
 * For a side effect that belongs to the failure alone — a log line, a counter — without breaking
 * the chain the result is being passed along in. It changes nothing about the result: mapping an
 * error to a different one is a `when`, not this.
 *
 * @param T the value type
 * @param action called with the classified error of a failure; not called for a success
 * @return this result
 */
inline fun <T> ApiResult<T>.onFailure(action: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}

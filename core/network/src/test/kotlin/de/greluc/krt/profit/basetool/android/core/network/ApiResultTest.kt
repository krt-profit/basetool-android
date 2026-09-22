/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three combinators every repository now leans on instead of a hand-written `when`.
 *
 * The property that matters is the failure half: a failure must come out as the **same** object it
 * went in as, so its [ApiError] subtype — the thing every screen branches on — cannot be lost in a
 * re-wrap, and the transformation must not run at all, because for a repository it is a DTO mapper
 * that would dereference a value that does not exist.
 */
class ApiResultTest {
    private val failure: ApiResult<Int> = ApiResult.Failure(ApiError.NotFound(problem = null))

    @Test
    fun `map transforms the value of a success`() {
        assertEquals(ApiResult.Success("$VALUE"), ApiResult.Success(VALUE).map { it.toString() })
    }

    @Test
    fun `map hands a failure on as the same object and never calls the transform`() {
        var called = false

        val mapped = failure.map { called = true }

        assertSame("the classified error must survive unchanged", failure, mapped)
        assertTrue("the transform must not run for a failure", !called)
    }

    @Test
    fun `flatMap continues with the second step's own result`() {
        val second: ApiResult<String> = ApiResult.Failure(ApiError.Forbidden(problem = null))

        assertEquals(ApiResult.Success("$VALUE"), ApiResult.Success(VALUE).flatMap { ApiResult.Success(it.toString()) })
        assertSame("a failure of the second step is the answer", second, ApiResult.Success(VALUE).flatMap { second })
    }

    @Test
    fun `flatMap hands a failure of the first step on and never starts the second`() {
        var called = false

        val chained =
            failure.flatMap {
                called = true
                ApiResult.Success(it)
            }

        assertSame(failure, chained)
        assertTrue(!called)
    }

    @Test
    fun `onFailure sees the error of a failure and returns the result unchanged`() {
        var seen: ApiError? = null

        val returned = failure.onFailure { seen = it }

        assertSame(failure, returned)
        assertEquals((failure as ApiResult.Failure).error, seen)
    }

    @Test
    fun `onFailure does nothing for a success`() {
        var called = false
        val success = ApiResult.Success(1)

        assertSame(success, success.onFailure { called = true })
        assertTrue(!called)
    }

    private companion object {
        /** Any value; what matters is whether it arrives transformed or not at all. */
        const val VALUE = 42
    }
}

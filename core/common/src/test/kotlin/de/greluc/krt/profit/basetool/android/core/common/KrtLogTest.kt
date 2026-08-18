/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.common

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The level gate of the logging facade.
 *
 * What is worth pinning is not that a line reaches logcat but that a **dropped** line never
 * evaluates its message lambda: call sites are expected to build strings inline, and a facade that
 * evaluates them regardless would put that cost — and any accidental token interpolation — into
 * release builds where the message is discarded anyway.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KrtLogTest {
    private val originalLevel = KrtLog.minimumLevel

    @After
    fun restoreLevel() {
        KrtLog.minimumLevel = originalLevel
    }

    @Test
    fun `a message below the minimum level is never built`() {
        KrtLog.minimumLevel = Log.INFO
        var evaluations = 0

        KrtLog.d("test") {
            evaluations++
            "expensive"
        }

        assertEquals(0, evaluations)
    }

    @Test
    fun `a message at or above the minimum level is built exactly once`() {
        KrtLog.minimumLevel = Log.DEBUG
        var evaluations = 0

        KrtLog.d("test") {
            evaluations++
            "cheap"
        }
        KrtLog.w("test") {
            evaluations++
            "warning"
        }

        assertEquals(2, evaluations)
    }

    @Test
    fun `raising the level silences the lower ones without touching the higher`() {
        KrtLog.minimumLevel = Log.ERROR
        var evaluations = 0

        KrtLog.i("test") {
            evaluations++
            "info"
        }
        KrtLog.w("test") {
            evaluations++
            "warn"
        }
        KrtLog.e("test") {
            evaluations++
            "error"
        }

        assertEquals(1, evaluations)
    }
}

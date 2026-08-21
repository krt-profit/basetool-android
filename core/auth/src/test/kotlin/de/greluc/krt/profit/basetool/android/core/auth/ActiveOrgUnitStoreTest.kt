/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The org-unit pin, and the property everything else rests on: it answers **synchronously**.
 *
 * `MandatoryHeadersInterceptor` reads it on an OkHttp dispatcher thread that cannot suspend, and
 * the first request of a cold start goes out before anything has had a chance to warm a cache. The
 * earlier DataStore-backed version failed exactly there — measured on a device, the first three
 * requests of every launch carried no header — and the `runBlocking` patch for it deadlocked the
 * first test written against it. Hence a store whose contract is synchronous, and a test that reads
 * it the way the interceptor does: fresh object, no priming, no coroutine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveOrgUnitStoreTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    /**
     * A store over the app's real preference file, as the object graph builds it.
     *
     * @return a fresh instance; a second one models the next process launch.
     */
    private fun store() = ActiveOrgUnitStore(context)

    @Test
    fun `nothing is pinned until something is`() {
        assertNull(store().current())
    }

    @Test
    fun `a pin is readable straight away, from a fresh instance and without priming`() {
        store().pin("b2")

        // What the interceptor does on the first request of a cold start: build, read, done.
        assertEquals("b2", store().current())
    }

    @Test
    fun `the value is visible to the writer before the disk write lands`() {
        val store = store()

        store.pin("a1")

        assertEquals("a1", store.current())
    }

    @Test
    fun `clearing removes it`() {
        val store = store()
        store.pin("a1")

        store.clear()

        assertNull(store.current())
        assertNull(store().current())
    }

    @Test
    fun `the last pin wins`() {
        val store = store()

        store.pin("a1")
        store.pin("b2")

        assertEquals("b2", store.current())
        assertEquals("b2", store().current())
    }

    @Test
    fun `the file name the backup rules exclude is the one the store uses`() {
        // A rename here with no matching edit in the backup rules fails nothing at build time; it
        // just starts carrying one member's org scope onto another member's device.
        assertEquals("${ActiveOrgUnitStore.FILE_NAME}.xml", ActiveOrgUnitStore.BACKUP_PATH)
    }
}

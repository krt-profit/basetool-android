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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that the org-unit pin answers synchronously from a fresh, unprimed store, as `MandatoryHeadersInterceptor`
 * reads it on an OkHttp thread.
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
        assertEquals("${ActiveOrgUnitStore.FILE_NAME}.xml", ActiveOrgUnitStore.BACKUP_PATH)
    }

    /**
     * "All units" has to survive a restart as a *choice*.
     *
     * Storing it as the absence of a pin would work for exactly one session: the next cold start
     * would see no pin, fall through to the server default, and quietly put the member back into a
     * single Staffel they never chose.
     */
    @Test
    fun `choosing all units is remembered, and is not the same as no pin`() {
        val first = store()
        first.pinAll()

        val fresh = store()
        assertTrue("the choice must survive a new instance", fresh.isAllChosen())
        assertNull("there is no unit to pin, so no header goes out", fresh.current())
    }

    @Test
    fun `pinning a unit takes back the all-units choice`() {
        val subject = store()
        subject.pinAll()

        subject.pin("unit-7")

        assertFalse(subject.isAllChosen())
        assertEquals("unit-7", subject.current())
    }

    @Test
    fun `clearing drops the all-units choice too`() {
        val subject = store()
        subject.pinAll()

        subject.clear()

        assertFalse(subject.isAllChosen())
        assertNull(subject.current())
    }
}

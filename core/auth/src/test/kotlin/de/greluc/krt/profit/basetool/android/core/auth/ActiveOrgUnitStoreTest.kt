/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The org-unit pin, and the one property that is easy to get wrong.
 *
 * `MandatoryHeadersInterceptor` reads this **synchronously**, on an OkHttp dispatcher thread that
 * cannot suspend. So the store mirrors its value in memory, and the mirror has to be right at three
 * moments: after a cold start (nothing in memory yet, a value on disk), after a write, and after a
 * clear. Get the first one wrong and the app's very first request goes out unscoped — no error, no
 * symptom, just a screen showing the wrong scope's data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveOrgUnitStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        // On the test dispatcher, so `runTest` actually waits for the file writes rather than
        // asserting against a coroutine still suspended on real IO.
        //
        // The file is NAMED, never created: DataStore writes a `.tmp` and renames it over the
        // target, and `newFile()` would hand it a target that already exists.
        //
        // Robolectric is what makes the rename work at all here. On a plain JVM test on Windows,
        // `File.renameTo` refuses to replace an existing file, so the SECOND write of any test
        // failed with `Unable to rename ...` while CI on Linux stayed green — a split that hides a
        // failure from whoever is most able to fix it.
        dataStore =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + Job())) {
                java.io.File(folder.root, "org.preferences_pb")
            }
    }

    @Test
    fun `nothing is pinned until something is`() =
        runTest(dispatcher) {
            val store = ActiveOrgUnitStore(dataStore)

            assertNull(store.load())
            assertNull(store.current())
        }

    @Test
    fun `a pin is readable synchronously straight away`() =
        runTest(dispatcher) {
            val store = ActiveOrgUnitStore(dataStore)

            store.pin("a1")

            // Without waiting, without collecting: this is what the interceptor gets.
            assertEquals("a1", store.current())
        }

    @Test
    fun `a pin survives a cold start`() =
        runTest(dispatcher) {
            ActiveOrgUnitStore(dataStore).pin("b2")

            // A second instance over the same file is what the next process launch builds.
            val next = ActiveOrgUnitStore(dataStore)
            assertNull("nothing is mirrored before load()", next.current())
            assertEquals("b2", next.load())
            assertEquals("b2", next.current())
        }

    @Test
    fun `clearing removes it from both the file and the mirror`() =
        runTest(dispatcher) {
            val store = ActiveOrgUnitStore(dataStore)
            store.pin("a1")

            store.clear()

            assertNull(store.current())
            assertNull(ActiveOrgUnitStore(dataStore).load())
        }

    @Test
    fun `the last pin wins`() =
        runTest(dispatcher) {
            val store = ActiveOrgUnitStore(dataStore)

            store.pin("a1")
            store.pin("b2")

            assertEquals("b2", store.current())
            assertEquals("b2", ActiveOrgUnitStore(dataStore).load())
        }
}

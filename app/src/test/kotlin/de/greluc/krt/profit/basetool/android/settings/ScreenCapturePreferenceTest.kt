/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The one property this preference must never get wrong: **unset means blocked**.
 *
 * A fresh install has nothing stored, and so does an install whose store failed to read. Both must
 * behave as if the member had never asked for screenshots, because the alternative — defaulting to
 * allowed — would silently undo `REQ-APP-AUTH-010` for everyone who never opens Einstellungen.
 */
class ScreenCapturePreferenceTest {
    @get:Rule val tmp = TemporaryFolder()

    /**
     * Builds a preference over a throwaway store file.
     *
     * **Write to it at most once per test.** DataStore commits by renaming its temp file over the
     * target, and `File.renameTo` refuses an existing destination on Windows — so a second write
     * dies with "multiple instances of DataStore for this file", which is neither what happened nor
     * a defect in the code under test. The round trip is covered against [RecordingStore] instead.
     *
     * @return the preference under test, backed by a file that dies with the test.
     */
    private fun fileBackedPreference(): ScreenCapturePreference {
        val file = File(tmp.newFolder(), "krt_settings.preferences_pb")
        return ScreenCapturePreference(PreferenceDataStoreFactory.create { file })
    }

    @Test
    fun `an untouched install blocks capture`() =
        runTest {
            // The default is the security property. Nothing in the UI has to run for it to hold.
            assertTrue(fileBackedPreference().blocked.first())
        }

    @Test
    fun `allowing capture survives a real store`() =
        runTest {
            val pref = fileBackedPreference()

            pref.set(blocked = false)

            assertFalse(pref.blocked.first())
        }

    @Test
    fun `blocking it again is remembered rather than dropped`() =
        runTest {
            // A tester who took their screenshot must be able to put the guard back. The risk is a
            // "restore the default" implementation that removes the key: reading it back would then
            // still say blocked, so this asserts the value was actually written.
            val store = RecordingStore()
            val pref = ScreenCapturePreference(store)
            pref.set(blocked = false)

            pref.set(blocked = true)

            assertTrue(pref.blocked.first())
            assertTrue("the choice must be stored, not reset", store.data.first().asMap().isNotEmpty())
        }

    /**
     * An in-memory stand-in for the preferences store.
     *
     * Exists only so a test may write twice; see [fileBackedPreference] for why the real store
     * cannot on this platform.
     */
    private class RecordingStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }
}

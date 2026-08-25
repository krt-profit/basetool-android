/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the member has allowed screenshots and screen recording.
 *
 * Blocking capture (`FLAG_SECURE`) is the app's default and stays the default — see
 * `REQ-APP-AUTH-010`. What this adds is a way out, because the block also stopped the one capture
 * we actually want: a tester photographing a defect. An app whose bug reports cannot carry a
 * picture is harder to fix than one whose recents thumbnail is legible.
 *
 * **A plain boolean, unlike the app lock beside it.** `AppLockSetting` deliberately stores a sealed
 * key rather than a flag, so "armed" cannot disagree with "satisfiable". Nothing of the sort
 * applies here: this records a preference, the platform enforces it, and there is no second fact it
 * could contradict.
 *
 * **Its own store, not the token store.** Signing out wipes that one, and a preference about
 * screenshots is a property of the device and its owner, not of a session. A tester who logs out
 * and back in should not find the block silently restored.
 *
 * The value is not secret — it says nothing about the member — so it needs neither the Keystore nor
 * a backup-exclusion rule of its own. Backups are off app-wide regardless
 * (`android:allowBackup="false"`).
 *
 * @property dataStore the preferences store this app's settings live in.
 */
class ScreenCapturePreference(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Emits whether capture must be blocked, and again on every change.
     *
     * **Defaults to `true` when unset**, which is what makes a fresh install secure without anyone
     * choosing anything, and what makes a failed read fail closed rather than open.
     */
    val blocked: Flow<Boolean> = dataStore.data.map { it[KEY] ?: true }

    /**
     * Records the member's choice.
     *
     * @param blocked `true` to keep screenshots and screen recording blocked, `false` to allow
     *   them.
     */
    suspend fun set(blocked: Boolean) {
        dataStore.edit { it[KEY] = blocked }
    }

    companion object {
        /** Name of the settings store; separate from the token store on purpose (see above). */
        private const val STORE_NAME = "krt_settings"

        private val KEY = booleanPreferencesKey("screen_capture_blocked")

        /**
         * Opens (or creates) the settings DataStore.
         *
         * @param context any context; the application context is used internally.
         * @return the store this preference is read from and written to.
         */
        fun createStore(context: Context): DataStore<Preferences> {
            val appContext = context.applicationContext
            return PreferenceDataStoreFactory.create {
                appContext.preferencesDataStoreFile(STORE_NAME)
            }
        }
    }
}

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
 * Whether the member has allowed screenshots and screen recording (REQ-APP-AUTH-010).
 *
 * Capture is blocked (`FLAG_SECURE`) by default. The flag lives in its own settings store, not the
 * token store, so signing out does not reset it.
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
        /** Name of the settings store, kept separate from the token store so sign-out does not clear it. */
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

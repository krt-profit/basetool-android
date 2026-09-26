/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

/**
 * Creates the DataStore holding the encrypted refresh token and owns its file name.
 *
 * The name must match the `cloud-backup` and `device-transfer` exclusions in
 * `data_extraction_rules.xml`; `BackupExclusionTest` checks them against [RELATIVE_PATH].
 */
object AuthDataStore {
    /**
     * Preferences DataStore name, without the extension; matches the backup exclusions.
     */
    const val STORE_NAME: String = "krt_tokens"

    /**
     * The on-disk name DataStore derives from [STORE_NAME].
     *
     * Preferences DataStore appends this suffix; it is spelled out here so the backup rules can
     * exclude an exact file rather than a directory that might later hold something else.
     */
    const val FILE_NAME: String = "$STORE_NAME.preferences_pb"

    /**
     * The path a backup rule must exclude, relative to the app's `files/` directory.
     *
     * `preferencesDataStoreFile` places the store under `files/datastore/`, so excluding the bare
     * store name — which is what a first reading of the rules suggests — would match nothing.
     */
    const val RELATIVE_PATH: String = "datastore/$FILE_NAME"

    /**
     * Opens (or creates) the token DataStore for this app.
     *
     * @param context any context; the application context is used internally
     * @return the store the [RefreshTokenStore] writes its ciphertext into
     */
    fun create(context: Context): DataStore<Preferences> {
        val appContext = context.applicationContext
        return PreferenceDataStoreFactory.create {
            appContext.preferencesDataStoreFile(STORE_NAME)
        }
    }
}

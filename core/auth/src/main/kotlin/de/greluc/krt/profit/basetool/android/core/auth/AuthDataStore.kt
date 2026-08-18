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
 * Creates the DataStore that holds the encrypted refresh token, and — more importantly — owns the
 * file name that the backup exclusions have to match.
 *
 * The name is a published constant rather than a string literal at the call site because the
 * protection depends on two files agreeing: `backup_rules.xml` (API ≤ 30) and
 * `data_extraction_rules.xml` (API 31+, in **both** its `cloud-backup` and `device-transfer`
 * sections). A rename here with no matching edit there does not fail anything at build time; it
 * just starts uploading a refresh token to Google Drive. `BackupExclusionTest` in `:app` reads the
 * XML and compares it against [RELATIVE_PATH] for exactly that reason.
 */
object AuthDataStore {
    /**
     * Preferences DataStore name, without the extension.
     *
     * Chosen to match the exclusions that were written before this store existed, rather than
     * renaming those and hoping every copy was found.
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

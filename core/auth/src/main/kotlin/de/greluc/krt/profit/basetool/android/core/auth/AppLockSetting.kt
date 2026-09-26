/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The app lock's armed state, stored as the session key sealed by the auth-bound Keystore key.
 *
 * Its presence means the lock is armed; opening the app decrypts it, which needs a real
 * authentication. The value is secret and must never be logged or exported. It lives in the token
 * store so logout's wipe removes it.
 *
 * @property dataStore the same preferences store the refresh token uses
 */
class AppLockSetting(
    private val dataStore: DataStore<Preferences>,
) {
    /** Emits whether the lock is armed, and again whenever that changes. */
    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY] != null }

    /**
     * Reads the sealed session key.
     *
     * @return the blob to hand to [AppLockKey.unlockCipher], or `null` when the lock is off
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun sealedSessionKey(): ByteArray? =
        dataStore.data.first()[KEY]?.let { stored ->
            try {
                Base64.decode(stored)
            } catch (malformed: IllegalArgumentException) {
                KrtLog.w(LOG_TAG, malformed) { "stored app-lock session key is not decodable" }
                null
            }
        }

    /**
     * Arms the lock with a freshly sealed session key.
     *
     * @param sealed the ciphertext from [AppLockKey.seal]
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun arm(sealed: ByteArray) {
        dataStore.edit { it[KEY] = Base64.encode(sealed) }
    }

    /**
     * Disarms the lock.
     */
    suspend fun disarm() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        /** Log subsystem; the sealed session key is never written to a log. */
        const val LOG_TAG = "lock"

        /** Preference key; distinct from the token entry so a wipe can target either. */
        val KEY = stringPreferencesKey("app_lock_session_key")
    }
}

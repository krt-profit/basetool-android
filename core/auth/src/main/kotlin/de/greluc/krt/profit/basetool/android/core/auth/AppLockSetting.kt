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
 * The app lock's armed state — which is the **sealed session key**, not a boolean.
 *
 * A `Boolean` was the first design here and it was the wrong one: a flag records that the member
 * *wants* a lock, and nothing in the app can then tell whether a lock actually stands. What is
 * stored instead is the session key [SessionEnvelope] wraps the refresh token with, sealed by the
 * auth-bound Keystore key. Its presence means a key exists; opening the app means decrypting it
 * back, which Keystore permits only after a real authentication. "Is the lock armed" and "can the
 * lock be satisfied" therefore stop being two facts that can disagree.
 *
 * **This one IS secret**, unlike the sentinel it replaced: recovering the plaintext here would
 * remove the refresh token's outer layer without any authentication, which is the whole property
 * being bought. It is safe at rest only because the bytes stored are ciphertext under a key that
 * never leaves the Keystore — so the encoding below protects nothing on its own, and nothing that
 * reads this value may log or export it.
 *
 * It lives in the token store so logout's wipe reaches it: a device handed on with the app signed
 * out should not still ask the next person for a fingerprint.
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
                // A blob that cannot even be decoded is one no key could open. Reading it as "no
                // lock" is the only outcome that does not strand the member behind a broken gate.
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
